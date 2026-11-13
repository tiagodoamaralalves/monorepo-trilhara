package br.com.trilhara.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;

/**
 * R13/D-45 — rate limiting transversal por usuário e por IP.
 *
 * <p>Cobre os limitadores que existiam apenas na decisão e não no código até a R13:
 * {@code refresh} (10/min/IP), {@code handoff} (10/min/IP), {@code handoff/consume}
 * (20/min/IP), {@code notas/busca} (60/min/usuário) e {@code POST /api/trilhas}
 * (30/min/usuário).</p>
 *
 * <p>A asserção é no contrato observável — {@code 429}, {@code code=RATE_LIMITED},
 * {@code application/problem+json} e header {@code Retry-After} — não na implementação.</p>
 */
@QuarkusTest
@DisplayName("R13 - Rate limiting por usuário e por IP (D-45)")
class RateLimitPorUsuarioTest {

    private static final String API_TRILHAS = "/api/trilhas";
    private static final String API_REFRESH = "/api/auth/refresh";
    private static final String API_HANDOFF = "/api/auth/handoff";
    private static final String API_HANDOFF_CONSUME = "/api/auth/handoff/consume";
    private static final String API_BUSCA_NOTAS = "/api/notas/busca";

    @Inject
    RateLimitService rateLimitService;

    @Inject
    TokenService tokenService;

    @Inject
    UserRepository userRepository;

    private String token;
    private String usuarioId;

    @BeforeEach
    void setUp() {
        // Cada teste começa com janelas zeradas: sem isso a ordem de execução definiria o
        // resultado (os limitadores são estado em memória do aplicativo).
        rateLimitService.limparTudo();

        usuarioId = new ObjectId().toHexString();
        Instant agora = Instant.now();
        Usuario usuario = new Usuario(
                usuarioId,
                "Usuario Rate Limit",
                "rl-" + UUID.randomUUID() + "@trilhara.test",
                "hash",
                List.of(Role.USER),
                true,
                true,
                0,
                null,
                agora,
                agora,
                agora);
        userRepository.inserir(usuario);
        token = tokenService.gerarAccessToken(usuario);
    }

    private io.restassured.specification.RequestSpecification autenticado() {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON);
    }

    private static void assertarRateLimit(ValidatableResponse resposta) {
        resposta.statusCode(429)
                .contentType(containsString("application/problem+json"))
                .header("Retry-After", notNullValue())
                .body("code", equalTo("RATE_LIMITED"))
                .body("status", equalTo(429))
                .body("type", equalTo("urn:trilhara:problem:rate-limited"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh: 11ª tentativa no mesmo IP responde 429 com Retry-After")
    void refreshLimitaDezPorMinutoPorIp() {
        for (int i = 0; i < 10; i++) {
            // Sem cookie/token válido a resposta é 401 — o que importa é que o limitador
            // contabiliza a tentativa antes de qualquer validação de credencial.
            given()
                    .contentType(ContentType.JSON)
                    .body("{}")
                    .when().post(API_REFRESH);
        }

        assertarRateLimit(given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post(API_REFRESH)
                .then());
    }

    @Test
    @DisplayName("POST /api/auth/handoff: 11ª emissão no mesmo IP responde 429")
    void handoffEmissaoLimitaDezPorMinutoPorIp() {
        for (int i = 0; i < 10; i++) {
            autenticado()
                    .body("{\"destino\":\"http://tarefas.localhost\"}")
                    .when().post(API_HANDOFF);
        }

        assertarRateLimit(autenticado()
                .body("{\"destino\":\"http://tarefas.localhost\"}")
                .when().post(API_HANDOFF)
                .then());
    }

    @Test
    @DisplayName("POST /api/auth/handoff/consume: 21ª tentativa no mesmo IP responde 429")
    void handoffConsumoLimitaVintePorMinutoPorIp() {
        for (int i = 0; i < 20; i++) {
            given()
                    .contentType(ContentType.JSON)
                    .body("{\"codigo\":\"inexistente-" + i + "\"}")
                    .when().post(API_HANDOFF_CONSUME);
        }

        assertarRateLimit(given()
                .contentType(ContentType.JSON)
                .body("{\"codigo\":\"inexistente-final\"}")
                .when().post(API_HANDOFF_CONSUME)
                .then());
    }

    @Test
    @DisplayName("GET /api/notas/busca: 61ª busca do mesmo usuário responde 429")
    void buscaNotasLimitaSessentaPorMinutoPorUsuario() {
        for (int i = 0; i < 60; i++) {
            autenticado().queryParam("q", "resumo").when().get(API_BUSCA_NOTAS);
        }

        assertarRateLimit(autenticado()
                .queryParam("q", "resumo")
                .when().get(API_BUSCA_NOTAS)
                .then());
    }

    @Test
    @DisplayName("POST /api/trilhas: 31ª criação do mesmo usuário responde 429")
    void criacaoTrilhaLimitaTrintaPorMinutoPorUsuario() {
        for (int i = 0; i < 30; i++) {
            autenticado()
                    .body("{\"nome\":\"Trilha " + i + "\"}")
                    .when().post(API_TRILHAS);
        }

        assertarRateLimit(autenticado()
                .body("{\"nome\":\"Trilha excedente\"}")
                .when().post(API_TRILHAS)
                .then());
    }

    @Test
    @DisplayName("Janela de criação é por usuário: outro usuário não herda a contagem")
    void janelaDeCriacaoNaoColideEntreUsuarios() {
        for (int i = 0; i < 30; i++) {
            autenticado().body("{\"nome\":\"Trilha " + i + "\"}").when().post(API_TRILHAS);
        }

        String outroId = new ObjectId().toHexString();
        Instant agora = Instant.now();
        userRepository.inserir(new Usuario(
                outroId,
                "Outro Usuario",
                "rl2-" + UUID.randomUUID() + "@trilhara.test",
                "hash",
                List.of(Role.USER),
                true,
                true,
                0,
                null,
                agora,
                agora,
                agora));
        String outroToken = tokenService.gerarAccessToken(
                userRepository.buscarPorId(outroId).orElseThrow());

        // O limite de 30 pertence ao primeiro usuário; o segundo ainda tem a própria janela.
        given()
                .header("Authorization", "Bearer " + outroToken)
                .contentType(ContentType.JSON)
                .body("{\"nome\":\"Trilha do outro usuario\"}")
                .when().post(API_TRILHAS)
                .then()
                .statusCode(201);
    }
}
