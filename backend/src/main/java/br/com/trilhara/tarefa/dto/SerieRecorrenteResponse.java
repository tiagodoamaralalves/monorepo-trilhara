package br.com.trilhara.tarefa.dto;

import java.time.Instant;
import java.util.List;

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
        Integer lembreteMinutosAntes,
        boolean usaPadraoGlobalLembrete,
        String fusoAgendamento,
        Instant deletedAt,
        Instant expiresAt,
        String loteExclusaoId,
        Instant criadoEm,
        Instant atualizadoEm) {
}
