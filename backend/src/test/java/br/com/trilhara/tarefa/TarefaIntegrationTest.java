package br.com.trilhara.tarefa;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.trilhara.auth.Role;
import br.com.trilhara.auth.TokenService;
import br.com.trilhara.auth.UserRepository;
import br.com.trilhara.auth.Usuario;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/**
 * Contratos de tarefas manuais, recorrência e lixeira.
 *
 * <p>Cada teste é nomeado conforme o critério de aceite correspondente do guia de revisão.</p>
 */
@QuarkusTest
class TarefaIntegrationTest {

    private static final String LISTAS = "/api/listas-tarefas";
    private static final String TAREFAS = "/api/tarefas";
    private static final String SERIES = "/api/series-recorrentes";
    private static final String LIXEIRA = "/api/tarefas/lixeira";

    @Inject
    TokenService tokenService;

    @Inject
    UserRepository userRepository;

    @Inject
    ListaTarefasRepository listaRepository;

    @Inject
    TarefaRepository tarefaRepository;

    @Inject
    SerieRecorrenteRepository serieRepository;

    @Inject
    TarefaPurgaService purgaService;

    private String tokenA;
    private String usuarioA;
    private String tokenB;
    private String usuarioB;

    @BeforeEach
    void setUp() {
        usuarioA = novoUsuario("A");
        tokenA = tokenService.gerarAccessToken(userRepository.buscarPorId(usuarioA).orElseThrow());
        usuarioB = novoUsuario("B");
        tokenB = tokenService.gerarAccessToken(userRepository.buscarPorId(usuarioB).orElseThrow());
    }

    // ── Hoje somente pendentes ─────────────────────────────────────────

    @Test
    @DisplayName("hojeNaoContemConcluidas: GET /api/tarefas/hoje devolve apenas PENDENTE e deletedAt nulo")
    void hojeNaoContemConcluidas() {
        LocalDate hoje = LocalDate.now();
        String tarefaId = criarTarefa(tokenA, """
                {"titulo":"Pendente de hoje","prioridade":"MEDIA","dataVencimento":"%s"}
                """.formatted(hoje));

        given().header("Authorization", "Bearer " + tokenA)
                .contentType(ContentType.JSON)
                .body("{\"concluida\": true}")
                .when().patch(TAREFAS + "/" + tarefaId + "/conclusao")
                .then().statusCode(200);

        String outraId = criarTarefa(tokenA, """
                {"titulo":"Outra pendente","prioridade":"BAIXA","dataVencimento":"%s"}
                """.formatted(hoje));

        given().header("Authorization", "Bearer " + tokenA)
                .when().get(TAREFAS + "/hoje")
                .then().statusCode(200)
                .body("hoje", hasSize(1))
                .body("hoje[0].id", equalTo(outraId))
                .body("hoje[0].estado", equalTo("PENDENTE"))
                .body("atrasadas", hasSize(0))
                .body("concluidasHoje", hasSize(1))
                .body("concluidasHoje[0].id", equalTo(tarefaId))
                .body("concluidasHoje[0].estado", equalTo("CONCLUIDA"));
    }

    // ── TODA_A_SERIE preserva serieId das concluídas ─────────────────

    @Test
    @DisplayName("todaASeriePreservaSerieIdDasConcluidas: exclusão de série não desvincula concluídas")
    void todaASeriePreservaSerieIdDasConcluidas() {
        LocalDate hoje = LocalDate.now();
        String serieId = criarSerieDiaria(tokenA, hoje, 1);

        String ocorrenciaHoje = tarefaIdPorDataOriginal(serieId, hoje);
        given().header("Authorization", "Bearer " + tokenA)
                .contentType(ContentType.JSON)
                .body("{\"concluida\": true}")
                .when().patch(TAREFAS + "/" + ocorrenciaHoje + "/conclusao")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + tokenA)
                .when().delete(TAREFAS + "/" + ocorrenciaHoje + "?escopo=TODA_A_SERIE")
                .then().statusCode(204);

        given().header("Authorization", "Bearer " + tokenA)
                .when().get(TAREFAS + "/" + ocorrenciaHoje)
                .then().statusCode(200)
                .body("serieId", equalTo(serieId))
                .body("dataOriginalOcorrencia", equalTo(hoje.toString()))
                .body("concluida", equalTo(true));
    }

    // ── ESTA_E_FUTURAS na edição preserva as desvinculadas ───────────

    @Test
    @DisplayName("estaEFuturasEdicaoPreservaDesvinculadas: edição mantém ocorrência editada individualmente")
    void estaEFuturasEdicaoPreservaDesvinculadas() {
        LocalDate hoje = LocalDate.now();
        String serieId = criarSerieDiaria(tokenA, hoje, 1);

        LocalDate alvoDesvinculada = hoje.plusDays(3);
        String desvinculada = tarefaIdPorDataOriginal(serieId, alvoDesvinculada);

        given().header("Authorization", "Bearer " + tokenA)
                .contentType(ContentType.JSON)
                .queryParam("escopo", "ESTA")
                .body("{\"titulo\":\"Editada individualmente\"}")
                .when().put(TAREFAS + "/" + desvinculada)
                .then().statusCode(200)
                .body("desvinculadaDaSerie", equalTo(true));

        String ancora = tarefaIdPorDataOriginal(serieId, hoje.plusDays(1));

        given().header("Authorization", "Bearer " + tokenA)
                .contentType(ContentType.JSON)
                .queryParam("escopo", "ESTA_E_FUTURAS")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body("{\"titulo\":\"Nova regra daqui em diante\"}")
                .when().put(TAREFAS + "/" + ancora)
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + tokenA)
                .when().get(TAREFAS + "/" + desvinculada)
                .then().statusCode(200)
                .body("titulo", equalTo("Editada individualmente"))
                .body("desvinculadaDaSerie", equalTo(true));
    }

    // ── idempotência de ESTA_E_FUTURAS ───────────────────────────────

    @Test
    @DisplayName("estaEFuturasRetryComMesmaChaveNaoDuplicaSerie: retry devolve 200 e a mesma série")
    void estaEFuturasRetryComMesmaChaveNaoDuplicaSerie() {
        LocalDate hoje = LocalDate.now();
        String serieId = criarSerieDiaria(tokenA, hoje, 1);
        String ancora = tarefaIdPorDataOriginal(serieId, hoje.plusDays(2));
        String chave = UUID.randomUUID().toString();

        // Primeira execução: corta a série original e cria a sucessora (2 séries ativas no total).
        given().header("Authorization", "Bearer " + tokenA)
                .contentType(ContentType.JSON)
                .queryParam("escopo", "ESTA_E_FUTURAS")
                .header("Idempotency-Key", chave)
                .body("{\"titulo\":\"Rotina nova\"}")
                .when().put(TAREFAS + "/" + ancora)
                .then().statusCode(200);

        long seriesAposPrimeira = serieRepository.listarAtivas(new ObjectId(usuarioA)).size();
        String sucessoraId = serieRepository.buscarPorOrigemOperacaoId(new ObjectId(usuarioA), chave)
                .orElseThrow().id().toHexString();

        // Retry com a mesma chave: mesmo 200 e nenhuma série adicional.
        given().header("Authorization", "Bearer " + tokenA)
                .contentType(ContentType.JSON)
                .queryParam("escopo", "ESTA_E_FUTURAS")
                .header("Idempotency-Key", chave)
                .body("{\"titulo\":\"Rotina nova\"}")
                .when().put(TAREFAS + "/" + ancora)
                .then().statusCode(200)
                .body("serieId", equalTo(sucessoraId));

        assertEquals(seriesAposPrimeira, serieRepository.listarAtivas(new ObjectId(usuarioA)).size(),
                "Retry com a mesma Idempotency-Key não pode criar uma segunda série sucessora");
        assertTrue(serieRepository.buscarPorOrigemOperacaoId(new ObjectId(usuarioA), chave).isPresent(),
                "A chave de idempotência deve ficar gravada em origemOperacaoId");
    }

    @Test
    @DisplayName("estaEFuturasSemChaveRetorna400: falta de Idempotency-Key é IDEMPOTENCY_KEY_REQUIRED")
    void estaEFuturasSemChaveRetorna400() {
        LocalDate hoje = LocalDate.now();
        String serieId = criarSerieDiaria(tokenA, hoje, 1);
        String ancora = tarefaIdPorDataOriginal(serieId, hoje.plusDays(1));

        given().header("Authorization", "Bearer " + tokenA)
                .contentType(ContentType.JSON)
                .queryParam("escopo", "ESTA_E_FUTURAS")
                .body("{\"titulo\":\"Sem chave\"}")
                .when().put(TAREFAS + "/" + ancora)
                .then().statusCode(400)
                .body("code", equalTo("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("escopoAusenteEmTarefaRecorrenteRetorna400: escopo é obrigatório em ocorrência de série")
    void escopoAusenteEmTarefaRecorrenteRetorna400() {
        LocalDate hoje = LocalDate.now();
        String serieId = criarSerieDiaria(tokenA, hoje, 1);
        String ocorrencia = tarefaIdPorDataOriginal(serieId, hoje);

        given().header("Authorization", "Bearer " + tokenA)
                .contentType(ContentType.JSON)
                .body("{\"titulo\":\"Sem escopo\"}")
                .when().put(TAREFAS + "/" + ocorrencia)
                .then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));

        given().header("Authorization", "Bearer " + tokenA)
                .when().delete(TAREFAS + "/" + ocorrencia)
                .then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    // ── materialização concorrente e séries suspensas ─────────

    @Test
    @DisplayName("materializacaoConcorrenteNaoDuplicaNemRetorna500: 10 GETs paralelos não duplicam ocorrências")
    void materializacaoConcorrenteNaoDuplicaNemRetorna500() throws Exception {
        LocalDate hoje = LocalDate.now();
        criarSerieDiaria(tokenA, hoje, 1);

        int chamadas = 10;
        ExecutorService pool = Executors.newFixedThreadPool(chamadas);
        try {
            List<Callable<Integer>> consultas = new ArrayList<>();
            for (int i = 0; i < chamadas; i++) {
                consultas.add(() -> given().header("Authorization", "Bearer " + tokenA)
                        .when().get(TAREFAS + "/hoje")
                        .then().extract().statusCode());
            }
            List<Future<Integer>> futuros = pool.invokeAll(consultas);
            for (Future<Integer> futuro : futuros) {
                assertEquals(200, futuro.get(), "Nenhuma consulta concorrente pode devolver 500");
            }
        } finally {
            pool.shutdownNow();
        }

        // Sem duplicidade: uma ocorrência por (serieId, dataOriginalOcorrencia).
        List<String> datas = given().header("Authorization", "Bearer " + tokenA)
                .queryParam("tamanho", 100)
                .when().get(TAREFAS)
                .then().statusCode(200)
                .extract().jsonPath().getList("itens.dataOriginalOcorrencia");
        assertEquals(datas.size(), datas.stream().distinct().count(),
                "A materialização concorrente não pode duplicar ocorrências");
    }

    @Test
    @DisplayName("serieDeListaNaLixeiraNaoMaterializa: série suspensa enquanto a lista está na lixeira")
    void serieDeListaNaLixeiraNaoMaterializa() {
        LocalDate hoje = LocalDate.now();
        String listaId = criarLista(tokenA, "Lista suspensa");
        String serieId = criarSerieDiariaEmLista(tokenA, hoje, 1, listaId);

        long antes = tarefaRepository.contarPorSerie(new ObjectId(usuarioA), new ObjectId(serieId));
        assertTrue(antes > 0, "A série deve materializar a janela antes da suspensão");

        given().header("Authorization", "Bearer " + tokenA)
                .when().delete(LISTAS + "/" + listaId)
                .then().statusCode(200);

        // Amplia a janela consultada: nada novo é materializado enquanto a lista está na lixeira.
        given().header("Authorization", "Bearer " + tokenA)
                .queryParam("dataFim", hoje.plusDays(300).toString())
                .queryParam("tamanho", 100)
                .when().get(TAREFAS)
                .then().statusCode(200);

        long depois = tarefaRepository.contarPorSerie(new ObjectId(usuarioA), new ObjectId(serieId));
        assertEquals(antes, depois, "Série com lista na lixeira não pode materializar novas ocorrências");
    }

    @Test
    @DisplayName("purgaDeListaMoveSeriesParaListaGeral: séries sobreviventes ficam com listaId nulo")
    void purgaDeListaMoveSeriesParaListaGeral() {
        LocalDate hoje = LocalDate.now();
        String listaId = criarLista(tokenA, "Lista purgada");
        String serieId = criarSerieDiariaEmLista(tokenA, hoje, 1, listaId);

        given().header("Authorization", "Bearer " + tokenA)
                .when().delete(LISTAS + "/" + listaId)
                .then().statusCode(200);

        // A purga é parametrizada por instante (design do serviço): rodar 31 dias à frente
        // expira a lista na lixeira sem precisar alterar documentos à mão.
        purgaService.purgarExpiradas(Instant.now().plus(31, ChronoUnit.DAYS));

        assertTrue(listaRepository.buscarQualquerStatus(new ObjectId(listaId)).isEmpty(),
                "A lista expirada deve ser removida definitivamente");
        serieRepository.buscar(new ObjectId(usuarioA), new ObjectId(serieId))
                .ifPresent(serie -> assertNull(serie.listaId(),
                        "Série sobrevivente deve ir para a Lista Geral (listaId nulo)"));
    }

    // ── restauração ───────────────────────────────────────────────────

    @Test
    @DisplayName("restaurarTarefaComListaNaLixeiraRestauraSoContainer: irmãs do lote permanecem na lixeira")
    void restaurarTarefaComListaNaLixeiraRestauraSoContainer() {
        String listaId = criarLista(tokenA, "Lista com irmãs");
        String tarefa1 = criarTarefa(tokenA, """
                {"titulo":"Primeira","prioridade":"MEDIA","listaId":"%s"}
                """.formatted(listaId));
        String tarefa2 = criarTarefa(tokenA, """
                {"titulo":"Segunda","prioridade":"MEDIA","listaId":"%s"}
                """.formatted(listaId));

        given().header("Authorization", "Bearer " + tokenA)
                .when().delete(LISTAS + "/" + listaId)
                .then().statusCode(200)
                .body("tarefasAfetadas", equalTo(2));

        given().header("Authorization", "Bearer " + tokenA)
                .when().post(TAREFAS + "/lixeira/" + tarefa1 + "/restaurar")
                .then().statusCode(200)
                .body("tarefa.id", equalTo(tarefa1))
                .body("tarefa.listaId", equalTo(listaId))
                .body("realocadaParaListaGeral", equalTo(false));

        // O container voltou, mas a irmã continua na lixeira.
        given().header("Authorization", "Bearer " + tokenA)
                .when().get(LISTAS + "/" + listaId)
                .then().statusCode(200);
        given().header("Authorization", "Bearer " + tokenA)
                .when().get(TAREFAS + "/" + tarefa2)
                .then().statusCode(404);
    }

    @Test
    @DisplayName("restaurarTarefaComListaPurgadaRetornaFlag: listaId nulo e realocadaParaListaGeral true")
    void restaurarTarefaComListaPurgadaRetornaFlag() {
        String listaId = criarLista(tokenA, "Lista que será purgada");
        String tarefaId = criarTarefa(tokenA, """
                {"titulo":"Órfã","prioridade":"MEDIA","listaId":"%s"}
                """.formatted(listaId));

        given().header("Authorization", "Bearer " + tokenA)
                .when().delete(LISTAS + "/" + listaId)
                .then().statusCode(200);

        listaRepository.removerDefinitivamente(new ObjectId(usuarioA), new ObjectId(listaId));

        given().header("Authorization", "Bearer " + tokenA)
                .when().post(TAREFAS + "/lixeira/" + tarefaId + "/restaurar")
                .then().statusCode(200)
                .body("tarefa.id", equalTo(tarefaId))
                .body("tarefa.listaId", equalTo(null))
                .body("realocadaParaListaGeral", equalTo(true));
    }

    // ── Validação de referências e paginação ─────────────────────────

    @Test
    @DisplayName("listaIdDeOutroUsuarioRetorna400: lista de outro tenant não é aceita no body")
    void listaIdDeOutroUsuarioRetorna400() {
        String listaDoB = criarLista(tokenB, "Lista do B");

        given().header("Authorization", "Bearer " + tokenA)
                .contentType(ContentType.JSON)
                .body("""
                        {"titulo":"Invasora","prioridade":"MEDIA","listaId":"%s"}
                        """.formatted(listaDoB))
                .when().post(TAREFAS)
                .then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("paginacaoTarefasOrdenacaoEstavel: 50/100, totalItens e ordenação com _id como desempate")
    void paginacaoTarefasOrdenacaoEstavel() {
        String listaId = criarLista(tokenA, "Lista paginada");
        for (int i = 0; i < 7; i++) {
            criarTarefa(tokenA, """
                    {"titulo":"Tarefa %d","prioridade":"MEDIA","dataVencimento":"2026-03-01","ordem":%d,"listaId":"%s"}
                    """.formatted(i, i, listaId));
        }

        var pagina1 = given().header("Authorization", "Bearer " + tokenA)
                .queryParam("listaId", listaId)
                .queryParam("pagina", 1)
                .queryParam("tamanho", 3)
                .when().get(TAREFAS)
                .then().statusCode(200)
                .body("pagina", equalTo(1))
                .body("tamanho", equalTo(3))
                .body("totalItens", equalTo(7))
                .body("totalPaginas", equalTo(3))
                .body("itens", hasSize(3))
                .extract().jsonPath();

        var pagina2 = given().header("Authorization", "Bearer " + tokenA)
                .queryParam("listaId", listaId)
                .queryParam("pagina", 2)
                .queryParam("tamanho", 3)
                .when().get(TAREFAS)
                .then().statusCode(200)
                .body("itens", hasSize(3))
                .extract().jsonPath();

        assertEquals(0, pagina1.getInt("itens[0].ordem"));
        assertEquals(2, pagina1.getInt("itens[2].ordem"));
        assertEquals(3, pagina2.getInt("itens[0].ordem"));

        given().header("Authorization", "Bearer " + tokenA)
                .queryParam("tamanho", 101)
                .when().get(TAREFAS)
                .then().statusCode(400)
                .body("code", equalTo("INVALID_PAGINATION"));

        given().header("Authorization", "Bearer " + tokenA)
                .queryParam("pagina", 0)
                .when().get(TAREFAS)
                .then().statusCode(400)
                .body("code", equalTo("INVALID_PAGINATION"));
    }

    @Test
    @DisplayName("lixeiraPaginadaApenasItensVigentes: lixeira pagina e devolve metadados")
    void lixeiraPaginadaApenasItensVigentes() {
        String tarefa1 = criarTarefa(tokenA, "{\"titulo\":\"Del 1\",\"prioridade\":\"MEDIA\"}");
        criarTarefa(tokenA, "{\"titulo\":\"Del 2\",\"prioridade\":\"MEDIA\"}");

        given().header("Authorization", "Bearer " + tokenA)
                .when().delete(TAREFAS + "/" + tarefa1)
                .then().statusCode(204);

        given().header("Authorization", "Bearer " + tokenA)
                .queryParam("pagina", 1)
                .queryParam("tamanho", 50)
                .when().get(LIXEIRA)
                .then().statusCode(200)
                .body("tarefas", hasSize(1))
                .body("pagina", equalTo(1))
                .body("totalItens", equalTo(1))
                .body("totalPaginas", equalTo(1));
    }

    @Test
    @DisplayName("impactoExclusaoListaContaTarefasESeries: devolve contagens por conclusão")
    void impactoExclusaoListaContaTarefasESeries() {
        String listaId = criarLista(tokenA, "Lista com impacto");
        criarTarefa(tokenA, """
                {"titulo":"Ativa","prioridade":"MEDIA","listaId":"%s"}
                """.formatted(listaId));
        String aConcluir = criarTarefa(tokenA, """
                {"titulo":"Concluída","prioridade":"MEDIA","listaId":"%s"}
                """.formatted(listaId));

        given().header("Authorization", "Bearer " + tokenA)
                .contentType(ContentType.JSON)
                .body("{\"concluida\": true}")
                .when().patch(TAREFAS + "/" + aConcluir + "/conclusao")
                .then().statusCode(200);

        // Sem séries: as contagens refletem apenas as duas tarefas manuais.
        given().header("Authorization", "Bearer " + tokenA)
                .when().get(LISTAS + "/" + listaId + "/impacto-exclusao")
                .then().statusCode(200)
                .body("id", equalTo(listaId))
                .body("tarefasAtivas", equalTo(1))
                .body("tarefasConcluidas", equalTo(1))
                .body("seriesVinculadas", equalTo(0));

        // Uma série vinculada passa a ser contada em seriesVinculadas.
        criarSerieDiariaEmLista(tokenA, LocalDate.now(), 1, listaId);

        given().header("Authorization", "Bearer " + tokenA)
                .when().get(LISTAS + "/" + listaId + "/impacto-exclusao")
                .then().statusCode(200)
                .body("seriesVinculadas", equalTo(1));
    }

    // ── Isolamento A×B em todas as rotas ────────────────────────────────────

    @Test
    @DisplayName("isolamentoEntreUsuariosEmTodasAsRotas: A nunca lê nem altera dados de B (404)")
    void isolamentoEntreUsuariosEmTodasAsRotas() {
        String tarefaDoB = criarTarefa(tokenB, "{\"titulo\":\"Do B\",\"prioridade\":\"MEDIA\"}");
        String listaDoB = criarLista(tokenB, "Lista privada de B");
        String serieDoB = criarSerieDiaria(tokenB, LocalDate.now(), 1);

        given().header("Authorization", "Bearer " + tokenA)
                .when().get(TAREFAS + "/" + tarefaDoB).then().statusCode(404);
        given().header("Authorization", "Bearer " + tokenA)
                .when().get(LISTAS + "/" + listaDoB).then().statusCode(404);
        given().header("Authorization", "Bearer " + tokenA)
                .when().get(SERIES + "/" + serieDoB).then().statusCode(404);
        given().header("Authorization", "Bearer " + tokenA)
                .contentType(ContentType.JSON)
                .body("{\"titulo\":\"Invasão\"}")
                .when().put(TAREFAS + "/" + tarefaDoB).then().statusCode(404);
        given().header("Authorization", "Bearer " + tokenA)
                .when().delete(TAREFAS + "/" + tarefaDoB).then().statusCode(404);
        given().header("Authorization", "Bearer " + tokenA)
                .when().delete(LISTAS + "/" + listaDoB).then().statusCode(404);
        given().header("Authorization", "Bearer " + tokenA)
                .when().delete(SERIES + "/" + serieDoB).then().statusCode(404);
        given().header("Authorization", "Bearer " + tokenA)
                .when().get(LISTAS + "/" + listaDoB + "/impacto-exclusao").then().statusCode(404);

        given().when().get(TAREFAS).then().statusCode(401);

        given().header("Authorization", "Bearer " + tokenB)
                .when().get(TAREFAS + "/" + tarefaDoB)
                .then().statusCode(200)
                .body("titulo", equalTo("Do B"));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String novoUsuario(String rotulo) {
        String id = new ObjectId().toHexString();
        Instant agora = Instant.now();
        userRepository.inserir(new Usuario(
                id,
                "Usuario " + rotulo,
                "u-" + UUID.randomUUID() + "@teste.com",
                "hash",
                List.of(Role.USER),
                true,
                true,
                0,
                null,
                agora,
                agora,
                agora));
        return id;
    }

    private String criarLista(String token, String nome) {
        return given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"nome\":\"%s\"}".formatted(nome))
                .when().post(LISTAS)
                .then().statusCode(201)
                .extract().path("id");
    }

    private String criarTarefa(String token, String json) {
        return given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(json)
                .when().post(TAREFAS)
                .then().statusCode(201)
                .extract().path("id");
    }

    private String criarSerieDiaria(String token, LocalDate inicio, int intervalo) {
        return criarSerieDiariaEmLista(token, inicio, intervalo, null);
    }

    private String criarSerieDiariaEmLista(String token, LocalDate inicio, int intervalo, String listaId) {
        String corpo = listaId == null
                ? """
                  {"titulo":"Rotina diária","prioridade":"MEDIA","frequencia":"DIARIO",
                   "intervalo":%d,"tipoTermino":"NUNCA","dataInicio":"%s"}
                  """.formatted(intervalo, inicio)
                : """
                  {"titulo":"Rotina diária","prioridade":"MEDIA","frequencia":"DIARIO",
                   "intervalo":%d,"tipoTermino":"NUNCA","dataInicio":"%s","listaId":"%s"}
                  """.formatted(intervalo, inicio, listaId);

        String serieId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(corpo)
                .when().post(SERIES)
                .then().statusCode(201)
                .body("id", notNullValue())
                .extract().path("id");
        assertNotNull(serieId);
        return serieId;
    }

    private String tarefaIdPorDataOriginal(String serieId, LocalDate data) {
        return tarefaRepository
                .buscarPorOcorrencia(new ObjectId(usuarioA), new ObjectId(serieId), data)
                .orElseThrow(() -> new AssertionError("Ocorrência de " + data + " não materializada"))
                .id().toHexString();
    }
}
