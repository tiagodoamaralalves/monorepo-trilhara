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
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import br.com.trilhara.shared.mongo.BsonDates;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TarefaRepository {

    private final MongoCollection<Document> collection;

    /** Página de entidades {@link Tarefa}; a conversão para DTO acontece no serviço. */
    public record PaginaTarefas(
            List<Tarefa> itens,
            int pagina,
            int tamanho,
            long totalItens,
            int totalPaginas) {
    }

    /**
     * Filtro da listagem paginada de tarefas (D-28). {@code hojeLocal} alimenta o filtro
     * {@code FUTURO} e {@code filtro} aceita exclusivamente {@code TODAS|FUTURO|CONCLUIDAS}.
     */
    public record FiltroPaginacaoTarefas(
            ObjectId usuarioId,
            ObjectId listaId,
            boolean apenasSemLista,
            LocalDate dataVencimento,
            LocalDate dataInicio,
            LocalDate dataFim,
            Boolean concluida,
            String estado,
            String filtro,
            LocalDate hojeLocal,
            int pagina,
            int tamanho) {
    }

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
                eq("dataOriginalOcorrencia", BsonDates.toIso(dataOriginalOcorrencia))
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
            filtros.add(eq("dataVencimento", BsonDates.toIso(dataVencimento)));
        } else if (dataInicio != null && dataFim != null) {
            filtros.add(gte("dataVencimento", BsonDates.toIso(dataInicio)));
            filtros.add(lte("dataVencimento", BsonDates.toIso(dataFim)));
        } else if (dataInicio != null) {
            filtros.add(gte("dataVencimento", BsonDates.toIso(dataInicio)));
        } else if (dataFim != null) {
            filtros.add(lte("dataVencimento", BsonDates.toIso(dataFim)));
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
                lt("dataVencimento", BsonDates.toIso(hojeLocal))
        )).forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    public List<Tarefa> listarHoje(ObjectId usuarioId, LocalDate hojeLocal) {
        List<Tarefa> resultado = new ArrayList<>();
        collection.find(and(
                eq("usuarioId", usuarioId),
                eq("deletedAt", null),
                eq("estado", "PENDENTE"),
                eq("dataVencimento", BsonDates.toIso(hojeLocal))
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
                eq("modoLembrete", ModoLembrete.HERDAR.name()),
                ne("horario", null),
                ne("dataVencimento", null)
        );
        collection.find(filtro).forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    /**
     * Consulta paginada de tarefas ativas (D-28).
     *
     * <p>Nunca inclui blocos de cronograma: blocos não são tarefas (§7.4). A ordenação é
     * estável e inclui {@code _id} como desempate final. Como {@code prioridade} é persistida
     * como texto e {@code dataVencimento} é opcional, o peso numérico e o desempate
     * "nulos por último" são derivados no pipeline — a fonte de verdade continua sendo o
     * documento, sem campo duplicado.</p>
     */
    public PaginaTarefas listarPaginado(FiltroPaginacaoTarefas filtro) {
        List<Bson> filtros = new ArrayList<>();
        filtros.add(eq("usuarioId", filtro.usuarioId()));
        filtros.add(eq("deletedAt", null));

        if (filtro.apenasSemLista()) {
            filtros.add(eq("listaId", null));
        } else if (filtro.listaId() != null) {
            filtros.add(eq("listaId", filtro.listaId()));
        }

        if (filtro.dataVencimento() != null) {
            filtros.add(eq("dataVencimento", BsonDates.toIso(filtro.dataVencimento())));
        } else if (filtro.dataInicio() != null && filtro.dataFim() != null) {
            filtros.add(gte("dataVencimento", BsonDates.toIso(filtro.dataInicio())));
            filtros.add(lte("dataVencimento", BsonDates.toIso(filtro.dataFim())));
        } else if (filtro.dataInicio() != null) {
            filtros.add(gte("dataVencimento", BsonDates.toIso(filtro.dataInicio())));
        } else if (filtro.dataFim() != null) {
            filtros.add(lte("dataVencimento", BsonDates.toIso(filtro.dataFim())));
        }

        if (filtro.estado() != null) {
            filtros.add(eq("estado", filtro.estado()));
        }
        if (filtro.concluida() != null) {
            filtros.add(eq("concluida", filtro.concluida()));
        }

        if ("CONCLUIDAS".equalsIgnoreCase(filtro.filtro())) {
            filtros.add(eq("estado", "CONCLUIDA"));
        }
        if ("TODAS".equalsIgnoreCase(filtro.filtro())) {
            filtros.add(ne("estado", "CANCELADA"));
        }
        if ("FUTURO".equalsIgnoreCase(filtro.filtro())) {
            filtros.add(eq("estado", "PENDENTE"));
            filtros.add(gte("dataVencimento", BsonDates.toIso(filtro.hojeLocal())));
        }

        Bson predicado = and(filtros);
        long totalItens = collection.countDocuments(predicado);
        int totalPaginas = totalItens > 0 ? (int) Math.ceil((double) totalItens / filtro.tamanho()) : 1;

        boolean somenteConcluidas = Boolean.TRUE.equals(filtro.concluida())
                || "CONCLUIDA".equalsIgnoreCase(filtro.estado())
                || "CONCLUIDAS".equalsIgnoreCase(filtro.filtro());

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(predicado));
        if (somenteConcluidas) {
            pipeline.add(Aggregates.sort(Sorts.orderBy(
                    Sorts.descending("concluidaEm"), Sorts.descending("_id"))));
        } else {
            pipeline.add(Aggregates.addFields(
                    new Field<>("_prioridadePeso", pesoPrioridadeExpressao()),
                    new Field<>("_semData", semDataExpressao())));
            pipeline.add(Aggregates.sort(Sorts.orderBy(
                    Sorts.ascending("_semData"),
                    Sorts.ascending("dataVencimento"),
                    Sorts.descending("_prioridadePeso"),
                    Sorts.ascending("ordem"),
                    Sorts.ascending("_id"))));
        }
        pipeline.add(Aggregates.skip((filtro.pagina() - 1) * filtro.tamanho()));
        pipeline.add(Aggregates.limit(filtro.tamanho()));

        List<Tarefa> itens = new ArrayList<>();
        collection.aggregate(pipeline).forEach(doc -> itens.add(paraEntidade(doc)));

        return new PaginaTarefas(itens, filtro.pagina(), filtro.tamanho(), totalItens, totalPaginas);
    }

    /** Lixeira paginada de tarefas (D-28), com filtro {@code expiresAt > agora}. */
    public PaginaTarefas listarLixeiraPaginada(ObjectId usuarioId, Instant agora, int pagina, int tamanho) {
        Bson predicado = and(
                eq("usuarioId", usuarioId),
                ne("deletedAt", null),
                gt("expiresAt", BsonDates.toBsonDate(agora))
        );

        long totalItens = collection.countDocuments(predicado);
        int totalPaginas = totalItens > 0 ? (int) Math.ceil((double) totalItens / tamanho) : 1;

        List<Tarefa> itens = new ArrayList<>();
        collection.find(predicado)
                .sort(Sorts.orderBy(Sorts.descending("deletedAt"), Sorts.descending("_id")))
                .skip((pagina - 1) * tamanho)
                .limit(tamanho)
                .forEach(doc -> itens.add(paraEntidade(doc)));

        return new PaginaTarefas(itens, pagina, tamanho, totalItens, totalPaginas);
    }

    /** {@code ALTA -> 3, MEDIA -> 2, BAIXA -> 1} sem duplicar o dado no documento. */
    private static Document pesoPrioridadeExpressao() {
        List<Document> branches = List.of(
                new Document("case", new Document("$eq", List.of("$prioridade", "ALTA"))).append("then", 3),
                new Document("case", new Document("$eq", List.of("$prioridade", "MEDIA"))).append("then", 2),
                new Document("case", new Document("$eq", List.of("$prioridade", "BAIXA"))).append("then", 1));
        return new Document("$switch", new Document("branches", branches).append("default", 0));
    }

    /** {@code 0} quando há data (ordena antes), {@code 1} quando ausente ({@code NULLS LAST}). */
    private static Document semDataExpressao() {
        return new Document("$cond", List.of(
                new Document("$in", List.of(
                        new Document("$type", "$dataVencimento"),
                        List.of("missing", "null"))),
                1,
                0));
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
                gte("dataOriginalOcorrencia", BsonDates.toIso(aPartirDe)),
                ne("estado", "CONCLUIDA"),
                ne("desvinculadaDaSerie", true)
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

    /**
     * {@code ESTA_E_FUTURAS} por exclusão (D-24.2): move à lixeira as ocorrências
     * <strong>pendentes</strong> com {@code dataOriginalOcorrencia >= aPartirDe}, com o mesmo
     * {@code loteExclusaoId} da série. Concluídas nunca são tocadas.
     */
    public long moverPendentesAPartirParaLixeira(
            ObjectId usuarioId,
            ObjectId serieId,
            LocalDate aPartirDe,
            String loteExclusaoId,
            Instant agora,
            Instant expiresAt) {
        return collection.updateMany(
                and(
                        eq("usuarioId", usuarioId),
                        eq("serieId", serieId),
                        gte("dataOriginalOcorrencia", BsonDates.toIso(aPartirDe)),
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

    public long desvincularLista(ObjectId usuarioId, ObjectId listaId) {
        return collection.updateMany(
                and(eq("usuarioId", usuarioId), eq("listaId", listaId)),
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

    /** Total de ocorrências materializadas de uma série (inclui lixeira e concluídas). */
    public long contarPorSerie(ObjectId usuarioId, ObjectId serieId) {
        return collection.countDocuments(and(
                eq("usuarioId", usuarioId),
                eq("serieId", serieId)
        ));
    }

    /** Contagem por estado de conclusão para o impacto de exclusão da lista (D-33). */
    public long contarPorListaPorConclusao(ObjectId usuarioId, ObjectId listaId, boolean concluida) {
        return collection.countDocuments(and(
                eq("usuarioId", usuarioId),
                eq("listaId", listaId),
                eq("concluida", concluida),
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
            doc.put("dataOriginalOcorrencia", BsonDates.toIso(t.dataOriginalOcorrencia()));
        }
        doc.put("desvinculadaDaSerie", t.desvinculadaDaSerie());
        doc.put("titulo", t.titulo());
        if (t.descricao() != null) {
            doc.put("descricao", t.descricao());
        }
        doc.put("prioridade", t.prioridade().getValor());
        if (t.dataVencimento() != null) {
            doc.put("dataVencimento", BsonDates.toIso(t.dataVencimento()));
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

        doc.put("modoLembrete", t.modoLembrete().name());
        if (t.lembreteMinutosAntes() != null) {
            doc.put("lembreteMinutosAntes", t.lembreteMinutosAntes());
        }
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
                BsonDates.lerLocalDate(doc, "dataOriginalOcorrencia"),
                Boolean.TRUE.equals(doc.getBoolean("desvinculadaDaSerie", false)),
                doc.getString("titulo"),
                doc.getString("descricao") != null ? doc.getString("descricao") : doc.getString("observacoes"),
                PrioridadeTarefa.fromString(doc.getString("prioridade")),
                BsonDates.lerLocalDate(doc, "dataVencimento"),
                doc.getString("horario"),
                estado,
                concluida,
                BsonDates.lerInstant(doc, "concluidaEm"),
                doc.getInteger("ordem", 0),
                subtarefas,
                ModoLembrete.fromString(doc.getString("modoLembrete")),
                doc.getInteger("lembreteMinutosAntes"),
                doc.getString("fusoAgendamento"),
                BsonDates.lerInstant(doc, "deletedAt"),
                BsonDates.lerInstant(doc, "expiresAt"),
                doc.getString("loteExclusaoId"),
                BsonDates.lerInstant(doc, "criadoEm"),
                BsonDates.lerInstant(doc, "atualizadoEm")
        );
    }
}
