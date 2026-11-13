package br.com.trilhara.tarefa;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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

@QuarkusTest
class TarefaRecorrenciaResourceTest {

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
    PurgaLixeiraTarefasJob purgaJob;

    private String tokenUsuario;
    private String usuarioId;

    @BeforeEach
    void setUp() {
        usuarioId = new ObjectId().toHexString();
        Instant agora = Instant.now();
        Usuario usuario = new Usuario(
                usuarioId,
                "Usuario Tarefas",
                "u-" + UUID.randomUUID() + "@teste.com",
                "hash",
                List.of(Role.USER),
                true,
                true,
                0,
                null,
                agora,
                agora,
                agora);

        userRepository.inserir(usuario);
        tokenUsuario = tokenService.gerarAccessToken(usuario);
    }

    @Test
    @DisplayName("Geração idempotente na janela de 90 dias a partir da criação de série recorrente")
    void deveGerarOcorrenciasNaJanela90DiasDeFormaIdempotente() {
        LocalDate hoje = LocalDate.now();
        String payloadSerie = """
                {
                  "titulo": "Exercício Físico Diário",
                  "prioridade": "ALTA",
                  "horario": "07:00",
                  "frequencia": "DIARIO",
                  "intervalo": 1,
                  "tipoTermino": "NUNCA",
                  "dataInicio": "%s"
                }
                """.formatted(hoje.toString());

        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .contentType(ContentType.JSON)
                .body(payloadSerie)
                .when().post(SERIES)
                .then().statusCode(201)
                .body("id", notNullValue())
                .extract().path("id");

        // Consulta tarefas: deve conter pelo menos 91 ocorrências geradas na janela de 90 dias
        List<?> tarefas1 = given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .queryParam("tamanho", 100)
                .when().get(TAREFAS)
                .then().statusCode(200)
                .body("totalItens", greaterThanOrEqualTo(90))
                .extract().path("itens");

        int totalInicial = tarefas1.size();

        // Segunda consulta / recálculo: 100% idempotente, sem duplicar registros
        List<?> tarefas2 = given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .queryParam("tamanho", 100)
                .when().get(TAREFAS)
                .then().statusCode(200)
                .extract().path("itens");

        assertEquals(totalInicial, tarefas2.size(), "Consultas repetidas não devem recriar tarefas já existentes");
    }

    @Test
    @DisplayName("Reagendar dataVencimento de uma ocorrência não recria a ocorrência na dataOriginalOcorrencia")
    void devePreservarIdempotenciaAoReagendarVencimento() {
        LocalDate hoje = LocalDate.now();
        String payloadSerie = """
                {
                  "titulo": "Reunião Semanal",
                  "prioridade": "MEDIA",
                  "frequencia": "DIARIO",
                  "intervalo": 1,
                  "tipoTermino": "NUNCA",
                  "dataInicio": "%s"
                }
                """.formatted(hoje.toString());

        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .contentType(ContentType.JSON)
                .body(payloadSerie)
                .when().post(SERIES)
                .then().statusCode(201)
                .extract().path("id");

        // Localiza a ocorrência de hoje
        String tarefaHojeId = given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .queryParam("data", hoje.toString())
                .when().get(TAREFAS)
                .then().statusCode(200)
                .body("itens", hasSize(1))
                .extract().path("itens[0].id");

        // Reagenda o vencimento para amanhã com escopo ESTA
        LocalDate amanha = hoje.plusDays(1);
        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .contentType(ContentType.JSON)
                .queryParam("escopo", "ESTA")
                .body("{\"dataVencimento\":\"" + amanha + "\"}")
                .when().put(TAREFAS + "/" + tarefaHojeId)
                .then().statusCode(200)
                .body("dataVencimento", equalTo(amanha.toString()))
                .body("dataOriginalOcorrencia", equalTo(hoje.toString()))
                .body("desvinculadaDaSerie", equalTo(true));

        // Força nova consulta de listagem: a janela móvel NÃO recria a ocorrência de hoje
        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .queryParam("data", hoje.toString())
                .when().get(TAREFAS)
                .then().statusCode(200)
                .body("itens", hasSize(0));
    }

    @Test
    @DisplayName("Edição e exclusão nos escopos ESTA, ESTA_E_FUTURAS e TODA_A_SERIE preservando concluídas")
    void deveOperarEscoposDeEdicaoEExclusaoPreservandoConcluidas() {
        LocalDate hoje = LocalDate.now();
        String payloadSerie = """
                {
                  "titulo": "Estudo Diário Matutino",
                  "prioridade": "ALTA",
                  "frequencia": "DIARIO",
                  "intervalo": 1,
                  "tipoTermino": "NUNCA",
                  "dataInicio": "%s"
                }
                """.formatted(hoje.toString());

        String serieId = given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .contentType(ContentType.JSON)
                .body(payloadSerie)
                .when().post(SERIES)
                .then().statusCode(201)
                .extract().path("id");

        // 1. Conclui a tarefa de hoje
        String tarefaHojeId = given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .queryParam("data", hoje.toString())
                .when().get(TAREFAS)
                .then().statusCode(200)
                .extract().path("itens[0].id");

        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .contentType(ContentType.JSON)
                .body("{\"concluida\": true}")
                .when().patch(TAREFAS + "/" + tarefaHojeId + "/conclusao")
                .then().statusCode(200)
                .body("concluida", equalTo(true))
                .body("estado", equalTo("CONCLUIDA"));

        // 2. Escopo ESTA_E_FUTURAS a partir de hoje + 2 dias
        LocalDate alvo = hoje.plusDays(2);
        String tarefaAlvoId = given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .queryParam("data", alvo.toString())
                .when().get(TAREFAS)
                .then().statusCode(200)
                .extract().path("itens[0].id");

        String operationId = UUID.randomUUID().toString();
        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .contentType(ContentType.JSON)
                .queryParam("escopo", "ESTA_E_FUTURAS")
                .header("Idempotency-Key", operationId)
                .body("""
                        {
                          "titulo": "Estudo Noturno Atualizado",
                          "prioridade": "MEDIA"
                        }
                        """)
                .when().put(TAREFAS + "/" + tarefaAlvoId)
                .then().statusCode(200)
                .body("titulo", equalTo("Estudo Noturno Atualizado"));

        // A tarefa de hoje (concluída) deve permanecer intacta com o título e série de origem
        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .when().get(TAREFAS + "/" + tarefaHojeId)
                .then().statusCode(200)
                .body("titulo", equalTo("Estudo Diário Matutino"))
                .body("concluida", equalTo(true));

        // 3. Escopo TODA_A_SERIE na série original: move pendentes para lixeira mas preserva concluídas
        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .when().delete(SERIES + "/" + serieId)
                .then().statusCode(204);

        // A tarefa concluída de hoje continua consultável e NÃO está na lixeira
        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .when().get(TAREFAS + "/" + tarefaHojeId)
                .then().statusCode(200)
                .body("concluida", equalTo(true))
                .body("deletedAt", nullValue());
    }

    @Test
    @DisplayName("Lixeira de tarefas/listas, restauração com lista pai em lixeira/purgada e esvaziamento")
    void deveGerenciarLixeiraERestauracaoComHierarquia() {
        // 1. Cria lista manual
        String listaId = given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .contentType(ContentType.JSON)
                .body("{\"nome\":\"Faculdade 2026\",\"cor\":\"#4f46e5\"}")
                .when().post(LISTAS)
                .then().statusCode(201)
                .extract().path("id");

        // 2. Cria tarefa vinculada à lista
        String tarefaId = given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "listaId": "%s",
                          "titulo": "Trabalho de Conclusão",
                          "prioridade": "ALTA",
                          "subtarefas": [
                            {"titulo": "Introdução", "concluida": false},
                            {"titulo": "Metodologia", "concluida": false}
                          ]
                        }
                        """.formatted(listaId))
                .when().post(TAREFAS)
                .then().statusCode(201)
                .extract().path("id");

        // 3. Atualizar subtarefa não conclui o pai
        String subId = given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .when().get(TAREFAS + "/" + tarefaId)
                .then().statusCode(200)
                .extract().path("subtarefas[0].id");

        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .contentType(ContentType.JSON)
                .body("{\"concluida\": true}")
                .when().patch(TAREFAS + "/" + tarefaId + "/subtarefas/" + subId)
                .then().statusCode(200)
                .body("concluida", equalTo(false))
                .body("subtarefas[0].concluida", equalTo(true));

        // 4. Excluir lista move a lista e as tarefas para a lixeira
        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .when().delete(LISTAS + "/" + listaId)
                .then().statusCode(200)
                .body("tarefasAfetadas", equalTo(1));

        // Consulta lixeira: deve listar a lista e a tarefa
        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .when().get(LIXEIRA)
                .then().statusCode(200)
                .body("listas", hasSize(1))
                .body("tarefas", hasSize(1));

        // 5. Restaurar a tarefa individual enquanto a lista pai está na lixeira:
        // Deve restaurar automaticamente o container da lista pai!
        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .when().post(TAREFAS + "/lixeira/" + tarefaId + "/restaurar")
                .then().statusCode(200)
                .body("tarefa.id", equalTo(tarefaId))
                .body("tarefa.listaId", equalTo(listaId))
                .body("realocadaParaListaGeral", equalTo(false));

        // A lista pai agora está ativa novamente
        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .when().get(LISTAS + "/" + listaId)
                .then().statusCode(200)
                .body("nome", equalTo("Faculdade 2026"));

        // 6. Teste de lista pai purgada definitivamente:
        // Move lista e tarefa novamente para lixeira
        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .when().delete(LISTAS + "/" + listaId)
                .then().statusCode(200);

        // Remove definitivamente a lista do banco (simula purga definitiva após 30d)
        listaRepository.removerDefinitivamente(new ObjectId(usuarioId), new ObjectId(listaId));

        // Ao restaurar a tarefa individual cuja lista foi purgada:
        // Restaura na Lista Geral (listaId = null)
        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .when().post(TAREFAS + "/lixeira/" + tarefaId + "/restaurar")
                .then().statusCode(200)
                .body("tarefa.id", equalTo(tarefaId))
                .body("tarefa.listaId", nullValue())
                .body("realocadaParaListaGeral", equalTo(true));

        // 7. Esvaziar lixeira manual
        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .when().delete(TAREFAS + "/lixeira/esvaziar")
                .then().statusCode(204);

        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .when().get(LIXEIRA)
                .then().statusCode(200)
                .body("listas", hasSize(0))
                .body("tarefas", hasSize(0));
    }

    private static org.hamcrest.Matcher<Object> nullValue() {
        return org.hamcrest.Matchers.nullValue();
    }
}
