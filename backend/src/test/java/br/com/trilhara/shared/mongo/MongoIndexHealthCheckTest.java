package br.com.trilhara.shared.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * readiness reflete o diagnóstico de índices: conflito ⇒ {@code DOWN},
 * drift ⇒ dado informativo sem derrubar.
 */
@DisplayName("MongoIndexHealthCheck: DOWN em conflito, drift informativo")
class MongoIndexHealthCheckTest {

    private static MongoIndexHealthCheck checkCom(DiagnosticoIndices diagnostico) {
        return new MongoIndexHealthCheck(() -> diagnostico, () -> true, () -> diagnostico, 30);
    }

    @Test
    @DisplayName("deveFicarDownComConflitoDeOpcoes: conflito de TTL reporta DOWN com esperado × encontrado")
    void deveFicarDownComConflito() {
        DiagnosticoIndices diagnostico = new DiagnosticoIndices(
                List.of(new ConflitoIndice("refresh_tokens", "idx_refresh_tokens_expiraEm",
                        "key={ \"expiraEm\" : 1 }, unique=false, expireAfterSeconds=0, partialFilterExpression=null",
                        "expireAfterSeconds=null")),
                List.of());

        HealthCheckResponse resposta = checkCom(diagnostico).call();

        assertEquals(HealthCheckResponse.Status.DOWN, resposta.getStatus());
        String dado = String.valueOf(resposta.getData()
                .flatMap(dados -> java.util.Optional.ofNullable(dados.get("refresh_tokens.idx_refresh_tokens_expiraEm")))
                .orElse(""));
        assertTrue(dado.contains("esperado"), "Diagnóstico deve mostrar o esperado");
        assertTrue(dado.contains("encontrado"), "Diagnóstico deve mostrar o encontrado");
    }

    @Test
    @DisplayName("deveFicarUpComDrift: índice idx_ não declarado é reportado sem derrubar o readiness")
    void deveFicarUpComDrift() {
        DiagnosticoIndices diagnostico = new DiagnosticoIndices(List.of(),
                List.of("usuarios.idx_usuarios_nome_legado"));

        HealthCheckResponse resposta = checkCom(diagnostico).call();

        assertEquals(HealthCheckResponse.Status.UP, resposta.getStatus());
        String drift = String.valueOf(resposta.getData()
                .flatMap(dados -> java.util.Optional.ofNullable(dados.get("drift")))
                .orElse(""));
        assertTrue(drift.contains("idx_usuarios_nome_legado"));
    }

    @Test
    @DisplayName("deveFicarUpSemDivergencia: tabela canônica aplicada responde UP sem dados de erro")
    void deveFicarUpSemDivergencia() {
        HealthCheckResponse resposta = checkCom(DiagnosticoIndices.naoExecutado()).call();
        assertEquals(HealthCheckResponse.Status.UP, resposta.getStatus());
        assertEquals(MongoIndexHealthCheck.NOME, resposta.getName());
    }
}
