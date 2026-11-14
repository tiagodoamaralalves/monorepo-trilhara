package br.com.trilhara.sessao;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoClient;

import br.com.trilhara.auth.TokenService;
import br.com.trilhara.auth.UserRepository;
import br.com.trilhara.auth.Usuario;
import br.com.trilhara.trilha.DisciplinaRepository;
import br.com.trilhara.trilha.TrilhaRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

@QuarkusTest
@DisplayName("Domínio Estudos (Parte 2): Sessões em Segundos, Edição/Exclusão e Estatísticas Agregadas")
class SessaoNormalizadaIntegrationTest {

    private static final String API_TRILHAS = "/api/trilhas";
    private static final ZoneId FUSO_TESTES = ZoneId.of("America/Sao_Paulo");

    @Inject
    TrilhaRepository trilhaRepository;

    @Inject
    DisciplinaRepository disciplinaRepository;

    @Inject
    SessaoRepository sessaoRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    TokenService tokenService;

    @Inject
    MongoClient mongoClient;

    private String gerarTokenParaUsuario(ObjectId usuarioId) {
        Usuario u = new Usuario(
                usuarioId.toHexString(),
                "Usuario Teste F3",
                "teste-f3-" + usuarioId.toHexString() + "@trilhara.com.br",
                "$pbkdf2-sha512$mock",
                List.of(br.com.trilhara.auth.Role.USER),
                true,
                true,
                0,
                null,
                null,
                Instant.now(),
                Instant.now()
        );
        userRepository.inserir(u);
        return tokenService.gerarAccessToken(u);
    }

    private record TrilhaEDisciplinas(String trilhaId, String disciplina1Id, String disciplina2Id) {}

    private TrilhaEDisciplinas criarTrilhaComDisciplinas(String token) {
        String payload = """
                {
                  "nome": "Trilha F3 %s",
                  "cargo": "Analista",
                  "banca": "FGV",
                  "dataProva": "2026-11-30",
                  "cor": "#8F9E8B",
                  "disciplinasIniciais": [
                    {
                      "nome": "Português",
                      "cor": "#4A6B82",
                      "peso": 3
                    },
                    {
                      "nome": "Direito Administrativo",
                      "cor": "#D97706",
                      "peso": 2
                    }
                  ]
                }
                """.formatted(UUID.randomUUID());

        io.restassured.response.Response resp = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(API_TRILHAS)
                .then()
                .statusCode(201)
                .extract().response();

        String trilhaId = resp.path("id");
        String d1Id = resp.path("disciplinas[0].id");
        String d2Id = resp.path("disciplinas[1].id");

        return new TrilhaEDisciplinas(trilhaId, d1Id, d2Id);
    }

    @Test
    @DisplayName("3.1 Deve registrar sessão em segundos (duracaoSegundos = 95) e verificar persistência exata")
    void deveRegistrarSessaoEmSegundosEVerificarPersistenciaExata() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);
        TrilhaEDisciplinas setup = criarTrilhaComDisciplinas(token);

        String chave = UUID.randomUUID().toString();
        String payload = """
                {
                  "disciplinaId": "%s",
                  "dataSessao": "%s",
                  "duracaoSegundos": 95,
                  "questoes": 10,
                  "acertos": 8,
                  "chaveIdempotencia": "%s"
                }
                """.formatted(setup.disciplina1Id(), LocalDate.now(FUSO_TESTES), chave);

        given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", chave)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes")
                .then()
                .statusCode(201)
                .header("Location", containsString("/api/trilhas/" + setup.trilhaId() + "/sessoes/"))
                .body("duracaoSegundos", equalTo(95))
                .body("questoes", equalTo(10))
                .body("acertos", equalTo(8))
                .body("erros", equalTo(2))
                .body("percentualAcerto", equalTo(80.0f))
                .body("disciplinaNome", equalTo("Português"))
                .body("disciplinaCor", equalTo("#4A6B82"))
                .body("disciplinaRemovida", equalTo(false))
                .body("editavel", equalTo(true))
                .extract().path("id");

        // Verifica agregação refletindo exatamente 95 segundos
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(API_TRILHAS + "/" + setup.trilhaId() + "/estatisticas?periodo=tudo")
                .then()
                .statusCode(200)
                .body("totalSegundos", equalTo(95))
                .body("totalSessoes", equalTo(1))
                .body("totalQuestoes", equalTo(10))
                .body("totalAcertos", equalTo(8))
                .body("totalErros", equalTo(2))
                .body("percentualAcertoGlobal", equalTo(80.0f))
                .body("porDisciplina[0].totalSegundos", equalTo(95));
    }

    @Test
    @DisplayName("3.2 Deve garantir idempotência estrita (200 para replay idêntico, 409 para payload divergente)")
    void deveGarantirIdempotenciaEstrita() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);
        TrilhaEDisciplinas setup = criarTrilhaComDisciplinas(token);

        String chave = UUID.randomUUID().toString();
        String hoje = LocalDate.now(FUSO_TESTES).toString();
        String payload = """
                {
                  "disciplinaId": "%s",
                  "dataSessao": "%s",
                  "duracaoSegundos": 120,
                  "questoes": 5,
                  "acertos": 4
                }
                """.formatted(setup.disciplina1Id(), hoje);

        // 1ª chamada: 201 Created
        String sessaoId = given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", chave)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes")
                .then()
                .statusCode(201)
                .extract().path("id");

        // Replay idêntico com mesma Idempotency-Key: 200 OK e mesmo ID
        given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", chave)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes")
                .then()
                .statusCode(200)
                .body("id", equalTo(sessaoId));

        // Replay com mesma chave mas payload funcional divergente (duracaoSegundos diferente): 409 Conflict
        String payloadDivergente = """
                {
                  "disciplinaId": "%s",
                  "dataSessao": "%s",
                  "duracaoSegundos": 180,
                  "questoes": 5,
                  "acertos": 4
                }
                """.formatted(setup.disciplina1Id(), hoje);

        given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", chave)
                .contentType(ContentType.JSON)
                .body(payloadDivergente)
                .when()
                .post(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes")
                .then()
                .statusCode(409)
                .body("code", equalTo("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    @DisplayName("3.3 Deve validar cabeçalho Idempotency-Key (ausência e mismatch com body)")
    void deveValidarCabecalhoIdempotencyKey() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);
        TrilhaEDisciplinas setup = criarTrilhaComDisciplinas(token);

        String payload = """
                {
                  "disciplinaId": "%s",
                  "dataSessao": "%s",
                  "duracaoSegundos": 100,
                  "questoes": 0,
                  "acertos": 0
                }
                """.formatted(setup.disciplina1Id(), LocalDate.now(FUSO_TESTES));

        // Ausência de Idempotency-Key: 400 IDEMPOTENCY_KEY_REQUIRED
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes")
                .then()
                .statusCode(400)
                .body("code", equalTo("IDEMPOTENCY_KEY_REQUIRED"));

        // Divergência entre Idempotency-Key e body chaveIdempotencia: 400 IDEMPOTENCY_KEY_MISMATCH
        String payloadComChaveDivergente = """
                {
                  "disciplinaId": "%s",
                  "dataSessao": "%s",
                  "duracaoSegundos": 100,
                  "questoes": 0,
                  "acertos": 0,
                  "chaveIdempotencia": "chave-no-body-123"
                }
                """.formatted(setup.disciplina1Id(), LocalDate.now(FUSO_TESTES));

        given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "chave-no-header-456")
                .contentType(ContentType.JSON)
                .body(payloadComChaveDivergente)
                .when()
                .post(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes")
                .then()
                .statusCode(400)
                .body("code", equalTo("IDEMPOTENCY_KEY_MISMATCH"));
    }

    @Test
    @DisplayName("3.4 Deve rejeitar dataSessao futura em relação ao TimezoneContext")
    void deveRejeitarDataSessaoFutura() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);
        TrilhaEDisciplinas setup = criarTrilhaComDisciplinas(token);

        String amanha = LocalDate.now(FUSO_TESTES).plusDays(1).toString();
        String payload = """
                {
                  "disciplinaId": "%s",
                  "dataSessao": "%s",
                  "duracaoSegundos": 100
                }
                """.formatted(setup.disciplina1Id(), amanha);

        given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("3.5 Deve permitir editar apenas a sessão mais recente da trilha (ordem canônica) e rejeitar as demais")
    void devePermitirEditarApenasSessaoMaisRecente() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);
        TrilhaEDisciplinas setup = criarTrilhaComDisciplinas(token);

        LocalDate hoje = LocalDate.now(FUSO_TESTES);

        // Sessão 1: hoje - 2 dias (mais antiga)
        String s1Id = given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(ContentType.JSON)
                .body("""
                        {"disciplinaId":"%s","dataSessao":"%s","duracaoSegundos":100,"questoes":5,"acertos":3}
                        """.formatted(setup.disciplina1Id(), hoje.minusDays(2)))
                .when().post(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes")
                .then().statusCode(201).extract().path("id");

        // Sessão 2: hoje - 1 dia (intermediária)
        String s2Id = given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(ContentType.JSON)
                .body("""
                        {"disciplinaId":"%s","dataSessao":"%s","duracaoSegundos":200,"questoes":10,"acertos":8}
                        """.formatted(setup.disciplina2Id(), hoje.minusDays(1)))
                .when().post(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes")
                .then().statusCode(201).extract().path("id");

        // Sessão 3: hoje (mais recente global)
        String s3Id = given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(ContentType.JSON)
                .body("""
                        {"disciplinaId":"%s","dataSessao":"%s","duracaoSegundos":300,"questoes":20,"acertos":15}
                        """.formatted(setup.disciplina1Id(), hoje))
                .when().post(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes")
                .then().statusCode(201).extract().path("id");

        // Listagem: verifica que s3 possui editavel: true e s1, s2 possuem editavel: false
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes?pagina=1&tamanho=10")
                .then()
                .statusCode(200)
                .body("itens[0].id", equalTo(s3Id))
                .body("itens[0].editavel", equalTo(true))
                .body("itens[1].id", equalTo(s2Id))
                .body("itens[1].editavel", equalTo(false))
                .body("itens[2].id", equalTo(s1Id))
                .body("itens[2].editavel", equalTo(false));

        // Edição de s3 (mais recente): 200 OK
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"duracaoSegundos": 360, "questoes": 25, "acertos": 22}
                        """)
                .when()
                .patch(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes/" + s3Id)
                .then()
                .statusCode(200)
                .body("duracaoSegundos", equalTo(360))
                .body("questoes", equalTo(25))
                .body("acertos", equalTo(22))
                .body("erros", equalTo(3))
                .body("percentualAcerto", equalTo(88.0f))
                .body("editavel", equalTo(true));

        // Tentativa de edição em s2 ou s1: 409 Conflict (LATEST_SESSION_ONLY)
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"duracaoSegundos": 250, "questoes": 10, "acertos": 9}
                        """)
                .when()
                .patch(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes/" + s2Id)
                .then()
                .statusCode(409)
                .body("code", equalTo("LATEST_SESSION_ONLY"));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"duracaoSegundos": 150, "questoes": 5, "acertos": 4}
                        """)
                .when()
                .patch(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes/" + s1Id)
                .then()
                .statusCode(409)
                .body("code", equalTo("LATEST_SESSION_ONLY"));
    }

    @Test
    @DisplayName("3.6 Deve excluir individualmente uma sessão com retenção de tombstone por 24h e bloquear retry da chave")
    void deveExcluirIndividualmenteComTombstone() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);
        TrilhaEDisciplinas setup = criarTrilhaComDisciplinas(token);

        String chave = UUID.randomUUID().toString();
        String sessaoId = given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", chave)
                .contentType(ContentType.JSON)
                .body("""
                        {"disciplinaId":"%s","dataSessao":"%s","duracaoSegundos":150,"questoes":5,"acertos":5}
                        """.formatted(setup.disciplina1Id(), LocalDate.now(FUSO_TESTES)))
                .when().post(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes")
                .then().statusCode(201).extract().path("id");

        // Exclusão individual da sessão: 204 No Content
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes/" + sessaoId)
                .then()
                .statusCode(204);

        // Sessão não aparece mais no histórico
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes")
                .then()
                .statusCode(200)
                .body("itens", hasSize(0));

        // Reenvio da mesma requisição com a mesma chave dentro de 24h: 409 IDEMPOTENCY_RESULT_REMOVED
        given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", chave)
                .contentType(ContentType.JSON)
                .body("""
                        {"disciplinaId":"%s","dataSessao":"%s","duracaoSegundos":150,"questoes":5,"acertos":5}
                        """.formatted(setup.disciplina1Id(), LocalDate.now(FUSO_TESTES)))
                .when()
                .post(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes")
                .then()
                .statusCode(409)
                .body("code", equalTo("IDEMPOTENCY_RESULT_REMOVED"));

        // Excluir novamente: 404 Not Found
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes/" + sessaoId)
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("3.7 Deve preservar sessões ao excluir disciplina da trilha e permitir limpeza dedicada")
    void devePreservarSessoesAoExcluirDisciplinaELimparDedicado() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);
        TrilhaEDisciplinas setup = criarTrilhaComDisciplinas(token);

        LocalDate hoje = LocalDate.now(FUSO_TESTES);

        // Registra sessão na disciplina 1
        given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(ContentType.JSON)
                .body("""
                        {"disciplinaId":"%s","dataSessao":"%s","duracaoSegundos":100,"questoes":5,"acertos":5}
                        """.formatted(setup.disciplina1Id(), hoje))
                .when().post(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes")
                .then().statusCode(201);

        // Registra duas sessões na disciplina 2
        given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(ContentType.JSON)
                .body("""
                        {"disciplinaId":"%s","dataSessao":"%s","duracaoSegundos":200,"questoes":10,"acertos":8}
                        """.formatted(setup.disciplina2Id(), hoje))
                .when().post(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes")
                .then().statusCode(201);

        given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(ContentType.JSON)
                .body("""
                        {"disciplinaId":"%s","dataSessao":"%s","duracaoSegundos":300,"questoes":15,"acertos":12}
                        """.formatted(setup.disciplina2Id(), hoje))
                .when().post(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes")
                .then().statusCode(201);

        // Exclui a disciplina 2 da trilha
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete(API_TRILHAS + "/" + setup.trilhaId() + "/disciplinas/" + setup.disciplina2Id())
                .then()
                .statusCode(204);

        // As sessões da disciplina 2 continuam presentes no histórico, marcadas com disciplinaRemovida = true!
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes?pagina=1&tamanho=10")
                .then()
                .statusCode(200)
                .body("totalItens", equalTo(3))
                .body("itens[0].disciplinaRemovida", equalTo(true))
                .body("itens[0].disciplinaNome", equalTo("Direito Administrativo"))
                .body("itens[0].disciplinaCor", equalTo("#D97706"))
                .body("itens[1].disciplinaRemovida", equalTo(true))
                .body("itens[2].disciplinaRemovida", equalTo(false))
                .body("itens[2].disciplinaNome", equalTo("Português"));

        // Nas estatísticas, a disciplina removida também aparece sinalizada
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(API_TRILHAS + "/" + setup.trilhaId() + "/estatisticas?periodo=tudo")
                .then()
                .statusCode(200)
                .body("totalSessoes", equalTo(3))
                .body("totalSegundos", equalTo(600))
                .body("porDisciplina", hasSize(2))
                .body("porDisciplina[0].disciplinaRemovida", equalTo(false))
                .body("porDisciplina[1].disciplinaRemovida", equalTo(true))
                .body("porDisciplina[1].nome", equalTo("Direito Administrativo"))
                .body("porDisciplina[1].totalSegundos", equalTo(500))
                .body("porDisciplina[1].totalSessoes", equalTo(2));

        // Limpeza dedicada das sessões da disciplina removida: DELETE /disciplinas/{id}/sessoes
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete(API_TRILHAS + "/" + setup.trilhaId() + "/disciplinas/" + setup.disciplina2Id() + "/sessoes")
                .then()
                .statusCode(200)
                .body("sessoesRemovidas", equalTo(2))
                .body("totalSegundosRemovidos", equalTo(500));

        // Agora restou apenas a sessão da disciplina 1
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes")
                .then()
                .statusCode(200)
                .body("totalItens", equalTo(1))
                .body("itens[0].disciplinaNome", equalTo("Português"));
    }

    @Test
    @DisplayName("3.8 Deve validar paginação e períodos nas estatísticas e no histórico")
    void deveValidarPaginacaoEPeriodos() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);
        TrilhaEDisciplinas setup = criarTrilhaComDisciplinas(token);

        // Paginação inválida: pagina < 1 ou tamanho > 100
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes?pagina=0")
                .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_PAGINATION"));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes?tamanho=101")
                .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_PAGINATION"));

        // Intervalo de datas invertido: 400 VALIDATION_ERROR
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(API_TRILHAS + "/" + setup.trilhaId() + "/sessoes?dataInicio=2026-11-10&dataFim=2026-11-01")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));

        // Estatísticas com período inválido: 400 VALIDATION_ERROR
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(API_TRILHAS + "/" + setup.trilhaId() + "/estatisticas?periodo=invalido")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));

        // Estatísticas vazias: totais zerados sem exceção
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(API_TRILHAS + "/" + setup.trilhaId() + "/estatisticas?periodo=7d")
                .then()
                .statusCode(200)
                .body("totalSegundos", equalTo(0))
                .body("totalSessoes", equalTo(0))
                .body("totalQuestoes", equalTo(0))
                .body("totalAcertos", equalTo(0))
                .body("percentualAcertoGlobal", equalTo(0.0f))
                .body("porDisciplina", hasSize(0))
                .body("serieTemporal", hasSize(0))
                .body("streakDias", equalTo(0));
    }

    @Test
    @DisplayName("3.9 Deve garantir isolamento estrito por usuarioId")
    void deveGarantirIsolamentoEstritoPorUsuarioId() {
        ObjectId usuarioA = new ObjectId();
        String tokenA = gerarTokenParaUsuario(usuarioA);
        TrilhaEDisciplinas setupA = criarTrilhaComDisciplinas(tokenA);

        ObjectId usuarioB = new ObjectId();
        String tokenB = gerarTokenParaUsuario(usuarioB);

        // Usuário A registra sessão
        String sessaoAId = given()
                .header("Authorization", "Bearer " + tokenA)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(ContentType.JSON)
                .body("""
                        {"disciplinaId":"%s","dataSessao":"%s","duracaoSegundos":120,"questoes":5,"acertos":5}
                        """.formatted(setupA.disciplina1Id(), LocalDate.now(FUSO_TESTES)))
                .when().post(API_TRILHAS + "/" + setupA.trilhaId() + "/sessoes")
                .then().statusCode(201).extract().path("id");

        // Usuário B tenta ler a sessão de A: 404 Not Found
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .get(API_TRILHAS + "/" + setupA.trilhaId() + "/sessoes")
                .then()
                .statusCode(404);

        // Usuário B tenta obter estatísticas de A: 404 Not Found
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .get(API_TRILHAS + "/" + setupA.trilhaId() + "/estatisticas")
                .then()
                .statusCode(404);

        // Usuário B tenta editar a sessão de A: 404 Not Found
        given()
                .header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON)
                .body("""
                        {"duracaoSegundos": 200, "questoes": 5, "acertos": 5}
                        """)
                .when()
                .patch(API_TRILHAS + "/" + setupA.trilhaId() + "/sessoes/" + sessaoAId)
                .then()
                .statusCode(404);

        // Usuário B tenta excluir a sessão de A: 404 Not Found
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .delete(API_TRILHAS + "/" + setupA.trilhaId() + "/sessoes/" + sessaoAId)
                .then()
                .statusCode(404);
    }

    // =========================================================================
    // Cobertura obrigatória da revisão de sessões (nomes normativos)
    // =========================================================================

    /** Setup padrão da trilha com 2 disciplinas para um usuário novo. */
    private record Setup(String token, TrilhaEDisciplinas trilhas) {}

    private Setup setup() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);
        return new Setup(token, criarTrilhaComDisciplinas(token));
    }

    private String postarSessao(
            String token, String trilhaId, String disciplinaId, String dataSessao,
            int duracao, int questoes, int acertos, String chave) {
        return given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", chave)
                .contentType(ContentType.JSON)
                .body("""
                        {"disciplinaId":"%s","dataSessao":"%s","duracaoSegundos":%d,"questoes":%d,"acertos":%d}
                        """.formatted(disciplinaId, dataSessao, duracao, questoes, acertos))
                .when()
                .post(API_TRILHAS + "/" + trilhaId + "/sessoes")
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    @Test
    @DisplayName("1: Idempotency-Key ausente retorna 400 IDEMPOTENCY_KEY_REQUIRED sem criar sessão")
    void deveExigirIdempotencyKey() {
        Setup ctx = setup();
        String hoje = LocalDate.now(FUSO_TESTES).toString();

        given()
                .header("Authorization", "Bearer " + ctx.token())
                .contentType(ContentType.JSON)
                .body("""
                        {"disciplinaId":"%s","dataSessao":"%s","duracaoSegundos":120,"questoes":0,"acertos":0}
                        """.formatted(ctx.trilhas().disciplina1Id(), hoje))
                .when()
                .post(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/sessoes")
                .then()
                .statusCode(400)
                .body("code", equalTo("IDEMPOTENCY_KEY_REQUIRED"));

        given()
                .header("Authorization", "Bearer " + ctx.token())
                .when()
                .get(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/sessoes")
                .then()
                .statusCode(200)
                .body("totalItens", equalTo(0));
    }

    @Test
    @DisplayName("2: concorrência da mesma chave produz 1 documento e respostas 201 + 200")
    void deveTratarConcorrenciaDaMesmaChave() throws Exception {
        Setup ctx = setup();
        String hoje = LocalDate.now(FUSO_TESTES).toString();
        String chave = UUID.randomUUID().toString();
        String payload = """
                {"disciplinaId":"%s","dataSessao":"%s","duracaoSegundos":300,"questoes":10,"acertos":7}
                """.formatted(ctx.trilhas().disciplina1Id(), hoje);

        java.util.concurrent.Callable<Integer> requisicao = () -> given()
                .header("Authorization", "Bearer " + ctx.token())
                .header("Idempotency-Key", chave)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/sessoes")
                .then()
                .extract().statusCode();

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.List<Integer> status = new java.util.ArrayList<>();
        try {
            java.util.List<java.util.concurrent.Future<Integer>> futuros = new java.util.ArrayList<>();
            futuros.add(pool.submit(requisicao));
            futuros.add(pool.submit(requisicao));
            for (java.util.concurrent.Future<Integer> f : futuros) {
                status.add(f.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        java.util.Collections.sort(status);
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(200, 201), status,
                "a corrida do E11000 deve resolver em uma criação e um replay");

        given()
                .header("Authorization", "Bearer " + ctx.token())
                .when()
                .get(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/sessoes")
                .then()
                .statusCode(200)
                .body("totalItens", equalTo(1));
    }

    @Test
    @DisplayName("3: retry após editar retorna a sessão já atualizada (mesma chave, mesmo hash)")
    void retryAposEditarRetornaSessaoAtual() {
        Setup ctx = setup();
        String hoje = LocalDate.now(FUSO_TESTES).toString();
        String chave = UUID.randomUUID().toString();
        String payload = """
                {"disciplinaId":"%s","dataSessao":"%s","duracaoSegundos":120,"questoes":5,"acertos":4}
                """.formatted(ctx.trilhas().disciplina1Id(), hoje);

        String sessaoId = postarSessao(ctx.token(), ctx.trilhas().trilhaId(),
                ctx.trilhas().disciplina1Id(), hoje, 120, 5, 4, chave);

        given()
                .header("Authorization", "Bearer " + ctx.token())
                .contentType(ContentType.JSON)
                .body("""
                        {"duracaoSegundos":240,"questoes":5,"acertos":5}
                        """)
                .when()
                .patch(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/sessoes/" + sessaoId)
                .then()
                .statusCode(200)
                .body("duracaoSegundos", equalTo(240));

        // Retry do POST original: devolve o estado atual, não um 409.
        given()
                .header("Authorization", "Bearer " + ctx.token())
                .header("Idempotency-Key", chave)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/sessoes")
                .then()
                .statusCode(200)
                .body("id", equalTo(sessaoId))
                .body("duracaoSegundos", equalTo(240));
    }

    @Test
    @DisplayName("4: retry após excluir retorna 409 IDEMPOTENCY_RESULT_REMOVED")
    void retryAposExcluirRetorna409ResultRemoved() {
        Setup ctx = setup();
        String hoje = LocalDate.now(FUSO_TESTES).toString();
        String chave = UUID.randomUUID().toString();
        String payload = """
                {"disciplinaId":"%s","dataSessao":"%s","duracaoSegundos":150,"questoes":3,"acertos":2}
                """.formatted(ctx.trilhas().disciplina1Id(), hoje);

        String sessaoId = postarSessao(ctx.token(), ctx.trilhas().trilhaId(),
                ctx.trilhas().disciplina1Id(), hoje, 150, 3, 2, chave);

        given()
                .header("Authorization", "Bearer " + ctx.token())
                .when()
                .delete(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/sessoes/" + sessaoId)
                .then()
                .statusCode(204);

        given()
                .header("Authorization", "Bearer " + ctx.token())
                .header("Idempotency-Key", chave)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/sessoes")
                .then()
                .statusCode(409)
                .body("code", equalTo("IDEMPOTENCY_RESULT_REMOVED"));
    }

    @Test
    @DisplayName("5: editavel é global mesmo com filtro de disciplina que exclui a sessão nº 1")
    void editavelEhGlobalMesmoComFiltroDeDisciplina() {
        Setup ctx = setup();
        LocalDate hoje = LocalDate.now(FUSO_TESTES);

        postarSessao(ctx.token(), ctx.trilhas().trilhaId(), ctx.trilhas().disciplina1Id(),
                hoje.minusDays(2).toString(), 100, 0, 0, UUID.randomUUID().toString());
        String maisRecente = postarSessao(ctx.token(), ctx.trilhas().trilhaId(),
                ctx.trilhas().disciplina2Id(), hoje.toString(), 200, 0, 0,
                UUID.randomUUID().toString());

        // Filtrando pela disciplina 1, a sessão nº 1 global (da disciplina 2) não aparece:
        // a sessão listada NÃO pode ser marcada como editável.
        given()
                .header("Authorization", "Bearer " + ctx.token())
                .when()
                .get(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/sessoes?disciplinaId="
                        + ctx.trilhas().disciplina1Id())
                .then()
                .statusCode(200)
                .body("totalItens", equalTo(1))
                .body("itens[0].editavel", equalTo(false));

        given()
                .header("Authorization", "Bearer " + ctx.token())
                .when()
                .get(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/sessoes")
                .then()
                .statusCode(200)
                .body("itens[0].id", equalTo(maisRecente))
                .body("itens[0].editavel", equalTo(true))
                .body("itens[1].editavel", equalTo(false));
    }

    @Test
    @DisplayName("6: editavel é falso em todas as sessões da página 2")
    void editavelFalsoNaPagina2() {
        Setup ctx = setup();
        LocalDate hoje = LocalDate.now(FUSO_TESTES);

        for (int i = 0; i < 3; i++) {
            postarSessao(ctx.token(), ctx.trilhas().trilhaId(), ctx.trilhas().disciplina1Id(),
                    hoje.minusDays(i).toString(), 100, 0, 0, UUID.randomUUID().toString());
        }

        given()
                .header("Authorization", "Bearer " + ctx.token())
                .when()
                .get(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/sessoes?pagina=2&tamanho=1")
                .then()
                .statusCode(200)
                .body("itens", hasSize(1))
                .body("itens[0].editavel", equalTo(false));
    }

    @Test
    @DisplayName("7: tombstone não aparece na listagem nem nas estatísticas")
    void tombstoneNaoApareceEmListagemNemEstatistica() {
        Setup ctx = setup();
        LocalDate hoje = LocalDate.now(FUSO_TESTES);

        postarSessao(ctx.token(), ctx.trilhas().trilhaId(), ctx.trilhas().disciplina1Id(),
                hoje.minusDays(1).toString(), 100, 2, 2, UUID.randomUUID().toString());
        String removida = postarSessao(ctx.token(), ctx.trilhas().trilhaId(),
                ctx.trilhas().disciplina1Id(), hoje.toString(), 500, 10, 10,
                UUID.randomUUID().toString());

        given()
                .header("Authorization", "Bearer " + ctx.token())
                .when()
                .delete(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/sessoes/" + removida)
                .then()
                .statusCode(204);

        given()
                .header("Authorization", "Bearer " + ctx.token())
                .when()
                .get(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/sessoes")
                .then()
                .statusCode(200)
                .body("totalItens", equalTo(1));

        given()
                .header("Authorization", "Bearer " + ctx.token())
                .when()
                .get(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/estatisticas?periodo=tudo")
                .then()
                .statusCode(200)
                .body("totalSegundos", equalTo(100))
                .body("totalSessoes", equalTo(1))
                .body("totalQuestoes", equalTo(2));
    }

    @Test
    @DisplayName("8: período invertido no histórico e nas estatísticas retorna 400")
    void periodoInvertidoRetorna400() {
        Setup ctx = setup();
        String filtro = "?dataInicio=2026-11-10&dataFim=2026-11-01";

        given()
                .header("Authorization", "Bearer " + ctx.token())
                .when()
                .get(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/sessoes" + filtro)
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));

        given()
                .header("Authorization", "Bearer " + ctx.token())
                .when()
                .get(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/estatisticas" + filtro)
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("9: estatística vazia retorna zeros, percentual 0 e streak 0")
    void estatisticaVaziaRetornaZerosEPercentual0() {
        Setup ctx = setup();

        given()
                .header("Authorization", "Bearer " + ctx.token())
                .when()
                .get(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/estatisticas?periodo=7d")
                .then()
                .statusCode(200)
                .body("totalSegundos", equalTo(0))
                .body("totalSessoes", equalTo(0))
                .body("percentualAcertoGlobal", equalTo(0.0f))
                .body("porDisciplina", hasSize(0))
                .body("serieTemporal", hasSize(0))
                .body("streakDias", equalTo(0))
                .body("granularidade", equalTo("DIA"));
    }

    @Test
    @DisplayName("11: disciplina de outra trilha do mesmo usuário retorna 400 em errors.disciplinaId")
    void disciplinaDeOutraTrilhaRetorna400() {
        Setup ctx = setup();
        TrilhaEDisciplinas outra = criarTrilhaComDisciplinas(ctx.token());
        String hoje = LocalDate.now(FUSO_TESTES).toString();

        given()
                .header("Authorization", "Bearer " + ctx.token())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(ContentType.JSON)
                .body("""
                        {"disciplinaId":"%s","dataSessao":"%s","duracaoSegundos":120,"questoes":0,"acertos":0}
                        """.formatted(outra.disciplina1Id(), hoje))
                .when()
                .post(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/sessoes")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("errors.disciplinaId", hasSize(1));
    }

    @Test
    @DisplayName("12: dataSessao futura retorna 400 em errors.dataSessao")
    void dataSessaoFuturaRetorna400NoCampoDataSessao() {
        Setup ctx = setup();
        String amanha = LocalDate.now(FUSO_TESTES).plusDays(1).toString();

        given()
                .header("Authorization", "Bearer " + ctx.token())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(ContentType.JSON)
                .body("""
                        {"disciplinaId":"%s","dataSessao":"%s","duracaoSegundos":120,"questoes":0,"acertos":0}
                        """.formatted(ctx.trilhas().disciplina1Id(), amanha))
                .when()
                .post(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/sessoes")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("errors.dataSessao", hasSize(1));
    }

    @Test
    @DisplayName("10: granularidade semanal/mensal preserva os totais das estatísticas")
    void granularidadeSemanalPreservaTotais() {
        Setup ctx = setup();
        LocalDate hoje = LocalDate.now(FUSO_TESTES);

        postarSessao(ctx.token(), ctx.trilhas().trilhaId(), ctx.trilhas().disciplina1Id(),
                hoje.minusDays(70).toString(), 600, 10, 8, UUID.randomUUID().toString());
        postarSessao(ctx.token(), ctx.trilhas().trilhaId(), ctx.trilhas().disciplina1Id(),
                hoje.minusDays(69).toString(), 400, 5, 5, UUID.randomUUID().toString());
        postarSessao(ctx.token(), ctx.trilhas().trilhaId(), ctx.trilhas().disciplina2Id(),
                hoje.minusDays(3).toString(), 200, 0, 0, UUID.randomUUID().toString());

        // 90 dias → SEMANA, com os totais completos.
        given()
                .header("Authorization", "Bearer " + ctx.token())
                .when()
                .get(API_TRILHAS + "/" + ctx.trilhas().trilhaId()
                        + "/estatisticas?dataInicio=" + hoje.minusDays(89) + "&dataFim=" + hoje)
                .then()
                .statusCode(200)
                .body("granularidade", equalTo("SEMANA"))
                .body("totalSegundos", equalTo(1200))
                .body("totalSessoes", equalTo(3))
                .body("totalQuestoes", equalTo(15))
                .body("totalAcertos", equalTo(13));

        // 31 dias → DIA, ignorando as sessões antigas.
        given()
                .header("Authorization", "Bearer " + ctx.token())
                .when()
                .get(API_TRILHAS + "/" + ctx.trilhas().trilhaId()
                        + "/estatisticas?dataInicio=" + hoje.minusDays(30) + "&dataFim=" + hoje)
                .then()
                .statusCode(200)
                .body("granularidade", equalTo("DIA"))
                .body("totalSegundos", equalTo(200));

        // "tudo" → MES, com o total completo.
        given()
                .header("Authorization", "Bearer " + ctx.token())
                .when()
                .get(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/estatisticas?periodo=tudo")
                .then()
                .statusCode(200)
                .body("granularidade", equalTo("MES"))
                .body("totalSegundos", equalTo(1200));
    }

    @Test
    @DisplayName("13: streak considera ontem quando não há sessão hoje")
    void streakConsideraOntemQuandoSemSessaoHoje() {
        Setup ctx = setup();
        LocalDate hoje = LocalDate.now(FUSO_TESTES);

        postarSessao(ctx.token(), ctx.trilhas().trilhaId(), ctx.trilhas().disciplina1Id(),
                hoje.minusDays(1).toString(), 100, 0, 0, UUID.randomUUID().toString());
        postarSessao(ctx.token(), ctx.trilhas().trilhaId(), ctx.trilhas().disciplina1Id(),
                hoje.minusDays(2).toString(), 100, 0, 0, UUID.randomUUID().toString());

        given()
                .header("Authorization", "Bearer " + ctx.token())
                .when()
                .get(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/estatisticas?periodo=7d")
                .then()
                .statusCode(200)
                .body("streakDias", equalTo(2));
    }

    @Test
    @DisplayName("14: streak independe do filtro de período (calculado sobre a trilha inteira)")
    void streakIndependeDoFiltroDePeriodo() {
        Setup ctx = setup();
        LocalDate hoje = LocalDate.now(FUSO_TESTES);

        postarSessao(ctx.token(), ctx.trilhas().trilhaId(), ctx.trilhas().disciplina1Id(),
                hoje.toString(), 100, 0, 0, UUID.randomUUID().toString());
        postarSessao(ctx.token(), ctx.trilhas().trilhaId(), ctx.trilhas().disciplina1Id(),
                hoje.minusDays(1).toString(), 100, 0, 0, UUID.randomUUID().toString());
        postarSessao(ctx.token(), ctx.trilhas().trilhaId(), ctx.trilhas().disciplina1Id(),
                hoje.minusDays(120).toString(), 100, 0, 0, UUID.randomUUID().toString());

        given()
                .header("Authorization", "Bearer " + ctx.token())
                .when()
                .get(API_TRILHAS + "/" + ctx.trilhas().trilhaId() + "/estatisticas?periodo=7d")
                .then()
                .statusCode(200)
                .body("streakDias", equalTo(2))
                .body("totalSessoes", equalTo(2));
    }
}
