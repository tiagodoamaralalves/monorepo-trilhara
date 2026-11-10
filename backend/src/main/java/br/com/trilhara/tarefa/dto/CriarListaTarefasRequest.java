package br.com.trilhara.tarefa.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.Size;

public record CriarListaTarefasRequest(
        @JsonAlias("titulo") @Size(min = 1, max = 100) String nome,
        String cor,
        Integer ordem) {
}
