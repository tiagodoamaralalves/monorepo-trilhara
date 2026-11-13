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
import br.com.trilhara.tarefa.ModoLembrete;
import br.com.trilhara.tarefa.Tarefa;
import br.com.trilhara.tarefa.TarefaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Regras de agendamento e reagendamento do lembrete push (D-26 / D-36a).
 *
 * <p>Regra de elegibilidade, avaliada sempre a partir do estado atual da tarefa:</p>
 * <pre>
 * elegível = estado == PENDENTE
 *            ∧ deletedAt == null
 *            ∧ dataVencimento != null
 *            ∧ horario != null
 *            ∧ modoLembrete != SEM_ALERTA
 *            ∧ instanteEntregaUtc &gt; agora
 * </pre>
 *
 * <p>Quando a tarefa é elegível e o instante calculado difere da versão vigente
 * {@code PENDENTE}, cria-se {@code versaoAgendamento + 1} e cancelam-se as anteriores. Quando
 * nada relevante mudou, nenhuma versão nova é criada (idempotência). Quando a tarefa deixa de
 * ser elegível, as versões {@code PENDENTE} são canceladas — a suspensão preserva
 * {@code modoLembrete}/{@code lembreteMinutosAntes} na tarefa.</p>
 */
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
     * Recalcula o agendamento da tarefa aplicando a regra D-36a. Idempotente: chamadas repetidas
     * com o mesmo estado material não criam versões novas.
     *
     * @param tarefa  estado material da tarefa (já persistido)
     * @param modoOverride modo a considerar antes da persistência (criação/edição); {@code null}
     *                     usa o da tarefa
     * @param minutosOverride minutos a considerar antes da persistência; {@code null} usa o da
     *                        tarefa
     * @return a versão {@code PENDENTE} vigente, ou vazio quando a tarefa não é elegível
     */
    public Optional<LembretePush> agendarOuAtualizarLembrete(
            Tarefa tarefa,
            ModoLembrete modoOverride,
            Integer minutosOverride,
            String fusoOverride) {

        if (tarefa == null || tarefa.id() == null || tarefa.usuarioId() == null) {
            return Optional.empty();
        }

        Optional<Instant> instanteOpt = calcularInstanteElegivel(tarefa, modoOverride, minutosOverride, fusoOverride);
        if (instanteOpt.isEmpty()) {
            lembretePushRepository.cancelarPendentes(tarefa.usuarioId(), tarefa.id());
            return Optional.empty();
        }

        Instant instanteEntregaUtc = instanteOpt.get();
        Optional<LembretePush> vigente = lembretePushRepository.buscarVersaoVigente(tarefa.usuarioId(), tarefa.id());

        // Idempotência: mesma tarefa, mesmo instante e versão ainda PENDENTE = nada a fazer.
        if (vigente.isPresent()
                && LembretePush.ESTADO_PENDENTE.equals(vigente.get().estado())
                && instanteEntregaUtc.equals(vigente.get().instanteEntregaUtc())) {
            return vigente;
        }

        long proximaVersao = vigente.map(v -> v.versaoAgendamento() + 1).orElse(LembretePush.VERSAO_INICIAL);
        Instant agora = Instant.now();
        LembretePush nova = new LembretePush(
                new ObjectId(),
                tarefa.usuarioId(),
                tarefa.id(),
                proximaVersao,
                instanteEntregaUtc,
                LembretePush.ESTADO_PENDENTE,
                0,
                null,
                null,
                agora,
                agora);

        if (!lembretePushRepository.inserirNovaVersao(nova)) {
            // Corrida perdida para outra requisição da mesma tarefa: a versão vigente é a fonte.
            return lembretePushRepository.buscarVersaoVigente(tarefa.usuarioId(), tarefa.id());
        }
        lembretePushRepository.cancelarPendentesAnteriores(tarefa.usuarioId(), tarefa.id(), proximaVersao);
        return Optional.of(nova);
    }

    /**
     * Calcula o instante de entrega quando a tarefa é elegível; vazio caso contrário.
     *
     * <p>O cálculo roda mesmo quando a tarefa não é elegível por tempo, para que a decisão fique
     * centralizada: a rejeição de gap DST ({@code 400 INVALID_LOCAL_DATETIME}) precisa acontecer
     * também em criação/edição.</p>
     */
    private Optional<Instant> calcularInstanteElegivel(
            Tarefa tarefa, ModoLembrete modoOverride, Integer minutosOverride, String fusoOverride) {

        if (tarefa.deletedAt() != null) {
            return Optional.empty();
        }
        if (!"PENDENTE".equalsIgnoreCase(tarefa.estado())) {
            return Optional.empty();
        }
        if (tarefa.dataVencimento() == null || tarefa.horario() == null || tarefa.horario().isBlank()) {
            return Optional.empty();
        }

        ModoLembrete modo = modoOverride != null ? modoOverride : tarefa.modoLembrete();
        if (modo == ModoLembrete.SEM_ALERTA) {
            return Optional.empty();
        }
        Integer minutosAntes = resolverMinutosAntes(tarefa, modo, minutosOverride);
        if (minutosAntes == null || minutosAntes < 0) {
            return Optional.empty();
        }

        Optional<LocalTime> horarioOpt = parseHorario(tarefa.horario(), tarefa.id());
        if (horarioOpt.isEmpty()) {
            return Optional.empty();
        }

        ZoneId zoneId = resolverZoneId(tarefa, fusoOverride);
        Instant instante = LembretePushCalculator.calcularInstanteEntregaUtc(
                tarefa.dataVencimento(), horarioOpt.get(), zoneId, minutosAntes);

        if (instante == null || !instante.isAfter(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(instante);
    }

    /**
     * Resolve os minutos de antecedência (D-26).
     *
     * <p>{@code HERDAR} consulta a preferência global do usuário no momento do agendamento;
     * {@code SEM_ALERTA} devolve {@code null} (nenhum push); {@code PERSONALIZADO} usa o
     * deslocamento gravado na tarefa.</p>
     */
    private Integer resolverMinutosAntes(Tarefa tarefa, ModoLembrete modo, Integer minutosOverride) {
        return switch (modo) {
            case SEM_ALERTA -> null;
            case PERSONALIZADO -> minutosOverride != null ? minutosOverride : tarefa.lembreteMinutosAntes();
            case HERDAR -> userRepository.obterPadraoLembreteMinutos(tarefa.usuarioId());
        };
    }

    /**
     * Fuso do agendamento: o persistido na tarefa prevalece; {@code X-Timezone} só entra quando a
     * tarefa ainda não tem fuso (D-26) e é materializado pelo chamador ao salvar.
     */
    private ZoneId resolverZoneId(Tarefa tarefa, String fusoAgendamentoOverride) {
        if (tarefa.fusoAgendamento() != null && !tarefa.fusoAgendamento().isBlank()) {
            return TimezoneContext.resolverZoneId(tarefa.fusoAgendamento());
        }
        if (fusoAgendamentoOverride != null && !fusoAgendamentoOverride.isBlank()) {
            return TimezoneContext.resolverZoneId(fusoAgendamentoOverride);
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

    /** Cancela as versões {@code PENDENTE} (conclusão, exclusão, pulo). */
    public void cancelarLembrete(ObjectId usuarioId, ObjectId tarefaId) {
        if (usuarioId != null && tarefaId != null) {
            lembretePushRepository.cancelarPendentes(usuarioId, tarefaId);
        }
    }

    /** Alias semântico de {@link #cancelarLembrete}: remover o horário suspende, não descarta. */
    public void suspenderLembrete(ObjectId usuarioId, ObjectId tarefaId) {
        cancelarLembrete(usuarioId, tarefaId);
    }

    /** Cancela em lote todos os lembretes de tarefas de uma série (exclusão de escopo). */
    public void cancelarLembretesDaSerie(ObjectId usuarioId, List<ObjectId> tarefasIds) {
        if (usuarioId == null || tarefasIds == null) {
            return;
        }
        for (ObjectId tarefaId : tarefasIds) {
            cancelarLembrete(usuarioId, tarefaId);
        }
    }

    /**
     * Atualiza a preferência global do usuário e recalcula lembretes exclusivamente das tarefas
     * herdeiras ({@code modoLembrete == HERDAR}, D-26.2). Overrides {@code PERSONALIZADO} e
     * {@code SEM_ALERTA} nunca são tocados.
     *
     * @return quantidade de tarefas herdeiras efetivamente reagendadas
     */
    public long atualizarPreferenciaGlobal(ObjectId usuarioId, int novoPadraoMinutos) {
        userRepository.salvarPadraoLembreteMinutos(usuarioId, novoPadraoMinutos);

        List<Tarefa> tarefasHerdeiras = tarefaRepository.listarAtivasComHorarioEPadraoLembrete(usuarioId);
        long recalculados = 0;

        for (Tarefa t : tarefasHerdeiras) {
            try {
                // O modo da própria tarefa é HERDAR; o novo padrão entra pelo override de minutos.
                Optional<LembretePush> agendado =
                        agendarOuAtualizarLembrete(t, ModoLembrete.HERDAR, novoPadraoMinutos, null);
                if (agendado.isPresent()) {
                    recalculados++;
                }
            } catch (Exception e) {
                // Uma tarefa com data inválida (ex.: gap DST) nunca impede o recálculo das demais.
                LOG.warnf("Falha ao recalcular lembrete da tarefa %s: %s", t.id(), e.getMessage());
            }
        }

        return recalculados;
    }
}
