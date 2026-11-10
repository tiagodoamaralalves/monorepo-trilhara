package br.com.trilhara.nota;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.trilhara.auth.Role;
import br.com.trilhara.auth.TokenService;
import br.com.trilhara.auth.UserRepository;
import br.com.trilhara.auth.Usuario;
import br.com.trilhara.trilha.Disciplina;
import br.com.trilhara.trilha.DisciplinaRepository;
import br.com.trilhara.trilha.TopicoEmbutido;
import br.com.trilhara.trilha.TrilhaRepository;
import br.com.trilhara.trilha.TrilhaService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

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
    TrilhaService trilhaService;

    @Inject
    PurgaLixeiraNotasJob purgaLixeiraNotasJob;

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
        Usuario usuarioA = new Usuario(
                usuarioAId.toHexString(),
                "Usuario A",
                "a-" + UUID.randomUUID() + "@teste.com",
                "hash",
                List.of(Role.USER),
                true,
                true,
                0,
                null,
                agora,
                agora,
                agora);

        Usuario usuarioB = new Usuario(
                usuarioBId.toHexString(),
                "Usuario B",
                "b-" + UUID.randomUUID() + "@teste.com",
                "hash",
                List.of(Role.USER),
                true,
                true,
                0,
                null,
                agora,
                agora,
                agora);

        userRepository.inserir(usuarioA);
        userRepository.inserir(usuarioB);

        tokenUsuarioA = tokenService.gerarAccessToken(usuarioA);
        tokenUsuarioB = tokenService.gerarAccessToken(usuarioB);
    }

    @Test
    @DisplayName("8.1 Autenticação obrigatória em todas as rotas de notas")
    void deveExigirAutenticacaoNasRotasDeNotas() {
        given().contentType(ContentType.JSON)
                .when().get(CADERNOS)
                .then().statusCode(401);

        given().contentType(ContentType.JSON)
                .when().get(NOTAS + "/busca?q=teste")
                .then().statusCode(401);

        given().contentType(ContentType.JSON)
                .when().get(NOTAS + "/lixeira")
                .then().statusCode(401);

        given().contentType(ContentType.JSON)
                .when().get(NOTAS + "/qualquer-id")
                .then().statusCode(401);
    }

    @Test
    @DisplayName("8.2 Ciclo de vida completo: Caderno, Seções, Notas e permissão de zero seções ativas")
    void deveGerenciarFluxoCompletoCadernoSecaoENotaEPermitirZeroSecoes() throws Exception {
        // 1. Cria caderno
        String cadernoId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .body(jsonMapper.writeValueAsString(Map.of(
                        "titulo", "Caderno de Direito Constitucional",
                        "cor", "#0284c7",
                        "icone", "book-open")))
                .when().post(CADERNOS)
                .then()
                .statusCode(201)
                .header("Location", notNullValue())
                .body("id", notNullValue())
                .body("titulo", equalTo("Caderno de Direito Constitucional"))
                .body("secoes", hasSize(1))
                .body("secoes[0].titulo", equalTo("Geral"))
                .extract().path("id");

        String secaoGeralId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .pathParam("id", cadernoId)
                .when().get(CADERNO)
                .then()
                .statusCode(200)
                .extract().path("secoes[0].id");

        // 2. Cria nova seção
        String novaSecaoId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .pathParam("id", cadernoId)
                .body(jsonMapper.writeValueAsString(Map.of(
                        "titulo", "Controle de Constitucionalidade",
                        "cor", "#38bdf8")))
                .when().post("/api/cadernos/{id}/secoes")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("titulo", equalTo("Controle de Constitucionalidade"))
                .extract().path("id");

        // 3. Atualiza seção
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .pathParam("id", novaSecaoId)
                .body(jsonMapper.writeValueAsString(Map.of("titulo", "Controle Concentrado e Difuso")))
                .when().put(SECAO)
                .then()
                .statusCode(200)
                .body("titulo", equalTo("Controle Concentrado e Difuso"));

        // 4. Cria nota na seção
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .body(jsonMapper.writeValueAsString(Map.of(
                        "cadernoId", cadernoId,
                        "secaoId", novaSecaoId,
                        "titulo", "ADI e ADC",
                        "conteudoMarkdown", "# Ações do Controle Concentrado")))
                .when().post(NOTAS)
                .then()
                .statusCode(201)
                .header("Location", notNullValue())
                .body("id", notNullValue())
                .body("titulo", equalTo("ADI e ADC"))
                .body("conteudoMarkdown", equalTo("# Ações do Controle Concentrado"))
                .extract().path("id");

        // 5. Apaga seções até restar ZERO seções ativas no caderno (removendo trava legada)
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", novaSecaoId)
                .when().delete(SECAO)
                .then()
                .statusCode(200)
                .body("tipo", equalTo("SECAO"));

        // Apaga a última seção ("Geral") com sucesso
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", secaoGeralId)
                .when().delete(SECAO)
                .then()
                .statusCode(200)
                .body("tipo", equalTo("SECAO"));

        // Caderno agora tem 0 seções ativas
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().get(CADERNO)
                .then()
                .statusCode(200)
                .body("secoes", hasSize(0));
    }

    @Test
    @DisplayName("8.3 Listagem de páginas projeta apenas metadados (sem conteudoMarkdown); GET individual traz completo")
    void deveProjetarApenasMetadadosSemConteudoMarkdownEmListagensEExporCompletoEmGetIndividual() throws Exception {
        // Cria caderno e obtém a seção padrão
        String cadernoId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .body(jsonMapper.writeValueAsString(Map.of("titulo", "Caderno Metadados")))
                .when().post(CADERNOS)
                .then().statusCode(201)
                .extract().path("id");

        String secaoId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().get(CADERNO)
                .then().statusCode(200)
                .extract().path("secoes[0].id");

        String markdownLongo = "# Cabeçalho Extenso\n\nEste é um texto muito detalhado com múltiplos parágrafos para estudo.";

        // Cria nota
        String notaId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .body(jsonMapper.writeValueAsString(Map.of(
                        "cadernoId", cadernoId,
                        "secaoId", secaoId,
                        "titulo", "Resumo Denso de Direito",
                        "conteudoMarkdown", markdownLongo)))
                .when().post(NOTAS)
                .then().statusCode(201)
                .extract().path("id");

        // Listagem de notas: NÃO pode conter conteudoMarkdown
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .pathParam("secaoId", secaoId)
                .when().get("/api/cadernos/{id}/secoes/{secaoId}/notas")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(notaId))
                .body("[0].titulo", equalTo("Resumo Denso de Direito"))
                .body("[0].conteudoMarkdown", nullValue());

        // Leitura individual: DEVE conter o conteudoMarkdown completo
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", notaId)
                .when().get(NOTA)
                .then()
                .statusCode(200)
                .body("id", equalTo(notaId))
                .body("titulo", equalTo("Resumo Denso de Direito"))
                .body("conteudoMarkdown", equalTo(markdownLongo));

        // Atualização de nota (apenas markdown ou apenas título)
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .pathParam("id", notaId)
                .body(jsonMapper.writeValueAsString(Map.of("conteudoMarkdown", "# Markdown Atualizado")))
                .when().put(NOTA)
                .then()
                .statusCode(200)
                .body("titulo", equalTo("Resumo Denso de Direito"))
                .body("conteudoMarkdown", equalTo("# Markdown Atualizado"));
    }

    @Test
    @DisplayName("8.4 Validação estrita do limite máximo de 512 KB em Markdown")
    void deveValidarLimiteMaximoDe512KBDeMarkdown() throws Exception {
        String cadernoId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .body(jsonMapper.writeValueAsString(Map.of("titulo", "Caderno Limite")))
                .when().post(CADERNOS)
                .then().statusCode(201)
                .extract().path("id");

        String secaoId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().get(CADERNO)
                .then().statusCode(200)
                .extract().path("secoes[0].id");

        // String com 513 KB (525.000 bytes)
        String markdownGigante = "A".repeat(525000);

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .body(jsonMapper.writeValueAsString(Map.of(
                        "cadernoId", cadernoId,
                        "secaoId", secaoId,
                        "titulo", "Nota Grande Demais",
                        "conteudoMarkdown", markdownGigante)))
                .when().post(NOTAS)
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("8.5 Busca global em português: ignora acentos, gera snippets de até 180 chars, isola usuário e ignora lixeira")
    void deveRealizarBuscaGlobalEmPortuguesComSnippetSemMarkdownCompletoEIgnorandoAcentos() throws Exception {
        // Cria caderno do Usuário A
        String cadernoAId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .body(jsonMapper.writeValueAsString(Map.of("titulo", "Caderno de Constitucional A")))
                .when().post(CADERNOS)
                .then().statusCode(201)
                .extract().path("id");

        String secaoAId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoAId)
                .when().get(CADERNO)
                .then().statusCode(200)
                .extract().path("secoes[0].id");

        // Cria nota com termo com acento: "Constituição da República Federativa"
        String markdownNota1 = "Introdução detalhada sobre os princípios fundamentais e a Constituição da República Federativa do Brasil promulgada no ano de 1988.";
        String nota1Id = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .body(jsonMapper.writeValueAsString(Map.of(
                        "cadernoId", cadernoAId,
                        "secaoId", secaoAId,
                        "titulo", "Princípios Fundamentais",
                        "conteudoMarkdown", markdownNota1)))
                .when().post(NOTAS)
                .then().statusCode(201)
                .extract().path("id");

        // Cria nota do Usuário B (não deve aparecer na busca de A)
        String cadernoBId = given()
                .header("Authorization", "Bearer " + tokenUsuarioB)
                .contentType(ContentType.JSON)
                .body(jsonMapper.writeValueAsString(Map.of("titulo", "Caderno B")))
                .when().post(CADERNOS)
                .then().statusCode(201)
                .extract().path("id");

        String secaoBId = given()
                .header("Authorization", "Bearer " + tokenUsuarioB)
                .pathParam("id", cadernoBId)
                .when().get(CADERNO)
                .then().statusCode(200)
                .extract().path("secoes[0].id");

        given()
                .header("Authorization", "Bearer " + tokenUsuarioB)
                .contentType(ContentType.JSON)
                .body(jsonMapper.writeValueAsString(Map.of(
                        "cadernoId", cadernoBId,
                        "secaoId", secaoBId,
                        "titulo", "Constituição do Usuário B",
                        "conteudoMarkdown", "Texto exclusivo do usuário B sobre a constituição")))
                .when().post(NOTAS)
                .then().statusCode(201);

        // 1. Busca sem acento ("constituicao") encontra a nota que contém "Constituição"
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .queryParam("q", "constituicao")
                .when().get(NOTAS + "/busca")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("itens", hasSize(1))
                .body("itens[0].notaId", equalTo(nota1Id))
                .body("itens[0].titulo", equalTo("Princípios Fundamentais"))
                .body("itens[0].cadernoTitulo", equalTo("Caderno de Constitucional A"))
                .body("itens[0].conteudoMarkdown", nullValue()) // Nunca retorna markdown completo!
                .body("itens[0].snippet", notNullValue());

        // 2. Busca com acento ("Constituição") também encontra
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .queryParam("q", "Constituição")
                .when().get(NOTAS + "/busca")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("itens[0].notaId", equalTo(nota1Id));

        // 3. Move a nota para a lixeira: a busca deve ignorá-la imediatamente
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", nota1Id)
                .when().delete(NOTA)
                .then().statusCode(200);

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .queryParam("q", "constituicao")
                .when().get(NOTAS + "/busca")
                .then()
                .statusCode(200)
                .body("total", equalTo(0))
                .body("itens", hasSize(0));
    }

    @Test
    @DisplayName("8.6 Validação de queries inválidas na busca: 400 INVALID_SEARCH_QUERY")
    void deveValidarErrosDeBuscaQueryInvalidaEIsolamentoDeUsuarioELixeira() {
        // Query com menos de 2 caracteres
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .queryParam("q", "a")
                .when().get(NOTAS + "/busca")
                .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_SEARCH_QUERY"));

        // Query vazia
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .queryParam("q", "   ")
                .when().get(NOTAS + "/busca")
                .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_SEARCH_QUERY"));

        // Query composta exclusivamente por stop words
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .queryParam("q", "de em para com")
                .when().get(NOTAS + "/busca")
                .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_SEARCH_QUERY"));
    }

    @Test
    @DisplayName("8.7 Importação, reimportação e sincronização explícita com trilha de Estudos")
    void deveImportarReimportarESincronizarTrilhaPreservandoCadernosAntigos() {
        // Cria Trilha com 2 disciplinas e tópicos
        ObjectId trilhaId = new ObjectId();
        ObjectId disc1Id = new ObjectId();
        ObjectId disc2Id = new ObjectId();

        TopicoEmbutido topico1 = new TopicoEmbutido(new ObjectId().toHexString(), "Morfologia e Sintaxe", 0);
        TopicoEmbutido topico2 = new TopicoEmbutido(new ObjectId().toHexString(), "Pontuação e Crase", 1);
        Disciplina disc1 = new Disciplina(disc1Id, usuarioAId, trilhaId, "Língua Portuguesa", "#4A6B82", 3, 0, null, List.of(topico1, topico2), Instant.now(), Instant.now());

        TopicoEmbutido topico3 = new TopicoEmbutido(new ObjectId().toHexString(), "Atos Administrativos", 0);
        Disciplina disc2 = new Disciplina(disc2Id, usuarioAId, trilhaId, "Direito Administrativo", "#D97706", 2, 1, null, List.of(topico3), Instant.now(), Instant.now());

        trilhaRepository.inserir(new TrilhaRepository.DadosInsercaoTrilha(
                usuarioAId, trilhaId, "Trilha Auditor Fiscal", "Auditor", "FGV", null, "#8F9E8B", null, 0, null, Instant.now()));
        disciplinaRepository.inserir(disc1);
        disciplinaRepository.inserir(disc2);

        // 1. Importa a trilha para o Caderno
        String caderno1Id = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("trilhaId", trilhaId.toHexString())
                .when().post("/api/cadernos/importar-trilha/{trilhaId}")
                .then()
                .statusCode(201)
                .body("titulo", equalTo("Trilha Auditor Fiscal"))
                .body("vinculadoATrilha", equalTo(true))
                .body("totalSecoes", equalTo(2))
                .body("totalNotas", equalTo(3))
                .extract().path("id");

        // Verifica que as seções e notas vazias foram criadas
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", caderno1Id)
                .when().get("/api/cadernos/{id}/secoes")
                .then()
                .statusCode(200)
                .body("$", hasSize(2))
                .body("titulo", hasItem("Língua Portuguesa"))
                .body("titulo", hasItem("Direito Administrativo"));

        // 2. Reimportação: Cria um novo caderno vinculado à trilha e desvincula o caderno antigo sem apagar
        String caderno2Id = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("trilhaId", trilhaId.toHexString())
                .when().post("/api/cadernos/importar-trilha/{trilhaId}")
                .then()
                .statusCode(201)
                .body("vinculadoATrilha", equalTo(true))
                .extract().path("id");

        assertNotEquals(caderno1Id, caderno2Id, "Reimportação deve gerar novo caderno com id distinto");

        // Caderno antigo permanece intacto, mas desvinculado
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", caderno1Id)
                .when().get(CADERNO)
                .then()
                .statusCode(200)
                .body("vinculadoATrilha", equalTo(false))
                .body("origemTrilhaId", nullValue())
                .body("secoes", hasSize(2));

        // 3. Sincronização explícita: Renomeia a trilha e adiciona nova disciplina
        trilhaRepository.atualizarMetadados(usuarioAId, trilhaId, new TrilhaRepository.MetadadosTrilha(
                "Trilha Auditor Fiscal 2027", "Auditor", "FGV", null, "#8F9E8B", Instant.now()));

        ObjectId disc3Id = new ObjectId();
        TopicoEmbutido topicoNovo = new TopicoEmbutido(new ObjectId().toHexString(), "Impostos Federais", 0);
        Disciplina disc3 = new Disciplina(disc3Id, usuarioAId, trilhaId, "Direito Tributário", "#10B981", 4, 2, null, List.of(topicoNovo), Instant.now(), Instant.now());
        disciplinaRepository.inserir(disc3);

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", caderno2Id)
                .when().post("/api/cadernos/{id}/sincronizar-trilha")
                .then()
                .statusCode(200)
                .body("cadernoId", equalTo(caderno2Id))
                .body("secoesCriadas", equalTo(1))
                .body("paginasCriadas", equalTo(1));

        // Título do caderno foi atualizado e nova seção está presente
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", caderno2Id)
                .when().get(CADERNO)
                .then()
                .statusCode(200)
                .body("titulo", equalTo("Trilha Auditor Fiscal 2027"))
                .body("secoes", hasSize(3))
                .body("secoes.titulo", hasItem("Direito Tributário"));
    }

    @Test
    @DisplayName("8.8 Desvinculação ao excluir trilha: cadernos e seções têm origemTrilhaId e origemDisciplinaId limpos")
    void deveDesvincularNotasCadernosESecoesAoExcluirTrilha() {
        ObjectId trilhaId = new ObjectId();
        ObjectId discId = new ObjectId();
        Disciplina disc = new Disciplina(discId, usuarioAId, trilhaId, "Economia", "#4A6B82", 2, 0, null, List.of(), Instant.now(), Instant.now());

        trilhaRepository.inserir(new TrilhaRepository.DadosInsercaoTrilha(
                usuarioAId, trilhaId, "Trilha Economia", "Economista", "FGV", null, "#8F9E8B", null, 0, null, Instant.now()));
        disciplinaRepository.inserir(disc);

        String cadernoId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("trilhaId", trilhaId.toHexString())
                .when().post("/api/cadernos/importar-trilha/{trilhaId}")
                .then().statusCode(201)
                .extract().path("id");

        // Exclui a trilha via TrilhaService
        trilhaRepository.excluir(usuarioAId, trilhaId);
        disciplinaRepository.excluirTodasDaTrilha(usuarioAId, trilhaId);
        cadernoRepository.desvincularTrilha(usuarioAId, trilhaId);
        secaoRepository.desvincularOrigemDisciplinaPorCaderno(usuarioAId, new ObjectId(cadernoId));

        // Caderno permanece ativo com origemTrilhaId nulo
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().get(CADERNO)
                .then()
                .statusCode(200)
                .body("vinculadoATrilha", equalTo(false))
                .body("origemTrilhaId", nullValue())
                .body("secoes[0].origemDisciplinaId", nullValue());
    }

    @Test
    @DisplayName("8.9 Lixeira hierárquica com loteExclusaoId e restauração automática de ancestrais")
    void deveGerenciarLixeiraHierarquicaComLoteExclusaoIdERestauracaoAutomaticaDeAncestrais() throws Exception {
        // 1. Cria Caderno com Seção e Nota
        String cadernoId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .body(jsonMapper.writeValueAsString(Map.of("titulo", "Caderno Lixeira")))
                .when().post(CADERNOS)
                .then().statusCode(201)
                .extract().path("id");

        String secaoId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().get(CADERNO)
                .then().statusCode(200)
                .extract().path("secoes[0].id");

        String notaId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .body(jsonMapper.writeValueAsString(Map.of(
                        "cadernoId", cadernoId,
                        "secaoId", secaoId,
                        "titulo", "Página na Lixeira",
                        "conteudoMarkdown", "Conteúdo teste")))
                .when().post(NOTAS)
                .then().statusCode(201)
                .extract().path("id");

        // 2. Exclui o Caderno: o conteúdo inteiro recebe o mesmo loteExclusaoId
        String loteExclusaoId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().delete(CADERNO)
                .then()
                .statusCode(200)
                .body("tipo", equalTo("CADERNO"))
                .body("loteExclusaoId", notNullValue())
                .extract().path("loteExclusaoId");

        // Lista na lixeira
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .when().get(NOTAS + "/lixeira")
                .then()
                .statusCode(200)
                .body("$", hasSize(3))
                .body("loteExclusaoId", everyItem(equalTo(loteExclusaoId)));

        // 3. Restaura apenas a Página/Nota: os ancestrais (seção e caderno) devem ser restaurados automaticamente!
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("tipo", "NOTA")
                .pathParam("id", notaId)
                .when().post(NOTAS + "/lixeira/{tipo}/{id}/restaurar")
                .then()
                .statusCode(200)
                .body("ancestralRestaurado", equalTo(true));

        // Caderno e página voltam a estar ativos
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().get(CADERNO)
                .then().statusCode(200);

        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", notaId)
                .when().get(NOTA)
                .then().statusCode(200);
    }

    @Test
    @DisplayName("8.10 Restauração de página cujo ancestral foi purgado definitivamente: move para caderno 'Restauradas'")
    void deveRealocarParaCadernoRestauradasQuandoAncestralJaFoiPurgado() throws Exception {
        String cadernoId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .body(jsonMapper.writeValueAsString(Map.of("titulo", "Caderno Purgado")))
                .when().post(CADERNOS)
                .then().statusCode(201)
                .extract().path("id");

        String secaoId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().get(CADERNO)
                .then().statusCode(200)
                .extract().path("secoes[0].id");

        String notaId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .body(jsonMapper.writeValueAsString(Map.of(
                        "cadernoId", cadernoId,
                        "secaoId", secaoId,
                        "titulo", "Página Órfã")))
                .when().post(NOTAS)
                .then().statusCode(201)
                .extract().path("id");

        // Move a nota para a lixeira
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", notaId)
                .when().delete(NOTA)
                .then().statusCode(200);

        // Purga definitivamente o caderno e a seção do banco
        cadernoRepository.esvaziarLixeira(usuarioAId);
        secaoRepository.esvaziarLixeira(usuarioAId);
        // Exclui direto do banco
        Caderno c = cadernoRepository.buscarAtivo(usuarioAId, new ObjectId(cadernoId)).orElse(null);
        if (c != null) {
            cadernoRepository.moverParaLixeira(usuarioAId, c.id(), "lote", Instant.now(), Instant.now().minus(1, ChronoUnit.DAYS));
            cadernoRepository.esvaziarLixeira(usuarioAId);
        }

        // Restaura a página: deve ser realocada para o caderno 'Restauradas'
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("tipo", "NOTA")
                .pathParam("id", notaId)
                .when().post(NOTAS + "/lixeira/{tipo}/{id}/restaurar")
                .then()
                .statusCode(200)
                .body("realocadoParaRestauradas", equalTo(true));

        // A página agora pode ser consultada ativamente
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", notaId)
                .when().get(NOTA)
                .then().statusCode(200);
    }

    @Test
    @DisplayName("8.11 Esvaziamento de lixeira e rejeição de restauração fora do prazo de 30 dias (404 TRASH_ITEM_EXPIRED)")
    void deveEsvaziarLixeiraERejeitarRestauracaoForaDoPrazoComTrashItemExpired() throws Exception {
        String cadernoId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .body(jsonMapper.writeValueAsString(Map.of("titulo", "Caderno Expiracao")))
                .when().post(CADERNOS)
                .then().statusCode(201)
                .extract().path("id");

        // Move para a lixeira
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("id", cadernoId)
                .when().delete(CADERNO)
                .then().statusCode(200);

        // Força expiração manual do item (> 30 dias atrás)
        Caderno c = cadernoRepository.buscarQualquerStatus(usuarioAId, new ObjectId(cadernoId)).orElseThrow();
        Caderno expirado = new Caderno(
                c.id(),
                c.usuarioId(),
                c.titulo(),
                c.cor(),
                c.icone(),
                c.origemTrilhaId(),
                Instant.now().minus(35, ChronoUnit.DAYS),
                Instant.now().minus(5, ChronoUnit.DAYS), // expirado há 5 dias
                c.loteExclusaoId(),
                c.criadoEm(),
                c.atualizadoEm()
        );
        cadernoRepository.atualizar(expirado);

        // Tentar restaurar item expirado responde 404 TRASH_ITEM_EXPIRED
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .pathParam("tipo", "CADERNO")
                .pathParam("id", cadernoId)
                .when().post(NOTAS + "/lixeira/{tipo}/{id}/restaurar")
                .then()
                .statusCode(404)
                .body("code", equalTo("TRASH_ITEM_EXPIRED"));

        // Executa o Job diário de purga automática
        int purgados = purgaLixeiraNotasJob.executarPurga(Instant.now());
        assertTrue(purgados >= 1, "Purga diária deve remover itens expirados da lixeira");

        // Esvazia lixeira manualmente
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .when().delete(NOTAS + "/lixeira/esvaziar")
                .then()
                .statusCode(200);
    }
}
