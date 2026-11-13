package br.com.trilhara.nota.dto;

/**
 * Impacto da exclusão de uma seção (D-33), exibido no {@code ConfirmDialog}.
 *
 * <p>Conta apenas páginas <strong>ativas</strong> — as que receberão o {@code loteExclusaoId}
 * junto da seção. Páginas já excluídas isoladamente permanecem na lixeira com o lote antigo.</p>
 *
 * @param paginas páginas ativas que irão para a lixeira
 */
public record SecaoImpactoExclusaoResponse(String id, String titulo, long paginas) {
}
