package br.com.trilhara.nota;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Filters.ne;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.TextSearchOptions;
import com.mongodb.client.model.Updates;

import br.com.trilhara.shared.mongo.BsonDates;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Repositório BSON da coleção {@code notas}.
 *
 * <p>Toda operação de tenant recebe {@code ObjectId usuarioId} como primeiro parâmetro
 * e o aplica no filtro — inclusive as consultas compostas. As únicas exceções são as
 * conversões BSON e a varredura de purga por {@code expiresAt}, que é multi-tenant por
 * definição.</p>
 *
 * <p>Listagens nunca projetam {@code conteudoMarkdown}: ele só é lido por
 * {@link #buscarAtiva(ObjectId, ObjectId)} e pela busca textual, que precisa do texto para
 * montar o snippet em memória.</p>
 */
@ApplicationScoped
public class NotaRepository {

    private static final String CAMPO_CONTEUDO = "conteudoMarkdown";
    private static final String CAMPO_USUARIO_ID = "usuarioId";
    private static final String CAMPO_DELETED_AT = "deletedAt";
    private static final String CAMPO_CADERNO_ID = "cadernoId";
    private static final String CAMPO_SECAO_ID = "secaoId";
    private static final String CAMPO_ORDEM = "ordem";
    private static final String CAMPO_CRIADO_EM = "criadoEm";
    private static final String CAMPO_ATUALIZADO_EM = "atualizadoEm";
    private static final String CAMPO_TITULO = "titulo";
    private static final String CAMPO_TOTAL = "total";
    private static final String CAMPO_EXPIRES_AT = "expiresAt";
    private static final String CAMPO_LOTE_EXCLUSAO_ID = "loteExclusaoId";

    private final MongoCollection<Document> collection;

    @Inject
    public NotaRepository(
            MongoClient mongoClient,
            @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "trilhara") String databaseName) {
        this.collection = mongoClient.getDatabase(databaseName).getCollection("notas");
    }

    public void inserir(Nota nota) {
        collection.insertOne(paraDocumento(nota));
    }

    public boolean atualizar(Nota nota) {
        Document doc = paraDocumento(nota);
        return collection.replaceOne(
                and(eq("_id", nota.id()), eq(CAMPO_USUARIO_ID, nota.usuarioId())),
                doc,
                new ReplaceOptions().upsert(false)
        ).getMatchedCount() > 0;
    }

    public Optional<Nota> buscarAtiva(ObjectId usuarioId, ObjectId notaId) {
        Document doc = collection.find(and(
                eq("_id", notaId),
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_DELETED_AT, null)
        )).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public Optional<Nota> buscarQualquerStatus(ObjectId usuarioId, ObjectId notaId) {
        Document doc = collection.find(and(
                eq("_id", notaId),
                eq(CAMPO_USUARIO_ID, usuarioId)
        )).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    /**
 * Lista metadados de uma seção, sem {@code conteudoMarkdown}.
 *
 * <p>A ordenação inclui {@code _id} como desempate estável, como exige a norma de
 * paginação por offset.</p>
 */
    public List<Nota> listarMetadadosPorSecao(
            ObjectId usuarioId, ObjectId cadernoId, ObjectId secaoId, int pagina, int tamanho) {
        List<Nota> lista = new ArrayList<>();
        collection.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_CADERNO_ID, cadernoId),
                eq(CAMPO_SECAO_ID, secaoId),
                eq(CAMPO_DELETED_AT, null)
        ))
        .projection(Projections.exclude(CAMPO_CONTEUDO))
        .sort(Sorts.orderBy(
                Sorts.ascending(CAMPO_ORDEM),
                Sorts.ascending(CAMPO_CRIADO_EM),
                Sorts.ascending("_id")))
        .skip(deslocamento(pagina, tamanho))
        .limit(tamanho)
        .forEach(doc -> lista.add(paraEntidade(doc)));

        return lista;
    }

    public long contarPorSecao(ObjectId usuarioId, ObjectId secaoId) {
        return collection.countDocuments(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_SECAO_ID, secaoId),
                eq(CAMPO_DELETED_AT, null)
        ));
    }

    public long contarPorCaderno(ObjectId usuarioId, ObjectId cadernoId) {
        return collection.countDocuments(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_CADERNO_ID, cadernoId),
                eq(CAMPO_DELETED_AT, null)
        ));
    }

    /**
 * Contagens de páginas por caderno em <strong>uma</strong> agregação — evita N+1 na
 * listagem de até 500 cadernos.
 */
    public Map<ObjectId, Long> contarPorCadernosEmLote(ObjectId usuarioId, List<ObjectId> cadernoIds) {
        Map<ObjectId, Long> contagens = new HashMap<>();
        if (cadernoIds == null || cadernoIds.isEmpty()) {
            return contagens;
        }
        collection.aggregate(List.of(
                new Document("$match", new Document(CAMPO_USUARIO_ID, usuarioId)
                        .append(CAMPO_CADERNO_ID, new Document("$in", cadernoIds))
                        .append(CAMPO_DELETED_AT, null)),
                new Document("$group", new Document("_id", "$" + CAMPO_CADERNO_ID)
                        .append(CAMPO_TOTAL, new Document("$sum", 1)))
        )).forEach(doc -> contagens.put(doc.getObjectId("_id"), ((Number) doc.get(CAMPO_TOTAL)).longValue()));
        return contagens;
    }

    /** Contagens de páginas por seção em uma agregação — evita N+1 na listagem de seções. */
    public Map<ObjectId, Long> contarPorSecoesEmLote(ObjectId usuarioId, List<ObjectId> secaoIds) {
        Map<ObjectId, Long> contagens = new HashMap<>();
        if (secaoIds == null || secaoIds.isEmpty()) {
            return contagens;
        }
        collection.aggregate(List.of(
                new Document("$match", new Document(CAMPO_USUARIO_ID, usuarioId)
                        .append(CAMPO_SECAO_ID, new Document("$in", secaoIds))
                        .append(CAMPO_DELETED_AT, null)),
                new Document("$group", new Document("_id", "$" + CAMPO_SECAO_ID)
                        .append(CAMPO_TOTAL, new Document("$sum", 1)))
        )).forEach(doc -> contagens.put(doc.getObjectId("_id"), ((Number) doc.get(CAMPO_TOTAL)).longValue()));
        return contagens;
    }

    public int proximaOrdem(ObjectId usuarioId, ObjectId secaoId) {
        Document doc = collection.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_SECAO_ID, secaoId),
                eq(CAMPO_DELETED_AT, null)
        )).sort(Sorts.descending(CAMPO_ORDEM)).first();

        if (doc != null && doc.getInteger(CAMPO_ORDEM) != null) {
            return doc.getInteger(CAMPO_ORDEM) + 1;
        }
        return 0;
    }

    /**
 * Busca textual sobre o índice composto {@code idx_notas_usuarioId_deletedAt_text}
 *. Sempre {@code $text}: nenhum fallback para regex ou varredura da coleção.
 */
    public List<Document> buscaGlobal(ObjectId usuarioId, String termoBusca, int pagina, int tamanho) {
        List<Document> resultados = new ArrayList<>();
        collection.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_DELETED_AT, null),
                Filters.text(termoBusca, new TextSearchOptions().language("portuguese"))
        ))
        .projection(Projections.fields(
                Projections.include("_id", CAMPO_USUARIO_ID, CAMPO_CADERNO_ID, CAMPO_SECAO_ID, CAMPO_TITULO,
                        CAMPO_CONTEUDO, CAMPO_ATUALIZADO_EM, CAMPO_CRIADO_EM),
                Projections.metaTextScore("score")
        ))
        .sort(Sorts.orderBy(
                Sorts.descending("score"),
                Sorts.descending(CAMPO_ATUALIZADO_EM),
                Sorts.descending("_id")
        ))
        .skip(deslocamento(pagina, tamanho))
        .limit(tamanho)
        .into(resultados);

        return resultados;
    }

    public long contarBuscaGlobal(ObjectId usuarioId, String termoBusca) {
        return collection.countDocuments(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_DELETED_AT, null),
                Filters.text(termoBusca, new TextSearchOptions().language("portuguese"))
        ));
    }

    public boolean moverParaLixeira(
            ObjectId usuarioId, ObjectId notaId, String loteExclusaoId, Instant agora, Instant expiresAt) {
        return collection.updateOne(
                and(eq("_id", notaId), eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_DELETED_AT, null)),
                Updates.combine(
                        Updates.set(CAMPO_DELETED_AT, BsonDates.toBsonDate(agora)),
                        Updates.set(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(expiresAt)),
                        Updates.set(CAMPO_LOTE_EXCLUSAO_ID, loteExclusaoId),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(agora))
                )
        ).getModifiedCount() > 0;
    }

    public long moverPorSecaoParaLixeira(
            ObjectId usuarioId, ObjectId secaoId, String loteExclusaoId, Instant agora, Instant expiresAt) {
        return collection.updateMany(
                and(eq(CAMPO_SECAO_ID, secaoId), eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_DELETED_AT, null)),
                Updates.combine(
                        Updates.set(CAMPO_DELETED_AT, BsonDates.toBsonDate(agora)),
                        Updates.set(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(expiresAt)),
                        Updates.set(CAMPO_LOTE_EXCLUSAO_ID, loteExclusaoId),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(agora))
                )
        ).getModifiedCount();
    }

    public long moverPorCadernoParaLixeira(
            ObjectId usuarioId, ObjectId cadernoId, String loteExclusaoId, Instant agora, Instant expiresAt) {
        return collection.updateMany(
                and(eq(CAMPO_CADERNO_ID, cadernoId), eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_DELETED_AT, null)),
                Updates.combine(
                        Updates.set(CAMPO_DELETED_AT, BsonDates.toBsonDate(agora)),
                        Updates.set(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(expiresAt)),
                        Updates.set(CAMPO_LOTE_EXCLUSAO_ID, loteExclusaoId),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(agora))
                )
        ).getModifiedCount();
    }

    public boolean restaurar(ObjectId usuarioId, ObjectId notaId) {
        return collection.updateOne(
                and(eq("_id", notaId), eq(CAMPO_USUARIO_ID, usuarioId)),
                Updates.combine(
                        Updates.unset(CAMPO_DELETED_AT),
                        Updates.unset(CAMPO_EXPIRES_AT),
                        Updates.unset(CAMPO_LOTE_EXCLUSAO_ID),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
                )
        ).getModifiedCount() > 0;
    }

    public long restaurarPorLoteECaderno(ObjectId usuarioId, ObjectId cadernoId, String loteExclusaoId) {
        if (loteExclusaoId == null) {
            return 0;
        }
        return collection.updateMany(
                and(
                        eq(CAMPO_CADERNO_ID, cadernoId),
                        eq(CAMPO_USUARIO_ID, usuarioId),
                        eq(CAMPO_LOTE_EXCLUSAO_ID, loteExclusaoId)
                ),
                Updates.combine(
                        Updates.unset(CAMPO_DELETED_AT),
                        Updates.unset(CAMPO_EXPIRES_AT),
                        Updates.unset(CAMPO_LOTE_EXCLUSAO_ID),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
                )
        ).getModifiedCount();
    }

    public long restaurarPorLoteESecao(ObjectId usuarioId, ObjectId secaoId, String loteExclusaoId) {
        if (loteExclusaoId == null) {
            return 0;
        }
        return collection.updateMany(
                and(
                        eq(CAMPO_SECAO_ID, secaoId),
                        eq(CAMPO_USUARIO_ID, usuarioId),
                        eq(CAMPO_LOTE_EXCLUSAO_ID, loteExclusaoId)
                ),
                Updates.combine(
                        Updates.unset(CAMPO_DELETED_AT),
                        Updates.unset(CAMPO_EXPIRES_AT),
                        Updates.unset(CAMPO_LOTE_EXCLUSAO_ID),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
                )
        ).getModifiedCount();
    }

    public long contarLixeira(ObjectId usuarioId, Instant agora) {
        return collection.countDocuments(filtroLixeira(usuarioId, agora));
    }

    public List<Nota> listarLixeira(ObjectId usuarioId, Instant agora, int pagina, int tamanho) {
        List<Nota> lista = new ArrayList<>();
        collection.find(filtroLixeira(usuarioId, agora))
                .sort(Sorts.orderBy(Sorts.descending(CAMPO_DELETED_AT), Sorts.descending("_id")))
                .skip(deslocamento(pagina, tamanho))
                .limit(tamanho)
                .forEach(doc -> lista.add(paraEntidade(doc)));
        return lista;
    }

    public long esvaziarLixeira(ObjectId usuarioId) {
        return collection.deleteMany(
                and(eq(CAMPO_USUARIO_ID, usuarioId), ne(CAMPO_DELETED_AT, null))
        ).getDeletedCount();
    }

    /** Purga definitiva por {@code expiresAt} em todos os tenants. */
    public long purgarExpirados(Instant agora) {
        return collection.deleteMany(
                and(ne(CAMPO_DELETED_AT, null), lte(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(agora)))
        ).getDeletedCount();
    }

    private static Bson filtroLixeira(ObjectId usuarioId, Instant agora) {
        return and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                ne(CAMPO_DELETED_AT, null),
                gt(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(agora))
        );
    }

    private static int deslocamento(int pagina, int tamanho) {
        return Math.max(0, (pagina - 1) * tamanho);
    }

    public Document paraDocumento(Nota n) {
        Document doc = new Document("_id", n.id() != null ? n.id() : new ObjectId())
                .append(CAMPO_USUARIO_ID, n.usuarioId())
                .append(CAMPO_CADERNO_ID, n.cadernoId())
                .append(CAMPO_SECAO_ID, n.secaoId())
                .append(CAMPO_TITULO, n.titulo())
                .append(CAMPO_CONTEUDO, n.conteudoMarkdown() != null ? n.conteudoMarkdown() : "")
                .append(CAMPO_ORDEM, n.ordem())
                .append(CAMPO_CRIADO_EM, BsonDates.toBsonDate(n.criadoEm()))
                .append(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(n.atualizadoEm()));

        if (n.topicoOrigemId() != null) {
            doc.append("topicoOrigemId", n.topicoOrigemId());
        }
        if (n.deletedAt() != null) {
            doc.append(CAMPO_DELETED_AT, BsonDates.toBsonDate(n.deletedAt()));
        }
        if (n.expiresAt() != null) {
            doc.append(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(n.expiresAt()));
        }
        if (n.loteExclusaoId() != null) {
            doc.append(CAMPO_LOTE_EXCLUSAO_ID, n.loteExclusaoId());
        }
        return doc;
    }

    public Nota paraEntidade(Document doc) {
        if (doc == null) {
            return null;
        }
        return new Nota(
                doc.getObjectId("_id"),
                doc.getObjectId(CAMPO_USUARIO_ID),
                doc.getObjectId(CAMPO_CADERNO_ID),
                doc.getObjectId(CAMPO_SECAO_ID),
                doc.getString(CAMPO_TITULO),
                doc.getString(CAMPO_CONTEUDO),
                doc.getInteger(CAMPO_ORDEM, 0),
                doc.getString("topicoOrigemId"),
                BsonDates.lerInstant(doc, CAMPO_DELETED_AT),
                BsonDates.lerInstant(doc, CAMPO_EXPIRES_AT),
                doc.getString(CAMPO_LOTE_EXCLUSAO_ID),
                BsonDates.lerInstant(doc, CAMPO_CRIADO_EM),
                BsonDates.lerInstant(doc, CAMPO_ATUALIZADO_EM)
        );
    }
}
