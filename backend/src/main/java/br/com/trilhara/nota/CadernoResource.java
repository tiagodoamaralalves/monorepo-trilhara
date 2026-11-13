package br.com.trilhara.nota;

import java.net.URI;
import java.util.List;

import org.bson.types.ObjectId;

import br.com.trilhara.auth.AuthContext;
import br.com.trilhara.auth.Autenticado;
import br.com.trilhara.nota.dto.AtualizarCadernoRequest;
import br.com.trilhara.nota.dto.AtualizarSecaoRequest;
import br.com.trilhara.nota.dto.CadernoImpactoExclusaoResponse;
import br.com.trilhara.nota.dto.CadernoResponse;
import br.com.trilhara.nota.dto.CriarCadernoRequest;
import br.com.trilhara.nota.dto.CriarNotaRequest;
import br.com.trilhara.nota.dto.CriarSecaoRequest;
import br.com.trilhara.nota.dto.ExclusaoLixeiraResponse;
import br.com.trilhara.nota.dto.NotaDetalheResponse;
import br.com.trilhara.nota.dto.PaginaNotasResponse;
import br.com.trilhara.nota.dto.SecaoResponse;
import br.com.trilhara.nota.dto.SincronizacaoTrilhaResponse;
import br.com.trilhara.shared.mongo.BsonIds;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Cadernos, seções e páginas (D-29 a D-34).
 *
 * <p>A criação de página existe <strong>apenas</strong> na rota aninhada
 * {@code POST /api/cadernos/{id}/secoes/{secaoId}/notas}: a rota duplicada {@code POST /api/notas}
 * foi removida (DC-12). A leitura completa continua exclusivamente em
 * {@code GET /api/notas/{notaId}} (D-32).</p>
 */
@Path("/api/cadernos")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Autenticado
public class CadernoResource {

    @Inject
    NotaService notaService;

    @Inject
    AuthContext authContext;

    private ObjectId usuarioId() {
        return authContext.usuarioIdObjectId();
    }

    // ── CADERNOS ──

    /**
     * Lista os cadernos ativos, opcionalmente filtrados pela trilha de origem.
     *
     * <p>É o filtro usado pelo link "Abrir no Notas" de Estudos (R3/R9).</p>
     */
    @GET
    public List<CadernoResponse> listar(@QueryParam("origemTrilhaId") String origemTrilhaId) {
        ObjectId origem = origemTrilhaId != null && !origemTrilhaId.isBlank()
                ? BsonIds.parse(origemTrilhaId, "origemTrilhaId")
                : null;
        return notaService.listarCadernos(usuarioId(), origem);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response criar(@Valid CriarCadernoRequest request) {
        CadernoResponse caderno = notaService.criarCaderno(usuarioId(), request);
        URI location = URI.create("/api/cadernos/" + caderno.id());
        return Response.created(location).entity(caderno).build();
    }

    @GET
    @Path("/{id}")
    public CadernoResponse buscar(@PathParam("id") String id) {
        return notaService.buscarCaderno(usuarioId(), BsonIds.parse(id, "id"));
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public CadernoResponse atualizar(
            @PathParam("id") String id, @Valid AtualizarCadernoRequest request) {
        return notaService.atualizarCaderno(usuarioId(), BsonIds.parse(id, "id"), request);
    }

    /** Contagens exibidas no {@code ConfirmDialog} antes de mover o caderno para a lixeira. */
    @GET
    @Path("/{id}/impacto-exclusao")
    public CadernoImpactoExclusaoResponse impactoExclusao(@PathParam("id") String id) {
        return notaService.obterImpactoExclusaoCaderno(usuarioId(), BsonIds.parse(id, "id"));
    }

    @DELETE
    @Path("/{id}")
    public ExclusaoLixeiraResponse deletar(@PathParam("id") String id) {
        return notaService.deletarCaderno(usuarioId(), BsonIds.parse(id, "id"));
    }

    // ── IMPORTAÇÃO E SINCRONIZAÇÃO DE TRILHA (D-29) ──

    /**
     * Importa uma trilha como caderno novo.
     *
     * <p>Sem {@code reimportar=true}, uma trilha que já possui caderno ativo vinculado responde
     * {@code 409 TRAIL_ALREADY_IMPORTED} com {@code errors.cadernoId}. Com a reimportação, o
     * caderno antigo é mantido e desvinculado.</p>
     */
    @POST
    @Path("/importar-trilha/{trilhaId}")
    public Response importarTrilha(
            @PathParam("trilhaId") String trilhaId,
            @QueryParam("reimportar") @DefaultValue("false") boolean reimportar) {
        CadernoResponse caderno = notaService.importarTrilha(
                usuarioId(), BsonIds.parse(trilhaId, "trilhaId"), reimportar);
        URI location = URI.create("/api/cadernos/" + caderno.id());
        return Response.created(location).entity(caderno).build();
    }

    /**
     * Sincroniza o caderno com a trilha de origem.
     *
     * <p>Caderno sem {@code origemTrilhaId} responde {@code 400 VALIDATION_ERROR} com
     * {@code errors.cadernoId}, não um 400 genérico.</p>
     */
    @POST
    @Path("/{id}/sincronizar-trilha")
    public SincronizacaoTrilhaResponse sincronizarTrilha(@PathParam("id") String id) {
        return notaService.sincronizarTrilha(usuarioId(), BsonIds.parse(id, "id"));
    }

    // ── SEÇÕES DO CADERNO ──

    @GET
    @Path("/{id}/secoes")
    public List<SecaoResponse> listarSecoes(@PathParam("id") String id) {
        return notaService.listarSecoes(usuarioId(), BsonIds.parse(id, "id"));
    }

    @POST
    @Path("/{id}/secoes")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response criarSecao(@PathParam("id") String id, @Valid CriarSecaoRequest request) {
        SecaoResponse secao = notaService.criarSecao(usuarioId(), BsonIds.parse(id, "id"), request);
        URI location = URI.create("/api/secoes/" + secao.id());
        return Response.created(location).entity(secao).build();
    }

    @PUT
    @Path("/{id}/secoes/{secaoId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public SecaoResponse atualizarSecaoNoCaderno(
            @PathParam("id") String id,
            @PathParam("secaoId") String secaoId,
            @Valid AtualizarSecaoRequest request) {
        ObjectId cadernoId = BsonIds.parse(id, "id");
        ObjectId secao = BsonIds.parse(secaoId, "secaoId");
        notaService.buscarCaderno(usuarioId(), cadernoId);
        return notaService.atualizarSecao(usuarioId(), secao, request);
    }

    @DELETE
    @Path("/{id}/secoes/{secaoId}")
    public ExclusaoLixeiraResponse deletarSecaoNoCaderno(
            @PathParam("id") String id,
            @PathParam("secaoId") String secaoId) {
        ObjectId cadernoId = BsonIds.parse(id, "id");
        ObjectId secao = BsonIds.parse(secaoId, "secaoId");
        notaService.buscarCaderno(usuarioId(), cadernoId);
        return notaService.deletarSecao(usuarioId(), secao);
    }

    // ── PÁGINAS DA SEÇÃO ──

    /** Metadados paginados das páginas; nunca inclui {@code conteudoMarkdown}. */
    @GET
    @Path("/{id}/secoes/{secaoId}/notas")
    public PaginaNotasResponse listarNotas(
            @PathParam("id") String id,
            @PathParam("secaoId") String secaoId,
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamanho") @DefaultValue("50") int tamanho) {
        return notaService.listarNotasMetadados(
                usuarioId(),
                BsonIds.parse(id, "id"),
                BsonIds.parse(secaoId, "secaoId"),
                pagina,
                tamanho);
    }

    @POST
    @Path("/{id}/secoes/{secaoId}/notas")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response criarNota(
            @PathParam("id") String id,
            @PathParam("secaoId") String secaoId,
            @Valid CriarNotaRequest request) {
        NotaDetalheResponse nota = notaService.criarNota(
                usuarioId(),
                BsonIds.parse(id, "id"),
                BsonIds.parse(secaoId, "secaoId"),
                request);
        URI location = URI.create("/api/notas/" + nota.id());
        return Response.created(location).entity(nota).build();
    }
}
