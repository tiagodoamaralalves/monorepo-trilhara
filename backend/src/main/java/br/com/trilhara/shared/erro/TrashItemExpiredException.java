package br.com.trilhara.shared.erro;

/**
 * Restauração de item de lixeira fora do prazo de 30 dias (ou item já purgado)
 * → {@code 404 TRASH_ITEM_EXPIRED}.
 */
public class TrashItemExpiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private static final String DETAIL_PADRAO =
            "O item da lixeira expirou após o período de 30 dias ou não foi encontrado.";

    public TrashItemExpiredException() {
        super(DETAIL_PADRAO);
    }

    public TrashItemExpiredException(String detail) {
        super(detail != null && !detail.isBlank() ? detail : DETAIL_PADRAO);
    }
}
