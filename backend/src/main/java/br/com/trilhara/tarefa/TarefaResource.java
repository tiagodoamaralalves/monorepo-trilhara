package br.com.trilhara.tarefa;

import java.util.List;

import br.com.trilhara.auth.AuthContext;
import br.com.trilhara.auth.Autenticado;
import br.com.trilhara.tarefa.dto.AtualizarConclusaoTarefaRequest;
import br.com.trilhara.tarefa.dto.AtualizarSubtarefaRequest;
import br.com.trilhara.tarefa.dto.AtualizarTarefaRequest;
import br.com.trilhara.tarefa.dto.CriarTarefaRequest;
import br.com.trilhara.tarefa.dto.LixeiraTarefasResponse;
import br.com.trilhara.tarefa.dto.TarefaResponse;
import br.com.trilhara.tarefa.dto.TarefasHojeResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/tarefas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Autenticado
public class TarefaResource {

    private final TarefaService service;
    private final AuthContext authContext;

    public TarefaResource(TarefaService service, AuthContext authContext) {
        this.service = service;
        this.authContext = authContext;
    }

    @GET
    public List<TarefaResponse> listar(
            @QueryParam("listaId") String listaId,
            @QueryParam("data") String data,
            @QueryParam("dataInicio") String dataInicio,
            @QueryParam("dataFim") String dataFim,
            @QueryParam("concluida") Boolean concluida,
            @QueryParam("estado") String estado,
            @QueryParam("filtro") String filtro) {
        return service.listarTarefas(
                authContext.usuarioId(),
                new TarefaService.FiltroTarefas(
                        listaId,
                        data,
                        dataInicio,
                        dataFim,
                        concluida,
                        estado,
                        filtro));
    }

    @POST
    public Response criar(@NotNull @Valid CriarTarefaRequest request) {
        TarefaResponse criada = service.criarTarefa(request, authContext.usuarioId());
        return Response.status(Response.Status.CREATED).entity(criada).build();
    }

    @GET
    @Path("hoje")
    public TarefasHojeResponse obterTarefasHoje() {
        return service.obterTarefasHoje(authContext.usuarioId());
    }

    @GET
    @Path("lixeira")
    public LixeiraTarefasResponse listarLixeira() {
        return service.listarLixeira(authContext.usuarioId());
    }

    @POST
    @Path("lixeira/{tarefaId}/restaurar")
    @Consumes(MediaType.WILDCARD)
    public TarefaResponse restaurarDaLixeira(@PathParam("tarefaId") String tarefaId) {
        return service.restaurarTarefaDaLixeira(tarefaId, authContext.usuarioId());
    }

    @DELETE
    @Path("lixeira/esvaziar")
    public Response esvaziarLixeira() {
        service.esvaziarLixeira(authContext.usuarioId());
        return Response.noContent().build();
    }

    @GET
    @Path("{tarefaId}")
    public TarefaResponse buscar(@PathParam("tarefaId") String tarefaId) {
        return service.buscarTarefa(tarefaId, authContext.usuarioId());
    }

    @PUT
    @Path("{tarefaId}")
    public TarefaResponse atualizar(
            @PathParam("tarefaId") String tarefaId,
            @QueryParam("escopo") String escopo,
            @NotNull @Valid AtualizarTarefaRequest request) {
        return service.atualizarTarefa(tarefaId, request, escopo, authContext.usuarioId());
    }

    @DELETE
    @Path("{tarefaId}")
    public Response excluir(
            @PathParam("tarefaId") String tarefaId,
            @QueryParam("escopo") String escopo) {
        service.excluirTarefa(tarefaId, escopo, authContext.usuarioId());
        return Response.noContent().build();
    }

    @PATCH
    @Path("{tarefaId}/conclusao")
    public TarefaResponse atualizarConclusao(
            @PathParam("tarefaId") String tarefaId,
            @NotNull @Valid AtualizarConclusaoTarefaRequest request) {
        return service.atualizarConclusao(tarefaId, request.concluida(), authContext.usuarioId());
    }

    @PATCH
    @Path("{tarefaId}/subtarefas/{subtarefaId}")
    public TarefaResponse atualizarSubtarefa(
            @PathParam("tarefaId") String tarefaId,
            @PathParam("subtarefaId") String subtarefaId,
            @NotNull @Valid AtualizarSubtarefaRequest request) {
        return service.atualizarSubtarefa(tarefaId, subtarefaId, request.concluida(), authContext.usuarioId());
    }

    @POST
    @Path("{tarefaId}/pular")
    @Consumes(MediaType.WILDCARD)
    public TarefaResponse pular(@PathParam("tarefaId") String tarefaId) {
        return service.pularTarefa(tarefaId, authContext.usuarioId());
    }
}
