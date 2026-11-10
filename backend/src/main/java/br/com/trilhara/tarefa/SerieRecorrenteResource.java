package br.com.trilhara.tarefa;

import java.util.List;

import br.com.trilhara.auth.AuthContext;
import br.com.trilhara.auth.Autenticado;
import br.com.trilhara.tarefa.dto.AtualizarSerieRecorrenteRequest;
import br.com.trilhara.tarefa.dto.CriarSerieRecorrenteRequest;
import br.com.trilhara.tarefa.dto.SerieRecorrenteResponse;
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

@Path("/api/series-recorrentes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Autenticado
public class SerieRecorrenteResource {

    private final TarefaService service;
    private final AuthContext authContext;

    public SerieRecorrenteResource(TarefaService service, AuthContext authContext) {
        this.service = service;
        this.authContext = authContext;
    }

    @GET
    public List<SerieRecorrenteResponse> listar() {
        return service.listarSeries(authContext.usuarioId());
    }

    @POST
    public Response criar(@NotNull @Valid CriarSerieRecorrenteRequest request) {
        SerieRecorrenteResponse criada = service.criarSerie(request, authContext.usuarioId());
        return Response.status(Response.Status.CREATED).entity(criada).build();
    }

    @GET
    @Path("{serieId}")
    public SerieRecorrenteResponse buscar(@PathParam("serieId") String serieId) {
        return service.buscarSerie(serieId, authContext.usuarioId());
    }

    @PUT
    @Path("{serieId}")
    public SerieRecorrenteResponse atualizar(
            @PathParam("serieId") String serieId,
            @NotNull @Valid AtualizarSerieRecorrenteRequest request) {
        return service.atualizarSerie(serieId, request, authContext.usuarioId());
    }

    @DELETE
    @Path("{serieId}")
    public Response excluir(@PathParam("serieId") String serieId) {
        service.excluirSerie(serieId, authContext.usuarioId());
        return Response.noContent().build();
    }
}
