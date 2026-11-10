package br.com.trilhara.push;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
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
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

@QuarkusTest
class PushResourceTest {

    private static final String PUSH_CONFIG = "/api/push/config";
    private static final String PUSH_SUBSCRIPTIONS = "/api/push/subscriptions";
    private static final String PUSH_PREFERENCIAS = "/api/push/preferencias";

    @Inject
    TokenService tokenService;

    @Inject
    UserRepository userRepository;

    @Inject
    PushSubscriptionRepository subscriptionRepository;

    private String usuarioId;
    private String token;

    @BeforeEach
    void setUp() {
        usuarioId = new ObjectId().toHexString();
        Usuario usuario = new Usuario(
                usuarioId,
                "Teste Push Resource",
                "push-res-" + UUID.randomUUID() + "@teste.com",
                "hash",
                List.of(Role.USER),
                true,
                true,
                0,
                null,
                null,
                Instant.now(),
                Instant.now()
        );
        userRepository.inserir(usuario);
        token = tokenService.gerarAccessToken(usuario);
    }

    @Test
    @DisplayName("GET /api/push/config retorna configurações de push e padrão global (default 15)")
    void deveRetornarConfigDePush() {
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(PUSH_CONFIG)
                .then()
                .statusCode(200)
                .body("padraoMinutosAntes", equalTo(15));
    }

    @Test
    @DisplayName("POST e DELETE /api/push/subscriptions registra e remove inscrição com sucesso")
    void deveRegistrarERemoverInscricao() {
        String endpoint = "https://fcm.googleapis.com/fcm/send/test-token-" + UUID.randomUUID();

        // 1. Registrar
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "endpoint", endpoint,
                        "keys", Map.of(
                                "p256dh", "BH_p256dh_test_key",
                                "auth", "auth_secret_test"
                        ),
                        "userAgent", "Vitest-Chrome/120.0"
                ))
                .when()
                .post(PUSH_SUBSCRIPTIONS)
                .then()
                .statusCode(201)
                .body("endpoint", equalTo(endpoint))
                .body("id", notNullValue());

        assertEquals(1, subscriptionRepository.listarPorUsuario(new ObjectId(usuarioId)).size());

        // 2. Remover via query param
        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("endpoint", endpoint)
                .when()
                .delete(PUSH_SUBSCRIPTIONS)
                .then()
                .statusCode(200)
                .body("removido", equalTo(true));

        assertEquals(0, subscriptionRepository.listarPorUsuario(new ObjectId(usuarioId)).size());
    }

    @Test
    @DisplayName("PUT /api/push/preferencias atualiza padrão global de lembretes")
    void deveAtualizarPreferenciaGlobal() {
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("padraoMinutosAntes", 30))
                .when()
                .put(PUSH_PREFERENCIAS)
                .then()
                .statusCode(200)
                .body("padraoMinutosAntes", equalTo(30));

        // GET deve refletir novo padrão
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(PUSH_CONFIG)
                .then()
                .statusCode(200)
                .body("padraoMinutosAntes", equalTo(30));
    }
}
