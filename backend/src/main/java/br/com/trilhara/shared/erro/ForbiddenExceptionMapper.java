package br.com.trilhara.shared.erro;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Operação administrativa sem papel exigido → {@code 403 FORBIDDEN}.
 */
@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ForbiddenException exception) {
        String msg = exception.getMessage();
        String detail = (msg != null && !msg.isBlank() && !msg.startsWith("HTTP 403"))
                ? msg
                : "Acesso negado.";

        ProblemDetails problem = ProblemDetailsFactory.criar(
                CodigoErro.FORBIDDEN,
                detail,
                DomainConflictExceptionMapper.resolvePath(uriInfo));

        return Response.status(problem.status())
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}

