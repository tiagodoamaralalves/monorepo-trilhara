package br.com.trilhara.shared.erro;

import br.com.trilhara.auth.RateLimitService.RateLimitExceededException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Limite de requisições excedido → {@code 429 RATE_LIMITED} com {@code Retry-After}.
 */
@Provider
public class RateLimitExceptionMapper implements ExceptionMapper<RateLimitExceededException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(RateLimitExceededException exception) {
        String detail = exception.getMessage() != null
                ? exception.getMessage()
                : "Muitas tentativas. Tente novamente mais tarde.";

        ProblemDetails problem = ProblemDetailsFactory.criar(
                CodigoErro.RATE_LIMITED,
                detail,
                DomainConflictExceptionMapper.resolvePath(uriInfo));

        return Response.status(problem.status())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}

