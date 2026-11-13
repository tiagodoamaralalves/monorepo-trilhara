package br.com.trilhara.nota.dto;

/**
 * Resultado da sincronização explícita de um caderno com a trilha de origem (D-29).
 *
 * <p>A sincronização <strong>não</strong> cria nem altera páginas de seções já existentes: só
 * renomeia o caderno e as seções vinculadas, cria seções (com uma página vazia por tópico)
 * para disciplinas novas e desvincula seções de disciplinas removidas, preservando conteúdo.</p>
 *
 * @param secoesCriadas       seções criadas para disciplinas novas
 * @param secoesRenomeadas    seções vinculadas cujo título mudou
 * @param secoesDesvinculadas seções cuja disciplina de origem não existe mais
 * @param paginasCriadas      páginas vazias criadas junto das seções novas
 */
public record SincronizacaoTrilhaResponse(
        String cadernoId,
        int secoesCriadas,
        int secoesRenomeadas,
        int secoesDesvinculadas,
        int paginasCriadas) {
}
