package br.com.trilhara.trilha.dto;

import java.time.Instant;

/**
 * DTO leve de resumo retornado por GET /api/trilhas para o Hub.
 * Não transfere arrays de tópicos.
 *
 * <p>Identificadores de usuário são omitidos do contrato público: o cliente nunca
 * precisa deles e a trilha já é implicitamente do usuário autenticado. {@code cargo} é o
 * único campo de área/cargo, espelhando {@code AtualizarMetadadosTrilhaRequest.obterCargoEfetivo}.</p>
 */
public record TrilhaResumoDTO(
        String id,
        String nome,
        String cargo,
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
