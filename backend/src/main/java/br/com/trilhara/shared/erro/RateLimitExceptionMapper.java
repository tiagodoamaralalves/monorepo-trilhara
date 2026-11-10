package br.com.trilhara.shared.erro;

import br.com.trilhara.auth.RateLimitService.RateLimitExceededException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class RateLimitExceptionMapper implements ExceptionMapper<RateLimitExceededException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(RateLimitExceededException exception) {
        String instance = DomainConflictExceptionMapper.resolvePath(uriInfo);
        String detail = exception.getMessage() != null
                ? exception.getMessage()
                : "Muitas tentativas. Tente novamente mais tarde.";

        ProblemDetails problem = new ProblemDetails(
                "urn:trilhara:error:rate-limit",
                "Limite de requisições excedido",
                Response.Status.TOO_MANY_REQUESTS.getStatusCode(),
                detail,
                instance,
                "RATE_LIMIT_EXCEEDED",
                null
        );

        return Response.status(Response.Status.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}
