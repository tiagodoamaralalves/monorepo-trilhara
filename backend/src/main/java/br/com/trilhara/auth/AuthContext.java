package br.com.trilhara.auth;

import java.util.List;

import br.com.trilhara.auth.TokenService.ClaimsToken;
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class AuthContext {

    private ClaimsToken claims;

    public void definir(ClaimsToken claims) {
        this.claims = claims;
    }

    public boolean estaAutenticado() {
        return claims != null && claims.usuarioId() != null && !claims.usuarioId().isBlank();
    }

    public String usuarioId() {
        return estaAutenticado() ? claims.usuarioId() : null;
    }

    public String email() {
        return estaAutenticado() ? claims.email() : null;
    }

    public List<Role> roles() {
        return estaAutenticado() ? claims.roles() : List.of();
    }

    public boolean possuiRole(Role role) {
        return estaAutenticado() && claims.roles() != null && claims.roles().contains(role);
    }

    public ClaimsToken claims() {
        return claims;
    }
}

