package br.com.trilhara.auth;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;

import br.com.trilhara.auth.dto.HandoffResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;

@ApplicationScoped
public class HandoffService {

    private static final Logger LOG = Logger.getLogger(HandoffService.class);
    private static final int TTL_SEGUNDOS = 60;

    private final SecureRandom secureRandom = new SecureRandom();
    private final MongoCollection<Document> collection;
    private final Set<String> allowlistOrigens;

    @Inject
    public HandoffService(
            MongoClient mongoClient,
            @ConfigProperty(name = "quarkus.mongodb.database") String database,
            @ConfigProperty(name = "trilhara.auth.cors.allowed-origins", defaultValue = "http://localhost:5173,http://localhost:5174,http://localhost:5175,http://localhost:5176") String allowedOriginsConfig) {
        this.collection = mongoClient.getDatabase(database).getCollection("handoff_tokens");
        this.allowlistOrigens = new HashSet<>();
        if (allowedOriginsConfig != null && !allowedOriginsConfig.isBlank()) {
            Arrays.stream(allowedOriginsConfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .forEach(this.allowlistOrigens::add);
        }
        // Índices de busca por hash e expiração. Falhas devem impedir a inicialização do serviço.
        collection.createIndex(Indexes.ascending("tokenHash"), new IndexOptions().name("idx_handoff_tokens_tokenHash_unique").unique(true));
        collection.createIndex(Indexes.ascending("expiraEm"), new IndexOptions().name("idx_handoff_tokens_expiraEm"));
    }

    public HandoffResponse emitirHandoff(String usuarioId, String destinoUrl) {
        if (usuarioId == null || usuarioId.isBlank()) {
            throw new NotAuthorizedException("Usuário não autenticado.");
        }
        if (destinoUrl == null || destinoUrl.isBlank()) {
            throw new BadRequestException("Destino é obrigatório.");
        }

        validarDestinoPermitido(destinoUrl);

        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String codigoPuro = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String tokenHash = calcularSha256(codigoPuro);

        Instant agora = Instant.now();
        Instant expiraEm = agora.plusSeconds(TTL_SEGUNDOS);

        Document doc = new Document("tokenHash", tokenHash)
                .append("usuarioId", usuarioId)
                .append("destino", destinoUrl)
                .append("criadoEm", Date.from(agora))
                .append("expiraEm", Date.from(expiraEm))
                .append("usado", false);

        collection.insertOne(doc);
        LOG.infof("Token de handoff emitido para usuarioId=%s com TTL de %ds", usuarioId, TTL_SEGUNDOS);
        return new HandoffResponse(codigoPuro, expiraEm);
    }

    public String consumirHandoff(String codigoPuro, String callerHost) {
        if (codigoPuro == null || codigoPuro.isBlank()) {
            throw new BadRequestException("Código de handoff é obrigatório.");
        }

        String tokenHash = calcularSha256(codigoPuro.trim());
        Instant agora = Instant.now();

        // Consome atomicamente garantindo uso único (usado: false -> usado: true)
        Document doc = collection.findOneAndUpdate(
                Filters.and(
                        Filters.eq("tokenHash", tokenHash),
                        Filters.eq("usado", false),
                        Filters.gt("expiraEm", Date.from(agora))
                ),
                Updates.set("usado", true)
        );

        if (doc == null) {
            throw new NotAuthorizedException("Código de handoff inválido, expirado ou já utilizado.");
        }

        String destino = doc.getString("destino");
        if (callerHost != null && destino != null) {
            validarOrigemDestinoConsistente(destino, callerHost);
        }

        return doc.getString("usuarioId");
    }

    private void validarDestinoPermitido(String destinoUrl) {
        try {
            URI uri = URI.create(destinoUrl.trim());
            String host = uri.getHost();
            if (host == null) {
                throw new BadRequestException("Destino inválido para handoff: host ausente.");
            }
            host = host.toLowerCase();

            // Permite subdomínios locais (*.localhost, localhost, 127.0.0.1)
            if (host.equals("localhost") || host.endsWith(".localhost") || host.equals("127.0.0.1")) {
                return;
            }

            // Permite origens explicitamente configuradas
            String origin = uri.getScheme() + "://" + uri.getAuthority();
            if (allowlistOrigens.contains(origin)) {
                return;
            }

            throw new BadRequestException("Destino não permitido na allowlist de SSO local: " + destinoUrl);
        } catch (BadRequestException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("URL de destino malformada para handoff.", e);
        }
    }

    private void validarOrigemDestinoConsistente(String destino, String callerHost) {
        URI uri;
        try {
            uri = URI.create(destino);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Destino divergente do autorizado no handoff.");
        }

        String hostEsperado = uri.getHost();
        String callerHostLimpo = callerHost.split(":")[0].trim().toLowerCase();
        if (hostEsperado == null) {
            throw new BadRequestException("Destino divergente do autorizado no handoff.");
        }

        String hostEsperadoNormalizado = hostEsperado.toLowerCase();
        boolean mesmosHosts = hostEsperadoNormalizado.equals(callerHostLimpo);
        boolean ambosLocais = ehHostLocal(hostEsperadoNormalizado) && ehHostLocal(callerHostLimpo);
        if (!mesmosHosts && !ambosLocais) {
            LOG.warnf("Tentativa de consumo de handoff com destino divergente: esperado=%s, caller=%s", hostEsperadoNormalizado, callerHostLimpo);
            throw new BadRequestException("Destino divergente do autorizado no handoff.");
        }
    }

    private boolean ehHostLocal(String host) {
        return host.equals("localhost") || host.endsWith(".localhost") || host.equals("127.0.0.1");
    }

    private static String calcularSha256(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponível", e);
        }
    }
}
