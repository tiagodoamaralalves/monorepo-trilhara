package br.com.trilhara.auth;

import java.time.Instant;

public record TokenConfirmacaoEmail(
        String id,
        String tokenHash,
        String usuarioId,
        String email,
        Instant criadoEm,
        Instant expiraEm,
        Instant confirmadoEm) {

    public boolean foiConfirmado() {
        return confirmadoEm != null;
    }

    public boolean estaExpirado(Instant agora) {
        return expiraEm == null || !expiraEm.isAfter(agora);
    }
}
