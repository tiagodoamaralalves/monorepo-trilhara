package br.com.trilhara.edital;

import br.com.trilhara.auth.Autenticado;
import br.com.trilhara.auth.Role;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/admin/editais")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Autenticado(roles = Role.ADMIN)
public class EditalAdminResource {

    private final EditalService service;

    public EditalAdminResource(EditalService service) {
        this.service = service;
    }

    @POST
    public Response criar(@Valid Edital edital) {
        Edital criado = service.criar(edital);
        return Response.status(Response.Status.CREATED).entity(criado).build();
    }

    @PUT
    @Path("{id}")
    public Edital atualizar(@PathParam("id") String id, @Valid Edital edital) {
        return service.atualizar(id, edital);
    }

    @PATCH
    @Path("{id}/status")
    public Edital alterarStatus(@PathParam("id") String id, AlteracaoStatus alteracao) {
        return service.alterarStatus(id, alteracao == null ? null : alteracao.status());
    }

    @GET
    public Pagina<Edital> listar(
            @QueryParam("status") String status,
            @QueryParam("banca") String banca,
            @QueryParam("orgao") String orgao,
            @QueryParam("cargo") String cargo,
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size) {
        return service.listarAdmin(status, banca, orgao, cargo, page == null ? 0 : page, size == null ? 20 : size);
    }

    @GET
    @Path("{id}")
    public Edital buscar(@PathParam("id") String id) {
        return service.buscarAdmin(id);
    }

    @DELETE
    @Path("{id}")
    public void excluir(@PathParam("id") String id) {
        service.excluir(id);
    }

    public record AlteracaoStatus(StatusEdital status) {
    }
}
