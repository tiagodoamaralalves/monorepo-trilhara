package br.com.trilhara.tarefa.dto;

import java.util.List;

import br.com.trilhara.tarefa.ModoLembrete;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarSerieRecorrenteRequest(
        String listaId,
        @NotBlank @Size(min = 1, max = 200) String titulo,
        String descricao,
        String prioridade,
        String horario,
        List<SubtarefaDTO> subtarefasModelo,
        @NotBlank String frequencia,
        int intervalo,
        List<Integer> diasDaSemana,
        String modoMensal,
        Integer diaDoMes,
        Integer ordinalSemana,
        Integer diaSemanaOrdinal,
        @NotBlank String tipoTermino,
        @NotBlank String dataInicio,
        String dataFim,
        Integer maxOcorrencias,
        ModoLembrete modoLembrete,
        Integer lembreteMinutosAntes,
        String fusoAgendamento,
        String origemOperacaoId) {
}
