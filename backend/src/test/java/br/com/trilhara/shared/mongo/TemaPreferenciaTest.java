package br.com.trilhara.shared.mongo;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * R2 — preferência de tema persistida no servidor (D-10).
 */
@QuarkusTest
@DisplayName("R2 - Preferência de tema (usuarios.preferencias.tema)")
class TemaPreferenciaTest {

    private static final String ROTA = "/api/usuarios/me/preferencias/tema";
    private static final String ME = "/api/auth/me";

    @Test
    @DisplayName("PUT persiste o tema e devolve as preferências completas")
    void devePersistirTemaERetornarPreferencias() {
        String token = novoUsuarioComToken("dark");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"tema\": \"light\"}")
                .when().put(ROTA)
                .then().statusCode(200)
                .body("tema", equalTo("light"))
                .body("disponibilidadeSemanalMinutos", hasSize(7))
                .body("padraoLembreteMinutosAntes", equalTo(15));

        given()
                .header("Authorization", "Bearer " + token)
                .when().get(ME)
                .then().statusCode(200)
                .body("preferencias.tema", equalTo("light"));
    }

    @Test
    @DisplayName("tema inválido responde 400 VALIDATION_ERROR")
    void temaInvalidoResponde400() {
        String token = novoUsuarioComToken("dark");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"tema\": \"neon\"}")
                .when().put(ROTA)
                .then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("sem autenticação responde 401")
    void semAutenticacaoResponde401() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"tema\": \"light\"}")
                .when().put(ROTA)
                .then().statusCode(401);
    }

    /** Cria a conta via API (fluxo real) e devolve o access token do login. */
    private String novoUsuarioComToken(String temaInicial) {
        String email = "tema." + UUID.randomUUID() + "@exemplo.com.br";
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"nome": "Usuário Tema", "email": "%s", "senha": "Senh@Forte2026!"}
                        """.formatted(email))
                .when().post("/api/auth/cadastro")
                .then().statusCode(201);

        String token = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"email": "%s", "senha": "Senh@Forte2026!"}
                        """.formatted(email))
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .extract().path("accessToken");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"tema\": \"" + temaInicial + "\"}")
                .when().put(ROTA)
                .then().statusCode(200);

        return token;
    }
}