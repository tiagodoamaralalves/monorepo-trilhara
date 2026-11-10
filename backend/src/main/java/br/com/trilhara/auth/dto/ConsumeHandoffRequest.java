package br.com.trilhara.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ConsumeHandoffRequest(
        @NotBlank(message = "Código de handoff é obrigatório.")
        String codigo
) {}
