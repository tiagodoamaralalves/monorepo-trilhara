package br.com.trilhara.auth;

import java.time.Instant;

public record RefreshToken(
        String id,
        String tokenHash,
        String familiaId,
        String usuarioId,
        String userAgent,
        String ip,
        Instant criadoEm,
        Instant ultimoUsoEm,
        Instant expiraEm,
        Instant revogadoEm,
        String substituidoPorHash,
        String motivoRevogacao) {

    public boolean estaRevogado() {
        return revogadoEm != null;
    }

    public boolean estaExpirado(Instant agora) {
        return expiraEm == null || !expiraEm.isAfter(agora);
    }
}

