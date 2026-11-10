package br.com.trilhara.nota;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Filters.ne;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
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
 * Repositório direto BSON para a coleção normalizada 'notas'.
 * Conforme REQUISITOS-REESTRUTURACAO-DADOS.md §10 e PLANO-IMPLEMENTACAO-REESTRUTURACAO.md §8.2.
 */
@ApplicationScoped
public class NotaRepository {

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

    public Optional<Nota> buscarPorIdSemUsuario(ObjectId notaId) {
        Document doc = collection.find(eq("_id", notaId)).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public List<Nota> listarMetadadosPorSecao(ObjectId usuarioId, ObjectId cadernoId, ObjectId secaoId, int pagina, int tamanho) {
        int limit = Math.max(1, Math.min(100, tamanho));
        int skip = Math.max(0, (pagina - 1) * limit);

        List<Nota> lista = new ArrayList<>();
        collection.find(and(
                eq("usuarioId", usuarioId),
                eq("cadernoId", cadernoId),
                eq("secaoId", secaoId),
                eq("deletedAt", null)
        ))
        .projection(Projections.exclude("conteudoMarkdown"))
        .sort(Sorts.ascending("ordem", "criadoEm"))
        .skip(skip)
        .limit(limit)
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

    public List<Document> buscaGlobal(ObjectId usuarioId, String termoBusca, int pagina, int tamanho) {
        int limit = Math.max(1, Math.min(100, tamanho));
        int skip = Math.max(0, (pagina - 1) * limit);

        List<Document> resultados = new ArrayList<>();
        collection.find(and(
                eq("usuarioId", usuarioId),
                eq("deletedAt", null),
                Filters.text(termoBusca, new TextSearchOptions().language("portuguese"))
        ))
        .projection(Projections.fields(
                Projections.include("_id", "usuarioId", "cadernoId", "secaoId", "titulo", "conteudoMarkdown", "atualizadoEm", "criadoEm"),
                Projections.metaTextScore("score")
        ))
        .sort(Sorts.orderBy(
                Sorts.descending("score"),
                Sorts.descending("atualizadoEm"),
                Sorts.descending("_id")
        ))
        .skip(skip)
        .limit(limit)
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

    public boolean moverParaLixeira(ObjectId usuarioId, ObjectId notaId, String loteExclusaoId, Instant agora, Instant expiresAt) {
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

    public long moverPorSecaoParaLixeira(ObjectId usuarioId, ObjectId secaoId, String loteExclusaoId, Instant agora, Instant expiresAt) {
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

    public long restaurarPorLoteESecao(ObjectId usuarioId, ObjectId secaoId, String loteExclusaoId) {
        if (loteExclusaoId == null) return 0;
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

    public List<Nota> listarLixeira(ObjectId usuarioId, Instant agora) {
        List<Nota> lista = new ArrayList<>();
        collection.find(and(
                eq("usuarioId", usuarioId),
                ne("deletedAt", null),
                gt("expiresAt", BsonDates.toBsonDate(agora))
        )).sort(Sorts.descending("deletedAt"))
          .forEach(doc -> lista.add(paraEntidade(doc)));
        return lista;
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

    public Document paraDocumento(Nota n) {
        Document doc = new Document("_id", n.id() != null ? n.id() : new ObjectId())
                .append("usuarioId", n.usuarioId())
                .append("cadernoId", n.cadernoId())
                .append("secaoId", n.secaoId())
                .append("titulo", n.titulo())
                .append("conteudoMarkdown", n.conteudoMarkdown() != null ? n.conteudoMarkdown() : "")
                .append("ordem", n.ordem())
                .append("criadoEm", BsonDates.toBsonDate(n.criadoEm()))
                .append("atualizadoEm", BsonDates.toBsonDate(n.atualizadoEm()));

        if (n.origemTopicoId() != null) {
            doc.append("origemTopicoId", n.origemTopicoId());
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
        if (doc == null) return null;
        return new Nota(
                doc.getObjectId("_id"),
                doc.getObjectId("usuarioId"),
                doc.getObjectId("cadernoId"),
                doc.getObjectId("secaoId"),
                doc.getString("titulo"),
                doc.getString("conteudoMarkdown"),
                doc.getInteger("ordem", 0),
                doc.getString("origemTopicoId"),
                BsonDates.toInstant(doc, "deletedAt"),
                BsonDates.toInstant(doc, "expiresAt"),
                doc.getString("loteExclusaoId"),
                BsonDates.toInstant(doc, "criadoEm"),
                BsonDates.toInstant(doc, "atualizadoEm")
        );
    }
}
