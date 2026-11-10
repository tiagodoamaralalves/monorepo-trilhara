package br.com.trilhara.shared.erro;

/**
 * Exceção de negócio para situações de conflito (HTTP 409 Conflict),
 * carregando um código estável de erro (code) e mensagem legível.
 */
public class DomainConflictException extends RuntimeException {

    public static final String DEFAULT_CODE = "CONFLICT";

    private final String code;

    public DomainConflictException(String code, String message) {
        super(message);
        this.code = code != null && !code.isBlank() ? code : DEFAULT_CODE;
    }

    public DomainConflictException(String message) {
        this(DEFAULT_CODE, message);
    }

    public DomainConflictException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code != null && !code.isBlank() ? code : DEFAULT_CODE;
    }

    public String getCode() {
        return code;
    }
}
