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
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import br.com.trilhara.shared.mongo.BsonDates;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Repositório direto BSON para a coleção normalizada 'secoes'.
 */
@ApplicationScoped
public class SecaoRepository {

    /** Teto normativo de seções por caderno (500 seções/caderno). */
    public static final int LIMITE_LISTAGEM = 500;

    private static final String CAMPO_USUARIO_ID = "usuarioId";
    private static final String CAMPO_DELETED_AT = "deletedAt";
    private static final String CAMPO_CADERNO_ID = "cadernoId";
    private static final String CAMPO_ORDEM = "ordem";
    private static final String CAMPO_CRIADO_EM = "criadoEm";
    private static final String CAMPO_ATUALIZADO_EM = "atualizadoEm";
    private static final String CAMPO_ORIGEM_DISCIPLINA_ID = "origemDisciplinaId";
    private static final String CAMPO_EXPIRES_AT = "expiresAt";
    private static final String CAMPO_LOTE_EXCLUSAO_ID = "loteExclusaoId";
    private static final String CAMPO_TITULO = "titulo";

    private final MongoCollection<Document> collection;

    @Inject
    public SecaoRepository(
            MongoClient mongoClient,
            @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "trilhara") String databaseName) {
        this.collection = mongoClient.getDatabase(databaseName).getCollection("secoes");
    }

    public void inserir(Secao secao) {
        collection.insertOne(paraDocumento(secao));
    }

    public boolean atualizar(Secao secao) {
        Document doc = paraDocumento(secao);
        return collection.replaceOne(
                and(eq("_id", secao.id()), eq(CAMPO_USUARIO_ID, secao.usuarioId())),
                doc,
                new ReplaceOptions().upsert(false)
        ).getMatchedCount() > 0;
    }

    public Optional<Secao> buscarAtiva(ObjectId usuarioId, ObjectId secaoId) {
        Document doc = collection.find(and(
                eq("_id", secaoId),
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_DELETED_AT, null)
        )).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public Optional<Secao> buscarQualquerStatus(ObjectId usuarioId, ObjectId secaoId) {
        Document doc = collection.find(and(
                eq("_id", secaoId),
                eq(CAMPO_USUARIO_ID, usuarioId)
        )).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public List<Secao> listarPorCaderno(ObjectId usuarioId, ObjectId cadernoId) {
        return listarPorCaderno(usuarioId, cadernoId, LIMITE_LISTAGEM);
    }

    /**
 * Seções ativas do caderno, com teto normativo de 500.
 *
 * <p>A ordenação usa {@code ordem} e {@code _id} como desempate estável, para que duas
 * leituras concorrentes não troquem a posição de seções com a mesma ordem.</p>
 */
    public List<Secao> listarPorCaderno(ObjectId usuarioId, ObjectId cadernoId, int limite) {
        List<Secao> lista = new ArrayList<>();
        collection.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_CADERNO_ID, cadernoId),
                eq(CAMPO_DELETED_AT, null)
        )).sort(Sorts.orderBy(Sorts.ascending(CAMPO_ORDEM), Sorts.ascending(CAMPO_CRIADO_EM), Sorts.ascending("_id")))
          .limit(limite)
          .forEach(doc -> lista.add(paraEntidade(doc)));
        return lista;
    }

    public long contarPorCaderno(ObjectId usuarioId, ObjectId cadernoId) {
        return collection.countDocuments(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_CADERNO_ID, cadernoId),
                eq(CAMPO_DELETED_AT, null)
        ));
    }

    public int proximaOrdem(ObjectId usuarioId, ObjectId cadernoId) {
        Document doc = collection.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_CADERNO_ID, cadernoId),
                eq(CAMPO_DELETED_AT, null)
        )).sort(Sorts.descending(CAMPO_ORDEM)).first();

        if (doc != null && doc.getInteger(CAMPO_ORDEM) != null) {
            return doc.getInteger(CAMPO_ORDEM) + 1;
        }
        return 0;
    }

    public void desvincularOrigemDisciplinaPorCaderno(ObjectId usuarioId, ObjectId cadernoId) {
        collection.updateMany(
                and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_CADERNO_ID, cadernoId)),
                Updates.combine(
                        Updates.unset(CAMPO_ORIGEM_DISCIPLINA_ID),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
                )
        );
    }

    public void desvincularOrigemDisciplina(ObjectId usuarioId, ObjectId secaoId) {
        collection.updateOne(
                and(eq(CAMPO_USUARIO_ID, usuarioId), eq("_id", secaoId)),
                Updates.combine(
                        Updates.unset(CAMPO_ORIGEM_DISCIPLINA_ID),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
                )
        );
    }

    public boolean moverParaLixeira(ObjectId usuarioId, ObjectId secaoId, String loteExclusaoId, Instant agora, Instant expiresAt) {
        return collection.updateOne(
                and(eq("_id", secaoId), eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_DELETED_AT, null)),
                Updates.combine(
                        Updates.set(CAMPO_DELETED_AT, BsonDates.toBsonDate(agora)),
                        Updates.set(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(expiresAt)),
                        Updates.set(CAMPO_LOTE_EXCLUSAO_ID, loteExclusaoId),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(agora))
                )
        ).getModifiedCount() > 0;
    }

    public long moverPorCadernoParaLixeira(ObjectId usuarioId, ObjectId cadernoId, String loteExclusaoId, Instant agora, Instant expiresAt) {
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

    public boolean restaurar(ObjectId usuarioId, ObjectId secaoId) {
        return collection.updateOne(
                and(eq("_id", secaoId), eq(CAMPO_USUARIO_ID, usuarioId)),
                Updates.combine(
                        Updates.unset(CAMPO_DELETED_AT),
                        Updates.unset(CAMPO_EXPIRES_AT),
                        Updates.unset(CAMPO_LOTE_EXCLUSAO_ID),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
                )
        ).getModifiedCount() > 0;
    }

    public long restaurarPorLoteECaderno(ObjectId usuarioId, ObjectId cadernoId, String loteExclusaoId) {
        if (loteExclusaoId == null) return 0;
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

    public List<Secao> listarLixeira(ObjectId usuarioId, Instant agora) {
        List<Secao> lista = new ArrayList<>();
        collection.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                ne(CAMPO_DELETED_AT, null),
                gt(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(agora))
        )).sort(Sorts.orderBy(Sorts.descending(CAMPO_DELETED_AT), Sorts.descending("_id")))
          .forEach(doc -> lista.add(paraEntidade(doc)));
        return lista;
    }

    public long contarLixeira(ObjectId usuarioId, Instant agora) {
        return collection.countDocuments(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                ne(CAMPO_DELETED_AT, null),
                gt(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(agora))
        ));
    }

    public List<Secao> listarLixeira(ObjectId usuarioId, Instant agora, int pagina, int tamanho) {
        List<Secao> lista = new ArrayList<>();
        collection.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                ne(CAMPO_DELETED_AT, null),
                gt(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(agora))
        )).sort(Sorts.orderBy(Sorts.descending(CAMPO_DELETED_AT), Sorts.descending("_id")))
          .skip(Math.max(0, (pagina - 1) * tamanho))
          .limit(tamanho)
          .forEach(doc -> lista.add(paraEntidade(doc)));
        return lista;
    }

    /** Seções ativas do caderno — usado no teto de 500 seções. */
    public long contarAtivas(ObjectId usuarioId, ObjectId cadernoId) {
        return collection.countDocuments(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_CADERNO_ID, cadernoId),
                eq(CAMPO_DELETED_AT, null)
        ));
    }

    /** Contagem de seções ativas por caderno em uma agregação — evita N+1. */
    public Map<ObjectId, Long> contarPorCadernos(ObjectId usuarioId, List<ObjectId> cadernoIds) {
        Map<ObjectId, Long> contagens = new HashMap<>();
        if (cadernoIds == null || cadernoIds.isEmpty()) {
            return contagens;
        }
        collection.aggregate(List.of(
                new Document("$match", new Document(CAMPO_USUARIO_ID, usuarioId)
                        .append(CAMPO_CADERNO_ID, new Document("$in", cadernoIds))
                        .append(CAMPO_DELETED_AT, null)),
                new Document("$group", new Document("_id", "$" + CAMPO_CADERNO_ID)
                        .append("total", new Document("$sum", 1)))
        )).forEach(doc -> contagens.put(doc.getObjectId("_id"), ((Number) doc.get("total")).longValue()));
        return contagens;
    }

    /** Títulos de seções ativas do caderno — desempate de "Restauradas (2)", "(3)"…. */
    public List<String> titulosAtivosPorCaderno(ObjectId usuarioId, ObjectId cadernoId) {
        List<String> titulos = new ArrayList<>();
        collection.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_CADERNO_ID, cadernoId),
                eq(CAMPO_DELETED_AT, null)
        )).projection(new Document(CAMPO_TITULO, 1))
          .forEach(doc -> {
              String titulo = doc.getString(CAMPO_TITULO);
              if (titulo != null) {
                  titulos.add(titulo);
              }
          });
        return titulos;
    }

    public long esvaziarLixeira(ObjectId usuarioId) {
        return collection.deleteMany(
                and(eq(CAMPO_USUARIO_ID, usuarioId), ne(CAMPO_DELETED_AT, null))
        ).getDeletedCount();
    }

    public long purgarExpirados(Instant agora) {
        return collection.deleteMany(
                and(ne(CAMPO_DELETED_AT, null), lte(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(agora)))
        ).getDeletedCount();
    }

    public Document paraDocumento(Secao s) {
        Document doc = new Document("_id", s.id() != null ? s.id() : new ObjectId())
                .append(CAMPO_USUARIO_ID, s.usuarioId())
                .append(CAMPO_CADERNO_ID, s.cadernoId())
                .append(CAMPO_TITULO, s.titulo())
                .append(CAMPO_ORDEM, s.ordem())
                .append(CAMPO_CRIADO_EM, BsonDates.toBsonDate(s.criadoEm()))
                .append(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(s.atualizadoEm()));

        if (s.origemDisciplinaId() != null) {
            doc.append(CAMPO_ORIGEM_DISCIPLINA_ID, s.origemDisciplinaId());
        }
        if (s.deletedAt() != null) {
            doc.append(CAMPO_DELETED_AT, BsonDates.toBsonDate(s.deletedAt()));
        }
        if (s.expiresAt() != null) {
            doc.append(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(s.expiresAt()));
        }
        if (s.loteExclusaoId() != null) {
            doc.append(CAMPO_LOTE_EXCLUSAO_ID, s.loteExclusaoId());
        }
        return doc;
    }

    public Secao paraEntidade(Document doc) {
        if (doc == null) return null;
        return new Secao(
                doc.getObjectId("_id"),
                doc.getObjectId(CAMPO_USUARIO_ID),
                doc.getObjectId(CAMPO_CADERNO_ID),
                doc.getString(CAMPO_TITULO),
                doc.getInteger(CAMPO_ORDEM, 0),
                doc.getObjectId(CAMPO_ORIGEM_DISCIPLINA_ID),
                BsonDates.lerInstant(doc, CAMPO_DELETED_AT),
                BsonDates.lerInstant(doc, CAMPO_EXPIRES_AT),
                doc.getString(CAMPO_LOTE_EXCLUSAO_ID),
                BsonDates.lerInstant(doc, CAMPO_CRIADO_EM),
                BsonDates.lerInstant(doc, CAMPO_ATUALIZADO_EM)
        );
    }
}
