package br.com.trilhara.nota.dto;

import java.time.Instant;

/**
 * Página de uma seção (D-29/D-30).
 *
 * <p>É o formato exclusivo de {@code GET|PUT /api/notas/{notaId}} (endpoint canônico de
 * leitura — D-32). O endpoint aninhado de leitura de nota foi removido.</p>
 */
public record NotaDetalheResponse(
        String id,
        String cadernoId,
        String secaoId,
        String titulo,
        String conteudoMarkdown,
        int ordem,
        String topicoOrigemId,
        Instant criadoEm,
        Instant atualizadoEm) {
}
