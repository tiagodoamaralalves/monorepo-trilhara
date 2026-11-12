package br.com.trilhara.shared.erro;

/**
 * Exceção de negócio para conflito de estado → HTTP 409, carregando um
 * {@link CodigoErro} do catálogo fechado (D-04) e uma mensagem legível em pt-BR.
 */
public class DomainConflictException extends RuntimeException {

    private final CodigoErro codigo;

    public DomainConflictException(CodigoErro codigo, String detail) {
        super(detail);
        this.codigo = codigo != null ? codigo : CodigoErro.DUPLICATE_RESOURCE;
    }

    public DomainConflictException(CodigoErro codigo, String detail, Throwable cause) {
        super(detail, cause);
        this.codigo = codigo != null ? codigo : CodigoErro.DUPLICATE_RESOURCE;
    }

    public CodigoErro getCodigo() {
        return codigo;
    }
}

