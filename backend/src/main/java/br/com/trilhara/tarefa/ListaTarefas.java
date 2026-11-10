package br.com.trilhara.tarefa;

import java.time.Instant;

import org.bson.types.ObjectId;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ListaTarefas(
        ObjectId id,
        @NotNull ObjectId usuarioId,
        @NotBlank @Size(min = 1, max = 100) String nome,
        String cor,
        int ordem,
        Instant deletedAt,
        Instant expiresAt,
        String loteExclusaoId,
        @NotNull Instant criadoEm,
        @NotNull Instant atualizadoEm) {
}
