package br.com.trilhara.security;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;

import br.com.trilhara.auth.PasswordService;
import br.com.trilhara.auth.Role;
import br.com.trilhara.auth.SecurityFilter;
import br.com.trilhara.auth.TokenService;
import br.com.trilhara.auth.UserRepository;
import br.com.trilhara.auth.Usuario;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

@QuarkusTest
@DisplayName("Auditoria de Autorização por Proprietário (Anti-IDOR / 404 sem vazar existência)")
public class AuthorizationByOwnerTest {

    @Inject
    MongoClient mongoClient;

    @ConfigProperty(name = "quarkus.mongodb.database")
    String databaseName;

    @Inject
    UserRepository userRepository;

    @Inject
    TokenService tokenService;

    @Inject
    PasswordService passwordService;

    @Inject
    SecurityFilter securityFilter;

    private ObjectId usuarioAId;
    private ObjectId usuarioBId;
    private String tokenB;

    // IDs dos recursos criados pertencentes exclusivamente a Usuario A
    private ObjectId trilhaAId;
    private ObjectId disciplinaAId;
    private String topicoAId = new ObjectId().toHexString();
    private ObjectId cronogramaAId;
    private ObjectId blocoAId;
    private ObjectId sessaoAId;
    private ObjectId listaTarefasAId;
    private ObjectId serieRecorrenteAId;
    private ObjectId tarefaAId;
    private ObjectId cadernoAId;
    private ObjectId secaoAId;
    private ObjectId notaAId;
    private String pushEndpointA;

    @BeforeEach
    void setup() {
        securityFilter.setStrictMode(false);

        usuarioAId = new ObjectId();
        usuarioBId = new ObjectId();

        Usuario usuarioA = criarUsuario(usuarioAId, "usuarioA_" + UUID.randomUUID() + "@auditoria.com");
        Usuario usuarioB = criarUsuario(usuarioBId, "usuarioB_" + UUID.randomUUID() + "@auditoria.com");

        tokenService.gerarAccessToken(usuarioA);
        tokenB = tokenService.gerarAccessToken(usuarioB);

        popularRecursosUsuarioA();
        disciplinaCorpoId = disciplinaAId.toHexString();
    }

    private Usuario criarUsuario(ObjectId id, String email) {
        Date agora = Date.from(Instant.now());
        Usuario u = new Usuario(
                id.toHexString(),
                "Usuario " + id.toHexString(),
                email,
                passwordService.gerarHash("Senha@123456"),
                List.of(Role.USER),
                true,
                true,
                0,
                null,
                null,
                agora.toInstant(),
                agora.toInstant()
        );
        userRepository.inserir(u);
        return u;
    }

    private MongoDatabase getDatabase() {
        return mongoClient.getDatabase(databaseName);
    }

    private void popularRecursosUsuarioA() {
        MongoDatabase db = getDatabase();
        Date agora = Date.from(Instant.now());

        // 1. Trilha
        trilhaAId = new ObjectId();
        db.getCollection("trilhas").insertOne(new Document()
                .append("_id", trilhaAId)
                .append("usuarioId", usuarioAId)
                .append("nome", "Trilha Auditoria A")
                .append("cargo", "Auditor")
                .append("banca", "FGV")
                .append("atualizadoEm", agora));

        // 2. Disciplina
        disciplinaAId = new ObjectId();
        db.getCollection("disciplinas").insertOne(new Document()
                .append("_id", disciplinaAId)
                .append("usuarioId", usuarioAId)
                .append("trilhaId", trilhaAId)
                .append("nome", "Direito Constitucional")
                .append("ordem", 0)
                .append("topicos", List.of(new Document("id", topicoAId).append("nome", "Tópico 1"))));

        // 3. Progresso Tópico
        db.getCollection("progresso_topicos").insertOne(new Document()
                .append("usuarioId", usuarioAId)
                .append("trilhaId", trilhaAId)
                .append("topicoId", topicoAId)
                .append("concluido", true));

        // 4. Cronograma
        cronogramaAId = new ObjectId();
        db.getCollection("cronogramas").insertOne(new Document()
                .append("_id", cronogramaAId)
                .append("usuarioId", usuarioAId)
                .append("trilhaId", trilhaAId)
                .append("modalidade", "SEQUENCIAL")
                .append("ativo", true)
                .append("versaoAtiva", 1));

        // 5. Bloco Cronograma
        blocoAId = new ObjectId();
        db.getCollection("blocos_cronograma").insertOne(new Document()
                .append("_id", blocoAId)
                .append("usuarioId", usuarioAId)
                .append("trilhaId", trilhaAId)
                .append("cronogramaId", cronogramaAId)
                .append("versaoGeracao", 1)
                .append("data", "2026-11-10")
                .append("ordem", 0)
                .append("concluido", false));

        // 6. Sessão
        sessaoAId = new ObjectId();
        db.getCollection("sessoes").insertOne(new Document()
                .append("_id", sessaoAId)
                .append("usuarioId", usuarioAId)
                .append("trilhaId", trilhaAId)
                .append("disciplinaId", disciplinaAId)
                .append("duracaoSegundos", 1800)
                .append("dataSessao", "2026-11-10")
                .append("criadoEm", agora));

        // 7. Lista Tarefas
        listaTarefasAId = new ObjectId();
        db.getCollection("listas_tarefas").insertOne(new Document()
                .append("_id", listaTarefasAId)
                .append("usuarioId", usuarioAId)
                .append("nome", "Lista Pessoal A")
                .append("ordem", 0));

        // 8. Série Recorrente
        serieRecorrenteAId = new ObjectId();
        db.getCollection("series_recorrentes").insertOne(new Document()
                .append("_id", serieRecorrenteAId)
                .append("usuarioId", usuarioAId)
                .append("titulo", "Revisão Periódica A")
                .append("frequencia", "SEMANAL")
                .append("intervalo", 1)
                .append("ativa", true));

        // 9. Tarefa
        tarefaAId = new ObjectId();
        db.getCollection("tarefas").insertOne(new Document()
                .append("_id", tarefaAId)
                .append("usuarioId", usuarioAId)
                .append("listaId", listaTarefasAId)
                .append("titulo", "Tarefa Confidencial A")
                .append("concluido", false)
                .append("ordem", 0));

        // 10. Caderno
        cadernoAId = new ObjectId();
        db.getCollection("cadernos").insertOne(new Document()
                .append("_id", cadernoAId)
                .append("usuarioId", usuarioAId)
                .append("nome", "Caderno Privado A")
                .append("atualizadoEm", agora));

        // 11. Seção
        secaoAId = new ObjectId();
        db.getCollection("secoes").insertOne(new Document()
                .append("_id", secaoAId)
                .append("usuarioId", usuarioAId)
                .append("cadernoId", cadernoAId)
                .append("nome", "Seção Privada A")
                .append("ordem", 0));

        // 12. Nota
        notaAId = new ObjectId();
        db.getCollection("notas").insertOne(new Document()
                .append("_id", notaAId)
                .append("usuarioId", usuarioAId)
                .append("cadernoId", cadernoAId)
                .append("secaoId", secaoAId)
                .append("titulo", "Nota Privada A")
                .append("conteudoMarkdown", "# Segredo"));

        // 13. Push Subscription
        pushEndpointA = "https://fcm.googleapis.com/fcm/send/token-a-" + UUID.randomUUID();
        db.getCollection("push_subscriptions").insertOne(new Document()
                .append("usuarioId", usuarioAId)
                .append("endpoint", pushEndpointA)
                .append("p256dh", "keyA")
                .append("auth", "authA"));
    }

    @Test
    @DisplayName("1. Auditoria de Trilhas: Usuario B não lê, não edita e não exclui trilha de Usuario A (404)")
    void auditarTrilhas() {
        // Leitura
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .get("/api/trilhas/" + trilhaAId)
                .then()
                .statusCode(404);

        // Escrita / Atualização
        given()
                .header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON)
                .body(Map.of("nome", "Tentativa Invasao", "cargo", "Hacker", "banca", "FGV"))
                .when()
                .put("/api/trilhas/" + trilhaAId)
                .then()
                .statusCode(404);

        // Exclusão
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .delete("/api/trilhas/" + trilhaAId)
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("2. Auditoria de Disciplinas: Usuario B não insere, não edita e não exclui disciplina de Usuario A (404)")
    void auditarDisciplinas() {
        // Criação dentro da trilha de A
        given()
                .header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON)
                .body(Map.of("nome", "Disciplina Invasora", "cor", "#FF0000", "peso", 2))
                .when()
                .post("/api/trilhas/" + trilhaAId + "/disciplinas")
                .then()
                .statusCode(404);

        // Edição
        given()
                .header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON)
                .body(Map.of("nome", "Disciplina Alterada", "cor", "#00FF00", "peso", 3))
                .when()
                .put("/api/trilhas/" + trilhaAId + "/disciplinas/" + disciplinaAId)
                .then()
                .statusCode(404);

        // Exclusão
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .delete("/api/trilhas/" + trilhaAId + "/disciplinas/" + disciplinaAId)
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("3. Auditoria de Progresso de Tópicos: Usuario B não marca nem desmarca tópicos de Usuario A (404)")
    void auditarProgressoTopicos() {
        // Marcar progresso
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .put("/api/trilhas/" + trilhaAId + "/topicos/" + topicoAId + "/progresso")
                .then()
                .statusCode(404);

        // Desmarcar progresso
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .delete("/api/trilhas/" + trilhaAId + "/topicos/" + topicoAId + "/progresso")
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("4. Auditoria de Cronogramas: Usuario B não lê, não cria, não ativa e não exclui cronogramas de Usuario A (404)")
    void auditarCronogramas() {
        // Listagem de cronogramas da trilha de A
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .get("/api/trilhas/" + trilhaAId + "/cronogramas")
                .then()
                .statusCode(404);

        // Detalhe de cronograma
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .get("/api/trilhas/" + trilhaAId + "/cronogramas/" + cronogramaAId)
                .then()
                .statusCode(404);

        // Ativação
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .put("/api/trilhas/" + trilhaAId + "/cronogramas/" + cronogramaAId + "/ativar")
                .then()
                .statusCode(404);

        // Criação de cronograma na trilha de A
        given()
                .header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "modalidade", "SEQUENCIAL",
                        "duracaoBlocoMinutos", 60,
                        "tipoPeriodo", "DIAS_CORRIDOS",
                        "dataInicio", "2026-11-10",
                        "dataFim", "2026-12-10",
                        "disciplinasParticipantesIds", List.of(disciplinaAId.toHexString())
                ))
                .when()
                .post("/api/trilhas/" + trilhaAId + "/cronogramas")
                .then()
                .statusCode(404);

        // Exclusão
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .delete("/api/trilhas/" + trilhaAId + "/cronogramas/" + cronogramaAId)
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("5. Auditoria de Blocos de Cronograma: Usuario B não lista blocos de A e não altera conclusão de bloco de A (404)")
    void auditarBlocosCronograma() {
        // Listagem paginada de blocos
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .get("/api/trilhas/" + trilhaAId + "/cronogramas/" + cronogramaAId + "/blocos")
                .then()
                .statusCode(404);

        // Conclusão de bloco individual
        given()
                .header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON)
                .body(Map.of("concluido", true))
                .when()
                .patch("/api/blocos/" + blocoAId + "/conclusao")
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("6. Auditoria de Sessões de Estudo: Usuario B não lê, não registra, não altera e não exclui sessões de A (404)")
    void auditarSessoes() {
        // Listagem de sessões na trilha de A
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .get("/api/trilhas/" + trilhaAId + "/sessoes")
                .then()
                .statusCode(404);

        // Registro de sessão na trilha de A
        given()
                .header("Authorization", "Bearer " + tokenB)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "disciplinaId", disciplinaAId.toHexString(),
                        "duracaoSegundos", 1800,
                        "dataSessao", "2026-11-10",
                        "questoes", 10,
                        "acertos", 8
                ))
                .when()
                .post("/api/trilhas/" + trilhaAId + "/sessoes")
                .then()
                .statusCode(404);

        // Atualização de sessão de A
        given()
                .header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON)
                .body(Map.of("duracaoSegundos", 2400))
                .when()
                .patch("/api/trilhas/" + trilhaAId + "/sessoes/" + sessaoAId)
                .then()
                .statusCode(404);

        // Exclusão individual de sessão de A
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .delete("/api/trilhas/" + trilhaAId + "/sessoes/" + sessaoAId)
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("7. Auditoria de Listas de Tarefas: Usuario B não busca, não atualiza, não exclui e não restaura lista de A (404)")
    void auditarListasTarefas() {
        // Leitura
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .get("/api/listas-tarefas/" + listaTarefasAId)
                .then()
                .statusCode(404);

        // Atualização
        given()
                .header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON)
                .body(Map.of("nome", "Lista Invasao", "cor", "#10B981"))
                .when()
                .put("/api/listas-tarefas/" + listaTarefasAId)
                .then()
                .statusCode(404);

        // Exclusão
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .delete("/api/listas-tarefas/" + listaTarefasAId)
                .then()
                .statusCode(404);

        // Restauração
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .post("/api/listas-tarefas/" + listaTarefasAId + "/restaurar")
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("8. Auditoria de Séries Recorrentes: Usuario B não consulta, não edita e não deleta série de A (404)")
    void auditarSeriesRecorrentes() {
        // Leitura
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .get("/api/series-recorrentes/" + serieRecorrenteAId)
                .then()
                .statusCode(404);

        // Atualização
        given()
                .header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON)
                .body(Map.of("titulo", "Serie Invasora", "frequencia", "MENSAL", "intervalo", 1))
                .when()
                .put("/api/series-recorrentes/" + serieRecorrenteAId)
                .then()
                .statusCode(404);

        // Exclusão
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .delete("/api/series-recorrentes/" + serieRecorrenteAId)
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("9. Auditoria de Tarefas: Usuario B não consulta, não altera, não exclui, não conclui, não pula e não restaura tarefa de A (404)")
    void auditarTarefas() {
        // Leitura
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .get("/api/tarefas/" + tarefaAId)
                .then()
                .statusCode(404);

        // Atualização
        given()
                .header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON)
                .body(Map.of("titulo", "Tarefa Modificada"))
                .when()
                .put("/api/tarefas/" + tarefaAId)
                .then()
                .statusCode(404);

        // Conclusão via PATCH
        given()
                .header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON)
                .body(Map.of("concluida", true))
                .when()
                .patch("/api/tarefas/" + tarefaAId + "/conclusao")
                .then()
                .statusCode(404);

        // Pular
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .post("/api/tarefas/" + tarefaAId + "/pular")
                .then()
                .statusCode(404);

        // Exclusão
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .delete("/api/tarefas/" + tarefaAId)
                .then()
                .statusCode(404);

        // Restauração da lixeira
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .post("/api/tarefas/lixeira/" + tarefaAId + "/restaurar")
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("10. Auditoria de Cadernos: Usuario B não lê, não atualiza e não remove caderno de A (404)")
    void auditarCadernos() {
        // Leitura
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .get("/api/cadernos/" + cadernoAId)
                .then()
                .statusCode(404);

        // Atualização
        given()
                .header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON)
                .body(Map.of("titulo", "Caderno Hackeado", "cor", "#EF4444"))
                .when()
                .put("/api/cadernos/" + cadernoAId)
                .then()
                .statusCode(404);

        // Exclusão
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .delete("/api/cadernos/" + cadernoAId)
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("11. Auditoria de Seções: Usuario B não insere seção no caderno de A, não busca, não altera e não remove seção de A (404)")
    void auditarSecoes() {
        // Criação de seção no caderno de A
        given()
                .header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON)
                .body(Map.of("titulo", "Seção Invasora", "cor", "#6366F1"))
                .when()
                .post("/api/cadernos/" + cadernoAId + "/secoes")
                .then()
                .statusCode(404);

        // Leitura
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .get("/api/secoes/" + secaoAId)
                .then()
                .statusCode(404);

        // Atualização
        given()
                .header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON)
                .body(Map.of("titulo", "Seção Alterada", "cor", "#8B5CF6"))
                .when()
                .put("/api/secoes/" + secaoAId)
                .then()
                .statusCode(404);

        // Exclusão
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .delete("/api/secoes/" + secaoAId)
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("12. Auditoria de Notas: Usuario B não consulta, não altera, não exclui e não restaura nota de A (404)")
    void auditarNotas() {
        // Leitura
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .get("/api/notas/" + notaAId)
                .then()
                .statusCode(404);

        // Atualização
        given()
                .header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON)
                .body(Map.of("titulo", "Nota Hackeada", "conteudoMarkdown", "invadido"))
                .when()
                .put("/api/notas/" + notaAId)
                .then()
                .statusCode(404);

        // Exclusão
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .delete("/api/notas/" + notaAId)
                .then()
                .statusCode(404);

        // Restauração da lixeira
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .post("/api/notas/lixeira/nota/" + notaAId + "/restaurar")
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("13. Auditoria de Web Push: Usuario B não consegue excluir inscrição push pertencente a Usuario A (404)")
    void auditarPush() {
        // Usuario B tenta remover o endpoint de A. O contrato de
        // DELETE /api/push/subscriptions recebe o endpoint no CORPO
        // (RemoverInscricaoPushRequest); não há grafia por query string.
        given()
                .header("Authorization", "Bearer " + tokenB)
                .contentType("application/json")
                .body(Map.of("endpoint", pushEndpointA))
                .when()
                .delete("/api/push/subscriptions")
                .then()
                .statusCode(404);

        // Confirma que a inscrição de A permanece intacta na coleção push_subscriptions
        long countA = getDatabase().getCollection("push_subscriptions")
                .countDocuments(new Document("usuarioId", usuarioAId).append("endpoint", pushEndpointA));
        assertEquals(1, countA, "Inscrição push de Usuario A não pode ter sido removida!");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Casos dirigidos pela allowlist
    // ─────────────────────────────────────────────────────────────────────

    /** Substitui os marcadores da allowlist pelos IDs reais do usuário A. */
    private String resolver(String caminho) {
        return caminho
                .replace("{tipo}", "nota")
                .replace("{trilhaA}", trilhaAId.toHexString())
                .replace("{disciplinaA}", disciplinaAId.toHexString())
                .replace("{topicoA}", topicoAId)
                .replace("{cronogramaA}", cronogramaAId.toHexString())
                .replace("{blocoA}", blocoAId.toHexString())
                .replace("{sessaoA}", sessaoAId.toHexString())
                .replace("{listaA}", listaTarefasAId.toHexString())
                .replace("{serieA}", serieRecorrenteAId.toHexString())
                .replace("{tarefaA}", tarefaAId.toHexString())
                .replace("{subtarefaA}", tarefaAId.toHexString())
                .replace("{cadernoA}", cadernoAId.toHexString())
                .replace("{secaoA}", secaoAId.toHexString())
                .replace("{notaA}", notaAId.toHexString());
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> casosAllowlist() {
        return RotasPrivadas.casos();
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "[{index}] {4}")
    @org.junit.jupiter.params.provider.MethodSource("casosAllowlist")
    @DisplayName("Toda rota privada da allowlist: B usando IDs de A recebe 404 (nunca 200/204/403/500)")
    void auditarRotaDaAllowlist(String metodo, String caminho, boolean comCorpo,
                                int statusEsperado, String descricao) {
        io.restassured.specification.RequestSpecification requisicao = given()
                .header("Authorization", "Bearer " + tokenB);

        if (comCorpo) {
            // Corpo estruturalmente válido: sem ele a validação de entrada responderia 400
            // antes de o filtro de tenant ser exercitado, e o teste não provaria o 404.
            requisicao = requisicao.contentType(ContentType.JSON).body(corpoPara(caminho));
        }

        // Registrar sessão exige Idempotency-Key no cabeçalho, validado ANTES do tenant.
        if (caminho.contains("/sessoes") && !caminho.contains("/sessoes/")) {
            requisicao = requisicao.header("Idempotency-Key", UUID.randomUUID().toString());
        }

        int status = requisicao
                .when()
                .request(metodo, resolver(caminho))
                .then()
                .extract()
                .statusCode();

        assertEquals(statusEsperado, status,
                descricao + " — " + metodo + " " + resolver(caminho)
                        + " deveria responder " + statusEsperado + " para o usuário B");
    }

    /** Corpo mínimo válido por família de rota (evita 400 de validação antes do tenant). */
    private String corpoPara(String caminho) {
        if (caminho.contains("/blocos/concluir-atrasados")) {
            // ConcluirAtrasadosRequest exige operationId (UUID) — chave de idempotência.
            return "{\"operationId\":\"" + java.util.UUID.randomUUID() + "\"}";
        }
        if (caminho.contains("/sessoes")) {
            // RegistrarSessaoRequest: disciplinaId, dataSessao e duracaoSegundos (>= 60).
            // A data precisa ser passada: o serviço rejeita sessão futura com 400.
            return "{\"disciplinaId\":\"" + disciplinaCorpoId + "\",\"duracaoSegundos\":1800,"
                    + "\"dataSessao\":\"2024-01-10\",\"questoes\":10,\"acertos\":8}";
        }
        if (caminho.contains("/cronogramas")) {
            // Criar/AtualizarCronogramaRequest: modalidade, duracaoBlocoMinutos (30..240),
            // dataInicio e disciplinasParticipantesIds (@NotEmpty).
            return "{\"modalidade\":\"SEQUENCIAL\",\"duracaoBlocoMinutos\":50,"
                    + "\"tipoPeriodo\":\"DIARIO\",\"dataInicio\":\"2026-11-10\","
                    + "\"dataFim\":\"2026-12-10\",\"diasAtivosSemana\":[1,2,3,4,5],"
                    + "\"blocosPorDia\":2,"
                    + "\"disciplinasParticipantesIds\":[\"" + disciplinaCorpoId + "\"]}";
        }
        if (caminho.contains("/conclusao")) {
            // AtualizarConclusaoBlocoRequest: campo `concluido` (bloco), `concluida` (tarefa).
            return caminho.contains("/blocos/")
                    ? "{\"concluido\":true}"
                    : "{\"concluida\":true}";
        }
        if (caminho.contains("/disciplinas/reordenar")) {
            return "{\"disciplinaIds\":[]}";
        }
        if (caminho.contains("/disciplinas")) {
            return "{\"nome\":\"Disciplina Invasora\",\"cor\":\"#FF0000\",\"peso\":2}";
        }
        if (caminho.contains("/topicos/")) {
            return "{\"concluido\":true}";
        }
        if (caminho.contains("/secoes/") && caminho.endsWith("/notas")) {
            return "{\"titulo\":\"Nota Invasora\",\"conteudoMarkdown\":\"x\"}";
        }
        if (caminho.contains("/secoes")) {
            return "{\"titulo\":\"Seção Invasora\",\"cor\":\"#6366F1\"}";
        }
        if (caminho.contains("/cadernos")) {
            return "{\"titulo\":\"Caderno Invasor\",\"cor\":\"#EF4444\"}";
        }
        if (caminho.contains("/listas-tarefas")) {
            return "{\"nome\":\"Lista Invasora\",\"cor\":\"#10B981\"}";
        }
        if (caminho.contains("/series-recorrentes")) {
            return "{\"titulo\":\"Série Invasora\",\"frequencia\":\"MENSAL\",\"intervalo\":1}";
        }
        if (caminho.contains("/subtarefas")) {
            return "{\"concluida\":true}";
        }
        if (caminho.contains("/tarefas")) {
            return "{\"titulo\":\"Tarefa Invasora\"}";
        }
        if (caminho.contains("/notas")) {
            return "{\"titulo\":\"Nota Invasora\",\"conteudoMarkdown\":\"x\"}";
        }
        return "{\"nome\":\"Invasao\",\"cargo\":\"X\",\"banca\":\"FGV\"}";
    }

    /** Preenchido no setup: disciplina pertencente à trilha do usuário A. */
    private String disciplinaCorpoId;

    @org.junit.jupiter.params.ParameterizedTest(name = "[{index}] {0}")
    @org.junit.jupiter.params.provider.MethodSource("listagensSemId")
    @DisplayName("Listagens sem ID: B não recebe nenhum item de A")
    void auditarListagemSemId(String rota, String marcadorTituloDeA) {
        String corpo = given()
                .header("Authorization", "Bearer " + tokenB)
                .when()
                .get(rota)
                .then()
                .statusCode(200)
                .extract()
                .asString();

        org.junit.jupiter.api.Assertions.assertFalse(
                corpo.contains(marcadorTituloDeA),
                "Usuário B recebeu dado de A em " + rota + ": " + marcadorTituloDeA);
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> listagensSemId() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("/api/trilhas", "Trilha Auditoria A"),
                org.junit.jupiter.params.provider.Arguments.of("/api/tarefas", "Tarefa Confidencial A"),
                org.junit.jupiter.params.provider.Arguments.of("/api/cadernos", "Caderno Privado A"),
                org.junit.jupiter.params.provider.Arguments.of("/api/listas-tarefas", "Lista Pessoal A")
        );
    }
}

