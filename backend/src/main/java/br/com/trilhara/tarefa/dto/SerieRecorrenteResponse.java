package br.com.trilhara.tarefa.dto;

import java.time.Instant;
import java.util.List;

import br.com.trilhara.tarefa.ModoLembrete;

public record SerieRecorrenteResponse(
        String id,
        String usuarioId,
        String listaId,
        String titulo,
        String descricao,
        String prioridade,
        String horario,
        List<SubtarefaDTO> subtarefasModelo,
        String frequencia,
        int intervalo,
        List<Integer> diasDaSemana,
        String modoMensal,
        Integer diaDoMes,
        Integer ordinalSemana,
        Integer diaSemanaOrdinal,
        String tipoTermino,
        String dataInicio,
        String dataFim,
        Integer maxOcorrencias,
        String geradoAte,
        int totalGeradas,
        boolean ativa,
        ModoLembrete modoLembrete,
        Integer lembreteMinutosAntes,
        String fusoAgendamento,
        String origemOperacaoId,
        Instant deletedAt,
        Instant expiresAt,
        String loteExclusaoId,
        Instant criadoEm,
        Instant atualizadoEm) {
}
