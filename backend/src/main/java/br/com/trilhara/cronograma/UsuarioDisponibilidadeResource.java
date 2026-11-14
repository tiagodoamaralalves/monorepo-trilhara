package br.com.trilhara.cronograma;

import br.com.trilhara.auth.Autenticado;
import br.com.trilhara.cronograma.dto.AtualizarDisponibilidadeSemanalRequest;
import br.com.trilhara.cronograma.dto.DisponibilidadeSemanalDTO;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/usuarios/me/disponibilidade-semanal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Autenticado
public class UsuarioDisponibilidadeResource {

    private final CronogramaService service;

    @Inject
    public UsuarioDisponibilidadeResource(CronogramaService service) {
        this.service = service;
    }

    @GET
    public DisponibilidadeSemanalDTO obterDisponibilidadeSemanal() {
        return service.obterDisponibilidadeSemanal();
    }

    @PUT
    public DisponibilidadeSemanalDTO atualizarDisponibilidadeSemanal(
            @Valid @NotNull AtualizarDisponibilidadeSemanalRequest request) {
        return service.atualizarDisponibilidadeSemanal(request.disponibilidadeSemanalMinutos());
    }
}
