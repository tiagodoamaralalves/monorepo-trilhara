package br.com.trilhara.shared.erro;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Requisição sintaticamente inválida → {@code 400 VALIDATION_ERROR}.
 *
 * <p>O código legado {@code BAD_REQUEST} foi eliminado do catálogo: qualquer 400 do produto é
 * falha de validação do payload. Mensagens genéricas do container ({@code HTTP 400 ...})
 * são substituídas por um {@code detail} neutro.</p>
 */
@Provider
public class BadRequestExceptionMapper implements ExceptionMapper<BadRequestException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(BadRequestException exception) {
        String msg = exception.getMessage();
        String detail = (msg != null && !msg.isBlank() && !msg.startsWith("HTTP 400"))
                ? msg
                : "Um ou mais campos contêm valores inválidos.";

        ProblemDetails problem = ProblemDetailsFactory.criar(
                CodigoErro.VALIDATION_ERROR,
                detail,
                DomainConflictExceptionMapper.resolvePath(uriInfo));

        return Response.status(problem.status())
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}

