package br.com.trilhara.tarefa.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record ReordenarTarefasRequest(
        @NotNull List<String> tarefaIds) {
}

