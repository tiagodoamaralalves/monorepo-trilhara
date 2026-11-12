package br.com.trilhara.push;

import java.time.Instant;
import java.util.Map;

import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import br.com.trilhara.auth.Autenticado;
import br.com.trilhara.auth.AuthContext;
import br.com.trilhara.auth.UserRepository;
import br.com.trilhara.push.dto.AtualizarPreferenciasPushRequest;
import br.com.trilhara.push.dto.AtualizarPreferenciasPushResponse;
import br.com.trilhara.push.dto.PushConfigResponse;
import br.com.trilhara.push.dto.PushSubscriptionRequest;
import br.com.trilhara.push.dto.PushSubscriptionResponse;
import br.com.trilhara.shared.erro.ValidationException;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/push")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Autenticado
public class PushResource {

    private final AuthContext authContext;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final LembretePushService lembretePushService;
    private final UserRepository userRepository;

    @ConfigProperty(name = "trilhara.push.habilitado", defaultValue = "true")
    boolean pushHabilitado;

    @ConfigProperty(name = "trilhara.push.vapid.public-key")
    java.util.Optional<String> vapidPublicKey;

    @Inject
    public PushResource(
            AuthContext authContext,
            PushSubscriptionRepository pushSubscriptionRepository,
            LembretePushService lembretePushService,
            UserRepository userRepository) {
        this.authContext = authContext;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.lembretePushService = lembretePushService;
        this.userRepository = userRepository;
    }

    @GET
    @Path("/config")
    public PushConfigResponse obterConfig() {
        ObjectId usuarioId = extrairUsuarioId();
        int padrao = userRepository.obterPadraoLembreteMinutos(usuarioId);

        String pubKey = vapidPublicKey.map(String::trim).filter(s -> !s.isBlank()).orElse(null);
        boolean habilitado = pushHabilitado && pubKey != null;

        return new PushConfigResponse(habilitado, pubKey, padrao);
    }

    @POST
    @Path("/subscriptions")
    public Response registrarInscricao(PushSubscriptionRequest request) {
        if (request == null || request.endpoint() == null || request.endpoint().isBlank()) {
            throw new ValidationException("endpoint", "Endpoint é obrigatório.");
        }

        ObjectId usuarioId = extrairUsuarioId();
        ObjectId id = new ObjectId();
        Instant agora = Instant.now();

        PushSubscription sub = new PushSubscription(
                id,
                usuarioId,
                request.endpoint().trim(),
                request.resolvedP256dh(),
                request.resolvedAuth(),
                request.userAgent(),
                agora,
                agora
        );

        pushSubscriptionRepository.salvarOuAtualizar(sub);

        return Response.status(Response.Status.CREATED)
                .entity(new PushSubscriptionResponse(id.toHexString(), sub.endpoint(), agora))
                .build();
    }

    @DELETE
    @Path("/subscriptions")
    public Response removerInscricao(
            @QueryParam("endpoint") String endpointParam,
            PushSubscriptionRequest requestBody) {

        ObjectId usuarioId = extrairUsuarioId();
        String endpoint = endpointParam;
        if ((endpoint == null || endpoint.isBlank()) && requestBody != null) {
            endpoint = requestBody.endpoint();
        }

        if (endpoint == null || endpoint.isBlank()) {
            throw new ValidationException("endpoint", "Endpoint é obrigatório para remoção.");
        }

        boolean removido = pushSubscriptionRepository.removerPorEndpoint(usuarioId, endpoint.trim());
        if (!removido) {
            throw new NotFoundException("Inscrição push não encontrada.");
        }
        return Response.ok(Map.of("removido", true)).build();
    }

    @PUT
    @Path("/preferencias")
    public AtualizarPreferenciasPushResponse atualizarPreferencias(@Valid AtualizarPreferenciasPushRequest request) {
        ObjectId usuarioId = extrairUsuarioId();
        int novoPadrao = request.padraoMinutosAntes();
        long recalculados = lembretePushService.atualizarPreferenciaGlobal(usuarioId, novoPadrao);
        return new AtualizarPreferenciasPushResponse(novoPadrao, recalculados);
    }

    private ObjectId extrairUsuarioId() {
        return authContext.usuarioIdObjectId();
    }
}
