package br.com.trilhara.trilha.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtualizarDisciplinaRequest(
        @NotBlank(message = "O nome da disciplina é obrigatório.")
        @Size(max = 120, message = "O nome da disciplina deve ter no máximo 120 caracteres.")
        String nome,
        @Size(max = 32, message = "A cor da disciplina deve ter no máximo 32 caracteres.")
        String cor,
        @Min(value = 1, message = "O peso mínimo é 1.")
        @Max(value = 5, message = "O peso máximo é 5.")
        Integer peso,
        @Size(max = 2048, message = "O link do caderno deve ter no máximo 2048 caracteres.")
        String cadernoLink,
        @Size(max = 500, message = "Cada disciplina pode ter no máximo 500 tópicos.")
        List<@Valid CriarTopicoRequest> topicos) {

    public AtualizarDisciplinaRequest {
        peso = peso == null ? 1 : peso;
    }
}
