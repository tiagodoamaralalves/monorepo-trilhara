package br.com.trilhara.nota.dto;

import java.time.Instant;
import java.util.List;

public record CadernoResponse(
    String id,
    String usuarioId,
    String titulo,
    String cor,
    String icone,
    String origemTrilhaId,
    boolean vinculadoATrilha,
    long totalSecoes,
    long totalNotas,
    List<SecaoResponse> secoes,
    Instant criadoEm,
    Instant atualizadoEm
) {}
