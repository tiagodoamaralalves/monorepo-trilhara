package br.com.trilhara.nota;

import static br.com.trilhara.shared.mongo.BsonIds.toObjectIdOrNotFound;

import org.bson.types.ObjectId;

import br.com.trilhara.auth.Autenticado;
import br.com.trilhara.auth.AuthContext;
import br.com.trilhara.nota.dto.AtualizarSecaoRequest;
import br.com.trilhara.nota.dto.ExclusaoLixeiraResponse;
import br.com.trilhara.nota.dto.SecaoResponse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/secoes")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Autenticado
public class SecaoResource {

    @Inject
    NotaService notaService;

    @Inject
    AuthContext authContext;

    private ObjectId usuarioId() {
        return toObjectIdOrNotFound(authContext.usuarioId());
    }

    @GET
    @Path("/{id}")
    public SecaoResponse buscar(@PathParam("id") String id) {
        return notaService.buscarSecao(usuarioId(), toObjectIdOrNotFound(id));
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public SecaoResponse atualizar(@PathParam("id") String id, @Valid AtualizarSecaoRequest request) {
        return notaService.atualizarSecao(usuarioId(), toObjectIdOrNotFound(id), request);
    }

    @DELETE
    @Path("/{id}")
    public ExclusaoLixeiraResponse deletar(@PathParam("id") String id) {
        return notaService.deletarSecao(usuarioId(), toObjectIdOrNotFound(id));
    }
}
