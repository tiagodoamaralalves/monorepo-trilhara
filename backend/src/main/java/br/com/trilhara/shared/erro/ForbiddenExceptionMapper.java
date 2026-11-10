package br.com.trilhara.shared.erro;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ForbiddenException exception) {
        String instance = DomainConflictExceptionMapper.resolvePath(uriInfo);
        String msg = exception.getMessage();
        String detail = (msg != null && !msg.isBlank() && !msg.startsWith("HTTP 403"))
                ? msg
                : "Acesso negado.";

        ProblemDetails problem = new ProblemDetails(
                "urn:trilhara:error:forbidden",
                "Acesso negado",
                Response.Status.FORBIDDEN.getStatusCode(),
                detail,
                instance,
                "FORBIDDEN",
                null
        );

        return Response.status(Response.Status.FORBIDDEN)
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}
