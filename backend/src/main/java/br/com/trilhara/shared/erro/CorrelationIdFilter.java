package br.com.trilhara.shared.erro;

import java.io.IOException;
import java.util.UUID;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Garante {@code X-Correlation-ID} em toda resposta (D-04).
 *
 * <p>Se o cliente enviar o cabeçalho, ele é ecoado; caso contrário um UUID é gerado.
 * O mesmo valor é exposto ao frontend via {@code Access-Control-Expose-Headers}.</p>
 */
@Provider
public class CorrelationIdFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        if (responseContext.getHeaders().containsKey(ProblemDetailsFactory.HEADER_CORRELATION_ID)) {
            return;
        }
        String recebido = requestContext.getHeaderString(ProblemDetailsFactory.HEADER_CORRELATION_ID);
        String correlationId = recebido != null && !recebido.isBlank()
                ? recebido.trim()
                : UUID.randomUUID().toString();
        responseContext.getHeaders().putSingle(ProblemDetailsFactory.HEADER_CORRELATION_ID, correlationId);
    }
}
