package br.com.trilhara.shared.erro;

import br.com.trilhara.auth.UserRepository.EmailJaCadastradoException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class EmailJaCadastradoExceptionMapper implements ExceptionMapper<EmailJaCadastradoException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(EmailJaCadastradoException exception) {
        String instance = DomainConflictExceptionMapper.resolvePath(uriInfo);
        String detail = exception.getMessage() != null
                ? exception.getMessage()
                : "Não foi possível concluir o cadastro com os dados informados.";

        ProblemDetails problem = new ProblemDetails(
                "urn:trilhara:error:conflict",
                "Conflito de negócio",
                Response.Status.CONFLICT.getStatusCode(),
                detail,
                instance,
                "EMAIL_JA_CADASTRADO",
                null
        );

        return Response.status(Response.Status.CONFLICT)
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}
