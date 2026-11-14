package br.com.trilhara.push;

import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

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
import br.com.trilhara.push.dto.RemoverInscricaoPushRequest;
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
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Web Push do usuário autenticado.
 *
 * <p>Rotas: {@code GET /config}, {@code POST|DELETE /subscriptions} e
 * {@code PUT /preferencias}. Todas exigem autenticação; a inscrição de outro usuário não existe
 * (404), e o upsert de um {@code endpoint} já existente o reatribui ao usuário atual.</p>
 */
@Path("/api/push")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Autenticado
public class PushResource {

    /** Conjunto fechado de antecedências aceitas. */
    static final Set<Integer> MINUTOS_ACEITOS = Set.of(0, 5, 15, 30, 60);

    private final AuthContext authContext;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final LembretePushService lembretePushService;
    private final UserRepository userRepository;

    @ConfigProperty(name = "trilhara.push.habilitado", defaultValue = "true")
    boolean pushHabilitado;

    @ConfigProperty(name = "trilhara.push.vapid.public-key", defaultValue = "")
    java.util.Optional<String> vapidPublicKey = java.util.Optional.empty();

    @ConfigProperty(name = "trilhara.push.vapid.private-key", defaultValue = "")
    java.util.Optional<String> vapidPrivateKey = java.util.Optional.empty();

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
        // "habilitado" só é verdadeiro quando é possível assinar o envio (par de chaves completo).
        boolean habilitado = pushHabilitado && pubKey != null && temChavePrivada();

        return new PushConfigResponse(habilitado, pubKey, padrao);
    }

    /**
 * Upsert por {@code endpoint}. Se o {@code endpoint} já pertence a outro usuário,
 * ele é <strong>reatribuído</strong> ao usuário autenticado — o dispositivo físico é o mesmo
 * e a inscrição push não pode continuar entregando para a conta anterior.
 */
    @POST
    @Path("/subscriptions")
    public Response registrarInscricao(PushSubscriptionRequest request) {
        if (request == null || request.endpoint() == null || request.endpoint().isBlank()) {
            throw new ValidationException("endpoint", "Endpoint é obrigatório.");
        }
        String endpoint = request.endpoint().trim();
        validarEndpoint(endpoint);
        validarChaves(request.resolvedP256dh(), request.resolvedAuth());

        ObjectId usuarioId = extrairUsuarioId();
        Instant agora = Instant.now();

        PushSubscription sub = new PushSubscription(
                new ObjectId(),
                usuarioId,
                endpoint,
                request.resolvedP256dh(),
                request.resolvedAuth(),
                request.userAgent(),
                agora,
                agora);

        String idPersistido = pushSubscriptionRepository.salvarOuAtualizar(sub);

        return Response.status(Response.Status.CREATED)
                .entity(new PushSubscriptionResponse(idPersistido, endpoint, agora))
                .build();
    }

    /**
 * Remove a inscrição deste dispositivo (logout, ). Endpoint de outro usuário ou
 * inexistente responde {@code 404} sem revelar existência.
 */
    @DELETE
    @Path("/subscriptions")
    public Response removerInscricao(RemoverInscricaoPushRequest request) {
        if (request == null || request.endpoint() == null || request.endpoint().isBlank()) {
            throw new ValidationException("endpoint", "Endpoint é obrigatório para remoção.");
        }
        ObjectId usuarioId = extrairUsuarioId();
        boolean removido = pushSubscriptionRepository.removerPorEndpoint(usuarioId, request.endpoint().trim());
        if (!removido) {
            throw new NotFoundException("Inscrição push não encontrada.");
        }
        return Response.ok(Map.of("removido", true)).build();
    }

    @PUT
    @Path("/preferencias")
    public AtualizarPreferenciasPushResponse atualizarPreferencias(@Valid AtualizarPreferenciasPushRequest request) {
        int novoPadrao = request.padraoMinutosAntes();
        if (!MINUTOS_ACEITOS.contains(novoPadrao)) {
            throw new ValidationException("padraoMinutosAntes",
                    "Valor inválido. Use 0, 5, 15, 30 ou 60 minutos.");
        }
        ObjectId usuarioId = extrairUsuarioId();
        long recalculados = lembretePushService.atualizarPreferenciaGlobal(usuarioId, novoPadrao);
        return new AtualizarPreferenciasPushResponse(novoPadrao, recalculados);
    }

    /** Endpoint de Web Push é sempre HTTPS (RFC 8030 §3.2). */
    private void validarEndpoint(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException _) {
            throw new ValidationException("endpoint", "Endpoint malformado.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new ValidationException("endpoint", "Endpoint deve ser uma URL HTTPS.");
        }
    }

    /**
 * {@code p256dh} é chave pública P-256 não comprimida (65 bytes) e {@code auth} tem 16 bytes,
 * ambos em base64url sem padding (RFC 8291 §3.2).
 */
    private void validarChaves(String p256dh, String auth) {
        byte[] chavePublica = decodificarBase64Url(p256dh);
        if (chavePublica != null && (chavePublica.length != 65 || chavePublica[0] != 0x04)) {
            throw new ValidationException("keys.p256dh", "Chave p256dh deve ter 65 bytes (ponto EC não comprimido).");
        }
        byte[] segredo = decodificarBase64Url(auth);
        if (segredo != null && segredo.length != 16) {
            throw new ValidationException("keys.auth", "Chave auth deve ter 16 bytes.");
        }
    }

    private byte[] decodificarBase64Url(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ValidationException("keys", "As chaves p256dh e auth são obrigatórias.");
        }
        try {
            return Base64.getUrlDecoder().decode(valor.trim());
        } catch (IllegalArgumentException _) {
            throw new ValidationException("keys", "Chaves devem estar em base64url.");
        }
    }

    private boolean temChavePrivada() {
        return vapidPrivateKey.isPresent() && !vapidPrivateKey.get().isBlank();
    }

    private ObjectId extrairUsuarioId() {
        return authContext.usuarioIdObjectId();
    }
}
