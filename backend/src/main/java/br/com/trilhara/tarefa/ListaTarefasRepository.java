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

    private static final String CAMPO_USUARIO_ID = "usuarioId";
    private static final String CAMPO_DELETED_AT = "deletedAt";
    private static final String CAMPO_ORDEM = "ordem";
    private static final String CAMPO_CRIADO_EM = "criadoEm";
    private static final String CAMPO_EXPIRES_AT = "expiresAt";
    private static final String CAMPO_LOTE_EXCLUSAO_ID = "loteExclusaoId";
    private static final String CAMPO_ATUALIZADO_EM = "atualizadoEm";

    private final MongoCollection<Document> collection;

    public ListaTarefasRepository(
            MongoClient client,
            @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "trilhara") String database) {
        this.collection = client.getDatabase(database).getCollection("listas_tarefas");
    }

    public void inserir(ListaTarefas lista) {
        Document doc = paraDocumento(lista);
        collection.insertOne(doc);
    }

    public boolean atualizar(ListaTarefas lista) {
        Document doc = paraDocumento(lista);
        return collection.replaceOne(
                and(eq("_id", lista.id()), eq(CAMPO_USUARIO_ID, lista.usuarioId())),
                doc,
                new ReplaceOptions().upsert(false)
        ).getMatchedCount() > 0;
    }

    public Optional<ListaTarefas> buscar(ObjectId usuarioId, ObjectId id) {
        Document doc = collection.find(and(eq("_id", id), eq(CAMPO_USUARIO_ID, usuarioId))).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public Optional<ListaTarefas> buscarQualquerStatus(ObjectId id) {
        Document doc = collection.find(eq("_id", id)).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public List<ListaTarefas> listarAtivas(ObjectId usuarioId) {
        List<ListaTarefas> resultado = new ArrayList<>();
        collection.find(and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_DELETED_AT, null)))
                .sort(Sorts.ascending(CAMPO_ORDEM, CAMPO_CRIADO_EM))
                .forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    public List<ListaTarefas> listarLixeira(ObjectId usuarioId, Instant agora) {
        List<ListaTarefas> resultado = new ArrayList<>();
        collection.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                ne(CAMPO_DELETED_AT, null),
                gt(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(agora))
        )).sort(Sorts.descending(CAMPO_DELETED_AT))
          .forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    public boolean moverParaLixeira(ObjectId usuarioId, ObjectId id, String loteExclusaoId, Instant agora, Instant expiresAt) {
        return collection.updateOne(
                and(eq("_id", id), eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_DELETED_AT, null)),
                Updates.combine(
                        Updates.set(CAMPO_DELETED_AT, BsonDates.toBsonDate(agora)),
                        Updates.set(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(expiresAt)),
                        Updates.set(CAMPO_LOTE_EXCLUSAO_ID, loteExclusaoId),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(agora))
                )
        ).getModifiedCount() > 0;
    }

    public boolean restaurar(ObjectId usuarioId, ObjectId id) {
        return collection.updateOne(
                and(eq("_id", id), eq(CAMPO_USUARIO_ID, usuarioId)),
                Updates.combine(
                        Updates.unset(CAMPO_DELETED_AT),
                        Updates.unset(CAMPO_EXPIRES_AT),
                        Updates.unset(CAMPO_LOTE_EXCLUSAO_ID),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
                )
        ).getModifiedCount() > 0;
    }

    public boolean removerDefinitivamente(ObjectId usuarioId, ObjectId id) {
        return collection.deleteOne(and(eq("_id", id), eq(CAMPO_USUARIO_ID, usuarioId))).getDeletedCount() > 0;
    }

    public long esvaziarLixeira(ObjectId usuarioId) {
        return collection.deleteMany(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                ne(CAMPO_DELETED_AT, null)
        )).getDeletedCount();
    }

    public List<ListaTarefas> listarExpiradas(Instant agora) {
        List<ListaTarefas> resultado = new ArrayList<>();
        collection.find(and(
                ne(CAMPO_DELETED_AT, null),
                lte(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(agora))
        )).forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    public long purgarExpiradas(Instant agora) {
        return collection.deleteMany(and(
                ne(CAMPO_DELETED_AT, null),
                lte(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(agora))
        )).getDeletedCount();
    }

    public int proximaOrdem(ObjectId usuarioId) {
        Document doc = collection.find(and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_DELETED_AT, null)))
                .sort(Sorts.descending(CAMPO_ORDEM))
                .first();
        if (doc != null && doc.getInteger(CAMPO_ORDEM) != null) {
            return doc.getInteger(CAMPO_ORDEM) + 1;
        }
        return 0;
    }

    public Document paraDocumento(ListaTarefas lista) {
        Document doc = new Document();
        if (lista.id() != null) {
            doc.put("_id", lista.id());
        }
        doc.put(CAMPO_USUARIO_ID, lista.usuarioId());
        doc.put("nome", lista.nome());
        if (lista.cor() != null) {
            doc.put("cor", lista.cor());
        }
        doc.put(CAMPO_ORDEM, lista.ordem());
        if (lista.deletedAt() != null) {
            doc.put(CAMPO_DELETED_AT, BsonDates.toBsonDate(lista.deletedAt()));
        }
        if (lista.expiresAt() != null) {
            doc.put(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(lista.expiresAt()));
        }
        if (lista.loteExclusaoId() != null) {
            doc.put(CAMPO_LOTE_EXCLUSAO_ID, lista.loteExclusaoId());
        }
        doc.put(CAMPO_CRIADO_EM, BsonDates.toBsonDate(lista.criadoEm()));
        doc.put(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(lista.atualizadoEm()));
        return doc;
    }

    public ListaTarefas paraEntidade(Document doc) {
        return new ListaTarefas(
                doc.getObjectId("_id"),
                doc.getObjectId(CAMPO_USUARIO_ID),
                doc.getString("nome"),
                doc.getString("cor"),
                doc.getInteger(CAMPO_ORDEM, 0),
                BsonDates.lerInstant(doc, CAMPO_DELETED_AT),
                BsonDates.lerInstant(doc, CAMPO_EXPIRES_AT),
                doc.getString(CAMPO_LOTE_EXCLUSAO_ID),
                BsonDates.lerInstant(doc, CAMPO_CRIADO_EM),
                BsonDates.lerInstant(doc, CAMPO_ATUALIZADO_EM)
        );
    }
}
