package br.com.trilhara.trilha.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReordenarDisciplinasRequest(
        @NotNull(message = "A lista de IDs das disciplinas é obrigatória.")
        List<@NotBlank String> disciplinaIds) {
}
