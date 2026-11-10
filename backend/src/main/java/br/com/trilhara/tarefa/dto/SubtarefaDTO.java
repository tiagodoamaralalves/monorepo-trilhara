package br.com.trilhara.tarefa.dto;

public record SubtarefaDTO(
        String id,
        String titulo,
        boolean concluida,
        int ordem) {
}
