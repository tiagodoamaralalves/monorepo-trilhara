package br.com.trilhara.shared.erro;

import br.com.trilhara.auth.UserRepository.EmailJaCadastradoException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * E-mail já cadastrado → {@code 409 DUPLICATE_RESOURCE} (chave única não mapeada para
 * um código de domínio específico, conforme D-04).
 */
@Provider
public class EmailJaCadastradoExceptionMapper implements ExceptionMapper<EmailJaCadastradoException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(EmailJaCadastradoException exception) {
        String detail = exception.getMessage() != null
                ? exception.getMessage()
                : "Já existe uma conta com este e-mail.";

        ProblemDetails problem = ProblemDetailsFactory.criar(
                CodigoErro.DUPLICATE_RESOURCE,
                detail,
                DomainConflictExceptionMapper.resolvePath(uriInfo));

        return Response.status(problem.status())
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}

