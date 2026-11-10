package br.com.trilhara.edital;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/editais")
@Produces(MediaType.APPLICATION_JSON)
public class EditalResource {

    private final EditalService service;

    public EditalResource(EditalService service) {
        this.service = service;
    }

    @GET
    public Pagina<EditalPublico> listar(
            @QueryParam("esfera") String esfera,
            @QueryParam("uf") String uf,
            @QueryParam("banca") String banca,
            @QueryParam("area") String area,
            @QueryParam("cargo") String cargo,
            @QueryParam("q") String busca,
            @QueryParam("emAndamento") Boolean emAndamento,
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size) {
        return service.listarPublicos(new FiltroEditalPublico(
                esfera, uf, banca, area, cargo, busca, emAndamento,
                new Paginacao(page == null ? 0 : page, size == null ? 20 : size)));
    }

    @GET
    @Path("{id}")
    public EditalPublico buscar(@PathParam("id") String id) {
        return service.buscarPublico(id);
    }
}
