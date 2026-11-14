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
 * Conforme REQUISITOS-REESTRUTURACAO-DADOS.md §10 e PLANO-IMPLEMENTACAO-REESTRUTURACAO.md §8.2.
 */
@ApplicationScoped
public class SecaoRepository {

    /** Teto normativo de seções por caderno (500 seções/caderno). */
    public static final int LIMITE_LISTAGEM = 500;

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
                and(eq("_id", secao.id()), eq("usuarioId", secao.usuarioId())),
                doc,
                new ReplaceOptions().upsert(false)
        ).getMatchedCount() > 0;
    }

    public Optional<Secao> buscarAtiva(ObjectId usuarioId, ObjectId secaoId) {
        Document doc = collection.find(and(
                eq("_id", secaoId),
                eq("usuarioId", usuarioId),
                eq("deletedAt", null)
        )).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public Optional<Secao> buscarQualquerStatus(ObjectId usuarioId, ObjectId secaoId) {
        Document doc = collection.find(and(
                eq("_id", secaoId),
                eq("usuarioId", usuarioId)
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
                eq("usuarioId", usuarioId),
                eq("cadernoId", cadernoId),
                eq("deletedAt", null)
        )).sort(Sorts.orderBy(Sorts.ascending("ordem"), Sorts.ascending("criadoEm"), Sorts.ascending("_id")))
          .limit(limite)
          .forEach(doc -> lista.add(paraEntidade(doc)));
        return lista;
    }

    public long contarPorCaderno(ObjectId usuarioId, ObjectId cadernoId) {
        return collection.countDocuments(and(
                eq("usuarioId", usuarioId),
                eq("cadernoId", cadernoId),
                eq("deletedAt", null)
        ));
    }

    public int proximaOrdem(ObjectId usuarioId, ObjectId cadernoId) {
        Document doc = collection.find(and(
                eq("usuarioId", usuarioId),
                eq("cadernoId", cadernoId),
                eq("deletedAt", null)
        )).sort(Sorts.descending("ordem")).first();

        if (doc != null && doc.getInteger("ordem") != null) {
            return doc.getInteger("ordem") + 1;
        }
        return 0;
    }

    public void desvincularOrigemDisciplinaPorCaderno(ObjectId usuarioId, ObjectId cadernoId) {
        collection.updateMany(
                and(eq("usuarioId", usuarioId), eq("cadernoId", cadernoId)),
                Updates.combine(
                        Updates.unset("origemDisciplinaId"),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(Instant.now()))
                )
        );
    }

    public void desvincularOrigemDisciplina(ObjectId usuarioId, ObjectId secaoId) {
        collection.updateOne(
                and(eq("usuarioId", usuarioId), eq("_id", secaoId)),
                Updates.combine(
                        Updates.unset("origemDisciplinaId"),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(Instant.now()))
                )
        );
    }

    public boolean moverParaLixeira(ObjectId usuarioId, ObjectId secaoId, String loteExclusaoId, Instant agora, Instant expiresAt) {
        return collection.updateOne(
                and(eq("_id", secaoId), eq("usuarioId", usuarioId), eq("deletedAt", null)),
                Updates.combine(
                        Updates.set("deletedAt", BsonDates.toBsonDate(agora)),
                        Updates.set("expiresAt", BsonDates.toBsonDate(expiresAt)),
                        Updates.set("loteExclusaoId", loteExclusaoId),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(agora))
                )
        ).getModifiedCount() > 0;
    }

    public long moverPorCadernoParaLixeira(ObjectId usuarioId, ObjectId cadernoId, String loteExclusaoId, Instant agora, Instant expiresAt) {
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

    public boolean restaurar(ObjectId usuarioId, ObjectId secaoId) {
        return collection.updateOne(
                and(eq("_id", secaoId), eq("usuarioId", usuarioId)),
                Updates.combine(
                        Updates.unset("deletedAt"),
                        Updates.unset("expiresAt"),
                        Updates.unset("loteExclusaoId"),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(Instant.now()))
                )
        ).getModifiedCount() > 0;
    }

    public long restaurarPorLoteECaderno(ObjectId usuarioId, ObjectId cadernoId, String loteExclusaoId) {
        if (loteExclusaoId == null) return 0;
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

    public List<Secao> listarLixeira(ObjectId usuarioId, Instant agora) {
        List<Secao> lista = new ArrayList<>();
        collection.find(and(
                eq("usuarioId", usuarioId),
                ne("deletedAt", null),
                gt("expiresAt", BsonDates.toBsonDate(agora))
        )).sort(Sorts.orderBy(Sorts.descending("deletedAt"), Sorts.descending("_id")))
          .forEach(doc -> lista.add(paraEntidade(doc)));
        return lista;
    }

    public long contarLixeira(ObjectId usuarioId, Instant agora) {
        return collection.countDocuments(and(
                eq("usuarioId", usuarioId),
                ne("deletedAt", null),
                gt("expiresAt", BsonDates.toBsonDate(agora))
        ));
    }

    public List<Secao> listarLixeira(ObjectId usuarioId, Instant agora, int pagina, int tamanho) {
        List<Secao> lista = new ArrayList<>();
        collection.find(and(
                eq("usuarioId", usuarioId),
                ne("deletedAt", null),
                gt("expiresAt", BsonDates.toBsonDate(agora))
        )).sort(Sorts.orderBy(Sorts.descending("deletedAt"), Sorts.descending("_id")))
          .skip(Math.max(0, (pagina - 1) * tamanho))
          .limit(tamanho)
          .forEach(doc -> lista.add(paraEntidade(doc)));
        return lista;
    }

    /** Seções ativas do caderno — usado no teto de 500 seções. */
    public long contarAtivas(ObjectId usuarioId, ObjectId cadernoId) {
        return collection.countDocuments(and(
                eq("usuarioId", usuarioId),
                eq("cadernoId", cadernoId),
                eq("deletedAt", null)
        ));
    }

    /** Contagem de seções ativas por caderno em uma agregação — evita N+1. */
    public Map<ObjectId, Long> contarPorCadernos(ObjectId usuarioId, List<ObjectId> cadernoIds) {
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

    /** Títulos de seções ativas do caderno — desempate de "Restauradas (2)", "(3)"…. */
    public List<String> titulosAtivosPorCaderno(ObjectId usuarioId, ObjectId cadernoId) {
        List<String> titulos = new ArrayList<>();
        collection.find(and(
                eq("usuarioId", usuarioId),
                eq("cadernoId", cadernoId),
                eq("deletedAt", null)
        )).projection(new Document("titulo", 1))
          .forEach(doc -> {
              String titulo = doc.getString("titulo");
              if (titulo != null) {
                  titulos.add(titulo);
              }
          });
        return titulos;
    }

    public long esvaziarLixeira(ObjectId usuarioId) {
        return collection.deleteMany(
                and(eq("usuarioId", usuarioId), ne("deletedAt", null))
        ).getDeletedCount();
    }

    public long purgarExpirados(Instant agora) {
        return collection.deleteMany(
                and(ne("deletedAt", null), lte("expiresAt", BsonDates.toBsonDate(agora)))
        ).getDeletedCount();
    }

    public Document paraDocumento(Secao s) {
        Document doc = new Document("_id", s.id() != null ? s.id() : new ObjectId())
                .append("usuarioId", s.usuarioId())
                .append("cadernoId", s.cadernoId())
                .append("titulo", s.titulo())
                .append("ordem", s.ordem())
                .append("criadoEm", BsonDates.toBsonDate(s.criadoEm()))
                .append("atualizadoEm", BsonDates.toBsonDate(s.atualizadoEm()));

        if (s.origemDisciplinaId() != null) {
            doc.append("origemDisciplinaId", s.origemDisciplinaId());
        }
        if (s.deletedAt() != null) {
            doc.append("deletedAt", BsonDates.toBsonDate(s.deletedAt()));
        }
        if (s.expiresAt() != null) {
            doc.append("expiresAt", BsonDates.toBsonDate(s.expiresAt()));
        }
        if (s.loteExclusaoId() != null) {
            doc.append("loteExclusaoId", s.loteExclusaoId());
        }
        return doc;
    }

    public Secao paraEntidade(Document doc) {
        if (doc == null) return null;
        return new Secao(
                doc.getObjectId("_id"),
                doc.getObjectId("usuarioId"),
                doc.getObjectId("cadernoId"),
                doc.getString("titulo"),
                doc.getInteger("ordem", 0),
                doc.getObjectId("origemDisciplinaId"),
                BsonDates.lerInstant(doc, "deletedAt"),
                BsonDates.lerInstant(doc, "expiresAt"),
                doc.getString("loteExclusaoId"),
                BsonDates.lerInstant(doc, "criadoEm"),
                BsonDates.lerInstant(doc, "atualizadoEm")
        );
    }
}
