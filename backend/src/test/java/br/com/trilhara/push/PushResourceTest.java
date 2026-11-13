package br.com.trilhara.push;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.trilhara.auth.Role;
import br.com.trilhara.auth.TokenService;
import br.com.trilhara.auth.UserRepository;
import br.com.trilhara.auth.Usuario;
import br.com.trilhara.tarefa.ModoLembrete;
import br.com.trilhara.tarefa.TarefaService;
import br.com.trilhara.tarefa.dto.CriarTarefaRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/**
 * R11 — contrato de `PushResource` (D-36c/d/e).
 */
@QuarkusTest
class PushResourceTest {

    private static final String PUSH_CONFIG = "/api/push/config";
    private static final String PUSH_SUBSCRIPTIONS = "/api/push/subscriptions";
    private static final String PUSH_PREFERENCIAS = "/api/push/preferencias";
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Inject
    TokenService tokenService;

    @Inject
    UserRepository userRepository;

    @Inject
    PushSubscriptionRepository subscriptionRepository;

    @Inject
    LembretePushRepository lembretePushRepository;

    @Inject
    TarefaService tarefaService;

    private String usuarioId;
    private String token;

    @BeforeEach
    void setUp() {
        usuarioId = new ObjectId().toHexString();
        Usuario usuario = new Usuario(
                usuarioId, "Teste Push Resource", "push-res-" + UUID.randomUUID() + "@teste.com",
                "hash", List.of(Role.USER), true, true, 0, null, null, Instant.now(), Instant.now());
        userRepository.inserir(usuario);
        token = tokenService.gerarAccessToken(usuario);
    }

    /** Par EC real: o backend valida p256dh (65 bytes) e auth (16 bytes) em base64url. */
    private static Map<String, String> chavesValidas() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair par = kpg.generateKeyPair();
        byte[] publica = WebPushCriptografia.exportarNaoComprimido((ECPublicKey) par.getPublic());
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        return Map.of(
                "p256dh", Base64.getUrlEncoder().withoutPadding().encodeToString(publica),
                "auth", Base64.getUrlEncoder().withoutPadding().encodeToString(auth));
    }

    @Test
    @DisplayName("GET /api/push/config retorna padrão global (default 15)")
    void configRetornaPadraoPadrao() {
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(PUSH_CONFIG)
                .then()
                .statusCode(200)
                .body("padraoMinutosAntes", equalTo(15));
    }

    @Test
    @DisplayName("upsertReatribuiEndpoint: endpoint já existente passa a pertencer ao usuário atual")
    void upsertReatribuiEndpoint() throws Exception {
        String endpoint = "https://fcm.googleapis.com/fcm/send/reatribuido-" + UUID.randomUUID();
        Map<String, String> chaves = chavesValidas();

        // Primeiro usuário registra o dispositivo.
        String outraPessoaId = new ObjectId().toHexString();
        Usuario outraPessoa = new Usuario(
                outraPessoaId, "Outro Usuário", "outro-" + UUID.randomUUID() + "@teste.com",
                "hash", List.of(Role.USER), true, true, 0, null, null, Instant.now(), Instant.now());
        userRepository.inserir(outraPessoa);
        subscriptionRepository.salvarOuAtualizar(new PushSubscription(
                new ObjectId(), new ObjectId(outraPessoaId), endpoint,
                chaves.get("p256dh"), chaves.get("auth"), "Agente A", Instant.now(), Instant.now()));

        // O usuário autenticado registra o MESMO endpoint (mesmo dispositivo, conta nova).
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("endpoint", endpoint, "keys", chaves, "userAgent", "Agente B"))
                .when()
                .post(PUSH_SUBSCRIPTIONS)
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("endpoint", equalTo(endpoint));

        // A inscrição agora pertence ao usuário atual e saiu da conta anterior.
        assertEquals(1, subscriptionRepository.listarPorUsuario(new ObjectId(usuarioId)).size());
        assertEquals(0, subscriptionRepository.listarPorUsuario(new ObjectId(outraPessoaId)).size());
    }

    @Test
    @DisplayName("deleteEndpointAlheioRetorna404: remover dispositivo de outro usuário não revela existência")
    void deleteEndpointAlheioRetorna404() throws Exception {
        String endpoint = "https://fcm.googleapis.com/fcm/send/alheio-" + UUID.randomUUID();
        String outraPessoaId = new ObjectId().toHexString();
        Usuario outraPessoa = new Usuario(
                outraPessoaId, "Dono Original", "dono-" + UUID.randomUUID() + "@teste.com",
                "hash", List.of(Role.USER), true, true, 0, null, null, Instant.now(), Instant.now());
        userRepository.inserir(outraPessoa);

        Map<String, String> chaves = chavesValidas();
        subscriptionRepository.salvarOuAtualizar(new PushSubscription(
                new ObjectId(), new ObjectId(outraPessoaId), endpoint,
                chaves.get("p256dh"), chaves.get("auth"), "Agente", Instant.now(), Instant.now()));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("endpoint", endpoint))
                .when()
                .delete(PUSH_SUBSCRIPTIONS)
                .then()
                .statusCode(404);

        // A inscrição do outro usuário continua intacta.
        assertTrue(subscriptionRepository.buscarPorEndpoint(new ObjectId(outraPessoaId), endpoint).isPresent());

    }

    @Test
    @DisplayName("putPreferenciasRecalculaSoHerdar: PUT recalcula herdeiras e preserva overrides")
    void putPreferenciasRecalculaSoHerdar() {
        String horario = "10:00";
        String data = ZonedDateTime.now(FUSO).toLocalDate().plusDays(1).toString();

        String herdeira = tarefaService.criarTarefa(new CriarTarefaRequest(
                null, "Herdeira", null, "MEDIA", data, horario, null,
                ModoLembrete.HERDAR, null, FUSO.getId(), 1), usuarioId).id();
        String override = tarefaService.criarTarefa(new CriarTarefaRequest(
                null, "Override", null, "MEDIA", data, horario, null,
                ModoLembrete.PERSONALIZADO, 5, FUSO.getId(), 2), usuarioId).id();

        Instant entregaOverrideAntes = lembretePushRepository
                .buscarVersaoVigente(new ObjectId(usuarioId), new ObjectId(override)).orElseThrow()
                .instanteEntregaUtc();

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("padraoMinutosAntes", 30))
                .when()
                .put(PUSH_PREFERENCIAS)
                .then()
                .statusCode(200)
                .body("padraoMinutosAntes", equalTo(30));

        Instant entregaHerdeira = lembretePushRepository
                .buscarVersaoVigente(new ObjectId(usuarioId), new ObjectId(herdeira)).orElseThrow()
                .instanteEntregaUtc();
        Instant entregaOverrideDepois = lembretePushRepository
                .buscarVersaoVigente(new ObjectId(usuarioId), new ObjectId(override)).orElseThrow()
                .instanteEntregaUtc();

        // 10:00 local (UTC-3) menos 30 min = 12:30 UTC na data informada.
        assertEquals(Instant.parse(data + "T12:30:00Z"), entregaHerdeira);
        assertEquals(entregaOverrideAntes, entregaOverrideDepois,
                "Override PERSONALIZADO nunca acompanha o padrão global.");
    }

    @Test
    @DisplayName("putPreferenciasComValorForaDoConjuntoRetorna400")
    void putPreferenciasComValorForaDoConjuntoRetorna400() {
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("padraoMinutosAntes", 7))
                .when()
                .put(PUSH_PREFERENCIAS)
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("deleteSemEndpointRetorna400: corpo é o único canal")
    void deleteSemEndpointRetorna400() {
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .delete(PUSH_SUBSCRIPTIONS)
                .then()
                .statusCode(400);
    }
}
