package br.com.trilhara.trilha;

import java.net.URI;
import java.util.List;

import br.com.trilhara.auth.Autenticado;
import br.com.trilhara.trilha.dto.AtualizarDisciplinaRequest;
import br.com.trilhara.trilha.dto.AtualizarMetadadosTrilhaRequest;
import br.com.trilhara.trilha.dto.CriarDisciplinaRequest;
import br.com.trilhara.trilha.dto.CriarTrilhaRequest;
import br.com.trilhara.trilha.dto.DisciplinaDTO;
import br.com.trilhara.trilha.dto.ProgressoTopicoDTO;
import br.com.trilhara.trilha.dto.ReordenarDisciplinasRequest;
import br.com.trilhara.trilha.dto.TrilhaDetalheDTO;
import br.com.trilhara.trilha.dto.TrilhaImpactoExclusaoDTO;
import br.com.trilhara.trilha.dto.TrilhaResumoDTO;
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

/**
 * Rotas de trilhas, disciplinas e progresso de tópicos (Anexo A da R3).
 *
 * <p>Somente as rotas da allowlist: qualquer variante mantida "por compatibilidade" (PATCH de
 * progresso, PATCH de metadados, importação por path) foi removida por D-11.</p>
 */
@Path("/api/trilhas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Autenticado
public class TrilhaResource {

    private static final String TEXTO_API_TRILHAS = "/api/trilhas/";

    private final TrilhaService service;

    public TrilhaResource(TrilhaService service) {
        this.service = service;
    }

    /** Listagem leve do Hub: metadados e contadores, sem arrays de tópicos (requisito §4.1). */
    @GET
    public List<TrilhaResumoDTO> listar() {
        return service.listarResumo();
    }

    @POST
    public Response criar(@Valid @NotNull CriarTrilhaRequest request) {
        TrilhaDetalheDTO criada = service.criarTrilha(request);
        return Response.created(URI.create(TEXTO_API_TRILHAS + criada.id())).entity(criada).build();
    }

    @GET
    @Path("{trilhaId}")
    public TrilhaDetalheDTO buscar(@PathParam("trilhaId") String trilhaId) {
        return service.buscarDetalhe(trilhaId);
    }

    @PUT
    @Path("{trilhaId}")
    public TrilhaDetalheDTO atualizar(
            @PathParam("trilhaId") String trilhaId,
            @Valid @NotNull AtualizarMetadadosTrilhaRequest request) {
        return service.atualizarMetadados(trilhaId, request);
    }

    @DELETE
    @Path("{trilhaId}")
    public Response excluir(@PathParam("trilhaId") String trilhaId) {
        service.excluirTrilha(trilhaId);
        return Response.noContent().build();
    }

    /** Contagens exibidas antes da exclusão irreversível da trilha (D-38). */
    @GET
    @Path("{trilhaId}/impacto-exclusao")
    public TrilhaImpactoExclusaoDTO impactoExclusao(@PathParam("trilhaId") String trilhaId) {
        return service.obterImpactoExclusao(trilhaId);
    }

    // =========================================================================
    // DISCIPLINAS (CRUD GRANULAR)
    // =========================================================================

    @POST
    @Path("{trilhaId}/disciplinas")
    public Response criarDisciplina(
            @PathParam("trilhaId") String trilhaId,
            @Valid @NotNull CriarDisciplinaRequest request) {
        DisciplinaDTO criada = service.criarDisciplina(trilhaId, request);
        return Response.created(URI.create(TEXTO_API_TRILHAS + trilhaId + "/disciplinas/" + criada.id()))
                .entity(criada)
                .build();
    }

    @PUT
    @Path("{trilhaId}/disciplinas/reordenar")
    public Response reordenarDisciplinas(
            @PathParam("trilhaId") String trilhaId,
            @Valid @NotNull ReordenarDisciplinasRequest request) {
        service.reordenarDisciplinas(trilhaId, request);
        return Response.noContent().build();
    }

    @PUT
    @Path("{trilhaId}/disciplinas/{disciplinaId}")
    public DisciplinaDTO atualizarDisciplina(
            @PathParam("trilhaId") String trilhaId,
            @PathParam("disciplinaId") String disciplinaId,
            @Valid @NotNull AtualizarDisciplinaRequest request) {
        return service.atualizarDisciplina(trilhaId, disciplinaId, request);
    }

    @DELETE
    @Path("{trilhaId}/disciplinas/{disciplinaId}")
    public Response excluirDisciplina(
            @PathParam("trilhaId") String trilhaId,
            @PathParam("disciplinaId") String disciplinaId) {
        service.excluirDisciplina(trilhaId, disciplinaId);
        return Response.noContent().build();
    }

    // =========================================================================
    // PROGRESSO DE TÓPICOS (IDEMPOTENTE)
    // =========================================================================

    @PUT
    @Path("{trilhaId}/topicos/{topicoId}/progresso")
    public ProgressoTopicoDTO marcarTopicoConcluido(
            @PathParam("trilhaId") String trilhaId,
            @PathParam("topicoId") String topicoId) {
        return service.marcarTopicoConcluido(trilhaId, topicoId);
    }

    /** Idempotente: {@code 204} mesmo quando não havia progresso registrado. */
    @DELETE
    @Path("{trilhaId}/topicos/{topicoId}/progresso")
    public Response desmarcarTopicoConcluido(
            @PathParam("trilhaId") String trilhaId,
            @PathParam("topicoId") String topicoId) {
        service.desmarcarTopicoConcluido(trilhaId, topicoId);
        return Response.noContent().build();
    }
}
