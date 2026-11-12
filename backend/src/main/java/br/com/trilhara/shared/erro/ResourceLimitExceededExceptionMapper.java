package br.com.trilhara.shared.erro;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * {@code ResourceLimitExceededException} → {@code 409 RESOURCE_LIMIT_EXCEEDED} (D-04).
 */
@Provider
public class ResourceLimitExceededExceptionMapper implements ExceptionMapper<ResourceLimitExceededException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ResourceLimitExceededException exception) {
        ProblemDetails problem = ProblemDetailsFactory.criar(
                CodigoErro.RESOURCE_LIMIT_EXCEEDED,
                exception.getMessage(),
                DomainConflictExceptionMapper.resolvePath(uriInfo));

        return Response.status(problem.status())
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}
