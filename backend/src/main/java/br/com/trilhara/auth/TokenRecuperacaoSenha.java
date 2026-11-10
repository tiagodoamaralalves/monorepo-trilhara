package br.com.trilhara.auth;

import java.time.Instant;

public record TokenRecuperacaoSenha(
        String id,
        String tokenHash,
        String usuarioId,
        String ip,
        Instant criadoEm,
        Instant expiraEm,
        Instant utilizadoEm) {

    public boolean foiUtilizado() {
        return utilizadoEm != null;
    }

    public boolean estaExpirado(Instant agora) {
        return expiraEm == null || !expiraEm.isAfter(agora);
    }
}

