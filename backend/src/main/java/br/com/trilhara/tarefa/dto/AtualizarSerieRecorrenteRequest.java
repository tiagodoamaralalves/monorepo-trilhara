package br.com.trilhara.tarefa.dto;

import java.util.List;

import br.com.trilhara.tarefa.ModoLembrete;

import jakarta.validation.constraints.Size;

public record AtualizarSerieRecorrenteRequest(
        String listaId,
        @Size(min = 1, max = 200) String titulo,
        String descricao,
        String prioridade,
        String horario,
        List<SubtarefaDTO> subtarefasModelo,
        String frequencia,
        Integer intervalo,
        List<Integer> diasDaSemana,
        String modoMensal,
        Integer diaDoMes,
        Integer ordinalSemana,
        Integer diaSemanaOrdinal,
        String tipoTermino,
        String dataInicio,
        String dataFim,
        Integer maxOcorrencias,
        Boolean ativa,
        ModoLembrete modoLembrete,
        Integer lembreteMinutosAntes,
        String fusoAgendamento,
        String origemOperacaoId) {
}
