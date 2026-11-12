package br.com.trilhara.shared.erro;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Propriedade desconhecida no corpo da requisição → {@code 400 VALIDATION_ERROR} com
 * {@code errors[propriedade]} (D-05, SEC-07).
 *
 * <p>Com {@code quarkus.jackson.fail-on-unknown-properties=true}, enviar {@code usuarioId},
 * {@code criadoEm} ou qualquer campo de sistema no body falha explicitamente em vez de ser
 * ignorado em silêncio.</p>
 */
@Provider
public class UnrecognizedPropertyExceptionMapper implements ExceptionMapper<UnrecognizedPropertyException> {

    static final String MENSAGEM_PROPRIEDADE_NAO_PERMITIDA = "Propriedade não permitida.";

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(UnrecognizedPropertyException exception) {
        String propriedade = exception.getPropertyName();
        Map<String, List<String>> errors = new LinkedHashMap<>();
        errors.put(
                propriedade != null && !propriedade.isBlank() ? propriedade : "geral",
                List.of(MENSAGEM_PROPRIEDADE_NAO_PERMITIDA));

        ProblemDetails problem = ProblemDetailsFactory.criar(
                CodigoErro.VALIDATION_ERROR,
                "Um ou mais campos contêm valores inválidos.",
                DomainConflictExceptionMapper.resolvePath(uriInfo),
                errors);

        return Response.status(problem.status())
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(problem)
                .build();
    }
}
