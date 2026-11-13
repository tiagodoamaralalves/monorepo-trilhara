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
 * Repositório BSON da coleção {@code notas} (REQUISITOS §10, D-29).
 *
 * <p>Toda operação de tenant recebe {@code ObjectId usuarioId} como primeiro parâmetro
 * (D-06a) e o aplica no filtro — inclusive as consultas compostas. As únicas exceções são as
 * conversões BSON e a varredura de purga por {@code expiresAt}, que é multi-tenant por
 * definição (D-06b).</p>
 *
 * <p>Listagens nunca projetam {@code conteudoMarkdown}: ele só é lido por
 * {@link #buscarAtiva(ObjectId, ObjectId)} e pela busca textual, que precisa do texto para
 * montar o snippet em memória.</p>
 */
@ApplicationScoped
public class NotaRepository {

    private static final String CAMPO_CONTEUDO = "conteudoMarkdown";

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
                and(eq("_id", nota.id()), eq("usuarioId", nota.usuarioId())),
                doc,
                new ReplaceOptions().upsert(false)
        ).getMatchedCount() > 0;
    }

    public Optional<Nota> buscarAtiva(ObjectId usuarioId, ObjectId notaId) {
        Document doc = collection.find(and(
                eq("_id", notaId),
                eq("usuarioId", usuarioId),
                eq("deletedAt", null)
        )).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public Optional<Nota> buscarQualquerStatus(ObjectId usuarioId, ObjectId notaId) {
        Document doc = collection.find(and(
                eq("_id", notaId),
                eq("usuarioId", usuarioId)
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
                eq("usuarioId", usuarioId),
                eq("cadernoId", cadernoId),
                eq("secaoId", secaoId),
                eq("deletedAt", null)
        ))
        .projection(Projections.exclude(CAMPO_CONTEUDO))
        .sort(Sorts.orderBy(
                Sorts.ascending("ordem"),
                Sorts.ascending("criadoEm"),
                Sorts.ascending("_id")))
        .skip(deslocamento(pagina, tamanho))
        .limit(tamanho)
        .forEach(doc -> lista.add(paraEntidade(doc)));

        return lista;
    }

    public long contarPorSecao(ObjectId usuarioId, ObjectId secaoId) {
        return collection.countDocuments(and(
                eq("usuarioId", usuarioId),
                eq("secaoId", secaoId),
                eq("deletedAt", null)
        ));
    }

    public long contarPorCaderno(ObjectId usuarioId, ObjectId cadernoId) {
        return collection.countDocuments(and(
                eq("usuarioId", usuarioId),
                eq("cadernoId", cadernoId),
                eq("deletedAt", null)
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
                new Document("$match", new Document("usuarioId", usuarioId)
                        .append("cadernoId", new Document("$in", cadernoIds))
                        .append("deletedAt", null)),
                new Document("$group", new Document("_id", "$cadernoId")
                        .append("total", new Document("$sum", 1)))
        )).forEach(doc -> contagens.put(doc.getObjectId("_id"), ((Number) doc.get("total")).longValue()));
        return contagens;
    }

    /** Contagens de páginas por seção em uma agregação — evita N+1 na listagem de seções. */
    public Map<ObjectId, Long> contarPorSecoesEmLote(ObjectId usuarioId, List<ObjectId> secaoIds) {
        Map<ObjectId, Long> contagens = new HashMap<>();
        if (secaoIds == null || secaoIds.isEmpty()) {
            return contagens;
        }
        collection.aggregate(List.of(
                new Document("$match", new Document("usuarioId", usuarioId)
                        .append("secaoId", new Document("$in", secaoIds))
                        .append("deletedAt", null)),
                new Document("$group", new Document("_id", "$secaoId")
                        .append("total", new Document("$sum", 1)))
        )).forEach(doc -> contagens.put(doc.getObjectId("_id"), ((Number) doc.get("total")).longValue()));
        return contagens;
    }

    public int proximaOrdem(ObjectId usuarioId, ObjectId secaoId) {
        Document doc = collection.find(and(
                eq("usuarioId", usuarioId),
                eq("secaoId", secaoId),
                eq("deletedAt", null)
        )).sort(Sorts.descending("ordem")).first();

        if (doc != null && doc.getInteger("ordem") != null) {
            return doc.getInteger("ordem") + 1;
        }
        return 0;
    }

    /**
     * Busca textual sobre o índice composto {@code idx_notas_usuarioId_deletedAt_text}
     * (D-30). Sempre {@code $text}: nenhum fallback para regex ou varredura da coleção.
     */
    public List<Document> buscaGlobal(ObjectId usuarioId, String termoBusca, int pagina, int tamanho) {
        List<Document> resultados = new ArrayList<>();
        collection.find(and(
                eq("usuarioId", usuarioId),
                eq("deletedAt", null),
                Filters.text(termoBusca, new TextSearchOptions().language("portuguese"))
        ))
        .projection(Projections.fields(
                Projections.include("_id", "usuarioId", "cadernoId", "secaoId", "titulo",
                        CAMPO_CONTEUDO, "atualizadoEm", "criadoEm"),
                Projections.metaTextScore("score")
        ))
        .sort(Sorts.orderBy(
                Sorts.descending("score"),
                Sorts.descending("atualizadoEm"),
                Sorts.descending("_id")
        ))
        .skip(deslocamento(pagina, tamanho))
        .limit(tamanho)
        .into(resultados);

        return resultados;
    }

    public long contarBuscaGlobal(ObjectId usuarioId, String termoBusca) {
        return collection.countDocuments(and(
                eq("usuarioId", usuarioId),
                eq("deletedAt", null),
                Filters.text(termoBusca, new TextSearchOptions().language("portuguese"))
        ));
    }

    public boolean moverParaLixeira(
            ObjectId usuarioId, ObjectId notaId, String loteExclusaoId, Instant agora, Instant expiresAt) {
        return collection.updateOne(
                and(eq("_id", notaId), eq("usuarioId", usuarioId), eq("deletedAt", null)),
                Updates.combine(
                        Updates.set("deletedAt", BsonDates.toBsonDate(agora)),
                        Updates.set("expiresAt", BsonDates.toBsonDate(expiresAt)),
                        Updates.set("loteExclusaoId", loteExclusaoId),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(agora))
                )
        ).getModifiedCount() > 0;
    }

    public long moverPorSecaoParaLixeira(
            ObjectId usuarioId, ObjectId secaoId, String loteExclusaoId, Instant agora, Instant expiresAt) {
        return collection.updateMany(
                and(eq("secaoId", secaoId), eq("usuarioId", usuarioId), eq("deletedAt", null)),
                Updates.combine(
                        Updates.set("deletedAt", BsonDates.toBsonDate(agora)),
                        Updates.set("expiresAt", BsonDates.toBsonDate(expiresAt)),
                        Updates.set("loteExclusaoId", loteExclusaoId),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(agora))
                )
        ).getModifiedCount();
    }

    public long moverPorCadernoParaLixeira(
            ObjectId usuarioId, ObjectId cadernoId, String loteExclusaoId, Instant agora, Instant expiresAt) {
        return collection.updateMany(
                and(eq("cadernoId", cadernoId), eq("usuarioId", usuarioId), eq("deletedAt", null)),
                Updates.combine(
                        Updates.set("deletedAt", BsonDates.toBsonDate(agora)),
                        Updates.set("expiresAt", BsonDates.toBsonDate(expiresAt)),
                        Updates.set("loteExclusaoId", loteExclusaoId),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(agora))
                )
        ).getModifiedCount();
    }

    public boolean restaurar(ObjectId usuarioId, ObjectId notaId) {
        return collection.updateOne(
                and(eq("_id", notaId), eq("usuarioId", usuarioId)),
                Updates.combine(
                        Updates.unset("deletedAt"),
                        Updates.unset("expiresAt"),
                        Updates.unset("loteExclusaoId"),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(Instant.now()))
                )
        ).getModifiedCount() > 0;
    }

    public long restaurarPorLoteECaderno(ObjectId usuarioId, ObjectId cadernoId, String loteExclusaoId) {
        if (loteExclusaoId == null) {
            return 0;
        }
        return collection.updateMany(
                and(
                        eq("cadernoId", cadernoId),
                        eq("usuarioId", usuarioId),
                        eq("loteExclusaoId", loteExclusaoId)
                ),
                Updates.combine(
                        Updates.unset("deletedAt"),
                        Updates.unset("expiresAt"),
                        Updates.unset("loteExclusaoId"),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(Instant.now()))
                )
        ).getModifiedCount();
    }

    public long restaurarPorLoteESecao(ObjectId usuarioId, ObjectId secaoId, String loteExclusaoId) {
        if (loteExclusaoId == null) {
            return 0;
        }
        return collection.updateMany(
                and(
                        eq("secaoId", secaoId),
                        eq("usuarioId", usuarioId),
                        eq("loteExclusaoId", loteExclusaoId)
                ),
                Updates.combine(
                        Updates.unset("deletedAt"),
                        Updates.unset("expiresAt"),
                        Updates.unset("loteExclusaoId"),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(Instant.now()))
                )
        ).getModifiedCount();
    }

    public long contarLixeira(ObjectId usuarioId, Instant agora) {
        return collection.countDocuments(filtroLixeira(usuarioId, agora));
    }

    public List<Nota> listarLixeira(ObjectId usuarioId, Instant agora, int pagina, int tamanho) {
        List<Nota> lista = new ArrayList<>();
        collection.find(filtroLixeira(usuarioId, agora))
                .sort(Sorts.orderBy(Sorts.descending("deletedAt"), Sorts.descending("_id")))
                .skip(deslocamento(pagina, tamanho))
                .limit(tamanho)
                .forEach(doc -> lista.add(paraEntidade(doc)));
        return lista;
    }

    public long esvaziarLixeira(ObjectId usuarioId) {
        return collection.deleteMany(
                and(eq("usuarioId", usuarioId), ne("deletedAt", null))
        ).getDeletedCount();
    }

    /** Purga definitiva por {@code expiresAt} em todos os tenants (D-06b). */
    public long purgarExpirados(Instant agora) {
        return collection.deleteMany(
                and(ne("deletedAt", null), lte("expiresAt", BsonDates.toBsonDate(agora)))
        ).getDeletedCount();
    }

    private static Bson filtroLixeira(ObjectId usuarioId, Instant agora) {
        return and(
                eq("usuarioId", usuarioId),
                ne("deletedAt", null),
                gt("expiresAt", BsonDates.toBsonDate(agora))
        );
    }

    private static int deslocamento(int pagina, int tamanho) {
        return Math.max(0, (pagina - 1) * tamanho);
    }

    public Document paraDocumento(Nota n) {
        Document doc = new Document("_id", n.id() != null ? n.id() : new ObjectId())
                .append("usuarioId", n.usuarioId())
                .append("cadernoId", n.cadernoId())
                .append("secaoId", n.secaoId())
                .append("titulo", n.titulo())
                .append(CAMPO_CONTEUDO, n.conteudoMarkdown() != null ? n.conteudoMarkdown() : "")
                .append("ordem", n.ordem())
                .append("criadoEm", BsonDates.toBsonDate(n.criadoEm()))
                .append("atualizadoEm", BsonDates.toBsonDate(n.atualizadoEm()));

        if (n.topicoOrigemId() != null) {
            doc.append("topicoOrigemId", n.topicoOrigemId());
        }
        if (n.deletedAt() != null) {
            doc.append("deletedAt", BsonDates.toBsonDate(n.deletedAt()));
        }
        if (n.expiresAt() != null) {
            doc.append("expiresAt", BsonDates.toBsonDate(n.expiresAt()));
        }
        if (n.loteExclusaoId() != null) {
            doc.append("loteExclusaoId", n.loteExclusaoId());
        }
        return doc;
    }

    public Nota paraEntidade(Document doc) {
        if (doc == null) {
            return null;
        }
        return new Nota(
                doc.getObjectId("_id"),
                doc.getObjectId("usuarioId"),
                doc.getObjectId("cadernoId"),
                doc.getObjectId("secaoId"),
                doc.getString("titulo"),
                doc.getString(CAMPO_CONTEUDO),
                doc.getInteger("ordem", 0),
                doc.getString("topicoOrigemId"),
                BsonDates.lerInstant(doc, "deletedAt"),
                BsonDates.lerInstant(doc, "expiresAt"),
                doc.getString("loteExclusaoId"),
                BsonDates.lerInstant(doc, "criadoEm"),
                BsonDates.lerInstant(doc, "atualizadoEm")
        );
    }
}
