package br.com.trilhara.trilha.dto;

/**
 * Resumo do cronograma ativo de uma trilha projetado nos DTOs de resumo e detalhe.
 */
public record CronogramaAtivoResumoDTO(
        String cronogramaAtivoId,
        String modalidade,
        String dataInicio,
        String dataFim) {
}
