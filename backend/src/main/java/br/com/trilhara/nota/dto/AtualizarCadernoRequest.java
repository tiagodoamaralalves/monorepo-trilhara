package br.com.trilhara.nota.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtualizarCadernoRequest(
    @NotBlank(message = "O título do caderno é obrigatório")
    @Size(max = 120, message = "O título do caderno não pode exceder 120 caracteres")
    String titulo,
    String cor,
    String icone
) {}
