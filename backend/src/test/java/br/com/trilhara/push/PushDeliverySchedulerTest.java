package br.com.trilhara.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoClient;

import br.com.trilhara.tarefa.TarefaRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * scheduler de entrega.
 *
 * <p>As asserções usam datas relativas a {@code agora} porque a janela útil de 15 minutos e o
 * backoff são medidos a partir de {@code instanteEntregaUtc}.</p>
 */
@QuarkusTest
class PushDeliverySchedulerTest {

    @Inject
    LembretePushRepository lembretePushRepository;

    @Inject
    LembretePushFilaRepository filaRepository;

    @Inject
    PushSubscriptionRepository pushSubscriptionRepository;

    @Inject
    PushPayloadFactory payloadFactory;

    @Inject
    TarefaRepository tarefaRepository;

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

    /** Inscrição sintética com chaves EC reais: a cifra RFC 8291 exige par válido. */
    private PushSubscription inscricaoValida(String endpoint) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair par = kpg.generateKeyPair();
        byte[] publica = WebPushCriptografia.exportarNaoComprimido((ECPublicKey) par.getPublic());
        byte[] auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);

        String p256dh = Base64.getUrlEncoder().withoutPadding().encodeToString(publica);
        String authB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(auth);

        PushSubscription sub = new PushSubscription(
                new ObjectId(), usuarioId, endpoint, p256dh, authB64, "Mozilla/5.0", Instant.now(), Instant.now());
        pushSubscriptionRepository.salvarOuAtualizar(sub);
        return sub;
    }

    private LembretePush inserirLembrete(ObjectId tarefaId, Instant instante, long versao, int tentativas) {
        Instant criado = Instant.now();
        LembretePush lembrete = new LembretePush(
                new ObjectId(), usuarioId, tarefaId, versao, instante,
                LembretePush.ESTADO_PENDENTE, tentativas, null, null, criado, criado);
        lembretePushRepository.inserirNovaVersao(lembrete);
        return lembrete;
    }

    /** Tarefa mínima persistida: o scheduler lê o título no momento do envio. */
    private ObjectId tarefaComTitulo(String titulo) {
        ObjectId tarefaId = new ObjectId();
        Document doc = new Document()
                .append("_id", tarefaId)
                .append("usuarioId", usuarioId)
                .append("titulo", titulo)
                .append("prioridade", "MEDIA")
                .append("estado", "PENDENTE")
                .append("concluida", false)
                .append("ordem", 1)
                .append("subtarefas", List.of())
                .append("modoLembrete", "HERDAR")
                .append("criadoEm", java.util.Date.from(Instant.now()))
                .append("atualizadoEm", java.util.Date.from(Instant.now()));
        mongoClient.getDatabase(databaseName).getCollection("tarefas").insertOne(doc);
        return tarefaId;
    }

    private PushDeliveryScheduler scheduler(WebPushSender sender) {
        PushDeliveryScheduler s = new PushDeliveryScheduler(
                lembretePushRepository, filaRepository, pushSubscriptionRepository,
                tarefaRepository, sender, payloadFactory);
        s.pushHabilitado = true;
        s.vapidPublicKey = java.util.Optional.of("public-key-teste");
        s.vapidPrivateKey = java.util.Optional.of("private-key-teste");
        return s;
    }

    private static WebPushSender senderQueConta(AtomicInteger chamadas, AtomicBoolean capturouTtl) {
        return (sub, payload, ttl) -> {
            chamadas.incrementAndGet();
            if (ttl == 900) {
                capturouTtl.set(true);
            }
            return WebPushSender.PushSendResult.sucesso(201);
        };
    }

    @Test
    @DisplayName("doisWorkersNaoProcessamOMesmoItem: o claim atômico entrega o item a um único worker")
    void doisWorkersNaoProcessamOMesmoItem() throws Exception {
        inscricaoValida("https://push.test/sub-concorrente-" + ObjectId.get().toHexString());
        ObjectId tarefaId = tarefaComTitulo("Tarefa concorrente");
        Instant agora = Instant.now();
        LembretePush lembrete = inserirLembrete(tarefaId, agora.minusSeconds(5), 1, 0);

        // Mesmo instante de referência: o segundo claim não pode encontrar o documento PENDENTE.
        var primeiro = filaRepository.claim(lembrete.id(), "worker-a", agora, agora.plusSeconds(60));
        var segundo = filaRepository.claim(lembrete.id(), "worker-b", agora, agora.plusSeconds(60));

        assertTrue(primeiro.isPresent(), "O primeiro worker deve adquirir o lease.");
        assertFalse(segundo.isPresent(), "O segundo worker não pode adquirir o mesmo item.");
        assertEquals("worker-a", primeiro.get().leaseToken());
    }

    @Test
    @DisplayName("atrasoMaiorQue15MinExpiraSemEnviar: EXPIRADO_FORA_DA_JANELA e nenhum push")
    void atrasoMaiorQue15MinExpiraSemEnviar() throws Exception {
        inscricaoValida("https://push.test/sub-atrasada-" + ObjectId.get().toHexString());
        ObjectId tarefaId = tarefaComTitulo("Tarefa atrasada");
        Instant agora = Instant.now();
        inserirLembrete(tarefaId, agora.minus(Duration.ofMinutes(16)), 1, 0);

        AtomicInteger chamadas = new AtomicInteger();
        int processados = scheduler(senderQueConta(chamadas, new AtomicBoolean()))
                .processarLembretes(agora);

        assertEquals(1, processados);
        assertEquals(0, chamadas.get(), "Nenhum push pode ser enviado fora da janela útil.");
        assertEquals(LembretePush.ESTADO_EXPIRADO_FORA_DA_JANELA,
                lembretePushRepository.buscarVersaoVigente(usuarioId, tarefaId).orElseThrow().estado());
    }

    @Test
    @DisplayName("falhaTransitoriaRespeitaJanela: volta a PENDENTE sem reescrever o instante de entrega")
    void falhaTransitoriaRespeitaJanela() throws Exception {
        inscricaoValida("https://push.test/sub-falha-" + ObjectId.get().toHexString());
        ObjectId tarefaId = tarefaComTitulo("Tarefa com falha transitória");
        Instant agora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant entrega = agora.minusSeconds(5);
        LembretePush lembrete = inserirLembrete(tarefaId, entrega, 1, 0);

        WebPushSender senderFalha = (sub, payload, ttl) ->
                WebPushSender.PushSendResult.falha(500, "Provedor indisponível");

        scheduler(senderFalha).processarLembretes(agora);

        LembretePush apos = lembretePushRepository.buscarVersaoVigente(usuarioId, tarefaId).orElseThrow();
        assertEquals(LembretePush.ESTADO_PENDENTE, apos.estado(), "Falha transitória volta para a fila.");
        assertEquals(entrega, apos.instanteEntregaUtc(), "A janela é medida pelo instante original.");
        assertTrue(apos.tentativas() >= 1, "tentativas é incrementado no claim.");
        assertNull(apos.leaseToken(), "O lease é liberado ao devolver para a fila.");

        // Backoff de 2^1 = 2 s medido a partir de `atualizadoEm` (última transição):
        // um instante imediatamente após a falha não é candidato de novo.
        assertTrue(filaRepository.listarCandidatosEnvio(agora, 50).isEmpty(),
                "O backoff impede nova tentativa imediata.");
    }

    @Test
    @DisplayName("falhaSemTempoNaJanelaTerminaEmFalhaFinal: não fica PENDENTE para sempre")
    void falhaSemTempoNaJanelaTerminaEmFalhaFinal() throws Exception {
        inscricaoValida("https://push.test/sub-sem-janela-" + ObjectId.get().toHexString());
        ObjectId tarefaId = tarefaComTitulo("Tarefa no limite da janela");
        Instant agora = Instant.now();
        // Entrega há 14min59s: dentro da janela agora, mas sem 1 s de folga para retentar.
        inserirLembrete(tarefaId, agora.minusSeconds(899), 1, 0);

        WebPushSender senderFalha = (sub, payload, ttl) ->
                WebPushSender.PushSendResult.falha(503, "Provedor indisponível");

        scheduler(senderFalha).processarLembretes(agora);

        assertEquals(LembretePush.ESTADO_FALHA_FINAL,
                lembretePushRepository.buscarVersaoVigente(usuarioId, tarefaId).orElseThrow().estado(),
                "Sem tempo hábil na janela, o lembrete encerra em FALHA_FINAL.");
    }

    @Test
    @DisplayName("gone410RemoveInscricao: 410 Gone remove a inscrição e encerra em FALHA_FINAL")
    void gone410RemoveInscricao() throws Exception {
        String endpoint = "https://push.test/sub-gone-" + ObjectId.get().toHexString();
        inscricaoValida(endpoint);
        ObjectId tarefaId = tarefaComTitulo("Tarefa 410");
        Instant agora = Instant.now();
        inserirLembrete(tarefaId, agora.minusSeconds(5), 1, 0);

        WebPushSender sender410 = (sub, payload, ttl) ->
                WebPushSender.PushSendResult.endpointInvalido(410, "Subscription expired (410 Gone)");

        scheduler(sender410).processarLembretes(agora);

        assertFalse(pushSubscriptionRepository.buscarPorEndpoint(usuarioId, endpoint).isPresent(),
                "Inscrição com 410 Gone deve sair imediatamente de push_subscriptions.");
        assertEquals(LembretePush.ESTADO_FALHA_FINAL,
                lembretePushRepository.buscarVersaoVigente(usuarioId, tarefaId).orElseThrow().estado());
    }

    @Test
    @DisplayName("versaoSubstituidaNaoEhEntregue: fencing impede entrega de versão já substituída")
    void versaoSubstituidaNaoEhEntregue() throws Exception {
        inscricaoValida("https://push.test/sub-versao-" + ObjectId.get().toHexString());
        ObjectId tarefaId = tarefaComTitulo("Tarefa com duas versões");
        Instant agora = Instant.now();

        // Versão 1 vencida e versão 2 vigente (mudança de horário no intervalo).
        inserirLembrete(tarefaId, agora.minusSeconds(5), 1, 0);
        inserirLembrete(tarefaId, agora.plusSeconds(600), 2, 0);

        AtomicInteger chamadas = new AtomicInteger();
        scheduler(senderQueConta(chamadas, new AtomicBoolean())).processarLembretes(agora);

        assertEquals(0, chamadas.get(), "A versão substituída não pode gerar notificação.");
        LembretePush v1 = lembretePushRepository.buscarVersaoVigente(usuarioId, tarefaId).orElseThrow();
        assertEquals(2, v1.versaoAgendamento(), "A versão vigente continua sendo a mais nova.");
        assertEquals(LembretePush.ESTADO_PENDENTE, v1.estado());
    }

    @Test
    @DisplayName("ttl900NoEnvio: entrega bem-sucedida usa TTL 900 e marca ENVIADO")
    void ttl900NoEnvio() throws Exception {
        String endpoint = "https://push.test/sub-ok-" + ObjectId.get().toHexString();
        inscricaoValida(endpoint);
        ObjectId tarefaId = tarefaComTitulo("Tarefa no prazo");
        Instant agora = Instant.now();
        inserirLembrete(tarefaId, agora.minusSeconds(5), 1, 0);

        AtomicInteger chamadas = new AtomicInteger();
        AtomicBoolean ttl900 = new AtomicBoolean(false);
        int processados = scheduler(senderQueConta(chamadas, ttl900)).processarLembretes(agora);

        assertEquals(1, processados);
        assertEquals(1, chamadas.get());
        assertTrue(ttl900.get(), "O envio deve usar TTL: 900 (RFC 8030).");
        assertTrue(pushSubscriptionRepository.buscarPorEndpoint(usuarioId, endpoint).isPresent(),
                "Inscrição válida permanece na coleção.");
        assertEquals(LembretePush.ESTADO_ENVIADO,
                lembretePushRepository.buscarVersaoVigente(usuarioId, tarefaId).orElseThrow().estado());
    }
}
