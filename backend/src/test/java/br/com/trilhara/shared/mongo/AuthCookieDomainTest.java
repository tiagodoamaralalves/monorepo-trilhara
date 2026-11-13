package br.com.trilhara.shared.mongo;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.trilhara.auth.AuthResource;
import br.com.trilhara.auth.AuthService;
import br.com.trilhara.auth.EmailService;
import br.com.trilhara.auth.HandoffService;
import br.com.trilhara.auth.RefreshTokenService;
import br.com.trilhara.auth.UserService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;

@QuarkusTest
@DisplayName("FASE 1 - Emissão de Cookies (Produção vs Localhost) e SSO Handoff")
class AuthCookieDomainTest {

    @Inject
    AuthService authService;

    @Inject
    UserService userService;

    @Inject
    RefreshTokenService refreshTokenService;

    @Inject
    EmailService emailService;

    @Inject
    HandoffService handoffService;

    @Test
    @DisplayName("determinarCookieDomain: deve omitir Domain em desenvolvimento local (localhost, *.localhost, 127.0.0.1)")
    void deveOmitirDomainEmAmbienteLocal() {
        AuthResource resourceLocal = new AuthResource(
                authService, userService, refreshTokenService, null, emailService, handoffService, null, null,
                "trilhara_rt", false, "Strict", Optional.empty(),
                "trilhara_session_state", "Lax"
        );

        assertNull(resourceLocal.determinarCookieDomain("estudos.localhost"));
        assertNull(resourceLocal.determinarCookieDomain("tarefas.localhost"));
        assertNull(resourceLocal.determinarCookieDomain("localhost:5173"));
        assertNull(resourceLocal.determinarCookieDomain("127.0.0.1:8080"));

        AuthResource resourceComLocalhostConfigurado = new AuthResource(
                authService, userService, refreshTokenService, null, emailService, handoffService, null, null,
                "trilhara_rt", false, "Strict", Optional.of("localhost"),
                "trilhara_session_state", "Lax"
        );
        assertNull(resourceComLocalhostConfigurado.determinarCookieDomain("estudos.localhost"));
    }

    @Test
    @DisplayName("determinarCookieDomain: deve emitir Domain compartilhado apenas quando host pertencer ao domínio produtivo")
    void deveEmitirDomainEmProducaoValida() {
        AuthResource resourceProd = new AuthResource(
                authService, userService, refreshTokenService, null, emailService, handoffService, null, null,
                "trilhara_rt", true, "Strict", Optional.of(".trilhara.com.br"),
                "trilhara_session_state", "Lax"
        );

        assertEquals(".trilhara.com.br", resourceProd.determinarCookieDomain("estudos.trilhara.com.br"));
        assertEquals(".trilhara.com.br", resourceProd.determinarCookieDomain("tarefas.trilhara.com.br"));
        assertEquals(".trilhara.com.br", resourceProd.determinarCookieDomain("trilhara.com.br"));

        // Host externo ou malicioso: não confia e omite Domain
        assertNull(resourceProd.determinarCookieDomain("atacante.com"));
        assertNull(resourceProd.determinarCookieDomain("estudos.localhost"));
    }

    @Test
    @DisplayName("construirCookieRefreshToken e session_state: sem atributo Domain quando Host for estudos.localhost")
    void deveConstruirCookiesSemDomainParaHostLocal() {
        AuthResource resource = new AuthResource(
                authService, userService, refreshTokenService, null, emailService, handoffService, null, null,
                "trilhara_rt", false, "Strict", Optional.of(".trilhara.com.br"),
                "trilhara_session_state", "Lax"
        );

        String rtCookie = resource.construirCookieRefreshToken("token123", 3600, "estudos.localhost");
        String ssCookie = resource.construirCookieSessionState("nonce123", 3600, "estudos.localhost");

        assertNotNull(rtCookie);
        assertTrue(!rtCookie.contains("Domain="), "Cookie de refresh em estudos.localhost não deve ter Domain");
        assertTrue(rtCookie.contains("HttpOnly"));
        assertTrue(rtCookie.contains("SameSite=Strict"));

        assertNotNull(ssCookie);
        assertTrue(!ssCookie.contains("Domain="), "Cookie de session_state em estudos.localhost não deve ter Domain");
        assertTrue(!ssCookie.contains("HttpOnly"), "Session state deve ser acessível pelo frontend (não-HttpOnly)");
    }

    @Test
    @DisplayName("construirCookieRefreshToken e session_state: com Domain=.trilhara.com.br quando configurado para produção")
    void deveConstruirCookiesComDomainParaHostProducao() {
        AuthResource resource = new AuthResource(
                authService, userService, refreshTokenService, null, emailService, handoffService, null, null,
                "trilhara_rt", true, "Strict", Optional.of(".trilhara.com.br"),
                "trilhara_session_state", "Lax"
        );

        String rtCookie = resource.construirCookieRefreshToken("token123", 3600, "estudos.trilhara.com.br");
        String ssCookie = resource.construirCookieSessionState("nonce123", 3600, "tarefas.trilhara.com.br");

        assertTrue(rtCookie.contains("Domain=.trilhara.com.br"));
        assertTrue(ssCookie.contains("Domain=.trilhara.com.br"));
    }

    @Test
    @DisplayName("HTTP: fluxo de login em estudos.localhost deve emitir cookies host-only sem Domain e logout deve expirar ambos")
    void deveEmitirCookiesHostOnlyNoLoginELogout() {
        String email = "sso.cookies." + UUID.randomUUID() + "@exemplo.com.br";
        String senha = "Senh@Forte2026!";

        // Cadastro
        Response cadastroRes = given()
                .header("Host", "estudos.localhost")
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "nome": "Usuário SSO",
                          "email": "%s",
                          "senha": "%s"
                        }
                        """.formatted(email, senha))
                .when().post("/api/auth/cadastro")
                .then().statusCode(201)
                .extract().response();

        assertNotNull(cadastroRes.cookie("trilhara_rt"));
        assertNotNull(cadastroRes.cookie("trilhara_session_state"));
        for (String cookieHeader : cadastroRes.getHeaders().getValues("Set-Cookie")) {
            assertFalse(cookieHeader.contains("Domain="), "Cookie não deve ter Domain em localhost");
        }

        String accessToken = cadastroRes.path("accessToken");
        String refreshToken = cadastroRes.cookie("trilhara_rt");

        // Logout deve expirar ambos os cookies com Max-Age=0 e sem Domain
        Response logoutRes = given()
                .header("Host", "estudos.localhost")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body("{\"refreshToken\":\"" + refreshToken + "\"}")
                .when().post("/api/auth/logout")
                .then().statusCode(204)
                .extract().response();

        for (String cookieHeader : logoutRes.getHeaders().getValues("Set-Cookie")) {
            assertFalse(cookieHeader.contains("Domain="), "Cookie expirado não deve ter Domain em localhost");
            assertTrue(cookieHeader.contains("Max-Age=0"));
        }
    }

    @Test
    @DisplayName("Handoff SSO: emissão com allowlist de destino e consumo atômico de uso único")
    void deveExecutarFluxoDeHandoffSsoLocal() {
        String email = "handoff." + UUID.randomUUID() + "@exemplo.com.br";
        String senha = "Senh@Forte2026!";

        // 1. Cadastra usuário
        Response cadastro = given()
                .header("Host", "estudos.localhost")
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "nome": "Usuário Handoff",
                          "email": "%s",
                          "senha": "%s"
                        }
                        """.formatted(email, senha))
                .when().post("/api/auth/cadastro")
                .then().statusCode(201)
                .extract().response();

        String token = cadastro.path("accessToken");

        // 2. Destino inválido fora da allowlist deve falhar (400)
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"destino\":\"https://site-malicioso.com/\"}")
                .when().post("/api/auth/handoff")
                .then().statusCode(400);

        // 3. Destino válido emite código
        Response handoffRes = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"destino\":\"http://localhost:5174/\"}")
                .when().post("/api/auth/handoff")
                .then().statusCode(200)
                .body("codigo", notNullValue())
                .extract().response();

        String codigo = handoffRes.path("codigo");

        // Uma origem não local não pode consumir um handoff destinado a localhost.
        Response handoffParaOutraOrigem = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"destino\":\"http://localhost:5174/\"}")
                .when().post("/api/auth/handoff")
                .then().statusCode(200)
                .extract().response();
        String codigoOutraOrigem = handoffParaOutraOrigem.path("codigo");

        given()
                .header("Host", "atacante.example")
                .contentType(ContentType.JSON)
                .body("{\"codigo\":\"%s\"}".formatted(codigoOutraOrigem))
                .when().post("/api/auth/handoff/consume")
                .then().statusCode(400);

        // 4. Consome o código na nova origem (origem não autenticada)
        given()
                .header("Host", "localhost:5174")
                .contentType(ContentType.JSON)
                .body("{\"codigo\":\"%s\"}".formatted(codigo))
                .when().post("/api/auth/handoff/consume")
                .then().statusCode(200)
                .body("accessToken", notNullValue())
                .body("usuario.email", equalTo(email.toLowerCase()))
                .cookie("trilhara_rt", notNullValue())
                .cookie("trilhara_session_state", notNullValue());

        // 5. Tentativa de reuso do mesmo código deve falhar (uso único!)
        given()
                .header("Host", "localhost:5174")
                .contentType(ContentType.JSON)
                .body("{\"codigo\":\"%s\"}".formatted(codigo))
                .when().post("/api/auth/handoff/consume")
                .then().statusCode(401);
    }
}
