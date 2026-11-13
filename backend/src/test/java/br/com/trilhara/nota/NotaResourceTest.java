package br.com.trilhara.nota;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;

import br.com.trilhara.auth.Role;
import br.com.trilhara.auth.TokenService;
import br.com.trilhara.auth.UserRepository;
import br.com.trilhara.auth.Usuario;
import br.com.trilhara.trilha.Disciplina;
import br.com.trilhara.trilha.DisciplinaRepository;
import br.com.trilhara.trilha.TopicoEmbutido;
import br.com.trilhara.trilha.TrilhaRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Integração do domínio Notas (R9: D-29 a D-34).
 *
 * <p>Cobre os contratos congelados na revisão: guarda de reimportação, sincronização sem tocar
 * páginas existentes, busca com snippet texto puro + destaques, endpoint canônico de página,
 * impacto de exclusão, lixeira paginada, "Restauradas" com sufixo, limites de acervo e validação
 * de Markdown.</p>
 */
@QuarkusTest
class NotaResourceTest {

    private static final String CADERNOS = "/api/cadernos";
    private static final String CADERNO = "/api/cadernos/{id}";
    private static final String NOTAS = "/api/notas";
    private static final String NOTA = "/api/notas/{id}";
    private static final String SECAO = "/api/secoes/{id}";

    @Inject
    TokenService tokenService;

    @Inject
    UserRepository userRepository;

    @Inject
    CadernoRepository cadernoRepository;

    @Inject
    SecaoRepository secaoRepository;

    @Inject
    NotaRepository notaRepository;

    @Inject
    TrilhaRepository trilhaRepository;

    @Inject
    DisciplinaRepository disciplinaRepository;

    @Inject
    PurgaLixeiraNotasJob purgaLixeiraNotasJob;

    @Inject
    MongoClient mongoClient;

    @Inject
    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "trilhara")
    String databaseName;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    private String tokenUsuarioA;
    private String tokenUsuarioB;
    private ObjectId usuarioAId;
    private ObjectId usuarioBId;

    @BeforeEach
    void setUp() {
        usuarioAId = new ObjectId();
        usuarioBId = new ObjectId();

        Instant agora = Instant.now();
        userRepository.inserir(usuario(usuarioAId, "Usuario A", "a-"));
        userRepository.inserir(usuario(usuarioBId, "Usuario B", "b-"));

        tokenUsuarioA = tokenService.gerarAccessToken(
                new Usuario(usuarioAId.toHexString(), "Usuario A", "a@teste.com", "hash",
                        List.of(Role.USER), true, true, 0, null, agora, agora, agora));
        tokenUsuarioB = tokenService.gerarAccessToken(
                new Usuario(usuarioBId.toHexString(), "Usuario B", "b@teste.com", "hash",
                        List.of(Role.USER), true, true, 0, null, agora, agora, agora));
    }

    private Usuario usuario(ObjectId id, String nome, String prefixoEmail) {
        Instant agora = Instant.now();
        return new Usuario(
                id.toHexString(),
                nome,
                prefixoEmail + UUID.randomUUID() + "@teste.com",
                "hash",
                List.of(Role.USER),
                true,
                true,
                0,
                null,
                agora,
                agora,
                agora);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private String criarCaderno(String token, String titulo) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("titulo", titulo))
                .when().post(CADERNOS)
                .then().statusCode(201)
                .extract().path("id");
    }

    private String primeiraSecao(String token, String cadernoId) {
        return given()
                .header("Authorization", "Bearer " + token)
                .pathParam("id", cadernoId)
                .when().get(CADERNOS + "/{id}/secoes")
                .then().statusCode(200)
                .extract().path("[0].id");
    }

    private String criarNota(String token, String cadernoId, String secaoId, String titulo, String markdown) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("titulo", titulo);
        if (markdown != null) {
            corpo.put("conteudoMarkdown", markdown);
        }
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(corpo)
                .when().post(CADERNOS + "/" + cadernoId + "/secoes/" + secaoId + "/notas")
                .then().statusCode(201)
                .extract().path("id");
    }

    private void semearTrilha(ObjectId trilhaId, ObjectId usuarioId, String nome, List<Disciplina> disciplinas) {
        trilhaRepository.inserir(new TrilhaRepository.DadosInsercaoTrilha(
                usuarioId, trilhaId, nome, "Cargo", "Banca", null, "#8F9E8B", null, 0, null, Instant.now()));
        disciplinas.forEach(disciplinaRepository::inserir);
    }

    private Disciplina disciplina(ObjectId trilhaId, ObjectId usuarioId, String nome, int ordem,
            List<TopicoEmbutido> topicos) {
        return new Disciplina(new ObjectId(), usuarioId, trilhaId, nome, "#4A6B82", 2, ordem,
                topicos, Instant.now(), Instant.now());
    }

    private TopicoEmbutido topico(String nome, int ordem) {
        return new TopicoEmbutido(new ObjectId().toHexString(), nome, ordem);
    }

    // ── 1. autenticação e ciclo de vida ─────────────────────────────────────

    @Test
    @DisplayName("R9.1 Todas as rotas privadas exigem autenticação (401)")
    void deveExigirAutenticacaoEmTodasAsRotas() {
        given().when().get(CADERNOS).then().statusCode(401);
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("titulo", "Sem token"))
                .when().post(CADERNOS).then().statusCode(401);
        given().when().get(NOTAS + "/busca?q=constituicao").then().statusCode(401);
        given().when().get(NOTAS + "/lixeira").then().statusCode(401);
        given().when().post(NOTAS + "/lixeira/nota/" + new ObjectId().toHexString() + "/restaurar")
                .then().statusCode(401);
        given().when().delete(NOTAS + "/lixeira/esvaziar").then().statusCode(401);
        given().when().delete(NOTAS + "/" + new ObjectId().toHexString()).then().statusCode(401);
    }

    @Test
    @DisplayName("R9.2 Ciclo de vida: caderno, seções, página e caderno com zero seções ativas")
    void deveGerenciarCicloCompletoEPermitirCadernoComZeroSecoes() {
        String cadernoId = criarCaderno(tokenUsuarioA, "Caderno de Constitucional");

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().get(CADERNO)
                .then()
                .statusCode(200)
                .body("id", equalTo(cadernoId))
                .body("titulo", equalTo("Caderno de Constitucional"))
                .body("totalSecoes", equalTo(1))
                .body("totalPaginas", equalTo(0))
                .body("trilhaNome", nullValue());

        String secaoGeralId = primeiraSecao(tokenUsuarioA, cadernoId);

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .pathParam("id", cadernoId)
                .body(Map.of("titulo", "Controle de Constitucionalidade"))
                .when().post(CADERNOS + "/{id}/secoes")
                .then().statusCode(201)
                .body("titulo", equalTo("Controle de Constitucionalidade"));

        criarNota(tokenUsuarioA, cadernoId, secaoGeralId, "ADI e ADC", "# Ações do Controle Concentrado");

        // Remover todas as seções ativas é permitido: o caderno fica vazio com estado orientador.
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", secaoGeralId)
                .when().delete(SECAO)
                .then().statusCode(200)
                .body("tipo", equalTo("SECAO"));

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().get(CADERNOS + "/{id}/secoes")
                .then().statusCode(200)
                .body("$", hasSize(1));

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().get(CADERNO)
                .then().statusCode(200)
                .body("totalSecoes", equalTo(1));
    }

    @Test
    @DisplayName("R9.3 Listagem de páginas é paginada e nunca projeta conteudoMarkdown")
    void listagemNaoContemConteudoMarkdown() {
        String cadernoId = criarCaderno(tokenUsuarioA, "Caderno Metadados");
        String secaoId = primeiraSecao(tokenUsuarioA, cadernoId);
        String markdown = "# Cabeçalho\n\nTexto detalhado de estudo.";
        criarNota(tokenUsuarioA, cadernoId, secaoId, "Resumo Denso", markdown);

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .pathParam("secaoId", secaoId)
                .when().get(CADERNOS + "/{id}/secoes/{secaoId}/notas")
                .then()
                .statusCode(200)
                .body("itens", hasSize(1))
                .body("pagina", equalTo(1))
                .body("tamanho", equalTo(50))
                .body("totalItens", equalTo(1))
                .body("totalPaginas", equalTo(1))
                .body("itens[0].conteudoMarkdown", nullValue())
                .body("itens[0].titulo", equalTo("Resumo Denso"));
    }

    @Test
    @DisplayName("R9.4 Paginação fora do intervalo responde 400 INVALID_PAGINATION")
    void paginacaoInvalidaRetorna400() {
        String cadernoId = criarCaderno(tokenUsuarioA, "Caderno Paginação");
        String secaoId = primeiraSecao(tokenUsuarioA, cadernoId);

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .pathParam("secaoId", secaoId)
                .queryParam("pagina", 0)
                .when().get(CADERNOS + "/{id}/secoes/{secaoId}/notas")
                .then().statusCode(400).body("code", equalTo("INVALID_PAGINATION"));

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .pathParam("secaoId", secaoId)
                .queryParam("tamanho", 101)
                .when().get(CADERNOS + "/{id}/secoes/{secaoId}/notas")
                .then().statusCode(400).body("code", equalTo("INVALID_PAGINATION"));
    }

    @Test
    @DisplayName("R9.5 Endpoint canônico de página existe e o aninhado foi removido (D-32)")
    void endpointAninhadoDeNotaNaoExiste() {
        String cadernoId = criarCaderno(tokenUsuarioA, "Caderno Canônico");
        String secaoId = primeiraSecao(tokenUsuarioA, cadernoId);
        String notaId = criarNota(tokenUsuarioA, cadernoId, secaoId, "Página Canônica", "# Conteúdo");

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", notaId)
                .when().get(NOTA)
                .then().statusCode(200)
                .body("conteudoMarkdown", equalTo("# Conteúdo"));

        // Rota de leitura aninhada removida: 404/405, nunca 200.
        int status = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .when().get(CADERNOS + "/" + cadernoId + "/secoes/" + secaoId + "/notas/" + notaId)
                .then().extract().statusCode();
        assertTrue(status == 404 || status == 405, "Leitura aninhada deve estar ausente, status=" + status);
    }

    @Test
    @DisplayName("R9.6 POST /api/notas não existe: criação é apenas pela rota aninhada (DC-12)")
    void rotaDuplicadaDeCriacaoNaoExiste() {
        String cadernoId = criarCaderno(tokenUsuarioA, "Caderno Sem Duplicata");
        String secaoId = primeiraSecao(tokenUsuarioA, cadernoId);

        int status = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .body(Map.of("cadernoId", cadernoId, "secaoId", secaoId, "titulo", "Duplicada"))
                .when().post(NOTAS)
                .then().extract().statusCode();
        assertTrue(status == 404 || status == 405, "POST /api/notas deve estar ausente, status=" + status);
    }

    @Test
    @DisplayName("R9.7 Página em seção de outro caderno responde 404 (hierarquia do path)")
    void notaComSecaoDeOutroCadernoRetorna404() {
        String cadernoA = criarCaderno(tokenUsuarioA, "Caderno A");
        String cadernoB = criarCaderno(tokenUsuarioA, "Caderno B");
        String secaoDoB = primeiraSecao(tokenUsuarioA, cadernoB);

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .body(Map.of("titulo", "Cruzada"))
                .when().post(CADERNOS + "/" + cadernoA + "/secoes/" + secaoDoB + "/notas")
                .then().statusCode(404);

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoA)
                .pathParam("secaoId", secaoDoB)
                .when().get(CADERNOS + "/{id}/secoes/{secaoId}/notas")
                .then().statusCode(404);
    }

    @Test
    @DisplayName("R9.8 Markdown acima de 512 KB responde 400 VALIDATION_ERROR em conteudoMarkdown")
    void markdownAcimaDe512KBRetorna400ValidationError() {
        String cadernoId = criarCaderno(tokenUsuarioA, "Caderno Limite");
        String secaoId = primeiraSecao(tokenUsuarioA, cadernoId);
        String markdownGigante = "A".repeat(525000);

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .body(Map.of("titulo", "Grande demais", "conteudoMarkdown", markdownGigante))
                .when().post(CADERNOS + "/" + cadernoId + "/secoes/" + secaoId + "/notas")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("errors.conteudoMarkdown", notNullValue());
    }

    @Test
    @DisplayName("R9.9 Markdown com caractere de controle responde 400 VALIDATION_ERROR (D-30d)")
    void markdownComCaractereDeControleRetorna400() {
        String cadernoId = criarCaderno(tokenUsuarioA, "Caderno Controle");
        String secaoId = primeiraSecao(tokenUsuarioA, cadernoId);

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .body(Map.of("titulo", "Com controle", "conteudoMarkdown", "texto\u0000invalido"))
                .when().post(CADERNOS + "/" + cadernoId + "/secoes/" + secaoId + "/notas")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("errors.conteudoMarkdown", notNullValue());

        // Tab, LF e CR continuam aceitos.
        criarNota(tokenUsuarioA, cadernoId, secaoId, "Com tabulação", "linha 1\n\tlinha 2\r\nlinha 3");
    }

    @Test
    @DisplayName("R9.10 Busca devolve snippet texto puro com destaques, acento-insensível, sem lixeira nem outro usuário")
    void buscaRetornaSnippetTextoPuroComDestaques() {
        String cadernoA = criarCaderno(tokenUsuarioA, "Caderno de Constitucional A");
        String secaoA = primeiraSecao(tokenUsuarioA, cadernoA);
        String markdown = "Introdução detalhada sobre os princípios fundamentais e a Constituição "
                + "da República Federativa do Brasil promulgada em 1988.";
        String notaId = criarNota(tokenUsuarioA, cadernoA, secaoA, "Princípios Fundamentais", markdown);

        String cadernoB = criarCaderno(tokenUsuarioB, "Caderno B");
        String secaoB = primeiraSecao(tokenUsuarioB, cadernoB);
        criarNota(tokenUsuarioB, cadernoB, secaoB, "Constituição do Usuário B",
                "Texto exclusivo do usuário B sobre a constituição");

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .queryParam("q", "constituicao")
                .when().get(NOTAS + "/busca")
                .then()
                .statusCode(200)
                .body("totalItens", equalTo(1))
                .body("itens", hasSize(1))
                .body("itens[0].notaId", equalTo(notaId))
                .body("itens[0].cadernoTitulo", equalTo("Caderno de Constitucional A"))
                .body("itens[0].secaoTitulo", equalTo("Geral"))
                .body("itens[0].conteudoMarkdown", nullValue())
                .body("itens[0].snippet", notNullValue())
                .body("itens[0].destaques", notNullValue())
                // O backend nunca gera marcação: o snippet é texto puro.
                .body("itens[0].snippet", not(containsString("<mark>")))
                .body("itens[0].snippet.length()", lessThanOrEqualTo(180));

        // Busca com acento encontra o mesmo documento (D-30).
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .queryParam("q", "Constituição")
                .when().get(NOTAS + "/busca")
                .then().statusCode(200)
                .body("totalItens", equalTo(1))
                .body("itens[0].notaId", equalTo(notaId));
    }

    @Test
    @DisplayName("R9.11 Busca ignora itens na lixeira e consulta curta responde 400")
    void buscaNaoRetornaLixeiraNemOutroUsuario() {
        String cadernoId = criarCaderno(tokenUsuarioA, "Caderno Busca Lixeira");
        String secaoId = primeiraSecao(tokenUsuarioA, cadernoId);
        String notaId = criarNota(tokenUsuarioA, cadernoId, secaoId, "Princípios",
                "Texto sobre a Constituição brasileira.");

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", notaId)
                .when().delete(NOTA)
                .then().statusCode(200);

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .queryParam("q", "constituicao")
                .when().get(NOTAS + "/busca")
                .then().statusCode(200)
                .body("totalItens", equalTo(0))
                .body("itens", hasSize(0));

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .queryParam("q", "a")
                .when().get(NOTAS + "/busca")
                .then().statusCode(400)
                .body("code", equalTo("INVALID_SEARCH_QUERY"));

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .queryParam("q", "de que o a")
                .when().get(NOTAS + "/busca")
                .then().statusCode(400)
                .body("code", equalTo("INVALID_SEARCH_QUERY"));
    }

    // ── 2. importação, reimportação e sincronização (D-29) ───────────────────

    @Test
    @DisplayName("R9.12 Reimportar sem reimportar=true com vínculo ativo responde 409 TRAIL_ALREADY_IMPORTED")
    void importarSemReimportarComVinculoExistenteRetorna409() {
        ObjectId trilhaId = new ObjectId();
        semearTrilha(trilhaId, usuarioAId, "Trilha Auditor",
                List.of(disciplina(trilhaId, usuarioAId, "Português", 0, List.of(topico("Crase", 0)))));

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("trilhaId", trilhaId.toHexString())
                .when().post("/api/cadernos/importar-trilha/{trilhaId}")
                .then().statusCode(201);

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("trilhaId", trilhaId.toHexString())
                .when().post("/api/cadernos/importar-trilha/{trilhaId}")
                .then()
                .statusCode(409)
                .body("code", equalTo("TRAIL_ALREADY_IMPORTED"))
                .body("errors.cadernoId", notNullValue());
    }

    @Test
    @DisplayName("R9.13 Reimportar desvincula o antigo e cria novo com o nome da trilha (sem sufixo)")
    void reimportarDesvinculaAntigoECriaNovoComNomeDaTrilha() {
        ObjectId trilhaId = new ObjectId();
        semearTrilha(trilhaId, usuarioAId, "Trilha Auditor Fiscal",
                List.of(disciplina(trilhaId, usuarioAId, "Língua Portuguesa", 0,
                        List.of(topico("Morfologia", 0), topico("Pontuação", 1)))));

        String primeiroCaderno = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("trilhaId", trilhaId.toHexString())
                .when().post("/api/cadernos/importar-trilha/{trilhaId}")
                .then().statusCode(201)
                .body("titulo", equalTo("Trilha Auditor Fiscal"))
                .body("trilhaNome", equalTo("Trilha Auditor Fiscal"))
                .body("origemTrilhaId", equalTo(trilhaId.toHexString()))
                .body("totalSecoes", equalTo(1))
                .body("totalPaginas", equalTo(2))
                .extract().path("id");

        String segundoCaderno = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("trilhaId", trilhaId.toHexString())
                .queryParam("reimportar", true)
                .when().post("/api/cadernos/importar-trilha/{trilhaId}")
                .then().statusCode(201)
                .body("titulo", equalTo("Trilha Auditor Fiscal"))
                .body("origemTrilhaId", equalTo(trilhaId.toHexString()))
                .extract().path("id");

        assertNotEquals(primeiroCaderno, segundoCaderno, "Reimportação cria um novo caderno");

        // Antigo permanece ativo, com conteúdo preservado e sem vínculo de origem.
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", primeiroCaderno)
                .when().get(CADERNO)
                .then()
                .statusCode(200)
                .body("origemTrilhaId", nullValue())
                .body("trilhaNome", nullValue())
                .body("totalSecoes", equalTo(1))
                .body("totalPaginas", equalTo(2));

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", primeiroCaderno)
                .when().get(CADERNOS + "/{id}/secoes")
                .then().statusCode(200)
                .body("[0].origemDisciplinaId", nullValue());
    }

    @Test
    @DisplayName("R9.15 Importar trilha de outro usuário responde 404")
    void importarTrilhaDeOutroUsuarioRetorna404() {
        ObjectId trilhaDoB = new ObjectId();
        semearTrilha(trilhaDoB, usuarioBId, "Trilha Privada do B",
                List.of(disciplina(trilhaDoB, usuarioBId, "Matéria", 0, List.of())));

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("trilhaId", trilhaDoB.toHexString())
                .when().post("/api/cadernos/importar-trilha/{trilhaId}")
                .then().statusCode(404);
    }

    @Test
    @DisplayName("R9.16 Sincronizar caderno sem origem responde 400 VALIDATION_ERROR em errors.cadernoId")
    void sincronizarCadernoSemOrigemRetorna400ComCampo() {
        String cadernoId = criarCaderno(tokenUsuarioA, "Caderno sem trilha");

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().post(CADERNOS + "/{id}/sincronizar-trilha")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("errors.cadernoId[0]", equalTo("Caderno não vinculado a uma trilha."));
    }

    @Test
    @DisplayName("R9.14 Sincronizar não cria nem altera páginas de seções existentes")
    void sincronizarNaoCriaNotasEmSecoesExistentes() {
        ObjectId trilhaId = new ObjectId();
        Disciplina portugues = disciplina(trilhaId, usuarioAId, "Português", 0, List.of(topico("Crase", 0)));
        semearTrilha(trilhaId, usuarioAId, "Trilha Sync", List.of(portugues));

        String cadernoId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("trilhaId", trilhaId.toHexString())
                .when().post("/api/cadernos/importar-trilha/{trilhaId}")
                .then().statusCode(201)
                .extract().path("id");

        String secaoId = primeiraSecao(tokenUsuarioA, cadernoId);
        String notaId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .pathParam("secaoId", secaoId)
                .when().get(CADERNOS + "/{id}/secoes/{secaoId}/notas")
                .then().statusCode(200)
                .extract().path("itens[0].id");

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .pathParam("id", notaId)
                .body(Map.of("conteudoMarkdown", "# Anotação do estudante"))
                .when().put(NOTA)
                .then().statusCode(200);

        // A disciplina ganha um tópico novo: a sincronização NÃO deve criar página nessa seção.
        disciplinaRepository.atualizar(new Disciplina(
                portugues.id(), usuarioAId, trilhaId, "Português", portugues.cor(), portugues.peso(),
                portugues.ordem(), List.of(topico("Crase", 0), topico("Regência", 1)),
                portugues.criadoEm(), Instant.now()));

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().post(CADERNOS + "/{id}/sincronizar-trilha")
                .then()
                .statusCode(200)
                .body("secoesCriadas", equalTo(0))
                .body("paginasCriadas", equalTo(0));

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", notaId)
                .when().get(NOTA)
                .then().statusCode(200)
                .body("conteudoMarkdown", equalTo("# Anotação do estudante"));

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .pathParam("secaoId", secaoId)
                .when().get(CADERNOS + "/{id}/secoes/{secaoId}/notas")
                .then().statusCode(200).body("totalItens", equalTo(1));
    }

    @Test
    @DisplayName("R9.17 Sincronizar cria seção com páginas apenas para disciplina nova")
    void sincronizarCriaSecaoParaDisciplinaNovaComPaginas() {
        ObjectId trilhaId = new ObjectId();
        Disciplina portugues = disciplina(trilhaId, usuarioAId, "Português", 0, List.of(topico("Crase", 0)));
        semearTrilha(trilhaId, usuarioAId, "Trilha Nova Disciplina", List.of(portugues));

        String cadernoId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("trilhaId", trilhaId.toHexString())
                .when().post("/api/cadernos/importar-trilha/{trilhaId}")
                .then().statusCode(201)
                .extract().path("id");

        disciplinaRepository.inserir(disciplina(trilhaId, usuarioAId, "Direito Tributário", 1,
                List.of(topico("Impostos Federais", 0), topico("Competência", 1))));

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().post(CADERNOS + "/{id}/sincronizar-trilha")
                .then()
                .statusCode(200)
                .body("secoesCriadas", equalTo(1))
                .body("paginasCriadas", equalTo(2))
                .body("secoesDesvinculadas", equalTo(0));

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().get(CADERNOS + "/{id}/secoes")
                .then().statusCode(200)
                .body("titulo", hasItem("Direito Tributário"));
    }

    // ── 3. impacto de exclusão, lixeira e Restauradas (D-33/D-34) ────────────

    @Test
    @DisplayName("R9.18 impacto-exclusao conta somente itens ativos")
    void impactoExclusaoContaSoAtivos() {
        String cadernoId = criarCaderno(tokenUsuarioA, "Caderno Impacto");
        String secaoGeral = primeiraSecao(tokenUsuarioA, cadernoId);

        String secaoExtra = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .pathParam("id", cadernoId)
                .body(Map.of("titulo", "Seção Extra"))
                .when().post(CADERNOS + "/{id}/secoes")
                .then().statusCode(201)
                .extract().path("id");

        String paginaAtiva = criarNota(tokenUsuarioA, cadernoId, secaoGeral, "Ativa", "conteúdo");
        String paginaExcluida = criarNota(tokenUsuarioA, cadernoId, secaoGeral, "Excluída", "conteúdo");

        // Uma página vai para a lixeira isoladamente: não deve contar no impacto.
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", paginaExcluida)
                .when().delete(NOTA)
                .then().statusCode(200);

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().get(CADERNOS + "/{id}/impacto-exclusao")
                .then()
                .statusCode(200)
                .body("id", equalTo(cadernoId))
                .body("titulo", equalTo("Caderno Impacto"))
                .body("secoes", equalTo(2))
                .body("paginas", equalTo(1));

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", secaoGeral)
                .when().get("/api/secoes/{id}/impacto-exclusao")
                .then()
                .statusCode(200)
                .body("paginas", equalTo(1));

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", secaoExtra)
                .when().get("/api/secoes/{id}/impacto-exclusao")
                .then().statusCode(200).body("paginas", equalTo(0));

        assertTrue(notaRepository.buscarAtiva(usuarioAId, new ObjectId(paginaAtiva)).isPresent());
    }

    @Test
    @DisplayName("R9.19 Lixeira é paginada com breadcrumb e restaura ancestrais do mesmo lote")
    void lixeiraPaginadaRestauraAncestraisDoMesmoLote() {
        String cadernoId = criarCaderno(tokenUsuarioA, "Caderno Lixeira");
        String secaoId = primeiraSecao(tokenUsuarioA, cadernoId);
        String notaId = criarNota(tokenUsuarioA, cadernoId, secaoId, "Página na Lixeira", "Conteúdo teste");

        String lote = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().delete(CADERNO)
                .then()
                .statusCode(200)
                .body("tipo", equalTo("CADERNO"))
                .body("loteExclusaoId", notNullValue())
                .body("secoesAfetadas", equalTo(1))
                .body("notasAfetadas", equalTo(1))
                .extract().path("loteExclusaoId");

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .queryParam("pagina", 1)
                .queryParam("tamanho", 2)
                .when().get(NOTAS + "/lixeira")
                .then()
                .statusCode(200)
                .body("itens", hasSize(2))
                .body("pagina", equalTo(1))
                .body("tamanho", equalTo(2))
                .body("totalItens", equalTo(3))
                .body("totalPaginas", equalTo(2))
                .body("itens.loteExclusaoId", everyItem(equalTo(lote)))
                .body("itens.tipo", notNullValue())
                .body("itens.breadcrumb", notNullValue());

        // Restaurar só a página sobe seção e caderno do mesmo lote.
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("tipo", "nota")
                .pathParam("id", notaId)
                .when().post(NOTAS + "/lixeira/{tipo}/{id}/restaurar")
                .then()
                .statusCode(200)
                .body("ancestralRestaurado", equalTo(true))
                .body("realocadaPara", nullValue());

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", notaId)
                .when().get(NOTA)
                .then().statusCode(200);

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().get(CADERNO)
                .then().statusCode(200);
    }

    @Test
    @DisplayName("R9.20 Restaurar fora do prazo responde 404 TRASH_ITEM_EXPIRED")
    void restaurarForaDoPrazoRetorna404TrashItemExpired() {
        String cadernoId = criarCaderno(tokenUsuarioA, "Caderno Expirado");
        String secaoId = primeiraSecao(tokenUsuarioA, cadernoId);
        String notaId = criarNota(tokenUsuarioA, cadernoId, secaoId, "Expirada", "conteúdo");

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", notaId)
                .when().delete(NOTA)
                .then().statusCode(200);

        // Envelhece o item além dos 30 dias diretamente no BSON.
        Instant passado = Instant.now().minus(40, ChronoUnit.DAYS);
        mongoClient.getDatabase(databaseName).getCollection("notas").updateOne(
                new Document("_id", new ObjectId(notaId)),
                new Document("$set", new Document("expiresAt", java.util.Date.from(passado))));

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("tipo", "nota")
                .pathParam("id", notaId)
                .when().post(NOTAS + "/lixeira/{tipo}/{id}/restaurar")
                .then()
                .statusCode(404)
                .body("code", equalTo("TRASH_ITEM_EXPIRED"));

        // Restaurar algo que nunca esteve na lixeira é 404 NOT_FOUND, não expirado.
        String outraNota = criarNota(tokenUsuarioA, cadernoId, secaoId, "Ativa", "ok");
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("tipo", "nota")
                .pathParam("id", outraNota)
                .when().post(NOTAS + "/lixeira/{tipo}/{id}/restaurar")
                .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    @DisplayName("R9.21 Caderno purgado: página vai para a seção Restauradas do caderno Restauradas")
    void restaurarComCadernoPurgadoCriaCadernoESecaoRestauradas() {
        String cadernoId = criarCaderno(tokenUsuarioA, "Caderno que será purgado");
        String secaoId = primeiraSecao(tokenUsuarioA, cadernoId);
        String notaId = criarNota(tokenUsuarioA, cadernoId, secaoId, "Página órfã", "conteúdo");

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().delete(CADERNO)
                .then().statusCode(200);

        // Purga definitiva de seção e caderno (mantendo a página na lixeira).
        Instant passado = Instant.now().minus(40, ChronoUnit.DAYS);
        var banco = mongoClient.getDatabase(databaseName);
        banco.getCollection("secoes").deleteOne(new Document("_id", new ObjectId(secaoId)));
        banco.getCollection("cadernos").deleteOne(new Document("_id", new ObjectId(cadernoId)));

        String cadernoDestino = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("tipo", "nota")
                .pathParam("id", notaId)
                .when().post(NOTAS + "/lixeira/{tipo}/{id}/restaurar")
                .then()
                .statusCode(200)
                .body("realocadaPara", notNullValue())
                .body("realocadaPara.cadernoId", notNullValue())
                .body("realocadaPara.secaoId", notNullValue())
                .extract().path("realocadaPara.cadernoId");

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoDestino)
                .when().get(CADERNO)
                .then().statusCode(200)
                .body("titulo", equalTo("Restauradas"));

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", notaId)
                .when().get(NOTA)
                .then().statusCode(200)
                .body("cadernoId", equalTo(cadernoDestino));
    }

    // ── 4. limites, filtro e lixeira administrativa ──────────────────────────

    @Test
    @DisplayName("R9.22 Limite de 500 cadernos por usuário responde 409 RESOURCE_LIMIT_EXCEEDED")
    void limiteDe500CadernosRetorna409() {
        int maximo = NotaServiceLimiteCadernos();
        for (int i = 0; i < maximo; i++) {
            // insere direto no BSON: criar 500 cadernos via HTTP seria desnecessariamente lento
            cadernoRepository.inserir(new Caderno(
                    new ObjectId(), usuarioAId, "Caderno " + i, "#0284c7", "book-open",
                    null, null, null, null, Instant.now(), Instant.now()));
        }

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .body(Map.of("titulo", "Excedente"))
                .when().post(CADERNOS)
                .then()
                .statusCode(409)
                .body("code", equalTo("RESOURCE_LIMIT_EXCEEDED"));
    }

    private static int NotaServiceLimiteCadernos() {
        return org.eclipse.microprofile.config.ConfigProvider.getConfig()
                .getOptionalValue("trilhara.notas.max-cadernos-por-usuario", Integer.class)
                .orElse(500);
    }

    @Test
    @DisplayName("R9.23 Filtro origemTrilhaId devolve apenas o caderno vinculado da trilha")
    void filtroOrigemTrilhaId() {
        ObjectId trilhaId = new ObjectId();
        semearTrilha(trilhaId, usuarioAId, "Trilha Filtro",
                List.of(disciplina(trilhaId, usuarioAId, "Disciplina", 0, List.of())));

        String vinculado = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("trilhaId", trilhaId.toHexString())
                .when().post("/api/cadernos/importar-trilha/{trilhaId}")
                .then().statusCode(201)
                .extract().path("id");

        criarCaderno(tokenUsuarioA, "Caderno manual");

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .queryParam("origemTrilhaId", trilhaId.toHexString())
                .when().get(CADERNOS)
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(vinculado))
                .body("[0].trilhaNome", equalTo("Trilha Filtro"));

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .queryParam("origemTrilhaId", "id-invalido")
                .when().get(CADERNOS)
                .then().statusCode(400).body("code", equalTo("INVALID_IDENTIFIER"));
    }

    @Test
    @DisplayName("R9.24 Esvaziar lixeira remove tudo e a purga agendada roda sem erro")
    void esvaziarLixeiraEPurgar() {
        String cadernoId = criarCaderno(tokenUsuarioA, "Caderno Esvaziar");
        String secaoId = primeiraSecao(tokenUsuarioA, cadernoId);
        criarNota(tokenUsuarioA, cadernoId, secaoId, "Página", "conteúdo");

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().delete(CADERNO)
                .then().statusCode(200);

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .when().delete(NOTAS + "/lixeira/esvaziar")
                .then()
                .statusCode(200)
                .body("cadernosRemovidos", greaterThan(0));

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .when().get(NOTAS + "/lixeira")
                .then().statusCode(200).body("totalItens", equalTo(0));

        assertTrue(purgaLixeiraNotasJob.executarPurga(Instant.now()) >= 0);
    }

    @Test
    @DisplayName("R12.1 Lote de exclusão respeita itens já excluídos isoladamente e descendentes já purgados")
    void loteRespeitaItensJaExcluidosEDescendentesPurgados() {
        String cadernoId = criarCaderno(tokenUsuarioA, "Caderno Lote Misto");
        String secaoId = primeiraSecao(tokenUsuarioA, cadernoId);

        String notaIsolada = criarNota(tokenUsuarioA, cadernoId, secaoId, "Excluída antes", "a");
        String notaPurgada = criarNota(tokenUsuarioA, cadernoId, secaoId, "Já purgada", "b");
        String notaAtiva = criarNota(tokenUsuarioA, cadernoId, secaoId, "Ainda ativa", "c");

        // 1) Uma página já estava na lixeira com lote próprio (exclusão isolada anterior).
        String loteIsolado = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", notaIsolada)
                .when().delete(NOTA)
                .then().statusCode(200)
                .extract().path("loteExclusaoId");

        // 2) Outra página é excluída e tem os descendentes purgados fisicamente (fora do prazo).
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", notaPurgada)
                .when().delete(NOTA)
                .then().statusCode(200);

        mongoClient.getDatabase(databaseName).getCollection("notas").updateOne(
                new Document("_id", new ObjectId(notaPurgada)),
                new Document("$set", new Document("expiresAt",
                        java.util.Date.from(Instant.now().minus(40, ChronoUnit.DAYS)))));

        // 3) Excluir o caderno inteiro cria um novo lote.
        String loteCaderno = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().delete(CADERNO)
                .then().statusCode(200)
                .body("tipo", equalTo("CADERNO"))
                .body("loteExclusaoId", notNullValue())
                .extract().path("loteExclusaoId");

        // O item já excluído isoladamente mantém o lote ORIGINAL (não é reatribuído).
        String lotePersistido = mongoClient.getDatabase(databaseName)
                .getCollection("notas")
                .find(new Document("_id", new ObjectId(notaIsolada)))
                .first()
                .getString("loteExclusaoId");
        assertEquals(loteIsolado, lotePersistido,
                "Item previamente excluído não pode trocar de lote na exclusão em cascata");

        // O caderno e a página ainda ativa entram no lote novo.
        assertEquals(loteCaderno, mongoClient.getDatabase(databaseName)
                        .getCollection("cadernos")
                        .find(new Document("_id", new ObjectId(cadernoId)))
                        .first()
                        .getString("loteExclusaoId"),
                "O caderno deve carregar o lote da exclusão em cascata");
        assertEquals(loteCaderno, mongoClient.getDatabase(databaseName)
                        .getCollection("notas")
                        .find(new Document("_id", new ObjectId(notaAtiva)))
                        .first()
                        .getString("loteExclusaoId"),
                "A página ativa deve entrar no lote da cascata");

        // Restaurar o caderno não ressuscita o descendente já purgado nem move o isolado.
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("tipo", "caderno")
                .pathParam("id", cadernoId)
                .when().post(NOTAS + "/lixeira/{tipo}/{id}/restaurar")
                .then().statusCode(200);

        assertTrue(notaRepository.buscarAtiva(usuarioAId, new ObjectId(notaAtiva)).isPresent(),
                "Página ativa deve voltar junto com o caderno");
        assertTrue(notaRepository.buscarAtiva(usuarioAId, new ObjectId(notaPurgada)).isEmpty(),
                "Página já purgada não pode ser ressuscitada pela restauração do lote");
        assertTrue(notaRepository.buscarAtiva(usuarioAId, new ObjectId(notaIsolada)).isEmpty(),
                "Página com lote próprio só volta pela sua própria restauração");
    }
}
