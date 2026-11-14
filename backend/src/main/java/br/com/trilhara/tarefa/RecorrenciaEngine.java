package br.com.trilhara.tarefa;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;

import com.mongodb.MongoWriteException;

import br.com.trilhara.push.LembretePushService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RecorrenciaEngine {

    private final TarefaRepository tarefaRepository;
    private final SerieRecorrenteRepository serieRepository;
    private final ListaTarefasRepository listaRepository;
    private final LembretePushService lembretePushService;

    public RecorrenciaEngine(
            TarefaRepository tarefaRepository,
            SerieRecorrenteRepository serieRepository) {
        this(tarefaRepository, serieRepository, null, null);
    }

    @Inject
    public RecorrenciaEngine(
            TarefaRepository tarefaRepository,
            SerieRecorrenteRepository serieRepository,
            ListaTarefasRepository listaRepository,
            LembretePushService lembretePushService) {
        this.tarefaRepository = tarefaRepository;
        this.serieRepository = serieRepository;
        this.listaRepository = listaRepository;
        this.lembretePushService = lembretePushService;
    }

    public void expandirSeriesDoUsuario(ObjectId usuarioId, LocalDate hojeLocal, LocalDate fimIntervaloConsultado) {
        List<SerieRecorrente> seriesAtivas = serieRepository.listarAtivas(usuarioId);
        Set<ObjectId> listasLixeira = (listaRepository != null)
                ? listaRepository.listarLixeira(usuarioId, Instant.now()).stream().map(ListaTarefas::id).collect(Collectors.toSet())
                : Set.of();

        for (SerieRecorrente serie : seriesAtivas) {
            if (serie.listaId() != null && listasLixeira.contains(serie.listaId())) {
                continue; // série suspensa enquanto a lista estiver na lixeira (§7.3)
            }
            expandirSerie(serie, hojeLocal, fimIntervaloConsultado);
        }
    }

    public void expandirSerie(SerieRecorrente serie, LocalDate hojeLocal, LocalDate fimIntervaloConsultado) {
        if (!serie.ativa() || serie.deletedAt() != null) {
            return;
        }

        if (serieEstaComListaNaLixeira(serie)) {
            return;
        }

        LocalDate dataInicio = serie.dataInicio();
        if (dataInicio == null) {
            return;
        }

        LocalDate alvo = calcularAlvo(hojeLocal, fimIntervaloConsultado);

        if (deveInativarPorDataFimAnterior(serie, dataInicio)) {
            inativarSerie(serie);
            return;
        }

        List<LocalDate> candidatas = calcularDatasCandidatas(serie, alvo);
        if (candidatas.isEmpty()) {
            if (serie.geradoAte() == null || alvo.isAfter(serie.geradoAte())) {
                atualizarCursorSerie(serie, alvo, serie.totalGeradas(), serie.ativa());
            }
            return;
        }

        candidatas.sort(null);
        processarCandidatas(serie, candidatas, dataInicio, alvo);
    }

    private boolean serieEstaComListaNaLixeira(SerieRecorrente serie) {
        if (listaRepository != null && serie.listaId() != null) {
            Optional<ListaTarefas> listaOpt = listaRepository.buscarQualquerStatus(serie.listaId());
            return listaOpt.isPresent() && listaOpt.get().deletedAt() != null;
        }
        return false;
    }

    private LocalDate calcularAlvo(LocalDate hojeLocal, LocalDate fimIntervaloConsultado) {
        LocalDate padrao90d = hojeLocal.plusDays(90);
        LocalDate alvo = (fimIntervaloConsultado != null && fimIntervaloConsultado.isAfter(padrao90d))
                ? fimIntervaloConsultado
                : padrao90d;

        LocalDate tetoMax = hojeLocal.plusDays(366);
        return alvo.isAfter(tetoMax) ? tetoMax : alvo;
    }

    private boolean deveInativarPorDataFimAnterior(SerieRecorrente serie, LocalDate dataInicio) {
        return "EM_DATA".equalsIgnoreCase(serie.tipoTermino())
                && serie.dataFim() != null
                && serie.dataFim().isBefore(dataInicio);
    }

    private void inativarSerie(SerieRecorrente serie) {
        serieRepository.atualizar(new SerieRecorrente(
                serie.id(), serie.usuarioId(), serie.listaId(), serie.titulo(), serie.descricao(),
                serie.prioridade(), serie.horario(), serie.subtarefasModelo(), serie.frequencia(),
                serie.intervalo(), serie.diasDaSemana(), serie.modoMensal(), serie.diaDoMes(),
                serie.ordinalSemana(), serie.diaSemanaOrdinal(), serie.tipoTermino(), serie.dataInicio(),
                serie.dataFim(), serie.maxOcorrencias(), serie.geradoAte(), serie.totalGeradas(),
                false, serie.modoLembrete(), serie.lembreteMinutosAntes(), serie.fusoAgendamento(),
                serie.origemOperacaoId(), serie.deletedAt(), serie.expiresAt(), serie.loteExclusaoId(),
                serie.criadoEm(), Instant.now()
        ));
    }

    private void processarCandidatas(
            SerieRecorrente serie,
            List<LocalDate> candidatas,
            LocalDate dataInicio,
            LocalDate alvo) {
        int totalGeradas = serie.totalGeradas();
        LocalDate ultimoGeradoAte = serie.geradoAte() != null ? serie.geradoAte() : dataInicio.minusDays(1);
        boolean ativa = serie.ativa();
        Instant agora = Instant.now();

        List<LocalDate> permitidas = aplicarLimiteTermino(serie, candidatas);
        boolean terminoAtingido = permitidas.size() < candidatas.size();

        for (LocalDate dataOcorrencia : permitidas) {
            materializarOcorrenciaSeNecessario(serie, dataOcorrencia, agora);

            totalGeradas++;
            if (dataOcorrencia.isAfter(ultimoGeradoAte)) {
                ultimoGeradoAte = dataOcorrencia;
            }
            if ("APOS_OCORRENCIAS".equalsIgnoreCase(serie.tipoTermino())
                    && serie.maxOcorrencias() != null
                    && totalGeradas >= serie.maxOcorrencias()) {
                ativa = false;
            }
        }
        if (terminoAtingido) {
            // O término foi alcançado nesta janela: a série não volta a materializar.
            ativa = false;
        }

        LocalDate cursorFinal = ultimoGeradoAte.isAfter(alvo) ? ultimoGeradoAte : alvo;
        atualizarCursorSerie(serie, cursorFinal, totalGeradas, ativa);
    }

    private void materializarOcorrenciaSeNecessario(SerieRecorrente serie, LocalDate dataOcorrencia, Instant agora) {
        if (tarefaRepository.buscarPorOcorrencia(serie.usuarioId(), serie.id(), dataOcorrencia).isPresent()) {
            return;
        }

        List<Subtarefa> subtarefasClonadas = new ArrayList<>();
        if (serie.subtarefasModelo() != null) {
            for (Subtarefa sub : serie.subtarefasModelo()) {
                subtarefasClonadas.add(new Subtarefa(
                        new ObjectId().toHexString(),
                        sub.titulo(),
                        false,
                        sub.ordem()
                ));
            }
        }

        Tarefa ocorrencia = new Tarefa(
                new ObjectId(),
                serie.usuarioId(),
                serie.listaId(),
                serie.id(),
                dataOcorrencia,
                false,
                serie.titulo(),
                serie.descricao(),
                serie.prioridade(),
                dataOcorrencia,
                serie.horario(),
                "PENDENTE",
                false,
                null,
                0,
                subtarefasClonadas,
                serie.modoLembrete(),
                serie.lembreteMinutosAntes(),
                serie.fusoAgendamento(),
                null,
                null,
                null,
                agora,
                agora
        );

        try {
            tarefaRepository.inserir(ocorrencia);
            agendarLembreteDaOcorrencia(ocorrencia);
        } catch (MongoWriteException e) {
            if (e.getError().getCode() != 11000) {
                throw e;
            }
        }
    }

    /**
 * Agenda o lembrete da ocorrência recém-materializada (§11: "ocorrências recorrentes herdam
 * lembrete").
 *
 * <p>A inserção é idempotente ({@code (tarefaId, versaoAgendamento)}), então uma ocorrência
 * já existente nunca duplica versão. {@code lembretePushService} é opcional no construtor
 * para permitir o uso da engine em testes puros de cálculo de datas.</p>
 */
    private void agendarLembreteDaOcorrencia(Tarefa ocorrencia) {
        if (lembretePushService == null) {
            return;
        }
        lembretePushService.agendarOuAtualizarLembrete(ocorrencia, null, null, null);
    }

    /**
 * Trunca as candidatas pelas regras de término vigentes.
 *
 * <p>{@code APOS_OCORRENCIAS} conta as posições já consumidas em {@code totalGeradas} —
 * inclusive as concluídas, canceladas, movidas para a lixeira ou editadas
 * individualmente —, porque cada posição programada ocupa um lugar na contagem.</p>
 */
    public List<LocalDate> aplicarLimiteTermino(SerieRecorrente serie, List<LocalDate> candidatas) {
        if (candidatas == null || candidatas.isEmpty()) {
            return List.of();
        }
        if ("APOS_OCORRENCIAS".equalsIgnoreCase(serie.tipoTermino()) && serie.maxOcorrencias() != null) {
            int restantes = serie.maxOcorrencias() - serie.totalGeradas();
            if (restantes <= 0) {
                return List.of();
            }
            return candidatas.size() <= restantes ? candidatas : candidatas.subList(0, restantes);
        }
        if ("EM_DATA".equalsIgnoreCase(serie.tipoTermino()) && serie.dataFim() != null) {
            return candidatas.stream().filter(d -> !d.isAfter(serie.dataFim())).toList();
        }
        return candidatas;
    }

    private void atualizarCursorSerie(SerieRecorrente serie, LocalDate novoGeradoAte, int totalGeradas, boolean ativa) {
        SerieRecorrente atualizada = new SerieRecorrente(
                serie.id(),
                serie.usuarioId(),
                serie.listaId(),
                serie.titulo(),
                serie.descricao(),
                serie.prioridade(),
                serie.horario(),
                serie.subtarefasModelo(),
                serie.frequencia(),
                serie.intervalo(),
                serie.diasDaSemana(),
                serie.modoMensal(),
                serie.diaDoMes(),
                serie.ordinalSemana(),
                serie.diaSemanaOrdinal(),
                serie.tipoTermino(),
                serie.dataInicio(),
                serie.dataFim(),
                serie.maxOcorrencias(),
                novoGeradoAte,
                totalGeradas,
                ativa,
                serie.modoLembrete(),
                serie.lembreteMinutosAntes(),
                serie.fusoAgendamento(),
                serie.origemOperacaoId(),
                serie.deletedAt(),
                serie.expiresAt(),
                serie.loteExclusaoId(),
                serie.criadoEm(),
                Instant.now()
        );
        serieRepository.atualizar(atualizada);
    }

    public List<LocalDate> calcularDatasCandidatas(SerieRecorrente serie, LocalDate alvo) {
        LocalDate dataInicio = serie.dataInicio();
        if (dataInicio == null || dataInicio.isAfter(alvo)) {
            return List.of();
        }

        LocalDate cursorExclusivo = serie.geradoAte();
        int intervalo = Math.max(1, serie.intervalo());
        String freq = serie.frequencia() != null ? serie.frequencia().toUpperCase().trim() : "DIARIO";

        return switch (freq) {
            case "DIARIO" -> calcularCandidatasDiarias(dataInicio, alvo, cursorExclusivo, intervalo);
            case "SEMANAL" -> calcularCandidatasSemanais(serie, dataInicio, alvo, cursorExclusivo, intervalo);
            case "MENSAL" -> calcularCandidatasMensais(serie, dataInicio, alvo, cursorExclusivo, intervalo);
            default -> List.of();
        };
    }

    private List<LocalDate> calcularCandidatasDiarias(
            LocalDate dataInicio,
            LocalDate alvo,
            LocalDate cursorExclusivo,
            int intervalo) {
        List<LocalDate> resultado = new ArrayList<>();
        long k = 0;
        LocalDate cand = dataInicio;
        if (cursorExclusivo != null && cursorExclusivo.isAfter(dataInicio)) {
            long diff = ChronoUnit.DAYS.between(dataInicio, cursorExclusivo);
            k = (diff / intervalo) + 1;
            cand = dataInicio.plusDays(k * intervalo);
        }
        while (!cand.isAfter(alvo)) {
            if (cursorExclusivo == null || cand.isAfter(cursorExclusivo)) {
                resultado.add(cand);
            }
            k++;
            cand = dataInicio.plusDays(k * intervalo);
        }
        return resultado;
    }

    private List<LocalDate> calcularCandidatasSemanais(
            SerieRecorrente serie,
            LocalDate dataInicio,
            LocalDate alvo,
            LocalDate cursorExclusivo,
            int intervalo) {
        List<Integer> dias = obterDiasSemana(serie, dataInicio);
        LocalDate segundaBase = dataInicio.with(DayOfWeek.MONDAY);
        long semanaIdx = obterIndiceSemanaInicial(segundaBase, dataInicio, cursorExclusivo, intervalo);
        return coletarCandidatasSemanais(dias, dataInicio, alvo, cursorExclusivo, intervalo, segundaBase, semanaIdx);
    }

    private List<Integer> obterDiasSemana(SerieRecorrente serie, LocalDate dataInicio) {
        return serie.diasDaSemana() != null && !serie.diasDaSemana().isEmpty()
                ? serie.diasDaSemana()
                : List.of(dataInicio.getDayOfWeek().getValue());
    }

    private long obterIndiceSemanaInicial(
            LocalDate segundaBase, LocalDate dataInicio, LocalDate cursorExclusivo, int intervalo) {
        if (cursorExclusivo == null || !cursorExclusivo.isAfter(dataInicio)) {
            return 0;
        }
        LocalDate segundaCursor = cursorExclusivo.with(DayOfWeek.MONDAY);
        long semanasAteCursor = ChronoUnit.WEEKS.between(segundaBase, segundaCursor);
        return Math.max(0, (semanasAteCursor / intervalo) * intervalo);
    }

    private List<LocalDate> coletarCandidatasSemanais(
            List<Integer> dias,
            LocalDate dataInicio,
            LocalDate alvo,
            LocalDate cursorExclusivo,
            int intervalo,
            LocalDate segundaBase,
            long semanaIdx) {
        List<LocalDate> resultado = new ArrayList<>();
        LocalDate segundaSemana = segundaBase.plusWeeks(semanaIdx);
        while (!segundaSemana.isAfter(alvo)) {
            for (Integer diaSemana : dias) {
                if (diaSemana != null && diaSemana >= 1 && diaSemana <= 7) {
                    LocalDate cand = segundaSemana.plusDays(diaSemana - 1L);
                    boolean dentroDoIntervalo = !cand.isBefore(dataInicio) && !cand.isAfter(alvo);
                    boolean depoisDoCursor = cursorExclusivo == null || cand.isAfter(cursorExclusivo);
                    if (dentroDoIntervalo && depoisDoCursor) {
                        resultado.add(cand);
                    }
                }
            }
            semanaIdx += intervalo;
            segundaSemana = segundaBase.plusWeeks(semanaIdx);
        }
        return resultado;
    }

    private List<LocalDate> calcularCandidatasMensais(
            SerieRecorrente serie,
            LocalDate dataInicio,
            LocalDate alvo,
            LocalDate cursorExclusivo,
            int intervalo) {
        List<LocalDate> resultado = new ArrayList<>();
        long mesIdx = 0;
        if (cursorExclusivo != null && cursorExclusivo.isAfter(dataInicio)) {
            long meses = ChronoUnit.MONTHS.between(
                    YearMonth.from(dataInicio),
                    YearMonth.from(cursorExclusivo));
            mesIdx = Math.max(0, (meses / intervalo) * intervalo);
        }

        String modo = serie.modoMensal() != null ? serie.modoMensal().toUpperCase().trim() : "DIA_DO_MES";

        LocalDate mesBase = dataInicio.plusMonths(mesIdx);
        while (!mesBase.withDayOfMonth(1).isAfter(alvo)) {
            LocalDate cand = "ORDINAL_SEMANA".equalsIgnoreCase(modo)
                    ? calcularDataMensalOrdinal(serie, mesBase, dataInicio)
                    : calcularDataMensalDiaDoMes(serie, mesBase, dataInicio);

            if (cand != null && !cand.isBefore(dataInicio) && !cand.isAfter(alvo)
                    && (cursorExclusivo == null || cand.isAfter(cursorExclusivo))) {
                resultado.add(cand);
            }

            mesIdx += intervalo;
            mesBase = dataInicio.plusMonths(mesIdx);
        }
        return resultado;
    }

    private LocalDate calcularDataMensalOrdinal(SerieRecorrente serie, LocalDate mesBase, LocalDate dataInicio) {
        int ordinal = serie.ordinalSemana() != null ? serie.ordinalSemana() : 1;
        int diaSemana = serie.diaSemanaOrdinal() != null ? serie.diaSemanaOrdinal() : dataInicio.getDayOfWeek().getValue();

        if (ordinal == -1) {
            LocalDate ultimoDia = YearMonth.from(mesBase).atEndOfMonth();
            while (ultimoDia.getDayOfWeek().getValue() != diaSemana) {
                ultimoDia = ultimoDia.minusDays(1);
            }
            return ultimoDia;
        }

        if (ordinal >= 1 && ordinal <= 4) {
            LocalDate primeiro = mesBase.withDayOfMonth(1);
            while (primeiro.getDayOfWeek().getValue() != diaSemana) {
                primeiro = primeiro.plusDays(1);
            }
            LocalDate possivel = primeiro.plusWeeks(ordinal - 1L);
            if (possivel.getMonth().equals(mesBase.getMonth())) {
                return possivel;
            }
        }
        return null;
    }

    private LocalDate calcularDataMensalDiaDoMes(SerieRecorrente serie, LocalDate mesBase, LocalDate dataInicio) {
        int diaDesejado = serie.diaDoMes() != null ? serie.diaDoMes() : dataInicio.getDayOfMonth();
        int maxDias = YearMonth.from(mesBase).lengthOfMonth();
        int diaReal = Math.min(diaDesejado, maxDias);
        return mesBase.withDayOfMonth(diaReal);
    }
}
