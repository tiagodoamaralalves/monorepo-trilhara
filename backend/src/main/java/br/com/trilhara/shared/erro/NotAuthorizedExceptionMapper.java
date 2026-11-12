package br.com.trilhara.shared.erro;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Falta de autenticação, sessão revogada ou conta em exclusão → {@code 401 UNAUTHORIZED} (D-04).
 */
@Provider
public class NotAuthorizedExceptionMapper implements ExceptionMapper<NotAuthorizedException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(NotAuthorizedException exception) {
        String msg = exception.getMessage();
        String detail = (msg != null && !msg.isBlank() && !msg.startsWith("HTTP 401"))
                ? msg
                : "Não autorizado.";

        ProblemDetails problem = ProblemDetailsFactory.criar(
                CodigoErro.UNAUTHORIZED,
                detail,
                DomainConflictExceptionMapper.resolvePath(uriInfo));

        return Response.status(problem.status())
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"trilhara\"")
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}

