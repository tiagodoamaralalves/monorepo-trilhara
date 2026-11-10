package br.com.trilhara.trilha.dto;

import java.time.Instant;

/**
 * DTO leve de resumo retornado por GET /api/trilhas para o Hub.
 * Não transfere arrays de tópicos.
 * Conforme REQUISITOS-REESTRUTURACAO-DADOS.md §4.1 e PLANO-IMPLEMENTACAO-REESTRUTURACAO.md §2.3.
 */
public record TrilhaResumoDTO(
        String id,
        String usuarioId,
        String donoId,
        String nome,
        String cargo,
        String areaCargo,
        String banca,
        String dataProva,
        String cor,
        String origemEditalId,
        int totalDisciplinas,
        int totalTopicos,
        int topicosConcluidos,
        double percentualProgresso,
        CronogramaAtivoResumoDTO cronogramaAtivoResumo,
        Instant criadoEm,
        Instant atualizadoEm) {
}
