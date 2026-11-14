package br.com.trilhara.shared.erro;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Recurso inexistente ou de outro tenant → {@code 404 NOT_FOUND}, sem revelar existência.
 */
@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(NotFoundException exception) {
        String msg = exception.getMessage();
        String detail = (msg != null && !msg.isBlank() && !msg.startsWith("HTTP 404"))
                ? msg
                : "Recurso não encontrado.";

        ProblemDetails problem = ProblemDetailsFactory.criar(
                CodigoErro.NOT_FOUND,
                detail,
                DomainConflictExceptionMapper.resolvePath(uriInfo));

        return Response.status(problem.status())
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}

