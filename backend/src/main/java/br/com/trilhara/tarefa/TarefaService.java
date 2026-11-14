package br.com.trilhara.tarefa;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.bson.types.ObjectId;

import com.mongodb.MongoWriteException;

import br.com.trilhara.push.LembretePushService;
import br.com.trilhara.shared.TimezoneContext;
import br.com.trilhara.tarefa.TarefaMapper.DadosEdicaoTarefa;
import br.com.trilhara.tarefa.dto.AtualizarTarefaRequest;
import br.com.trilhara.tarefa.dto.CriarTarefaRequest;
import br.com.trilhara.tarefa.dto.PaginaTarefasDTO;
import br.com.trilhara.tarefa.dto.TarefaResponse;
import br.com.trilhara.tarefa.dto.TarefasHojeResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class TarefaService {

    public record FiltroTarefas(
            String listaId,
            String data,
            String dataInicio,
            String dataFim,
            Boolean concluida,
            String estado,
            String filtro) {
    }

    private static final String ERRO_TAREFA_NAO_ENCONTRADA_OU_INACESSIVEL =
            "Tarefa não encontrada ou inacessível.";

    private final ListaTarefasRepository listaRepository;
    private final SerieRecorrenteRepository serieRepository;
    private final TarefaRepository tarefaRepository;
    private final RecorrenciaEngine recorrenciaEngine;
    private final TimezoneContext timezoneContext;
    private final LembretePushService lembretePushService;

    public TarefaService(
            ListaTarefasRepository listaRepository,
            SerieRecorrenteRepository serieRepository,
            TarefaRepository tarefaRepository,
            RecorrenciaEngine recorrenciaEngine,
            TimezoneContext timezoneContext,
            LembretePushService lembretePushService) {
        this.listaRepository = listaRepository;
        this.serieRepository = serieRepository;
        this.tarefaRepository = tarefaRepository;
        this.recorrenciaEngine = recorrenciaEngine;
        this.timezoneContext = timezoneContext;
        this.lembretePushService = lembretePushService;
    }

    public TarefaResponse criarTarefa(CriarTarefaRequest request, String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        if (request.titulo() == null || request.titulo().isBlank()) {
            throw new BadRequestException("O título da tarefa é obrigatório.");
        }

        ObjectId listaId = TarefaSupport.validarEResolverListaId(listaRepository, usuarioId, request.listaId());
        LocalDate dataVencimento = TarefaSupport.paraLocalDate(request.dataVencimento());
        TarefaSupport.validarGapDst(
                timezoneContext, dataVencimento, request.horario(), request.fusoAgendamento());
        TarefaSupport.validarPreferenciaLembrete(request.modoLembrete(), request.lembreteMinutosAntes());
        String fusoAgendamento = TarefaSupport.resolverFusoAgendamento(
                timezoneContext, request.fusoAgendamento());

        int ordem = request.ordem() != null ? request.ordem() : tarefaRepository.proximaOrdem(usuarioId, listaId);
        Instant agora = Instant.now();
        Tarefa tarefa = TarefaMapper.construirNovaTarefa(
                usuarioId, listaId, request, dataVencimento, ordem, fusoAgendamento, agora);

        tarefaRepository.inserir(tarefa);
        lembretePushService.agendarOuAtualizarLembrete(
                tarefa,
                request.modoLembrete(),
                request.lembreteMinutosAntes(),
                fusoAgendamento
        );
        return TarefaMapper.paraTarefaResponse(tarefa);
    }

    /**
     * Listagem paginada de tarefas manuais. {@code filtro} aceita apenas
     * {@code TODAS|FUTURO|CONCLUIDAS}; qualquer outro valor é {@code 400 VALIDATION_ERROR}.
     * Nunca retorna blocos de cronograma (§7.4).
     */
    public PaginaTarefasDTO listarTarefas(
            String usuarioIdStr, FiltroTarefas filtro, Integer paginaParam, Integer tamanhoParam) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        LocalDate hojeLocal = timezoneContext.hojeLocal();

        String filtroNome = TarefaSupport.normalizarFiltroListagem(filtro != null ? filtro.filtro() : null);
        var paginacao = TarefaSupport.validarPaginacao(paginaParam, tamanhoParam);

        LocalDate dataFimBusca = TarefaSupport.paraLocalDate(filtro != null ? filtro.dataFim() : null);
        recorrenciaEngine.expandirSeriesDoUsuario(usuarioId, hojeLocal, dataFimBusca);

        var filtroLista = TarefaSupport.resolverFiltroLista(filtro != null ? filtro.listaId() : null);
        LocalDate data = TarefaSupport.paraLocalDate(filtro != null ? filtro.data() : null);
        LocalDate dataInicio = TarefaSupport.paraLocalDate(filtro != null ? filtro.dataInicio() : null);

        var resultado = tarefaRepository.listarPaginado(new TarefaRepository.FiltroPaginacaoTarefas(
                usuarioId,
                filtroLista.listaId(),
                filtroLista.apenasSemLista(),
                data,
                dataInicio,
                dataFimBusca,
                filtro != null ? filtro.concluida() : null,
                filtro != null ? filtro.estado() : null,
                filtroNome,
                hojeLocal,
                paginacao.pagina(),
                paginacao.tamanho()));

        return new PaginaTarefasDTO(
                resultado.itens().stream().map(TarefaMapper::paraTarefaResponse).toList(),
                resultado.pagina(),
                resultado.tamanho(),
                resultado.totalItens(),
                resultado.totalPaginas());
    }

    public TarefaResponse buscarTarefa(String tarefaIdStr, String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        ObjectId tarefaId = TarefaSupport.validarObjectId(tarefaIdStr, TarefaSupport.CAMPO_TAREFA_ID);
        return TarefaMapper.paraTarefaResponse(buscarTarefaAtiva(usuarioId, tarefaId));
    }

    public TarefaResponse atualizarTarefa(
            String tarefaIdStr,
            AtualizarTarefaRequest request,
            String escopoParam,
            String usuarioIdStr,
            String origemOperacaoId) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        ObjectId tarefaId = TarefaSupport.validarObjectId(tarefaIdStr, TarefaSupport.CAMPO_TAREFA_ID);

        Optional<Tarefa> localizada = tarefaRepository.buscar(usuarioId, tarefaId)
                .filter(t -> t.deletedAt() == null);
        if (localizada.isEmpty()) {
            return resolverResultadoIdempotente(usuarioId, origemOperacaoId)
                    .orElseThrow(() -> new NotFoundException(ERRO_TAREFA_NAO_ENCONTRADA_OU_INACESSIVEL));
        }
        Tarefa existente = localizada.get();

        DadosEdicaoTarefa dados = TarefaMapper.extrairDadosEdicao(
                listaRepository, timezoneContext, usuarioId, existente, request);
        TarefaSupport.validarGapDst(
                timezoneContext, dados.dataVencimento(), dados.horario(), dados.fusoAgendamento());

        if (existente.serieId() != null && !existente.desvinculadaDaSerie()) {
            return atualizarTarefaRecorrente(
                    usuarioId, existente, dados, request.escopo(), escopoParam, origemOperacaoId);
        }

        TarefaSupport.proibirEscopoEmTarefaAvulsa(escopoParam);
        return atualizarTarefaManual(usuarioId, existente, dados);
    }

    private TarefaResponse atualizarTarefaRecorrente(
            ObjectId usuarioId,
            Tarefa existente,
            DadosEdicaoTarefa dados,
            String escopoRequest,
            String escopoParam,
            String origemOperacaoId) {
        EscopoRecorrencia escopo = TarefaSupport.resolverEscopoRecorrenciaObrigatorio(
                escopoRequest, escopoParam);

        if (escopo == EscopoRecorrencia.ESTA_E_FUTURAS) {
            String chaveIdempotencia = TarefaSupport.exigirIdempotencyKey(origemOperacaoId);
            return atualizarOcorrenciaEstaEFuturas(usuarioId, existente, dados, chaveIdempotencia);
        }
        if (escopo == EscopoRecorrencia.TODA_A_SERIE) {
            atualizarRegraSeriePorOcorrencia(usuarioId, existente, dados);
        }
        return atualizarOcorrenciaEsta(usuarioId, existente, dados);
    }

    private TarefaResponse atualizarOcorrenciaEstaEFuturas(
            ObjectId usuarioId,
            Tarefa existente,
            DadosEdicaoTarefa dados,
            String origemOperacaoId) {
        LocalDate alvo = existente.dataOriginalOcorrencia();
        SerieRecorrente serieOriginal = serieRepository.buscar(usuarioId, existente.serieId())
                .orElseThrow(() -> new NotFoundException("Série recorrente de origem não encontrada."));

        Optional<SerieRecorrente> jaAplicada = serieRepository
                .buscarPorOrigemOperacaoId(usuarioId, origemOperacaoId);
        if (jaAplicada.isPresent()) {
            return resolverOcorrenciaDaSerieSucessora(usuarioId, jaAplicada.get(), alvo, existente);
        }

        LocalDate corte = alvo.minusDays(1);
        serieRepository.atualizar(TarefaMapper.cortarSerieEmData(serieOriginal, corte));
        tarefaRepository.removerPendentesFuturas(usuarioId, serieOriginal.id(), alvo);

        SerieRecorrente novaSerie = construirNovaSerieSucessora(
                usuarioId, serieOriginal, dados, alvo, origemOperacaoId);

        try {
            serieRepository.inserir(novaSerie);
        } catch (MongoWriteException e) {
            if (e.getError().getCode() != 11000) {
                throw e;
            }
            novaSerie = serieRepository.buscarPorOrigemOperacaoId(usuarioId, origemOperacaoId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Conflito de idempotência sem série correspondente para a operação informada."));
        }

        recorrenciaEngine.expandirSerie(novaSerie, timezoneContext.hojeLocal(), null);

        return tarefaRepository.buscarPorOcorrencia(usuarioId, novaSerie.id(), alvo)
                .map(TarefaMapper::paraTarefaResponse)
                .orElseGet(() -> TarefaMapper.paraTarefaResponse(existente));
    }

    private TarefaResponse resolverOcorrenciaDaSerieSucessora(
            ObjectId usuarioId, SerieRecorrente serieExistente, LocalDate alvo, Tarefa fallback) {
        return tarefaRepository.buscarPorOcorrencia(usuarioId, serieExistente.id(), alvo)
                .map(TarefaMapper::paraTarefaResponse)
                .orElseGet(() -> {
                    recorrenciaEngine.expandirSerie(serieExistente, timezoneContext.hojeLocal(), null);
                    return tarefaRepository.buscarPorOcorrencia(usuarioId, serieExistente.id(), alvo)
                            .map(TarefaMapper::paraTarefaResponse)
                            .orElseGet(() -> TarefaMapper.paraTarefaResponse(fallback));
                });
    }

    private SerieRecorrente construirNovaSerieSucessora(
            ObjectId usuarioId,
            SerieRecorrente serieOriginal,
            DadosEdicaoTarefa dados,
            LocalDate alvo,
            String origemOperacaoId) {
        Instant agora = Instant.now();
        return new SerieRecorrente(
                new ObjectId(),
                usuarioId,
                dados.listaId(),
                dados.titulo(),
                dados.descricao(),
                dados.prioridade(),
                dados.horario(),
                dados.subtarefas(),
                serieOriginal.frequencia(),
                serieOriginal.intervalo(),
                serieOriginal.diasDaSemana(),
                serieOriginal.modoMensal(),
                serieOriginal.diaDoMes(),
                serieOriginal.ordinalSemana(),
                serieOriginal.diaSemanaOrdinal(),
                serieOriginal.tipoTermino(),
                alvo,
                serieOriginal.dataFim(),
                serieOriginal.maxOcorrencias(),
                null,
                0,
                true,
                dados.modoLembrete(),
                dados.lembreteMinutosAntes(),
                dados.fusoAgendamento(),
                origemOperacaoId,
                null,
                null,
                null,
                agora,
                agora
        );
    }

    private void atualizarRegraSeriePorOcorrencia(
            ObjectId usuarioId, Tarefa existente, DadosEdicaoTarefa dados) {
        SerieRecorrente serieOriginal = serieRepository.buscar(usuarioId, existente.serieId()).orElse(null);
        if (serieOriginal != null) {
            SerieRecorrente atualizadaSerie = new SerieRecorrente(
                    serieOriginal.id(), serieOriginal.usuarioId(), dados.listaId(), dados.titulo(), dados.descricao(),
                    dados.prioridade(), dados.horario(), dados.subtarefas(), serieOriginal.frequencia(), serieOriginal.intervalo(),
                    serieOriginal.diasDaSemana(), serieOriginal.modoMensal(), serieOriginal.diaDoMes(),
                    serieOriginal.ordinalSemana(), serieOriginal.diaSemanaOrdinal(), serieOriginal.tipoTermino(),
                    serieOriginal.dataInicio(), serieOriginal.dataFim(), serieOriginal.maxOcorrencias(),
                    serieOriginal.geradoAte(), serieOriginal.totalGeradas(), serieOriginal.ativa(),
                    dados.modoLembrete(), dados.lembreteMinutosAntes(), dados.fusoAgendamento(),
                    serieOriginal.origemOperacaoId(), serieOriginal.deletedAt(),
                    serieOriginal.expiresAt(), serieOriginal.loteExclusaoId(), serieOriginal.criadoEm(), Instant.now()
            );
            serieRepository.atualizar(atualizadaSerie);
        }
    }

    private TarefaResponse atualizarOcorrenciaEsta(
            ObjectId usuarioId, Tarefa existente, DadosEdicaoTarefa dados) {
        Tarefa atualizada = new Tarefa(
                existente.id(),
                usuarioId,
                dados.listaId(),
                existente.serieId(),
                existente.dataOriginalOcorrencia(),
                true,
                dados.titulo(),
                dados.descricao(),
                dados.prioridade(),
                dados.dataVencimento(),
                dados.horario(),
                existente.estado(),
                existente.concluida(),
                existente.concluidaEm(),
                dados.ordem(),
                dados.subtarefas(),
                dados.modoLembrete(),
                dados.lembreteMinutosAntes(),
                dados.fusoAgendamento(),
                existente.deletedAt(),
                existente.expiresAt(),
                existente.loteExclusaoId(),
                existente.criadoEm(),
                Instant.now()
        );

        tarefaRepository.atualizar(atualizada);
        lembretePushService.agendarOuAtualizarLembrete(
                atualizada,
                atualizada.modoLembrete(),
                atualizada.lembreteMinutosAntes(),
                atualizada.fusoAgendamento()
        );
        return TarefaMapper.paraTarefaResponse(atualizada);
    }

    private TarefaResponse atualizarTarefaManual(
            ObjectId usuarioId, Tarefa existente, DadosEdicaoTarefa dados) {
        Tarefa atualizada = new Tarefa(
                existente.id(),
                usuarioId,
                dados.listaId(),
                null,
                null,
                false,
                dados.titulo(),
                dados.descricao(),
                dados.prioridade(),
                dados.dataVencimento(),
                dados.horario(),
                existente.estado(),
                existente.concluida(),
                existente.concluidaEm(),
                dados.ordem(),
                dados.subtarefas(),
                dados.modoLembrete(),
                dados.lembreteMinutosAntes(),
                dados.fusoAgendamento(),
                existente.deletedAt(),
                existente.expiresAt(),
                existente.loteExclusaoId(),
                existente.criadoEm(),
                Instant.now()
        );

        tarefaRepository.atualizar(atualizada);
        lembretePushService.agendarOuAtualizarLembrete(
                atualizada,
                atualizada.modoLembrete(),
                atualizada.lembreteMinutosAntes(),
                atualizada.fusoAgendamento()
        );
        return TarefaMapper.paraTarefaResponse(atualizada);
    }

    public void excluirTarefa(String tarefaIdStr, String escopoParam, String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        ObjectId tarefaId = TarefaSupport.validarObjectId(tarefaIdStr, TarefaSupport.CAMPO_TAREFA_ID);

        Tarefa existente = buscarTarefaAtiva(usuarioId, tarefaId);

        Instant agora = Instant.now();
        Instant expiresAt = agora.plus(30, ChronoUnit.DAYS);
        String loteExclusaoId = UUID.randomUUID().toString();

        if (existente.serieId() != null && !existente.desvinculadaDaSerie()) {
            excluirTarefaRecorrente(usuarioId, tarefaId, existente, escopoParam, loteExclusaoId, agora, expiresAt);
            return;
        }

        TarefaSupport.proibirEscopoEmTarefaAvulsa(escopoParam);
        tarefaRepository.moverParaLixeira(usuarioId, tarefaId, loteExclusaoId, agora, expiresAt);
        lembretePushService.cancelarLembrete(usuarioId, tarefaId);
    }

    private void excluirTarefaRecorrente(
            ObjectId usuarioId,
            ObjectId tarefaId,
            Tarefa existente,
            String escopoParam,
            String loteExclusaoId,
            Instant agora,
            Instant expiresAt) {
        EscopoRecorrencia escopo = TarefaSupport.resolverEscopoRecorrenciaObrigatorio(null, escopoParam);

        if (escopo == EscopoRecorrencia.ESTA_E_FUTURAS) {
            LocalDate alvo = existente.dataOriginalOcorrencia();
            SerieRecorrente serieOriginal = serieRepository.buscar(usuarioId, existente.serieId())
                    .orElseThrow(() -> new NotFoundException("Série recorrente de origem não encontrada."));

            LocalDate corte = alvo.minusDays(1);
            serieRepository.atualizar(TarefaMapper.cortarSerieEmData(serieOriginal, corte));

            tarefaRepository.moverPendentesAPartirParaLixeira(
                    usuarioId, serieOriginal.id(), alvo, loteExclusaoId, agora, expiresAt);
            lembretePushService.cancelarLembretesDaSerie(
                    usuarioId, tarefaRepository.listarIdsPorSerie(usuarioId, serieOriginal.id()));
            return;
        }

        if (escopo == EscopoRecorrencia.TODA_A_SERIE) {
            serieRepository.moverParaLixeira(
                    usuarioId, existente.serieId(), loteExclusaoId, agora, expiresAt);
            tarefaRepository.moverPendentesSerieParaLixeira(
                    usuarioId, existente.serieId(), loteExclusaoId, agora, expiresAt);
            lembretePushService.cancelarLembretesDaSerie(
                    usuarioId, tarefaRepository.listarIdsPorSerie(usuarioId, existente.serieId()));
            return;
        }

        tarefaRepository.moverParaLixeira(usuarioId, tarefaId, loteExclusaoId, agora, expiresAt);
        lembretePushService.cancelarLembrete(usuarioId, tarefaId);
    }

    public TarefaResponse atualizarConclusao(String tarefaIdStr, boolean concluida, String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        ObjectId tarefaId = TarefaSupport.validarObjectId(tarefaIdStr, TarefaSupport.CAMPO_TAREFA_ID);

        Tarefa existente = buscarTarefaAtiva(usuarioId, tarefaId);

        Instant agora = Instant.now();
        String estado = concluida ? "CONCLUIDA" : "PENDENTE";
        Instant concluidaEm = concluida ? agora : null;

        Tarefa atualizada = new Tarefa(
                existente.id(),
                usuarioId,
                existente.listaId(),
                existente.serieId(),
                existente.dataOriginalOcorrencia(),
                existente.desvinculadaDaSerie(),
                existente.titulo(),
                existente.descricao(),
                existente.prioridade(),
                existente.dataVencimento(),
                existente.horario(),
                estado,
                concluida,
                concluidaEm,
                existente.ordem(),
                existente.subtarefas(),
                existente.modoLembrete(),
                existente.lembreteMinutosAntes(),
                existente.fusoAgendamento(),
                existente.deletedAt(),
                existente.expiresAt(),
                existente.loteExclusaoId(),
                existente.criadoEm(),
                agora
        );

        tarefaRepository.atualizar(atualizada);
        if (concluida) {
            lembretePushService.cancelarLembrete(usuarioId, tarefaId);
        } else {
            lembretePushService.agendarOuAtualizarLembrete(
                    atualizada,
                    atualizada.modoLembrete(),
                    atualizada.lembreteMinutosAntes(),
                    atualizada.fusoAgendamento()
            );
        }
        return TarefaMapper.paraTarefaResponse(atualizada);
    }

    public TarefaResponse atualizarSubtarefa(
            String tarefaIdStr,
            String subtarefaIdStr,
            boolean concluida,
            String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        ObjectId tarefaId = TarefaSupport.validarObjectId(tarefaIdStr, TarefaSupport.CAMPO_TAREFA_ID);

        Tarefa existente = buscarTarefaAtiva(usuarioId, tarefaId);
        var novasSubtarefas = TarefaMapper.atualizarSubtarefaNaLista(
                existente.subtarefas(), subtarefaIdStr, concluida);

        Tarefa atualizada = new Tarefa(
                existente.id(),
                usuarioId,
                existente.listaId(),
                existente.serieId(),
                existente.dataOriginalOcorrencia(),
                existente.desvinculadaDaSerie(),
                existente.titulo(),
                existente.descricao(),
                existente.prioridade(),
                existente.dataVencimento(),
                existente.horario(),
                existente.estado(),
                existente.concluida(),
                existente.concluidaEm(),
                existente.ordem(),
                novasSubtarefas,
                existente.modoLembrete(),
                existente.lembreteMinutosAntes(),
                existente.fusoAgendamento(),
                existente.deletedAt(),
                existente.expiresAt(),
                existente.loteExclusaoId(),
                existente.criadoEm(),
                Instant.now()
        );

        tarefaRepository.atualizar(atualizada);
        return TarefaMapper.paraTarefaResponse(atualizada);
    }

    public TarefaResponse pularTarefa(String tarefaIdStr, String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        ObjectId tarefaId = TarefaSupport.validarObjectId(tarefaIdStr, TarefaSupport.CAMPO_TAREFA_ID);

        Tarefa existente = buscarTarefaAtiva(usuarioId, tarefaId);

        Tarefa atualizada = new Tarefa(
                existente.id(),
                usuarioId,
                existente.listaId(),
                existente.serieId(),
                existente.dataOriginalOcorrencia(),
                existente.desvinculadaDaSerie(),
                existente.titulo(),
                existente.descricao(),
                existente.prioridade(),
                existente.dataVencimento(),
                existente.horario(),
                "CANCELADA",
                false,
                null,
                existente.ordem(),
                existente.subtarefas(),
                existente.modoLembrete(),
                existente.lembreteMinutosAntes(),
                existente.fusoAgendamento(),
                existente.deletedAt(),
                existente.expiresAt(),
                existente.loteExclusaoId(),
                existente.criadoEm(),
                Instant.now()
        );

        tarefaRepository.atualizar(atualizada);
        lembretePushService.cancelarLembrete(usuarioId, tarefaId);
        return TarefaMapper.paraTarefaResponse(atualizada);
    }

    public TarefasHojeResponse obterTarefasHoje(String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        LocalDate hojeLocal = timezoneContext.hojeLocal();

        recorrenciaEngine.expandirSeriesDoUsuario(usuarioId, hojeLocal, null);

        return TarefaMapper.montarTarefasHojeResponse(
                hojeLocal,
                tarefaRepository.listarAtrasadas(usuarioId, hojeLocal),
                tarefaRepository.listarHoje(usuarioId, hojeLocal),
                tarefaRepository.listarSemData(usuarioId));
    }

    private Tarefa buscarTarefaAtiva(ObjectId usuarioId, ObjectId tarefaId) {
        return tarefaRepository.buscar(usuarioId, tarefaId)
                .filter(t -> t.deletedAt() == null)
                .orElseThrow(() -> new NotFoundException(ERRO_TAREFA_NAO_ENCONTRADA_OU_INACESSIVEL));
    }

    private Optional<TarefaResponse> resolverResultadoIdempotente(ObjectId usuarioId, String origemOperacaoId) {
        if (origemOperacaoId == null || origemOperacaoId.isBlank()) {
            return Optional.empty();
        }
        return serieRepository.buscarPorOrigemOperacaoId(usuarioId, origemOperacaoId.trim())
                .flatMap(serie -> {
                    recorrenciaEngine.expandirSerie(serie, timezoneContext.hojeLocal(), null);
                    return tarefaRepository.buscarPorOcorrencia(usuarioId, serie.id(), serie.dataInicio());
                })
                .map(TarefaMapper::paraTarefaResponse);
    }
}
