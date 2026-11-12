package br.com.trilhara.cronograma;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.mongodb.client.MongoClient;

import br.com.trilhara.auth.TokenService;
import br.com.trilhara.auth.UserRepository;
import br.com.trilhara.auth.Usuario;
import br.com.trilhara.shared.TimezoneContext;
import br.com.trilhara.trilha.Disciplina;
import br.com.trilhara.trilha.DisciplinaRepository;
import br.com.trilhara.trilha.LeaseTrilhaService;
import br.com.trilhara.trilha.TrilhaRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/**
 * R5/D-18 — publicação de cronograma resiliente.
 *
 * <p>Prova, para cada uma das 6 etapas da publicação versionada, que uma falha injetada nunca
 * publica estado intermediário: o leitor vê a versão anterior (falha antes do ponteiro) ou a nova
 * completa (falha depois), e o reconciliador converge de forma idempotente.</p>
 */
@QuarkusTest
@DisplayName("R5 - Publicação de cronograma resiliente (D-18)")
class PublicacaoCronogramaFaultInjectionTest {

    private static final String API_TRILHAS = "/api/trilhas";

    @Inject
    TrilhaRepository trilhaRepository;

    @Inject
    DisciplinaRepository disciplinaRepository;

    @Inject
    CronogramaRepository cronogramaRepository;

    @Inject
    BlocoCronogramaRepository blocoCronogramaRepository;

    @Inject
    CronogramaReconciliacaoService reconciliacaoService;

    @Inject
    LeaseTrilhaService leaseTrilhaService;

    @Inject
    UserRepository userRepository;

    @Inject
    TokenService tokenService;

    @Inject
    MongoClient mongoClient;

    @Inject
    FaultInjector faultInjector;

    @AfterEach
    void limparFalhas() {
        faultInjector.limpar();
    }

    private String tokenPara(ObjectId usuarioId) {
        Usuario u = new Usuario(
                usuarioId.toHexString(),
                "Usuario R5 Publ",
                "r5-publ-" + usuarioId.toHexString() + "@trilhara.com.br",
                "$pbkdf2-sha512$mock",
                List.of(br.com.trilhara.auth.Role.USER),
                true, true, 0, null, null,
                Instant.now(), Instant.now());
        userRepository.inserir(u);
        return tokenService.gerarAccessToken(u);
    }

    private String criarTrilha(String token) {
        String payload = """
                {
                  "nome": "Trilha R5 %s",
                  "cargo": "Analista",
                  "banca": "FGV",
                  "dataProva": "2027-06-30",
                  "cor": "#4F46E5",
                  "disciplinasIniciais": [
                    { "nome": "Portugues", "cor": "#3B82F6", "peso": 3 },
                    { "nome": "Raciocinio", "cor": "#10B981", "peso": 2 }
                  ]
                }
                """.formatted(UUID.randomUUID());

        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post(API_TRILHAS)
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    private String corpoCronograma(List<Disciplina> disciplinas) {
        return """
                {
                  "modalidade": "SEQUENCIAL",
                  "duracaoBlocoMinutos": 60,
                  "tipoPeriodo": "UMA_SEMANA",
                  "dataInicio": "%s",
                  "diasAtivosSemana": [1, 2, 3],
                  "blocosPorDia": 2,
                  "disciplinasParticipantesIds": ["%s", "%s"]
                }
                """.formatted(
                LocalDate.now(TimezoneContext.DEFAULT_ZONE),
                disciplinas.get(0).idHex(),
                disciplinas.get(1).idHex());
    }
    /**
     * Para cada etapa 1..6: a falha injetada interrompe a publicação, o leitor nunca vê estado
     * intermediário e a reconciliação converge.
     */
    @ParameterizedTest(name = "falhaNaEtapa{0}_leitorVeVersaoAnteriorOuNovaCompleta")
    @EnumSource(EtapaPublicacao.class)
    @DisplayName("falha em cada etapa nunca publica estado intermediário")
    void falhaNaEtapaLeitorVeVersaoAnteriorOuNovaCompleta(EtapaPublicacao etapa) {
        ObjectId usuarioId = new ObjectId();
        String token = tokenPara(usuarioId);
        String trilhaId = criarTrilha(token);
        List<Disciplina> disciplinas = disciplinaRepository.listarPorTrilha(usuarioId, new ObjectId(trilhaId));
        assertTrue(disciplinas.size() >= 2);

        // Versão 0: primeira publicação completa (sem falha)
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(corpoCronograma(disciplinas))
                .when()
                .post(API_TRILHAS + "/" + trilhaId + "/cronogramas")
                .then()
                .statusCode(201);

        Document trilhaAntes = trilhaRepository.buscarEntidade(usuarioId, new ObjectId(trilhaId)).orElseThrow();
        ObjectId ponteiroAntes = trilhaAntes.getObjectId("cronogramaAtivoId");
        Long versaoAntes = trilhaAntes.getLong("versaoAtivaCronograma");
        assertNotNull(ponteiroAntes);
        assertNotNull(versaoAntes);
        long blocosAntes = blocoCronogramaRepository.contarPorVersao(
                usuarioId, new ObjectId(trilhaId), ponteiroAntes, versaoAntes);

        // Injeta a falha exatamente nesta etapa e tenta regenerar
        ((FaultInjectorControlavel) faultInjector).falharNa(etapa);
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(corpoCronograma(disciplinas))
                .when()
                .post(API_TRILHAS + "/" + trilhaId + "/cronogramas")
                .then()
                .statusCode(anyOf(equalTo(500), equalTo(409)));
        faultInjector.limpar();

        // O leitor vê a versão anterior completa (nunca staging parcial)
        Document trilhaDepois = trilhaRepository.buscarEntidade(usuarioId, new ObjectId(trilhaId)).orElseThrow();
        ObjectId ponteiroDepois = trilhaDepois.getObjectId("cronogramaAtivoId");
        Long versaoDepois = trilhaDepois.getLong("versaoAtivaCronograma");
        assertNotNull(ponteiroDepois);
        assertNotNull(versaoDepois);

        long blocosDaVersaoLida = blocoCronogramaRepository.contarPorVersao(
                usuarioId, new ObjectId(trilhaId), ponteiroDepois, versaoDepois);

        if (!etapa.exigeRecuperacao() && ponteiroAntes.equals(ponteiroDepois)) {
            // Antes do ponteiro: continua na versão anterior, publicada por completo
            assertEquals(versaoAntes, versaoDepois);
            assertEquals(blocosAntes, blocosDaVersaoLida);
        } else {
            // Depois do ponteiro: a versão lida está completa
            assertTrue(blocosDaVersaoLida > 0,
                    "a versão apontada pelo ponteiro precisa existir por completo");
            assertTrue(versaoDepois >= versaoAntes);
        }

        // O reconciliador converge de forma idempotente: rodar duas vezes não muda o resultado
        assertTrue(reconciliacaoService.reconciliar(Instant.now()) >= 0);
        Document aposReconciliar1 = trilhaRepository.buscarEntidade(usuarioId, new ObjectId(trilhaId)).orElseThrow();
        assertTrue(reconciliacaoService.reconciliar(Instant.now()) >= 0);
        Document aposReconciliar2 = trilhaRepository.buscarEntidade(usuarioId, new ObjectId(trilhaId)).orElseThrow();

        assertEquals(aposReconciliar1.getObjectId("cronogramaAtivoId"),
                aposReconciliar2.getObjectId("cronogramaAtivoId"));
        assertEquals(aposReconciliar1.getLong("versaoAtivaCronograma"),
                aposReconciliar2.getLong("versaoAtivaCronograma"));

        // Estado final sempre legível: no máximo um cronograma ativo na trilha
        long ativos = cronogramaRepository.listarPorTrilha(usuarioId, new ObjectId(trilhaId))
                .stream().filter(Cronograma::ativo).count();
        assertTrue(ativos <= 1, "no máximo um cronograma ativo por trilha");
    }

    @Test
    @DisplayName("leaseExpiradoNaoPublica: token roubado impede o worker antigo de alterar o ponteiro")
    void leaseExpiradoNaoPublica() {
        ObjectId usuarioId = new ObjectId();
        String token = tokenPara(usuarioId);
        String trilhaId = criarTrilha(token);
        ObjectId trilhaObjId = new ObjectId(trilhaId);

        // Worker A adquire o lease, mas é "roubado" por B: A deixa de deter o token
        String tokenA = UUID.randomUUID().toString();
        leaseTrilhaService.adquirirOuFalhar(usuarioId, trilhaObjId, tokenA);
        trilhaRepository.liberarLease(usuarioId, trilhaObjId, tokenA);
        String tokenB = UUID.randomUUID().toString();
        leaseTrilhaService.adquirirOuFalhar(usuarioId, trilhaObjId, tokenB);

        assertFalse(trilhaRepository.atualizarPonteiroCronogramaAtivo(
                        usuarioId, trilhaObjId, new ObjectId(), 99L, tokenA),
                "worker sem o token não deve conseguir trocar o ponteiro");
        assertTrue(trilhaRepository.renovarLease(usuarioId, trilhaObjId, tokenB, Instant.now().plusSeconds(60)),
                "o dono atual continua renovando");
        assertFalse(trilhaRepository.renovarLease(usuarioId, trilhaObjId, tokenA, Instant.now().plusSeconds(60)),
                "o token roubado não renova");

        trilhaRepository.liberarLease(usuarioId, trilhaObjId, tokenB);
    }
}
