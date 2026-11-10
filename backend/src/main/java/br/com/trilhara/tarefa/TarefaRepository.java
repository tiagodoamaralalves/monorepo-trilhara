package br.com.trilhara.tarefa;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Filters.ne;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.bson.conversions.Bson;
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
public class TarefaRepository {

    private final MongoCollection<Document> collection;

    public TarefaRepository(
            MongoClient client,
            @ConfigProperty(name = "quarkus.mongodb.database") String database) {
        this.collection = client.getDatabase(database).getCollection("tarefas");
    }

    public void inserir(Tarefa tarefa) {
        Document doc = paraDocumento(tarefa);
        collection.insertOne(doc);
    }

    public boolean atualizar(Tarefa tarefa) {
        Document doc = paraDocumento(tarefa);
        return collection.replaceOne(
                and(eq("_id", tarefa.id()), eq("usuarioId", tarefa.usuarioId())),
                doc,
                new ReplaceOptions().upsert(false)
        ).getMatchedCount() > 0;
    }

    public Optional<Tarefa> buscar(ObjectId usuarioId, ObjectId id) {
        Document doc = collection.find(and(eq("_id", id), eq("usuarioId", usuarioId))).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public Optional<Tarefa> buscarPorOcorrencia(ObjectId usuarioId, ObjectId serieId, LocalDate dataOriginalOcorrencia) {
        Document doc = collection.find(and(
                eq("usuarioId", usuarioId),
                eq("serieId", serieId),
                eq("dataOriginalOcorrencia", BsonDates.toIsoString(dataOriginalOcorrencia))
        )).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public List<Tarefa> listar(
            ObjectId usuarioId,
            ObjectId listaId,
            boolean apenasSemLista,
            LocalDate dataVencimento,
            LocalDate dataInicio,
            LocalDate dataFim,
            Boolean concluida,
            String estado,
            String filtro) {
        List<Bson> filtros = new ArrayList<>();
        filtros.add(eq("usuarioId", usuarioId));
        filtros.add(eq("deletedAt", null));

        if (apenasSemLista) {
            filtros.add(eq("listaId", null));
        } else if (listaId != null) {
            filtros.add(eq("listaId", listaId));
        }

        if (dataVencimento != null) {
            filtros.add(eq("dataVencimento", BsonDates.toIsoString(dataVencimento)));
        } else if (dataInicio != null && dataFim != null) {
            filtros.add(gte("dataVencimento", BsonDates.toIsoString(dataInicio)));
            filtros.add(lte("dataVencimento", BsonDates.toIsoString(dataFim)));
        } else if (dataInicio != null) {
            filtros.add(gte("dataVencimento", BsonDates.toIsoString(dataInicio)));
        } else if (dataFim != null) {
            filtros.add(lte("dataVencimento", BsonDates.toIsoString(dataFim)));
        }

        if (concluida != null) {
            filtros.add(eq("concluida", concluida));
        }

        if (estado != null && !estado.isBlank()) {
            filtros.add(eq("estado", estado));
        }

        if (filtro != null) {
            switch (filtro.trim().toLowerCase()) {
                case "concluidas" -> filtros.add(eq("concluida", true));
                case "pendentes" -> filtros.add(eq("estado", "PENDENTE"));
                case "sem_data" -> filtros.add(eq("dataVencimento", null));
            }
        }

        List<Tarefa> resultado = new ArrayList<>();
        collection.find(and(filtros))
                .sort(Sorts.ascending("ordem", "criadoEm"))
                .forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    public List<Tarefa> listarAtrasadas(ObjectId usuarioId, LocalDate hojeLocal) {
        List<Tarefa> resultado = new ArrayList<>();
        collection.find(and(
                eq("usuarioId", usuarioId),
                eq("deletedAt", null),
                eq("estado", "PENDENTE"),
                ne("dataVencimento", null),
                lt("dataVencimento", BsonDates.toIsoString(hojeLocal))
        )).forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    public List<Tarefa> listarHoje(ObjectId usuarioId, LocalDate hojeLocal) {
        List<Tarefa> resultado = new ArrayList<>();
        collection.find(and(
                eq("usuarioId", usuarioId),
                eq("deletedAt", null),
                eq("estado", "PENDENTE"),
                eq("dataVencimento", BsonDates.toIsoString(hojeLocal))
        )).forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    public List<Tarefa> listarSemData(ObjectId usuarioId) {
        List<Tarefa> resultado = new ArrayList<>();
        collection.find(and(
                eq("usuarioId", usuarioId),
                eq("deletedAt", null),
                eq("estado", "PENDENTE"),
                eq("dataVencimento", null)
        )).forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    public List<Tarefa> listarLixeira(ObjectId usuarioId, Instant agora) {
        List<Tarefa> resultado = new ArrayList<>();
        collection.find(and(
                eq("usuarioId", usuarioId),
                ne("deletedAt", null),
                gt("expiresAt", BsonDates.toBsonDate(agora))
        )).sort(Sorts.descending("deletedAt"))
          .forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    public List<Tarefa> listarAtivasComHorarioEPadraoLembrete(ObjectId usuarioId) {
        List<Tarefa> resultado = new ArrayList<>();
        var filtro = and(
                eq("usuarioId", usuarioId),
                eq("deletedAt", null),
                eq("concluida", false),
                eq("usaPadraoGlobalLembrete", true),
                ne("horario", null),
                ne("dataVencimento", null)
        );
        collection.find(filtro).forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    public boolean moverParaLixeira(ObjectId usuarioId, ObjectId id, String loteExclusaoId, Instant agora, Instant expiresAt) {
        return collection.updateOne(
                and(eq("_id", id), eq("usuarioId", usuarioId)),
                Updates.combine(
                        Updates.set("deletedAt", BsonDates.toBsonDate(agora)),
                        Updates.set("expiresAt", BsonDates.toBsonDate(expiresAt)),
                        Updates.set("loteExclusaoId", loteExclusaoId),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(agora))
                )
        ).getModifiedCount() > 0;
    }

    public long moverPorListaParaLixeira(ObjectId usuarioId, ObjectId listaId, String loteExclusaoId, Instant agora, Instant expiresAt) {
        return collection.updateMany(
                and(eq("usuarioId", usuarioId), eq("listaId", listaId), eq("deletedAt", null)),
                Updates.combine(
                        Updates.set("deletedAt", BsonDates.toBsonDate(agora)),
                        Updates.set("expiresAt", BsonDates.toBsonDate(expiresAt)),
                        Updates.set("loteExclusaoId", loteExclusaoId),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(agora))
                )
        ).getModifiedCount();
    }

    public boolean restaurar(ObjectId usuarioId, ObjectId id, ObjectId novaListaId) {
        List<Bson> updates = new ArrayList<>();
        updates.add(Updates.unset("deletedAt"));
        updates.add(Updates.unset("expiresAt"));
        updates.add(Updates.unset("loteExclusaoId"));
        updates.add(Updates.set("atualizadoEm", BsonDates.toBsonDate(Instant.now())));
        if (novaListaId != null) {
            updates.add(Updates.set("listaId", novaListaId));
        } else {
            updates.add(Updates.unset("listaId"));
        }

        return collection.updateOne(
                and(eq("_id", id), eq("usuarioId", usuarioId)),
                Updates.combine(updates)
        ).getModifiedCount() > 0;
    }

    public long restaurarPorLote(ObjectId usuarioId, ObjectId listaId, String loteExclusaoId) {
        return collection.updateMany(
                and(eq("usuarioId", usuarioId), eq("listaId", listaId), eq("loteExclusaoId", loteExclusaoId)),
                Updates.combine(
                        Updates.unset("deletedAt"),
                        Updates.unset("expiresAt"),
                        Updates.unset("loteExclusaoId"),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(Instant.now()))
                )
        ).getModifiedCount();
    }

    public long removerPendentesFuturas(ObjectId usuarioId, ObjectId serieId, LocalDate aPartirDe) {
        return collection.deleteMany(and(
                eq("usuarioId", usuarioId),
                eq("serieId", serieId),
                gte("dataOriginalOcorrencia", BsonDates.toIsoString(aPartirDe)),
                ne("estado", "CONCLUIDA")
        )).getDeletedCount();
    }

    public long moverPendentesSerieParaLixeira(ObjectId usuarioId, ObjectId serieId, String loteExclusaoId, Instant agora, Instant expiresAt) {
        return collection.updateMany(
                and(
                        eq("usuarioId", usuarioId),
                        eq("serieId", serieId),
                        eq("estado", "PENDENTE"),
                        eq("deletedAt", null)
                ),
                Updates.combine(
                        Updates.set("deletedAt", BsonDates.toBsonDate(agora)),
                        Updates.set("expiresAt", BsonDates.toBsonDate(expiresAt)),
                        Updates.set("loteExclusaoId", loteExclusaoId),
                        Updates.set("atualizadoEm", BsonDates.toBsonDate(agora))
                )
        ).getModifiedCount();
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

    public long contarPorLista(ObjectId usuarioId, ObjectId listaId) {
        return collection.countDocuments(and(
                eq("usuarioId", usuarioId),
                eq("listaId", listaId),
                eq("deletedAt", null)
        ));
    }

    public int proximaOrdem(ObjectId usuarioId, ObjectId listaId) {
        Bson filtro = listaId != null
                ? and(eq("usuarioId", usuarioId), eq("listaId", listaId), eq("deletedAt", null))
                : and(eq("usuarioId", usuarioId), eq("listaId", null), eq("deletedAt", null));
        Document doc = collection.find(filtro).sort(Sorts.descending("ordem")).first();
        if (doc != null && doc.getInteger("ordem") != null) {
            return doc.getInteger("ordem") + 1;
        }
        return 0;
    }

    public Document paraDocumento(Tarefa t) {
        Document doc = new Document();
        if (t.id() != null) {
            doc.put("_id", t.id());
        }
        doc.put("usuarioId", t.usuarioId());
        if (t.listaId() != null) {
            doc.put("listaId", t.listaId());
        }
        if (t.serieId() != null) {
            doc.put("serieId", t.serieId());
        }
        if (t.dataOriginalOcorrencia() != null) {
            doc.put("dataOriginalOcorrencia", BsonDates.toIsoString(t.dataOriginalOcorrencia()));
        }
        doc.put("desvinculadaDaSerie", t.desvinculadaDaSerie());
        doc.put("titulo", t.titulo());
        if (t.descricao() != null) {
            doc.put("descricao", t.descricao());
        }
        doc.put("prioridade", t.prioridade().getValor());
        if (t.dataVencimento() != null) {
            doc.put("dataVencimento", BsonDates.toIsoString(t.dataVencimento()));
        }
        if (t.horario() != null) {
            doc.put("horario", t.horario());
        }
        doc.put("estado", t.estado());
        doc.put("concluida", t.concluida());
        doc.put("concluido", t.concluida()); // index compatibility
        if (t.concluidaEm() != null) {
            doc.put("concluidaEm", BsonDates.toBsonDate(t.concluidaEm()));
        }
        doc.put("ordem", t.ordem());

        List<Document> subsDocs = new ArrayList<>();
        if (t.subtarefas() != null) {
            for (Subtarefa sub : t.subtarefas()) {
                subsDocs.add(new Document()
                        .append("id", sub.id())
                        .append("titulo", sub.titulo())
                        .append("concluida", sub.concluida())
                        .append("ordem", sub.ordem()));
            }
        }
        doc.put("subtarefas", subsDocs);

        if (t.lembreteMinutosAntes() != null) {
            doc.put("lembreteMinutosAntes", t.lembreteMinutosAntes());
        }
        doc.put("usaPadraoGlobalLembrete", t.usaPadraoGlobalLembrete());
        if (t.fusoAgendamento() != null) {
            doc.put("fusoAgendamento", t.fusoAgendamento());
        }
        if (t.deletedAt() != null) {
            doc.put("deletedAt", BsonDates.toBsonDate(t.deletedAt()));
        }
        if (t.expiresAt() != null) {
            doc.put("expiresAt", BsonDates.toBsonDate(t.expiresAt()));
        }
        if (t.loteExclusaoId() != null) {
            doc.put("loteExclusaoId", t.loteExclusaoId());
        }
        doc.put("criadoEm", BsonDates.toBsonDate(t.criadoEm()));
        doc.put("atualizadoEm", BsonDates.toBsonDate(t.atualizadoEm()));
        return doc;
    }

    public Tarefa paraEntidade(Document doc) {
        List<Subtarefa> subtarefas = new ArrayList<>();
        List<Document> subsDocs = doc.getList("subtarefas", Document.class);
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

        String estado = doc.getString("estado");
        boolean concluida = Boolean.TRUE.equals(doc.getBoolean("concluida"))
                || Boolean.TRUE.equals(doc.getBoolean("concluido"))
                || "CONCLUIDA".equalsIgnoreCase(estado);
        if (estado == null) {
            estado = concluida ? "CONCLUIDA" : "PENDENTE";
        }

        return new Tarefa(
                doc.getObjectId("_id"),
                doc.getObjectId("usuarioId"),
                doc.getObjectId("listaId"),
                doc.getObjectId("serieId"),
                BsonDates.toLocalDate(doc, "dataOriginalOcorrencia"),
                Boolean.TRUE.equals(doc.getBoolean("desvinculadaDaSerie", false)),
                doc.getString("titulo"),
                doc.getString("descricao") != null ? doc.getString("descricao") : doc.getString("observacoes"),
                PrioridadeTarefa.fromString(doc.getString("prioridade")),
                BsonDates.toLocalDate(doc, "dataVencimento"),
                doc.getString("horario"),
                estado,
                concluida,
                BsonDates.toInstant(doc, "concluidaEm"),
                doc.getInteger("ordem", 0),
                subtarefas,
                doc.getInteger("lembreteMinutosAntes"),
                Boolean.TRUE.equals(doc.getBoolean("usaPadraoGlobalLembrete", false)),
                doc.getString("fusoAgendamento"),
                BsonDates.toInstant(doc, "deletedAt"),
                BsonDates.toInstant(doc, "expiresAt"),
                doc.getString("loteExclusaoId"),
                BsonDates.toInstant(doc, "criadoEm"),
                BsonDates.toInstant(doc, "atualizadoEm")
        );
    }
}
