package br.com.trilhara.nota.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtualizarSecaoRequest(
    @NotBlank(message = "O título da seção é obrigatório")
    @Size(max = 120, message = "O título da seção não pode exceder 120 caracteres")
    String titulo,
    String cor
) {}
