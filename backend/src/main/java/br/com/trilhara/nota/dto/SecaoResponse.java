package br.com.trilhara.nota.dto;

import java.time.Instant;

public record SecaoResponse(
    String id,
    String usuarioId,
    String cadernoId,
    String titulo,
    int ordem,
    String origemDisciplinaId,
    long totalNotas,
    Instant criadoEm,
    Instant atualizadoEm
) {}
