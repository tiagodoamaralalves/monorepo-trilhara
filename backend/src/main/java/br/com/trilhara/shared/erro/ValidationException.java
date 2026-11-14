package br.com.trilhara.shared.erro;

/**
 * Campo de payload inválido identificado na camada de serviço → {@code 400 VALIDATION_ERROR}
 * com {@code errors[campo]}.
 *
 * <p>Use para regras que não são expressáveis por Bean Validation (dependem de estado ou de
 * limite de tamanho em bytes).</p>
 */
public class ValidationException extends RuntimeException {

    private final String campo;

    public ValidationException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo != null && !campo.isBlank() ? campo : "geral";
    }

    public String getCampo() {
        return campo;
    }
}
