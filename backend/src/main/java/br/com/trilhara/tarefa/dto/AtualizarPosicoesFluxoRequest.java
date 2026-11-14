package br.com.trilhara.tarefa.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record AtualizarPosicoesFluxoRequest(
        @NotEmpty List<@Valid PosicaoTarefaFluxoRequest> posicoes) {

    public record PosicaoTarefaFluxoRequest(
            @NotBlank String tarefaId,
            double x,
            double y) {
    }
}
