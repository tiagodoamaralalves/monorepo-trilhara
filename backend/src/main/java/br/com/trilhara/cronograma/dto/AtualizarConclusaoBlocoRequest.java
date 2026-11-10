package br.com.trilhara.cronograma.dto;

import jakarta.validation.constraints.NotNull;

public record AtualizarConclusaoBlocoRequest(
        @NotNull(message = "O campo concluido é obrigatório.") Boolean concluido) {
}
