package br.com.trilhara.auth;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;

import br.com.trilhara.auth.dto.HandoffResponse;
import br.com.trilhara.shared.mongo.BsonDates;
import br.com.trilhara.shared.mongo.ColecaoTrilhara;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;

/**
 * Handoff SSO para ambiente local ({@code *.localhost}), onde não há cookie compartilhado.
 *
 * <p>Persistência na coleção privada {@code tokens_handoff_sso}. Os índices são
 * criados exclusivamente por {@code MongoIndexInitializer}. Consumo é atômico e de
 * uso único via {@code findOneAndUpdate} com {@code consumidoEm: null} e
 * {@code expiraEm > agora}.</p>
 */
@ApplicationScoped
public class HandoffService {

    private static final Logger LOG = Logger.getLogger(HandoffService.class);
    private static final int TTL_SEGUNDOS = 60;
    private static final String CAMPO_CONSUMIDO_EM = "consumidoEm";
    private static final String CAMPO_USUARIO_ID = "usuarioId";
    private static final String CAMPO_CODIGO_HASH = "codigoHash";
    private static final String CAMPO_ORIGEM_DESTINO = "origemDestino";
    private static final String CAMPO_EXPIRA_EM = "expiraEm";
    private static final String ERRO_DESTINO_DIVERGENTE = "Destino divergente do autorizado no handoff.";

    private final SecureRandom secureRandom = new SecureRandom();
    private final MongoCollection<Document> collection;
    private final Set<String> allowlistOrigens;

    @Inject
    public HandoffService(
            MongoClient mongoClient,
            @ConfigProperty(name = "quarkus.mongodb.database") String database,
            @ConfigProperty(name = "trilhara.auth.cors.allowed-origins", defaultValue = "http://localhost:5173,http://localhost:5174,http://localhost:5175,http://localhost:5176") String allowedOriginsConfig) {
        this.collection = mongoClient.getDatabase(database)
                .getCollection(ColecaoTrilhara.TOKENS_HANDOFF_SSO.nome());
        this.allowlistOrigens = new HashSet<>();
        if (allowedOriginsConfig != null && !allowedOriginsConfig.isBlank()) {
            Arrays.stream(allowedOriginsConfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .forEach(this.allowlistOrigens::add);
        }
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
        String codigoHash = calcularSha256(codigoPuro);

        Instant agora = Instant.now();
        Instant expiraEm = agora.plusSeconds(TTL_SEGUNDOS);

        Document doc = new Document("_id", new ObjectId())
                .append(CAMPO_USUARIO_ID, new ObjectId(usuarioId))
                .append(CAMPO_CODIGO_HASH, codigoHash)
                .append(CAMPO_ORIGEM_DESTINO, destinoUrl.trim())
                .append("criadoEm", BsonDates.toBsonDate(agora))
                .append(CAMPO_EXPIRA_EM, BsonDates.toBsonDate(expiraEm))
                .append(CAMPO_CONSUMIDO_EM, null);

        collection.insertOne(doc);
        LOG.infof("Código de handoff emitido com TTL de %ds.", TTL_SEGUNDOS);
        return new HandoffResponse(codigoPuro, expiraEm);
    }

    /**
 * Consome o código de handoff uma única vez.
 *
 * @return o {@code usuarioId} (hex) dono do código
 * @throws NotAuthorizedException código inexistente, expirado ou já consumido
 * @throws BadRequestException destino do código divergente do host chamador
 */
    public String consumirHandoff(String codigoPuro, String callerHost) {
        if (codigoPuro == null || codigoPuro.isBlank()) {
            throw new BadRequestException("Código de handoff é obrigatório.");
        }

        String codigoHash = calcularSha256(codigoPuro.trim());
        Instant agora = Instant.now();

        Document doc = collection.findOneAndUpdate(
                Filters.and(
                        Filters.eq(CAMPO_CODIGO_HASH, codigoHash),
                        Filters.eq(CAMPO_CONSUMIDO_EM, null),
                        Filters.gt(CAMPO_EXPIRA_EM, BsonDates.toBsonDate(agora))),
                Updates.set(CAMPO_CONSUMIDO_EM, BsonDates.toBsonDate(agora)),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE));

        if (doc == null) {
            throw new NotAuthorizedException("Código de handoff inválido, expirado ou já utilizado.");
        }

        String origemDestino = doc.getString(CAMPO_ORIGEM_DESTINO);
        if (callerHost != null && origemDestino != null) {
            validarOrigemDestinoConsistente(origemDestino, callerHost);
        }

        ObjectId usuarioId = doc.getObjectId(CAMPO_USUARIO_ID);
        if (usuarioId == null) {
            throw new NotAuthorizedException("Código de handoff inválido.");
        }
        return usuarioId.toHexString();
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
        } catch (IllegalArgumentException _) {
            throw new BadRequestException(ERRO_DESTINO_DIVERGENTE);
        }

        String hostEsperado = uri.getHost();
        String callerHostLimpo = callerHost.split(":")[0].trim().toLowerCase();
        if (hostEsperado == null) {
            throw new BadRequestException(ERRO_DESTINO_DIVERGENTE);
        }

        String hostEsperadoNormalizado = hostEsperado.toLowerCase();
        boolean mesmosHosts = hostEsperadoNormalizado.equals(callerHostLimpo);
        boolean ambosLocais = ehHostLocal(hostEsperadoNormalizado) && ehHostLocal(callerHostLimpo);
        if (!mesmosHosts && !ambosLocais) {
            LOG.warnf("Tentativa de consumo de handoff com destino divergente: esperado=%s, caller=%s", hostEsperadoNormalizado, callerHostLimpo);
            throw new BadRequestException(ERRO_DESTINO_DIVERGENTE);
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
