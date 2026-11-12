package br.com.trilhara.shared.erro;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Argumento ilegal é bug de programação, não erro do cliente → {@code 500 INTERNAL_ERROR}
 * com {@code detail} neutro (D-04). Exceções de domínio explícitas têm mapper próprio.
 */
@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(IllegalArgumentException exception) {
        ProblemDetails problem = ProblemDetailsFactory.interno(
                DomainConflictExceptionMapper.resolvePath(uriInfo));

        return Response.status(problem.status())
                .header(ProblemDetailsFactory.HEADER_CORRELATION_ID, ProblemDetailsFactory.novoCorrelationId())
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}

