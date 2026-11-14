package br.com.trilhara.nota.dto;

import java.util.List;

/**
 * Página de metadados de páginas de uma seção.
 *
 * <p>Envelope normativo {@code { itens, pagina, tamanho, totalItens, totalPaginas }}. Nenhum
 * item traz {@code conteudoMarkdown} — o Markdown é carregado sob demanda pelo endpoint
 * canônico {@code GET /api/notas/{notaId}}.</p>
 */
public record PaginaNotasResponse(
        List<NotaMetadataResponse> itens,
        int pagina,
        int tamanho,
        long totalItens,
        int totalPaginas) {
}
