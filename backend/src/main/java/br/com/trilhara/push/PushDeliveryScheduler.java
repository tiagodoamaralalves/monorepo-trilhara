package br.com.trilhara.push;

import br.com.trilhara.shared.Pseudonimo;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import br.com.trilhara.shared.SystemScope;
import br.com.trilhara.tarefa.Tarefa;
import br.com.trilhara.tarefa.TarefaRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Entrega dos lembretes Web Push.
 *
 * <p>Executa a cada 30 s ({@code trilhara.push.scheduler.interval}), adquire cada item
 * atomicamente com lease de 60 s e só conclui como detentor do lease. Regras:</p>
 * <ul>
 * <li>atraso acima de 15 min em relação a {@code instanteEntregaUtc} →
 * {@code EXPIRADO_FORA_DA_JANELA} <strong>sem</strong> enviar;</li>
 * <li>falha transitória → volta a {@code PENDENTE} com {@code tentativas + 1}; a próxima
 * tentativa é {@code min(2^n s, restante da janela)} e é derivada do próprio
 * {@code instanteEntregaUtc}, que nunca é reescrito;</li>
 * <li>{@code 404}/{@code 410} do provedor → remove a inscrição e continua;</li>
 * <li>envio com {@code TTL: 900} e {@code Urgency: normal}.</li>
 * </ul>
 *
 * <p>Classe {@link SystemScope}: varre todos os tenants. A varredura está em
 * {@link LembretePushFilaRepository}; o acesso por tenant em {@link LembretePushRepository}.</p>
 */
@SystemScope
@ApplicationScoped
public class PushDeliveryScheduler {

    private static final Logger LOG = Logger.getLogger(PushDeliveryScheduler.class);

    /** Janela útil de entrega: notificação atrasada além disso não é enviada. */
    static final Duration JANELA_UTIL_MAXIMA = Duration.ofMinutes(15);
    static final Duration LEASE_DURATION = Duration.ofSeconds(60);
    /** TTL do cabeçalho RFC 8030: igual à janela útil. */
    static final int TTL_SEGUNDOS = 900;
    static final int LOTE_MAXIMO = 50;

    private final LembretePushRepository lembretePushRepository;
    private final LembretePushFilaRepository filaRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final TarefaRepository tarefaRepository;
    private final WebPushSender webPushSender;
    private final PushPayloadFactory payloadFactory;

    @ConfigProperty(name = "trilhara.push.habilitado", defaultValue = "true")
    boolean pushHabilitado;

    @ConfigProperty(name = "trilhara.push.vapid.public-key")
    java.util.Optional<String> vapidPublicKey;

    @ConfigProperty(name = "trilhara.push.vapid.private-key")
    java.util.Optional<String> vapidPrivateKey;

    @Inject
    public PushDeliveryScheduler(
            LembretePushRepository lembretePushRepository,
            LembretePushFilaRepository filaRepository,
            PushSubscriptionRepository pushSubscriptionRepository,
            TarefaRepository tarefaRepository,
            WebPushSender webPushSender,
            PushPayloadFactory payloadFactory) {
        this.lembretePushRepository = lembretePushRepository;
        this.filaRepository = filaRepository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.tarefaRepository = tarefaRepository;
        this.webPushSender = webPushSender;
        this.payloadFactory = payloadFactory;
    }

    @Scheduled(every = "{trilhara.push.scheduler.interval:30s}")
    public void executarScheduler() {
        if (!isPushHabilitado()) {
            return;
        }
        processarLembretes(Instant.now());
    }

    public int processarLembretes(Instant agora) {
        if (!isPushHabilitado()) {
            return 0;
        }

        String leaseToken = UUID.randomUUID().toString();
        List<LembretePush> candidatos = filaRepository.listarCandidatosEnvio(agora, LOTE_MAXIMO);
        int processados = 0;

        for (LembretePush candidato : candidatos) {
            Optional<LembretePush> claim =
                    filaRepository.claim(candidato.id(), leaseToken, agora, agora.plus(LEASE_DURATION));
            if (claim.isEmpty()) {
                // Outro worker venceu a corrida por este item.
                continue;
            }
            processarLembreteUnitario(claim.get(), leaseToken, agora);
            processados++;
        }

        return processados;
    }

    /**
 * Processa uma versão já adquirida. Só o detentor do lease conclui (fencing por
 * {@code leaseToken} no repositório da fila).
 */
    private void processarLembreteUnitario(LembretePush lembrete, String leaseToken, Instant agora) {
        Instant limiteJanela = lembrete.instanteEntregaUtc().plus(JANELA_UTIL_MAXIMA);

        // Atraso acima da janela útil: descarta sem disparar notificação atrasada.
        if (agora.isAfter(limiteJanela)) {
            LOG.infof("Lembrete %s expirado fora da janela de 15 min (instante: %s, agora: %s).",
                    lembrete.id(), lembrete.instanteEntregaUtc(), agora);
            filaRepository.concluir(lembrete.id(), leaseToken,
                    LembretePush.ESTADO_EXPIRADO_FORA_DA_JANELA, agora);
            return;
        }

        List<PushSubscription> subscriptions = pushSubscriptionRepository.listarPorUsuario(lembrete.usuarioId());
        if (subscriptions.isEmpty()) {
            // Nada foi entregue: FALHA_FINAL (ENVIADO significa entrega efetiva ao provedor).
            // o usuarioId não pode aparecer em log; pseudônimo preserva a correlação.
            LOG.infof("event=PUSH_SEM_INSCRICOES lembrete=%s conta=%s",
                    lembrete.id(), Pseudonimo.de(lembrete.usuarioId().toHexString()));
            filaRepository.concluir(lembrete.id(), leaseToken, LembretePush.ESTADO_FALHA_FINAL, agora);
            return;
        }

        Optional<Tarefa> tarefa = tarefaRepository.buscar(lembrete.usuarioId(), lembrete.tarefaId());
        if (tarefa.isEmpty() || tarefa.get().deletedAt() != null) {
            // Tarefa removida entre o agendamento e a entrega: nunca notificar item inexistente.
            filaRepository.concluir(lembrete.id(), leaseToken, LembretePush.ESTADO_CANCELADO, agora);
            return;
        }

        // Fencing de versão: se uma versão mais nova já existe, esta foi substituída e não pode
        // entregar. Cobre a janela entre `inserirNovaVersao` e `cancelarPendentesAnteriores` sem
        // transação multi-documento.
        Optional<LembretePush> vigente =
                lembretePushRepository.buscarVersaoVigente(lembrete.usuarioId(), lembrete.tarefaId());
        if (vigente.isPresent() && vigente.get().versaoAgendamento() > lembrete.versaoAgendamento()) {
            LOG.infof("Lembrete %s (versão %d) substituído pela versão %d; não será entregue.",
                    lembrete.id(), lembrete.versaoAgendamento(), vigente.get().versaoAgendamento());
            filaRepository.concluir(lembrete.id(), leaseToken, LembretePush.ESTADO_CANCELADO, agora);
            return;
        }

        byte[] payload = payloadFactory
                .payloadJson(lembrete.id().toHexString(), lembrete.tarefaId().toHexString(), tarefa.get().titulo())
                .getBytes(StandardCharsets.UTF_8);

        boolean algumaInscricaoValida = false;
        boolean todasInscricoesInvalidas = true;

        for (PushSubscription sub : subscriptions) {
            WebPushSender.PushSendResult resultado = webPushSender.send(sub, payload, TTL_SEGUNDOS);

            if (resultado.sucesso()) {
                algumaInscricaoValida = true;
                todasInscricoesInvalidas = false;
            } else if (resultado.endpointInvalido()) {
                // 404/410: inscrição morta é removida imediatamente de push_subscriptions.
                LOG.infof("Inscrição push %s inválida (status %d). Removendo de push_subscriptions.",
                        sub.endpoint(), resultado.statusCode());
                pushSubscriptionRepository.removerEndpointInvalido(sub.endpoint());
            } else {
                todasInscricoesInvalidas = false;
            }
        }

        if (algumaInscricaoValida) {
            filaRepository.concluir(lembrete.id(), leaseToken, LembretePush.ESTADO_ENVIADO, agora);
            return;
        }
        if (todasInscricoesInvalidas) {
            // Todas as inscrições morreram: não há para quem entregar.
            filaRepository.concluir(lembrete.id(), leaseToken, LembretePush.ESTADO_FALHA_FINAL, agora);
            return;
        }
        tratarFalhaTransitoria(lembrete, leaseToken, agora, limiteJanela);
    }

    /**
 * Falha transitória com backoff exponencial limitado à janela.
 *
 * <p>O item volta a {@code PENDENTE} e o backoff é aplicado pelo filtro da fila a partir de
 * {@code atualizadoEm + min(2^n s, restante da janela)}. Se o restante da janela não comporta
 * nem 1 segundo, não há tempo hábil: o lembrete termina em {@code FALHA_FINAL} em vez de
 * ficar pendente indefinidamente.</p>
 */
    private void tratarFalhaTransitoria(
            LembretePush lembrete, String leaseToken, Instant agora, Instant limiteJanela) {
        long restanteJanelaSegundos = Duration.between(agora, limiteJanela).toSeconds();

        if (restanteJanelaSegundos > 0) {
            filaRepository.devolverParaPendente(lembrete.id(), leaseToken, agora);
        } else {
            LOG.infof("Lembrete %s sem tempo hábil na janela de 15 min; encerrando como FALHA_FINAL.",
                    lembrete.id());
            filaRepository.concluir(lembrete.id(), leaseToken, LembretePush.ESTADO_FALHA_FINAL, agora);
        }
    }

    public boolean isPushHabilitado() {
        if (!pushHabilitado) {
            return false;
        }
        boolean temPublica = vapidPublicKey != null && vapidPublicKey.isPresent() && !vapidPublicKey.get().isBlank();
        boolean temPrivada = vapidPrivateKey != null && vapidPrivateKey.isPresent() && !vapidPrivateKey.get().isBlank();
        return temPublica && temPrivada;
    }
}
