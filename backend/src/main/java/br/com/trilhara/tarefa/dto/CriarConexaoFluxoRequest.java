package br.com.trilhara.tarefa.dto;

import br.com.trilhara.tarefa.TipoConexaoFluxo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CriarConexaoFluxoRequest(
        @NotBlank String tarefaAlvoId,
        @NotNull TipoConexaoFluxo tipo) {
}
