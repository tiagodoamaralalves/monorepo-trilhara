package br.com.trilhara.shared.erro;

/**
 * Identificador HTTP malformado (não é hexadecimal de 24 caracteres) → {@code 400 INVALID_IDENTIFIER}.
 *
 * <p>Recurso de outro usuário ou inexistente continua respondendo {@code 404 NOT_FOUND},
 * sem revelar existência.</p>
 */
public class InvalidIdentifierException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String campo;

    public InvalidIdentifierException(String campo) {
        this(campo, null);
    }

    public InvalidIdentifierException(String campo, Throwable cause) {
        super("Identificador inválido para " + normalizarCampo(campo) + ".", cause);
        this.campo = normalizarCampo(campo);
    }

    public String getCampo() {
        return campo;
    }

    private static String normalizarCampo(String campo) {
        return campo != null && !campo.isBlank() ? campo : "id";
    }
}
