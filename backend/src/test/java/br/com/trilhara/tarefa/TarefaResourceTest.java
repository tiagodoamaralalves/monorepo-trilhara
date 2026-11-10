package br.com.trilhara.tarefa;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.trilhara.auth.Role;
import br.com.trilhara.auth.TokenService;
import br.com.trilhara.auth.UserRepository;
import br.com.trilhara.auth.Usuario;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

@QuarkusTest
class TarefaResourceTest {

    private static final String LISTAS = "/api/listas-tarefas";
    private static final String TAREFAS = "/api/tarefas";
    private static final String TAREFA = "/api/tarefas/{id}";
    private static final String CONCLUSAO = "/api/tarefas/{id}/conclusao";

    @Inject
    TokenService tokenService;

    @Inject
    UserRepository userRepository;

    @Inject
    ListaTarefasRepository listaRepository;

    @Inject
    TarefaRepository tarefaRepository;

    private String tokenUsuarioA;
    private String tokenUsuarioB;
    private String usuarioAId;
    private String usuarioBId;

    @BeforeEach
    void setUp() {
        usuarioAId = new ObjectId().toHexString();
        usuarioBId = new ObjectId().toHexString();

        Instant agora = Instant.now();
        Usuario usuarioA = new Usuario(
                usuarioAId,
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
                usuarioBId,
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
    void deveExigirAutenticacaoNasRotas() {
        given().when().get(LISTAS).then().statusCode(401);
        given().when().get(TAREFAS).then().statusCode(401);
        given().contentType(ContentType.JSON).body("{\"nome\":\"Teste\"}").when().post(LISTAS).then().statusCode(401);
        given().contentType(ContentType.JSON).body("{\"titulo\":\"Teste\"}").when().post(TAREFAS).then().statusCode(401);
    }

    @Test
    void fluxoCompletoDeListasETarefasComSubtarefas() {
        // 1. Cria lista personalizada para Usuario A
        String listaPayload = """
                {
                  "nome": "Estudos e Concursos",
                  "cor": "#3b82f6"
                }
                """;

        String listaId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .body(listaPayload)
                .when().post(LISTAS)
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("nome", equalTo("Estudos e Concursos"))
                .body("cor", equalTo("#3b82f6"))
                .extract().path("id");

        // 2. Cria tarefa vinculada à lista com subtarefas, observações e data
        String hoje = LocalDate.now().toString();
        String tarefaPayload = """
                {
                  "listaId": "%s",
                  "titulo": "Revisar Direito Constitucional",
                  "descricao": "Focar em Direitos e Garantias Fundamentais",
                  "dataVencimento": "%s",
                  "horario": "14:30",
                  "prioridade": "ALTA",
                  "subtarefas": [
                    {"titulo": "Ler artigos 5º ao 17", "concluida": true},
                    {"titulo": "Fazer 20 questões", "concluida": false}
                  ]
                }
                """.formatted(listaId, hoje);

        String tarefaId = given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .body(tarefaPayload)
                .when().post(TAREFAS)
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("titulo", equalTo("Revisar Direito Constitucional"))
                .body("descricao", equalTo("Focar em Direitos e Garantias Fundamentais"))
                .body("prioridade", equalTo("ALTA"))
                .body("concluida", equalTo(false))
                .body("subtarefas", hasSize(2))
                .body("subtarefas[0].concluida", equalTo(true))
                .body("subtarefas[1].concluida", equalTo(false))
                .extract().path("id");

        // 3. Consulta tarefa por ID
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .when().get(TAREFA, tarefaId)
                .then().statusCode(200)
                .body("titulo", equalTo("Revisar Direito Constitucional"))
                .body("listaId", equalTo(listaId));

        // 4. Alterna conclusão da tarefa
        given()
                .header("Authorization", "Bearer " + tokenUsuarioA)
                .contentType(ContentType.JSON)
                .body("{\"concluida\": true}")
                .when().patch(CONCLUSAO, tarefaId)
                .then().statusCode(200)
                .body("concluida", equalTo(true))
                .body("concluidaEm", notNullValue());

        // 5. Isolamento entre usuários: Usuário B não acessa tarefa do Usuário A
        given()
                .header("Authorization", "Bearer " + tokenUsuarioB)
                .when().get(TAREFA, tarefaId)
                .then().statusCode(404);
    }
}
