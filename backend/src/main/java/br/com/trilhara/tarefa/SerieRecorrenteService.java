package br.com.trilhara.tarefa;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.bson.types.ObjectId;

import br.com.trilhara.shared.TimezoneContext;
import br.com.trilhara.tarefa.RegraRecorrenciaValidator.ParametrosRegraRecorrencia;
import br.com.trilhara.tarefa.dto.AtualizarSerieRecorrenteRequest;
import br.com.trilhara.tarefa.dto.CriarSerieRecorrenteRequest;
import br.com.trilhara.tarefa.dto.SerieRecorrenteResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class SerieRecorrenteService {

    private static final String ERRO_SERIE_NAO_ENCONTRADA_OU_INACESSIVEL =
            "Série recorrente não encontrada ou inacessível.";

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
            ModoLembrete modoLembrete,
            Integer lembrete,
            String fuso,
            List<Subtarefa> modelo,
            String origemOperacaoId) {
    }

    private final ListaTarefasRepository listaRepository;
    private final SerieRecorrenteRepository serieRepository;
    private final TarefaRepository tarefaRepository;
    private final RecorrenciaEngine recorrenciaEngine;
    private final TimezoneContext timezoneContext;
    private final RegraRecorrenciaValidator regraRecorrenciaValidator;

    public SerieRecorrenteService(
            ListaTarefasRepository listaRepository,
            SerieRecorrenteRepository serieRepository,
            TarefaRepository tarefaRepository,
            RecorrenciaEngine recorrenciaEngine,
            TimezoneContext timezoneContext,
            RegraRecorrenciaValidator regraRecorrenciaValidator) {
        this.listaRepository = listaRepository;
        this.serieRepository = serieRepository;
        this.tarefaRepository = tarefaRepository;
        this.recorrenciaEngine = recorrenciaEngine;
        this.timezoneContext = timezoneContext;
        this.regraRecorrenciaValidator = regraRecorrenciaValidator;
    }

    public SerieRecorrenteResponse criarSerie(CriarSerieRecorrenteRequest request, String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        regraRecorrenciaValidator.validar(new ParametrosRegraRecorrencia(
                request.frequencia(), request.intervalo(), request.diasDaSemana(),
                request.modoMensal(), request.diaDoMes(), request.ordinalSemana(), request.diaSemanaOrdinal(),
                request.tipoTermino(), request.dataInicio(), request.dataFim(), request.maxOcorrencias()
        ));

        ObjectId listaId = TarefaSupport.validarEResolverListaId(listaRepository, usuarioId, request.listaId());
        List<Subtarefa> modelo = TarefaMapper.converterSubtarefas(request.subtarefasModelo());
        LocalDate dataInicio = TarefaSupport.paraLocalDate(request.dataInicio());
        LocalDate dataFim = TarefaSupport.paraLocalDate(request.dataFim());
        PrioridadeTarefa prioridade = PrioridadeTarefa.fromString(request.prioridade());
        TarefaSupport.validarPreferenciaLembrete(request.modoLembrete(), request.lembreteMinutosAntes());

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
                request.modoLembrete(),
                request.lembreteMinutosAntes(),
                request.fusoAgendamento(),
                request.origemOperacaoId(),
                null,
                null,
                null,
                agora,
                agora
        );

        serieRepository.inserir(serie);
        recorrenciaEngine.expandirSerie(serie, timezoneContext.hojeLocal(), null);

        SerieRecorrente salva = serieRepository.buscar(usuarioId, serie.id()).orElse(serie);
        return TarefaMapper.paraSerieResponse(salva);
    }

    public List<SerieRecorrenteResponse> listarSeries(String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        return serieRepository.listarAtivas(usuarioId).stream()
                .map(TarefaMapper::paraSerieResponse)
                .toList();
    }

    public SerieRecorrenteResponse buscarSerie(String serieIdStr, String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        ObjectId serieId = TarefaSupport.validarObjectId(serieIdStr, TarefaSupport.CAMPO_SERIE_ID);
        return TarefaMapper.paraSerieResponse(buscarSerieAtiva(usuarioId, serieId));
    }

    public SerieRecorrenteResponse atualizarSerie(
            String serieIdStr,
            AtualizarSerieRecorrenteRequest request,
            String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        ObjectId serieId = TarefaSupport.validarObjectId(serieIdStr, TarefaSupport.CAMPO_SERIE_ID);
        SerieRecorrente existente = buscarSerieAtiva(usuarioId, serieId);
        DadosEdicaoSerie dados = resolverDadosEdicaoSerie(usuarioId, existente, request);
        validarDadosEdicaoSerie(dados);
        SerieRecorrente atualizada = montarSerieAtualizada(existente, usuarioId, dados);
        serieRepository.atualizar(atualizada);
        if (dados.ativa()) {
            recorrenciaEngine.expandirSerie(atualizada, timezoneContext.hojeLocal(), null);
        }
        SerieRecorrente salva = serieRepository.buscar(usuarioId, serieId).orElse(atualizada);
        return TarefaMapper.paraSerieResponse(salva);
    }

    public void excluirSerie(String serieIdStr, String usuarioIdStr) {
        ObjectId usuarioId = TarefaSupport.validarObjectId(usuarioIdStr, TarefaSupport.CAMPO_USUARIO_ID);
        ObjectId serieId = TarefaSupport.validarObjectId(serieIdStr, TarefaSupport.CAMPO_SERIE_ID);

        buscarSerieAtiva(usuarioId, serieId);

        Instant agora = Instant.now();
        Instant expiresAt = agora.plus(30, ChronoUnit.DAYS);
        String loteExclusaoId = UUID.randomUUID().toString();

        serieRepository.moverParaLixeira(usuarioId, serieId, loteExclusaoId, agora, expiresAt);
        tarefaRepository.moverPendentesSerieParaLixeira(usuarioId, serieId, loteExclusaoId, agora, expiresAt);
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
                : TarefaSupport.validarEResolverListaId(listaRepository, usuarioId, request.listaId());
        PrioridadeTarefa prioridade = request.prioridade() != null
                ? PrioridadeTarefa.fromString(request.prioridade())
                : existente.prioridade();
        int intervalo = request.intervalo() != null ? Math.max(1, request.intervalo()) : existente.intervalo();
        LocalDate dataInicio = resolverDataEdicao(request.dataInicio(), existente.dataInicio());
        LocalDate dataFim = resolverDataEdicao(request.dataFim(), existente.dataFim());
        List<Subtarefa> modelo = request.subtarefasModelo() != null
                ? TarefaMapper.converterSubtarefas(request.subtarefasModelo())
                : existente.subtarefasModelo();

        return new DadosEdicaoSerie(
                listaId,
                textoNaoVazioOuPadrao(request.titulo(), existente.titulo()),
                textoAparadoOuPadrao(request.descricao(), existente.descricao()),
                prioridade,
                valorOuPadrao(request.horario(), existente.horario()),
                textoMaiusculoOuPadrao(request.frequencia(), existente.frequencia()),
                intervalo,
                valorOuPadrao(request.diasDaSemana(), existente.diasDaSemana()),
                valorOuPadrao(request.modoMensal(), existente.modoMensal()),
                valorOuPadrao(request.diaDoMes(), existente.diaDoMes()),
                valorOuPadrao(request.ordinalSemana(), existente.ordinalSemana()),
                valorOuPadrao(request.diaSemanaOrdinal(), existente.diaSemanaOrdinal()),
                textoMaiusculoOuPadrao(request.tipoTermino(), existente.tipoTermino()),
                dataInicio,
                dataFim,
                valorOuPadrao(request.maxOcorrencias(), existente.maxOcorrencias()),
                valorOuPadrao(request.ativa(), existente.ativa()),
                valorOuPadrao(request.modoLembrete(), existente.modoLembrete()),
                valorOuPadrao(request.lembreteMinutosAntes(), existente.lembreteMinutosAntes()),
                valorOuPadrao(request.fusoAgendamento(), existente.fusoAgendamento()),
                modelo,
                valorOuPadrao(request.origemOperacaoId(), existente.origemOperacaoId()));
    }

    private static <T> T valorOuPadrao(T novoValor, T valorAtual) {
        return novoValor != null ? novoValor : valorAtual;
    }

    private static String textoNaoVazioOuPadrao(String novoValor, String valorAtual) {
        return novoValor != null && !novoValor.isBlank() ? novoValor.trim() : valorAtual;
    }

    private static String textoAparadoOuPadrao(String novoValor, String valorAtual) {
        return novoValor != null ? novoValor.trim() : valorAtual;
    }

    private static String textoMaiusculoOuPadrao(String novoValor, String valorAtual) {
        return novoValor != null ? novoValor.toUpperCase().trim() : valorAtual;
    }

    private static LocalDate resolverDataEdicao(String novaDataStr, LocalDate dataAtual) {
        return novaDataStr != null ? TarefaSupport.paraLocalDate(novaDataStr) : dataAtual;
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
                existente.geradoAte(), existente.totalGeradas(), dados.ativa(), dados.modoLembrete(),
                dados.lembrete(), dados.fuso(), dados.origemOperacaoId(), existente.deletedAt(),
                existente.expiresAt(), existente.loteExclusaoId(), existente.criadoEm(), Instant.now());
    }
}
