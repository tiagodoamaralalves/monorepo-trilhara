package br.com.trilhara.sessao;

import java.net.URI;

import br.com.trilhara.auth.Autenticado;
import br.com.trilhara.sessao.dto.EstatisticasTrilhaDTO;
import br.com.trilhara.sessao.dto.PaginaSessoesDTO;
import br.com.trilhara.sessao.dto.RegistrarSessaoRequest;
import br.com.trilhara.sessao.dto.RemocaoSessoesDisciplinaResponse;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/trilhas/{trilhaId}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Autenticado
public class SessaoResource {

    private final SessaoService service;

    public SessaoResource(SessaoService service) {
        this.service = service;
    }

    @POST
    @Path("sessoes")
    public Response registrar(
            @PathParam("trilhaId") String trilhaId,
            @HeaderParam("Idempotency-Key") String idempotencyKeyHeader,
            @Valid RegistrarSessaoRequest request) {
        SessaoService.RegistroResultado resultado = service.registrar(trilhaId, idempotencyKeyHeader, request);
        if (resultado.criada()) {
            URI location = URI.create("/api/trilhas/" + trilhaId + "/sessoes/" + resultado.sessao().id());
            return Response.status(Response.Status.CREATED)
                    .location(location)
                    .entity(resultado.sessao())
                    .build();
        }
        return Response.ok(resultado.sessao()).build();
    }

    @DELETE
    @Path("sessoes/{sessaoId}")
    public Response excluirIndividual(
            @PathParam("trilhaId") String trilhaId,
            @PathParam("sessaoId") String sessaoId) {
        service.excluirIndividual(trilhaId, sessaoId);
        return Response.noContent().build();
    }

    @DELETE
    @Path("disciplinas/{disciplinaId}/sessoes")
    public Response excluirDaDisciplina(
            @PathParam("trilhaId") String trilhaId,
            @PathParam("disciplinaId") String disciplinaId) {
        RemocaoSessoesDisciplinaResponse resposta = service.excluirSessoesDaDisciplina(trilhaId, disciplinaId);
        return Response.ok(resposta).build();
    }

    @GET
    @Path("sessoes")
    public Response listar(
            @PathParam("trilhaId") String trilhaId,
            @QueryParam("disciplinaId") String disciplinaId,
            @QueryParam("dataInicio") String dataInicio,
            @QueryParam("dataFim") String dataFim,
            @QueryParam("pagina") Integer pagina,
            @QueryParam("tamanho") Integer tamanho) {
        PaginaSessoesDTO resultado = service.listarPaginado(
                trilhaId,
                disciplinaId,
                dataInicio,
                dataFim,
                pagina,
                tamanho
        );
        return Response.ok(resultado).build();
    }

    @GET
    @Path("estatisticas")
    public Response estatisticas(
            @PathParam("trilhaId") String trilhaId,
            @QueryParam("periodo") String periodo,
            @QueryParam("dataInicio") String dataInicio,
            @QueryParam("dataFim") String dataFim) {
        EstatisticasTrilhaDTO estatisticas = service.obterEstatisticas(
                trilhaId,
                periodo,
                dataInicio,
                dataFim
        );
        return Response.ok(estatisticas).build();
    }
}
