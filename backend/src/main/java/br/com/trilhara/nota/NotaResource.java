package br.com.trilhara.nota;

import static br.com.trilhara.shared.mongo.BsonIds.toObjectIdOrNotFound;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;

import br.com.trilhara.auth.Autenticado;
import br.com.trilhara.auth.AuthContext;
import br.com.trilhara.nota.dto.AtualizarNotaRequest;
import br.com.trilhara.nota.dto.BuscaNotasResponse;
import br.com.trilhara.nota.dto.CriarNotaRequest;
import br.com.trilhara.nota.dto.ExclusaoLixeiraResponse;
import br.com.trilhara.nota.dto.ItemLixeiraResponse;
import br.com.trilhara.nota.dto.NotaDetalheResponse;
import br.com.trilhara.nota.dto.RestauracaoLixeiraResponse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
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

@Path("/api/notas")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Autenticado
public class NotaResource {

    @Inject
    NotaService notaService;

    @Inject
    AuthContext authContext;

    private ObjectId usuarioId() {
        return toObjectIdOrNotFound(authContext.usuarioId());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response criar(@Valid CriarNotaRequest request) {
        String cadernoIdStr = request.obterCadernoId();
        String secaoIdStr = request.secaoId();

        if (cadernoIdStr == null || cadernoIdStr.isBlank()) {
            throw new BadRequestException("O identificador do caderno é obrigatório.");
        }
        if (secaoIdStr == null || secaoIdStr.isBlank()) {
            throw new BadRequestException("O identificador da seção é obrigatório.");
        }

        NotaDetalheResponse nota = notaService.criarNota(
                usuarioId(),
                toObjectIdOrNotFound(cadernoIdStr),
                toObjectIdOrNotFound(secaoIdStr),
                request
        );
        URI location = URI.create("/api/notas/" + nota.id());
        return Response.created(location).entity(nota).build();
    }

    @GET
    @Path("/busca")
    public BuscaNotasResponse buscaGlobal(
            @QueryParam("q") String q,
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamanho") @DefaultValue("20") int tamanho) {
        return notaService.buscarGlobal(usuarioId(), q, pagina, tamanho);
    }

    @GET
    @Path("/lixeira")
    public List<ItemLixeiraResponse> listarLixeira(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamanho") @DefaultValue("50") int tamanho) {
        return notaService.listarLixeira(usuarioId(), pagina, tamanho);
    }

    @POST
    @Path("/lixeira/{tipo}/{id}/restaurar")
    public RestauracaoLixeiraResponse restaurar(
            @PathParam("tipo") String tipo,
            @PathParam("id") String id) {
        return notaService.restaurarItem(usuarioId(), tipo, toObjectIdOrNotFound(id));
    }

    @DELETE
    @Path("/lixeira/esvaziar")
    public Map<String, Long> esvaziarLixeira() {
        return notaService.esvaziarLixeira(usuarioId());
    }

    @GET
    @Path("/{id}")
    public NotaDetalheResponse buscar(@PathParam("id") String id) {
        return notaService.buscarNota(usuarioId(), toObjectIdOrNotFound(id));
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public NotaDetalheResponse atualizar(@PathParam("id") String id, @Valid AtualizarNotaRequest request) {
        return notaService.atualizarNota(usuarioId(), toObjectIdOrNotFound(id), request);
    }

    @DELETE
    @Path("/{id}")
    public ExclusaoLixeiraResponse deletar(@PathParam("id") String id) {
        return notaService.deletarNota(usuarioId(), toObjectIdOrNotFound(id));
    }
}
