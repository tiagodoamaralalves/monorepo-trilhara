package br.com.trilhara.tarefa.dto;

import java.time.Instant;

public record ListaTarefasResponse(
        String id,
        String usuarioId,
        String nome,
        String cor,
        int ordem,
        long totalTarefas,
        Instant criadoEm,
        Instant atualizadoEm) {
}
