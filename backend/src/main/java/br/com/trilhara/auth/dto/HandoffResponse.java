package br.com.trilhara.auth.dto;

import java.time.Instant;

public record HandoffResponse(
        String codigo,
        Instant expiraEm
) {}
