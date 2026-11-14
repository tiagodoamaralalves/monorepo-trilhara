package br.com.trilhara.shared.erro;

import org.jboss.logging.Logger;

import com.mongodb.MongoTimeoutException;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Timeout de servidor/seleção do MongoDB → {@code 503 SERVICE_UNAVAILABLE} com
 * {@code X-Correlation-ID}.
 */
@Provider
public class MongoTimeoutExceptionMapper implements ExceptionMapper<MongoTimeoutException> {

    private static final Logger LOG = Logger.getLogger(MongoTimeoutExceptionMapper.class);

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(MongoTimeoutException exception) {
        String correlationId = ProblemDetailsFactory.novoCorrelationId();
        LOG.errorf(exception, "Timeout no MongoDB [correlationId=%s]", correlationId);

        ProblemDetails problem = ProblemDetailsFactory.criar(
                CodigoErro.SERVICE_UNAVAILABLE,
                "Banco de dados indisponível. Tente novamente em instantes.",
                DomainConflictExceptionMapper.resolvePath(uriInfo));

        return Response.status(problem.status())
                .header(ProblemDetailsFactory.HEADER_CORRELATION_ID, correlationId)
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}
