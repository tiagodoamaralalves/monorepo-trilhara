package br.com.trilhara.shared.erro;

/**
 * Limite de recursos atingido (50 trilhas, 100 disciplinas, 500 tópicos, cronogramas, blocos)
 * → {@code 409 RESOURCE_LIMIT_EXCEEDED}.
 *
 * <p>Limite é conflito de estado com o acervo atual do usuário, não erro de sintaxe do payload.</p>
 */
public class ResourceLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ResourceLimitExceededException(String detail) {
        super(detail);
    }

    public ResourceLimitExceededException(String detail, Throwable cause) {
        super(detail, cause);
    }
}
