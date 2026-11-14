package br.com.trilhara.shared;

import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import br.com.trilhara.auth.AuthContext;
import io.quarkus.runtime.LaunchMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import java.util.Optional;

/**
 * Fornece a identidade do usuário atualmente autenticado para os serviços de negócio.
 * 
 * Regras:
 * Em produção/dev, a autenticação é estritamente obrigatória; lança NotAuthorizedException se não autenticado.
 * O fallback legado "local" foi removido.
 * Em perfil de teste (%test / LaunchMode.TEST), permite fallback fixo via configuração validado estritamente como ObjectId.
 */
@ApplicationScoped
public class UsuarioAtual {

    private final AuthContext authContext;
    private final Optional<String> testFallbackUserId;

    public UsuarioAtual() {
        this.authContext = null;
        this.testFallbackUserId = Optional.empty();
    }

    @Inject
    public UsuarioAtual(
            AuthContext authContext,
            @ConfigProperty(name = "trilhara.auth.test-fallback-user-id") Optional<String> testFallbackUserId) {
        this.authContext = authContext;
        this.testFallbackUserId = testFallbackUserId;
    }

    public String donoId() {
        if (authContext != null && authContext.estaAutenticado()) {
            return authContext.usuarioId();
        }
        if (LaunchMode.current() == LaunchMode.TEST && testFallbackUserId.isPresent()) {
            String fallbackUserId = testFallbackUserId.get();
            if (!ObjectId.isValid(fallbackUserId)) {
                throw new IllegalStateException("testFallbackUserId deve ser um ObjectId hexadecimal válido de 24 caracteres: " + fallbackUserId);
            }
            return fallbackUserId;
        }
        throw new NotAuthorizedException("Autenticação obrigatória.");
    }

    public ObjectId usuarioIdObjectId() {
        String dono = donoId();
        if (dono == null || !ObjectId.isValid(dono)) {
            // O ID vem exclusivamente do token; ausência/erro é 401, nunca 400 do cliente.
            throw new NotAuthorizedException("Autenticação obrigatória.");
        }
        return new ObjectId(dono);
    }

    public String usuarioId() {
        return donoId();
    }
}
