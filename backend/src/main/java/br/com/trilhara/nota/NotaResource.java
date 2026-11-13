package br.com.trilhara.nota;

import java.util.Map;

import org.bson.types.ObjectId;

import br.com.trilhara.auth.AuthContext;
import br.com.trilhara.auth.Autenticado;
import br.com.trilhara.nota.dto.AtualizarNotaRequest;
import br.com.trilhara.nota.dto.BuscaNotasResponse;
import br.com.trilhara.nota.dto.ExclusaoLixeiraResponse;
import br.com.trilhara.nota.dto.LixeiraNotasResponse;
import br.com.trilhara.nota.dto.NotaDetalheResponse;
import br.com.trilhara.nota.dto.RestauracaoLixeiraResponse;
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

/**
 * Leitura canônica, atualização e exclusão de páginas, busca global e lixeira (D-30 a D-34).
 *
 * <p>A criação acontece apenas na rota aninhada por seção, em {@link CadernoResource}; não há
 * {@code POST /api/notas} nem leitura aninhada de página (DC-12/D-32).</p>
 */
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
        return authContext.usuarioIdObjectId();
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
    public LixeiraNotasResponse listarLixeira(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamanho") @DefaultValue("50") int tamanho) {
        return notaService.listarLixeira(usuarioId(), pagina, tamanho);
    }

    @POST
    @Path("/lixeira/{tipo}/{id}/restaurar")
    public RestauracaoLixeiraResponse restaurar(
            @PathParam("tipo") String tipo,
            @PathParam("id") String id) {
        return notaService.restaurarItem(usuarioId(), tipo, BsonIds.parse(id, "id"));
    }

    /** Esvaziamento imediato da lixeira (D-34); a UI confirma com variante {@code danger}. */
    @DELETE
    @Path("/lixeira/esvaziar")
    public Map<String, Long> esvaziarLixeira() {
        return notaService.esvaziarLixeira(usuarioId());
    }

    /** Endpoint canônico de leitura completa (D-32). */
    @GET
    @Path("/{id}")
    public NotaDetalheResponse buscar(@PathParam("id") String id) {
        return notaService.buscarNota(usuarioId(), BsonIds.parse(id, "id"));
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public NotaDetalheResponse atualizar(
            @PathParam("id") String id, @Valid AtualizarNotaRequest request) {
        return notaService.atualizarNota(usuarioId(), BsonIds.parse(id, "id"), request);
    }

    @DELETE
    @Path("/{id}")
    public ExclusaoLixeiraResponse deletar(@PathParam("id") String id) {
        return notaService.deletarNota(usuarioId(), BsonIds.parse(id, "id"));
    }
}
