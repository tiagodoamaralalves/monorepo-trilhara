package br.com.trilhara.shared.erro;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * {@code DuplicateResourceException} → {@code 409 DUPLICATE_RESOURCE} (D-04).
 */
@Provider
public class DuplicateResourceExceptionMapper implements ExceptionMapper<DuplicateResourceException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(DuplicateResourceException exception) {
        ProblemDetails problem = ProblemDetailsFactory.criar(
                CodigoErro.DUPLICATE_RESOURCE,
                exception.getMessage(),
                DomainConflictExceptionMapper.resolvePath(uriInfo));

        return Response.status(problem.status())
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}
