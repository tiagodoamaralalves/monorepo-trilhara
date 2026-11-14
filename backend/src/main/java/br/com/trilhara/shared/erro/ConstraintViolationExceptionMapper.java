package br.com.trilhara.shared.erro;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Bean Validation → {@code 400 VALIDATION_ERROR} com {@code errors} por campo.
 */
@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        Map<String, List<String>> errors = agruparErros(exception);

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

    private static Map<String, List<String>> agruparErros(ConstraintViolationException exception) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        if (exception.getConstraintViolations() == null) {
            return errors;
        }

        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            String campo = obterNomeCampo(violation);
            String mensagem = violation.getMessage() != null ? violation.getMessage() : "Valor inválido";
            errors.computeIfAbsent(campo, ignored -> new ArrayList<>()).add(mensagem);
        }
        return errors;
    }

    private static String obterNomeCampo(ConstraintViolation<?> violation) {
        Path propertyPath = violation.getPropertyPath();
        if (propertyPath == null) {
            return "geral";
        }

        String nomeCampo = "";
        for (Path.Node node : propertyPath) {
            nomeCampo = node.getName();
        }
        return nomeCampo == null || nomeCampo.isEmpty() ? "geral" : nomeCampo;
    }
}

