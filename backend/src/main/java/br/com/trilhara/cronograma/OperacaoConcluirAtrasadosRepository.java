package br.com.trilhara.cronograma;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;

import br.com.trilhara.cronograma.dto.ConcluirAtrasadosResponse;
import br.com.trilhara.cronograma.dto.FalhaBlocoDTO;
import br.com.trilhara.shared.mongo.BsonDates;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OperacaoConcluirAtrasadosRepository {

    private final MongoCollection<Document> collection;

    public OperacaoConcluirAtrasadosRepository(
            MongoClient mongoClient,
            @ConfigProperty(name = "quarkus.mongodb.database") String databaseName) {
        this.collection = mongoClient.getDatabase(databaseName).getCollection("operacoes_concluir_atrasados");
    }

    public Optional<ConcluirAtrasadosResponse> buscarPorOperationId(ObjectId usuarioId, UUID operationId) {
        Document doc = collection.find(and(
                eq("_id", operationId.toString()),
                eq("usuarioId", usuarioId)
        )).first();

        if (doc == null) {
            return Optional.empty();
        }

        List<FalhaBlocoDTO> falhas = new ArrayList<>();
        List<Document> falhasDoc = doc.getList("falhas", Document.class);
        if (falhasDoc != null) {
            for (Document fd : falhasDoc) {
                falhas.add(new FalhaBlocoDTO(fd.getString("blocoId"), fd.getString("code")));
            }
        }

        return Optional.of(new ConcluirAtrasadosResponse(
                operationId,
                doc.getInteger("totalProcessados", 0),
                doc.getInteger("totalConcluidos", 0),
                falhas
        ));
    }

    public boolean existeOperacaoEmAndamento(ObjectId usuarioId, ObjectId trilhaId, ObjectId cronogramaId) {
        Document doc = collection.find(and(
                eq("usuarioId", usuarioId),
                eq("trilhaId", trilhaId),
                eq("cronogramaId", cronogramaId),
                eq("status", "EM_ANDAMENTO")
        )).first();
        return doc != null;
    }

    public void registrarInicio(ObjectId usuarioId, ObjectId trilhaId, ObjectId cronogramaId, UUID operationId) {
        Document doc = new Document();
        doc.put("_id", operationId.toString());
        doc.put("usuarioId", usuarioId);
        doc.put("trilhaId", trilhaId);
        doc.put("cronogramaId", cronogramaId);
        doc.put("status", "EM_ANDAMENTO");
        doc.put("totalProcessados", 0);
        doc.put("totalConcluidos", 0);
        doc.put("falhas", List.of());
        doc.put("criadoEm", BsonDates.toBsonDate(Instant.now()));
        doc.put("atualizadoEm", BsonDates.toBsonDate(Instant.now()));

        collection.replaceOne(eq("_id", operationId.toString()), doc, new ReplaceOptions().upsert(true));
    }

    public void registrarConclusao(
            ObjectId usuarioId,
            UUID operationId,
            int totalProcessados,
            int totalConcluidos,
            List<FalhaBlocoDTO> falhas) {

        List<Document> falhasDoc = new ArrayList<>();
        if (falhas != null) {
            for (FalhaBlocoDTO f : falhas) {
                falhasDoc.add(new Document("blocoId", f.blocoId()).append("code", f.code()));
            }
        }

        Document doc = collection.find(and(
                eq("_id", operationId.toString()),
                eq("usuarioId", usuarioId)
        )).first();

        if (doc != null) {
            doc.put("status", "CONCLUIDO");
            doc.put("totalProcessados", totalProcessados);
            doc.put("totalConcluidos", totalConcluidos);
            doc.put("falhas", falhasDoc);
            doc.put("atualizadoEm", BsonDates.toBsonDate(Instant.now()));

            collection.replaceOne(eq("_id", operationId.toString()), doc);
        }
    }
}
