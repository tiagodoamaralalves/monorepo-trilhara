package br.com.trilhara.shared.erro;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class NotAuthorizedExceptionMapper implements ExceptionMapper<NotAuthorizedException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(NotAuthorizedException exception) {
        String instance = DomainConflictExceptionMapper.resolvePath(uriInfo);
        String msg = exception.getMessage();
        String detail = (msg != null && !msg.isBlank() && !msg.startsWith("HTTP 401"))
                ? msg
                : "Não autorizado.";

        ProblemDetails problem = new ProblemDetails(
                "urn:trilhara:error:unauthorized",
                "Não autorizado",
                Response.Status.UNAUTHORIZED.getStatusCode(),
                detail,
                instance,
                "UNAUTHORIZED",
                null
        );

        return Response.status(Response.Status.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"trilhara\"")
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}
