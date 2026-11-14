package br.com.trilhara.cronograma;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoClient;

import br.com.trilhara.auth.TokenService;
import br.com.trilhara.auth.UserRepository;
import br.com.trilhara.auth.Usuario;
import br.com.trilhara.cronograma.dto.BlocoCronogramaItemDTO;
import br.com.trilhara.cronograma.dto.ConcluirAtrasadosResponse;
import br.com.trilhara.cronograma.dto.CronogramaDTO;
import br.com.trilhara.cronograma.dto.PaginaBlocosDTO;
import br.com.trilhara.shared.TimezoneContext;
import br.com.trilhara.trilha.Disciplina;
import br.com.trilhara.trilha.DisciplinaRepository;
import br.com.trilhara.trilha.TrilhaRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/**
 * Testes de integração para geração, publicação e blocos de cronogramas.
 */
@QuarkusTest
@DisplayName("Cronogramas normalizados: geradores, blocos, lote e ponteiro")
class CronogramaPublicacaoIntegrationTest {

    private static final String API_TRILHAS = "/api/trilhas";
    private static final String API_BLOCOS = "/api/blocos";

    @Inject
    TrilhaRepository trilhaRepository;

    @Inject
    DisciplinaRepository disciplinaRepository;

    @Inject
    CronogramaRepository cronogramaRepository;

    @Inject
    BlocoCronogramaRepository blocoCronogramaRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    TokenService tokenService;

    @Inject
    MongoClient mongoClient;

    private String tokenPara(ObjectId usuarioId) {
        Usuario u = new Usuario(
                usuarioId.toHexString(),
                "Usuario ",
                "r5-" + usuarioId.toHexString() + "@trilhara.com.br",
                "$pbkdf2-sha512$mock",
                List.of(br.com.trilhara.auth.Role.USER),
                true, true, 0, null, null,
                Instant.now(), Instant.now());
        userRepository.inserir(u);
        return tokenService.gerarAccessToken(u);
    }

    private record TrilhaInfo(String trilhaId, List<Disciplina> disciplinas) {}

    private TrilhaInfo criarTrilha(ObjectId usuarioId, String token) {
        String payload = """
                {
                  "nome": "Trilha %s",
                  "cargo": "Auditor",
                  "banca": "CEBRASPE",
                  "dataProva": "2027-12-15",
                  "cor": "#4F46E5",
                  "disciplinasIniciais": [
                    { "nome": "Portugues", "cor": "#3B82F6", "peso": 1 },
                    { "nome": "Matematica", "cor": "#10B981", "peso": 5 }
                  ]
                }
                """.formatted(UUID.randomUUID());

        String trilhaId = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(API_TRILHAS)
                .then()
                .statusCode(201)
                .extract().path("id");

        List<Disciplina> disciplinas = disciplinaRepository.listarPorTrilha(usuarioId, new ObjectId(trilhaId));
        return new TrilhaInfo(trilhaId, disciplinas);
    }

    private String corpo(String modalidade, LocalDate inicio, LocalDate dataFim, int blocosPorDia,
                         List<Integer> dias, TrilhaInfo info) {
        String fim = dataFim != null ? "\"dataFim\": \"" + dataFim + "\"," : "";
        return """
                {
                  "modalidade": "%s",
                  "duracaoBlocoMinutos": 60,
                  "tipoPeriodo": "UM_MES",
                  "dataInicio": "%s",
                  %s
                  "diasAtivosSemana": %s,
                  "blocosPorDia": %d,
                  "disciplinasParticipantesIds": ["%s", "%s"]
                }
                """.formatted(modalidade, inicio, fim, dias, blocosPorDia,
                info.disciplinas().get(0).idHex(), info.disciplinas().get(1).idHex());
    }

    /** Determinismo: mesma configuração e mesmos dados produzem a mesma sequência de blocos. */
    @Test
    @DisplayName("geradoresDeterministicos: os modos são puros e reprodutíveis")
    void geradoresDeterministicos() {
        ObjectId uId = new ObjectId();
        String token = tokenPara(uId);
        TrilhaInfo info = criarTrilha(uId, token);
        LocalDate inicio = LocalDate.now(TimezoneContext.DEFAULT_ZONE);

        for (String modalidade : List.of("SEQUENCIAL", "PONDERADO")) {
            CronogramaDTO c = publicar(token, info, corpo(modalidade, inicio, null, 2,
                    List.of(1, 2, 3, 4, 5), info));
            List<BlocoCronogramaItemDTO> primeira = listarTodos(token, info.trilhaId(), c.id());

            CronogramaDTO regerado = given()
                    .header("Authorization", "Bearer " + token)
                    .contentType(ContentType.JSON)
                    .body(corpo(modalidade, inicio, null, 2, List.of(1, 2, 3, 4, 5), info))
                    .when()
                    .put(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas/" + c.id())
                    .then()
                    .statusCode(200)
                    .extract().as(CronogramaDTO.class);

            List<BlocoCronogramaItemDTO> segunda = listarTodos(token, info.trilhaId(), regerado.id());
            assertEquals(primeira.size(), segunda.size(), modalidade + ": quantidade de blocos");
            for (int i = 0; i < primeira.size(); i++) {
                assertEquals(primeira.get(i).data(), segunda.get(i).data(), modalidade + ": data[" + i + "]");
                assertEquals(primeira.get(i).ordem(), segunda.get(i).ordem(), modalidade + ": ordem[" + i + "]");
                assertEquals(primeira.get(i).disciplinaId(), segunda.get(i).disciplinaId(),
                        modalidade + ": disciplina[" + i + "]");
            }
            assertNotEquals(primeira.get(0).id(), segunda.get(0).id());
        }
    }

    /** Manual também é reprodutível e respeita a semana-modelo configurada. */
    @Test
    @DisplayName("geradoresDeterministicos: MANUAL repete a semana-modelo")
    void manualRepeteSemanaModelo() {
        ObjectId uId = new ObjectId();
        String token = tokenPara(uId);
        TrilhaInfo info = criarTrilha(uId, token);
        LocalDate inicio = LocalDate.now(TimezoneContext.DEFAULT_ZONE);
        String d1 = info.disciplinas().get(0).idHex();
        String d2 = info.disciplinas().get(1).idHex();

        String payload = """
                {
                  "modalidade": "MANUAL",
                  "duracaoBlocoMinutos": 60,
                  "tipoPeriodo": "UMA_SEMANA",
                  "dataInicio": "%s",
                  "disciplinasParticipantesIds": ["%s", "%s"],
                  "semanaModeloManual": { "1": ["%s"], "3": ["%s", "%s"] }
                }
                """.formatted(inicio, d1, d2, d1, d1, d2);

        CronogramaDTO c = publicar(token, info, payload);
        List<BlocoCronogramaItemDTO> blocos = listarTodos(token, info.trilhaId(), c.id());

        assertFalse(blocos.isEmpty());
        for (BlocoCronogramaItemDTO b : blocos) {
            int diaSemana = LocalDate.parse(b.data()).getDayOfWeek().getValue();
            assertTrue(diaSemana == 1 || diaSemana == 3, "dia inesperado: " + b.data());
        }
        long segundas = blocos.stream()
                .filter(b -> LocalDate.parse(b.data()).getDayOfWeek().getValue() == 1).count();
        long quartas = blocos.stream()
                .filter(b -> LocalDate.parse(b.data()).getDayOfWeek().getValue() == 3).count();
        assertEquals(segundas * 2, quartas, "quarta tem o dobro de blocos da segunda");
    }

    /** Proporção do ponderado respeita o déficit: pesos 1 e 5 → razão 1:5 (±1 bloco). */
    @Test
    @DisplayName("ponderadoRespeitaDeficit: proporção por peso dentro de ±1 bloco")
    void ponderadoRespeitaDeficit() {
        ObjectId uId = new ObjectId();
        String token = tokenPara(uId);
        TrilhaInfo info = criarTrilha(uId, token);
        LocalDate inicio = LocalDate.now(TimezoneContext.DEFAULT_ZONE);

        CronogramaDTO c = publicar(token, info, corpo("PONDERADO", inicio, inicio.plusDays(13), 2,
                List.of(1, 2, 3), info));

        List<BlocoCronogramaItemDTO> blocos = listarTodos(token, info.trilhaId(), c.id());
        String peso1 = info.disciplinas().get(0).idHex();
        long daPeso1 = blocos.stream().filter(b -> b.disciplinaId().equals(peso1)).count();
        long daPeso5 = blocos.size() - daPeso1;

        double esperadoPeso1 = blocos.size() * 1.0 / 6.0;
        double esperadoPeso5 = blocos.size() * 5.0 / 6.0;
        assertTrue(Math.abs(daPeso1 - esperadoPeso1) <= 1,
                "disciplina de peso 1 deveria ter ~" + esperadoPeso1 + " blocos, tem " + daPeso1);
        assertTrue(Math.abs(daPeso5 - esperadoPeso5) <= 1,
                "disciplina de peso 5 deveria ter ~" + esperadoPeso5 + " blocos, tem " + daPeso5);
    }

    /** Horizonte máximo de 366 dias e teto de 24 blocos por dia. */
    @Test
    @DisplayName("limite366DiasE24PorDia: clamp de horizonte e teto diário")
    void limite366DiasE24PorDia() {
        ObjectId uId = new ObjectId();
        String token = tokenPara(uId);
        TrilhaInfo info = criarTrilha(uId, token);
        LocalDate inicio = LocalDate.now(TimezoneContext.DEFAULT_ZONE);

        CronogramaDTO c = publicar(token, info, corpo("SEQUENCIAL", inicio, inicio.plusDays(1000), 1,
                List.of(1), info));
        assertEquals(inicio.plusDays(365).toString(), c.dataFim(),
                "dataFim deve ser truncada em +365 dias");

        // 24 blocos por dia é aceito; 25 é rejeitado com 400 VALIDATION_ERROR
        publicar(token, info, corpo("PONDERADO", inicio, inicio.plusDays(1), 24, List.of(1), info));
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(corpo("MANUAL", inicio, null, 25, List.of(1), info))
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    /** Concorrência: 2 gerações simultâneas → 201 + 409 e exatamente um ativo. */
    @Test
    @DisplayName("concorrenciaGeracao: 201/409 e exatamente 1 cronograma ativo")
    void concorrenciaGeracao() {
        ObjectId uId = new ObjectId();
        String token = tokenPara(uId);
        TrilhaInfo info = criarTrilha(uId, token);
        LocalDate inicio = LocalDate.now(TimezoneContext.DEFAULT_ZONE);

        // Lease de outro worker ativo: a geração concorrente deve ser recusada com 409
        ObjectId trilhaObjId = new ObjectId(info.trilhaId());
        String tokenOutro = UUID.randomUUID().toString();
        trilhaRepository.adquirirLease(uId, trilhaObjId, tokenOutro, Instant.now().plusSeconds(60));

        String corpoSeq = corpo("SEQUENCIAL", inicio, null, 2, List.of(1, 2, 3), info);
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(corpoSeq)
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas")
                .then()
                .statusCode(409)
                .body("code", equalTo("SCHEDULE_GENERATION_IN_PROGRESS"));

        // Liberado o lease, a mesma requisição cria (201) e há exatamente um ativo
        trilhaRepository.liberarLease(uId, trilhaObjId, tokenOutro);
        publicar(token, info, corpoSeq);

        long ativos = cronogramaRepository.listarPorTrilha(uId, trilhaObjId)
                .stream().filter(Cronograma::ativo).count();
        assertEquals(1, ativos, "exatamente um cronograma ativo por trilha");
    }

    /** Modalidade duplicada não altera blocos: contagem e IDs permanecem iguais. */
    @Test
    @DisplayName("duplicateModalityNaoAlteraBlocos: 409 sem tocar nos blocos atuais")
    void duplicateModalityNaoAlteraBlocos() {
        ObjectId uId = new ObjectId();
        String token = tokenPara(uId);
        TrilhaInfo info = criarTrilha(uId, token);
        LocalDate inicio = LocalDate.now(TimezoneContext.DEFAULT_ZONE);

        CronogramaDTO c = publicar(token, info, corpo("SEQUENCIAL", inicio, null, 2,
                List.of(1, 2, 3), info));
        List<BlocoCronogramaItemDTO> antes = listarTodos(token, info.trilhaId(), c.id());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(corpo("SEQUENCIAL", inicio, null, 2, List.of(1, 2, 3), info))
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas")
                .then()
                .statusCode(409)
                .body("code", equalTo("DUPLICATE_MODALITY"));

        List<BlocoCronogramaItemDTO> depois = listarTodos(token, info.trilhaId(), c.id());
        assertEquals(antes.size(), depois.size(), "quantidade de blocos não muda");
        assertEquals(
                antes.stream().map(BlocoCronogramaItemDTO::id).toList(),
                depois.stream().map(BlocoCronogramaItemDTO::id).toList(),
                "ids dos blocos não mudam");
    }

    /** o lote nunca inclui o dia de hoje. */
    @Test
    @DisplayName("concluirAtrasadosNaoIncluiHoje: filtro estrito data < hojeLocal")
    void concluirAtrasadosNaoIncluiHoje() {
        ObjectId uId = new ObjectId();
        String token = tokenPara(uId);
        TrilhaInfo info = criarTrilha(uId, token);
        LocalDate hoje = LocalDate.now(TimezoneContext.DEFAULT_ZONE);

        CronogramaDTO c = publicar(token, info, corpo("SEQUENCIAL", hoje.minusDays(3), null, 2,
                List.of(1, 2, 3, 4, 5, 6, 7), info));

        ConcluirAtrasadosResponse res = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"operationId\": \"%s\"}".formatted(UUID.randomUUID()))
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas/" + c.id() + "/blocos/concluir-atrasados")
                .then()
                .statusCode(200)
                .extract().as(ConcluirAtrasadosResponse.class);

        assertTrue(res.totalConcluidos() > 0);

        PaginaBlocosDTO deHoje = given()
                .header("Authorization", "Bearer " + token)
                .queryParam("statusData", "HOJE")
                .queryParam("tamanho", 100)
                .when()
                .get(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas/" + c.id() + "/blocos")
                .then()
                .statusCode(200)
                .extract().as(PaginaBlocosDTO.class);

        assertFalse(deHoje.itens().isEmpty());
        assertTrue(deHoje.itens().stream().noneMatch(BlocoCronogramaItemDTO::concluido),
                "o lote não pode concluir blocos de hoje");
    }

    /** retry recalcula e não duplica conclusões. */
    @Test
    @DisplayName("concluirAtrasadosRetryNaoDuplica: segunda chamada não reprocessa")
    void concluirAtrasadosRetryNaoDuplica() {
        ObjectId uId = new ObjectId();
        String token = tokenPara(uId);
        TrilhaInfo info = criarTrilha(uId, token);
        LocalDate hoje = LocalDate.now(TimezoneContext.DEFAULT_ZONE);

        CronogramaDTO c = publicar(token, info, corpo("SEQUENCIAL", hoje.minusDays(2), null, 1,
                List.of(1, 2, 3, 4, 5, 6, 7), info));

        ConcluirAtrasadosResponse primeira = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"operationId\": \"%s\"}".formatted(UUID.randomUUID()))
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas/" + c.id() + "/blocos/concluir-atrasados")
                .then()
                .statusCode(200)
                .extract().as(ConcluirAtrasadosResponse.class);

        ConcluirAtrasadosResponse segunda = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"operationId\": \"%s\"}".formatted(UUID.randomUUID()))
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas/" + c.id() + "/blocos/concluir-atrasados")
                .then()
                .statusCode(200)
                .extract().as(ConcluirAtrasadosResponse.class);

        assertTrue(primeira.totalConcluidos() > 0);
        assertEquals(0, segunda.totalProcessados(), "nada pendente sobra para o retry");
        assertEquals(0, segunda.totalConcluidos());
    }

    /** lote concorrente com lease ocupado responde 409. */
    @Test
    @DisplayName("concluirAtrasadosParaleloRetorna409: lease ocupado bloqueia o lote")
    void concluirAtrasadosParaleloRetorna409() {
        ObjectId uId = new ObjectId();
        String token = tokenPara(uId);
        TrilhaInfo info = criarTrilha(uId, token);
        LocalDate hoje = LocalDate.now(TimezoneContext.DEFAULT_ZONE);

        CronogramaDTO c = publicar(token, info, corpo("SEQUENCIAL", hoje.minusDays(2), null, 1,
                List.of(1, 2, 3, 4, 5, 6, 7), info));

        ObjectId trilhaObjId = new ObjectId(info.trilhaId());
        String tokenOutro = UUID.randomUUID().toString();
        trilhaRepository.adquirirLease(uId, trilhaObjId, tokenOutro, Instant.now().plusSeconds(60));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"operationId\": \"%s\"}".formatted(UUID.randomUUID()))
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas/" + c.id() + "/blocos/concluir-atrasados")
                .then()
                .statusCode(409)
                .body("code", equalTo("SCHEDULE_GENERATION_IN_PROGRESS"));

        trilhaRepository.liberarLease(uId, trilhaObjId, tokenOutro);
    }

    /** a contagem exata de atrasados vem de totalItens. */
    @Test
    @DisplayName("statusDataAtrasadoTotalItensExato: totalItens independe da página")
    void statusDataAtrasadoTotalItensExato() {
        ObjectId uId = new ObjectId();
        String token = tokenPara(uId);
        TrilhaInfo info = criarTrilha(uId, token);
        LocalDate hoje = LocalDate.now(TimezoneContext.DEFAULT_ZONE);

        CronogramaDTO c = publicar(token, info, corpo("SEQUENCIAL", hoje.minusDays(3), null, 2,
                List.of(1, 2, 3, 4, 5, 6, 7), info));

        PaginaBlocosDTO umaUnidade = given()
                .header("Authorization", "Bearer " + token)
                .queryParam("statusData", "ATRASADO")
                .queryParam("concluido", false)
                .queryParam("tamanho", 1)
                .when()
                .get(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas/" + c.id() + "/blocos")
                .then()
                .statusCode(200)
                .extract().as(PaginaBlocosDTO.class);

        PaginaBlocosDTO paginaCheia = given()
                .header("Authorization", "Bearer " + token)
                .queryParam("statusData", "ATRASADO")
                .queryParam("concluido", false)
                .queryParam("tamanho", 100)
                .when()
                .get(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas/" + c.id() + "/blocos")
                .then()
                .statusCode(200)
                .extract().as(PaginaBlocosDTO.class);

        assertTrue(umaUnidade.totalItens() > 0);
        assertEquals(1, umaUnidade.itens().size(), "tamanho=1 devolve um item");
        assertTrue(paginaCheia.itens().stream()
                .allMatch(b -> LocalDate.parse(b.data()).isBefore(hoje)),
                "todos os atrasados estão estritamente antes de hoje");
        assertEquals(paginaCheia.totalItens(), umaUnidade.totalItens(), "totalItens é o mesmo");
        assertEquals(umaUnidade.totalItens(), (long) paginaCheia.itens().size());
    }

    /** só `concluido` é aceito no PATCH; qualquer outro campo falha com 400. */
    @Test
    @DisplayName("patchConclusaoAceitaSomenteConcluido: propriedade desconhecida é 400")
    void patchConclusaoAceitaSomenteConcluido() {
        ObjectId uId = new ObjectId();
        String token = tokenPara(uId);
        TrilhaInfo info = criarTrilha(uId, token);
        LocalDate inicio = LocalDate.now(TimezoneContext.DEFAULT_ZONE);

        CronogramaDTO c = publicar(token, info, corpo("SEQUENCIAL", inicio, null, 1, List.of(1), info));
        String blocoId = listarTodos(token, info.trilhaId(), c.id()).get(0).id();

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"concluido\": true, \"status\": \"CANCELADO\"}")
                .when()
                .patch(API_BLOCOS + "/" + blocoId + "/conclusao")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));

        // O bloco continua pendente após o body inválido
        assertFalse(blocoCronogramaRepository.buscarPorId(uId, new ObjectId(blocoId)).orElseThrow().concluido());
    }

    /** bloco que não pertence à versão publicada responde 404. */
    @Test
    @DisplayName("blocoDeVersaoAntigaRetorna404: leitura sempre pela versão do ponteiro")
    void blocoDeVersaoAntigaRetorna404() {
        ObjectId uId = new ObjectId();
        String token = tokenPara(uId);
        TrilhaInfo info = criarTrilha(uId, token);
        LocalDate inicio = LocalDate.now(TimezoneContext.DEFAULT_ZONE);

        CronogramaDTO c = publicar(token, info, corpo("SEQUENCIAL", inicio, null, 1, List.of(1), info));
        String blocoAntigoId = listarTodos(token, info.trilhaId(), c.id()).get(0).id();

        // Regenerar descarta a versão anterior e publica uma nova
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(corpo("SEQUENCIAL", inicio, null, 2, List.of(1), info))
                .when()
                .put(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas/" + c.id())
                .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"concluido\": true}")
                .when()
                .patch(API_BLOCOS + "/" + blocoAntigoId + "/conclusao")
                .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
    }

    /** Paginação: `tamanho` acima de 100 é 400 INVALID_PAGINATION. */
    @Test
    @DisplayName("paginacaoBlocosLimite100Retorna400: teto de página é 100")
    void paginacaoBlocosLimite100Retorna400() {
        ObjectId uId = new ObjectId();
        String token = tokenPara(uId);
        TrilhaInfo info = criarTrilha(uId, token);
        LocalDate inicio = LocalDate.now(TimezoneContext.DEFAULT_ZONE);

        CronogramaDTO c = publicar(token, info, corpo("SEQUENCIAL", inicio, null, 1, List.of(1), info));

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("tamanho", 101)
                .when()
                .get(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas/" + c.id() + "/blocos")
                .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_PAGINATION"));

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("pagina", 0)
                .when()
                .get(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas/" + c.id() + "/blocos")
                .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_PAGINATION"));

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("tamanho", 100)
                .when()
                .get(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas/" + c.id() + "/blocos")
                .then()
                .statusCode(200);
    }

    /** o endpoint Hoje respeita a versão publicada no ponteiro da trilha. */
    @Test
    @DisplayName("blocosHojeRespeitamVersaoDoPonteiro: staging órfão nunca aparece")
    void blocosHojeRespeitamVersaoDoPonteiro() {
        ObjectId uId = new ObjectId();
        String token = tokenPara(uId);
        TrilhaInfo info = criarTrilha(uId, token);
        LocalDate hoje = LocalDate.now(TimezoneContext.DEFAULT_ZONE);
        ObjectId trilhaObjId = new ObjectId(info.trilhaId());

        CronogramaDTO c = publicar(token, info, corpo("SEQUENCIAL", hoje, null, 2, List.of(1, 2, 3, 4, 5, 6, 7), info));

        int esperado = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(API_BLOCOS + "/hoje")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("$").size();
        assertEquals(2, esperado, "dois blocos de hoje publicados");

        // Staging órfão: bloco da mesma data numa versão que não está no ponteiro
        var ponteiro = trilhaRepository.buscarEntidade(uId, trilhaObjId).orElseThrow();
        long versaoAtiva = ponteiro.getLong("versaoAtivaCronograma");
        blocoCronogramaRepository.inserirLote(List.of(new BlocoCronograma(
                new ObjectId(), uId, trilhaObjId, new ObjectId(c.id()),
                versaoAtiva + 10, hoje, 1,
                info.disciplinas().get(0).id(), "Fantasma", "#000000", 60,
                false, null, Instant.now(), Instant.now())));

        int depois = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(API_BLOCOS + "/hoje")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("$").size();
        assertEquals(esperado, depois, "staging órfão não entra na visão Hoje");
    }

    /** §5.3: renomear disciplina atualiza o snapshot apenas nos cronogramas ativos. */
    @Test
    @DisplayName("snapshotAtualizaSomenteCronogramasAtivos: inativo é histórico visual")
    void snapshotAtualizaSomenteCronogramasAtivos() {
        ObjectId uId = new ObjectId();
        String token = tokenPara(uId);
        TrilhaInfo info = criarTrilha(uId, token);
        LocalDate inicio = LocalDate.now(TimezoneContext.DEFAULT_ZONE);

        // Cronograma 1 (SEQUENCIAL) é criado e depois desativado por um novo cronograma
        CronogramaDTO inativo = publicar(token, info, corpo("SEQUENCIAL", inicio, null, 2, List.of(1), info));
        CronogramaDTO ativo = publicar(token, info, corpo("PONDERADO", inicio, null, 2, List.of(1), info));

        List<BlocoCronogramaItemDTO> blocosInativosAntes = listarTodos(token, info.trilhaId(), inativo.id());
        String nomeAntigoInativo = blocosInativosAntes.get(0).disciplinaNome();

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        { "nome": "Portugues Renomeado", "cor": "#111111", "peso": 1 }
                        """)
                .when()
                .put(API_TRILHAS + "/" + info.trilhaId() + "/disciplinas/" + info.disciplinas().get(0).idHex())
                .then()
                .statusCode(200);

        List<BlocoCronogramaItemDTO> blocosAtivos = listarTodos(token, info.trilhaId(), ativo.id());
        List<BlocoCronogramaItemDTO> blocosInativosDepois = listarTodos(token, info.trilhaId(), inativo.id());

        // No cronograma ativo o snapshot foi atualizado; no inativo permanece o histórico
        assertTrue(blocosAtivos.stream()
                        .filter(b -> b.disciplinaId().equals(info.disciplinas().get(0).idHex()))
                        .allMatch(b -> b.disciplinaNome().equals("Portugues Renomeado")),
                "cronograma ativo recebe o novo nome");
        assertTrue(blocosInativosDepois.stream()
                        .filter(b -> b.disciplinaId().equals(info.disciplinas().get(0).idHex()))
                        .allMatch(b -> b.disciplinaNome().equals(nomeAntigoInativo)),
                "cronograma inativo preserva o snapshot anterior");

        // disciplinaId nunca muda
        assertTrue(blocosAtivos.stream()
                .allMatch(b -> b.disciplinaId().equals(info.disciplinas().get(0).idHex())
                        || b.disciplinaId().equals(info.disciplinas().get(1).idHex())));
    }
    private CronogramaDTO publicar(String token, TrilhaInfo info, String corpo) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(corpo)
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas")
                .then()
                .statusCode(201)
                .extract().as(CronogramaDTO.class);
    }

    private List<BlocoCronogramaItemDTO> listarTodos(String token, String trilhaId, String cronogramaId) {
        List<BlocoCronogramaItemDTO> acumulado = new ArrayList<>();
        int pagina = 1;
        int totalPaginas;
        do {
            PaginaBlocosDTO p = given()
                    .header("Authorization", "Bearer " + token)
                    .queryParam("pagina", pagina)
                    .queryParam("tamanho", 100)
                    .when()
                    .get(API_TRILHAS + "/" + trilhaId + "/cronogramas/" + cronogramaId + "/blocos")
                    .then()
                    .statusCode(200)
                    .extract().as(PaginaBlocosDTO.class);
            acumulado.addAll(p.itens());
            totalPaginas = p.totalPaginas();
            pagina++;
        } while (pagina <= totalPaginas);
        return acumulado;
    }


}
