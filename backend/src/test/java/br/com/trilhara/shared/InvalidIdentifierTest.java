package br.com.trilhara.shared;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

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
 * D-03: ID malformado em path é {@code 400 INVALID_IDENTIFIER} (não 404) e ID válido
 * inexistente/fora do tenant é {@code 404 NOT_FOUND}, sem revelar existência.
 */
@QuarkusTest
@DisplayName("R1 - Identificadores HTTP (D-03)")
class InvalidIdentifierTest {

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
                "Usuario InvalidIdentifier",
                "invalid-id-" + usuarioId + "@trilhara.com.br",
                "$2a$12$dummyHashParaTesteDeIdentificadorInvalido1234567890",
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

    @Test
    @DisplayName("ID malformado em path responde 400 INVALID_IDENTIFIER com errors[trilhaId]")
    void idMalformadoResponde400() {
        given()
                .header("Authorization", "Bearer " + token)
                .when().get(API_TRILHAS + "/xyz")
                .then()
                .statusCode(400)
                .contentType(containsString("application/problem+json"))
                .body("code", equalTo("INVALID_IDENTIFIER"))
                .body("type", equalTo("urn:trilhara:problem:invalid-identifier"))
                .body("status", equalTo(400))
                .body("instance", equalTo("/api/trilhas/xyz"))
                .body("errors.trilhaId", notNullValue());
    }

    @Test
    @DisplayName("ID válido inexistente responde 404 NOT_FOUND")
    void idValidoInexistenteResponde404() {
        given()
                .header("Authorization", "Bearer " + token)
                .when().get(API_TRILHAS + "/" + new ObjectId().toHexString())
                .then()
                .statusCode(404)
                .contentType(containsString("application/problem+json"))
                .body("code", equalTo("NOT_FOUND"))
                .body("detail", not(containsString("ObjectId")));
    }

    @Test
    @DisplayName("Requisição sem autenticação responde 401 UNAUTHORIZED")
    void semAutenticacaoResponde401() {
        given()
                .when().get(API_TRILHAS)
                .then()
                .statusCode(401)
                .body("code", equalTo("UNAUTHORIZED"));
    }
}
