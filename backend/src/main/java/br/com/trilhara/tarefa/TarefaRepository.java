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

    private static final String CAMPO_USUARIO_ID = "usuarioId";
    private static final String CAMPO_SERIE_ID = "serieId";
    private static final String CAMPO_DATA_ORIGINAL_OCORRENCIA = "dataOriginalOcorrencia";
    private static final String CAMPO_DELETED_AT = "deletedAt";
    private static final String CAMPO_LISTA_ID = "listaId";
    private static final String CAMPO_DATA_VENCIMENTO = "dataVencimento";
    private static final String CAMPO_CONCLUIDA = "concluida";
    private static final String CAMPO_ESTADO = "estado";
    private static final String ESTADO_PENDENTE = "PENDENTE";
    private static final String ESTADO_CONCLUIDA = "CONCLUIDA";
    private static final String CAMPO_ORDEM = "ordem";
    private static final String CAMPO_CRIADO_EM = "criadoEm";
    private static final String CAMPO_EXPIRES_AT = "expiresAt";
    private static final String CAMPO_MODO_LEMBRETE = "modoLembrete";
    private static final String CAMPO_HORARIO = "horario";
    private static final String CAMPO_CONCLUIDA_EM = "concluidaEm";
    private static final String CAMPO_LOTE_EXCLUSAO_ID = "loteExclusaoId";
    private static final String CAMPO_ATUALIZADO_EM = "atualizadoEm";
    private static final String CAMPO_DESVINCULADA_DA_SERIE = "desvinculadaDaSerie";
    private static final String CAMPO_TITULO = "titulo";
    private static final String CAMPO_DESCRICAO = "descricao";

    private final MongoCollection<Document> collection;

    /** Página de entidades {@link Tarefa}; a conversão para DTO acontece no serviço. */
    public record PaginaTarefas(
            List<Tarefa> itens,
            int pagina,
            int tamanho,
            long totalItens,
            int totalPaginas) {
    }

    /** Filtro da listagem simples de tarefas ativas do usuário. */
    public record FiltroListagemTarefas(
            ObjectId listaId,
            boolean apenasSemLista,
            LocalDate dataVencimento,
            LocalDate dataInicio,
            LocalDate dataFim,
            Boolean concluida,
            String estado,
            String filtro) {
    }

    /**
 * Filtro da listagem paginada de tarefas. {@code hojeLocal} alimenta o filtro
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
            @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "trilhara") String database) {
        this.collection = client.getDatabase(database).getCollection("tarefas");
    }

    public void inserir(Tarefa tarefa) {
        Document doc = paraDocumento(tarefa);
        collection.insertOne(doc);
    }

    public boolean atualizar(Tarefa tarefa) {
        Document doc = paraDocumento(tarefa);
        return collection.replaceOne(
                and(eq("_id", tarefa.id()), eq(CAMPO_USUARIO_ID, tarefa.usuarioId())),
                doc,
                new ReplaceOptions().upsert(false)
        ).getMatchedCount() > 0;
    }

    public Optional<Tarefa> buscar(ObjectId usuarioId, ObjectId id) {
        Document doc = collection.find(and(eq("_id", id), eq(CAMPO_USUARIO_ID, usuarioId))).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public Optional<Tarefa> buscarPorOcorrencia(ObjectId usuarioId, ObjectId serieId, LocalDate dataOriginalOcorrencia) {
        Document doc = collection.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_SERIE_ID, serieId),
                eq(CAMPO_DATA_ORIGINAL_OCORRENCIA, BsonDates.toIso(dataOriginalOcorrencia))
        )).first();
        return Optional.ofNullable(doc).map(this::paraEntidade);
    }

    public List<Tarefa> listar(ObjectId usuarioId, FiltroListagemTarefas filtro) {
        List<Bson> filtros = new ArrayList<>();
        filtros.add(eq(CAMPO_USUARIO_ID, usuarioId));
        filtros.add(eq(CAMPO_DELETED_AT, null));

        adicionarFiltroLista(filtros, filtro.apenasSemLista(), filtro.listaId());
        adicionarFiltrosDataVencimento(filtros, filtro.dataVencimento(), filtro.dataInicio(), filtro.dataFim());

        if (filtro.concluida() != null) {
            filtros.add(eq(CAMPO_CONCLUIDA, filtro.concluida()));
        }

        if (filtro.estado() != null && !filtro.estado().isBlank()) {
            filtros.add(eq(CAMPO_ESTADO, filtro.estado()));
        }

        if (filtro.filtro() != null) {
            switch (filtro.filtro().trim().toLowerCase()) {
                case "concluidas" -> filtros.add(eq(CAMPO_CONCLUIDA, true));
                case "pendentes" -> filtros.add(eq(CAMPO_ESTADO, ESTADO_PENDENTE));
                case "sem_data" -> filtros.add(eq(CAMPO_DATA_VENCIMENTO, null));
                default -> { /* outros filtros ignorados */ }
            }
        }

        List<Tarefa> resultado = new ArrayList<>();
        collection.find(and(filtros))
                .sort(Sorts.ascending(CAMPO_ORDEM, CAMPO_CRIADO_EM))
                .forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    private static void adicionarFiltroLista(List<Bson> filtros, boolean apenasSemLista, ObjectId listaId) {
        if (apenasSemLista) {
            filtros.add(eq(CAMPO_LISTA_ID, null));
        } else if (listaId != null) {
            filtros.add(eq(CAMPO_LISTA_ID, listaId));
        }
    }

    private static void adicionarFiltrosDataVencimento(
            List<Bson> filtros,
            LocalDate dataVencimento,
            LocalDate dataInicio,
            LocalDate dataFim) {
        if (dataVencimento != null) {
            filtros.add(eq(CAMPO_DATA_VENCIMENTO, BsonDates.toIso(dataVencimento)));
            return;
        }
        if (dataInicio != null) {
            filtros.add(gte(CAMPO_DATA_VENCIMENTO, BsonDates.toIso(dataInicio)));
        }
        if (dataFim != null) {
            filtros.add(lte(CAMPO_DATA_VENCIMENTO, BsonDates.toIso(dataFim)));
        }
    }

    public List<Tarefa> listarAtrasadas(ObjectId usuarioId, LocalDate hojeLocal) {
        List<Tarefa> resultado = new ArrayList<>();
        collection.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_DELETED_AT, null),
                eq(CAMPO_ESTADO, ESTADO_PENDENTE),
                ne(CAMPO_DATA_VENCIMENTO, null),
                lt(CAMPO_DATA_VENCIMENTO, BsonDates.toIso(hojeLocal))
        )).forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    public List<Tarefa> listarHoje(ObjectId usuarioId, LocalDate hojeLocal) {
        List<Tarefa> resultado = new ArrayList<>();
        collection.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_DELETED_AT, null),
                eq(CAMPO_ESTADO, ESTADO_PENDENTE),
                eq(CAMPO_DATA_VENCIMENTO, BsonDates.toIso(hojeLocal))
        )).forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    public List<Tarefa> listarSemData(ObjectId usuarioId) {
        List<Tarefa> resultado = new ArrayList<>();
        collection.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_DELETED_AT, null),
                eq(CAMPO_ESTADO, ESTADO_PENDENTE),
                eq(CAMPO_DATA_VENCIMENTO, null)
        )).forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    public List<Tarefa> listarLixeira(ObjectId usuarioId, Instant agora) {
        List<Tarefa> resultado = new ArrayList<>();
        collection.find(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                ne(CAMPO_DELETED_AT, null),
                gt(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(agora))
        )).sort(Sorts.descending(CAMPO_DELETED_AT))
          .forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    public List<Tarefa> listarAtivasComHorarioEPadraoLembrete(ObjectId usuarioId) {
        List<Tarefa> resultado = new ArrayList<>();
        var filtro = and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_DELETED_AT, null),
                eq(CAMPO_CONCLUIDA, false),
                eq(CAMPO_MODO_LEMBRETE, ModoLembrete.HERDAR.name()),
                ne(CAMPO_HORARIO, null),
                ne(CAMPO_DATA_VENCIMENTO, null)
        );
        collection.find(filtro).forEach(doc -> resultado.add(paraEntidade(doc)));
        return resultado;
    }

    /**
 * Consulta paginada de tarefas ativas.
 *
 * <p>Nunca inclui blocos de cronograma: blocos não são tarefas (§7.4). A ordenação é
 * estável e inclui {@code _id} como desempate final. Como {@code prioridade} é persistida
 * como texto e {@code dataVencimento} é opcional, o peso numérico e o desempate
 * "nulos por último" são derivados no pipeline — a fonte de verdade continua sendo o
 * documento, sem campo duplicado.</p>
 */
    public PaginaTarefas listarPaginado(FiltroPaginacaoTarefas filtro) {
        Bson predicado = construirPredicadoPaginacao(filtro);
        long totalItens = collection.countDocuments(predicado);
        int totalPaginas = totalItens > 0 ? (int) Math.ceil((double) totalItens / filtro.tamanho()) : 1;

        boolean somenteConcluidas = Boolean.TRUE.equals(filtro.concluida())
                || ESTADO_CONCLUIDA.equalsIgnoreCase(filtro.estado())
                || "CONCLUIDAS".equalsIgnoreCase(filtro.filtro());

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(predicado));
        if (somenteConcluidas) {
            pipeline.add(Aggregates.sort(Sorts.orderBy(
                    Sorts.descending(CAMPO_CONCLUIDA_EM), Sorts.descending("_id"))));
        } else {
            pipeline.add(Aggregates.addFields(
                    new Field<>("_prioridadePeso", pesoPrioridadeExpressao()),
                    new Field<>("_semData", semDataExpressao())));
            pipeline.add(Aggregates.sort(Sorts.orderBy(
                    Sorts.ascending("_semData"),
                    Sorts.ascending(CAMPO_DATA_VENCIMENTO),
                    Sorts.descending("_prioridadePeso"),
                    Sorts.ascending(CAMPO_ORDEM),
                    Sorts.ascending("_id"))));
        }
        pipeline.add(Aggregates.skip((filtro.pagina() - 1) * filtro.tamanho()));
        pipeline.add(Aggregates.limit(filtro.tamanho()));

        List<Tarefa> itens = new ArrayList<>();
        collection.aggregate(pipeline).forEach(doc -> itens.add(paraEntidade(doc)));

        return new PaginaTarefas(itens, filtro.pagina(), filtro.tamanho(), totalItens, totalPaginas);
    }

    private static Bson construirPredicadoPaginacao(FiltroPaginacaoTarefas filtro) {
        List<Bson> filtros = new ArrayList<>();
        filtros.add(eq(CAMPO_USUARIO_ID, filtro.usuarioId()));
        filtros.add(eq(CAMPO_DELETED_AT, null));

        adicionarFiltroLista(filtros, filtro.apenasSemLista(), filtro.listaId());
        adicionarFiltrosDataVencimento(filtros, filtro.dataVencimento(), filtro.dataInicio(), filtro.dataFim());

        if (filtro.estado() != null) {
            filtros.add(eq(CAMPO_ESTADO, filtro.estado()));
        }
        if (filtro.concluida() != null) {
            filtros.add(eq(CAMPO_CONCLUIDA, filtro.concluida()));
        }

        if ("CONCLUIDAS".equalsIgnoreCase(filtro.filtro())) {
            filtros.add(eq(CAMPO_ESTADO, ESTADO_CONCLUIDA));
        } else if ("TODAS".equalsIgnoreCase(filtro.filtro())) {
            filtros.add(ne(CAMPO_ESTADO, "CANCELADA"));
        } else if ("FUTURO".equalsIgnoreCase(filtro.filtro())) {
            filtros.add(eq(CAMPO_ESTADO, ESTADO_PENDENTE));
            filtros.add(gte(CAMPO_DATA_VENCIMENTO, BsonDates.toIso(filtro.hojeLocal())));
        }

        return and(filtros);
    }

    /** Lixeira paginada de tarefas, com filtro {@code expiresAt > agora}. */
    public PaginaTarefas listarLixeiraPaginada(ObjectId usuarioId, Instant agora, int pagina, int tamanho) {
        Bson predicado = and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                ne(CAMPO_DELETED_AT, null),
                gt(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(agora))
        );

        long totalItens = collection.countDocuments(predicado);
        int totalPaginas = totalItens > 0 ? (int) Math.ceil((double) totalItens / tamanho) : 1;

        List<Tarefa> itens = new ArrayList<>();
        collection.find(predicado)
                .sort(Sorts.orderBy(Sorts.descending(CAMPO_DELETED_AT), Sorts.descending("_id")))
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
                and(eq("_id", id), eq(CAMPO_USUARIO_ID, usuarioId)),
                Updates.combine(
                        Updates.set(CAMPO_DELETED_AT, BsonDates.toBsonDate(agora)),
                        Updates.set(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(expiresAt)),
                        Updates.set(CAMPO_LOTE_EXCLUSAO_ID, loteExclusaoId),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(agora))
                )
        ).getModifiedCount() > 0;
    }

    public long moverPorListaParaLixeira(ObjectId usuarioId, ObjectId listaId, String loteExclusaoId, Instant agora, Instant expiresAt) {
        return collection.updateMany(
                and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_LISTA_ID, listaId), eq(CAMPO_DELETED_AT, null)),
                Updates.combine(
                        Updates.set(CAMPO_DELETED_AT, BsonDates.toBsonDate(agora)),
                        Updates.set(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(expiresAt)),
                        Updates.set(CAMPO_LOTE_EXCLUSAO_ID, loteExclusaoId),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(agora))
                )
        ).getModifiedCount();
    }

    public boolean restaurar(ObjectId usuarioId, ObjectId id, ObjectId novaListaId) {
        List<Bson> updates = new ArrayList<>();
        updates.add(Updates.unset(CAMPO_DELETED_AT));
        updates.add(Updates.unset(CAMPO_EXPIRES_AT));
        updates.add(Updates.unset(CAMPO_LOTE_EXCLUSAO_ID));
        updates.add(Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now())));
        if (novaListaId != null) {
            updates.add(Updates.set(CAMPO_LISTA_ID, novaListaId));
        } else {
            updates.add(Updates.unset(CAMPO_LISTA_ID));
        }

        return collection.updateOne(
                and(eq("_id", id), eq(CAMPO_USUARIO_ID, usuarioId)),
                Updates.combine(updates)
        ).getModifiedCount() > 0;
    }

    public long restaurarPorLote(ObjectId usuarioId, ObjectId listaId, String loteExclusaoId) {
        return collection.updateMany(
                and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_LISTA_ID, listaId), eq(CAMPO_LOTE_EXCLUSAO_ID, loteExclusaoId)),
                Updates.combine(
                        Updates.unset(CAMPO_DELETED_AT),
                        Updates.unset(CAMPO_EXPIRES_AT),
                        Updates.unset(CAMPO_LOTE_EXCLUSAO_ID),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
                )
        ).getModifiedCount();
    }

    public long removerPendentesFuturas(ObjectId usuarioId, ObjectId serieId, LocalDate aPartirDe) {
        return collection.deleteMany(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_SERIE_ID, serieId),
                gte(CAMPO_DATA_ORIGINAL_OCORRENCIA, BsonDates.toIso(aPartirDe)),
                ne(CAMPO_ESTADO, ESTADO_CONCLUIDA),
                ne(CAMPO_DESVINCULADA_DA_SERIE, true)
        )).getDeletedCount();
    }

    /**
 * IDs das tarefas de uma série no tenant (inclusive na lixeira), usado para cancelar os
 * lembretes push das ocorrências em lote. Começa por {@code usuarioId}.
 */
    public List<ObjectId> listarIdsPorSerie(ObjectId usuarioId, ObjectId serieId) {
        List<ObjectId> ids = new ArrayList<>();
        collection.find(and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_SERIE_ID, serieId)))
                .projection(new Document("_id", 1))
                .forEach(doc -> ids.add(doc.getObjectId("_id")));
        return ids;
    }

    public long moverPendentesSerieParaLixeira(ObjectId usuarioId, ObjectId serieId, String loteExclusaoId, Instant agora, Instant expiresAt) {
        return collection.updateMany(
                and(
                        eq(CAMPO_USUARIO_ID, usuarioId),
                        eq(CAMPO_SERIE_ID, serieId),
                        eq(CAMPO_ESTADO, ESTADO_PENDENTE),
                        eq(CAMPO_DELETED_AT, null)
                ),
                Updates.combine(
                        Updates.set(CAMPO_DELETED_AT, BsonDates.toBsonDate(agora)),
                        Updates.set(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(expiresAt)),
                        Updates.set(CAMPO_LOTE_EXCLUSAO_ID, loteExclusaoId),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(agora))
                )
        ).getModifiedCount();
    }

    /**
 * {@code ESTA_E_FUTURAS} por exclusão: move à lixeira as ocorrências
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
                        eq(CAMPO_USUARIO_ID, usuarioId),
                        eq(CAMPO_SERIE_ID, serieId),
                        gte(CAMPO_DATA_ORIGINAL_OCORRENCIA, BsonDates.toIso(aPartirDe)),
                        eq(CAMPO_ESTADO, ESTADO_PENDENTE),
                        eq(CAMPO_DELETED_AT, null)
                ),
                Updates.combine(
                        Updates.set(CAMPO_DELETED_AT, BsonDates.toBsonDate(agora)),
                        Updates.set(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(expiresAt)),
                        Updates.set(CAMPO_LOTE_EXCLUSAO_ID, loteExclusaoId),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(agora))
                )
        ).getModifiedCount();
    }

    public long desvincularLista(ObjectId usuarioId, ObjectId listaId) {
        return collection.updateMany(
                and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_LISTA_ID, listaId)),
                Updates.combine(
                        Updates.unset(CAMPO_LISTA_ID),
                        Updates.set(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(Instant.now()))
                )
        ).getModifiedCount();
    }

    public long esvaziarLixeira(ObjectId usuarioId) {
        return collection.deleteMany(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                ne(CAMPO_DELETED_AT, null)
        )).getDeletedCount();
    }

    public long purgarExpiradas(Instant agora) {
        return collection.deleteMany(and(
                ne(CAMPO_DELETED_AT, null),
                lte(CAMPO_EXPIRES_AT, BsonDates.toBsonDate(agora))
        )).getDeletedCount();
    }

    public boolean removerDefinitivamente(ObjectId usuarioId, ObjectId id) {
        return collection.deleteOne(and(eq("_id", id), eq(CAMPO_USUARIO_ID, usuarioId))).getDeletedCount() > 0;
    }

    public long contarPorLista(ObjectId usuarioId, ObjectId listaId) {
        return collection.countDocuments(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_LISTA_ID, listaId),
                eq(CAMPO_DELETED_AT, null)
        ));
    }

    /** Total de ocorrências materializadas de uma série (inclui lixeira e concluídas). */
    public long contarPorSerie(ObjectId usuarioId, ObjectId serieId) {
        return collection.countDocuments(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_SERIE_ID, serieId)
        ));
    }

    /** Contagem por estado de conclusão para o impacto de exclusão da lista. */
    public long contarPorListaPorConclusao(ObjectId usuarioId, ObjectId listaId, boolean concluida) {
        return collection.countDocuments(and(
                eq(CAMPO_USUARIO_ID, usuarioId),
                eq(CAMPO_LISTA_ID, listaId),
                eq(CAMPO_CONCLUIDA, concluida),
                eq(CAMPO_DELETED_AT, null)
        ));
    }

    public int proximaOrdem(ObjectId usuarioId, ObjectId listaId) {
        Bson filtro = listaId != null
                ? and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_LISTA_ID, listaId), eq(CAMPO_DELETED_AT, null))
                : and(eq(CAMPO_USUARIO_ID, usuarioId), eq(CAMPO_LISTA_ID, null), eq(CAMPO_DELETED_AT, null));
        Document doc = collection.find(filtro).sort(Sorts.descending(CAMPO_ORDEM)).first();
        if (doc != null && doc.getInteger(CAMPO_ORDEM) != null) {
            return doc.getInteger(CAMPO_ORDEM) + 1;
        }
        return 0;
    }

    public Document paraDocumento(Tarefa t) {
        Document doc = new Document();
        colocarSePresente(doc, "_id", t.id());
        doc.put(CAMPO_USUARIO_ID, t.usuarioId());
        colocarSePresente(doc, CAMPO_LISTA_ID, t.listaId());
        colocarSePresente(doc, CAMPO_SERIE_ID, t.serieId());
        colocarSePresente(doc, CAMPO_DATA_ORIGINAL_OCORRENCIA, BsonDates.toIso(t.dataOriginalOcorrencia()));
        doc.put(CAMPO_DESVINCULADA_DA_SERIE, t.desvinculadaDaSerie());
        doc.put(CAMPO_TITULO, t.titulo());
        colocarSePresente(doc, CAMPO_DESCRICAO, t.descricao());
        doc.put("prioridade", t.prioridade().getValor());
        colocarSePresente(doc, CAMPO_DATA_VENCIMENTO, BsonDates.toIso(t.dataVencimento()));
        colocarSePresente(doc, CAMPO_HORARIO, t.horario());
        doc.put(CAMPO_ESTADO, t.estado());
        doc.put(CAMPO_CONCLUIDA, t.concluida());
        doc.put("concluido", t.concluida()); // index compatibility
        colocarSePresente(doc, CAMPO_CONCLUIDA_EM, BsonDates.toBsonDate(t.concluidaEm()));
        doc.put(CAMPO_ORDEM, t.ordem());
        doc.put("subtarefas", converterSubtarefasParaDoc(t.subtarefas()));
        doc.put(CAMPO_MODO_LEMBRETE, t.modoLembrete().name());
        colocarSePresente(doc, "lembreteMinutosAntes", t.lembreteMinutosAntes());
        colocarSePresente(doc, "fusoAgendamento", t.fusoAgendamento());
        colocarSePresente(doc, CAMPO_DELETED_AT, BsonDates.toBsonDate(t.deletedAt()));
        colocarSePresente(doc, CAMPO_EXPIRES_AT, BsonDates.toBsonDate(t.expiresAt()));
        colocarSePresente(doc, CAMPO_LOTE_EXCLUSAO_ID, t.loteExclusaoId());
        doc.put(CAMPO_CRIADO_EM, BsonDates.toBsonDate(t.criadoEm()));
        doc.put(CAMPO_ATUALIZADO_EM, BsonDates.toBsonDate(t.atualizadoEm()));
        return doc;
    }

    private List<Document> converterSubtarefasParaDoc(List<Subtarefa> subtarefas) {
        List<Document> subsDocs = new ArrayList<>();
        if (subtarefas != null) {
            for (Subtarefa sub : subtarefas) {
                subsDocs.add(new Document()
                        .append("id", sub.id())
                        .append(CAMPO_TITULO, sub.titulo())
                        .append(CAMPO_CONCLUIDA, sub.concluida())
                        .append(CAMPO_ORDEM, sub.ordem()));
            }
        }
        return subsDocs;
    }

    private static void colocarSePresente(Document doc, String chave, Object valor) {
        if (valor != null) {
            doc.put(chave, valor);
        }
    }

    public Tarefa paraEntidade(Document doc) {
        List<Subtarefa> subtarefas = new ArrayList<>();
        List<Document> subsDocs = doc.getList("subtarefas", Document.class);
        if (subsDocs != null) {
            for (Document subDoc : subsDocs) {
                subtarefas.add(new Subtarefa(
                        subDoc.getString("id"),
                        subDoc.getString(CAMPO_TITULO),
                        Boolean.TRUE.equals(subDoc.getBoolean(CAMPO_CONCLUIDA)),
                        subDoc.getInteger(CAMPO_ORDEM, 0)
                ));
            }
        }

        String estado = doc.getString(CAMPO_ESTADO);
        boolean concluida = Boolean.TRUE.equals(doc.getBoolean(CAMPO_CONCLUIDA))
                || Boolean.TRUE.equals(doc.getBoolean("concluido"))
                || ESTADO_CONCLUIDA.equalsIgnoreCase(estado);
        if (estado == null) {
            estado = concluida ? ESTADO_CONCLUIDA : ESTADO_PENDENTE;
        }

        return new Tarefa(
                doc.getObjectId("_id"),
                doc.getObjectId(CAMPO_USUARIO_ID),
                doc.getObjectId(CAMPO_LISTA_ID),
                doc.getObjectId(CAMPO_SERIE_ID),
                BsonDates.lerLocalDate(doc, CAMPO_DATA_ORIGINAL_OCORRENCIA),
                Boolean.TRUE.equals(doc.getBoolean(CAMPO_DESVINCULADA_DA_SERIE, false)),
                doc.getString(CAMPO_TITULO),
                doc.getString(doc.getString(CAMPO_DESCRICAO) != null ? CAMPO_DESCRICAO : "observacoes"),
                PrioridadeTarefa.fromString(doc.getString("prioridade")),
                BsonDates.lerLocalDate(doc, CAMPO_DATA_VENCIMENTO),
                doc.getString(CAMPO_HORARIO),
                estado,
                concluida,
                BsonDates.lerInstant(doc, CAMPO_CONCLUIDA_EM),
                doc.getInteger(CAMPO_ORDEM, 0),
                subtarefas,
                ModoLembrete.fromString(doc.getString(CAMPO_MODO_LEMBRETE)),
                doc.getInteger("lembreteMinutosAntes"),
                doc.getString("fusoAgendamento"),
                BsonDates.lerInstant(doc, CAMPO_DELETED_AT),
                BsonDates.lerInstant(doc, CAMPO_EXPIRES_AT),
                doc.getString(CAMPO_LOTE_EXCLUSAO_ID),
                BsonDates.lerInstant(doc, CAMPO_CRIADO_EM),
                BsonDates.lerInstant(doc, CAMPO_ATUALIZADO_EM)
        );
    }
}
