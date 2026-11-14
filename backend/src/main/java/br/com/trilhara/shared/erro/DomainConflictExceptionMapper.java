package br.com.trilhara.shared.erro;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Conflito de estado de negócio → {@code 409} com o {@code code} do catálogo.
 */
@Provider
public class DomainConflictExceptionMapper implements ExceptionMapper<DomainConflictException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(DomainConflictException exception) {
        ProblemDetails problem = ProblemDetailsFactory.criar(
                exception.getCodigo(),
                exception.getMessage(),
                resolvePath(uriInfo),
                exception.getErrors());

        return Response.status(problem.status())
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }

    static String resolvePath(UriInfo uriInfo) {
        if (uriInfo == null) {
            return "/";
        }
        try {
            return ProblemDetailsFactory.normalizarInstance(uriInfo.getPath());
        } catch (Exception e) {
            return "/";
        }
    }
}

