package br.com.trilhara.edital;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
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
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;

@QuarkusTest
class EditalResourceTest {

    private static final String ADMIN = "/api/admin/editais";
    private static final String PUBLICOS = "/api/editais";

    @Inject
    TokenService tokenService;

    @Inject
    UserRepository userRepository;

    private String tokenAdmin;

    @BeforeEach
    void setupAdmin() {
        if (tokenAdmin == null) {
            String adminId = new ObjectId().toHexString();
            Usuario admin = new Usuario(
                    adminId, "Admin Test", "admin-" + adminId + "@trilhara.com.br",
                    "$2a$12$dummyHashParaTesteDeAdminDeEditais1234567890", List.of(Role.ADMIN), true, true, 0, null, null, Instant.now(), Instant.now());
            userRepository.inserir(admin);
            tokenAdmin = tokenService.gerarAccessToken(admin);
        }
    }

    private RequestSpecification adminGiven() {
        return given().header("Authorization", "Bearer " + tokenAdmin);
    }

    @Test
    void deveCriarPublicarConsultarEArquivarEdital() {
        String id = criar("""
                {
                  "nome": "Edital %s",
                  "cargo": "Analista",
                  "orgao": "Tribunal de Justiça",
                  "banca": "Banca Teste",
                  "esfera": "ESTADUAL",
                  "uf": "sp",
                  "inicioInscricao": "2026-03-01",
                  "fimInscricao": "2026-03-31",
                  "dataProva": "2026-06-14",
                  "disciplinas": [{"id":"portugues","nome":"Português","topicos":[{"id":"interpretacao","nome":"Interpretação"}]}]
                }
                """.formatted(UUID.randomUUID()));

        adminGiven().when().get(ADMIN + "/{id}", id)
                .then().statusCode(200)
                .body("id", equalTo(id))
                .body("status", equalTo("RASCUNHO"))
                .body("versao", equalTo(1))
                .body("uf", equalTo("SP"))
                .body("disciplinas[0].peso", equalTo(1));
        given().when().get(PUBLICOS + "/{id}", id).then().statusCode(404);

        adminGiven().contentType(ContentType.JSON)
                .body("{\"status\":\"PUBLICADA\"}")
                .when().patch(ADMIN + "/{id}/status", id)
                .then().statusCode(200).body("status", equalTo("PUBLICADA"));
        given().when().get(PUBLICOS + "/{id}", id)
                .then().statusCode(200)
                .body("id", equalTo(id))
                .body("$", not(hasKey("status")))
                .body("$", not(hasKey("versao")))
                .body("$", not(hasKey("criadoEm")))
                .body("disciplinas[0].topicos[0].id", equalTo("interpretacao"));

        adminGiven().contentType(ContentType.JSON)
                .body("{\"status\":\"ARQUIVADA\"}")
                .when().patch(ADMIN + "/{id}/status", id)
                .then().statusCode(200).body("status", equalTo("ARQUIVADA"));
        given().when().get(PUBLICOS + "/{id}", id).then().statusCode(404);
        adminGiven().when().delete(ADMIN + "/{id}", id).then().statusCode(204);
    }

    @Test
    void deveAtualizarEditalIncrementandoVersaoSemAlterarStatus() {
        String id = criar(payload("Atualização " + UUID.randomUUID()));
        adminGiven().contentType(ContentType.JSON)
                .body(payload("Edital atualizado " + UUID.randomUUID()))
                .when().put(ADMIN + "/{id}", id)
                .then().statusCode(200)
                .body("status", equalTo("RASCUNHO"))
                .body("versao", equalTo(2))
                .body("id", equalTo(id));
        adminGiven().contentType(ContentType.JSON)
                .body(payloadComStatus("Status só via patch", "PUBLICADA"))
                .when().put(ADMIN + "/{id}", id)
                .then().statusCode(400);
        adminGiven().when().get(ADMIN + "/{id}", id).then().statusCode(200).body("versao", equalTo(2));
        adminGiven().when().delete(ADMIN + "/{id}", id).then().statusCode(204);
    }

    @Test
    void deveListarSomentePublicadosComFiltrosEPaginacao() {
        String banca = "Banca-" + UUID.randomUUID();
        String idRascunho = criar(payloadComBanca("Rascunho", banca));
        String termoBusca = "BuscaUnica" + UUID.randomUUID().toString().replace("-", "");
        String idPublicado1 = criar(payloadComBanca("Publicado um " + termoBusca, banca));
        String idPublicado2 = criar(payloadComBanca("Publicado dois", banca));
        publicar(idPublicado1);
        publicar(idPublicado2);

        given().queryParam("banca", banca).queryParam("size", 1)
                .when().get(PUBLICOS)
                .then().statusCode(200)
                .body("size", equalTo(1))
                .body("total", equalTo(2));
        given().queryParam("banca", banca).queryParam("esfera", "ESTADUAL")
                .when().get(PUBLICOS)
                .then().statusCode(200)
                .body("total", equalTo(2));
        adminGiven().queryParam("banca", banca).queryParam("status", "RASCUNHO")
                .when().get(ADMIN)
                .then().statusCode(200)
                .body("total", equalTo(1))
                .body("itens[0].id", equalTo(idRascunho));
        given().queryParam("banca", banca).queryParam("q", termoBusca)
                .when().get(PUBLICOS)
                .then().statusCode(200)
                .body("total", equalTo(1))
                .body("itens[0].id", equalTo(idPublicado1));

        for (String id : new String[] {idRascunho, idPublicado1, idPublicado2}) {
            adminGiven().when().delete(ADMIN + "/{id}", id).then().statusCode(204);
        }
    }

    @Test
    void deveRejeitarDatasUfPesoIdsDuplicadosETransicoesInvalidas() {
        for (String invalido : new String[] {
                payloadDatas("2026-04-01", "2026-03-01", "2026-06-14"),
                payloadDatas("2026-03-01", "2026-03-31", "2026-03-30"),
                payloadEsferaFederalComUf(),
                payloadComPeso(0),
                payloadComIdsDuplicados()
        }) {
            adminGiven().contentType(ContentType.JSON).body(invalido)
                    .when().post(ADMIN).then().statusCode(400);
        }

        adminGiven().contentType(ContentType.JSON)
                .body("{\"status\":\"ARQUIVADA\"}")
                .when().patch(ADMIN + "/{id}/status", "inexistente")
                .then().statusCode(404);
        String id = criar(payload("Transição " + UUID.randomUUID()));
        adminGiven().contentType(ContentType.JSON)
                .body("{\"status\":\"ARQUIVADA\"}")
                .when().patch(ADMIN + "/{id}/status", id)
                .then().statusCode(400);
        given().queryParam("size", 101).when().get(PUBLICOS).then().statusCode(400);
        adminGiven().queryParam("page", -1).when().get(ADMIN).then().statusCode(400);
        adminGiven().when().delete(ADMIN + "/{id}", id).then().statusCode(204);
    }

    @Test
    void deveRetornarListaVaziaSemEditaisPublicadosNoFiltro() {
        String buscaUnica = "sem-resultados-" + UUID.randomUUID();
        given().queryParam("banca", buscaUnica).when().get(PUBLICOS)
                .then().statusCode(200)
                .body("total", equalTo(0))
                .body("itens.size()", equalTo(0))
                .body("page", equalTo(0))
                .body("size", equalTo(20))
                .body("itens", notNullValue());
    }

    @Test
    void deveBuscarPorNomeCargoBancaEFiltrarEmAndamento() {
        String chave = "Chave" + UUID.randomUUID().toString().replace("-", "");
        String futuro = java.time.LocalDate.now().plusDays(30).toString();
        String passado = java.time.LocalDate.now().minusDays(30).toString();
        String emAndamento = criar(payloadProva(chave + " futuro", "Auditor", futuro));
        String encerrado = criar(payloadProva(chave + " passado", "Analista", passado));
        String semData = criar(payloadProva(chave + " sem data", "Analista", null));
        for (String id : new String[] {emAndamento, encerrado, semData}) {
            publicar(id);
        }

        given().queryParam("q", chave.toLowerCase()).when().get(PUBLICOS)
                .then().statusCode(200).body("total", equalTo(3));
        given().queryParam("q", chave).queryParam("uf", "sp").when().get(PUBLICOS)
                .then().statusCode(200).body("total", equalTo(3));
        given().queryParam("q", chave).queryParam("uf", "rj").when().get(PUBLICOS)
                .then().statusCode(200).body("total", equalTo(0));
        given().queryParam("q", chave).queryParam("emAndamento", true).when().get(PUBLICOS)
                .then().statusCode(200)
                .body("total", equalTo(1))
                .body("itens[0].id", equalTo(emAndamento));
        given().queryParam("q", "Banca Teste " + chave).when().get(PUBLICOS)
                .then().statusCode(200).body("total", equalTo(0));

        for (String id : new String[] {emAndamento, encerrado, semData}) {
            adminGiven().when().delete(ADMIN + "/{id}", id).then().statusCode(204);
        }
    }

    private String payloadProva(String nome, String cargo, String dataProva) {
        String prova = dataProva == null ? "" : "\"dataProva\":\"" + dataProva + "\",";
        return """
                {
                  "nome": "%s",
                  "cargo": "%s",
                  "orgao": "Tribunal de Justiça",
                  "banca": "Banca Teste",
                  "esfera": "ESTADUAL",
                  "uf": "SP",
                  %s
                  "disciplinas": [{"id":"portugues","nome":"Português"}]
                }
                """.formatted(nome, cargo, prova);
    }

    private String criar(String payload) {
        return adminGiven().contentType(ContentType.JSON).body(payload)
                .when().post(ADMIN)
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("status", equalTo("RASCUNHO"))
                .extract().path("id");
    }

    private void publicar(String id) {
        adminGiven().contentType(ContentType.JSON).body("{\"status\":\"PUBLICADA\"}")
                .when().patch(ADMIN + "/{id}/status", id)
                .then().statusCode(200);
    }

    private String payload(String nome) {
        return payloadComBanca(nome, "Banca Teste");
    }

    private String payloadComBanca(String nome, String banca) {
        return """
                {
                  "nome": "%s",
                  "cargo": "Analista",
                  "orgao": "Tribunal de Justiça",
                  "banca": "%s",
                  "esfera": "ESTADUAL",
                  "uf": "SP",
                  "disciplinas": [{"id":"portugues","nome":"Português"}]
                }
                """.formatted(nome, banca);
    }

    private String payloadComStatus(String nome, String status) {
        return payload(nome).replace("\"disciplinas\"", "\"status\":\"" + status + "\",\"disciplinas\"");
    }

    private String payloadDatas(String inicio, String fim, String prova) {
        return """
                {
                  "nome":"Datas inválidas","cargo":"Analista","orgao":"Tribunal",
                  "banca":"Teste","esfera":"FEDERAL",
                  "inicioInscricao":"%s","fimInscricao":"%s","dataProva":"%s",
                  "disciplinas":[]
                }
                """.formatted(inicio, fim, prova);
    }

    private String payloadEsferaFederalComUf() {
        return """
                {"nome":"Federal","cargo":"Analista","orgao":"União","banca":"Teste",
                 "esfera":"FEDERAL","uf":"SP","disciplinas":[]}
                """;
    }

    private String payloadComPeso(int peso) {
        return """
                {"nome":"Peso inválido","cargo":"Analista","orgao":"Tribunal","banca":"Teste",
                 "esfera":"FEDERAL","disciplinas":[{"id":"d1","nome":"Disciplina","peso":%d}]}
                """.formatted(peso);
    }

    private String payloadComIdsDuplicados() {
        return """
                {"nome":"IDs duplicados","cargo":"Analista","orgao":"Tribunal","banca":"Teste",
                 "esfera":"FEDERAL","disciplinas":[
                   {"id":"d1","nome":"Uma"},{"id":"d1","nome":"Outra"}
                 ]}
                """;
    }
}
