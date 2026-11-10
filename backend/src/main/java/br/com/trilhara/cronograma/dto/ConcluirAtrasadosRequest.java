package br.com.trilhara.cronograma.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ConcluirAtrasadosRequest(
        @NotNull(message = "operationId é obrigatório.") UUID operationId) {
}
