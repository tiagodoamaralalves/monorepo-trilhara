package br.com.trilhara.nota.dto;

/**
 * Impacto da exclusão de um caderno, exibido no {@code ConfirmDialog}.
 *
 * <p>Conta apenas itens <strong>ativos</strong> — exatamente os que receberão o
 * {@code loteExclusaoId} na operação. Itens que já estavam na lixeira não entram na conta e
 * também não são tocados, preservando o estado anterior ao lote.</p>
 *
 * @param secoes seções ativas que irão para a lixeira
 * @param paginas páginas ativas que irão para a lixeira
 */
public record CadernoImpactoExclusaoResponse(String id, String titulo, long secoes, long paginas) {
}
