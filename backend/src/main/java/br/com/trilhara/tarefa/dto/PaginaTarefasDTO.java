package br.com.trilhara.tarefa.dto;

import java.util.List;

/**
 * Página de tarefas, no mesmo formato normativo de {@code PaginaSessoesDTO}
 * e {@code PaginaBlocosDTO}: {@code { itens, pagina, tamanho, totalItens, totalPaginas }}.
 */
public record PaginaTarefasDTO(
        List<TarefaResponse> itens,
        int pagina,
        int tamanho,
        long totalItens,
        int totalPaginas) {
}
