package br.com.trilhara.shared.erro;

import org.jboss.logging.Logger;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Último recurso: qualquer exceção não tratada vira {@code 500 INTERNAL_ERROR} neutro e
 * correlacionável. Nunca expõe stack trace, SQL, consulta ou dado pessoal.
 */
@Provider
public class GlobalThrowableMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GlobalThrowableMapper.class);

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof WebApplicationException webAppEx && webAppEx.getResponse() != null) {
            Response existing = webAppEx.getResponse();
            if (existing.getEntity() instanceof ProblemDetails) {
                return existing;
            }
            int status = existing.getStatus();
            if (status < 500) {
                ProblemDetails problem = ProblemDetailsFactory.criar(
                        CodigoErro.VALIDATION_ERROR,
                        "Não foi possível concluir a requisição.",
                        DomainConflictExceptionMapper.resolvePath(uriInfo));
                return Response.fromResponse(existing)
                        .type(ProblemDetails.MEDIA_TYPE)
                        .entity(problem)
                        .build();
            }
        }

        String correlationId = ProblemDetailsFactory.novoCorrelationId();
        LOG.errorf(exception, "Erro interno não tratado [correlationId=%s]", correlationId);

        ProblemDetails problem = ProblemDetailsFactory.interno(
                DomainConflictExceptionMapper.resolvePath(uriInfo));

        return Response.status(problem.status())
                .header(ProblemDetailsFactory.HEADER_CORRELATION_ID, correlationId)
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}

