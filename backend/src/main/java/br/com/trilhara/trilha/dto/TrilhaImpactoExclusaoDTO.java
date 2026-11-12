package br.com.trilhara.trilha.dto;

/**
 * Contagem de impacto exibida antes de excluir uma trilha (D-38: ação irreversível com
 * impacto descrito em números). Retornado por
 * {@code GET /api/trilhas/{trilhaId}/impacto-exclusao}.
 *
 * @param disciplinas       disciplinas que serão removidas com a trilha
 * @param cronogramas       cronogramas (e seus blocos) que serão removidos
 * @param sessoes           sessões de estudo que serão removidas
 * @param cadernosVinculados cadernos ativos com {@code origemTrilhaId} apontando para a trilha;
 *                           eles não são excluídos, apenas desvinculados
 */
public record TrilhaImpactoExclusaoDTO(
        long disciplinas,
        long cronogramas,
        long sessoes,
        long cadernosVinculados) {
}
