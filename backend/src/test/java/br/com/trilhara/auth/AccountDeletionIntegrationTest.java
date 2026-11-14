package br.com.trilhara.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import br.com.trilhara.shared.mongo.ColecaoTrilhara;
import br.com.trilhara.support.CapturadorDeLogs;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;

/**
 * exclusão de conta nas 18 coleções filhas privadas.
 *
 * <p>As fixtures são dirigidas por {@link ColecaoTrilhara#filhasPrivadas}: o mapa de
 * populadores precisa cobrir <strong>exatamente</strong> as 18 coleções filhas, e o teste falha
 * se uma fixture for esquecida (risco apontado no relato da F9).</p>
 */
@QuarkusTest
@DisplayName("Exclusão de conta e isolamento ponta a ponta")
class AccountDeletionIntegrationTest {

    private static final String CONTA_ENDPOINT = "/api/auth/conta";
    private static final String SENHA = "Senh@Forte2026!";

    /** Populadores por coleção filha; validados contra o enum em {@link #popularColecoesFilhas}. */
    private static final Map<ColecaoTrilhara, BiConsumer<MongoDatabase, Document>> POPULADORES =
            FixturesConta.populadores();

    @Inject
    AccountDeletionService accountDeletionService;

    @Inject
    FaultInjectorExclusaoContaControlavel faultInjector;

    @Inject
    MongoClient mongoClient;

    @ConfigProperty(name = "quarkus.mongodb.database")
    String databaseName;

    @Inject
    PasswordService passwordService;

    @Inject
    TokenService tokenService;

    @Inject
    RateLimitService rateLimitService;

    @Inject
    SecurityFilter securityFilter;

    @BeforeEach
    void setup() {
        rateLimitService.limparTudo();
        securityFilter.setStrictMode(false);
        faultInjector.limpar();
        getDatabase().getCollection(ColecaoTrilhara.EDITAIS.nome()).deleteMany(Filters.empty());
        // Neutraliza checkpoints de exclusão deixados por testes anteriores: o worker varre
        // todas as contas com `exclusaoConta`, não só as criadas por este teste.
        getDatabase().getCollection(ColecaoTrilhara.USUARIOS.nome()).updateMany(
                Filters.exists("exclusaoConta", true),
                Updates.combine(
                        Updates.unset("exclusaoConta"),
                        Updates.set("ativo", true)));
    }

    @AfterEach
    void tearDown() {
        faultInjector.limpar();
        getDatabase().getCollection(ColecaoTrilhara.EDITAIS.nome()).deleteMany(Filters.empty());
    }

    // ── 1. Cobertura total das 18 filhas + isolamento ────────────────────────────────────

    @Test
    @DisplayName("exclui as 18 coleções filhas, isola outro usuário e preserva o catálogo de editais")
    void excluiTodasAsColecoesPrivadasEIsolaOutrosUsuariosEEdital() {
        ObjectId usuarioA = new ObjectId();
        ObjectId usuarioB = new ObjectId();
        String senhaHash = passwordService.gerarHash(SENHA);

        popularColecoesFilhas(usuarioA, senhaHash);
        popularColecoesFilhas(usuarioB, senhaHash);
        getDatabase().getCollection(ColecaoTrilhara.EDITAIS.nome()).insertOne(new Document()
                .append("_id", new ObjectId())
                .append("titulo", "Edital Concurso Nacional")
                .append("status", "PUBLICADA")
                .append("esfera", "FEDERAL")
                .append("atualizadoEm", new Date()));
        long editaisIniciais = getDatabase().getCollection(ColecaoTrilhara.EDITAIS.nome()).countDocuments();

        for (String colecao : ColecaoTrilhara.nomesFilhasPrivadas()) {
            assertTrue(contar(usuarioA, colecao) > 0, "Fixture ausente para usuarioA em " + colecao);
            assertTrue(contar(usuarioB, colecao) > 0, "Fixture ausente para usuarioB em " + colecao);
        }

        String tokenA = tokenService.gerarAccessToken(usuarioDe(usuarioA, senhaHash));

        Response resposta = given()
                .header("Authorization", "Bearer " + tokenA)
                .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                .contentType(ContentType.JSON)
                .body(corpoExclusao())
                .when()
                .delete(CONTA_ENDPOINT)
                .then()
                .statusCode(202)
                .body("operationId", notNullValue())
                .body("estado", equalTo(AccountDeletionService.ESTADO_PENDENTE))
                .extract().response();

        List<String> cookies = resposta.headers().getValues("Set-Cookie");
        assertTrue(cookies.stream().anyMatch(c -> c.contains("trilhara_rt=") && c.contains("Max-Age=0")),
                "O cookie trilhara_rt deveria ser expirado");
        assertTrue(cookies.stream().anyMatch(c -> c.contains("trilhara_session_state=") && c.contains("Max-Age=0")),
                "O cookie trilhara_session_state deveria ser expirado");

        assertEquals(1, accountDeletionService.processarExclusoesPendentes(),
                "O worker deveria concluir a exclusão de uma conta");

        for (String colecao : ColecaoTrilhara.nomesFilhasPrivadas()) {
            assertEquals(0, contar(usuarioA, colecao),
                    "A coleção privada " + colecao + " ainda possui dados de usuarioA");
            assertTrue(contar(usuarioB, colecao) > 0,
                    "Os dados de usuarioB foram afetados em " + colecao);
        }
        assertEquals(0, getDatabase().getCollection(ColecaoTrilhara.USUARIOS.nome())
                .countDocuments(Filters.eq("_id", usuarioA)));
        assertEquals(1, getDatabase().getCollection(ColecaoTrilhara.USUARIOS.nome())
                .countDocuments(Filters.eq("_id", usuarioB)));
        assertEquals(editaisIniciais,
                getDatabase().getCollection(ColecaoTrilhara.EDITAIS.nome()).countDocuments(),
                "O catálogo público de editais não pode ser alterado");
    }

    // ── 2. Falha injetada em cada coleção filha ─────────────────────────────────────────

    @ParameterizedTest(name = "falha em {0} retoma sem órfãos")
    @EnumSource(value = ColecaoTrilhara.class, names = {
            "REFRESH_TOKENS", "TOKENS_RECUPERACAO_SENHA", "TOKENS_CONFIRMACAO_EMAIL",
            "TOKENS_HANDOFF_SSO", "TRILHAS", "DISCIPLINAS", "PROGRESSO_TOPICOS", "CRONOGRAMAS",
            "BLOCOS_CRONOGRAMA", "SESSOES", "LISTAS_TAREFAS", "SERIES_RECORRENTES", "TAREFAS",
            "CADERNOS", "SECOES", "NOTAS", "PUSH_SUBSCRIPTIONS", "LEMBRETES_PUSH"
    })
    void falhaInjetadaEmCadaColecaoRetomaSemOrfaos(ColecaoTrilhara colecaoComFalha) {
        ObjectId usuario = new ObjectId();
        popularColecoesFilhas(usuario, passwordService.gerarHash(SENHA));

        faultInjector.falharNa(colecaoComFalha);
        assertFalse(accountDeletionService.executarLimpezaUsuario(usuario, "OP-FALHA"),
                "A limpeza deveria falhar em " + colecaoComFalha.nome());

        Document checkpoint = getDatabase().getCollection(ColecaoTrilhara.USUARIOS.nome())
                .find(Filters.eq("_id", usuario)).first();
        assertNotNull(checkpoint, "O documento raiz precisa permanecer para a retomada");
        Document exclusao = checkpoint.get("exclusaoConta", Document.class);
        assertNotNull(exclusao);
        assertEquals(colecaoComFalha.nome(), exclusao.getString("erroColecao"));
        assertNotNull(exclusao.getDate("proximaTentativa"));

        // Enquanto a falha persistir, a conta não é concluída.
        assertEquals(0, accountDeletionService.processarExclusoesPendentes());

        // Removida a falha, o worker retoma do checkpoint e não deixa órfãos.
        faultInjector.limpar();
        forcarRetentativaImediata(usuario);
        assertEquals(1, accountDeletionService.processarExclusoesPendentes());

        for (String colecao : ColecaoTrilhara.nomesFilhasPrivadas()) {
            assertEquals(0, contar(usuario, colecao), "Órfão em " + colecao);
        }
        assertNull(getDatabase().getCollection(ColecaoTrilhara.USUARIOS.nome())
                .find(Filters.eq("_id", usuario)).first(), "O documento raiz deveria ser removido");
    }

    // ── 3. Backoff e ausência de estado terminal silencioso ─────────────────────────────

    @Test
    @DisplayName("após 5 falhas entra em RETENTANDO_LENTO, não conclui e agenda retentativa horária")
    void apos5FalhasEntraEmRetentandoLentoENaoConclui() {
        ObjectId usuario = new ObjectId();
        popularColecoesFilhas(usuario, passwordService.gerarHash(SENHA));

        faultInjector.falharNa(ColecaoTrilhara.NOTAS);
        for (int tentativa = 1; tentativa <= 5; tentativa++) {
            accountDeletionService.executarLimpezaUsuario(usuario, "OP-LENTO");
        }

        Document checkpoint = getDatabase().getCollection(ColecaoTrilhara.USUARIOS.nome())
                .find(Filters.eq("_id", usuario)).first();
        assertNotNull(checkpoint, "Nenhum estado terminal silencioso: a conta precisa sobreviver");
        Document exclusao = checkpoint.get("exclusaoConta", Document.class);

        assertEquals(AccountDeletionService.ESTADO_RETENTANDO_LENTO, exclusao.getString("estado"));
        assertEquals(5, exclusao.getInteger("tentativas"));
        Date proxima = exclusao.getDate("proximaTentativa");
        assertNotNull(proxima);
        assertTrue(proxima.toInstant().isAfter(Instant.now().plus(Duration.ofMinutes(50))),
                "Em RETENTANDO_LENTO a próxima tentativa é horária, não imediata");

        assertEquals(0, accountDeletionService.processarExclusoesPendentes(),
                "O job não pode concluir antes do horário agendado");
    }

    @Test
    @DisplayName("retomada após RETENTANDO_LENTO conclui a exclusão sem órfãos")
    void retomadaAposRetentandoLentoConcluiSemOrfaos() {
        ObjectId usuario = new ObjectId();
        popularColecoesFilhas(usuario, passwordService.gerarHash(SENHA));

        faultInjector.falharNa(ColecaoTrilhara.SECOES);
        for (int tentativa = 1; tentativa <= 5; tentativa++) {
            accountDeletionService.executarLimpezaUsuario(usuario, "OP-RETOMA");
        }

        faultInjector.limpar();
        forcarRetentativaImediata(usuario);
        assertEquals(1, accountDeletionService.processarExclusoesPendentes());

        for (String colecao : ColecaoTrilhara.nomesFilhasPrivadas()) {
            assertEquals(0, contar(usuario, colecao), "Órfão em " + colecao);
        }
        assertNull(getDatabase().getCollection(ColecaoTrilhara.USUARIOS.nome())
                .find(Filters.eq("_id", usuario)).first());
    }

    // ── 4. Contrato HTTP e privacidade ──────────────────────────────────────────────────

    @Test
    @DisplayName("sem Idempotency-Key responde 400 IDEMPOTENCY_KEY_REQUIRED")
    void semIdempotencyKeyRetorna400() {
        ObjectId usuario = new ObjectId();
        String senhaHash = passwordService.gerarHash(SENHA);
        popularColecoesFilhas(usuario, senhaHash);
        String token = tokenService.gerarAccessToken(usuarioDe(usuario, senhaHash));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(corpoExclusao())
                .when()
                .delete(CONTA_ENDPOINT)
                .then()
                .statusCode(400)
                .body("code", equalTo("IDEMPOTENCY_KEY_REQUIRED"));

        assertEquals(1, getDatabase().getCollection(ColecaoTrilhara.USUARIOS.nome())
                .countDocuments(Filters.eq("_id", usuario)));
    }

    @Test
    @DisplayName("senha inválida responde 401 e não produz efeito")
    void senhaInvalidaRetorna401ESemEfeito() {
        ObjectId usuario = new ObjectId();
        String senhaHash = passwordService.gerarHash(SENHA);
        popularColecoesFilhas(usuario, senhaHash);
        String token = tokenService.gerarAccessToken(usuarioDe(usuario, senhaHash));

        given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                .contentType(ContentType.JSON)
                .body("""
                        {"senha": "SenhaErrada123!", "confirmacao": "EXCLUIR MINHA CONTA"}
                        """)
                .when()
                .delete(CONTA_ENDPOINT)
                .then()
                .statusCode(401);

        Document doc = getDatabase().getCollection(ColecaoTrilhara.USUARIOS.nome())
                .find(Filters.eq("_id", usuario)).first();
        assertNotNull(doc, "O usuário não pode ser removido");
        assertNull(doc.get("exclusaoConta"), "Senha inválida não pode registrar solicitação");
        assertEquals(Boolean.TRUE, doc.getBoolean("ativo"), "A conta não pode ser desativada");
        for (String colecao : ColecaoTrilhara.nomesFilhasPrivadas()) {
            assertTrue(contar(usuario, colecao) > 0, "Nenhum dado pode ser removido em " + colecao);
        }
    }

    @Test
    @DisplayName("confirmação em minúsculas responde 400 VALIDATION_ERROR com errors.confirmacao")
    void confirmacaoEmMinusculasRetorna400() {
        ObjectId usuario = new ObjectId();
        String senhaHash = passwordService.gerarHash(SENHA);
        popularColecoesFilhas(usuario, senhaHash);
        String token = tokenService.gerarAccessToken(usuarioDe(usuario, senhaHash));

        given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                .contentType(ContentType.JSON)
                .body("""
                        {"senha": "%s", "confirmacao": "excluir minha conta"}
                        """.formatted(SENHA))
                .when()
                .delete(CONTA_ENDPOINT)
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("errors.confirmacao", notNullValue());
    }

    @Test
    @DisplayName("o 202 usa application/json e não Problem Details")
    void respostaNaoEhProblemJson() {
        ObjectId usuario = new ObjectId();
        String senhaHash = passwordService.gerarHash(SENHA);
        popularColecoesFilhas(usuario, senhaHash);
        String token = tokenService.gerarAccessToken(usuarioDe(usuario, senhaHash));

        Response resposta = given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                .contentType(ContentType.JSON)
                .body(corpoExclusao())
                .when()
                .delete(CONTA_ENDPOINT)
                .then()
                .statusCode(202)
                .extract().response();

        assertTrue(resposta.contentType().toLowerCase().startsWith("application/json"),
                "O 202 deveria ser application/json, veio: " + resposta.contentType());
        assertFalse(resposta.contentType().contains("problem+json"));
        assertNotNull(resposta.path("operationId"));
        assertNotNull(resposta.path("estado"));
        assertNull(resposta.path("type"), "O corpo de sucesso não carrega campos de Problem Details");
        assertNull(resposta.path("title"));
        assertNull(resposta.path("code"));
    }

    @Test
    @DisplayName("retry com a mesma chave devolve 202 com o mesmo operationId")
    void retryComMesmaChaveRetorna202ComMesmoOperationId() {
        ObjectId usuario = new ObjectId();
        String senhaHash = passwordService.gerarHash(SENHA);
        popularColecoesFilhas(usuario, senhaHash);
        String token = tokenService.gerarAccessToken(usuarioDe(usuario, senhaHash));
        String chave = "idem-" + UUID.randomUUID();

        String opId1 = given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", chave)
                .contentType(ContentType.JSON)
                .body(corpoExclusao())
                .when().delete(CONTA_ENDPOINT)
                .then().statusCode(202).extract().path("operationId");

        String opId2 = given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", chave)
                .contentType(ContentType.JSON)
                .body(corpoExclusao())
                .when().delete(CONTA_ENDPOINT)
                .then().statusCode(202).extract().path("operationId");

        assertEquals(opId1, opId2, "A mesma chave deve devolver o mesmo operationId");
    }

    @Test
    @DisplayName("outra chave para a mesma conta já em exclusão responde 409")
    void outraChaveRetorna409() {
        ObjectId usuario = new ObjectId();
        String senhaHash = passwordService.gerarHash(SENHA);
        popularColecoesFilhas(usuario, senhaHash);
        String token = tokenService.gerarAccessToken(usuarioDe(usuario, senhaHash));

        given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                .contentType(ContentType.JSON)
                .body(corpoExclusao())
                .when().delete(CONTA_ENDPOINT)
                .then().statusCode(202);

        given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "outra-" + UUID.randomUUID())
                .contentType(ContentType.JSON)
                .body(corpoExclusao())
                .when().delete(CONTA_ENDPOINT)
                .then().statusCode(409)
                .body("code", equalTo("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    @DisplayName("após o 202, qualquer rota autenticada responde 401")
    void pos202QualquerRotaRetorna401() {
        ObjectId usuario = new ObjectId();
        String senhaHash = passwordService.gerarHash(SENHA);
        popularColecoesFilhas(usuario, senhaHash);
        String token = tokenService.gerarAccessToken(usuarioDe(usuario, senhaHash));

        given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                .contentType(ContentType.JSON)
                .body(corpoExclusao())
                .when().delete(CONTA_ENDPOINT)
                .then().statusCode(202);

        given().header("Authorization", "Bearer " + token)
                .when().get("/api/auth/me")
                .then().statusCode(401);
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/trilhas")
                .then().statusCode(401);
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(corpoExclusao())
                .when().delete(CONTA_ENDPOINT)
                .then().statusCode(401);
    }

    @Test
    @DisplayName("nenhum log da exclusão contém usuarioId, e-mail ou nome")
    void logsNaoContemUsuarioId() {
        ObjectId usuario = new ObjectId();
        String senhaHash = passwordService.gerarHash(SENHA);
        String email = "log_" + UUID.randomUUID() + "@exemplo.com";
        popularColecoesFilhas(usuario, senhaHash, email);
        String token = tokenService.gerarAccessToken(usuarioDe(usuario, senhaHash, email));

        try (CapturadorDeLogs logs = CapturadorDeLogs.iniciar()) {
            given()
                    .header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                    .contentType(ContentType.JSON)
                    .body(corpoExclusao())
                    .when().delete(CONTA_ENDPOINT)
                    .then().statusCode(202);

            accountDeletionService.processarExclusoesPendentes();

            String capturado = logs.textoCompleto();
            assertFalse(capturado.contains(usuario.toHexString()),
                    "O hex de usuarioId não pode aparecer em log");
            assertFalse(capturado.contains(email), "O e-mail não pode aparecer em log");
            assertFalse(capturado.contains("Usuário Teste"), "O nome não pode aparecer em log");
        }
    }

    private MongoDatabase getDatabase() {
        return mongoClient.getDatabase(databaseName);
    }

    /** Fixtures dirigidas pelo enum: falha se faltar populador para alguma filha privada. */
    private void popularColecoesFilhas(ObjectId usuarioId, String senhaHash) {
        popularColecoesFilhas(usuarioId, senhaHash,
                "usuario_" + usuarioId.toHexString() + "@exemplo.com");
    }

    private void popularColecoesFilhas(ObjectId usuarioId, String senhaHash, String email) {
        FixturesConta.popularColecoesFilhas(getDatabase(), usuarioId, senhaHash, email);
    }

    private static Usuario usuarioDe(ObjectId id, String senhaHash) {
        return usuarioDe(id, senhaHash, "usuario_" + id.toHexString() + "@exemplo.com");
    }

    private static Usuario usuarioDe(ObjectId id, String senhaHash, String email) {
        return new Usuario(
                id.toHexString(),
                "Usuário Teste",
                email,
                senhaHash,
                List.of(Role.USER),
                true,
                true,
                0,
                null,
                null,
                Instant.now(),
                Instant.now());
    }

    private static String corpoExclusao() {
        return """
                {"senha": "%s", "confirmacao": "EXCLUIR MINHA CONTA"}
                """.formatted(SENHA);
    }

    /** Zera o backoff para exercitar a retomada sem esperar o intervalo configurado. */
    private void forcarRetentativaImediata(ObjectId usuario) {
        getDatabase().getCollection(ColecaoTrilhara.USUARIOS.nome()).updateOne(
                Filters.eq("_id", usuario),
                Updates.set("exclusaoConta.proximaTentativa", new Date(0)));
    }

    private long contar(ObjectId usuarioId, String colecao) {
        return getDatabase().getCollection(colecao)
                .countDocuments(Filters.eq("usuarioId", usuarioId));
    }
}

