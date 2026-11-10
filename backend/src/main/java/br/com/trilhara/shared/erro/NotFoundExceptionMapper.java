package br.com.trilhara.shared.erro;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(NotFoundException exception) {
        String instance = DomainConflictExceptionMapper.resolvePath(uriInfo);
        String msg = exception.getMessage();
        String detail = (msg != null && !msg.isBlank() && !msg.startsWith("HTTP 404"))
                ? msg
                : "Recurso não encontrado.";

        ProblemDetails problem = new ProblemDetails(
                "urn:trilhara:error:not-found",
                "Recurso não encontrado",
                Response.Status.NOT_FOUND.getStatusCode(),
                detail,
                instance,
                "NOT_FOUND",
                null
        );

        return Response.status(Response.Status.NOT_FOUND)
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}
