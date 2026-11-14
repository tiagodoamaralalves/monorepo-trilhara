package br.com.trilhara.auth;

import br.com.trilhara.auth.dto.AtualizarTemaRequest;
import br.com.trilhara.auth.dto.PreferenciasDTO;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Preferências do usuário autenticado.
 *
 * <p>{@code PUT /api/usuarios/me/preferencias/tema} recebe {@code { "tema": "dark" }} e
 * devolve {@code 200} com as preferências completas. Tema nunca é transportado por query
 * string e o recurso de outro usuário não existe (escopo sempre pelo {@code usuarioId} do
 * token).</p>
 */
@Path("/api/usuarios/me/preferencias")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Autenticado
public class UsuarioPreferenciasResource {

    private final AuthContext authContext;
    private final UserService userService;

    @Inject
    public UsuarioPreferenciasResource(AuthContext authContext, UserService userService) {
        this.authContext = authContext;
        this.userService = userService;
    }

    @PUT
    @Path("/tema")
    public PreferenciasDTO atualizarTema(@Valid AtualizarTemaRequest request) {
        return userService.atualizarTema(authContext.usuarioId(), request.tema());
    }
}
