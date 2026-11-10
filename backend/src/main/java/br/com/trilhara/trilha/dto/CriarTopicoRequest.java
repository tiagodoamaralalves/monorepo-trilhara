package br.com.trilhara.trilha.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarTopicoRequest(
        String id,
        @NotBlank(message = "O nome do tópico é obrigatório.")
        @Size(max = 300, message = "O nome do tópico deve ter no máximo 300 caracteres.")
        String nome,
        Integer ordem) {
}
