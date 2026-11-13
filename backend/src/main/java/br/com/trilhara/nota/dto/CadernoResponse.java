package br.com.trilhara.nota.dto;

import java.time.Instant;

/**
 * Caderno na listagem e na leitura individual (D-29/D-33).
 *
 * <p>Não há modelo embutido de seções nem páginas: o caderno é um registro da coleção
 * {@code cadernos} e as contagens são agregadas das coleções filhas. Assim, listar 500
 * cadernos não transfere o conteúdo das seções.</p>
 *
 * @param trilhaNome   nome atual da trilha vinculada (preenchido apenas quando
 *                     {@code origemTrilhaId != null}); é o que a pílula exibe
 * @param totalSecoes  seções ativas do caderno
 * @param totalPaginas páginas ativas do caderno
 */
public record CadernoResponse(
        String id,
        String titulo,
        String cor,
        String icone,
        String origemTrilhaId,
        String trilhaNome,
        long totalSecoes,
        long totalPaginas,
        Instant criadoEm,
        Instant atualizadoEm) {
}
