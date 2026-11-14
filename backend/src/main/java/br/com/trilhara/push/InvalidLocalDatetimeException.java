package br.com.trilhara.push;

import br.com.trilhara.shared.erro.CodigoErro;
import br.com.trilhara.shared.erro.ProblemDetails;
import br.com.trilhara.shared.erro.ProblemDetailsFactory;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Horário local inexistente por gap de DST → {@code 400 INVALID_LOCAL_DATETIME}.
 */
public class InvalidLocalDatetimeException extends WebApplicationException {

    public InvalidLocalDatetimeException(String message) {
        super(message, resposta(message));
    }

    private static Response resposta(String message) {
        ProblemDetails problem = ProblemDetailsFactory.criar(
                CodigoErro.INVALID_LOCAL_DATETIME,
                message,
                "/api/tarefas");
        return Response.status(problem.status())
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}

