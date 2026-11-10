package br.com.trilhara.tarefa;

import java.util.List;

import br.com.trilhara.auth.AuthContext;
import br.com.trilhara.auth.Autenticado;
import br.com.trilhara.tarefa.dto.AtualizarListaTarefasRequest;
import br.com.trilhara.tarefa.dto.CriarListaTarefasRequest;
import br.com.trilhara.tarefa.dto.ExclusaoListaResponse;
import br.com.trilhara.tarefa.dto.ListaTarefasResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/listas-tarefas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Autenticado
public class ListaTarefasResource {

    private final TarefaService service;
    private final AuthContext authContext;

    public ListaTarefasResource(TarefaService service, AuthContext authContext) {
        this.service = service;
        this.authContext = authContext;
    }

    @GET
    public List<ListaTarefasResponse> listar() {
        return service.listarListas(authContext.usuarioId());
    }

    @POST
    public Response criar(@NotNull @Valid CriarListaTarefasRequest request) {
        ListaTarefasResponse criada = service.criarLista(request, authContext.usuarioId());
        return Response.status(Response.Status.CREATED).entity(criada).build();
    }

    @GET
    @Path("{listaId}")
    public ListaTarefasResponse buscar(@PathParam("listaId") String listaId) {
        return service.buscarLista(listaId, authContext.usuarioId());
    }

    @PUT
    @Path("{listaId}")
    public ListaTarefasResponse atualizar(
            @PathParam("listaId") String listaId,
            @NotNull @Valid AtualizarListaTarefasRequest request) {
        return service.atualizarLista(listaId, request, authContext.usuarioId());
    }

    @DELETE
    @Path("{listaId}")
    public ExclusaoListaResponse excluir(@PathParam("listaId") String listaId) {
        return service.excluirLista(listaId, authContext.usuarioId());
    }

    @POST
    @Path("{listaId}/restaurar")
    @Consumes(MediaType.WILDCARD)
    public ListaTarefasResponse restaurar(@PathParam("listaId") String listaId) {
        return service.restaurarLista(listaId, authContext.usuarioId());
    }
}
