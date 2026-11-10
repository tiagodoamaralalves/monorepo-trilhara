package br.com.trilhara.shared.erro;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(IllegalArgumentException exception) {
        String instance = DomainConflictExceptionMapper.resolvePath(uriInfo);
        String msg = exception.getMessage();
        String detail = (msg != null && !msg.isBlank()) ? msg : "Requisição inválida.";

        ProblemDetails problem = new ProblemDetails(
                "urn:trilhara:error:bad-request",
                "Requisição inválida",
                Response.Status.BAD_REQUEST.getStatusCode(),
                detail,
                instance,
                "BAD_REQUEST",
                null
        );

        return Response.status(Response.Status.BAD_REQUEST)
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}
