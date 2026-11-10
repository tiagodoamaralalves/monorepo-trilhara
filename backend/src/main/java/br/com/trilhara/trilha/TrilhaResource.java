package br.com.trilhara.trilha;

import java.net.URI;
import java.util.List;
import java.util.Map;

import br.com.trilhara.auth.Autenticado;
import br.com.trilhara.trilha.dto.AtualizarDisciplinaRequest;
import br.com.trilhara.trilha.dto.AtualizarMetadadosTrilhaRequest;
import br.com.trilhara.trilha.dto.CriarDisciplinaRequest;
import br.com.trilhara.trilha.dto.CriarTrilhaRequest;
import br.com.trilhara.trilha.dto.DisciplinaDTO;
import br.com.trilhara.trilha.dto.ReordenarDisciplinasRequest;
import br.com.trilhara.trilha.dto.TrilhaDetalheDTO;
import br.com.trilhara.trilha.dto.TrilhaResumoDTO;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

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

    // =========================================================================
    // 1. LISTAGEM LEVE DO HUB (GET /api/trilhas)
    // =========================================================================

    @GET
    public List<TrilhaResumoDTO> listar() {
        return service.listarResumo();
    }

    // =========================================================================
    // 2. CRIAÇÃO DE TRILHA (POST /api/trilhas)
    // =========================================================================

    @POST
    public Response criar(@Valid @NotNull CriarTrilhaRequest request) {
        TrilhaDetalheDTO criada = service.criarTrilha(request);
        return Response.created(URI.create(TEXTO_API_TRILHAS + criada.id())).entity(criada).build();
    }

    // =========================================================================
    // 3. DETALHE DA TRILHA (GET /api/trilhas/{id})
    // =========================================================================

    @GET
    @Path("{id}")
    public TrilhaDetalheDTO buscar(@PathParam("id") String id) {
        return service.buscarDetalhe(id);
    }

    // =========================================================================
    // 4. ATUALIZAÇÃO DE METADADOS (PUT /api/trilhas/{id})
    // =========================================================================

    @PUT
    @Path("{id}")
    public TrilhaDetalheDTO atualizar(
            @PathParam("id") String id,
            @Valid @NotNull AtualizarMetadadosTrilhaRequest request) {
        return service.atualizarMetadados(id, request);
    }

    // =========================================================================
    // 5. EXCLUSÃO DE TRILHA (DELETE /api/trilhas/{id})
    // =========================================================================

    @DELETE
    @Path("{id}")
    public Response excluir(@PathParam("id") String id) {
        service.excluirTrilha(id);
        return Response.noContent().build();
    }

    // =========================================================================
    // 6. ENDPOINTS GRANULARES DE DISCIPLINAS E TÓPICOS
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
    @Path("{trilhaId}/disciplinas/{disciplinaId}")
    public DisciplinaDTO atualizarDisciplina(
            @PathParam("trilhaId") String trilhaId,
            @PathParam("disciplinaId") String disciplinaId,
            @Valid @NotNull AtualizarDisciplinaRequest request) {
        return service.atualizarDisciplina(trilhaId, disciplinaId, request);
    }

    @PUT
    @Path("{trilhaId}/disciplinas/reordenar")
    public Response reordenarDisciplinas(
            @PathParam("trilhaId") String trilhaId,
            @Valid @NotNull ReordenarDisciplinasRequest request) {
        service.reordenarDisciplinas(trilhaId, request);
        return Response.ok().build();
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
    // 7. ENDPOINTS DE PROGRESSO DE TÓPICOS
    // =========================================================================

    @PUT
    @Path("{trilhaId}/topicos/{topicoId}/progresso")
    public Response marcarTopicoConcluido(
            @PathParam("trilhaId") String trilhaId,
            @PathParam("topicoId") String topicoId) {
        service.marcarTopicoConcluido(trilhaId, topicoId);
        return Response.ok(Map.of("topicoId", topicoId, "concluido", true)).build();
    }

    @DELETE
    @Path("{trilhaId}/topicos/{topicoId}/progresso")
    public Response desmarcarTopicoConcluido(
            @PathParam("trilhaId") String trilhaId,
            @PathParam("topicoId") String topicoId) {
        service.desmarcarTopicoConcluido(trilhaId, topicoId);
        return Response.noContent().build();
    }

    // =========================================================================
    // ENDPOINTS DE COMPATIBILIDADE / TRANSIÇÃO
    // =========================================================================

    @POST
    @Path("importar/{editalId}")
    @Consumes(MediaType.WILDCARD)
    public Response importar(@PathParam("editalId") String editalId) {
        var criada = service.criarTrilha(new CriarTrilhaRequest(
                null, null, null, null, null, null, editalId, null, null
        ));
        return Response.created(URI.create(TEXTO_API_TRILHAS + criada.id())).entity(criada).build();
    }

    @PATCH
    @Path("{id}")
    public TrilhaDetalheDTO atualizarMetadadosPatch(
            @PathParam("id") String id,
            @Valid @NotNull AtualizacaoMetadadosTrilha dados) {
        return service.atualizarMetadados(id, new AtualizarMetadadosTrilhaRequest(
                dados.nome(), dados.areaCargo(), dados.areaCargo(), dados.banca(), dados.dataProva(), null
        ));
    }

    @PATCH
    @Path("{id}/progresso/{topicoId}")
    public TrilhaDetalheDTO atualizarProgressoLegado(
            @PathParam("id") String id,
            @PathParam("topicoId") String topicoId,
            @Valid AtualizacaoProgresso atualizacao) {
        if (atualizacao == null || atualizacao.done() == null) {
            throw new jakarta.ws.rs.BadRequestException("O campo 'done' é obrigatório.");
        }
        if (Boolean.TRUE.equals(atualizacao.done())) {
            service.marcarTopicoConcluido(id, topicoId);
        } else {
            service.desmarcarTopicoConcluido(id, topicoId);
        }
        return service.buscarDetalhe(id);
    }

    public record AtualizacaoProgresso(@NotNull Boolean done) {
    }
}
