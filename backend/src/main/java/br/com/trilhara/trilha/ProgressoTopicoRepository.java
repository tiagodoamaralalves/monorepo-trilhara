package br.com.trilhara.trilha;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;

import br.com.trilhara.shared.mongo.BsonDates;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repositório direto BSON para a coleção normalizada 'progresso_topicos'.
 */
@ApplicationScoped
public class ProgressoTopicoRepository {

    private static final String CAMPO_USUARIO_ID = "usuarioId";
    private static final String CAMPO_TRILHA_ID = "trilhaId";
    private static final String CAMPO_TOPICO_ID = "topicoId";
    private static final String CAMPO_DISCIPLINA_ID = "disciplinaId";

    private final MongoCollection<Document> collection;

    public ProgressoTopicoRepository(
            MongoClient client,
            @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "trilhara") String database) {
        this.collection = client.getDatabase(database).getCollection("progresso_topicos");
    }

    public boolean marcarConcluido(ObjectId usuarioId, ObjectId trilhaId, ObjectId disciplinaId, String topicoId) {
        Document filter = new Document(CAMPO_USUARIO_ID, usuarioId)
                .append(CAMPO_TRILHA_ID, trilhaId)
                .append(CAMPO_TOPICO_ID, topicoId);

        Document setOnInsert = new Document("_id", new ObjectId())
                .append(CAMPO_DISCIPLINA_ID, disciplinaId)
                .append("concluidoEm", BsonDates.toBsonDate(Instant.now()));

        Document update = new Document("$setOnInsert", setOnInsert);
        var result = collection.updateOne(filter, update, new UpdateOptions().upsert(true));
        return result.getUpsertedId() != null || result.getMatchedCount() > 0;
    }

    public boolean desmarcarConcluido(ObjectId usuarioId, ObjectId trilhaId, String topicoId) {
        return collection.deleteOne(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_TOPICO_ID, topicoId))).getDeletedCount() > 0;
    }

    public Set<String> listarTopicosConcluidosIds(ObjectId usuarioId, ObjectId trilhaId) {
        Set<String> ids = new HashSet<>();
        collection.find(and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_TRILHA_ID, trilhaId)))
                .projection(Projections.include(CAMPO_TOPICO_ID))
                .forEach(doc -> {
                    String topicoId = doc.getString(CAMPO_TOPICO_ID);
                    if (topicoId != null) {
                        ids.add(topicoId);
                    }
                });
        return ids;
    }

    public List<ProgressoTopico> listarPorTrilha(ObjectId usuarioId, ObjectId trilhaId) {
        List<ProgressoTopico> lista = new ArrayList<>();
        collection.find(and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_TRILHA_ID, trilhaId)))
                .forEach(doc -> lista.add(paraEntidade(doc)));
        return lista;
    }

    public long contarConcluidosPorTrilha(ObjectId usuarioId, ObjectId trilhaId) {
        return collection.countDocuments(and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_TRILHA_ID, trilhaId)));
    }

    public Map<ObjectId, Long> contarConcluidosPorDisciplina(ObjectId usuarioId, ObjectId trilhaId) {
        Map<ObjectId, Long> contadores = new HashMap<>();
        List<org.bson.conversions.Bson> pipeline = List.of(
                Aggregates.match(and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_TRILHA_ID, trilhaId))),
                Aggregates.group("$disciplinaId", Accumulators.sum("total", 1))
        );
        collection.aggregate(pipeline).forEach(doc -> {
            ObjectId disciplinaId = doc.getObjectId("_id");
            if (disciplinaId != null) {
                Number total = (Number) doc.get("total");
                contadores.put(disciplinaId, total != null ? total.longValue() : 0L);
            }
        });
        return contadores;
    }

    public long excluirPorTopicos(ObjectId usuarioId, ObjectId trilhaId, Collection<String> topicoIds) {
        if (topicoIds == null || topicoIds.isEmpty()) {
            return 0;
        }
        return collection.deleteMany(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TRILHA_ID, trilhaId),
                in(CAMPO_TOPICO_ID, topicoIds))).getDeletedCount();
    }

    public long excluirPorDisciplina(ObjectId usuarioId, ObjectId trilhaId, ObjectId disciplinaId) {
        return collection.deleteMany(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TRILHA_ID, trilhaId),
                eq(CAMPO_DISCIPLINA_ID, disciplinaId))).getDeletedCount();
    }

    public long excluirPorTrilha(ObjectId usuarioId, ObjectId trilhaId) {
        return collection.deleteMany(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_TRILHA_ID, trilhaId))).getDeletedCount();
    }

    private ProgressoTopico paraEntidade(Document doc) {
        return new ProgressoTopico(
                doc.getObjectId("_id"),
                doc.getObjectId(CAMPO_USUARIO_ID),
                doc.getObjectId(CAMPO_TRILHA_ID),
                doc.getObjectId(CAMPO_DISCIPLINA_ID),
                doc.getString(CAMPO_TOPICO_ID),
                BsonDates.lerInstant(doc, "concluidoEm"));
    }
}
