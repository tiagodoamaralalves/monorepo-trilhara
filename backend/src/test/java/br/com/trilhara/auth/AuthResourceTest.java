package br.com.trilhara.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.trilhara.auth.dto.RecuperarSenhaRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;

@QuarkusTest
class AuthResourceTest {

    private static final String CADASTRO = "/api/auth/cadastro";
    private static final String LOGIN = "/api/auth/login";
    private static final String REFRESH = "/api/auth/refresh";
    private static final String LOGOUT = "/api/auth/logout";
    private static final String LOGOUT_ALL = "/api/auth/logout-all";
    private static final String ME = "/api/auth/me";
    private static final String SENHA = "/api/auth/senha";
    private static final String SESSOES = "/api/auth/sessoes";
    private static final String RESETAR_SENHA = "/api/auth/resetar-senha";
    private static final String ADMIN_USUARIOS = "/api/auth/admin/usuarios";
    private static final String ADMIN_EDITAIS = "/api/admin/editais";
    private static final String TRILHAS = "/api/trilhas";

    @Inject
    UserRepository userRepository;

    @Inject
    RefreshTokenRepository refreshTokenRepository;

    @Inject
    TokenService tokenService;

    @Inject
    RefreshTokenService refreshTokenService;

    @Inject
    AuthService authService;

    @Inject
    UserService userService;

    @Inject
    RateLimitService rateLimitService;

    @Inject
    SecurityFilter securityFilter;

    @BeforeEach
    void prepararContexto() {
        rateLimitService.limparTudo();
        securityFilter.setStrictMode(false);
    }

    @Test
    void deveCadastrarUsuarioValidoComHashDeSenhaESetCookieHttpOnly() {
        String email = "Ana.Cadastro." + UUID.randomUUID() + "@Exemplo.com.br";
        String senha = "Senh@Forte2026!";

        Response response = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "nome": "Ana Beatriz Silva",
                          "email": "%s",
                          "senha": "%s"
                        }
                        """.formatted(email, senha))
                .when().post(CADASTRO)
                .then().statusCode(201)
                .header("Set-Cookie", containsString("trilhara_rt="))
                .header("Set-Cookie", containsString("HttpOnly"))
                .header("Set-Cookie", containsString("SameSite=Strict"))
                .header("Cache-Control", containsString("no-store"))
                .body("accessToken", notNullValue())
                .cookie("trilhara_rt", notNullValue())
                .body("tokenType", equalTo("Bearer"))
                .body("expiresIn", equalTo(900))
                .body("usuario.id", notNullValue())
                .body("usuario.nome", equalTo("Ana Beatriz Silva"))
                .body("usuario.email", equalTo(email.toLowerCase()))
                .body("usuario.papeis", hasItem("USER"))
                .body("$", not(org.hamcrest.Matchers.hasKey("senha")))
                .body("usuario", not(org.hamcrest.Matchers.hasKey("senhaHash")))
                .extract().response();

        String usuarioId = response.path("usuario.id");
        String refreshTokenPuro = response.cookie("trilhara_rt");

        Usuario persistido = userRepository.buscarPorId(usuarioId).orElseThrow();
        assertNotEquals(senha, persistido.senhaHash());
        assertTrue(persistido.senhaHash().startsWith("$pbkdf2-sha512$"));

        String hashEsperado = refreshTokenService.calcularHashSha256(refreshTokenPuro);
        assertTrue(refreshTokenRepository.buscarPorHash(hashEsperado).isPresent());
        assertTrue(refreshTokenRepository.buscarPorHash(refreshTokenPuro).isEmpty());
    }

    @Test
    void deveRejeitarCadastroComEmailDuplicadoComMensagemNeutra() {
        String email = "duplicado." + UUID.randomUUID() + "@exemplo.com.br";
        cadastrarUsuario("Primeiro Usuário", email, "Senh@Forte2026!");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "nome": "Segundo Usuário",
                          "email": "%s",
                          "senha": "Outr@Senha2026!"
                        }
                        """.formatted(email.toUpperCase()))
                .when().post(CADASTRO)
                .then().statusCode(409)
                .body("detail", equalTo("Não foi possível concluir o cadastro com os dados informados."));
    }

    @Test
    void deveRejeitarCadastroComSenhaFracaOuEmailInvalido() {
        String email = "validacao." + UUID.randomUUID() + "@exemplo.com.br";

        String[] senhasFracas = {
                "Curta1!",
                "semmaiuscula123!",
                "SEMMINUSCULA123!",
                "SemNumeros@Forte",
                "SemCaractereEspecial123",
                " ValidaMasCom" + email.substring(0, 12) + "1!"
        };

        for (String senhaFraca : senhasFracas) {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                              "nome": "Usuário Teste",
                              "email": "%s",
                              "senha": "%s"
                            }
                            """.formatted(email, senhaFraca))
                    .when().post(CADASTRO)
                    .then().statusCode(400);
        }

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "nome": "Usuário Teste",
                          "email": "email-invalido-sem-dominio",
                          "senha": "Senh@Forte2026!"
                        }
                        """)
                .when().post(CADASTRO)
                .then().statusCode(400);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "nome": "<script>alert(1)</script>",
                          "email": "%s",
                          "senha": "Senh@Forte2026!"
                        }
                        """.formatted(email))
                .when().post(CADASTRO)
                .then().statusCode(400);
    }

    @Test
    void deveRealizarLoginValidoEmitindoJwtERefreshToken() {
        String email = "login.valido." + UUID.randomUUID() + "@exemplo.com.br";
        String senha = "Senh@Forte2026!";
        cadastrarUsuario("Carlos Souza", email, senha);

        Response loginResp = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "email": "  %s  ",
                          "senha": "%s"
                        }
                        """.formatted(email.toUpperCase(), senha))
                .when().post(LOGIN)
                .then().statusCode(200)
                .body("accessToken", notNullValue())
                .cookie("trilhara_rt", notNullValue())
                .body("usuario.email", equalTo(email))
                .extract().response();

        String accessToken = loginResp.path("accessToken");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .when().get(ME)
                .then().statusCode(200)
                .body("nome", equalTo("Carlos Souza"))
                .body("email", equalTo(email))
                .body("papeis", hasItem("USER"));
    }

    @Test
    void deveRejeitarLoginComSenhaIncorretaOuEmailInexistenteComMensagemGenerica() {
        String email = "login.invalido." + UUID.randomUUID() + "@exemplo.com.br";
        cadastrarUsuario("Usuário Login", email, "Senh@Forte2026!");

        String msgSenhaErrada = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "email": "%s",
                          "senha": "Senh@Errada999!"
                        }
                        """.formatted(email))
                .when().post(LOGIN)
                .then().statusCode(401)
                .extract().path("detail");

        String msgEmailInexistente = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "email": "nao.existe.%s@exemplo.com.br",
                          "senha": "Senh@Errada999!"
                        }
                        """.formatted(UUID.randomUUID()))
                .when().post(LOGIN)
                .then().statusCode(401)
                .extract().path("detail");

        assertEquals("E-mail ou senha inválidos.", msgSenhaErrada);
        assertEquals(msgSenhaErrada, msgEmailInexistente);
    }

    @Test
    void deveRejeitarTokenJwtInvalidoAdulteradoOuAlgoritmoNone() {
        String email = "jwt.invalido." + UUID.randomUUID() + "@exemplo.com.br";
        Response cadastro = cadastrarUsuario("Validador JWT", email, "Senh@Forte2026!");
        String tokenValido = cadastro.path("accessToken");
        String[] partes = tokenValido.split("\\.");

        // 1. Token com assinatura adulterada
        String tokenAdulterado = partes[0] + "." + partes[1] + ".assinaturaFalsaInvalida123";
        given().header("Authorization", "Bearer " + tokenAdulterado)
                .when().get(ME).then().statusCode(401);

        // 2. Ataque de algoritmo "none"
        String headerNone = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String tokenAlgNone = headerNone + "." + partes[1] + ".";
        given().header("Authorization", "Bearer " + tokenAlgNone)
                .when().get(ME).then().statusCode(401);

        // 3. Token malformado e esquema inválido
        given().header("Authorization", "Bearer token-malformado")
                .when().get(ME).then().statusCode(401);
        given().header("Authorization", "Basic dXNlcjpwYXNz")
                .when().get(ME).then().statusCode(401);
    }

    @Test
    void deveRejeitarTokenJwtExpirado() {
        String email = "jwt.expirado." + UUID.randomUUID() + "@exemplo.com.br";
        Response cadastro = cadastrarUsuario("Usuário Expirado", email, "Senh@Forte2026!");
        String usuarioId = cadastro.path("usuario.id");
        Usuario usuario = userRepository.buscarPorId(usuarioId).orElseThrow();

        String tokenExpirado = tokenService.gerarAccessToken(
                usuario,
                Instant.now().minusSeconds(3600),
                60);

        given().header("Authorization", "Bearer " + tokenExpirado)
                .when().get(ME)
                .then().statusCode(401)
                .body("detail", containsString("expirado"));

        given().header("Authorization", "Bearer " + tokenExpirado)
                .when().get(TRILHAS)
                .then().statusCode(401);
    }

    @Test
    void deveRenovarAccessTokenERotacionarRefreshToken() {
        String email = "refresh.valido." + UUID.randomUUID() + "@exemplo.com.br";
        Response cadastro = cadastrarUsuario("Usuário Refresh", email, "Senh@Forte2026!");
        String refreshToken1 = cadastro.cookie("trilhara_rt");

        Response refreshResp = given()
                .contentType(ContentType.JSON)
                .body("{\"refreshToken\":\"%s\"}".formatted(refreshToken1))
                .when().post(REFRESH)
                .then().statusCode(200)
                .header("Set-Cookie", containsString("trilhara_rt="))
                .body("accessToken", notNullValue())
                .cookie("trilhara_rt", notNullValue())
                .extract().response();

        String refreshToken2 = refreshResp.cookie("trilhara_rt");
        String novoAccessToken = refreshResp.path("accessToken");
        assertNotEquals(refreshToken1, refreshToken2);

        // Também deve suportar renovação via Cookie HttpOnly sem body
        given()
                .contentType(ContentType.JSON)
                .cookie("trilhara_rt", refreshToken2)
                .body("{}")
                .when().post(REFRESH)
                .then().statusCode(200)
                .body("accessToken", notNullValue());

        given().header("Authorization", "Bearer " + novoAccessToken)
                .when().get(ME)
                .then().statusCode(200)
                .body("email", equalTo(email));
    }

    @Test
    void deveRejeitarRefreshTokenInvalidoOuVazio() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"refreshToken\":\"token-inexistente-invalido-12345\"}")
                .when().post(REFRESH)
                .then().statusCode(401);

        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post(REFRESH)
                .then().statusCode(401);
    }

    @Test
    void deveDetectarReusoDeRefreshTokenRotacionadoERevogarTodaAFamilia() {
        String email = "replay.detection." + UUID.randomUUID() + "@exemplo.com.br";
        Response inicial = cadastrarUsuario("Usuário Replay", email, "Senh@Forte2026!");
        String refreshToken1 = inicial.cookie("trilhara_rt");

        // Rotação legítima: RT1 -> RT2
        String refreshToken2 = given()
                .contentType(ContentType.JSON)
                .body("{\"refreshToken\":\"%s\"}".formatted(refreshToken1))
                .when().post(REFRESH)
                .then().statusCode(200)
                .extract().cookie("trilhara_rt");

        // Tentativa de reutilizar o RT1 já rotacionado (Replay Attack!)
        given()
                .contentType(ContentType.JSON)
                .body("{\"refreshToken\":\"%s\"}".formatted(refreshToken1))
                .when().post(REFRESH)
                .then().statusCode(401)
                .body("detail", containsString("reutilização"));

        // Como toda a família foi comprometida e revogada, o RT2 também deve ter sido invalidado!
        given()
                .contentType(ContentType.JSON)
                .body("{\"refreshToken\":\"%s\"}".formatted(refreshToken2))
                .when().post(REFRESH)
                .then().statusCode(401);
    }

    @Test
    void deveInvalidarRefreshTokenNoLogoutESuportarMultiplosDispositivos() {
        String email = "multidevice." + UUID.randomUUID() + "@exemplo.com.br";
        String senha = "Senh@Forte2026!";

        // Dispositivo 1 (no cadastro)
        Response disp1 = cadastrarUsuario("Usuário Multi", email, senha);
        String access1 = disp1.path("accessToken");
        String refresh1 = disp1.cookie("trilhara_rt");

        // Dispositivo 2 (novo login)
        Response disp2 = given()
                .contentType(ContentType.JSON)
                .header("User-Agent", "TrilharaMobile/1.0")
                .body("""
                        {"email":"%s","senha":"%s"}
                        """.formatted(email, senha))
                .when().post(LOGIN)
                .then().statusCode(200)
                .extract().response();
        String access2 = disp2.path("accessToken");
        String refresh2 = disp2.cookie("trilhara_rt");

        // Deve listar as 2 sessões ativas
        given().header("Authorization", "Bearer " + access1)
                .when().get(SESSOES)
                .then().statusCode(200)
                .body("$", hasSize(2));

        // Logout apenas do Dispositivo 1
        given().header("Authorization", "Bearer " + access1)
                .contentType(ContentType.JSON)
                .body("{\"refreshToken\":\"%s\"}".formatted(refresh1))
                .when().post(LOGOUT)
                .then().statusCode(204)
                .header("Set-Cookie", containsString("Max-Age=0"));

        // Refresh do Dispositivo 1 agora deve falhar (401)
        given().contentType(ContentType.JSON)
                .body("{\"refreshToken\":\"%s\"}".formatted(refresh1))
                .when().post(REFRESH)
                .then().statusCode(401);

        // Dispositivo 2 continua ativo e consegue fazer refresh
        String refresh2Novo = given().contentType(ContentType.JSON)
                .body("{\"refreshToken\":\"%s\"}".formatted(refresh2))
                .when().post(REFRESH)
                .then().statusCode(200)
                .extract().cookie("trilhara_rt");

        // Logout Global (revoga todas as sessões restantes)
        given().header("Authorization", "Bearer " + access2)
                .contentType(ContentType.JSON)
                .when().post(LOGOUT_ALL)
                .then().statusCode(204);

        given().contentType(ContentType.JSON)
                .body("{\"refreshToken\":\"%s\"}".formatted(refresh2Novo))
                .when().post(REFRESH)
                .then().statusCode(401);
    }

    @Test
    void deveNegarAcessoDeUsuarioComumEmRotasAdminEPermitirAdmin() {
        String emailUser = "comum." + UUID.randomUUID() + "@exemplo.com.br";
        Response respUser = cadastrarUsuario("Usuário Comum", emailUser, "Senh@Forte2026!");
        String tokenUser = respUser.path("accessToken");
        String userId = respUser.path("usuario.id");

        // Usuário com role USER deve receber 403 Forbidden nas rotas administrativas
        given().header("Authorization", "Bearer " + tokenUser)
                .when().get(ADMIN_USUARIOS)
                .then().statusCode(403);

        given().header("Authorization", "Bearer " + tokenUser)
                .when().get(ADMIN_EDITAIS)
                .then().statusCode(403);

        // Promove usuário para ADMIN e gera novo token
        Usuario admin = userService.atualizarRoles(userId, List.of(Role.USER, Role.ADMIN));
        String tokenAdmin = tokenService.gerarAccessToken(admin);

        given().header("Authorization", "Bearer " + tokenAdmin)
                .when().get(ADMIN_USUARIOS)
                .then().statusCode(200)
                .body("id", hasItem(userId));

        given().header("Authorization", "Bearer " + tokenAdmin)
                .when().get(ADMIN_EDITAIS)
                .then().statusCode(200);
    }

    @Test
    void deveBloquearContaAposMultiplasTentativasInvalidasDeLogin() {
        String email = "bruteforce." + UUID.randomUUID() + "@exemplo.com.br";
        String senhaCorreta = "Senh@Forte2026!";
        cadastrarUsuario("Alvo BruteForce", email, senhaCorreta);

        // 4 tentativas inválidas retornam 401
        for (int i = 1; i <= 4; i++) {
            given().contentType(ContentType.JSON)
                    .body("{\"email\":\"%s\",\"senha\":\"SenhaIncorreta%d!\"}".formatted(email, i))
                    .when().post(LOGIN)
                    .then().statusCode(401);
        }

        // 5ª tentativa atinge o limite de falhas consecutivas e bloqueia a conta (429 Too Many Requests)
        given().contentType(ContentType.JSON)
                .body("{\"email\":\"%s\",\"senha\":\"SenhaIncorreta5!\"}".formatted(email))
                .when().post(LOGIN)
                .then().statusCode(429)
                .header("Retry-After", notNullValue())
                .body("detail", containsString("bloqueada"));

        // Mesmo informando a senha correta durante o período de bloqueio, o login é recusado (429)
        given().contentType(ContentType.JSON)
                .body("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senhaCorreta))
                .when().post(LOGIN)
                .then().statusCode(429);
    }

    @Test
    void deveIsolarDadosESessoesEntreUsuariosDistintos() {
        Response userA = cadastrarUsuario("Usuário A", "user.a." + UUID.randomUUID() + "@exemplo.com.br", "Senh@Forte2026!");
        Response userB = cadastrarUsuario("Usuário B", "user.b." + UUID.randomUUID() + "@exemplo.com.br", "Senh@Forte2026!");

        String tokenA = userA.path("accessToken");
        String refreshA = userA.cookie("trilhara_rt");

        String tokenB = userB.path("accessToken");

        // Usuário A cria uma trilha autenticada
        String trilhaId = given()
                .header("Authorization", "Bearer " + tokenA)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "nome": "Trilha Privada do Usuário A",
                          "disciplinasIniciais": [{"id":"d1","nome":"Português","topicos":[]}]
                        }
                        """)
                .when().post(TRILHAS)
                .then().statusCode(201)
                .extract().path("id");

        // Usuário B tenta acessar ou deletar a trilha do Usuário A usando seu próprio token -> 404
        given().header("Authorization", "Bearer " + tokenB)
                .when().get(TRILHAS + "/{id}", trilhaId)
                .then().statusCode(404);

        given().header("Authorization", "Bearer " + tokenB)
                .when().delete(TRILHAS + "/{id}", trilhaId)
                .then().statusCode(404);

        // Usuário B tenta fazer logout passando o refreshToken do Usuário A -> 403 Forbidden
        given().header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON)
                .body("{\"refreshToken\":\"%s\"}".formatted(refreshA))
                .when().post(LOGOUT)
                .then().statusCode(403);

        // Usuário A ainda consegue acessar e excluir sua trilha normalmente
        given().header("Authorization", "Bearer " + tokenA)
                .when().delete(TRILHAS + "/{id}", trilhaId)
                .then().statusCode(204);
    }

    @Test
    void deveBloquearAcessoSemAutenticacaoEInvalidarSessoesAoTrocarOuResetarSenha() {
        // 1. Acesso sem autenticação em rotas protegidas de auth
        given().when().get(ME).then().statusCode(401);
        given().when().get(SESSOES).then().statusCode(401);
        // Logout é público e idempotente: revoga pelo cookie, sem exigir access token válido
        given().contentType(ContentType.JSON).body("{}").when().post(LOGOUT).then().statusCode(204);
        given().when().get(ADMIN_USUARIOS).then().statusCode(401);

        // 2. Acesso sem autenticação em rotas de negócio com modo estrito ativado
        securityFilter.setStrictMode(true);
        try {
            given().when().get(TRILHAS).then().statusCode(401);
            given().when().get(ADMIN_EDITAIS).then().statusCode(401);
        } finally {
            securityFilter.setStrictMode(false);
        }

        // 3. Troca de senha e recuperação por token único invalidam sessões anteriores
        String email = "seguranca.conta." + UUID.randomUUID() + "@exemplo.com.br";
        String senhaInicial = "Senh@Inicial2026!";
        String novaSenha = "Nov@SenhaForte2027!";

        Response cadastro = cadastrarUsuario("Usuário Senha", email, senhaInicial);
        String accessInicial = cadastro.path("accessToken");
        String refreshInicial = cadastro.cookie("trilhara_rt");

        given().header("Authorization", "Bearer " + accessInicial)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "senhaAtual": "%s",
                          "novaSenha": "%s"
                        }
                        """.formatted(senhaInicial, novaSenha))
                .when().put(SENHA)
                .then().statusCode(204);

        // Refresh token antigo foi revogado na alteração de senha
        given().contentType(ContentType.JSON)
                .body("{\"refreshToken\":\"%s\"}".formatted(refreshInicial))
                .when().post(REFRESH)
                .then().statusCode(401);

        // Solicitação e uso de token único de recuperação de senha
        String tokenReset = authService.solicitarRecuperacaoSenha(new RecuperarSenhaRequest(email), "127.0.0.1")
                .orElseThrow();

        String senhaAposReset = "Senh@Resetada2028!";
        given().contentType(ContentType.JSON)
                .body("""
                        {
                          "token": "%s",
                          "novaSenha": "%s"
                        }
                        """.formatted(tokenReset, senhaAposReset))
                .when().post(RESETAR_SENHA)
                .then().statusCode(204);

        // Token de recuperação é de uso único: tentar reutilizá-lo deve falhar com 400
        given().contentType(ContentType.JSON)
                .body("""
                        {
                          "token": "%s",
                          "novaSenha": "Outr@Senha2029!"
                        }
                        """.formatted(tokenReset))
                .when().post(RESETAR_SENHA)
                .then().statusCode(400);

        // Login com a nova senha redefinida funciona perfeitamente
        given().contentType(ContentType.JSON)
                .body("""
                        {"email":"%s","senha":"%s"}
                        """.formatted(email, senhaAposReset))
                .when().post(LOGIN)
                .then().statusCode(200);
    }

    private Response cadastrarUsuario(String nome, String email, String senha) {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "nome": "%s",
                          "email": "%s",
                          "senha": "%s"
                        }
                        """.formatted(nome, email, senha))
                .when().post(CADASTRO)
                .then().statusCode(201)
                .extract().response();
    }
}

