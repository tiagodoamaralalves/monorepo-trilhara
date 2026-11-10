package br.com.trilhara.cronograma;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Filters.ne;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import br.com.trilhara.shared.mongo.BsonDates;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BlocoCronogramaRepository {

    private final MongoCollection<Document> collection;

    public BlocoCronogramaRepository(
            MongoClient mongoClient,
            @ConfigProperty(name = "quarkus.mongodb.database") String databaseName) {
        this.collection = mongoClient.getDatabase(databaseName).getCollection("blocos_cronograma");
    }

    public void inserirLote(List<BlocoCronograma> blocos) {
        if (blocos == null || blocos.isEmpty()) {
            return;
        }
        int batchSize = 500;
        List<Document> docs = new ArrayList<>(batchSize);
        for (BlocoCronograma b : blocos) {
            docs.add(paraDocumento(b));
            if (docs.size() == batchSize) {
                collection.insertMany(docs);
                docs.clear();
            }
        }
        if (!docs.isEmpty()) {
            collection.insertMany(docs);
        }
    }

    public long contarPorVersao(ObjectId usuarioId, ObjectId trilhaId, ObjectId cronogramaId, long versaoGeracao) {
        return collection.countDocuments(and(
                eq("usuarioId", usuarioId),
                eq("trilhaId", trilhaId),
                eq("cronogramaId", cronogramaId),
                eq("versaoGeracao", versaoGeracao)
        ));
    }

    public long excluirVersoesDiferentes(ObjectId usuarioId, ObjectId trilhaId, ObjectId cronogramaId, long versaoManter) {
        return collection.deleteMany(and(
                eq("usuarioId", usuarioId),
                eq("trilhaId", trilhaId),
                eq("cronogramaId", cronogramaId),
                ne("versaoGeracao", versaoManter)
        )).getDeletedCount();
    }

    public long excluirPorCronograma(ObjectId usuarioId, ObjectId trilhaId, ObjectId cronogramaId) {
        return collection.deleteMany(and(
                eq("usuarioId", usuarioId),
                eq("trilhaId", trilhaId),
                eq("cronogramaId", cronogramaId)
        )).getDeletedCount();
    }

    public long excluirPorTrilha(ObjectId usuarioId, ObjectId trilhaId) {
        return collection.deleteMany(and(
                eq("usuarioId", usuarioId),
                eq("trilhaId", trilhaId)
        )).getDeletedCount();
    }

    public long excluirPorUsuario(ObjectId usuarioId) {
        return collection.deleteMany(eq("usuarioId", usuarioId)).getDeletedCount();
    }

    public Optional<BlocoCronograma> buscarPorId(ObjectId usuarioId, ObjectId blocoId) {
        Document doc = collection.find(and(
                eq("_id", blocoId),
                eq("usuarioId", usuarioId)
        )).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public boolean atualizarConclusao(ObjectId usuarioId, ObjectId blocoId, boolean concluido, Instant concluidoEm) {
        Bson atualizacao = Updates.combine(
                Updates.set("concluido", concluido),
                Updates.set("concluidoEm", concluidoEm != null ? BsonDates.toBsonDate(concluidoEm) : null),
                Updates.set("atualizadoEm", BsonDates.toBsonDate(Instant.now()))
        );
        return collection.updateOne(
                and(eq("_id", blocoId), eq("usuarioId", usuarioId)),
                atualizacao
        ).getMatchedCount() > 0;
    }

    public List<BlocoCronograma> listarHoje(ObjectId usuarioId, LocalDate hojeLocal, List<ObjectId> cronogramasAtivosIds) {
        if (cronogramasAtivosIds == null || cronogramasAtivosIds.isEmpty()) {
            return List.of();
        }
        Bson filtro = and(
                eq("usuarioId", usuarioId),
                eq("data", hojeLocal.toString()),
                eq("concluido", false),
                in("cronogramaId", cronogramasAtivosIds)
        );
        List<BlocoCronograma> resultado = new ArrayList<>();
        // Note: Sort by ordem ASC, _id ASC (trilhaNome is sorted in service layer after joining trilhas)
        for (Document doc : collection.find(filtro).sort(Sorts.ascending("ordem", "_id"))) {
            resultado.add(paraEntidade(doc));
        }
        return Collections.unmodifiableList(resultado);
    }

    public ResultadoPaginado<BlocoCronograma> listarPaginado(
            ObjectId usuarioId,
            ObjectId trilhaId,
            ObjectId cronogramaId,
            long versaoGeracao,
            Boolean concluido,
            LocalDate dataInicio,
            LocalDate dataFim,
            int pagina,
            int tamanho) {

        List<Bson> filtros = new ArrayList<>();
        filtros.add(eq("usuarioId", usuarioId));
        filtros.add(eq("trilhaId", trilhaId));
        filtros.add(eq("cronogramaId", cronogramaId));
        filtros.add(eq("versaoGeracao", versaoGeracao));

        if (concluido != null) {
            filtros.add(eq("concluido", concluido));
        }
        if (dataInicio != null) {
            filtros.add(gte("data", dataInicio.toString()));
        }
        if (dataFim != null) {
            filtros.add(lte("data", dataFim.toString()));
        }

        Bson filtroComposto = and(filtros);
        long total = collection.countDocuments(filtroComposto);

        int p = Math.max(1, pagina);
        int t = Math.max(1, Math.min(200, tamanho));
        int skip = (p - 1) * t;

        List<BlocoCronograma> itens = new ArrayList<>();
        for (Document doc : collection.find(filtroComposto)
                .sort(Sorts.ascending("data", "ordem", "_id"))
                .skip(skip)
                .limit(t)) {
            itens.add(paraEntidade(doc));
        }

        return new ResultadoPaginado<>(itens, p, t, total);
    }

    public List<BlocoCronograma> listarAtrasadosPendentes(
            ObjectId usuarioId, ObjectId trilhaId, ObjectId cronogramaId, long versaoGeracao, LocalDate dataHoje) {
        Bson filtro = and(
                eq("usuarioId", usuarioId),
                eq("trilhaId", trilhaId),
                eq("cronogramaId", cronogramaId),
                eq("versaoGeracao", versaoGeracao),
                eq("concluido", false),
                lt("data", dataHoje.toString())
        );
        List<BlocoCronograma> resultado = new ArrayList<>();
        for (Document doc : collection.find(filtro).sort(Sorts.ascending("data", "ordem", "_id"))) {
            resultado.add(paraEntidade(doc));
        }
        return Collections.unmodifiableList(resultado);
    }

    public long atualizarSnapshotsDisciplina(
            ObjectId usuarioId, ObjectId trilhaId, ObjectId disciplinaId, String novoNome, String novaCor) {
        return collection.updateMany(
                and(
                        eq("usuarioId", usuarioId),
                        eq("trilhaId", trilhaId),
                        eq("disciplinaId", disciplinaId)
                ),
                Updates.combine(
                        Updates.set("disciplinaNomeSnapshot", novoNome),
                        Updates.set("disciplinaCorSnapshot", novaCor),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(Instant.now()))
                )
        ).getModifiedCount();
    }

    public long excluirBlocosFuturosPendentesDisciplina(
            ObjectId usuarioId, ObjectId trilhaId, ObjectId disciplinaId, LocalDate dataHoje) {
        return collection.deleteMany(and(
                eq("usuarioId", usuarioId),
                eq("trilhaId", trilhaId),
                eq("disciplinaId", disciplinaId),
                eq("concluido", false),
                gte("data", dataHoje.toString())
        )).getDeletedCount();
    }

    public List<BlocoCronograma> listarFuturosPendentesAtivos(
            ObjectId usuarioId, LocalDate dataHoje, List<ObjectId> cronogramasAtivosIds) {
        if (cronogramasAtivosIds == null || cronogramasAtivosIds.isEmpty()) {
            return List.of();
        }
        Bson filtro = and(
                eq("usuarioId", usuarioId),
                eq("concluido", false),
                gte("data", dataHoje.toString()),
                in("cronogramaId", cronogramasAtivosIds)
        );
        List<BlocoCronograma> resultado = new ArrayList<>();
        for (Document doc : collection.find(filtro)) {
            resultado.add(paraEntidade(doc));
        }
        return Collections.unmodifiableList(resultado);
    }

    private Document paraDocumento(BlocoCronograma b) {
        Document doc = new Document();
        if (b.id() != null) {
            doc.put("_id", b.id());
        }
        doc.put("usuarioId", b.usuarioId());
        doc.put("trilhaId", b.trilhaId());
        doc.put("cronogramaId", b.cronogramaId());
        doc.put("versaoGeracao", b.versaoGeracao());
        doc.put("data", b.data() != null ? b.data().toString() : null);
        doc.put("ordem", b.ordem());
        doc.put("disciplinaId", b.disciplinaId());
        doc.put("disciplinaNomeSnapshot", b.disciplinaNomeSnapshot());
        doc.put("disciplinaCorSnapshot", b.disciplinaCorSnapshot());
        doc.put("duracaoMinutos", b.duracaoMinutos());
        doc.put("concluido", b.concluido());
        if (b.concluidoEm() != null) {
            doc.put("concluidoEm", BsonDates.toBsonDate(b.concluidoEm()));
        }
        doc.put("criadoEm", BsonDates.toBsonDate(b.criadoEm() != null ? b.criadoEm() : Instant.now()));
        doc.put("atualizadoEm", BsonDates.toBsonDate(b.atualizadoEm() != null ? b.atualizadoEm() : Instant.now()));
        return doc;
    }

    private BlocoCronograma paraEntidade(Document doc) {
        return new BlocoCronograma(
                doc.getObjectId("_id"),
                doc.getObjectId("usuarioId"),
                doc.getObjectId("trilhaId"),
                doc.getObjectId("cronogramaId"),
                doc.getLong("versaoGeracao") != null ? doc.getLong("versaoGeracao") : 1L,
                BsonDates.toLocalDate(doc.getString("data")),
                doc.getInteger("ordem", 1),
                doc.getObjectId("disciplinaId"),
                doc.getString("disciplinaNomeSnapshot"),
                doc.getString("disciplinaCorSnapshot"),
                doc.getInteger("duracaoMinutos", 60),
                doc.getBoolean("concluido", false),
                BsonDates.toInstant(doc, "concluidoEm"),
                BsonDates.toInstant(doc, "criadoEm"),
                BsonDates.toInstant(doc, "atualizadoEm")
        );
    }

    public record ResultadoPaginado<T>(List<T> itens, int pagina, int tamanho, long total) {
        public int totalPaginas() {
            if (total == 0) return 1;
            return (int) Math.ceil((double) total / tamanho);
        }
    }
}
