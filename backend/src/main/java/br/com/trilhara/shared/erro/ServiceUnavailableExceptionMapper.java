package br.com.trilhara.shared.erro;

import org.jboss.logging.Logger;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * {@code ServiceUnavailableException} → {@code 503 SERVICE_UNAVAILABLE}.
 */
@Provider
public class ServiceUnavailableExceptionMapper implements ExceptionMapper<ServiceUnavailableException> {

    private static final Logger LOG = Logger.getLogger(ServiceUnavailableExceptionMapper.class);

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ServiceUnavailableException exception) {
        String correlationId = ProblemDetailsFactory.novoCorrelationId();
        LOG.errorf(exception, "Serviço indisponível [correlationId=%s]", correlationId);

        ProblemDetails problem = ProblemDetailsFactory.criar(
                CodigoErro.SERVICE_UNAVAILABLE,
                exception.getMessage(),
                DomainConflictExceptionMapper.resolvePath(uriInfo));

        return Response.status(problem.status())
                .header(ProblemDetailsFactory.HEADER_CORRELATION_ID, correlationId)
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}
