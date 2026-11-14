package br.com.trilhara.tarefa.dto;

import java.util.List;

public record TarefasHojeResponse(
        String hojeLocal,
        List<TarefaResponse> atrasadas,
        List<TarefaResponse> hoje,
        List<TarefaResponse> semData,
        List<TarefaResponse> concluidasHoje) {
}
