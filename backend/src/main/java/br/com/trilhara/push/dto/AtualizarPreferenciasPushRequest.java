package br.com.trilhara.push.dto;

import jakarta.validation.constraints.NotNull;

public record AtualizarPreferenciasPushRequest(
        @NotNull Integer padraoMinutosAntes) {
}
