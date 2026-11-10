package br.com.trilhara.nota.dto;

import java.time.Instant;

public record ItemLixeiraResponse(
    String tipo, // "CADERNO", "SECAO", "NOTA"
    String id,
    String titulo,
    String loteExclusaoId,
    String cadernoId,
    String cadernoTitulo,
    String secaoId,
    String secaoTitulo,
    Instant deletedAt,
    Instant expiresAt
) {}
