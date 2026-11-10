package br.com.trilhara.shared.erro;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Representação RFC 9457 (Problem Details for HTTP APIs) com extensões Trilhara.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetails(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String code,
        Map<String, List<String>> errors
) {

    public static final String MEDIA_TYPE = "application/problem+json";

    public ProblemDetails {
        if (errors != null) {
            Map<String, List<String>> defensivo = new LinkedHashMap<>();
            errors.forEach((k, v) -> defensivo.put(k, v != null ? List.copyOf(v) : Collections.emptyList()));
            errors = Collections.unmodifiableMap(defensivo);
        }
    }

    public static ProblemDetails of(String type, String title, int status, String detail, String instance, String code) {
        return new ProblemDetails(type, title, status, detail, instance, code, null);
    }

    public static ProblemDetails of(String type, String title, int status, String detail, String instance, String code, Map<String, List<String>> errors) {
        return new ProblemDetails(type, title, status, detail, instance, code, errors);
    }
}
