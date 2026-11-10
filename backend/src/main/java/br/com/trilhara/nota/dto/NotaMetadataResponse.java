package br.com.trilhara.nota.dto;

import java.time.Instant;

/**
 * Projeção leve de metadados de nota sem o conteudoMarkdown.
 */
public record NotaMetadataResponse(
    String id,
    String usuarioId,
    String cadernoId,
    String secaoId,
    String titulo,
    int ordem,
    String origemTopicoId,
    Instant criadoEm,
    Instant atualizadoEm
) {}
