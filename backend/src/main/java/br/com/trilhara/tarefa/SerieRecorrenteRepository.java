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
public class SerieRecorrenteRepository {

    private final MongoCollection<Document> collection;

    public SerieRecorrenteRepository(
            MongoClient client,
            @ConfigProperty(name = "quarkus.mongodb.database") String database) {
        this.collection = client.getDatabase(database).getCollection("series_recorrentes");
    }

    public void inserir(SerieRecorrente serie) {
        Document doc = paraDocumento(serie);
        collection.insertOne(doc);
    }

    public boolean atualizar(SerieRecorrente serie) {
        Document doc = paraDocumento(serie);
        return collection.replaceOne(
                and(eq("_id", serie.id()), eq("usuarioId", serie.usuarioId())),
                doc,
                new ReplaceOptions().upsert(false)
        ).getMatchedCount() > 0;
    }

    public Optional<SerieRecorrente> buscar(ObjectId usuarioId, ObjectId id) {
        Document doc = collection.find(and(eq("_id", id), eq("usuarioId", usuarioId))).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public Optional<SerieRecorrente> buscarPorOperationId(ObjectId usuarioId, String operationId) {
        if (operationId == null || operationId.isBlank()) {
            return Optional.empty();
        }
        Document doc = collection.find(and(eq("usuarioId", usuarioId), eq("operationId", operationId))).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public List<SerieRecorrente> listarAtivas(ObjectId usuarioId) {
        List<SerieRecorrente> resultado = new ArrayList<>();
        collection.find(and(
                eq("usuarioId", usuarioId),
                eq("deletedAt", null),
                eq("ativa", true)
        )).sort(Sorts.descending("criadoEm"))
          .forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    public List<SerieRecorrente> listarTodas(ObjectId usuarioId) {
        List<SerieRecorrente> resultado = new ArrayList<>();
        collection.find(and(eq("usuarioId", usuarioId), eq("deletedAt", null)))
                .sort(Sorts.descending("criadoEm"))
                .forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    public List<SerieRecorrente> listarLixeira(ObjectId usuarioId, Instant agora) {
        List<SerieRecorrente> resultado = new ArrayList<>();
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
                and(eq("_id", id), eq("usuarioId", usuarioId)),
                Updates.combine(
                        Updates.set("ativa", false),
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

    public long desvincularLista(ObjectId listaId) {
        return collection.updateMany(
                eq("listaId", listaId),
                Updates.combine(
                        Updates.unset("listaId"),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(Instant.now()))
                )
        ).getModifiedCount();
    }

    public long esvaziarLixeira(ObjectId usuarioId) {
        return collection.deleteMany(and(
                eq("usuarioId", usuarioId),
                ne("deletedAt", null)
        )).getDeletedCount();
    }

    public long purgarExpiradas(Instant agora) {
        return collection.deleteMany(and(
                ne("deletedAt", null),
                lte("expiresAt", BsonDates.toBsonDate(agora))
        )).getDeletedCount();
    }

    public boolean removerDefinitivamente(ObjectId usuarioId, ObjectId id) {
        return collection.deleteOne(and(eq("_id", id), eq("usuarioId", usuarioId))).getDeletedCount() > 0;
    }

    public Document paraDocumento(SerieRecorrente s) {
        Document doc = new Document();
        if (s.id() != null) {
            doc.put("_id", s.id());
        }
        doc.put("usuarioId", s.usuarioId());
        if (s.listaId() != null) {
            doc.put("listaId", s.listaId());
        }
        doc.put("titulo", s.titulo());
        if (s.descricao() != null) {
            doc.put("descricao", s.descricao());
        }
        doc.put("prioridade", s.prioridade().getValor());
        if (s.horario() != null) {
            doc.put("horario", s.horario());
        }

        List<Document> subtarefasDocs = new ArrayList<>();
        if (s.subtarefasModelo() != null) {
            for (Subtarefa sub : s.subtarefasModelo()) {
                subtarefasDocs.add(new Document()
                        .append("id", sub.id())
                        .append("titulo", sub.titulo())
                        .append("concluida", sub.concluida())
                        .append("ordem", sub.ordem()));
            }
        }
        doc.put("subtarefasModelo", subtarefasDocs);

        doc.put("frequencia", s.frequencia());
        doc.put("intervalo", s.intervalo());
        doc.put("diasDaSemana", s.diasDaSemana());
        if (s.modoMensal() != null) {
            doc.put("modoMensal", s.modoMensal());
        }
        if (s.diaDoMes() != null) {
            doc.put("diaDoMes", s.diaDoMes());
        }
        if (s.ordinalSemana() != null) {
            doc.put("ordinalSemana", s.ordinalSemana());
        }
        if (s.diaSemanaOrdinal() != null) {
            doc.put("diaSemanaOrdinal", s.diaSemanaOrdinal());
        }
        doc.put("tipoTermino", s.tipoTermino());
        doc.put("dataInicio", BsonDates.toIso(s.dataInicio()));
        if (s.dataFim() != null) {
            doc.put("dataFim", BsonDates.toIso(s.dataFim()));
        }
        if (s.maxOcorrencias() != null) {
            doc.put("maxOcorrencias", s.maxOcorrencias());
        }
        if (s.geradoAte() != null) {
            doc.put("geradoAte", BsonDates.toIso(s.geradoAte()));
        }
        doc.put("totalGeradas", s.totalGeradas());
        doc.put("ativa", s.ativa());

        if (s.lembreteMinutosAntes() != null) {
            doc.put("lembreteMinutosAntes", s.lembreteMinutosAntes());
        }
        doc.put("usaPadraoGlobalLembrete", s.usaPadraoGlobalLembrete());
        if (s.fusoAgendamento() != null) {
            doc.put("fusoAgendamento", s.fusoAgendamento());
        }
        if (s.operationId() != null) {
            doc.put("operationId", s.operationId());
        }
        if (s.deletedAt() != null) {
            doc.put("deletedAt", BsonDates.toBsonDate(s.deletedAt()));
        }
        if (s.expiresAt() != null) {
            doc.put("expiresAt", BsonDates.toBsonDate(s.expiresAt()));
        }
        if (s.loteExclusaoId() != null) {
            doc.put("loteExclusaoId", s.loteExclusaoId());
        }
        doc.put("criadoEm", BsonDates.toBsonDate(s.criadoEm()));
        doc.put("atualizadoEm", BsonDates.toBsonDate(s.atualizadoEm()));
        return doc;
    }

    public SerieRecorrente paraEntidade(Document doc) {
        List<Subtarefa> subtarefas = new ArrayList<>();
        List<Document> subsDocs = doc.getList("subtarefasModelo", Document.class);
        if (subsDocs != null) {
            for (Document subDoc : subsDocs) {
                subtarefas.add(new Subtarefa(
                        subDoc.getString("id"),
                        subDoc.getString("titulo"),
                        Boolean.TRUE.equals(subDoc.getBoolean("concluida")),
                        subDoc.getInteger("ordem", 0)
                ));
            }
        }

        List<Integer> dias = doc.getList("diasDaSemana", Integer.class);

        return new SerieRecorrente(
                doc.getObjectId("_id"),
                doc.getObjectId("usuarioId"),
                doc.getObjectId("listaId"),
                doc.getString("titulo"),
                doc.getString("descricao"),
                PrioridadeTarefa.fromString(doc.getString("prioridade")),
                doc.getString("horario"),
                subtarefas,
                doc.getString("frequencia"),
                doc.getInteger("intervalo", 1),
                dias != null ? dias : List.of(),
                doc.getString("modoMensal"),
                doc.getInteger("diaDoMes"),
                doc.getInteger("ordinalSemana"),
                doc.getInteger("diaSemanaOrdinal"),
                doc.getString("tipoTermino"),
                BsonDates.lerLocalDate(doc, "dataInicio"),
                BsonDates.lerLocalDate(doc, "dataFim"),
                doc.getInteger("maxOcorrencias"),
                BsonDates.lerLocalDate(doc, "geradoAte"),
                doc.getInteger("totalGeradas", 0),
                Boolean.TRUE.equals(doc.getBoolean("ativa", true)),
                doc.getInteger("lembreteMinutosAntes"),
                Boolean.TRUE.equals(doc.getBoolean("usaPadraoGlobalLembrete", false)),
                doc.getString("fusoAgendamento"),
                doc.getString("operationId"),
                BsonDates.lerInstant(doc, "deletedAt"),
                BsonDates.lerInstant(doc, "expiresAt"),
                doc.getString("loteExclusaoId"),
                BsonDates.lerInstant(doc, "criadoEm"),
                BsonDates.lerInstant(doc, "atualizadoEm")
        );
    }
}
