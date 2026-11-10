package br.com.trilhara.push;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PushDeliveryScheduler {

    private static final Logger LOG = Logger.getLogger(PushDeliveryScheduler.class);
    private static final Duration JANELA_UTIL_MAXIMA = Duration.ofMinutes(15);
    private static final Duration LEASE_DURATION = Duration.ofSeconds(60);

    private final LembretePushRepository lembretePushRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final WebPushSender webPushSender;
    private final ObjectMapper objectMapper;

    @ConfigProperty(name = "trilhara.push.habilitado", defaultValue = "true")
    boolean pushHabilitado;

    @ConfigProperty(name = "trilhara.push.vapid.public-key")
    java.util.Optional<String> vapidPublicKey;

    @ConfigProperty(name = "trilhara.push.vapid.private-key")
    java.util.Optional<String> vapidPrivateKey;

    @Inject
    public PushDeliveryScheduler(
            LembretePushRepository lembretePushRepository,
            PushSubscriptionRepository pushSubscriptionRepository,
            WebPushSender webPushSender,
            ObjectMapper objectMapper) {
        this.lembretePushRepository = lembretePushRepository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.webPushSender = webPushSender;
        this.objectMapper = objectMapper;
    }

    public PushDeliveryScheduler(
            LembretePushRepository lembretePushRepository,
            PushSubscriptionRepository pushSubscriptionRepository,
            WebPushSender webPushSender,
            ObjectMapper objectMapper,
            boolean pushHabilitado) {
        this.lembretePushRepository = lembretePushRepository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.webPushSender = webPushSender;
        this.objectMapper = objectMapper;
        this.pushHabilitado = pushHabilitado;
        this.vapidPublicKey = java.util.Optional.of("test-key-mock");
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

        String workerId = UUID.randomUUID().toString();
        List<LembretePush> candidatos = lembretePushRepository.listarCandidatosEnvio(agora, 50);
        int processados = 0;

        for (LembretePush candidato : candidatos) {
            Instant leaseAte = agora.plus(LEASE_DURATION);
            var claimOpt = lembretePushRepository.claimLembrete(candidato.id(), workerId, agora, leaseAte);
            if (claimOpt.isEmpty()) {
                // Outro worker adquiriu o lembrete
                continue;
            }

            LembretePush lembrete = claimOpt.get();
            processarLembreteUnitario(lembrete, workerId, agora);
            processados++;
        }

        return processados;
    }

    private void processarLembreteUnitario(LembretePush lembrete, String workerId, Instant agora) {
        Instant limiteJanela = lembrete.instanteEntregaUtc().plus(JANELA_UTIL_MAXIMA);

        // Se expirou além de 15 minutos do instante pretendido, descarta sem disparar
        if (agora.isAfter(limiteJanela)) {
            LOG.infof("Lembrete %s expirado fora da janela de 15 min (instante: %s, agora: %s).",
                    lembrete.id(), lembrete.instanteEntregaUtc(), agora);
            lembretePushRepository.concluirLembrete(
                    lembrete.id(),
                    workerId,
                    LembretePush.ESTADO_EXPIRADO_FORA_DA_JANELA,
                    "Atraso no scheduler superior a 15 minutos em relação ao horário agendado.",
                    agora
            );
            return;
        }

        List<PushSubscription> subscriptions = pushSubscriptionRepository.listarPorUsuario(lembrete.usuarioId());
        if (subscriptions.isEmpty()) {
            // Nenhuma inscrição cadastrada no dispositivo
            LOG.infof("Lembrete %s concluído: usuário %s não possui inscrições ativas.",
                    lembrete.id(), lembrete.usuarioId());
            lembretePushRepository.concluirLembrete(
                    lembrete.id(),
                    workerId,
                    LembretePush.ESTADO_ENVIADO,
                    "Nenhuma inscrição push cadastrada para o usuário.",
                    agora
            );
            return;
        }

        String payloadJson = montarPayloadJson(lembrete);
        boolean algumaInscricaoValida = false;
        boolean todasInscricoesInvalidas = true;
        String ultimoErro = null;

        for (PushSubscription sub : subscriptions) {
            WebPushSender.PushSendResult resultado = webPushSender.send(sub, payloadJson, 900);

            if (resultado.sucesso()) {
                algumaInscricaoValida = true;
                todasInscricoesInvalidas = false;
            } else if (resultado.endpointInvalido()) {
                // 410 Gone ou 404 Not Found: remove imediatamente a inscrição de push_subscriptions
                LOG.infof("Inscrição push %s inválida/expirada (status %d). Removendo de push_subscriptions.",
                        sub.endpoint(), resultado.statusCode());
                pushSubscriptionRepository.removerPorEndpoint(sub.endpoint());
                ultimoErro = resultado.erro();
            } else {
                todasInscricoesInvalidas = false;
                ultimoErro = resultado.erro();
            }
        }

        if (algumaInscricaoValida || (!subscriptions.isEmpty() && todasInscricoesInvalidas)) {
            // Se ao menos uma enviou com sucesso ou se todas eram inválidas e foram devidamente limpas
            String estadoFinal = algumaInscricaoValida ? LembretePush.ESTADO_ENVIADO : LembretePush.ESTADO_FALHA_FINAL;
            lembretePushRepository.concluirLembrete(lembrete.id(), workerId, estadoFinal, ultimoErro, agora);
        } else {
            // Falha temporária de rede/provedor: verifica se ainda há tempo hábil na janela de 15 minutos
            Instant proximaTentativa = agora.plusSeconds((long) Math.min(Math.pow(2, lembrete.tentativas()), 120));
            if (proximaTentativa.isBefore(limiteJanela)) {
                lembretePushRepository.remarcarFalhaTemporaria(lembrete.id(), workerId, proximaTentativa, ultimoErro, agora);
            } else {
                lembretePushRepository.concluirLembrete(
                        lembrete.id(),
                        workerId,
                        LembretePush.ESTADO_EXPIRADO_FORA_DA_JANELA,
                        "Limite de tentativas atingido ou fora da janela útil de 15min: " + ultimoErro,
                        agora
                );
            }
        }
    }

    private String montarPayloadJson(LembretePush lembrete) {
        try {
            var dados = java.util.Map.of(
                    "lembreteId", lembrete.id().toHexString(),
                    "tarefaId", lembrete.tarefaId().toHexString(),
                    "titulo", lembrete.tituloTarefa() != null ? lembrete.tituloTarefa() : "Lembrete de Tarefa",
                    "mensagem", "Horário previsto para a tarefa: " + (lembrete.tituloTarefa() != null ? lembrete.tituloTarefa() : "Tarefa agendada"),
                    "url", "/tarefas?tarefaId=" + lembrete.tarefaId().toHexString()
            );
            return objectMapper.writeValueAsString(dados);
        } catch (Exception e) {
            return "{\"tarefaId\":\"" + lembrete.tarefaId().toHexString() + "\"}";
        }
    }

    public boolean isPushHabilitado() {
        if (!pushHabilitado) {
            return false;
        }
        return vapidPublicKey != null && vapidPublicKey.isPresent() && !vapidPublicKey.get().isBlank();
    }
}
