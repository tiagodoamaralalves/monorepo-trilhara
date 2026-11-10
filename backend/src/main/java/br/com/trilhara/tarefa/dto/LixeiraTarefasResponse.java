package br.com.trilhara.tarefa.dto;

import java.util.List;

public record LixeiraTarefasResponse(
        List<TarefaResponse> tarefas,
        List<ListaTarefasResponse> listas,
        List<SerieRecorrenteResponse> series) {
}
