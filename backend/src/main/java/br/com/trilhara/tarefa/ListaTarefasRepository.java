package br.com.trilhara.tarefa;

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
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import br.com.trilhara.shared.mongo.BsonDates;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ListaTarefasRepository {

    private final MongoCollection<Document> collection;

    public ListaTarefasRepository(
            MongoClient client,
            @ConfigProperty(name = "quarkus.mongodb.database") String database) {
        this.collection = client.getDatabase(database).getCollection("listas_tarefas");
    }

    public void inserir(ListaTarefas lista) {
        Document doc = paraDocumento(lista);
        collection.insertOne(doc);
    }

    public boolean atualizar(ListaTarefas lista) {
        Document doc = paraDocumento(lista);
        return collection.replaceOne(
                and(eq("_id", lista.id()), eq("usuarioId", lista.usuarioId())),
                doc,
                new ReplaceOptions().upsert(false)
        ).getMatchedCount() > 0;
    }

    public Optional<ListaTarefas> buscar(ObjectId usuarioId, ObjectId id) {
        Document doc = collection.find(and(eq("_id", id), eq("usuarioId", usuarioId))).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public Optional<ListaTarefas> buscarQualquerStatus(ObjectId id) {
        Document doc = collection.find(eq("_id", id)).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public List<ListaTarefas> listarAtivas(ObjectId usuarioId) {
        List<ListaTarefas> resultado = new ArrayList<>();
        collection.find(and(eq("usuarioId", usuarioId), eq("deletedAt", null)))
                .sort(Sorts.ascending("ordem", "criadoEm"))
                .forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    public List<ListaTarefas> listarLixeira(ObjectId usuarioId, Instant agora) {
        List<ListaTarefas> resultado = new ArrayList<>();
        collection.find(and(
                eq("usuarioId", usuarioId),
                ne("deletedAt", null),
                gt("expiresAt", BsonDates.toBsonDate(agora))
        )).sort(Sorts.descending("deletedAt"))
          .forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    public boolean moverParaLixeira(ObjectId usuarioId, ObjectId id, String loteExclusaoId, Instant agora, Instant expiresAt) {
        return collection.updateOne(
                and(eq("_id", id), eq("usuarioId", usuarioId), eq("deletedAt", null)),
                Updates.combine(
                        Updates.set("deletedAt", BsonDates.toBsonDate(agora)),
                        Updates.set("expiresAt", BsonDates.toBsonDate(expiresAt)),
                        Updates.set("loteExclusaoId", loteExclusaoId),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(agora))
                )
        ).getModifiedCount() > 0;
    }

    public boolean restaurar(ObjectId usuarioId, ObjectId id) {
        return collection.updateOne(
                and(eq("_id", id), eq("usuarioId", usuarioId)),
                Updates.combine(
                        Updates.unset("deletedAt"),
                        Updates.unset("expiresAt"),
                        Updates.unset("loteExclusaoId"),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(Instant.now()))
                )
        ).getModifiedCount() > 0;
    }

    public boolean removerDefinitivamente(ObjectId usuarioId, ObjectId id) {
        return collection.deleteOne(and(eq("_id", id), eq("usuarioId", usuarioId))).getDeletedCount() > 0;
    }

    public long esvaziarLixeira(ObjectId usuarioId) {
        return collection.deleteMany(and(
                eq("usuarioId", usuarioId),
                ne("deletedAt", null)
        )).getDeletedCount();
    }

    public List<ListaTarefas> listarExpiradas(Instant agora) {
        List<ListaTarefas> resultado = new ArrayList<>();
        collection.find(and(
                ne("deletedAt", null),
                lte("expiresAt", BsonDates.toBsonDate(agora))
        )).forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    public long purgarExpiradas(Instant agora) {
        return collection.deleteMany(and(
                ne("deletedAt", null),
                lte("expiresAt", BsonDates.toBsonDate(agora))
        )).getDeletedCount();
    }

    public int proximaOrdem(ObjectId usuarioId) {
        Document doc = collection.find(and(eq("usuarioId", usuarioId), eq("deletedAt", null)))
                .sort(Sorts.descending("ordem"))
                .first();
        if (doc != null && doc.getInteger("ordem") != null) {
            return doc.getInteger("ordem") + 1;
        }
        return 0;
    }

    public Document paraDocumento(ListaTarefas lista) {
        Document doc = new Document();
        if (lista.id() != null) {
            doc.put("_id", lista.id());
        }
        doc.put("usuarioId", lista.usuarioId());
        doc.put("nome", lista.nome());
        if (lista.cor() != null) {
            doc.put("cor", lista.cor());
        }
        doc.put("ordem", lista.ordem());
        if (lista.deletedAt() != null) {
            doc.put("deletedAt", BsonDates.toBsonDate(lista.deletedAt()));
        }
        if (lista.expiresAt() != null) {
            doc.put("expiresAt", BsonDates.toBsonDate(lista.expiresAt()));
        }
        if (lista.loteExclusaoId() != null) {
            doc.put("loteExclusaoId", lista.loteExclusaoId());
        }
        doc.put("criadoEm", BsonDates.toBsonDate(lista.criadoEm()));
        doc.put("atualizadoEm", BsonDates.toBsonDate(lista.atualizadoEm()));
        return doc;
    }

    public ListaTarefas paraEntidade(Document doc) {
        return new ListaTarefas(
                doc.getObjectId("_id"),
                doc.getObjectId("usuarioId"),
                doc.getString("nome"),
                doc.getString("cor"),
                doc.getInteger("ordem", 0),
                BsonDates.lerInstant(doc, "deletedAt"),
                BsonDates.lerInstant(doc, "expiresAt"),
                doc.getString("loteExclusaoId"),
                BsonDates.lerInstant(doc, "criadoEm"),
                BsonDates.lerInstant(doc, "atualizadoEm")
        );
    }
}
