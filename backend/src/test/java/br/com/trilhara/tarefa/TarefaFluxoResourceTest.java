package br.com.trilhara.tarefa;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
class TarefaFluxoResourceTest {

    private static final String TAREFAS = "/api/tarefas";
    private static final String FLUXO = "/api/tarefas/fluxo";
    private static final String FLUXO_POSICOES = "/api/tarefas/fluxo/posicoes";
    private static final String FLUXO_TAREFA = "/api/tarefas/{id}/fluxo";
    private static final String FLUXO_CONEXOES = "/api/tarefas/{id}/fluxo/conexoes";
    private static final String FLUXO_DESCONECTAR = "/api/tarefas/{id}/fluxo/conexoes/{alvoId}";
    private static final String FLUXO_POSICAO = "/api/tarefas/{id}/fluxo/posicao";

    @Inject
    TokenService tokenService;

    @Inject
    UserRepository userRepository;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        Instant agora = Instant.now();
        Usuario usuarioA = new Usuario(
                new ObjectId().toHexString(),
                "Usuario Fluxo A",
                "fluxoa-" + UUID.randomUUID() + "@teste.com",
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
                new ObjectId().toHexString(),
                "Usuario Fluxo B",
                "fluxob-" + UUID.randomUUID() + "@teste.com",
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
        tokenA = tokenService.gerarAccessToken(usuarioA);
        tokenB = tokenService.gerarAccessToken(usuarioB);
    }

    private String criarTarefa(String token, String titulo) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("titulo", titulo, "prioridade", "MEDIA"))
                .when().post(TAREFAS)
                .then().statusCode(201)
                .extract().path("id");
    }

    @Test
    void deveConectarAntecessoraParSucessoraEDesconectarComSeguranca() {
        String idA = criarTarefa(tokenA, "Etapa 1 - Base");
        String idB1 = criarTarefa(tokenA, "Etapa 2A - Testes");
        String idB2 = criarTarefa(tokenA, "Etapa 2B - Lint");
        String idC = criarTarefa(tokenA, "Etapa 3 - Build");

        // A -> B1 (sucessora)
        given()
                .header("Authorization", "Bearer " + tokenA)
                .contentType(ContentType.JSON)
                .body(Map.of("tarefaAlvoId", idB1, "tipo", "SUCESSORA"))
                .when().post(FLUXO_CONEXOES, idA)
                .then().statusCode(200)
                .body("$", hasSize(2));

        // B1 par com B2
        given()
                .header("Authorization", "Bearer " + tokenA)
                .contentType(ContentType.JSON)
                .body(Map.of("tarefaAlvoId", idB2, "tipo", "PAR"))
                .when().post(FLUXO_CONEXOES, idB1)
                .then().statusCode(200)
                .body("$", hasSize(3));

        // B1 antecessora de C (conectado a partir de C usando ANTECESSORA)
        given()
                .header("Authorization", "Bearer " + tokenA)
                .contentType(ContentType.JSON)
                .body(Map.of("tarefaAlvoId", idB1, "tipo", "ANTECESSORA"))
                .when().post(FLUXO_CONEXOES, idC)
                .then().statusCode(200)
                .body("$", hasSize(4));

        // Impede ciclo C -> A (C já é alcançável a partir de A)
        given()
                .header("Authorization", "Bearer " + tokenA)
                .contentType(ContentType.JSON)
                .body(Map.of("tarefaAlvoId", idA, "tipo", "SUCESSORA"))
                .when().post(FLUXO_CONEXOES, idC)
                .then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));

        // Verifica que listagem de fluxo traz todas e antecessoras calculadas corretamente
        given()
                .header("Authorization", "Bearer " + tokenA)
                .when().get(FLUXO)
                .then().statusCode(200)
                .body("$", hasSize(4))
                .body("find { it.id == '" + idB1 + "' }.antecessorasIds", containsInAnyOrder(idA))
                .body("find { it.id == '" + idB1 + "' }.sucessorasIds", containsInAnyOrder(idC))
                .body("find { it.id == '" + idB1 + "' }.paresIds", containsInAnyOrder(idB2))
                .body("find { it.id == '" + idB2 + "' }.paresIds", containsInAnyOrder(idB1));

        // Verifica que GET /api/tarefas/{id} também expõe antecessorasIds e paresIds
        given()
                .header("Authorization", "Bearer " + tokenA)
                .when().get(TAREFAS + "/{id}", idB1)
                .then().statusCode(200)
                .body("noFluxo", equalTo(true))
                .body("antecessorasIds", hasItem(idA))
                .body("sucessorasIds", hasItem(idC))
                .body("paresIds", hasItem(idB2));

        // Atualiza posição individual e em lote
        given()
                .header("Authorization", "Bearer " + tokenA)
                .contentType(ContentType.JSON)
                .body(Map.of("x", 150.5, "y", 220.0))
                .when().patch(FLUXO_POSICAO, idA)
                .then().statusCode(200)
                .body("posicaoFluxo.x", equalTo(150.5f))
                .body("posicaoFluxo.y", equalTo(220.0f));

        given()
                .header("Authorization", "Bearer " + tokenA)
                .contentType(ContentType.JSON)
                .body(Map.of("posicoes", List.of(
                        Map.of("tarefaId", idA, "x", 80, "y", 120),
                        Map.of("tarefaId", idB1, "x", 420, "y", 120)
                )))
                .when().patch(FLUXO_POSICOES)
                .then().statusCode(200)
                .body("find { it.id == '" + idA + "' }.posicaoFluxo.x", equalTo(80.0f));

        // Desconecta B1 e B2
        given()
                .header("Authorization", "Bearer " + tokenA)
                .when().delete(FLUXO_DESCONECTAR, idB1, idB2)
                .then().statusCode(200)
                .body("find { it.id == '" + idB1 + "' }.paresIds", hasSize(0))
                .body("find { it.id == '" + idB2 + "' }.paresIds", hasSize(0));

        // Remove B1 do fluxo e verifica que A e C perdem referências para B1
        given()
                .header("Authorization", "Bearer " + tokenA)
                .when().delete(FLUXO_TAREFA, idB1)
                .then().statusCode(204);

        given()
                .header("Authorization", "Bearer " + tokenA)
                .when().get(FLUXO)
                .then().statusCode(200)
                .body("$", hasSize(3))
                .body("find { it.id == '" + idA + "' }.sucessorasIds", hasSize(0))
                .body("find { it.id == '" + idC + "' }.antecessorasIds", hasSize(0));

        // Isolamento de tenant: usuário B não consegue enviar tarefa de A ou atualizar posição em lote
        given()
                .header("Authorization", "Bearer " + tokenB)
                .when().post(FLUXO_TAREFA, idA)
                .then().statusCode(404);

        given()
                .header("Authorization", "Bearer " + tokenB)
                .contentType(ContentType.JSON)
                .body(Map.of("posicoes", List.of(Map.of("tarefaId", idA, "x", 0, "y", 0))))
                .when().patch(FLUXO_POSICOES)
                .then().statusCode(400);
    }
}
