package br.com.trilhara.cronograma;

import java.time.LocalDate;
import java.util.List;

import br.com.trilhara.auth.Autenticado;
import br.com.trilhara.cronograma.dto.AtualizarCronogramaRequest;
import br.com.trilhara.cronograma.dto.ConcluirAtrasadosRequest;
import br.com.trilhara.cronograma.dto.ConcluirAtrasadosResponse;
import br.com.trilhara.cronograma.dto.CriarCronogramaRequest;
import br.com.trilhara.cronograma.dto.CronogramaDTO;
import br.com.trilhara.cronograma.dto.PaginaBlocosDTO;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
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

@Path("/api/trilhas/{trilhaId}/cronogramas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Autenticado
public class CronogramaResource {

    @Inject
    CronogramaService service;

    @GET
    public List<CronogramaDTO> listar(@PathParam("trilhaId") String trilhaId) {
        return service.listar(trilhaId);
    }

    @POST
    public Response criar(
            @PathParam("trilhaId") String trilhaId,
            @Valid @NotNull CriarCronogramaRequest request) {
        CronogramaDTO criado = service.criar(trilhaId, request);
        return Response.status(Response.Status.CREATED).entity(criado).build();
    }

    @PUT
    @Path("/{cronogramaId}")
    public CronogramaDTO atualizar(
            @PathParam("trilhaId") String trilhaId,
            @PathParam("cronogramaId") String cronogramaId,
            @Valid @NotNull AtualizarCronogramaRequest request) {
        return service.atualizar(trilhaId, cronogramaId, request);
    }

    @PATCH
    @Path("/{cronogramaId}/ativar")
    public CronogramaDTO ativar(
            @PathParam("trilhaId") String trilhaId,
            @PathParam("cronogramaId") String cronogramaId) {
        return service.ativar(trilhaId, cronogramaId);
    }

    @DELETE
    @Path("/{cronogramaId}")
    public Response excluir(
            @PathParam("trilhaId") String trilhaId,
            @PathParam("cronogramaId") String cronogramaId) {
        service.excluir(trilhaId, cronogramaId);
        return Response.noContent().build();
    }

    @GET
    @Path("/{cronogramaId}/blocos")
    public PaginaBlocosDTO listarBlocos(
            @PathParam("trilhaId") String trilhaId,
            @PathParam("cronogramaId") String cronogramaId,
            @QueryParam("concluido") Boolean concluido,
            @QueryParam("dataInicio") String dataInicio,
            @QueryParam("dataFim") String dataFim,
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamanho") @DefaultValue("50") int tamanho) {

        LocalDate dInicio = dataInicio != null && !dataInicio.isBlank() ? LocalDate.parse(dataInicio) : null;
        LocalDate dFim = dataFim != null && !dataFim.isBlank() ? LocalDate.parse(dataFim) : null;

        return service.listarBlocosPaginado(trilhaId, cronogramaId, concluido, dInicio, dFim, pagina, tamanho);
    }

    @POST
    @Path("/{cronogramaId}/blocos/concluir-atrasados")
    public ConcluirAtrasadosResponse concluirAtrasados(
            @PathParam("trilhaId") String trilhaId,
            @PathParam("cronogramaId") String cronogramaId,
            @Valid @NotNull ConcluirAtrasadosRequest request) {
        return service.concluirAtrasados(trilhaId, cronogramaId, request.operationId());
    }
}
