package br.com.trilhara.shared.erro;

/**
 * Dependência externa indisponível (MongoDB fora do ar, timeout de servidor)
 * → {@code 503 SERVICE_UNAVAILABLE}.
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String detail) {
        super(detail);
    }

    public ServiceUnavailableException(String detail, Throwable cause) {
        super(detail, cause);
    }
}
