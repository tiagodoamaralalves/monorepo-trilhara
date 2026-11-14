package br.com.trilhara.shared.erro;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * {@code ValidationException} → {@code 400 VALIDATION_ERROR} com {@code errors[campo]}.
 */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ValidationException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ValidationException exception) {
        ProblemDetails problem = ProblemDetailsFactory.criarCampo(
                CodigoErro.VALIDATION_ERROR,
                "Um ou mais campos contêm valores inválidos.",
                DomainConflictExceptionMapper.resolvePath(uriInfo),
                exception.getCampo(),
                exception.getMessage());

        return Response.status(problem.status())
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}
