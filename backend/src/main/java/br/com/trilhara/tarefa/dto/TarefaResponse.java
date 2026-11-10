package br.com.trilhara.tarefa.dto;

import java.time.Instant;
import java.util.List;

public record TarefaResponse(
        String id,
        String usuarioId,
        String listaId,
        String serieId,
        String dataOriginalOcorrencia,
        boolean desvinculadaDaSerie,
        String titulo,
        String descricao,
        String prioridade,
        String dataVencimento,
        String horario,
        String estado,
        boolean concluida,
        Instant concluidaEm,
        int ordem,
        List<SubtarefaDTO> subtarefas,
        Integer lembreteMinutosAntes,
        boolean usaPadraoGlobalLembrete,
        String fusoAgendamento,
        Instant deletedAt,
        Instant expiresAt,
        String loteExclusaoId,
        Instant criadoEm,
        Instant atualizadoEm) {
}
