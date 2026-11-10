package br.com.trilhara.nota.dto;

import java.time.Instant;

/**
 * DTO completo da nota incluindo o conteudoMarkdown.
 */
public record NotaDetalheResponse(
    String id,
    String usuarioId,
    String cadernoId,
    String secaoId,
    String titulo,
    String conteudoMarkdown,
    int ordem,
    String origemTopicoId,
    Instant criadoEm,
    Instant atualizadoEm
) {}
