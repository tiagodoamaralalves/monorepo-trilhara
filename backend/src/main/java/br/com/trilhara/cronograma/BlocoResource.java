package br.com.trilhara.cronograma;

import java.util.List;

import br.com.trilhara.auth.Autenticado;
import br.com.trilhara.cronograma.dto.AtualizarConclusaoBlocoRequest;
import br.com.trilhara.cronograma.dto.BlocoCronogramaItemDTO;
import br.com.trilhara.cronograma.dto.BlocoHojeDTO;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/blocos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Autenticado
public class BlocoResource {

    private final CronogramaService service;

    @Inject
    public BlocoResource(CronogramaService service) {
        this.service = service;
    }

    @GET
    @Path("/hoje")
    public List<BlocoHojeDTO> listarHoje() {
        return service.listarBlocosHoje();
    }

    @PATCH
    @Path("/{blocoId}/conclusao")
    public BlocoCronogramaItemDTO atualizarConclusao(
            @PathParam("blocoId") String blocoId,
            @Valid @NotNull AtualizarConclusaoBlocoRequest request) {
        return service.atualizarConclusaoBloco(blocoId, request.concluido());
    }
}
