package br.com.trilhara.auth;

import java.util.List;

import org.bson.types.ObjectId;

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

    /**
 * ID do usuário autenticado como {@link ObjectId} nativo.
 *
 * <p>O valor vem exclusivamente do token; se ele não for um ObjectId válido há
 * falha de autenticação ({@code 401}), nunca erro de identificador do cliente.</p>
 */
    public ObjectId usuarioIdObjectId() {
        if (!estaAutenticado() || !ObjectId.isValid(claims.usuarioId())) {
            throw new jakarta.ws.rs.NotAuthorizedException("Autenticação obrigatória.");
        }
        return new ObjectId(claims.usuarioId());
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

