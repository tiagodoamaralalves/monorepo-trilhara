package br.com.trilhara.shared.erro;

/**
 * Chave única duplicada ({@code E11000}) não mapeada para um código de domínio específico
 * → {@code 409 DUPLICATE_RESOURCE}.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String detail) {
        super(detail != null && !detail.isBlank()
                ? detail
                : "Já existe um recurso com os mesmos dados únicos.");
    }

    public DuplicateResourceException(String detail, Throwable cause) {
        super(detail != null && !detail.isBlank()
                ? detail
                : "Já existe um recurso com os mesmos dados únicos.", cause);
    }
}
