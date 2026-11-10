package br.com.trilhara.trilha.dto;

/**
 * Resumo do cronograma ativo de uma trilha projetado nos DTOs de resumo e detalhe.
 * Conforme PLANO-IMPLEMENTACAO-REESTRUTURACAO.md §2.3.
 */
public record CronogramaAtivoResumoDTO(
        String cronogramaAtivoId,
        String modalidade,
        String dataInicio,
        String dataFim) {
}
