package br.com.trilhara.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.trilhara.shared.erro.CorrelationIdFilter;
import br.com.trilhara.shared.erro.ProblemDetailsFactory;

/**
 * {@code X-Correlation-ID} presente em toda resposta, ecoando o valor recebido
 * ou gerando um novo.
 */
@DisplayName("Cabeçalho X-Correlation-ID em toda resposta")
class CorrelationIdFilterTest {

    @Test
    @DisplayName("Gera X-Correlation-ID quando o cliente não envia")
    void geraQuandoAusente() throws Exception {
        var request = Stubs.requestSemCorrelation();
        var response = Stubs.response();

        new CorrelationIdFilter().filter(request, response);

        String correlationId = response.getHeaderString(ProblemDetailsFactory.HEADER_CORRELATION_ID);
        assertNotNull(correlationId);
        UUID.fromString(correlationId);
    }

    @Test
    @DisplayName("Ecoa o X-Correlation-ID recebido do cliente")
    void ecoaQuandoPresente() throws Exception {
        String recebido = UUID.randomUUID().toString();
        var request = Stubs.requestComCorrelation(recebido);
        var response = Stubs.response();

        new CorrelationIdFilter().filter(request, response);

        assertEquals(recebido, response.getHeaderString(ProblemDetailsFactory.HEADER_CORRELATION_ID));
    }

    @Test
    @DisplayName("Não sobrescreve um correlation ID já definido pelo mapper de erro")
    void naoSobrescreveValorDoMapper() throws Exception {
        String definido = "correlation-definido-pelo-mapper";
        var request = Stubs.requestSemCorrelation();
        var response = Stubs.response();
        response.getHeaders().putSingle(ProblemDetailsFactory.HEADER_CORRELATION_ID, definido);

        new CorrelationIdFilter().filter(request, response);

        assertEquals(definido, response.getHeaderString(ProblemDetailsFactory.HEADER_CORRELATION_ID));
    }
}
