package br.com.trilhara.nota.dto;

import java.time.Instant;

/**
 * Página de uma seção.
 *
 * <p>É o formato exclusivo de {@code GET|PUT /api/notas/{notaId}} (endpoint canônico de
 * leitura — ). O endpoint aninhado de leitura de nota foi removido.</p>
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
