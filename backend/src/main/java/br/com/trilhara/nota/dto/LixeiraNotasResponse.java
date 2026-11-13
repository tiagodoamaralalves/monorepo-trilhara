package br.com.trilhara.nota.dto;

import java.util.List;

/**
 * Página da lixeira hierárquica de Notas (D-34).
 *
 * <p>Segue o envelope normativo {@code { itens, pagina, tamanho, totalItens, totalPaginas }}.
 * A ordenação é {@code deletedAt} DESC com {@code _id} DESC como desempate estável.</p>
 */
public record LixeiraNotasResponse(
        List<ItemLixeiraResponse> itens,
        int pagina,
        int tamanho,
        long totalItens,
        int totalPaginas) {
}
