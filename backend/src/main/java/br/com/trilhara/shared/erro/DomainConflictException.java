package br.com.trilhara.shared.erro;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exceção de negócio para conflito de estado → HTTP 409, carregando um
 * {@link CodigoErro} do catálogo fechado e uma mensagem legível em pt-BR.
 *
 * <p>Conflitos que identificam o recurso causador expõem a extensão {@code errors}
 * (campo → mensagens). Exemplo: {@code 409 TRAIL_ALREADY_IMPORTED} aponta
 * {@code errors.cadernoId} para o caderno já vinculado.</p>
 */
public class DomainConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final CodigoErro codigo;
    private final Map<String, List<String>> errors;

    public DomainConflictException(CodigoErro codigo, String detail) {
        this(codigo, detail, (Map<String, List<String>>) null);
    }

    public DomainConflictException(CodigoErro codigo, String detail, Throwable cause) {
        super(detail, cause);
        this.codigo = codigo != null ? codigo : CodigoErro.DUPLICATE_RESOURCE;
        this.errors = null;
    }

    public DomainConflictException(CodigoErro codigo, String detail, Map<String, List<String>> errors) {
        super(detail);
        this.codigo = codigo != null ? codigo : CodigoErro.DUPLICATE_RESOURCE;
        this.errors = errors == null || errors.isEmpty() ? null : Map.copyOf(errors);
    }

    /** Conflito de um único campo (ex.: {@code cadernoId}). */
    public static DomainConflictException campo(CodigoErro codigo, String detail, String campo, String mensagem) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        errors.put(campo != null && !campo.isBlank() ? campo : "geral", List.of(mensagem));
        return new DomainConflictException(codigo, detail, errors);
    }

    public CodigoErro getCodigo() {
        return codigo;
    }

    public Map<String, List<String>> getErrors() {
        return errors;
    }
}

