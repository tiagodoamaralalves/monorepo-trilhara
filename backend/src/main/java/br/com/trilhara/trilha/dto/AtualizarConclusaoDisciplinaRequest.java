package br.com.trilhara.trilha.dto;

import jakarta.validation.constraints.NotNull;

public record AtualizarConclusaoDisciplinaRequest(
        @NotNull(message = "O campo concluida é obrigatório.") Boolean concluida) {
}
