package br.com.trilhara.shared.erro;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fábrica única de {@link ProblemDetails} do Trilhara.
 *
 * <p>Todos os mapeadores delegam para cá. Isso garante que todo erro use
 * {@link ProblemDetails#MEDIA_TYPE}, {@code type} no padrão
 * {@code urn:trilhara:problem:*}, {@code instance} normalizado e que nenhum
 * {@code detail} seja montado ad hoc em outro lugar.</p>
 *
 * <p>Regras fixas:</p>
 * <ul>
 * <li>{@code instance} é o path da requisição com barra inicial e <strong>sem</strong> query string;</li>
 * <li>{@code detail} é texto em pt-BR, sem stack trace, SQL, consulta ou valor enviado pelo cliente;</li>
 * <li>o header {@code X-Correlation-ID} acompanha toda resposta de erro 5xx.</li>
 * </ul>
 */
public final class ProblemDetailsFactory {

    public static final String HEADER_CORRELATION_ID = "X-Correlation-ID";

    private ProblemDetailsFactory() {
    }

    /** Problem Details completo, sem erro de campo. */
    public static ProblemDetails criar(CodigoErro codigo, String detail, String instance) {
        return criar(codigo, detail, instance, null);
    }

    /** Problem Details com extensão {@code errors} (campo → mensagens). */
    public static ProblemDetails criar(
            CodigoErro codigo,
            String detail,
            String instance,
            Map<String, List<String>> errors) {
        return new ProblemDetails(
                codigo.typeUri(),
                codigo.title(),
                codigo.httpStatus(),
                detail != null && !detail.isBlank() ? detail : codigo.title(),
                normalizarInstance(instance),
                codigo.name(),
                errors == null || errors.isEmpty() ? null : errors);
    }

    /** Problem Details de um único campo inválido. */
    public static ProblemDetails criarCampo(CodigoErro codigo, String detail, String instance, String campo, String mensagem) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        String chave = campo != null && !campo.isBlank() ? campo : "geral";
        errors.put(chave, List.of(mensagem != null && !mensagem.isBlank() ? mensagem : "Valor inválido."));
        return criar(codigo, detail, instance, errors);
    }

    /** {@code Internal Server Error} genérico: nunca expõe a causa original. */
    public static ProblemDetails interno(String instance) {
        return criar(
                CodigoErro.INTERNAL_ERROR,
                "Ocorreu um erro interno inesperado. Tente novamente mais tarde.",
                instance);
    }

    /**
 * Normaliza o path usado como {@code instance}: barra inicial, sem query string
 * e nunca nulo (vira {@code "/"}).
 */
    public static String normalizarInstance(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "/";
        }
        String semQuery = rawPath;
        int interrogacao = semQuery.indexOf('?');
        if (interrogacao >= 0) {
            semQuery = semQuery.substring(0, interrogacao);
        }
        int cerquilha = semQuery.indexOf('#');
        if (cerquilha >= 0) {
            semQuery = semQuery.substring(0, cerquilha);
        }
        if (semQuery.isEmpty()) {
            return "/";
        }
        return semQuery.startsWith("/") ? semQuery : "/" + semQuery;
    }

    /** Correlation ID novo para falhas inesperadas. */
    public static String novoCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
