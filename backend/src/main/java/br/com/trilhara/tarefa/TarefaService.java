package br.com.trilhara.tarefa;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bson.types.ObjectId;

import br.com.trilhara.push.InvalidLocalDatetimeException;
import br.com.trilhara.push.LembretePushCalculator;
import br.com.trilhara.push.LembretePushService;
import br.com.trilhara.shared.TimezoneContext;
import br.com.trilhara.shared.mongo.BsonDates;
import br.com.trilhara.tarefa.RegraRecorrenciaValidator.ParametrosRegraRecorrencia;
import br.com.trilhara.tarefa.dto.AtualizarListaTarefasRequest;
import br.com.trilhara.tarefa.dto.AtualizarSerieRecorrenteRequest;
import br.com.trilhara.tarefa.dto.AtualizarTarefaRequest;
import br.com.trilhara.tarefa.dto.CriarListaTarefasRequest;
import br.com.trilhara.tarefa.dto.CriarSerieRecorrenteRequest;
import br.com.trilhara.tarefa.dto.CriarTarefaRequest;
import br.com.trilhara.tarefa.dto.ExclusaoListaResponse;
import br.com.trilhara.tarefa.dto.LixeiraTarefasResponse;
import br.com.trilhara.tarefa.dto.ListaTarefasResponse;
import br.com.trilhara.tarefa.dto.SerieRecorrenteResponse;
import br.com.trilhara.tarefa.dto.SubtarefaDTO;
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

    private record DadosEdicaoTarefa(
            ObjectId listaId,
            String titulo,
            String descricao,
            PrioridadeTarefa prioridade,
            LocalDate dataVencimento,
            String horario,
            int ordem,
            List<Subtarefa> subtarefas,
            Integer lembreteMinutosAntes,
            boolean usaPadraoGlobalLembrete,
            String fusoAgendamento) {
    }

    private record DadosEdicaoSerie(
            ObjectId listaId,
            String titulo,
            String descricao,
            PrioridadeTarefa prioridade,
            String horario,
            String frequencia,
            int intervalo,
            List<Integer> dias,
            String modoMensal,
            Integer diaDoMes,
            Integer ordinalSemana,
            Integer diaSemanaOrdinal,
            String tipoTermino,
            LocalDate dataInicio,
            LocalDate dataFim,
            Integer maxOcorrencias,
            boolean ativa,
            Integer lembrete,
            boolean usaPadrao,
            String fuso,
            List<Subtarefa> modelo,
            String operationId) {
    }

    private static final String ERRO_LISTA_INFORMADA_INEXISTENTE_OU_NA_LIXEIRA = "A lista informada não existe ou está na lixeira.";
    private static final String VALOR_EM_DATA = "EM_DATA";
    private static final String ERRO_LISTA_NAO_ENCONTRADA_OU_INACESSIVEL = "Lista não encontrada ou inacessível.";
    private static final String ERRO_SERIE_NAO_ENCONTRADA_OU_INACESSIVEL = "Série recorrente não encontrada ou inacessível.";
    private static final String ERRO_TAREFA_NAO_ENCONTRADA_OU_INACESSIVEL = "Tarefa não encontrada ou inacessível.";
    private static final String CAMPO_LISTA_ID = "listaId";
    private static final String CAMPO_SERIE_ID = "serieId";
    private static final String CAMPO_TAREFA_ID = "tarefaId";
    private static final String CAMPO_USUARIO_ID = "usuarioId";

    private final ListaTarefasRepository listaRepository;
    private final SerieRecorrenteRepository serieRepository;
    private final TarefaRepository tarefaRepository;
    private final RecorrenciaEngine recorrenciaEngine;
    private final TimezoneContext timezoneContext;
    private final LembretePushService lembretePushService;
    private final RegraRecorrenciaValidator regraRecorrenciaValidator;

    public TarefaService(
            ListaTarefasRepository listaRepository,
            SerieRecorrenteRepository serieRepository,
            TarefaRepository tarefaRepository,
            RecorrenciaEngine recorrenciaEngine,
            TimezoneContext timezoneContext,
            LembretePushService lembretePushService,
            RegraRecorrenciaValidator regraRecorrenciaValidator) {
        this.listaRepository = listaRepository;
        this.serieRepository = serieRepository;
        this.tarefaRepository = tarefaRepository;
        this.recorrenciaEngine = recorrenciaEngine;
        this.timezoneContext = timezoneContext;
        this.lembretePushService = lembretePushService;
        this.regraRecorrenciaValidator = regraRecorrenciaValidator;
    }

    // =========================================================================
    // LISTAS DE TAREFAS
    // =========================================================================

    public ListaTarefasResponse criarLista(CriarListaTarefasRequest request, String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        if (request.nome() == null || request.nome().isBlank()) {
            throw new BadRequestException("O nome da lista não pode ser vazio.");
        }

        Instant agora = Instant.now();
        int ordem = request.ordem() != null ? request.ordem() : listaRepository.proximaOrdem(usuarioId);

        ListaTarefas lista = new ListaTarefas(
                new ObjectId(),
                usuarioId,
                request.nome().trim(),
                request.cor() != null ? request.cor().trim() : null,
                ordem,
                null,
                null,
                null,
                agora,
                agora
        );

        listaRepository.inserir(lista);
        return paraListaResponse(lista, 0);
    }

    public List<ListaTarefasResponse> listarListas(String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        return listaRepository.listarAtivas(usuarioId).stream()
                .map(l -> paraListaResponse(l, tarefaRepository.contarPorLista(usuarioId, l.id())))
                .toList();
    }

    public ListaTarefasResponse buscarLista(String listaIdStr, String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        ObjectId listaId = validarObjectId(listaIdStr, CAMPO_LISTA_ID);

        ListaTarefas lista = listaRepository.buscar(usuarioId, listaId)
                .filter(l -> l.deletedAt() == null)
                .orElseThrow(() -> new NotFoundException(ERRO_LISTA_NAO_ENCONTRADA_OU_INACESSIVEL));

        long total = tarefaRepository.contarPorLista(usuarioId, lista.id());
        return paraListaResponse(lista, total);
    }

    public ListaTarefasResponse atualizarLista(String listaIdStr, AtualizarListaTarefasRequest request, String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        ObjectId listaId = validarObjectId(listaIdStr, CAMPO_LISTA_ID);

        ListaTarefas existente = listaRepository.buscar(usuarioId, listaId)
                .filter(l -> l.deletedAt() == null)
                .orElseThrow(() -> new NotFoundException(ERRO_LISTA_NAO_ENCONTRADA_OU_INACESSIVEL));

        if (request.nome() == null || request.nome().isBlank()) {
            throw new BadRequestException("O nome da lista não pode ser vazio.");
        }

        ListaTarefas atualizada = new ListaTarefas(
                existente.id(),
                usuarioId,
                request.nome().trim(),
                request.cor() != null ? request.cor().trim() : existente.cor(),
                request.ordem() != null ? request.ordem() : existente.ordem(),
                existente.deletedAt(),
                existente.expiresAt(),
                existente.loteExclusaoId(),
                existente.criadoEm(),
                Instant.now()
        );

        listaRepository.atualizar(atualizada);
        long total = tarefaRepository.contarPorLista(usuarioId, atualizada.id());
        return paraListaResponse(atualizada, total);
    }

    public ExclusaoListaResponse excluirLista(String listaIdStr, String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        ObjectId listaId = validarObjectId(listaIdStr, CAMPO_LISTA_ID);

        listaRepository.buscar(usuarioId, listaId)
                .filter(l -> l.deletedAt() == null)
                .orElseThrow(() -> new NotFoundException(ERRO_LISTA_NAO_ENCONTRADA_OU_INACESSIVEL));

        Instant agora = Instant.now();
        Instant expiresAt = agora.plus(30, ChronoUnit.DAYS);
        String loteExclusaoId = UUID.randomUUID().toString();

        long tarefasAfetadas = tarefaRepository.moverPorListaParaLixeira(usuarioId, listaId, loteExclusaoId, agora, expiresAt);
        listaRepository.moverParaLixeira(usuarioId, listaId, loteExclusaoId, agora, expiresAt);

        return new ExclusaoListaResponse(listaIdStr, (int) tarefasAfetadas, loteExclusaoId);
    }

    public ListaTarefasResponse restaurarLista(String listaIdStr, String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        ObjectId listaId = validarObjectId(listaIdStr, CAMPO_LISTA_ID);

        ListaTarefas existente = listaRepository.buscar(usuarioId, listaId)
                .orElseThrow(() -> new NotFoundException(ERRO_LISTA_NAO_ENCONTRADA_OU_INACESSIVEL));

        if (existente.deletedAt() == null) {
            long total = tarefaRepository.contarPorLista(usuarioId, listaId);
            return paraListaResponse(existente, total);
        }

        String loteExclusaoId = existente.loteExclusaoId();
        listaRepository.restaurar(usuarioId, listaId);

        if (loteExclusaoId != null) {
            tarefaRepository.restaurarPorLote(usuarioId, listaId, loteExclusaoId);
        }

        long total = tarefaRepository.contarPorLista(usuarioId, listaId);
        ListaTarefas restaurada = listaRepository.buscar(usuarioId, listaId).orElse(existente);
        return paraListaResponse(restaurada, total);
    }

    // =========================================================================
    // TAREFAS MANUAIS E OCORRÊNCIAS
    // =========================================================================

    public TarefaResponse criarTarefa(CriarTarefaRequest request, String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        if (request.titulo() == null || request.titulo().isBlank()) {
            throw new BadRequestException("O título da tarefa é obrigatório.");
        }

        ObjectId listaId = validarEResolverListaId(usuarioId, request.listaId());
        LocalDate dataVencimento = BsonDates.toLocalDate(request.dataVencimento());
        validarGapDst(dataVencimento, request.horario(), request.fusoAgendamento());

        PrioridadeTarefa prioridade = PrioridadeTarefa.fromString(request.prioridade());
        int ordem = request.ordem() != null ? request.ordem() : tarefaRepository.proximaOrdem(usuarioId, listaId);
        List<Subtarefa> subtarefas = converterSubtarefas(request.subtarefas());

        Instant agora = Instant.now();
        Tarefa tarefa = new Tarefa(
                new ObjectId(),
                usuarioId,
                listaId,
                null, // serieId null (manual)
                null, // dataOriginalOcorrencia null
                false,
                request.titulo().trim(),
                request.descricao() != null ? request.descricao().trim() : null,
                prioridade,
                dataVencimento,
                request.horario(),
                "PENDENTE",
                false,
                null,
                ordem,
                subtarefas,
                request.lembreteMinutosAntes(),
                Boolean.TRUE.equals(request.usaPadraoGlobalLembrete()),
                request.fusoAgendamento(),
                null,
                null,
                null,
                agora,
                agora
        );

        tarefaRepository.inserir(tarefa);
        lembretePushService.agendarOuAtualizarLembrete(
                tarefa,
                request.lembreteMinutosAntes(),
                request.usaPadraoGlobalLembrete(),
                request.fusoAgendamento()
        );
        return paraTarefaResponse(tarefa);
    }

    public List<TarefaResponse> listarTarefas(String usuarioIdStr, FiltroTarefas filtro) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        LocalDate hojeLocal = timezoneContext.hojeLocal();

        String dataFimStr = filtro != null ? filtro.dataFim() : null;
        LocalDate dataFimBusca = BsonDates.toLocalDate(dataFimStr);
        recorrenciaEngine.expandirSeriesDoUsuario(usuarioId, hojeLocal, dataFimBusca);

        ObjectId listaId = null;
        boolean apenasSemLista = false;
        String listaIdStr = filtro != null ? filtro.listaId() : null;
        if (listaIdStr != null && !listaIdStr.isBlank()) {
            if ("sem_lista".equalsIgnoreCase(listaIdStr) || "null".equalsIgnoreCase(listaIdStr) || "geral".equalsIgnoreCase(listaIdStr)) {
                apenasSemLista = true;
            } else {
                listaId = validarObjectId(listaIdStr, CAMPO_LISTA_ID);
            }
        }

        LocalDate data = filtro != null ? BsonDates.toLocalDate(filtro.data()) : null;
        LocalDate dataInicio = filtro != null ? BsonDates.toLocalDate(filtro.dataInicio()) : null;
        Boolean concluida = filtro != null ? filtro.concluida() : null;
        String estado = filtro != null ? filtro.estado() : null;
        String termoFiltro = filtro != null ? filtro.filtro() : null;

        List<Tarefa> tarefas = tarefaRepository.listar(
                usuarioId, listaId, apenasSemLista, data, dataInicio, dataFimBusca, concluida, estado, termoFiltro);

        return tarefas.stream().map(this::paraTarefaResponse).toList();
    }

    public TarefaResponse buscarTarefa(String tarefaIdStr, String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        ObjectId tarefaId = validarObjectId(tarefaIdStr, CAMPO_TAREFA_ID);

        Tarefa tarefa = tarefaRepository.buscar(usuarioId, tarefaId)
                .filter(t -> t.deletedAt() == null)
                .orElseThrow(() -> new NotFoundException(ERRO_TAREFA_NAO_ENCONTRADA_OU_INACESSIVEL));

        return paraTarefaResponse(tarefa);
    }

    public TarefaResponse atualizarTarefa(
            String tarefaIdStr,
            AtualizarTarefaRequest request,
            String escopoParam,
            String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        ObjectId tarefaId = validarObjectId(tarefaIdStr, CAMPO_TAREFA_ID);

        Tarefa existente = tarefaRepository.buscar(usuarioId, tarefaId)
                .filter(t -> t.deletedAt() == null)
                .orElseThrow(() -> new NotFoundException(ERRO_TAREFA_NAO_ENCONTRADA_OU_INACESSIVEL));

        DadosEdicaoTarefa dados = extrairDadosEdicao(usuarioId, existente, request);
        validarGapDst(dados.dataVencimento(), dados.horario(), dados.fusoAgendamento());

        if (existente.serieId() != null && !existente.desvinculadaDaSerie()) {
            String escopoStr = request.escopo() != null ? request.escopo() : escopoParam;
            EscopoRecorrencia escopo = EscopoRecorrencia.fromString(escopoStr);

            if (escopo == EscopoRecorrencia.ESTA_E_FUTURAS) {
                return atualizarOcorrenciaEstaEFuturas(usuarioId, existente, request, dados);
            }
            if (escopo == EscopoRecorrencia.TODA_A_SERIE) {
                atualizarRegraSeriePorOcorrencia(usuarioId, existente, dados);
            }
            return atualizarOcorrenciaEsta(usuarioId, existente, dados);
        }

        return atualizarTarefaManual(usuarioId, existente, dados);
    }

    private DadosEdicaoTarefa extrairDadosEdicao(ObjectId usuarioId, Tarefa existente, AtualizarTarefaRequest request) {
        ObjectId listaId = existente.listaId();
        if (request.listaId() != null) {
            listaId = validarEResolverListaId(usuarioId, request.listaId());
        }

        String titulo = request.titulo() != null && !request.titulo().isBlank() ? request.titulo().trim() : existente.titulo();
        String descricao = request.descricao() != null ? request.descricao().trim() : existente.descricao();
        PrioridadeTarefa prioridade = request.prioridade() != null ? PrioridadeTarefa.fromString(request.prioridade()) : existente.prioridade();
        LocalDate dataVencimento = request.dataVencimento() != null ? BsonDates.toLocalDate(request.dataVencimento()) : existente.dataVencimento();
        String horario = request.horario() != null ? request.horario() : existente.horario();
        int ordem = request.ordem() != null ? request.ordem() : existente.ordem();
        Integer lembrete = request.lembreteMinutosAntes() != null ? request.lembreteMinutosAntes() : existente.lembreteMinutosAntes();
        boolean usaPadrao = request.usaPadraoGlobalLembrete() != null ? request.usaPadraoGlobalLembrete() : existente.usaPadraoGlobalLembrete();
        String fuso = request.fusoAgendamento() != null ? request.fusoAgendamento() : existente.fusoAgendamento();
        List<Subtarefa> subtarefas = request.subtarefas() != null ? converterSubtarefas(request.subtarefas()) : existente.subtarefas();

        return new DadosEdicaoTarefa(listaId, titulo, descricao, prioridade, dataVencimento, horario, ordem, subtarefas, lembrete, usaPadrao, fuso);
    }

    private TarefaResponse atualizarOcorrenciaEstaEFuturas(
            ObjectId usuarioId,
            Tarefa existente,
            AtualizarTarefaRequest request,
            DadosEdicaoTarefa dados) {
        LocalDate alvo = existente.dataOriginalOcorrencia();
        SerieRecorrente serieOriginal = serieRepository.buscar(usuarioId, existente.serieId())
                .orElseThrow(() -> new NotFoundException("Série recorrente de origem não encontrada."));

        LocalDate corte = alvo.minusDays(1);
        boolean serieAtiva = !corte.isBefore(serieOriginal.dataInicio());
        SerieRecorrente serieCortada = new SerieRecorrente(
                serieOriginal.id(), serieOriginal.usuarioId(), serieOriginal.listaId(), serieOriginal.titulo(),
                serieOriginal.descricao(), serieOriginal.prioridade(), serieOriginal.horario(),
                serieOriginal.subtarefasModelo(), serieOriginal.frequencia(), serieOriginal.intervalo(),
                serieOriginal.diasDaSemana(), serieOriginal.modoMensal(), serieOriginal.diaDoMes(),
                serieOriginal.ordinalSemana(), serieOriginal.diaSemanaOrdinal(), VALOR_EM_DATA,
                serieOriginal.dataInicio(), corte, null, corte, serieOriginal.totalGeradas(),
                serieAtiva, serieOriginal.lembreteMinutosAntes(), serieOriginal.usaPadraoGlobalLembrete(),
                serieOriginal.fusoAgendamento(), serieOriginal.operationId(), serieOriginal.deletedAt(),
                serieOriginal.expiresAt(), serieOriginal.loteExclusaoId(), serieOriginal.criadoEm(), Instant.now()
        );
        serieRepository.atualizar(serieCortada);

        tarefaRepository.removerPendentesFuturas(usuarioId, serieOriginal.id(), alvo);

        String opId = request.operationId();
        SerieRecorrente novaSerie = null;
        if (opId != null && !opId.isBlank()) {
            novaSerie = serieRepository.buscarPorOperationId(usuarioId, opId).orElse(null);
        }

        if (novaSerie == null) {
            novaSerie = new SerieRecorrente(
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
                    dados.lembreteMinutosAntes(),
                    dados.usaPadraoGlobalLembrete(),
                    dados.fusoAgendamento(),
                    opId,
                    null,
                    null,
                    null,
                    Instant.now(),
                    Instant.now()
            );
            serieRepository.inserir(novaSerie);
        }

        LocalDate hojeLocal = timezoneContext.hojeLocal();
        recorrenciaEngine.expandirSerie(novaSerie, hojeLocal, null);

        return tarefaRepository.buscarPorOcorrencia(usuarioId, novaSerie.id(), alvo)
                .map(this::paraTarefaResponse)
                .orElseGet(() -> paraTarefaResponse(existente));
    }

    private void atualizarRegraSeriePorOcorrencia(ObjectId usuarioId, Tarefa existente, DadosEdicaoTarefa dados) {
        SerieRecorrente serieOriginal = serieRepository.buscar(usuarioId, existente.serieId()).orElse(null);
        if (serieOriginal != null) {
            SerieRecorrente atualizadaSerie = new SerieRecorrente(
                    serieOriginal.id(), serieOriginal.usuarioId(), dados.listaId(), dados.titulo(), dados.descricao(),
                    dados.prioridade(), dados.horario(), dados.subtarefas(), serieOriginal.frequencia(), serieOriginal.intervalo(),
                    serieOriginal.diasDaSemana(), serieOriginal.modoMensal(), serieOriginal.diaDoMes(),
                    serieOriginal.ordinalSemana(), serieOriginal.diaSemanaOrdinal(), serieOriginal.tipoTermino(),
                    serieOriginal.dataInicio(), serieOriginal.dataFim(), serieOriginal.maxOcorrencias(),
                    serieOriginal.geradoAte(), serieOriginal.totalGeradas(), serieOriginal.ativa(),
                    dados.lembreteMinutosAntes(), dados.usaPadraoGlobalLembrete(), dados.fusoAgendamento(),
                    serieOriginal.operationId(), serieOriginal.deletedAt(),
                    serieOriginal.expiresAt(), serieOriginal.loteExclusaoId(), serieOriginal.criadoEm(), Instant.now()
            );
            serieRepository.atualizar(atualizadaSerie);
        }
    }

    private TarefaResponse atualizarOcorrenciaEsta(ObjectId usuarioId, Tarefa existente, DadosEdicaoTarefa dados) {
        Tarefa atualizada = new Tarefa(
                existente.id(),
                usuarioId,
                dados.listaId(),
                existente.serieId(),
                existente.dataOriginalOcorrencia(),
                true, // desvinculadaDaSerie = true
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
                dados.lembreteMinutosAntes(),
                dados.usaPadraoGlobalLembrete(),
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
                atualizada.lembreteMinutosAntes(),
                atualizada.usaPadraoGlobalLembrete(),
                atualizada.fusoAgendamento()
        );
        return paraTarefaResponse(atualizada);
    }

    private TarefaResponse atualizarTarefaManual(ObjectId usuarioId, Tarefa existente, DadosEdicaoTarefa dados) {
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
                dados.lembreteMinutosAntes(),
                dados.usaPadraoGlobalLembrete(),
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
                atualizada.lembreteMinutosAntes(),
                atualizada.usaPadraoGlobalLembrete(),
                atualizada.fusoAgendamento()
        );
        return paraTarefaResponse(atualizada);
    }

    public void excluirTarefa(String tarefaIdStr, String escopoParam, String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        ObjectId tarefaId = validarObjectId(tarefaIdStr, CAMPO_TAREFA_ID);

        Tarefa existente = tarefaRepository.buscar(usuarioId, tarefaId)
                .filter(t -> t.deletedAt() == null)
                .orElseThrow(() -> new NotFoundException(ERRO_TAREFA_NAO_ENCONTRADA_OU_INACESSIVEL));

        Instant agora = Instant.now();
        Instant expiresAt = agora.plus(30, ChronoUnit.DAYS);
        String loteExclusaoId = UUID.randomUUID().toString();

        if (existente.serieId() != null && !existente.desvinculadaDaSerie()) {
            EscopoRecorrencia escopo = EscopoRecorrencia.fromString(escopoParam);

            if (escopo == EscopoRecorrencia.ESTA_E_FUTURAS) {
                LocalDate alvo = existente.dataOriginalOcorrencia();
                SerieRecorrente serieOriginal = serieRepository.buscar(usuarioId, existente.serieId())
                        .orElseThrow(() -> new NotFoundException("Série recorrente de origem não encontrada."));

                LocalDate corte = alvo.minusDays(1);
                boolean serieAtiva = !corte.isBefore(serieOriginal.dataInicio());
                SerieRecorrente serieCortada = new SerieRecorrente(
                        serieOriginal.id(), serieOriginal.usuarioId(), serieOriginal.listaId(), serieOriginal.titulo(),
                        serieOriginal.descricao(), serieOriginal.prioridade(), serieOriginal.horario(),
                        serieOriginal.subtarefasModelo(), serieOriginal.frequencia(), serieOriginal.intervalo(),
                        serieOriginal.diasDaSemana(), serieOriginal.modoMensal(), serieOriginal.diaDoMes(),
                        serieOriginal.ordinalSemana(), serieOriginal.diaSemanaOrdinal(), VALOR_EM_DATA,
                        serieOriginal.dataInicio(), corte, null, corte, serieOriginal.totalGeradas(),
                        serieAtiva, serieOriginal.lembreteMinutosAntes(), serieOriginal.usaPadraoGlobalLembrete(),
                        serieOriginal.fusoAgendamento(), serieOriginal.operationId(), serieOriginal.deletedAt(),
                        serieOriginal.expiresAt(), serieOriginal.loteExclusaoId(), serieOriginal.criadoEm(), Instant.now()
                );
                serieRepository.atualizar(serieCortada);

                tarefaRepository.removerPendentesFuturas(usuarioId, serieOriginal.id(), alvo);
                return;
            }

            if (escopo == EscopoRecorrencia.TODA_A_SERIE) {
                serieRepository.moverParaLixeira(usuarioId, existente.serieId(), loteExclusaoId, agora, expiresAt);
                tarefaRepository.moverPendentesSerieParaLixeira(usuarioId, existente.serieId(), loteExclusaoId, agora, expiresAt);
                return;
            }

            tarefaRepository.moverParaLixeira(usuarioId, tarefaId, loteExclusaoId, agora, expiresAt);
            lembretePushService.cancelarLembrete(usuarioId, tarefaId);
            return;
        }

        tarefaRepository.moverParaLixeira(usuarioId, tarefaId, loteExclusaoId, agora, expiresAt);
        lembretePushService.cancelarLembrete(usuarioId, tarefaId);
    }

    public TarefaResponse atualizarConclusao(String tarefaIdStr, boolean concluida, String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        ObjectId tarefaId = validarObjectId(tarefaIdStr, CAMPO_TAREFA_ID);

        Tarefa existente = tarefaRepository.buscar(usuarioId, tarefaId)
                .filter(t -> t.deletedAt() == null)
                .orElseThrow(() -> new NotFoundException(ERRO_TAREFA_NAO_ENCONTRADA_OU_INACESSIVEL));

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
                existente.lembreteMinutosAntes(),
                existente.usaPadraoGlobalLembrete(),
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
                    atualizada.lembreteMinutosAntes(),
                    atualizada.usaPadraoGlobalLembrete(),
                    atualizada.fusoAgendamento()
            );
        }
        return paraTarefaResponse(atualizada);
    }

    public TarefaResponse atualizarSubtarefa(
            String tarefaIdStr,
            String subtarefaIdStr,
            boolean concluida,
            String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        ObjectId tarefaId = validarObjectId(tarefaIdStr, CAMPO_TAREFA_ID);

        Tarefa existente = tarefaRepository.buscar(usuarioId, tarefaId)
                .filter(t -> t.deletedAt() == null)
                .orElseThrow(() -> new NotFoundException(ERRO_TAREFA_NAO_ENCONTRADA_OU_INACESSIVEL));

        List<Subtarefa> novasSubtarefas = new ArrayList<>();
        boolean encontrada = false;
        for (Subtarefa sub : existente.subtarefas()) {
            if (sub.id().equals(subtarefaIdStr)) {
                novasSubtarefas.add(new Subtarefa(sub.id(), sub.titulo(), concluida, sub.ordem()));
                encontrada = true;
            } else {
                novasSubtarefas.add(sub);
            }
        }

        if (!encontrada) {
            throw new NotFoundException("Subtarefa não encontrada.");
        }

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
                existente.lembreteMinutosAntes(),
                existente.usaPadraoGlobalLembrete(),
                existente.fusoAgendamento(),
                existente.deletedAt(),
                existente.expiresAt(),
                existente.loteExclusaoId(),
                existente.criadoEm(),
                Instant.now()
        );

        tarefaRepository.atualizar(atualizada);
        return paraTarefaResponse(atualizada);
    }

    public TarefaResponse pularTarefa(String tarefaIdStr, String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        ObjectId tarefaId = validarObjectId(tarefaIdStr, CAMPO_TAREFA_ID);

        Tarefa existente = tarefaRepository.buscar(usuarioId, tarefaId)
                .filter(t -> t.deletedAt() == null)
                .orElseThrow(() -> new NotFoundException(ERRO_TAREFA_NAO_ENCONTRADA_OU_INACESSIVEL));

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
                existente.lembreteMinutosAntes(),
                existente.usaPadraoGlobalLembrete(),
                existente.fusoAgendamento(),
                existente.deletedAt(),
                existente.expiresAt(),
                existente.loteExclusaoId(),
                existente.criadoEm(),
                Instant.now()
        );

        tarefaRepository.atualizar(atualizada);
        lembretePushService.cancelarLembrete(usuarioId, tarefaId);
        return paraTarefaResponse(atualizada);
    }

    // =========================================================================
    // SÉRIES RECORRENTES
    // =========================================================================

    public SerieRecorrenteResponse criarSerie(CriarSerieRecorrenteRequest request, String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        regraRecorrenciaValidator.validar(new ParametrosRegraRecorrencia(
                request.frequencia(), request.intervalo(), request.diasDaSemana(),
                request.modoMensal(), request.diaDoMes(), request.ordinalSemana(), request.diaSemanaOrdinal(),
                request.tipoTermino(), request.dataInicio(), request.dataFim(), request.maxOcorrencias()
        ));

        ObjectId listaId = validarEResolverListaId(usuarioId, request.listaId());
        List<Subtarefa> modelo = converterSubtarefas(request.subtarefasModelo());
        LocalDate dataInicio = BsonDates.toLocalDate(request.dataInicio());
        LocalDate dataFim = BsonDates.toLocalDate(request.dataFim());
        PrioridadeTarefa prioridade = PrioridadeTarefa.fromString(request.prioridade());

        Instant agora = Instant.now();
        SerieRecorrente serie = new SerieRecorrente(
                new ObjectId(),
                usuarioId,
                listaId,
                request.titulo().trim(),
                request.descricao() != null ? request.descricao().trim() : null,
                prioridade,
                request.horario(),
                modelo,
                request.frequencia().toUpperCase().trim(),
                Math.max(1, request.intervalo()),
                request.diasDaSemana() != null ? request.diasDaSemana() : List.of(),
                request.modoMensal(),
                request.diaDoMes(),
                request.ordinalSemana(),
                request.diaSemanaOrdinal(),
                request.tipoTermino().toUpperCase().trim(),
                dataInicio,
                dataFim,
                request.maxOcorrencias(),
                null,
                0,
                true,
                request.lembreteMinutosAntes(),
                Boolean.TRUE.equals(request.usaPadraoGlobalLembrete()),
                request.fusoAgendamento(),
                request.operationId(),
                null,
                null,
                null,
                agora,
                agora
        );

        serieRepository.inserir(serie);

        LocalDate hojeLocal = timezoneContext.hojeLocal();
        recorrenciaEngine.expandirSerie(serie, hojeLocal, null);

        SerieRecorrente salva = serieRepository.buscar(usuarioId, serie.id()).orElse(serie);
        return paraSerieResponse(salva);
    }

    public List<SerieRecorrenteResponse> listarSeries(String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        return serieRepository.listarAtivas(usuarioId).stream()
                .map(this::paraSerieResponse)
                .toList();
    }

    public SerieRecorrenteResponse buscarSerie(String serieIdStr, String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        ObjectId serieId = validarObjectId(serieIdStr, CAMPO_SERIE_ID);

        SerieRecorrente serie = serieRepository.buscar(usuarioId, serieId)
                .filter(s -> s.deletedAt() == null)
                .orElseThrow(() -> new NotFoundException(ERRO_SERIE_NAO_ENCONTRADA_OU_INACESSIVEL));

        return paraSerieResponse(serie);
    }

    public SerieRecorrenteResponse atualizarSerie(
            String serieIdStr,
            AtualizarSerieRecorrenteRequest request,
            String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        ObjectId serieId = validarObjectId(serieIdStr, CAMPO_SERIE_ID);
        SerieRecorrente existente = buscarSerieAtiva(usuarioId, serieId);
        DadosEdicaoSerie dados = resolverDadosEdicaoSerie(usuarioId, existente, request);
        validarDadosEdicaoSerie(dados);
        SerieRecorrente atualizada = montarSerieAtualizada(existente, usuarioId, dados);
        serieRepository.atualizar(atualizada);
        expandirSerieSeAtiva(atualizada, dados.ativa());
        SerieRecorrente salva = serieRepository.buscar(usuarioId, serieId).orElse(atualizada);
        return paraSerieResponse(salva);
    }

    private SerieRecorrente buscarSerieAtiva(ObjectId usuarioId, ObjectId serieId) {
        return serieRepository.buscar(usuarioId, serieId)
                .filter(serie -> serie.deletedAt() == null)
                .orElseThrow(() -> new NotFoundException(ERRO_SERIE_NAO_ENCONTRADA_OU_INACESSIVEL));
    }

    private DadosEdicaoSerie resolverDadosEdicaoSerie(
            ObjectId usuarioId, SerieRecorrente existente, AtualizarSerieRecorrenteRequest request) {
        ObjectId listaId = request.listaId() == null
                ? existente.listaId()
                : validarEResolverListaId(usuarioId, request.listaId());
        return new DadosEdicaoSerie(
                listaId,
                request.titulo() != null && !request.titulo().isBlank() ? request.titulo().trim() : existente.titulo(),
                request.descricao() != null ? request.descricao().trim() : existente.descricao(),
                request.prioridade() != null ? PrioridadeTarefa.fromString(request.prioridade()) : existente.prioridade(),
                request.horario() != null ? request.horario() : existente.horario(),
                request.frequencia() != null ? request.frequencia().toUpperCase().trim() : existente.frequencia(),
                request.intervalo() != null ? Math.max(1, request.intervalo()) : existente.intervalo(),
                request.diasDaSemana() != null ? request.diasDaSemana() : existente.diasDaSemana(),
                request.modoMensal() != null ? request.modoMensal() : existente.modoMensal(),
                request.diaDoMes() != null ? request.diaDoMes() : existente.diaDoMes(),
                request.ordinalSemana() != null ? request.ordinalSemana() : existente.ordinalSemana(),
                request.diaSemanaOrdinal() != null ? request.diaSemanaOrdinal() : existente.diaSemanaOrdinal(),
                request.tipoTermino() != null ? request.tipoTermino().toUpperCase().trim() : existente.tipoTermino(),
                request.dataInicio() != null ? BsonDates.toLocalDate(request.dataInicio()) : existente.dataInicio(),
                request.dataFim() != null ? BsonDates.toLocalDate(request.dataFim()) : existente.dataFim(),
                request.maxOcorrencias() != null ? request.maxOcorrencias() : existente.maxOcorrencias(),
                request.ativa() != null ? request.ativa() : existente.ativa(),
                request.lembreteMinutosAntes() != null ? request.lembreteMinutosAntes() : existente.lembreteMinutosAntes(),
                request.usaPadraoGlobalLembrete() != null ? request.usaPadraoGlobalLembrete() : existente.usaPadraoGlobalLembrete(),
                request.fusoAgendamento() != null ? request.fusoAgendamento() : existente.fusoAgendamento(),
                request.subtarefasModelo() != null ? converterSubtarefas(request.subtarefasModelo()) : existente.subtarefasModelo(),
                request.operationId() != null ? request.operationId() : existente.operationId());
    }

    private void validarDadosEdicaoSerie(DadosEdicaoSerie dados) {
        regraRecorrenciaValidator.validar(new ParametrosRegraRecorrencia(
                dados.frequencia(), dados.intervalo(), dados.dias(), dados.modoMensal(), dados.diaDoMes(),
                dados.ordinalSemana(), dados.diaSemanaOrdinal(), dados.tipoTermino(),
                dados.dataInicio() != null ? dados.dataInicio().toString() : null,
                dados.dataFim() != null ? dados.dataFim().toString() : null,
                dados.maxOcorrencias()));
    }

    private SerieRecorrente montarSerieAtualizada(
            SerieRecorrente existente, ObjectId usuarioId, DadosEdicaoSerie dados) {
        return new SerieRecorrente(
                existente.id(), usuarioId, dados.listaId(), dados.titulo(), dados.descricao(), dados.prioridade(),
                dados.horario(), dados.modelo(), dados.frequencia(), dados.intervalo(), dados.dias(),
                dados.modoMensal(), dados.diaDoMes(), dados.ordinalSemana(), dados.diaSemanaOrdinal(),
                dados.tipoTermino(), dados.dataInicio(), dados.dataFim(), dados.maxOcorrencias(),
                existente.geradoAte(), existente.totalGeradas(), dados.ativa(), dados.lembrete(),
                dados.usaPadrao(), dados.fuso(), dados.operationId(), existente.deletedAt(),
                existente.expiresAt(), existente.loteExclusaoId(), existente.criadoEm(), Instant.now());
    }

    private void expandirSerieSeAtiva(SerieRecorrente serie, boolean ativa) {
        if (ativa) {
            recorrenciaEngine.expandirSerie(serie, timezoneContext.hojeLocal(), null);
        }
    }

    public void excluirSerie(String serieIdStr, String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        ObjectId serieId = validarObjectId(serieIdStr, CAMPO_SERIE_ID);

        serieRepository.buscar(usuarioId, serieId)
                .filter(s -> s.deletedAt() == null)
                .orElseThrow(() -> new NotFoundException(ERRO_SERIE_NAO_ENCONTRADA_OU_INACESSIVEL));

        Instant agora = Instant.now();
        Instant expiresAt = agora.plus(30, ChronoUnit.DAYS);
        String loteExclusaoId = UUID.randomUUID().toString();

        serieRepository.moverParaLixeira(usuarioId, serieId, loteExclusaoId, agora, expiresAt);
        tarefaRepository.moverPendentesSerieParaLixeira(usuarioId, serieId, loteExclusaoId, agora, expiresAt);
    }

    // =========================================================================
    // VISÃO HOJE
    // =========================================================================

    public TarefasHojeResponse obterTarefasHoje(String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        LocalDate hojeLocal = timezoneContext.hojeLocal();

        recorrenciaEngine.expandirSeriesDoUsuario(usuarioId, hojeLocal, null);

        List<Tarefa> atrasadasList = new ArrayList<>(tarefaRepository.listarAtrasadas(usuarioId, hojeLocal));
        List<Tarefa> hojeList = new ArrayList<>(tarefaRepository.listarHoje(usuarioId, hojeLocal));
        List<Tarefa> semDataList = new ArrayList<>(tarefaRepository.listarSemData(usuarioId));

        atrasadasList.sort(Comparator
                .comparing(Tarefa::dataVencimento, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing((Tarefa t) -> t.prioridade().getPeso(), Comparator.reverseOrder())
                .thenComparingInt(Tarefa::ordem));

        hojeList.sort(Comparator
                .comparing(Tarefa::horario, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing((Tarefa t) -> t.prioridade().getPeso(), Comparator.reverseOrder())
                .thenComparingInt(Tarefa::ordem));

        semDataList.sort(Comparator
                .comparing((Tarefa t) -> t.prioridade().getPeso(), Comparator.reverseOrder())
                .thenComparingInt(Tarefa::ordem));

        return new TarefasHojeResponse(
                hojeLocal.toString(),
                atrasadasList.stream().map(this::paraTarefaResponse).toList(),
                hojeList.stream().map(this::paraTarefaResponse).toList(),
                semDataList.stream().map(this::paraTarefaResponse).toList()
        );
    }

    // =========================================================================
    // LIXEIRA (30 DIAS) E RESTAURAÇÃO
    // =========================================================================

    public LixeiraTarefasResponse listarLixeira(String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        Instant agora = Instant.now();

        List<Tarefa> tarefas = tarefaRepository.listarLixeira(usuarioId, agora);
        List<ListaTarefas> listas = listaRepository.listarLixeira(usuarioId, agora);
        List<SerieRecorrente> series = serieRepository.listarLixeira(usuarioId, agora);

        return new LixeiraTarefasResponse(
                tarefas.stream().map(this::paraTarefaResponse).toList(),
                listas.stream().map(l -> paraListaResponse(l, 0)).toList(),
                series.stream().map(this::paraSerieResponse).toList()
        );
    }

    public TarefaResponse restaurarTarefaDaLixeira(String tarefaIdStr, String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);
        ObjectId tarefaId = validarObjectId(tarefaIdStr, CAMPO_TAREFA_ID);

        Tarefa existente = tarefaRepository.buscar(usuarioId, tarefaId)
                .orElseThrow(() -> new NotFoundException("Tarefa não encontrada na lixeira."));

        ObjectId novaListaId = existente.listaId();
        if (existente.listaId() != null) {
            Optional<ListaTarefas> listaOpt = listaRepository.buscarQualquerStatus(existente.listaId());
            if (listaOpt.isEmpty()) {
                novaListaId = null;
            } else {
                ListaTarefas lista = listaOpt.get();
                if (lista.deletedAt() != null) {
                    listaRepository.restaurar(usuarioId, lista.id());
                }
            }
        }

        tarefaRepository.restaurar(usuarioId, tarefaId, novaListaId);
        Tarefa restaurada = tarefaRepository.buscar(usuarioId, tarefaId).orElse(existente);
        lembretePushService.agendarOuAtualizarLembrete(
                restaurada,
                restaurada.lembreteMinutosAntes(),
                restaurada.usaPadraoGlobalLembrete(),
                restaurada.fusoAgendamento()
        );
        return paraTarefaResponse(restaurada);
    }

    public void esvaziarLixeira(String usuarioIdStr) {
        ObjectId usuarioId = validarObjectId(usuarioIdStr, CAMPO_USUARIO_ID);

        List<ListaTarefas> listasLixeira = listaRepository.listarLixeira(usuarioId, Instant.now());
        for (ListaTarefas l : listasLixeira) {
            serieRepository.desvincularLista(l.id());
        }

        tarefaRepository.esvaziarLixeira(usuarioId);
        serieRepository.esvaziarLixeira(usuarioId);
        listaRepository.esvaziarLixeira(usuarioId);
    }

    public int purgarExpiradasGlobal(Instant agora) {
        List<ObjectId> listasExpiradas = listaRepository.listarIdsExpiradas(agora);
        for (ObjectId lId : listasExpiradas) {
            serieRepository.desvincularLista(lId);
        }

        long tRemovidas = tarefaRepository.purgarExpiradas(agora);
        long sRemovidas = serieRepository.purgarExpiradas(agora);
        long lRemovidas = listaRepository.purgarExpiradas(agora);

        return (int) (tRemovidas + sRemovidas + lRemovidas);
    }

    // =========================================================================
    // AUXILIARES E VALIDAÇÕES
    // =========================================================================

    private void validarGapDst(LocalDate dataVencimento, String horario, String fusoAgendamento) {
        if (horario != null && !horario.isBlank() && dataVencimento != null) {
            ZoneId zoneId = fusoAgendamento != null && !fusoAgendamento.isBlank()
                    ? TimezoneContext.resolverZoneId(fusoAgendamento)
                    : timezoneContext.zoneId();
            try {
                LocalTime lt = LocalTime.parse(horario.trim());
                LembretePushCalculator.calcularInstanteEntregaUtc(dataVencimento, lt, zoneId, 0);
            } catch (InvalidLocalDatetimeException e) {
                throw e;
            } catch (Exception _) {
                // Formato de hora com outros erros ignorado aqui; tratado nos validadores de schema
            }
        }
    }

    private List<Subtarefa> converterSubtarefas(List<SubtarefaDTO> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return java.util.stream.IntStream.range(0, dtos.size())
                .mapToObj(ordem -> criarSubtarefa(dtos.get(ordem), ordem))
                .toList();
    }

    private Subtarefa criarSubtarefa(SubtarefaDTO dto, int ordem) {
        String id = dto.id() != null && !dto.id().isBlank() ? dto.id() : new ObjectId().toHexString();
        return new Subtarefa(id, dto.titulo(), Boolean.TRUE.equals(dto.concluida()), ordem);
    }

    private ObjectId validarEResolverListaId(ObjectId usuarioId, String listaIdStr) {
        if (listaIdStr == null || listaIdStr.isBlank() || "null".equalsIgnoreCase(listaIdStr)) {
            return null;
        }
        ObjectId listaId = validarObjectId(listaIdStr, CAMPO_LISTA_ID);
        listaRepository.buscar(usuarioId, listaId)
                .filter(l -> l.deletedAt() == null)
                .orElseThrow(() -> new BadRequestException(ERRO_LISTA_INFORMADA_INEXISTENTE_OU_NA_LIXEIRA));
        return listaId;
    }

    private ObjectId validarObjectId(String idStr, String nomeCampo) {
        if (idStr == null || !ObjectId.isValid(idStr.trim())) {
            throw new BadRequestException("Identificador inválido para " + nomeCampo + ": " + idStr);
        }
        return new ObjectId(idStr.trim());
    }

    private ListaTarefasResponse paraListaResponse(ListaTarefas l, long totalTarefas) {
        return new ListaTarefasResponse(
                l.id().toHexString(),
                l.usuarioId().toHexString(),
                l.nome(),
                l.cor(),
                l.ordem(),
                totalTarefas,
                l.criadoEm(),
                l.atualizadoEm()
        );
    }

    private TarefaResponse paraTarefaResponse(Tarefa t) {
        List<SubtarefaDTO> subs = t.subtarefas().stream()
                .map(s -> new SubtarefaDTO(s.id(), s.titulo(), s.concluida(), s.ordem()))
                .toList();

        return new TarefaResponse(
                t.id().toHexString(),
                t.usuarioId().toHexString(),
                t.listaId() != null ? t.listaId().toHexString() : null,
                t.serieId() != null ? t.serieId().toHexString() : null,
                BsonDates.toIsoString(t.dataOriginalOcorrencia()),
                t.desvinculadaDaSerie(),
                t.titulo(),
                t.descricao(),
                t.prioridade().getValor(),
                BsonDates.toIsoString(t.dataVencimento()),
                t.horario(),
                t.estado(),
                t.concluida(),
                t.concluidaEm(),
                t.ordem(),
                subs,
                t.lembreteMinutosAntes(),
                t.usaPadraoGlobalLembrete(),
                t.fusoAgendamento(),
                t.deletedAt(),
                t.expiresAt(),
                t.loteExclusaoId(),
                t.criadoEm(),
                t.atualizadoEm()
        );
    }

    private SerieRecorrenteResponse paraSerieResponse(SerieRecorrente s) {
        List<SubtarefaDTO> subs = s.subtarefasModelo().stream()
                .map(sub -> new SubtarefaDTO(sub.id(), sub.titulo(), sub.concluida(), sub.ordem()))
                .toList();

        return new SerieRecorrenteResponse(
                s.id().toHexString(),
                s.usuarioId().toHexString(),
                s.listaId() != null ? s.listaId().toHexString() : null,
                s.titulo(),
                s.descricao(),
                s.prioridade().getValor(),
                s.horario(),
                subs,
                s.frequencia(),
                s.intervalo(),
                s.diasDaSemana(),
                s.modoMensal(),
                s.diaDoMes(),
                s.ordinalSemana(),
                s.diaSemanaOrdinal(),
                s.tipoTermino(),
                BsonDates.toIsoString(s.dataInicio()),
                BsonDates.toIsoString(s.dataFim()),
                s.maxOcorrencias(),
                BsonDates.toIsoString(s.geradoAte()),
                s.totalGeradas(),
                s.ativa(),
                s.lembreteMinutosAntes(),
                s.usaPadraoGlobalLembrete(),
                s.fusoAgendamento(),
                s.deletedAt(),
                s.expiresAt(),
                s.loteExclusaoId(),
                s.criadoEm(),
                s.atualizadoEm()
        );
    }
}
