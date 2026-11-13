package br.com.trilhara.nota.dto;

import java.util.List;

/**
 * Página de resultados da busca global de notas (D-30).
 *
 * <p>Segue o envelope normativo de paginação por offset:
 * {@code { itens, pagina, tamanho, totalItens, totalPaginas }}. A ordenação é relevância
 * DESC, {@code atualizadoEm} DESC, {@code _id} DESC.</p>
 */
public record BuscaNotasResponse(
        List<ResultadoBuscaNotaDTO> itens,
        int pagina,
        int tamanho,
        long totalItens,
        int totalPaginas) {
}
