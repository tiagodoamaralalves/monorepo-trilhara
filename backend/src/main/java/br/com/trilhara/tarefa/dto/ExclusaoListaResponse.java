package br.com.trilhara.tarefa.dto;

public record ExclusaoListaResponse(
        String id,
        int tarefasAfetadas,
        String loteExclusaoId) {
}
