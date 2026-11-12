package br.com.trilhara.shared.erro;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * {@code TrashItemExpiredException} → {@code 404 TRASH_ITEM_EXPIRED} (D-04).
 */
@Provider
public class TrashItemExpiredExceptionMapper implements ExceptionMapper<TrashItemExpiredException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(TrashItemExpiredException exception) {
        ProblemDetails problem = ProblemDetailsFactory.criar(
                CodigoErro.TRASH_ITEM_EXPIRED,
                exception.getMessage(),
                DomainConflictExceptionMapper.resolvePath(uriInfo));

        return Response.status(problem.status())
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}
