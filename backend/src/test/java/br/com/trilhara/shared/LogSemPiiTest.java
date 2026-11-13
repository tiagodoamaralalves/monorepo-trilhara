package br.com.trilhara.shared;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.trilhara.auth.TokenService;
import br.com.trilhara.auth.UserRepository;
import br.com.trilhara.auth.Usuario;
import br.com.trilhara.support.CapturadorDeLogs;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/**
 * R13/D-04 — nenhum identificador pessoal em log de nível {@code INFO} ou superior.
 *
 * <p>Substitui a cobertura pontual de {@code AccountDeletionIntegrationTest#logsNaoContemUsuarioId}
 * (que cobre só a exclusão de conta) por uma varredura do ciclo de vida auditável: login, leitura
 * autenticada, busca de notas e as rotas que historicamente vazavam dados
 * ({@code EmailService}, {@code PushDeliveryScheduler}).</p>
 *
 * <p>As asserções usam o dado concreto do fixture — e-mail, nome, hex do {@code usuarioId} e o
 * access token — em vez de "procurar qualquer coisa parecida com e-mail": um objeto diferente
 * não gera falso positivo e o mesmo objeto gera falha garantida.</p>
 */
@QuarkusTest
@DisplayName("R13 - Logs sem PII em INFO+ (D-04)")
class LogSemPiiTest {

    /** Formato de e-mail: se um destinatário real escapar, este padrão casa. */
    private static final Pattern PADRAO_EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /** Hex de 24 caracteres é o formato de ObjectId; nenhum deve aparecer em log. */
    private static final Pattern PADRAO_OBJECTID = Pattern.compile("\\b[0-9a-f]{24}\\b");

    /** Marca de token: `Bearer x` e `codigo=` nunca podem aparecer na trilha. */
    private static final Pattern PADRAO_TOKEN =
            Pattern.compile("Bearer\\s+[A-Za-z0-9._-]{10,}|codigo=[A-Za-z0-9._-]{6,}");

    @Inject
    TokenService tokenService;

    @Inject
    UserRepository userRepository;

    private Usuario usuario;
    private String email;
    private String nome;
    private String token;

    @BeforeEach
    void setUp() {
        email = "pii-" + UUID.randomUUID() + "@trilhara.test";
        nome = "Fulano Sigiloso da Silva";
        String usuarioId = new ObjectId().toHexString();
        Instant agora = Instant.now();
        usuario = new Usuario(
                usuarioId, nome, email, "hash", List.of(),
                true, true, 0, null, agora, agora, agora);
        userRepository.inserir(usuario);
        token = tokenService.gerarAccessToken(usuario);
    }

    private void assertarSemPii(String capturado, String cenario) {
        assertFalse(capturado.contains(email),
                cenario + ": o e-mail do usuário não pode aparecer em log");
        assertFalse(capturado.contains(nome),
                cenario + ": o nome do usuário não pode aparecer em log");
        assertFalse(capturado.contains(usuario.id()),
                cenario + ": o hex do usuarioId não pode aparecer em log");
        assertFalse(capturado.contains(token),
                cenario + ": o access token não pode aparecer em log");
        assertFalse(PADRAO_OBJECTID.matcher(capturado).find(),
                cenario + ": nenhum ObjectId em hex pode aparecer em log (D-04)");
        assertFalse(PADRAO_TOKEN.matcher(capturado).find(),
                cenario + ": nenhum token/código pode aparecer em log (D-04)");
    }

    @Test
    @DisplayName("login, leitura autenticada e busca não registram e-mail, nome, token nem usuarioId")
    void cicloAutenticadoNaoVazaPii() {
        try (CapturadorDeLogs logs = CapturadorDeLogs.iniciar()) {
            given()
                    .contentType(ContentType.JSON)
                    .body("{\"email\":\"" + email + "\",\"senha\":\"senhaErradaParaNaoAutenticar\"}")
                    .when().post("/api/auth/login");

            given()
                    .header("Authorization", "Bearer " + token)
                    .when().get("/api/trilhas");

            given()
                    .header("Authorization", "Bearer " + token)
                    .queryParam("q", "resumo")
                    .when().get("/api/notas/busca");

            given()
                    .header("Authorization", "Bearer " + token)
                    .when().get("/api/tarefas/hoje");

            assertarSemPii(logs.textoCompleto(), "ciclo autenticado");
        }
    }

    @Test
    @DisplayName("recuperação de senha não registra o link (que carrega o token) nem o destinatário")
    void recuperacaoSenhaNaoVazaLinkNemDestinatario() {
        try (CapturadorDeLogs logs = CapturadorDeLogs.iniciar()) {
            given()
                    .contentType(ContentType.JSON)
                    .body("{\"email\":\"" + email + "\"}")
                    .when().post("/api/auth/recuperar-senha")
                    .then().statusCode(200);

            String capturado = logs.textoCompleto();
            assertFalse(capturado.contains(email),
                    "recuperação de senha: o destinatário não pode aparecer em log");
            assertFalse(capturado.contains("token=") || capturado.contains("codigo="),
                    "recuperação de senha: o link com token não pode aparecer em log");
        }
    }

    @Test
    @DisplayName("nenhum e-mail no formato padrão aparece em log durante tentativa de login")
    void nenhumEmailEmFormatoPadraoNoLog() {
        try (CapturadorDeLogs logs = CapturadorDeLogs.iniciar()) {
            given()
                    .contentType(ContentType.JSON)
                    .body("{\"email\":\"" + email + "\",\"senha\":\"incorreta-123\"}")
                    .when().post("/api/auth/login");

            String capturado = logs.textoCompleto();
            var matcher = PADRAO_EMAIL.matcher(capturado);
            StringBuilder encontrados = new StringBuilder();
            while (matcher.find()) {
                encontrados.append(matcher.group()).append(' ');
            }
            assertFalse(!encontrados.isEmpty(),
                    "Log contém e-mail(s) em INFO+: " + encontrados);
        }
    }
}
