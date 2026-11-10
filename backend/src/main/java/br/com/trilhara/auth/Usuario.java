package br.com.trilhara.auth;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record Usuario(
        String id,
        String nome,
        String email,
        String senhaHash,
        List<Role> roles,
        boolean ativo,
        boolean emailVerificado,
        int falhasLoginConsecutivas,
        Instant bloqueadoAte,
        Instant senhaAlteradaEm,
        Instant criadoEm,
        Instant atualizadoEm) {

    public Usuario {
        roles = roles == null || roles.isEmpty() ? List.of(Role.USER) : List.copyOf(roles);
        Objects.requireNonNull(nome, "Nome do usuário é obrigatório.");
        Objects.requireNonNull(email, "E-mail do usuário é obrigatório.");
        Objects.requireNonNull(senhaHash, "Hash da senha é obrigatório.");
    }

    public boolean estaBloqueado(Instant agora) {
        return bloqueadoAte != null && bloqueadoAte.isAfter(agora);
    }

    public boolean possuiRole(Role role) {
        return roles != null && roles.contains(role);
    }
}

