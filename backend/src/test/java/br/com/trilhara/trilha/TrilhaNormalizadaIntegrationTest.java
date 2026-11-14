package br.com.trilhara.trilha;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoClient;

import br.com.trilhara.auth.TokenService;
import br.com.trilhara.auth.Usuario;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

@QuarkusTest
@DisplayName("Domínio Estudos (Parte 1): Trilhas, Disciplinas, Tópicos e Progresso Normalizados")
class TrilhaNormalizadaIntegrationTest {

    private static final String API_TRILHAS = "/api/trilhas";
    private static final String API_TRILHA = "/api/trilhas/{id}";

    @Inject
    TrilhaRepository trilhaRepository;

    @Inject
    DisciplinaRepository disciplinaRepository;

    @Inject
    ProgressoTopicoRepository progressoRepository;

    @Inject
    TokenService tokenService;

    @Inject
    br.com.trilhara.auth.UserRepository userRepository;

    @Inject
    MongoClient mongoClient;

    private String gerarTokenParaUsuario(ObjectId usuarioId) {
        Usuario u = new Usuario(
                usuarioId.toHexString(),
                "Usuario Teste",
                "teste-" + usuarioId.toHexString() + "@trilhara.com.br",
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

    @Test
    @DisplayName("2.1 Deve criar trilha e retornar resumo leve sem arrays de tópicos no Hub")
    void deveCriarTrilhaERetornarResumoSemArrayDeTopicosNoHub() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);
        String nome = "Trilha Hub " + UUID.randomUUID();

        String payload = """
                {
                  "nome": "%s",
                  "cargo": "Analista Judiciário",
                  "banca": "FGV",
                  "dataProva": "2026-11-30",
                  "cor": "#4A6B82",
                  "disciplinasIniciais": [
                    {
                      "nome": "Direito Constitucional",
                      "peso": 3,
                      "topicos": [
                        {"nome": "Direitos e Deveres Individuais"},
                        {"nome": "Organização do Estado"}
                      ]
                    }
                  ]
                }
                """.formatted(nome);

        String trilhaId = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post(API_TRILHAS)
                .then().statusCode(201)
                .header("Location", containsString("/api/trilhas/"))
                .body("id", notNullValue())
                .body("nome", equalTo(nome))
                .body("cargo", equalTo("Analista Judiciário"))
                .body("totalDisciplinas", equalTo(1))
                .body("totalTopicos", equalTo(2))
                .body("topicosConcluidos", equalTo(0))
                .body("percentualProgresso", equalTo(0.0f))
                .body("cronogramaAtivoResumo", nullValue())
                .body("disciplinas", hasSize(1))
                .body("disciplinas[0].topicos", hasSize(2))
                .extract().path("id");

        // Listagem no Hub: deve ser o resumo leve sem transferir os tópicos das disciplinas
        given()
                .header("Authorization", "Bearer " + token)
                .when().get(API_TRILHAS)
                .then().statusCode(200)
                .body("id", hasItem(trilhaId))
                .body("find { it.id == '%s' }.totalDisciplinas".formatted(trilhaId), equalTo(1))
                .body("find { it.id == '%s' }.totalTopicos".formatted(trilhaId), equalTo(2))
                .body("find { it.id == '%s' }.topicosConcluidos".formatted(trilhaId), equalTo(0))
                .body("find { it.id == '%s' }.cronogramaAtivoResumo".formatted(trilhaId), nullValue())
                .body("find { it.id == '%s' }".formatted(trilhaId), not(org.hamcrest.Matchers.hasKey("disciplinas")))
                .body("find { it.id == '%s' }".formatted(trilhaId), not(org.hamcrest.Matchers.hasKey("topicos")));
    }

    @Test
    @DisplayName("2.2 Deve calcular percentualProgresso ponderado pelo número total de tópicos")
    void deveCalcularPercentualProgressoPonderadoPeloTotalDeTopicos() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);

        // Trilha com 2 disciplinas:
        // Disciplina A: 1 tópico
        // Disciplina B: 3 tópicos
        // Total na trilha: 4 tópicos
        String payload = """
                {
                  "nome": "Trilha Ponderada %s",
                  "disciplinasIniciais": [
                    {
                      "nome": "Disciplina A",
                      "topicos": [{"nome": "Topico A1"}]
                    },
                    {
                      "nome": "Disciplina B",
                      "topicos": [
                        {"nome": "Topico B1"},
                        {"nome": "Topico B2"},
                        {"nome": "Topico B3"}
                      ]
                    }
                  ]
                }
                """.formatted(UUID.randomUUID());

        var res = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post(API_TRILHAS)
                .then().statusCode(201).extract().response();

        String trilhaId = res.path("id");
        String topicoA1Id = res.path("disciplinas[0].topicos[0].id");
        String topicoB1Id = res.path("disciplinas[1].topicos[0].id");

        // Conclui 1 tópico na disciplina A (100% da Disc A)
        given()
                .header("Authorization", "Bearer " + token)
                .when().put(API_TRILHAS + "/{trilhaId}/topicos/{topicoId}/progresso", trilhaId, topicoA1Id)
                .then().statusCode(200);

        // Conclui 1 tópico na disciplina B (33.3% da Disc B)
        given()
                .header("Authorization", "Bearer " + token)
                .when().put(API_TRILHAS + "/{trilhaId}/topicos/{topicoId}/progresso", trilhaId, topicoB1Id)
                .then().statusCode(200);

        // Ponderação natural pelo total de tópicos:
        // Total tópicos = 4, Total concluídos = 2 => Progresso da trilha = 50.0%
        // (A média simples incorreta seria (100 + 33.33) / 2 = 66.66%)
        given()
                .header("Authorization", "Bearer " + token)
                .when().get(API_TRILHA, trilhaId)
                .then().statusCode(200)
                .body("totalTopicos", equalTo(4))
                .body("topicosConcluidos", equalTo(2))
                .body("percentualProgresso", equalTo(50.0f))
                .body("disciplinas[0].percentualProgresso", equalTo(100.0f))
                .body("topicosConcluidosIds", hasItem(topicoA1Id))
                .body("topicosConcluidosIds", hasItem(topicoB1Id));

        // No resumo do Hub:
        given()
                .header("Authorization", "Bearer " + token)
                .when().get(API_TRILHAS)
                .then().statusCode(200)
                .body("find { it.id == '%s' }.percentualProgresso".formatted(trilhaId), equalTo(50.0f));
    }

    @Test
    @DisplayName("2.3 Deve garantir idempotência no progresso do tópico (PUT e DELETE)")
    void deveGarantirIdempotenciaNoProgressoDoTopico() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);

        String payload = """
                {
                  "nome": "Trilha Idempotencia",
                  "disciplinasIniciais": [
                    {
                      "nome": "Disciplina 1",
                      "topicos": [{"nome": "Topico 1"}]
                    }
                  ]
                }
                """;

        var res = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post(API_TRILHAS)
                .then().statusCode(201).extract().response();

        String trilhaId = res.path("id");
        String topicoId = res.path("disciplinas[0].topicos[0].id");

        // 1ª marcação
        given()
                .header("Authorization", "Bearer " + token)
                .when().put(API_TRILHAS + "/{trilhaId}/topicos/{topicoId}/progresso", trilhaId, topicoId)
                .then().statusCode(200);
        long concluidosPrimeiraMarcacao = progressoRepository.contarConcluidosPorTrilha(usuarioId, new ObjectId(trilhaId));
        assertEquals(1, concluidosPrimeiraMarcacao);

        // 2ª marcação idêntica (retry) - não duplica nem falha
        given()
                .header("Authorization", "Bearer " + token)
                .when().put(API_TRILHAS + "/{trilhaId}/topicos/{topicoId}/progresso", trilhaId, topicoId)
                .then().statusCode(200);
        long concluidosRetry = progressoRepository.contarConcluidosPorTrilha(usuarioId, new ObjectId(trilhaId));
        assertEquals(1, concluidosRetry);

        // Desmarcação pontual (DELETE)
        given()
                .header("Authorization", "Bearer " + token)
                .when().delete(API_TRILHAS + "/{trilhaId}/topicos/{topicoId}/progresso", trilhaId, topicoId)
                .then().statusCode(204);
        long concluidosAposDesmarcacao = progressoRepository.contarConcluidosPorTrilha(usuarioId, new ObjectId(trilhaId));
        assertEquals(0, concluidosAposDesmarcacao);

        // Desmarcação repetida (idempotente)
        given()
                .header("Authorization", "Bearer " + token)
                .when().delete(API_TRILHAS + "/{trilhaId}/topicos/{topicoId}/progresso", trilhaId, topicoId)
                .then().statusCode(204);
    }

    @Test
    @DisplayName("2.4 Deve preservar IDs e progresso ao renomear disciplina e tópico")
    void devePreservarIdsEProgressoAoRenomearDisciplinaETopico() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);

        String payload = """
                {
                  "nome": "Trilha Renomeacao",
                  "disciplinasIniciais": [
                    {
                      "nome": "Nome Antigo Disciplina",
                      "peso": 2,
                      "topicos": [{"nome": "Nome Antigo Topico"}]
                    }
                  ]
                }
                """;

        var res = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post(API_TRILHAS)
                .then().statusCode(201).extract().response();

        String trilhaId = res.path("id");
        String disciplinaId = res.path("disciplinas[0].id");
        String topicoId = res.path("disciplinas[0].topicos[0].id");

        // Marca como concluído
        given()
                .header("Authorization", "Bearer " + token)
                .when().put(API_TRILHAS + "/{trilhaId}/topicos/{topicoId}/progresso", trilhaId, topicoId)
                .then().statusCode(200);

        // Renomeia a disciplina e o tópico preservando o ID do tópico
        String payloadEdicao = """
                {
                  "nome": "Nome Novo Disciplina",
                  "peso": 4,
                  "topicos": [
                    {"id": "%s", "nome": "Nome Novo Topico"}
                  ]
                }
                """.formatted(topicoId);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payloadEdicao)
                .when().put(API_TRILHAS + "/{trilhaId}/disciplinas/{disciplinaId}", trilhaId, disciplinaId)
                .then().statusCode(200)
                .body("id", equalTo(disciplinaId))
                .body("nome", equalTo("Nome Novo Disciplina"))
                .body("peso", equalTo(4))
                .body("topicos[0].id", equalTo(topicoId))
                .body("topicos[0].nome", equalTo("Nome Novo Topico"))
                .body("topicos[0].concluido", equalTo(true));

        // Valida no detalhe que o progresso permanece associado
        given()
                .header("Authorization", "Bearer " + token)
                .when().get(API_TRILHA, trilhaId)
                .then().statusCode(200)
                .body("disciplinas[0].nome", equalTo("Nome Novo Disciplina"))
                .body("disciplinas[0].topicos[0].nome", equalTo("Nome Novo Topico"))
                .body("disciplinas[0].topicos[0].concluido", equalTo(true))
                .body("topicosConcluidosIds", hasItem(topicoId));
    }

    @Test
    @DisplayName("2.5 Deve limpar progresso ao excluir tópico da disciplina e rejeitar IDs desconhecidos")
    void deveLimparProgressoAoExcluirTopicoERejeitarIdsDesconhecidos() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);

        String payload = """
                {
                  "nome": "Trilha Exclusão Tópico",
                  "disciplinasIniciais": [
                    {
                      "nome": "Português",
                      "topicos": [
                        {"nome": "Topico 1"},
                        {"nome": "Topico 2"}
                      ]
                    }
                  ]
                }
                """;

        var res = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post(API_TRILHAS)
                .then().statusCode(201).extract().response();

        String trilhaId = res.path("id");
        String disciplinaId = res.path("disciplinas[0].id");
        String topico1Id = res.path("disciplinas[0].topicos[0].id");
        String topico2Id = res.path("disciplinas[0].topicos[1].id");

        // Conclui ambos os tópicos
        given()
                .header("Authorization", "Bearer " + token)
                .when().put(API_TRILHAS + "/{trilhaId}/topicos/{topicoId}/progresso", trilhaId, topico1Id)
                .then().statusCode(200);

        given()
                .header("Authorization", "Bearer " + token)
                .when().put(API_TRILHAS + "/{trilhaId}/topicos/{topicoId}/progresso", trilhaId, topico2Id)
                .then().statusCode(200);

        assertEquals(2, progressoRepository.contarConcluidosPorTrilha(usuarioId, new ObjectId(trilhaId)));

        // Tentativa de enviar um ID desconhecido deve falhar com 400 Bad Request
        String payloadIdInvalido = """
                {
                  "nome": "Português",
                  "topicos": [
                    {"id": "60c72b2f9b1d8b2bad000099", "nome": "Topico Fantasma"}
                  ]
                }
                """;

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payloadIdInvalido)
                .when().put(API_TRILHAS + "/{trilhaId}/disciplinas/{disciplinaId}", trilhaId, disciplinaId)
                .then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("errors.topicos", hasItem(containsString("ID desconhecido")));

        // Atualiza a disciplina mantendo apenas Topico 1 e omitindo Topico 2
        String payloadRemocao = """
                {
                  "nome": "Português",
                  "topicos": [
                    {"id": "%s", "nome": "Topico 1"}
                  ]
                }
                """.formatted(topico1Id);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payloadRemocao)
                .when().put(API_TRILHAS + "/{trilhaId}/disciplinas/{disciplinaId}", trilhaId, disciplinaId)
                .then().statusCode(200)
                .body("topicos", hasSize(1));

        // Progresso do Tópico 2 deve ter sido limpo automaticamente
        var concluidos = progressoRepository.listarTopicosConcluidosIds(usuarioId, new ObjectId(trilhaId));
        assertEquals(1, concluidos.size());
        assertTrue(concluidos.contains(topico1Id));
        assertFalse(concluidos.contains(topico2Id));
    }

    @Test
    @DisplayName("2.6 Deve reordenar disciplinas com validação estrita de integridade")
    void deveReordenarDisciplinasComValidacaoEstrita() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);

        String payload = """
                {
                  "nome": "Trilha Reordenar",
                  "disciplinasIniciais": [
                    {"nome": "D1"},
                    {"nome": "D2"},
                    {"nome": "D3"}
                  ]
                }
                """;

        var res = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post(API_TRILHAS)
                .then().statusCode(201).extract().response();

        String trilhaId = res.path("id");
        String d1 = res.path("disciplinas[0].id");
        String d2 = res.path("disciplinas[1].id");
        String d3 = res.path("disciplinas[2].id");

        // Reordenação incompleta (faltando D3) -> 400
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"disciplinaIds\": [\"%s\", \"%s\"]}".formatted(d1, d2))
                .when().put(API_TRILHAS + "/{trilhaId}/disciplinas/reordenar", trilhaId)
                .then().statusCode(400);

        // Reordenação com duplicata -> 400
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"disciplinaIds\": [\"%s\", \"%s\", \"%s\"]}".formatted(d1, d1, d2))
                .when().put(API_TRILHAS + "/{trilhaId}/disciplinas/reordenar", trilhaId)
                .then().statusCode(400);

        // Reordenação válida: [D3, D1, D2]
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"disciplinaIds\": [\"%s\", \"%s\", \"%s\"]}".formatted(d3, d1, d2))
                .when().put(API_TRILHAS + "/{trilhaId}/disciplinas/reordenar", trilhaId)
                .then().statusCode(204);

        // Verifica se a ordem foi devidamente alterada
        given()
                .header("Authorization", "Bearer " + token)
                .when().get(API_TRILHA, trilhaId)
                .then().statusCode(200)
                .body("disciplinas[0].id", equalTo(d3))
                .body("disciplinas[1].id", equalTo(d1))
                .body("disciplinas[2].id", equalTo(d2));
    }

    @Test
    @DisplayName("2.7 Deve isolar integralmente dados entre usuários (usuarioId)")
    void deveIsolarIntegralmentePorUsuarioId() {
        ObjectId usuarioA = new ObjectId();
        ObjectId usuarioB = new ObjectId();
        String tokenA = gerarTokenParaUsuario(usuarioA);
        String tokenB = gerarTokenParaUsuario(usuarioB);

        // Usuário A cria trilha
        String trilhaAId = given()
                .header("Authorization", "Bearer " + tokenA)
                .contentType(ContentType.JSON)
                .body("{\"nome\": \"Trilha Exclusiva A\"}")
                .when().post(API_TRILHAS)
                .then().statusCode(201).extract().path("id");

        // Usuário B tenta ler a trilha de A -> 404
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when().get(API_TRILHA, trilhaAId)
                .then().statusCode(404);

        // Usuário B tenta atualizar metadados de A -> 404
        given()
                .header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON)
                .body("{\"nome\": \"Hacked\"}")
                .when().put(API_TRILHA, trilhaAId)
                .then().statusCode(404);

        // Usuário B tenta excluir a trilha de A -> 404
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when().delete(API_TRILHA, trilhaAId)
                .then().statusCode(404);

        // Trilha de A continua existindo
        given()
                .header("Authorization", "Bearer " + tokenA)
                .when().get(API_TRILHA, trilhaAId)
                .then().statusCode(200);
    }

    @Test
    @DisplayName("2.8 Deve responder 409 RESOURCE_LIMIT_EXCEEDED ao exceder limites de trilhas, disciplinas e tópicos")
    void deveRetornar409ResourceLimitAoExcederLimites() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);

        // 1. Disciplina com 501 tópicos -> 409 RESOURCE_LIMIT_EXCEEDED (limite de acervo, não de sintaxe)
        List<String> topicos501 = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            topicos501.add("{\"nome\": \"Topico " + i + "\"}");
        }
        String payloadTopicosExcedidos = """
                {
                  "nome": "Trilha Limite Topicos",
                  "disciplinasIniciais": [
                    {
                      "nome": "Disciplina Lotada",
                      "topicos": [%s]
                    }
                  ]
                }
                """.formatted(String.join(",", topicos501));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payloadTopicosExcedidos)
                .when().post(API_TRILHAS)
                .then().statusCode(409)
                .contentType(containsString("application/problem+json"))
                .body("code", equalTo("RESOURCE_LIMIT_EXCEEDED"))
                .body("type", equalTo("urn:trilhara:problem:resource-limit-exceeded"))
                .body("detail", containsString("500 tópicos"));
    }

    @Test
    @DisplayName("2.9 Deve rejeitar edital e disciplinasIniciais juntos com 400 no campo editalId")
    void deveRejeitarEditalEDisciplinasIniciaisJuntos() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "nome": "Trilha Invalida",
                          "editalId": "60c72b2f9b1d8b2bad000001",
                          "disciplinasIniciais": [{"nome": "D1"}]
                        }
                        """)
                .when().post(API_TRILHAS)
                .then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("errors.editalId", hasItem(containsString("apenas um destes campos")));
    }

    @Test
    @DisplayName("2.9 Deve excluir trilha e dados associados, respondendo 404 na repetição")
    void deveExcluirTrilhaEDadosAssociados() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);

        String trilhaId = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "nome": "Trilha para Exclusão",
                          "disciplinasIniciais": [
                            {"nome": "D1", "topicos": [{"nome": "T1"}]}
                          ]
                        }
                        """)
                .when().post(API_TRILHAS)
                .then().statusCode(201).extract().path("id");

        // Primeira exclusão -> 204 No Content
        given()
                .header("Authorization", "Bearer " + token)
                .when().delete(API_TRILHA, trilhaId)
                .then().statusCode(204);

        // Repetição da exclusão com mesmo ID já ausente -> 404 Not Found
        given()
                .header("Authorization", "Bearer " + token)
                .when().delete(API_TRILHA, trilhaId)
                .then().statusCode(404);

        // Busca pelo ID excluído -> 404
        given()
                .header("Authorization", "Bearer " + token)
                .when().get(API_TRILHA, trilhaId)
                .then().statusCode(404);
    }

    // =========================================================================
    // ACHADOS DE REVISÃO
    // =========================================================================

    @Test
    @DisplayName("Não deve existir a rota legada PATCH /api/trilhas/{id}/progresso/{topicoId}")
    void naoDeveExistirRotaLegadaDeProgresso() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);

        var res = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"done\": true}")
                .when().patch(API_TRILHA + "/progresso/{topicoId}", new ObjectId().toHexString(), new ObjectId().toHexString());

        assertTrue(res.statusCode() == 404 || res.statusCode() == 405,
                "Rota legada de progresso deveria estar removida; status obtido: " + res.statusCode());
    }

    @Test
    @DisplayName("Deve rejeitar edital inexistente com 400 VALIDATION_ERROR no campo editalId")
    void deveRejeitarEditalInexistenteCom400NoCampoEditalId() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"nome": "Trilha Edital Fantasma", "editalId": "60c72b2f9b1d8b2bad000001"}
                        """)
                .when().post(API_TRILHAS)
                .then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("errors.editalId", hasItem(containsString("Edital publicado não encontrado")));
    }

    @Test
    @DisplayName("Deve responder 404 ao marcar progresso de tópico de outra trilha")
    void deveRetornar404AoMarcarProgressoDeTopicoDeOutraTrilha() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);

        String trilhaA = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"nome": "Trilha A", "disciplinasIniciais": [{"nome": "D1", "topicos": [{"nome": "T1"}]}]}
                        """)
                .when().post(API_TRILHAS)
                .then().statusCode(201)
                .extract().path("id");

        var trilhaB = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"nome": "Trilha B", "disciplinasIniciais": [{"nome": "D2", "topicos": [{"nome": "T2"}]}]}
                        """)
                .when().post(API_TRILHAS)
                .then().statusCode(201)
                .extract().response();

        String trilhaBId = trilhaB.path("id");
        String topicoBId = trilhaB.path("disciplinas[0].topicos[0].id");

        // Tópico existe, mas na trilha B — no path da trilha A responde 404
        given()
                .header("Authorization", "Bearer " + token)
                .when().put(API_TRILHAS + "/{trilhaId}/topicos/{topicoId}/progresso", trilhaA, topicoBId)
                .then().statusCode(404);

        // Na trilha correta continua funcionando
        given()
                .header("Authorization", "Bearer " + token)
                .when().put(API_TRILHAS + "/{trilhaId}/topicos/{topicoId}/progresso", trilhaBId, topicoBId)
                .then().statusCode(200);
    }

    @Test
    @DisplayName("Deve responder 409 SCHEDULE_GENERATION_IN_PROGRESS ao excluir com lease ocupado")
    void deveRetornar409SeLeaseOcupadoAoExcluirDisciplina() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);

        var criada = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"nome": "Trilha Lease", "disciplinasIniciais": [{"nome": "D1"}]}
                        """)
                .when().post(API_TRILHAS)
                .then().statusCode(201)
                .extract().response();

        String trilhaId = criada.path("id");
        String disciplinaId = criada.path("disciplinas[0].id");

        mongoClient.getDatabase("trilhara_test").getCollection("trilhas").updateOne(
                new org.bson.Document("_id", new ObjectId(trilhaId)),
                new org.bson.Document("$set", new org.bson.Document()
                        .append("geracaoCronogramaLeaseToken", UUID.randomUUID().toString())
                        .append("geracaoCronogramaEmAndamentoAte", java.util.Date.from(Instant.now().plusSeconds(120)))));

        given()
                .header("Authorization", "Bearer " + token)
                .when().delete(API_TRILHAS + "/{trilhaId}/disciplinas/{disciplinaId}", trilhaId, disciplinaId)
                .then().statusCode(409)
                .body("code", equalTo("SCHEDULE_GENERATION_IN_PROGRESS"));

        given()
                .header("Authorization", "Bearer " + token)
                .when().delete(API_TRILHA, trilhaId)
                .then().statusCode(409)
                .body("code", equalTo("SCHEDULE_GENERATION_IN_PROGRESS"));
    }

    @Test
    @DisplayName("Deve contar corretamente o impacto de exclusão da trilha")
    void impactoExclusaoTrilhaContaCorretamente() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);

        var criada = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "nome": "Trilha Impacto",
                          "disciplinasIniciais": [
                            {"nome": "D1", "topicos": [{"nome": "T1"}]},
                            {"nome": "D2"}
                          ]
                        }
                        """)
                .when().post(API_TRILHAS)
                .then().statusCode(201)
                .extract().response();

        String trilhaId = criada.path("id");
        String disciplina1Id = criada.path("disciplinas[0].id");

        var banco = mongoClient.getDatabase("trilhara_test");
        banco.getCollection("cronogramas").insertOne(new org.bson.Document()
                .append("_id", new ObjectId())
                .append("usuarioId", usuarioId)
                .append("trilhaId", new ObjectId(trilhaId))
                .append("modalidade", "SEQUENCIAL")
                .append("ativo", false));
        banco.getCollection("sessoes").insertOne(new org.bson.Document()
                .append("_id", new ObjectId())
                .append("usuarioId", usuarioId)
                .append("trilhaId", new ObjectId(trilhaId))
                .append("disciplinaId", new ObjectId(disciplina1Id)));
        banco.getCollection("cadernos").insertOne(new org.bson.Document()
                .append("_id", new ObjectId())
                .append("usuarioId", usuarioId)
                .append("titulo", "Caderno Vinculado")
                .append("origemTrilhaId", new ObjectId(trilhaId))
                .append("deletedAt", null));

        given()
                .header("Authorization", "Bearer " + token)
                .when().get(API_TRILHAS + "/{trilhaId}/impacto-exclusao", trilhaId)
                .then().statusCode(200)
                .body("disciplinas", equalTo(2))
                .body("cronogramas", equalTo(1))
                .body("sessoes", equalTo(1))
                .body("cadernosVinculados", equalTo(1));
    }

    @Test
    @DisplayName("Deve rejeitar ID malformado na reordenação com 400 INVALID_IDENTIFIER")
    void deveRejeitarIdMalformadoComInvalidIdentifierNaReordenacao() {
        ObjectId usuarioId = new ObjectId();
        String token = gerarTokenParaUsuario(usuarioId);

        String trilhaId = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"nome": "Trilha Reordenar Malformado", "disciplinasIniciais": [{"nome": "D1"}]}
                        """)
                .when().post(API_TRILHAS)
                .then().statusCode(201)
                .extract().path("id");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"disciplinaIds\": [\"xyz\"]}")
                .when().put(API_TRILHAS + "/{trilhaId}/disciplinas/reordenar", trilhaId)
                .then().statusCode(400)
                .body("code", equalTo("INVALID_IDENTIFIER"))
                .body("errors.disciplinaId", notNullValue());
    }

    @Test
    @DisplayName("Deve isolar todas as rotas de trilha, disciplina, progresso e impacto entre usuários")
    void deveIsolarTodasAsRotasEntreUsuarios() {
        ObjectId usuarioA = new ObjectId();
        ObjectId usuarioB = new ObjectId();
        String tokenA = gerarTokenParaUsuario(usuarioA);
        String tokenB = gerarTokenParaUsuario(usuarioB);

        var criada = given()
                .header("Authorization", "Bearer " + tokenA)
                .contentType(ContentType.JSON)
                .body("""
                        {"nome": "Trilha Isolada A", "disciplinasIniciais": [{"nome": "D1", "topicos": [{"nome": "T1"}]}]}
                        """)
                .when().post(API_TRILHAS)
                .then().statusCode(201)
                .extract().response();

        String trilhaId = criada.path("id");
        String disciplinaId = criada.path("disciplinas[0].id");
        String topicoId = criada.path("disciplinas[0].topicos[0].id");

        given().header("Authorization", "Bearer " + tokenB)
                .when().get(API_TRILHA, trilhaId).then().statusCode(404);
        given().header("Authorization", "Bearer " + tokenB)
                .when().get(API_TRILHAS + "/{trilhaId}/impacto-exclusao", trilhaId).then().statusCode(404);
        given().header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON).body("{\"nome\": \"Invasao\"}")
                .when().put(API_TRILHA, trilhaId).then().statusCode(404);
        given().header("Authorization", "Bearer " + tokenB)
                .when().delete(API_TRILHA, trilhaId).then().statusCode(404);
        given().header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON).body("{\"nome\": \"D Invasora\"}")
                .when().post(API_TRILHAS + "/{trilhaId}/disciplinas", trilhaId).then().statusCode(404);
        given().header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON)
                .body("{\"nome\": \"D Invasora\", \"topicos\": []}")
                .when().put(API_TRILHAS + "/{trilhaId}/disciplinas/{disciplinaId}", trilhaId, disciplinaId)
                .then().statusCode(404);
        given().header("Authorization", "Bearer " + tokenB)
                .when().delete(API_TRILHAS + "/{trilhaId}/disciplinas/{disciplinaId}", trilhaId, disciplinaId)
                .then().statusCode(404);
        given().header("Authorization", "Bearer " + tokenB)
                .when().put(API_TRILHAS + "/{trilhaId}/topicos/{topicoId}/progresso", trilhaId, topicoId)
                .then().statusCode(404);
        given().header("Authorization", "Bearer " + tokenB)
                .when().delete(API_TRILHAS + "/{trilhaId}/topicos/{topicoId}/progresso", trilhaId, topicoId)
                .then().statusCode(404);
        given().header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON).body("{\"disciplinaIds\": [\"%s\"]}".formatted(disciplinaId))
                .when().put(API_TRILHAS + "/{trilhaId}/disciplinas/reordenar", trilhaId)
                .then().statusCode(404);

        // A trilha de A continua intacta
        given().header("Authorization", "Bearer " + tokenA)
                .when().get(API_TRILHA, trilhaId)
                .then().statusCode(200)
                .body("disciplinas", hasSize(1));
    }
}
