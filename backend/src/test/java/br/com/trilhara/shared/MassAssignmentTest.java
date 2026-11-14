package br.com.trilhara.shared;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import java.time.Instant;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.trilhara.auth.TokenService;
import br.com.trilhara.auth.UserRepository;
import br.com.trilhara.auth.Usuario;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/**
 * propriedade desconhecida (mass assignment) no corpo falha com
 * {@code 400 VALIDATION_ERROR} e {@code errors[propriedade]} em vez de ser ignorada.
 */
@QuarkusTest
@DisplayName("Mass assignment e propriedade desconhecida")
class MassAssignmentTest {

    private static final String API_TRILHAS = "/api/trilhas";

    @Inject
    TokenService tokenService;

    @Inject
    UserRepository userRepository;

    private String token;

    @BeforeEach
    void setup() {
        String usuarioId = new ObjectId().toHexString();
        Usuario usuario = new Usuario(
                usuarioId,
                "Usuario Mass Assignment",
                "mass-" + usuarioId + "@trilhara.com.br",
                "$2a$12$dummyHashParaTesteDeMassAssignment1234567890",
                List.of(),
                true,
                true,
                0,
                null,
                null,
                Instant.now(),
                Instant.now());
        userRepository.inserir(usuario);
        token = tokenService.gerarAccessToken(usuario);
    }

    private io.restassured.specification.RequestSpecification autenticado() {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON);
    }

    @Test
    @DisplayName("usuarioId no corpo é rejeitado com 400 VALIDATION_ERROR")
    void usuarioIdNoCorpoEhRejeitado() {
        autenticado()
                .body("""
                        {"nome":"Trilha inválida","usuarioId":"%s"}
                        """.formatted(new ObjectId().toHexString()))
                .when().post(API_TRILHAS)
                .then()
                .statusCode(400)
                .contentType(containsString("application/problem+json"))
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("errors.usuarioId", hasItem("Propriedade não permitida."));
    }

    @Test
    @DisplayName("criadoEm no corpo é rejeitado com 400 VALIDATION_ERROR")
    void criadoEmNoCorpoEhRejeitado() {
        autenticado()
                .body("""
                        {"nome":"Trilha inválida","criadoEm":"2026-01-01T00:00:00Z"}
                        """)
                .when().post(API_TRILHAS)
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("errors.criadoEm", hasItem("Propriedade não permitida."));
    }

    @Test
    @DisplayName("Payload válido continua sendo aceito (controle)")
    void payloadValidoEhAceito() {
        autenticado()
                .body("""
                        {"nome":"Trilha válida"}
                        """)
                .when().post(API_TRILHAS)
                .then()
                .statusCode(201);
    }
}
