package br.com.trilhara.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.inject.Inject;

@QuarkusTest
class PushDeliverySchedulerTest {

    @Inject
    LembretePushRepository lembretePushRepository;

    @Inject
    PushSubscriptionRepository pushSubscriptionRepository;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MongoClient mongoClient;

    @ConfigProperty(name = "quarkus.mongodb.database")
    String databaseName;

    private ObjectId usuarioId;

    @BeforeEach
    void setUp() {
        usuarioId = new ObjectId();
        mongoClient.getDatabase(databaseName).getCollection("lembretes_push").deleteMany(new Document());
        mongoClient.getDatabase(databaseName).getCollection("push_subscriptions").deleteMany(new Document());
    }

    @Test
    @DisplayName("Lembrete atrasado há mais de 15 minutos deve ser marcado como EXPIRADO_FORA_DA_JANELA sem enviar push")
    void lembreteAtrasadoMaisDe15MinutosDeveExpirarSemEnviar() {
        Instant agora = Instant.parse("2026-06-15T15:00:00Z");
        Instant instanteAtrasado = agora.minus(Duration.ofMinutes(16)); // 16 min atrás (> 15 min)

        ObjectId tarefaId = new ObjectId();
        LembretePush lembrete = new LembretePush(
                new ObjectId(),
                usuarioId,
                tarefaId,
                1,
                instanteAtrasado,
                15,
                "America/Sao_Paulo",
                LembretePush.ESTADO_PENDENTE,
                "Tarefa Atrasada",
                0,
                null,
                null,
                null,
                null,
                agora.minus(Duration.ofHours(1)),
                agora.minus(Duration.ofHours(1))
        );
        lembretePushRepository.salvarOuAtualizar(lembrete);

        // Subscrição existente
        PushSubscription sub = new PushSubscription(
                new ObjectId(),
                usuarioId,
                "https://push.test/sub-expirada-" + ObjectId.get().toHexString(),
                "p256dh",
                "auth",
                "Mozilla/5.0",
                agora,
                agora
        );
        pushSubscriptionRepository.salvarOuAtualizar(sub);

        AtomicBoolean senderAcionado = new AtomicBoolean(false);
        WebPushSender testSender = (s, payload, ttl) -> {
            senderAcionado.set(true);
            return WebPushSender.PushSendResult.sucesso(201);
        };

        PushDeliveryScheduler scheduler = new PushDeliveryScheduler(
                lembretePushRepository,
                pushSubscriptionRepository,
                testSender,
                objectMapper,
                true
        );

        int processados = scheduler.processarLembretes(agora);
        assertEquals(1, processados);
        assertFalse(senderAcionado.get(), "O sender não deve ser chamado para lembretes fora da janela de 15 minutos!");

        var lembreteAtualizado = lembretePushRepository.buscarPorTarefa(usuarioId, tarefaId).get();
        assertEquals(LembretePush.ESTADO_EXPIRADO_FORA_DA_JANELA, lembreteAtualizado.estado());
    }

    @Test
    @DisplayName("Resposta 410 Gone remove imediatamente a inscrição inválida de push_subscriptions")
    void resposta410GoneDeveRemoverInscricaoImediatamente() {
        Instant agora = Instant.parse("2026-06-15T15:00:00Z");
        Instant instanteEntrega = agora.minus(Duration.ofMinutes(5)); // dentro da janela de 15 min

        String endpointInvalido = "https://push.test/sub-gone-" + ObjectId.get().toHexString();
        PushSubscription sub = new PushSubscription(
                new ObjectId(),
                usuarioId,
                endpointInvalido,
                "p256dh",
                "auth",
                "Mozilla/5.0",
                agora,
                agora
        );
        pushSubscriptionRepository.salvarOuAtualizar(sub);
        assertTrue(pushSubscriptionRepository.buscarPorEndpoint(endpointInvalido).isPresent());

        ObjectId tarefaId = new ObjectId();
        LembretePush lembrete = new LembretePush(
                new ObjectId(),
                usuarioId,
                tarefaId,
                1,
                instanteEntrega,
                15,
                "America/Sao_Paulo",
                LembretePush.ESTADO_PENDENTE,
                "Tarefa Janela OK",
                0,
                null,
                null,
                null,
                null,
                agora.minus(Duration.ofHours(1)),
                agora.minus(Duration.ofHours(1))
        );
        lembretePushRepository.salvarOuAtualizar(lembrete);

        AtomicInteger ttlCapturado = new AtomicInteger(0);
        WebPushSender sender410 = (s, payload, ttl) -> {
            ttlCapturado.set(ttl);
            return WebPushSender.PushSendResult.endpointInvalido(410, "Subscription expired (410 Gone)");
        };

        PushDeliveryScheduler scheduler = new PushDeliveryScheduler(
                lembretePushRepository,
                pushSubscriptionRepository,
                sender410,
                objectMapper,
                true
        );

        int processados = scheduler.processarLembretes(agora);
        assertEquals(1, processados);
        assertEquals(900, ttlCapturado.get(), "O envio deve especificar TTL: 900 conforme RFC 8030");

        // Inscrição deve ter sido imediatamente removida
        assertFalse(pushSubscriptionRepository.buscarPorEndpoint(endpointInvalido).isPresent(),
                "Inscrição com 410 Gone deve ser imediatamente excluída de push_subscriptions");

        var lembreteAtualizado = lembretePushRepository.buscarPorTarefa(usuarioId, tarefaId).get();
        assertEquals(LembretePush.ESTADO_FALHA_FINAL, lembreteAtualizado.estado());
    }

    @Test
    @DisplayName("Envio bem sucedido com HTTP 201 marca o lembrete como ENVIADO e preserva a inscrição")
    void envioComSucessoDeveMarcarComoEnviado() {
        Instant agora = Instant.parse("2026-06-15T15:00:00Z");
        Instant instanteEntrega = agora.minus(Duration.ofMinutes(1)); // dentro da janela

        String endpointValido = "https://push.test/sub-ok-" + ObjectId.get().toHexString();
        PushSubscription sub = new PushSubscription(
                new ObjectId(),
                usuarioId,
                endpointValido,
                "p256dh",
                "auth",
                "Mozilla/5.0",
                agora,
                agora
        );
        pushSubscriptionRepository.salvarOuAtualizar(sub);

        ObjectId tarefaId = new ObjectId();
        LembretePush lembrete = new LembretePush(
                new ObjectId(),
                usuarioId,
                tarefaId,
                1,
                instanteEntrega,
                15,
                "America/Sao_Paulo",
                LembretePush.ESTADO_PENDENTE,
                "Tarefa Sucesso",
                0,
                null,
                null,
                null,
                null,
                agora.minus(Duration.ofHours(1)),
                agora.minus(Duration.ofHours(1))
        );
        lembretePushRepository.salvarOuAtualizar(lembrete);

        WebPushSender senderSucesso = (s, payload, ttl) -> WebPushSender.PushSendResult.sucesso(201);

        PushDeliveryScheduler scheduler = new PushDeliveryScheduler(
                lembretePushRepository,
                pushSubscriptionRepository,
                senderSucesso,
                objectMapper,
                true
        );

        int processados = scheduler.processarLembretes(agora);
        assertEquals(1, processados);

        assertTrue(pushSubscriptionRepository.buscarPorEndpoint(endpointValido).isPresent(),
                "Inscrição válida deve continuar presente");

        var lembreteAtualizado = lembretePushRepository.buscarPorTarefa(usuarioId, tarefaId).get();
        assertEquals(LembretePush.ESTADO_ENVIADO, lembreteAtualizado.estado());
    }
}
