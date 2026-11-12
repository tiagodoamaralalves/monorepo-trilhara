package br.com.trilhara.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;

@QuarkusTest
class AccountDeletionIntegrationTest {

    private static final String CONTA_ENDPOINT = "/api/auth/conta";

    @Inject
    AccountDeletionService accountDeletionService;

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

    private ObjectId editalTesteId = null;

    @BeforeEach
    void setup() {
        rateLimitService.limparTudo();
        securityFilter.setStrictMode(false);
        accountDeletionService.setHookFalha(null);
        getDatabase().getCollection("editais").deleteMany(Filters.eq("status", "PUBLICADO"));
    }

    @AfterEach
    void tearDown() {
        accountDeletionService.setHookFalha(null);
        if (editalTesteId != null) {
            getDatabase().getCollection("editais").deleteOne(Filters.eq("_id", editalTesteId));
            editalTesteId = null;
        }
        getDatabase().getCollection("editais").deleteMany(Filters.eq("status", "PUBLICADO"));
    }

    private MongoDatabase getDatabase() {
        return mongoClient.getDatabase(databaseName);
    }

    @Test
    @DisplayName("Critérios 1, 2 e 3: Popula 18 coleções privadas para usuarioA e usuarioB, exclui conta de usuarioA e atesta isolamento e limpeza total")
    void deveExcluirContaEmTodasAs18ColecoesPrivadasSemAfetarOutrosUsuariosOuEditais() throws Exception {
        ObjectId usuarioAId = new ObjectId();
        ObjectId usuarioBId = new ObjectId();

        String emailA = "usuarioA_" + UUID.randomUUID() + "@exemplo.com";
        String emailB = "usuarioB_" + UUID.randomUUID() + "@exemplo.com";
        String senhaPura = "Senh@Forte2026!";
        String senhaHash = passwordService.gerarHash(senhaPura);

        // 1. Popula documentos para usuarioA e usuarioB nas 18 coleções privadas e catálogo de editais
        popular18ColecoesPrivadas(usuarioAId, emailA, senhaHash);
        popular18ColecoesPrivadas(usuarioBId, emailB, senhaHash);
        popularEdital();

        // Assegura pré-condição: contagem > 0 para ambos em todas as 17 filhas e 1 em usuarios
        for (String colecao : AccountDeletionService.COLECOES_FILHAS) {
            assertTrue(getDatabase().getCollection(colecao).countDocuments(Filters.eq("usuarioId", usuarioAId)) > 0,
                    "Pré-condição falhou: usuarioA deveria ter doc na coleção " + colecao);
            assertTrue(getDatabase().getCollection(colecao).countDocuments(Filters.eq("usuarioId", usuarioBId)) > 0,
                    "Pré-condição falhou: usuarioB deveria ter doc na coleção " + colecao);
        }
        assertEquals(1, getDatabase().getCollection("usuarios").countDocuments(Filters.eq("_id", usuarioAId)));
        assertEquals(1, getDatabase().getCollection("usuarios").countDocuments(Filters.eq("_id", usuarioBId)));
        long totalEditaisInicial = getDatabase().getCollection("editais").countDocuments();
        assertTrue(totalEditaisInicial > 0, "Pré-condição falhou: catálogo de editais deve possuir ao menos 1 item");

        // Gera token de autenticação JWT para usuarioA
        Usuario usuarioA = new Usuario(
                usuarioAId.toHexString(),
                "Usuario A",
                emailA,
                senhaHash,
                List.of(Role.USER),
                true,
                true,
                0,
                null,
                null,
                Instant.now(),
                Instant.now()
        );
        String tokenA = tokenService.gerarAccessToken(usuarioA);
        String idempotencyKey = "idemp-" + UUID.randomUUID();

        // 2. Executa DELETE /api/auth/conta como usuarioA
        Response deleteResponse = given()
                .header("Authorization", "Bearer " + tokenA)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "senha": "%s",
                          "confirmacao": "EXCLUIR MINHA CONTA"
                        }
                        """.formatted(senhaPura))
                .when()
                .delete(CONTA_ENDPOINT)
                .then()
                .statusCode(202)
                .body("operationId", notNullValue())
                .body("estado", equalTo("PENDENTE"))
                .extract().response();

        List<String> setCookies = deleteResponse.headers().getValues("Set-Cookie");
        assertTrue(setCookies.stream().anyMatch(c -> c.contains("trilhara_rt=") && c.contains("Max-Age=0")),
                "Deveria invalidar o cookie trilhara_rt");
        assertTrue(setCookies.stream().anyMatch(c -> c.contains("trilhara_session_state=") && c.contains("Max-Age=0")),
                "Deveria invalidar o cookie trilhara_session_state");

        String operationId = deleteResponse.path("operationId");
        assertNotNull(operationId);

        // Aguarda conclusão assíncrona do worker
        aguardarExclusaoConcluida(usuarioAId, Duration.ofSeconds(10));

        // 3. Verifica nominalmente nas 18 coleções privadas que countDocuments para usuarioA é 0
        for (String colecao : AccountDeletionService.COLECOES_FILHAS) {
            long countA = getDatabase().getCollection(colecao).countDocuments(Filters.eq("usuarioId", usuarioAId));
            assertEquals(0, countA, "Coleção privada " + colecao + " ainda possui dados de usuarioA!");
        }
        long countUserA = getDatabase().getCollection("usuarios").countDocuments(Filters.eq("_id", usuarioAId));
        assertEquals(0, countUserA, "Coleção usuarios ainda possui o documento raiz de usuarioA!");

        // Verifica que todos os documentos do usuarioB e o catálogo de editais permanecem intactos
        for (String colecao : AccountDeletionService.COLECOES_FILHAS) {
            long countB = getDatabase().getCollection(colecao).countDocuments(Filters.eq("usuarioId", usuarioBId));
            assertTrue(countB > 0, "Documentos do usuarioB foram indevidamente alterados na coleção " + colecao);
        }
        long countUserB = getDatabase().getCollection("usuarios").countDocuments(Filters.eq("_id", usuarioBId));
        assertEquals(1, countUserB, "Documento de usuarioB na coleção usuarios foi indevidamente removido");

        long totalEditaisFinal = getDatabase().getCollection("editais").countDocuments();
        assertEquals(totalEditaisInicial, totalEditaisFinal, "Catálogo de editais foi alterado!");
    }

    @Test
    @DisplayName("Critério 4: Injeta falha em cada uma das coleções e atesta que o worker retoma sem deixar órfãos e sem reautenticação")
    void deveRetomarExclusaoAposFalhasEmCadaColecaoSemDeixarOrfaos() {
        // Percorre nominalmente as 17 coleções filhas simulando falha em cada uma
        for (String colecaoComFalha : AccountDeletionService.COLECOES_FILHAS) {
            ObjectId usuarioId = new ObjectId();
            String email = "falha_" + colecaoComFalha + "_" + UUID.randomUUID() + "@exemplo.com";
            String senhaHash = passwordService.gerarHash("Senh@Forte2026!");

            popular18ColecoesPrivadas(usuarioId, email, senhaHash);

            // Injeta falha na coleção alvo
            accountDeletionService.setHookFalha((col, uId) -> {
                if (col.equals(colecaoComFalha) && uId.equals(usuarioId)) {
                    throw new RuntimeException("FALHA INJETADA DE TESTE NA COLECAO: " + col);
                }
            });

            String operationId = "OP-FAIL-" + UUID.randomUUID();

            // Dispara limpeza; deve capturar a falha e persistir o estado no documento usuarios
            boolean executouComSucesso = accountDeletionService.executarLimpezaUsuario(usuarioId, operationId);
            assertFalse(executouComSucesso, "Limpeza deveria ter falhado na coleção " + colecaoComFalha);

            // Documento usuarios deve persistir com estado FALHA e erroColecao registrado
            Document userDoc = getDatabase().getCollection("usuarios").find(Filters.eq("_id", usuarioId)).first();
            assertNotNull(userDoc, "Documento usuarios não deve ser removido após falha intermediária!");
            Document excDoc = userDoc.get("exclusaoConta", Document.class);
            assertNotNull(excDoc);
            assertEquals("FALHA", excDoc.getString("estado"));
            assertEquals(colecaoComFalha, excDoc.getString("erroColecao"));

            // Remove o hook de falha
            accountDeletionService.setHookFalha(null);

            // Retoma a exclusão sem credenciais ou nova autenticação
            int reconciliadas = accountDeletionService.processarExclusoesPendentes();
            assertTrue(reconciliadas >= 1, "Deveria ter reconciliado e concluído a exclusão da conta interrompida.");

            // Verifica nominalmente que todas as 18 coleções foram limpas para usuarioId
            for (String colecao : AccountDeletionService.COLECOES_FILHAS) {
                long count = getDatabase().getCollection(colecao).countDocuments(Filters.eq("usuarioId", usuarioId));
                assertEquals(0, count, "Coleção " + colecao + " possui dados órfãos após retomada!");
            }
            long countUsuario = getDatabase().getCollection("usuarios").countDocuments(Filters.eq("_id", usuarioId));
            assertEquals(0, countUsuario, "Documento raiz na coleção usuarios permaneceu órfão após retomada!");
        }
    }

    @Test
    @DisplayName("Validações de segurança: rejeita senha incorreta e confirmação textual inválida")
    void deveRejeitarSenhaIncorretaEConfirmacaoInvalida() {
        ObjectId usuarioId = new ObjectId();
        String email = "seguranca_" + UUID.randomUUID() + "@exemplo.com";
        String senhaCorreta = "Senh@Correta2026!";
        String senhaHash = passwordService.gerarHash(senhaCorreta);

        popular18ColecoesPrivadas(usuarioId, email, senhaHash);

        Usuario usuario = new Usuario(
                usuarioId.toHexString(),
                "Usuario Seguranca",
                email,
                senhaHash,
                List.of(Role.USER),
                true,
                true,
                0,
                null,
                null,
                Instant.now(),
                Instant.now()
        );
        String token = tokenService.gerarAccessToken(usuario);

        // 1. Senha incorreta -> 401
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "senha": "SenhaErrada!",
                          "confirmacao": "EXCLUIR MINHA CONTA"
                        }
                        """)
                .when()
                .delete(CONTA_ENDPOINT)
                .then()
                .statusCode(401);

        // 2. Confirmação textual inválida -> 400
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "senha": "%s",
                          "confirmacao": "sim, quero excluir"
                        }
                        """.formatted(senhaCorreta))
                .when()
                .delete(CONTA_ENDPOINT)
                .then()
                .statusCode(400);

        // Limpeza após teste de validação
        accountDeletionService.executarLimpezaUsuario(usuarioId, "CLEANUP");
    }

    @Test
    @DisplayName("Idempotência: requisições repetidas com a mesma chave retornam 202 com mesmo operationId")
    void deveGarantirIdempotenciaComMesmaChave() {
        ObjectId usuarioId = new ObjectId();
        String email = "idemp_" + UUID.randomUUID() + "@exemplo.com";
        String senha = "Senh@Forte2026!";
        String senhaHash = passwordService.gerarHash(senha);

        popular18ColecoesPrivadas(usuarioId, email, senhaHash);

        Usuario usuario = new Usuario(
                usuarioId.toHexString(),
                "Usuario Idempotencia",
                email,
                senhaHash,
                List.of(Role.USER),
                true,
                true,
                0,
                null,
                null,
                Instant.now(),
                Instant.now()
        );
        String token = tokenService.gerarAccessToken(usuario);
        String chaveIdempotencia = "idem-key-" + UUID.randomUUID();

        // Injeta falha temporária para impedir conclusão imediata no worker
        accountDeletionService.setHookFalha((col, uId) -> {
            if (uId.equals(usuarioId)) {
                throw new RuntimeException("Pausa para teste de idempotencia");
            }
        });

        Response resp1 = given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", chaveIdempotencia)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "senha": "%s",
                          "confirmacao": "EXCLUIR MINHA CONTA"
                        }
                        """.formatted(senha))
                .when()
                .delete(CONTA_ENDPOINT)
                .then()
                .statusCode(202)
                .extract().response();

        String opId1 = resp1.path("operationId");

        Response resp2 = given()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", chaveIdempotencia)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "senha": "%s",
                          "confirmacao": "EXCLUIR MINHA CONTA"
                        }
                        """.formatted(senha))
                .when()
                .delete(CONTA_ENDPOINT)
                .then()
                .statusCode(202)
                .extract().response();

        String opId2 = resp2.path("operationId");
        assertEquals(opId1, opId2, "Idempotency-Key deve retornar o mesmo operationId");

        // Limpeza final
        accountDeletionService.setHookFalha(null);
        accountDeletionService.executarLimpezaUsuario(usuarioId, opId1);
    }

    private void aguardarExclusaoConcluida(ObjectId usuarioId, Duration timeout) {
        long limite = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < limite) {
            long restante = getDatabase().getCollection("usuarios").countDocuments(Filters.eq("_id", usuarioId));
            if (restante == 0) {
                return;
            }
            java.util.concurrent.locks.LockSupport.parkNanos(100_000_000L);
        }
        throw new AssertionError("Tempo limite excedido aguardando conclusão da exclusão de conta para: " + usuarioId);
    }

    private void popular18ColecoesPrivadas(ObjectId usuarioId, String email, String senhaHash) {
        MongoDatabase db = getDatabase();
        Date agora = Date.from(Instant.now());

        // 1. refresh_tokens
        db.getCollection("refresh_tokens").insertOne(new Document()
                .append("tokenHash", UUID.randomUUID().toString())
                .append("usuarioId", usuarioId)
                .append("familiaId", UUID.randomUUID().toString())
                .append("criadoEm", agora)
                .append("expiraEm", Date.from(Instant.now().plusSeconds(3600))));

        // 2. tokens_recuperacao_senha
        db.getCollection("tokens_recuperacao_senha").insertOne(new Document()
                .append("tokenHash", UUID.randomUUID().toString())
                .append("usuarioId", usuarioId)
                .append("ip", "127.0.0.1")
                .append("criadoEm", agora)
                .append("expiraEm", Date.from(Instant.now().plusSeconds(3600))));

        // 3. tokens_confirmacao_email
        db.getCollection("tokens_confirmacao_email").insertOne(new Document()
                .append("tokenHash", UUID.randomUUID().toString())
                .append("usuarioId", usuarioId)
                .append("email", email)
                .append("criadoEm", agora)
                .append("expiraEm", Date.from(Instant.now().plusSeconds(3600))));

        // 4. tokens_handoff_sso (D-08)
        db.getCollection("tokens_handoff_sso").insertOne(new Document()
                .append("_id", new ObjectId())
                .append("usuarioId", usuarioId)
                .append("codigoHash", UUID.randomUUID().toString())
                .append("origemDestino", "http://localhost:5174/")
                .append("criadoEm", agora)
                .append("expiraEm", Date.from(Instant.now().plusSeconds(60)))
                .append("consumidoEm", null));

        // 5. lembretes_push
        db.getCollection("lembretes_push").insertOne(new Document()
                .append("usuarioId", usuarioId)
                .append("tarefaId", new ObjectId())
                .append("estado", "AGENDADO")
                .append("instanteEntregaUtc", agora));

        // 5. push_subscriptions
        db.getCollection("push_subscriptions").insertOne(new Document()
                .append("usuarioId", usuarioId)
                .append("endpoint", "https://push.example.com/" + UUID.randomUUID())
                .append("p256dh", "dummy-p256dh")
                .append("auth", "dummy-auth"));

        // 6. blocos_cronograma
        ObjectId trilhaId = new ObjectId();
        ObjectId cronogramaId = new ObjectId();
        db.getCollection("blocos_cronograma").insertOne(new Document()
                .append("usuarioId", usuarioId)
                .append("trilhaId", trilhaId)
                .append("cronogramaId", cronogramaId)
                .append("versaoGeracao", 1)
                .append("data", "2026-11-10")
                .append("ordem", 0)
                .append("concluido", false));

        // 7. cronogramas
        db.getCollection("cronogramas").insertOne(new Document()
                .append("_id", cronogramaId)
                .append("usuarioId", usuarioId)
                .append("trilhaId", trilhaId)
                .append("modalidade", "CICLO")
                .append("ativo", true));

        // 8. progresso_topicos
        db.getCollection("progresso_topicos").insertOne(new Document()
                .append("usuarioId", usuarioId)
                .append("trilhaId", trilhaId)
                .append("topicoId", "topico-1")
                .append("concluido", true));

        // 9. disciplinas
        ObjectId disciplinaId = new ObjectId();
        db.getCollection("disciplinas").insertOne(new Document()
                .append("_id", disciplinaId)
                .append("usuarioId", usuarioId)
                .append("trilhaId", trilhaId)
                .append("nome", "Direito Constitucional")
                .append("ordem", 0));

        // 10. sessoes
        db.getCollection("sessoes").insertOne(new Document()
                .append("usuarioId", usuarioId)
                .append("trilhaId", trilhaId)
                .append("disciplinaId", disciplinaId)
                .append("duracaoSegundos", 1500)
                .append("dataSessao", "2026-11-10")
                .append("criadoEm", agora));

        // 11. trilhas
        db.getCollection("trilhas").insertOne(new Document()
                .append("_id", trilhaId)
                .append("usuarioId", usuarioId)
                .append("nome", "Trilha Auditor Fiscal")
                .append("atualizadoEm", agora));

        // 12. tarefas
        ObjectId listaId = new ObjectId();
        ObjectId serieId = new ObjectId();
        db.getCollection("tarefas").insertOne(new Document()
                .append("usuarioId", usuarioId)
                .append("listaId", listaId)
                .append("serieId", serieId)
                .append("titulo", "Estudar Direito Tributário")
                .append("concluido", false)
                .append("ordem", 0));

        // 13. series_recorrentes
        db.getCollection("series_recorrentes").insertOne(new Document()
                .append("_id", serieId)
                .append("usuarioId", usuarioId)
                .append("titulo", "Série Semanal de Revisão")
                .append("ativa", true));

        // 14. listas_tarefas
        db.getCollection("listas_tarefas").insertOne(new Document()
                .append("_id", listaId)
                .append("usuarioId", usuarioId)
                .append("nome", "Tarefas da Semana")
                .append("ordem", 0));

        // 15. notas
        ObjectId cadernoId = new ObjectId();
        ObjectId secaoId = new ObjectId();
        db.getCollection("notas").insertOne(new Document()
                .append("usuarioId", usuarioId)
                .append("cadernoId", cadernoId)
                .append("secaoId", secaoId)
                .append("titulo", "Resumo de Tributos")
                .append("conteudoMarkdown", "# Resumo"));

        // 16. secoes
        db.getCollection("secoes").insertOne(new Document()
                .append("_id", secaoId)
                .append("usuarioId", usuarioId)
                .append("cadernoId", cadernoId)
                .append("nome", "Direito Tributário")
                .append("ordem", 0));

        // 17. cadernos
        db.getCollection("cadernos").insertOne(new Document()
                .append("_id", cadernoId)
                .append("usuarioId", usuarioId)
                .append("nome", "Meu Caderno de Estudos")
                .append("atualizadoEm", agora));

        // 18. usuarios
        db.getCollection("usuarios").insertOne(new Document()
                .append("_id", usuarioId)
                .append("nome", "Usuário Teste")
                .append("email", email)
                .append("senhaHash", senhaHash)
                .append("roles", List.of("USER"))
                .append("ativo", true)
                .append("emailVerificado", true)
                .append("criadoEm", agora)
                .append("atualizadoEm", agora));
    }

    private void popularEdital() {
        MongoDatabase db = getDatabase();
        if (editalTesteId == null) {
            editalTesteId = new ObjectId();
            db.getCollection("editais").insertOne(new Document()
                    .append("_id", editalTesteId)
                    .append("titulo", "Edital Concurso Nacional")
                    .append("status", "PUBLICADA")
                    .append("esfera", "FEDERAL")
                    .append("atualizadoEm", new Date()));
        }
    }
}
