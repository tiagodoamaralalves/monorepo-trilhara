package br.com.trilhara.tarefa;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Instant;
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
class ListaTarefasResourceTest {

    private static final String LISTAS = "/api/listas-tarefas";
    private static final String LISTA = "/api/listas-tarefas/{id}";

    @Inject
    TokenService tokenService;

    @Inject
    UserRepository userRepository;

    @Inject
    ListaTarefasRepository listaRepository;

    private String tokenUsuario;
    private String usuarioId;

    @BeforeEach
    void setUp() {
        usuarioId = new ObjectId().toHexString();
        Instant agora = Instant.now();
        Usuario usuario = new Usuario(
                usuarioId,
                "Usuario Teste",
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
    void crudCompletoListaPersonalizada() {
        // Criar
        String id = given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .contentType(ContentType.JSON)
                .body("{\"nome\":\"Trabalho e Projetos\",\"cor\":\"#10b981\"}")
                .when().post(LISTAS)
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("nome", equalTo("Trabalho e Projetos"))
                .body("cor", equalTo("#10b981"))
                .extract().path("id");

        // Buscar
        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .when().get(LISTA, id)
                .then().statusCode(200)
                .body("nome", equalTo("Trabalho e Projetos"));

        // Atualizar
        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .contentType(ContentType.JSON)
                .body("{\"nome\":\"Projetos 2026\",\"cor\":\"#059669\"}")
                .when().put(LISTA, id)
                .then().statusCode(200)
                .body("nome", equalTo("Projetos 2026"))
                .body("cor", equalTo("#059669"));

        // Listar
        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .when().get(LISTAS)
                .then().statusCode(200)
                .body("$", hasSize(1));

        // Excluir (vai para lixeira)
        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .when().delete(LISTA, id)
                .then().statusCode(200)
                .body("id", equalTo(id));

        // Confirmar que não aparece mais em ativas
        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .when().get(LISTA, id)
                .then().statusCode(404);

        // Restaurar
        given()
                .header("Authorization", "Bearer " + tokenUsuario)
                .when().post(LISTA + "/restaurar", id)
                .then().statusCode(200)
                .body("nome", equalTo("Projetos 2026"));
    }
}
