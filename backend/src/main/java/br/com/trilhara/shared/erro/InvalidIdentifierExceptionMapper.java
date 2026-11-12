package br.com.trilhara.shared.erro;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * {@code InvalidIdentifierException} → {@code 400 INVALID_IDENTIFIER} com
 * {@code errors[campo]} (D-03).
 */
@Provider
public class InvalidIdentifierExceptionMapper implements ExceptionMapper<InvalidIdentifierException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(InvalidIdentifierException exception) {
        ProblemDetails problem = ProblemDetailsFactory.criarCampo(
                CodigoErro.INVALID_IDENTIFIER,
                exception.getMessage(),
                DomainConflictExceptionMapper.resolvePath(uriInfo),
                exception.getCampo(),
                exception.getMessage());

        return Response.status(problem.status())
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}
