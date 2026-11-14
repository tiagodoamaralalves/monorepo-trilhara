package br.com.trilhara.tarefa.dto;

import java.util.List;

/**
 * Lixeira de tarefas. As tarefas são paginadas com 50/100; listas e séries são
 * limitadas estruturalmente e vêm completas.
 */
public record LixeiraTarefasResponse(
        List<TarefaResponse> tarefas,
        List<ListaTarefasResponse> listas,
        List<SerieRecorrenteResponse> series,
        int pagina,
        int tamanho,
        long totalItens,
        int totalPaginas) {
}
