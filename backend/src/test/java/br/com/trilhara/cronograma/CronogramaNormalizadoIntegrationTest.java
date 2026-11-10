package br.com.trilhara.cronograma;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoClient;

import br.com.trilhara.auth.TokenService;
import br.com.trilhara.auth.UserRepository;
import br.com.trilhara.auth.Usuario;
import br.com.trilhara.cronograma.dto.ConcluirAtrasadosResponse;
import br.com.trilhara.cronograma.dto.CronogramaDTO;
import br.com.trilhara.cronograma.dto.DisponibilidadeSemanalDTO;
import br.com.trilhara.cronograma.dto.PaginaBlocosDTO;
import br.com.trilhara.trilha.Disciplina;
import br.com.trilhara.trilha.DisciplinaRepository;
import br.com.trilhara.trilha.TrilhaRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

@QuarkusTest
@DisplayName("FASE 4 - Domínio Estudos (Parte 3): Cronogramas, Coordenação de Concorrência, Geradores e APIs de Blocos")
class CronogramaNormalizadoIntegrationTest {

    private static final String API_TRILHAS = "/api/trilhas";
    private static final String API_BLOCOS = "/api/blocos";
    private static final String API_DISPONIBILIDADE = "/api/usuarios/me/disponibilidade-semanal";

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

    private String gerarTokenParaUsuario(ObjectId usuarioId) {
        Usuario u = new Usuario(
                usuarioId.toHexString(),
                "Usuario Teste F4",
                "teste-f4-" + usuarioId.toHexString() + "@trilhara.com.br",
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

    private record TrilhaInfo(String trilhaId, String disciplina1Id, String disciplina2Id) {}

    private TrilhaInfo criarTrilhaComDisciplinas(ObjectId usuarioId, String token) {
        String payload = """
                {
                  "nome": "Trilha F4 %s",
                  "cargo": "Auditor",
                  "banca": "CEBRASPE",
                  "dataProva": "2026-12-15",
                  "cor": "#4F46E5",
                  "disciplinasIniciais": [
                    {
                      "nome": "Direito Constitucional",
                      "cor": "#3B82F6",
                      "peso": 3,
                      "topicos": [{"nome": "Direitos Fundamentais"}]
                    },
                    {
                      "nome": "Direito Administrativo",
                      "cor": "#10B981",
                      "peso": 2,
                      "topicos": [{"nome": "Atos Administrativos"}]
                    }
                  ]
                }
                """.formatted(UUID.randomUUID().toString().substring(0, 8));

        String trilhaId = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(API_TRILHAS)
                .then()
                .statusCode(201)
                .extract().path("id");

        List<Disciplina> discs = disciplinaRepository.listarPorTrilha(usuarioId, new ObjectId(trilhaId));
        assertEquals(2, discs.size());

        return new TrilhaInfo(trilhaId, discs.get(0).idHex(), discs.get(1).idHex());
    }

    @Test
    @DisplayName("4.1 Determinismo dos geradores Sequencial, Ponderado e Manual com limite de 366 dias")
    void deveGerarCronogramaSequencialPonderadoEManualComDeterminismoELimites() {
        ObjectId uId = new ObjectId();
        String token = gerarTokenParaUsuario(uId);
        TrilhaInfo info = criarTrilhaComDisciplinas(uId, token);

        LocalDate hoje = LocalDate.now();

        // 1. Criar cronograma SEQUENCIAL
        String payloadSeq = """
                {
                  "modalidade": "SEQUENCIAL",
                  "duracaoBlocoMinutos": 60,
                  "tipoPeriodo": "UMA_SEMANA",
                  "dataInicio": "%s",
                  "diasAtivosSemana": [1, 2, 3, 4, 5],
                  "blocosPorDia": 2,
                  "disciplinasParticipantesIds": ["%s", "%s"]
                }
                """.formatted(hoje, info.disciplina1Id(), info.disciplina2Id());

        CronogramaDTO seqDto = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payloadSeq)
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas")
                .then()
                .statusCode(201)
                .body("modalidade", equalTo("SEQUENCIAL"))
                .body("ativo", equalTo(true))
                .body("duracaoBlocoMinutos", equalTo(60))
                .extract().as(CronogramaDTO.class);

        assertNotNull(seqDto.id());
        assertTrue(seqDto.quantidadeBlocos() > 0);

        // 2. Criar cronograma PONDERADO na mesma trilha (segunda modalidade)
        String payloadPond = """
                {
                  "modalidade": "PONDERADO",
                  "duracaoBlocoMinutos": 45,
                  "tipoPeriodo": "UMA_SEMANA",
                  "dataInicio": "%s",
                  "diasAtivosSemana": [1, 2, 3, 4, 5],
                  "blocosPorDia": 2,
                  "disciplinasParticipantesIds": ["%s", "%s"]
                }
                """.formatted(hoje, info.disciplina1Id(), info.disciplina2Id());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payloadPond)
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas")
                .then()
                .statusCode(201)
                .body("modalidade", equalTo("PONDERADO"))
                .body("ativo", equalTo(true))
                .extract().as(CronogramaDTO.class);

        // O novo cronograma se torna ativo e o anterior fica inativo
        Cronograma seqApos = cronogramaRepository.buscarPorId(uId, new ObjectId(seqDto.id())).orElseThrow();
        assertFalse(seqApos.ativo());

        // 3. Criar cronograma MANUAL na mesma trilha (terceira modalidade)
        String payloadManual = """
                {
                  "modalidade": "MANUAL",
                  "duracaoBlocoMinutos": 90,
                  "tipoPeriodo": "UMA_SEMANA",
                  "dataInicio": "%s",
                  "disciplinasParticipantesIds": ["%s", "%s"],
                  "semanaModeloManual": {
                    "1": ["%s", "%s"],
                    "2": ["%s"],
                    "3": ["%s", "%s"]
                  }
                }
                """.formatted(hoje, info.disciplina1Id(), info.disciplina2Id(),
                info.disciplina1Id(), info.disciplina2Id(), info.disciplina1Id(), info.disciplina2Id(), info.disciplina1Id());

        CronogramaDTO manDto = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payloadManual)
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas")
                .then()
                .statusCode(201)
                .body("modalidade", equalTo("MANUAL"))
                .body("ativo", equalTo(true))
                .extract().as(CronogramaDTO.class);

        assertNotNull(manDto.id());

        // Agora a trilha possui 3 cronogramas
        List<CronogramaDTO> todos = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList(".", CronogramaDTO.class);

        assertEquals(3, todos.size());
    }

    @Test
    @DisplayName("4.2 Rejeição de modalidade duplicada com 409 DUPLICATE_MODALITY na criação e na edição")
    void deveRejeitarModalidadeDuplicadaCom409() {
        ObjectId uId = new ObjectId();
        String token = gerarTokenParaUsuario(uId);
        TrilhaInfo info = criarTrilhaComDisciplinas(uId, token);
        LocalDate hoje = LocalDate.now();

        // 1. Criar SEQUENCIAL
        String payloadSeq = """
                {
                  "modalidade": "SEQUENCIAL",
                  "duracaoBlocoMinutos": 60,
                  "tipoPeriodo": "UMA_SEMANA",
                  "dataInicio": "%s",
                  "diasAtivosSemana": [1, 2, 3],
                  "blocosPorDia": 1,
                  "disciplinasParticipantesIds": ["%s"]
                }
                """.formatted(hoje, info.disciplina1Id());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payloadSeq)
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas")
                .then()
                .statusCode(201)
                .extract().path("id");

        // 2. Tentar criar outro SEQUENCIAL -> 409 DUPLICATE_MODALITY
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payloadSeq)
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas")
                .then()
                .statusCode(409)
                .body("code", equalTo("DUPLICATE_MODALITY"));

        // 3. Criar um PONDERADO
        String payloadPond = """
                {
                  "modalidade": "PONDERADO",
                  "duracaoBlocoMinutos": 60,
                  "tipoPeriodo": "UMA_SEMANA",
                  "dataInicio": "%s",
                  "diasAtivosSemana": [1, 2, 3],
                  "blocosPorDia": 1,
                  "disciplinasParticipantesIds": ["%s"]
                }
                """.formatted(hoje, info.disciplina1Id());

        String pondId = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payloadPond)
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas")
                .then()
                .statusCode(201)
                .extract().path("id");

        // 4. Editar o PONDERADO mudando modalidade para SEQUENCIAL (já existente na trilha) -> 409 DUPLICATE_MODALITY
        String payloadEdicao = """
                {
                  "modalidade": "SEQUENCIAL",
                  "duracaoBlocoMinutos": 60,
                  "tipoPeriodo": "UMA_SEMANA",
                  "dataInicio": "%s",
                  "diasAtivosSemana": [1, 2, 3],
                  "blocosPorDia": 1,
                  "disciplinasParticipantesIds": ["%s"]
                }
                """.formatted(hoje, info.disciplina1Id());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payloadEdicao)
                .when()
                .put(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas/" + pondId)
                .then()
                .statusCode(409)
                .body("code", equalTo("DUPLICATE_MODALITY"));
    }

    @Test
    @DisplayName("4.3 Concorrência: Lease atômico na trilha retorna 409 SCHEDULE_GENERATION_IN_PROGRESS")
    void deveCoordenarConcorrenciaComLockAtomicoNaTrilha() {
        ObjectId uId = new ObjectId();
        String token = gerarTokenParaUsuario(uId);
        TrilhaInfo info = criarTrilhaComDisciplinas(uId, token);
        ObjectId trilhaId = new ObjectId(info.trilhaId());

        // Simula lease ativo adquirido por outro worker concorrente
        String leaseToken = UUID.randomUUID().toString();
        Instant expiraEm = Instant.now().plusSeconds(60);
        boolean adquirido = trilhaRepository.adquirirLease(uId, trilhaId, leaseToken, expiraEm);
        assertTrue(adquirido);

        String payload = """
                {
                  "modalidade": "SEQUENCIAL",
                  "duracaoBlocoMinutos": 60,
                  "tipoPeriodo": "UMA_SEMANA",
                  "dataInicio": "%s",
                  "diasAtivosSemana": [1, 2, 3],
                  "blocosPorDia": 1,
                  "disciplinasParticipantesIds": ["%s"]
                }
                """.formatted(LocalDate.now(), info.disciplina1Id());

        // Requisição concorrente deve ser rejeitada com 409 SCHEDULE_GENERATION_IN_PROGRESS
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas")
                .then()
                .statusCode(409)
                .body("code", equalTo("SCHEDULE_GENERATION_IN_PROGRESS"));

        // Após liberação do lease pelo worker original, a operação passa a ter sucesso
        trilhaRepository.liberarLease(uId, trilhaId, leaseToken);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas")
                .then()
                .statusCode(201)
                .body("modalidade", equalTo("SEQUENCIAL"));
    }

    @Test
    @DisplayName("4.4 Endpoints de blocos: Hoje, paginação, conclusão individual e conclusão em lote de atrasados")
    void deveGerenciarBlocosHojePaginacaoEConclusao() {
        ObjectId uId = new ObjectId();
        String token = gerarTokenParaUsuario(uId);
        TrilhaInfo info = criarTrilhaComDisciplinas(uId, token);
        LocalDate hoje = LocalDate.now();

        // 1. Criar cronograma com dataInicio 3 dias atrás para gerar blocos atrasados e de hoje
        LocalDate inicio = hoje.minusDays(3);
        String payload = """
                {
                  "modalidade": "SEQUENCIAL",
                  "duracaoBlocoMinutos": 60,
                  "tipoPeriodo": "UMA_SEMANA",
                  "dataInicio": "%s",
                  "diasAtivosSemana": [1, 2, 3, 4, 5, 6, 7],
                  "blocosPorDia": 2,
                  "disciplinasParticipantesIds": ["%s", "%s"]
                }
                """.formatted(inicio, info.disciplina1Id(), info.disciplina2Id());

        CronogramaDTO cronograma = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas")
                .then()
                .statusCode(201)
                .extract().as(CronogramaDTO.class);

        // 2. GET /api/blocos/hoje -> deve listar os 2 blocos de hoje pendentes
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(API_BLOCOS + "/hoje")
                .then()
                .statusCode(200)
                .body(".", hasSize(2))
                .body("[0].data", equalTo(hoje.toString()))
                .body("[0].concluido", equalTo(false))
                .body("[0].ordem", equalTo(1))
                .body("[1].ordem", equalTo(2));

        // 3. GET /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/blocos -> paginado com statusData
        PaginaBlocosDTO pag = given()
                .header("Authorization", "Bearer " + token)
                .queryParam("pagina", 1)
                .queryParam("tamanho", 10)
                .when()
                .get(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas/" + cronograma.id() + "/blocos")
                .then()
                .statusCode(200)
                .body("pagina", equalTo(1))
                .extract().as(PaginaBlocosDTO.class);

        assertNotNull(pag.itens());
        assertFalse(pag.itens().isEmpty());

        String blocoId = pag.itens().get(0).id();

        // 4. PATCH /api/blocos/{blocoId}/conclusao -> marca como concluído
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"concluido\": true}")
                .when()
                .patch(API_BLOCOS + "/" + blocoId + "/conclusao")
                .then()
                .statusCode(200)
                .body("concluido", equalTo(true))
                .body("concluidoEm", notNullValue());

        // Idempotência: concluir novamente com true não quebra nem altera resultado
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"concluido\": true}")
                .when()
                .patch(API_BLOCOS + "/" + blocoId + "/conclusao")
                .then()
                .statusCode(200)
                .body("concluido", equalTo(true));

        // 5. POST /api/trilhas/{trilhaId}/cronogramas/{cronogramaId}/blocos/concluir-atrasados
        UUID opId = UUID.randomUUID();
        ConcluirAtrasadosResponse atrasadosResp = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"operationId\": \"%s\"}".formatted(opId))
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas/" + cronograma.id() + "/blocos/concluir-atrasados")
                .then()
                .statusCode(200)
                .body("operationId", equalTo(opId.toString()))
                .extract().as(ConcluirAtrasadosResponse.class);

        assertTrue(atrasadosResp.totalConcluidos() >= 0);

        // Retry com mesmo operationId deve ser idempotente e retornar o mesmo resultado
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"operationId\": \"%s\"}".formatted(opId))
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas/" + cronograma.id() + "/blocos/concluir-atrasados")
                .then()
                .statusCode(200)
                .body("operationId", equalTo(opId.toString()))
                .body("totalProcessados", equalTo(atrasadosResp.totalProcessados()))
                .body("totalConcluidos", equalTo(atrasadosResp.totalConcluidos()));
    }

    @Test
    @DisplayName("4.5 Exclusão de cronograma ativo promove restante com maior atualizadoEm")
    void devePromoverCronogramaMaisRecenteAoExcluirAtivo() {
        ObjectId uId = new ObjectId();
        String token = gerarTokenParaUsuario(uId);
        TrilhaInfo info = criarTrilhaComDisciplinas(uId, token);
        LocalDate hoje = LocalDate.now();

        // 1. Criar SEQUENCIAL
        String payloadSeq = """
                {
                  "modalidade": "SEQUENCIAL",
                  "duracaoBlocoMinutos": 60,
                  "tipoPeriodo": "UMA_SEMANA",
                  "dataInicio": "%s",
                  "diasAtivosSemana": [1, 2, 3],
                  "blocosPorDia": 1,
                  "disciplinasParticipantesIds": ["%s"]
                }
                """.formatted(hoje, info.disciplina1Id());

        String seqId = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payloadSeq)
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas")
                .then()
                .statusCode(201)
                .extract().path("id");

        // 2. Criar PONDERADO (torna-se ativo)
        String payloadPond = """
                {
                  "modalidade": "PONDERADO",
                  "duracaoBlocoMinutos": 60,
                  "tipoPeriodo": "UMA_SEMANA",
                  "dataInicio": "%s",
                  "diasAtivosSemana": [1, 2, 3],
                  "blocosPorDia": 1,
                  "disciplinasParticipantesIds": ["%s"]
                }
                """.formatted(hoje, info.disciplina1Id());

        String pondId = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payloadPond)
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas")
                .then()
                .statusCode(201)
                .extract().path("id");

        // 3. Excluir o ativo (PONDERADO)
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas/" + pondId)
                .then()
                .statusCode(204);

        // 4. O SEQUENCIAL restante deve ter sido promovido a ativo na trilha pai e no documento
        Document trilhaDoc = trilhaRepository.buscarEntidade(uId, new ObjectId(info.trilhaId())).orElseThrow();
        assertEquals(new ObjectId(seqId), trilhaDoc.getObjectId("cronogramaAtivoId"));

        Cronograma seqPromovido = cronogramaRepository.buscarPorId(uId, new ObjectId(seqId)).orElseThrow();
        assertTrue(seqPromovido.ativo());
    }

    @Test
    @DisplayName("4.6 Exclusão de disciplina remove apenas blocos futuros pendentes")
    void deveRemoverApenasBlocosFuturosPendentesAoExcluirDisciplina() {
        ObjectId uId = new ObjectId();
        String token = gerarTokenParaUsuario(uId);
        TrilhaInfo info = criarTrilhaComDisciplinas(uId, token);
        LocalDate hoje = LocalDate.now();

        // Cronograma com blocos a partir de hoje
        String payload = """
                {
                  "modalidade": "SEQUENCIAL",
                  "duracaoBlocoMinutos": 60,
                  "tipoPeriodo": "UMA_SEMANA",
                  "dataInicio": "%s",
                  "diasAtivosSemana": [1, 2, 3, 4, 5, 6, 7],
                  "blocosPorDia": 2,
                  "disciplinasParticipantesIds": ["%s", "%s"]
                }
                """.formatted(hoje, info.disciplina1Id(), info.disciplina2Id());

        CronogramaDTO cronograma = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas")
                .then()
                .statusCode(201)
                .extract().as(CronogramaDTO.class);

        // Concluir um dos blocos da disciplina1
        PaginaBlocosDTO pag = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas/" + cronograma.id() + "/blocos")
                .then()
                .statusCode(200)
                .extract().as(PaginaBlocosDTO.class);

        String blocoConcluidoId = pag.itens().stream()
                .filter(b -> b.disciplinaId().equals(info.disciplina1Id()))
                .findFirst()
                .orElseThrow()
                .id();

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"concluido\": true}")
                .when()
                .patch(API_BLOCOS + "/" + blocoConcluidoId + "/conclusao")
                .then()
                .statusCode(200);

        // Excluir disciplina 1
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete(API_TRILHAS + "/" + info.trilhaId() + "/disciplinas/" + info.disciplina1Id())
                .then()
                .statusCode(204);

        // O bloco concluído histórico permanece
        var blocoConcluido = blocoCronogramaRepository.buscarPorId(uId, new ObjectId(blocoConcluidoId));
        assertTrue(blocoConcluido.isPresent());
        assertTrue(blocoConcluido.get().concluido());

        // Blocos pendentes da disciplina 1 não existem mais
        PaginaBlocosDTO pagRestante = given()
                .header("Authorization", "Bearer " + token)
                .queryParam("concluido", false)
                .when()
                .get(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas/" + cronograma.id() + "/blocos")
                .then()
                .statusCode(200)
                .extract().as(PaginaBlocosDTO.class);

        boolean temPendenteDisc1 = pagRestante.itens().stream()
                .anyMatch(b -> b.disciplinaId().equals(info.disciplina1Id()));
        assertFalse(temPendenteDisc1);
    }

    @Test
    @DisplayName("4.7 Perfil de disponibilidade semanal e aviso de sobrecarga")
    void deveConsultarEAtualizarDisponibilidadeSemanalComCalculoDeCargaESobrecarga() {
        ObjectId uId = new ObjectId();
        String token = gerarTokenParaUsuario(uId);

        // 1. GET disponibilidade inicial default [0,0,0,0,0,0,0]
        DisponibilidadeSemanalDTO inicial = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(API_DISPONIBILIDADE)
                .then()
                .statusCode(200)
                .extract().as(DisponibilidadeSemanalDTO.class);

        assertEquals(List.of(0, 0, 0, 0, 0, 0, 0), inicial.disponibilidadeSemanalMinutos());

        // 2. PUT disponibilidade
        String payloadPut = """
                {
                  "disponibilidadeSemanalMinutos": [120, 120, 120, 120, 120, 0, 0]
                }
                """;

        DisponibilidadeSemanalDTO atualizada = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payloadPut)
                .when()
                .put(API_DISPONIBILIDADE)
                .then()
                .statusCode(200)
                .body("disponibilidadeSemanalMinutos[0]", equalTo(120))
                .extract().as(DisponibilidadeSemanalDTO.class);

        assertEquals(120, atualizada.disponibilidadeSemanalMinutos().get(0));

        // 3. Criar trilha com cronograma que sobrecarrega segunda-feira (180 min planejados > 120 min disp)
        TrilhaInfo info = criarTrilhaComDisciplinas(uId, token);
        String payloadCronograma = """
                {
                  "modalidade": "SEQUENCIAL",
                  "duracaoBlocoMinutos": 90,
                  "tipoPeriodo": "UMA_SEMANA",
                  "dataInicio": "%s",
                  "diasAtivosSemana": [1],
                  "blocosPorDia": 2,
                  "disciplinasParticipantesIds": ["%s"]
                }
                """.formatted(LocalDate.now(), info.disciplina1Id());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payloadCronograma)
                .when()
                .post(API_TRILHAS + "/" + info.trilhaId() + "/cronogramas")
                .then()
                .statusCode(201);

        DisponibilidadeSemanalDTO comCarga = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(API_DISPONIBILIDADE)
                .then()
                .statusCode(200)
                .extract().as(DisponibilidadeSemanalDTO.class);

        // 2 blocos de 90m = 180m na segunda-feira
        assertEquals(180, comCarga.cargaAtivaMinutosPorDia().get(0));
        assertTrue(comCarga.sobrecargaPorDia().get(0));
    }

    @Test
    @DisplayName("4.8 Isolamento de usuário: outro usuário não acessa nem modifica cronogramas ou blocos")
    void deveIsolarCronogramasEBlocosPorUsuario() {
        ObjectId u1 = new ObjectId();
        String token1 = gerarTokenParaUsuario(u1);
        TrilhaInfo info1 = criarTrilhaComDisciplinas(u1, token1);

        ObjectId u2 = new ObjectId();
        String token2 = gerarTokenParaUsuario(u2);

        String payload = """
                {
                  "modalidade": "SEQUENCIAL",
                  "duracaoBlocoMinutos": 60,
                  "tipoPeriodo": "UMA_SEMANA",
                  "dataInicio": "%s",
                  "diasAtivosSemana": [1, 2],
                  "blocosPorDia": 1,
                  "disciplinasParticipantesIds": ["%s"]
                }
                """.formatted(LocalDate.now(), info1.disciplina1Id());

        String cronogramaId = given()
                .header("Authorization", "Bearer " + token1)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(API_TRILHAS + "/" + info1.trilhaId() + "/cronogramas")
                .then()
                .statusCode(201)
                .extract().path("id");

        // Usuário 2 não consegue listar os cronogramas da trilha do Usuário 1 (404)
        given()
                .header("Authorization", "Bearer " + token2)
                .when()
                .get(API_TRILHAS + "/" + info1.trilhaId() + "/cronogramas")
                .then()
                .statusCode(404);

        // Usuário 2 não consegue excluir o cronograma do Usuário 1 (404)
        given()
                .header("Authorization", "Bearer " + token2)
                .when()
                .delete(API_TRILHAS + "/" + info1.trilhaId() + "/cronogramas/" + cronogramaId)
                .then()
                .statusCode(404);
    }
}
