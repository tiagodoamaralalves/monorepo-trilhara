package br.com.trilhara.push;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.jboss.logging.Logger;

import br.com.trilhara.auth.UserRepository;
import br.com.trilhara.shared.TimezoneContext;
import br.com.trilhara.tarefa.Tarefa;
import br.com.trilhara.tarefa.TarefaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LembretePushService {

    private static final Logger LOG = Logger.getLogger(LembretePushService.class);

    private final LembretePushRepository lembretePushRepository;
    private final UserRepository userRepository;
    private final TarefaRepository tarefaRepository;
    private final TimezoneContext timezoneContext;

    @Inject
    public LembretePushService(
            LembretePushRepository lembretePushRepository,
            UserRepository userRepository,
            TarefaRepository tarefaRepository,
            TimezoneContext timezoneContext) {
        this.lembretePushRepository = lembretePushRepository;
        this.userRepository = userRepository;
        this.tarefaRepository = tarefaRepository;
        this.timezoneContext = timezoneContext;
    }

    /**
     * Agenda ou atualiza o lembrete de uma tarefa manual com horário.
     */
    public Optional<LembretePush> agendarOuAtualizarLembrete(
            Tarefa tarefa,
            Integer lembreteMinutosAntesOverride,
            Boolean usaPadraoGlobalOverride,
            String fusoAgendamentoOverride) {

        if (tarefa == null || tarefa.id() == null) {
            return Optional.empty();
        }

        if (deveCancelarLembrete(tarefa)) {
            lembretePushRepository.cancelarPorTarefa(tarefa.usuarioId(), tarefa.id());
            return Optional.empty();
        }

        Integer minutosAntes = resolverMinutosAntes(tarefa, lembreteMinutosAntesOverride, usaPadraoGlobalOverride);
        if (minutosAntes == null || minutosAntes < 0) {
            lembretePushRepository.cancelarPorTarefa(tarefa.usuarioId(), tarefa.id());
            return Optional.empty();
        }

        ZoneId zoneId = resolverZoneId(tarefa, fusoAgendamentoOverride);
        Optional<LocalTime> horarioOpt = parseHorario(tarefa.horario(), tarefa.id());
        if (horarioOpt.isEmpty()) {
            return Optional.empty();
        }

        LocalTime horario = horarioOpt.get();
        Instant instanteEntregaUtc = LembretePushCalculator.calcularInstanteEntregaUtc(
                tarefa.dataVencimento(),
                horario,
                zoneId,
                minutosAntes
        );

        Optional<LembretePush> existente = lembretePushRepository.buscarPorTarefa(tarefa.usuarioId(), tarefa.id());
        int versao = existente.map(e -> e.versaoAgendamento() + 1).orElse(1);

        LembretePush novoLembrete = new LembretePush(
                existente.map(LembretePush::id).orElse(new ObjectId()),
                tarefa.usuarioId(),
                tarefa.id(),
                versao,
                instanteEntregaUtc,
                minutosAntes,
                zoneId.getId(),
                LembretePush.ESTADO_PENDENTE,
                tarefa.titulo(),
                0,
                null,
                null,
                null,
                null,
                existente.map(LembretePush::criadoEm).orElse(Instant.now()),
                Instant.now()
        );

        lembretePushRepository.salvarOuAtualizar(novoLembrete);
        return Optional.of(novoLembrete);
    }

    private boolean deveCancelarLembrete(Tarefa tarefa) {
        if (tarefa.dataVencimento() == null || tarefa.horario() == null || tarefa.horario().isBlank()) {
            return true;
        }
        return tarefa.concluida() || "CONCLUIDA".equalsIgnoreCase(tarefa.estado()) || tarefa.deletedAt() != null;
    }

    private Integer resolverMinutosAntes(Tarefa tarefa, Integer lembreteMinutosAntesOverride, Boolean usaPadraoGlobalOverride) {
        boolean usaPadrao = usaPadraoGlobalOverride != null
                ? usaPadraoGlobalOverride
                : tarefa.usaPadraoGlobalLembrete();
        if (usaPadrao) {
            return userRepository.obterPadraoLembreteMinutos(tarefa.usuarioId());
        }
        if (lembreteMinutosAntesOverride != null) {
            return lembreteMinutosAntesOverride;
        }
        return tarefa.lembreteMinutosAntes();
    }

    private ZoneId resolverZoneId(Tarefa tarefa, String fusoAgendamentoOverride) {
        if (fusoAgendamentoOverride != null && !fusoAgendamentoOverride.isBlank()) {
            return TimezoneContext.resolverZoneId(fusoAgendamentoOverride);
        }
        if (tarefa.fusoAgendamento() != null && !tarefa.fusoAgendamento().isBlank()) {
            return TimezoneContext.resolverZoneId(tarefa.fusoAgendamento());
        }
        try {
            return timezoneContext.zoneId();
        } catch (Exception _) {
            return TimezoneContext.DEFAULT_ZONE;
        }
    }

    private Optional<LocalTime> parseHorario(String horarioStr, ObjectId tarefaId) {
        try {
            return Optional.of(LocalTime.parse(horarioStr.trim()));
        } catch (Exception _) {
            LOG.warnf("Horário inválido na tarefa %s: %s", tarefaId, horarioStr);
            return Optional.empty();
        }
    }
    public void cancelarLembrete(ObjectId usuarioId, ObjectId tarefaId) {
        if (usuarioId != null && tarefaId != null) {
            lembretePushRepository.cancelarPorTarefa(usuarioId, tarefaId);
        }
    }

    public void suspenderLembrete(ObjectId usuarioId, ObjectId tarefaId) {
        if (usuarioId != null && tarefaId != null) {
            lembretePushRepository.cancelarPorTarefa(usuarioId, tarefaId);
        }
    }

    /**
     * Atualiza a preferência global do usuário e recalcula lembretes PENDENTES
     * exclusivamente das tarefas herdeiras (usaPadraoGlobalLembrete == true).
     */
    public long atualizarPreferenciaGlobal(ObjectId usuarioId, int novoPadraoMinutos) {
        userRepository.salvarPadraoLembreteMinutos(usuarioId, novoPadraoMinutos);

        List<Tarefa> tarefasHerdeiras = tarefaRepository.listarAtivasComHorarioEPadraoLembrete(usuarioId);
        long recalculados = 0;

        for (Tarefa t : tarefasHerdeiras) {
            if (t.dataVencimento() == null || t.horario() == null || t.horario().isBlank()) {
                continue;
            }

            ZoneId zoneId = t.fusoAgendamento() != null && !t.fusoAgendamento().isBlank()
                    ? TimezoneContext.resolverZoneId(t.fusoAgendamento())
                    : TimezoneContext.DEFAULT_ZONE;

            try {
                LocalTime horario = LocalTime.parse(t.horario().trim());
                Instant novoInstante = LembretePushCalculator.calcularInstanteEntregaUtc(
                        t.dataVencimento(),
                        horario,
                        zoneId,
                        novoPadraoMinutos
                );

                Optional<LembretePush> existente = lembretePushRepository.buscarPorTarefa(usuarioId, t.id());
                if (existente.isPresent() && LembretePush.ESTADO_PENDENTE.equals(existente.get().estado())) {
                    lembretePushRepository.atualizarPendente(existente.get().id(), novoInstante, novoPadraoMinutos);
                    recalculados++;
                } else if (existente.isEmpty()) {
                    agendarOuAtualizarLembrete(t, novoPadraoMinutos, true, zoneId.getId());
                    recalculados++;
                }
            } catch (Exception e) {
                LOG.warnf("Falha ao recalcular lembrete da tarefa %s: %s", t.id(), e.getMessage());
            }
        }

        return recalculados;
    }
}
