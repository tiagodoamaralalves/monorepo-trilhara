package br.com.trilhara.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record HandoffRequest(
        @NotBlank(message = "URL de destino é obrigatória para handoff.")
        String destino
) {}
