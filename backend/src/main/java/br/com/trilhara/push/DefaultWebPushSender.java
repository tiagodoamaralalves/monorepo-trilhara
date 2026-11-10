package br.com.trilhara.push;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DefaultWebPushSender implements WebPushSender {

    private static final Logger LOG = Logger.getLogger(DefaultWebPushSender.class);

    @ConfigProperty(name = "trilhara.push.vapid.public-key")
    java.util.Optional<String> vapidPublicKey;

    @ConfigProperty(name = "trilhara.push.vapid.private-key")
    java.util.Optional<String> vapidPrivateKey;

    @ConfigProperty(name = "trilhara.push.vapid.subject", defaultValue = "mailto:suporte@trilhara.com.br")
    String vapidSubject;

    @ConfigProperty(name = "trilhara.push.habilitado", defaultValue = "true")
    boolean pushHabilitado;

    private final HttpClient httpClient;

    public DefaultWebPushSender() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public DefaultWebPushSender(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public PushSendResult send(PushSubscription subscription, String payloadJson, int ttlSeconds) {
        if (subscription == null || subscription.endpoint() == null || subscription.endpoint().isBlank()) {
            return PushSendResult.endpointInvalido(400, "Endpoint vazio ou inexistente.");
        }

        try {
            URI endpointUri = URI.create(subscription.endpoint().trim());
            int ttl = ttlSeconds > 0 ? ttlSeconds : 900;

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(endpointUri)
                    .timeout(Duration.ofSeconds(10))
                    .header("TTL", String.valueOf(ttl))
                    .header("Urgency", "normal");

            if (payloadJson != null && !payloadJson.isBlank()) {
                builder.header("Content-Type", "application/json");
                builder.POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8));
            } else {
                builder.POST(HttpRequest.BodyPublishers.noBody());
            }

            // Injeta autenticação VAPID se chaves estiverem configuradas
            if (isVapidConfigured()) {
                try {
                    String origin = endpointUri.getScheme() + "://" + endpointUri.getAuthority();
                    String vapidToken = gerarVapidToken(origin);
                    builder.header("Authorization", "vapid t=" + vapidToken + ", k=" + vapidPublicKey.get().trim());
                } catch (Exception e) {
                    LOG.warnf("Falha ao assinar cabeçalho VAPID para %s: %s", endpointUri, e.getMessage());
                }
            }

            HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();

            if (status == 200 || status == 201 || status == 202) {
                return PushSendResult.sucesso(status);
            } else if (status == 404 || status == 410) {
                return PushSendResult.endpointInvalido(status, "Inscrição inválida ou expirada no provedor Push (HTTP " + status + ").");
            } else {
                return PushSendResult.falha(status, "Provedor Push retornou status HTTP " + status);
            }

        } catch (Exception e) {
            LOG.warnf("Erro de rede ao enviar notificação Web Push para %s: %s", subscription.endpoint(), e.getMessage());
            return PushSendResult.falha(0, "Falha de rede: " + e.getMessage());
        }
    }

    public boolean isVapidConfigured() {
        return vapidPublicKey != null && vapidPublicKey.isPresent() && !vapidPublicKey.get().isBlank()
                && vapidPrivateKey != null && vapidPrivateKey.isPresent() && !vapidPrivateKey.get().isBlank();
    }

    private String gerarVapidToken(String audience) throws Exception {
        long exp = Instant.now().plus(Duration.ofHours(12)).getEpochSecond();
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = String.format("{\"aud\":\"%s\",\"exp\":%d,\"sub\":\"%s\"}", audience, exp, vapidSubject);
        String claimsBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        String unsignedToken = header + "." + claimsBase64;

        byte[] privateKeyBytes = Base64.getUrlDecoder().decode(vapidPrivateKey.get().trim());
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

        Signature dsa = Signature.getInstance("SHA256withECDSA");
        dsa.initSign(privateKey);
        dsa.update(unsignedToken.getBytes(StandardCharsets.UTF_8));
        byte[] derSignature = dsa.sign();

        byte[] rawSignature = derToRawJose(derSignature);
        String signatureBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(rawSignature);

        return unsignedToken + "." + signatureBase64;
    }

    private static byte[] derToRawJose(byte[] der) {
        if (der.length < 8 || der[0] != 0x30) {
            return der;
        }
        int rOffset = 3;
        int rLen = der[rOffset];
        int rStart = rOffset + 1;
        while (rLen > 32 && der[rStart] == 0) {
            rStart++;
            rLen--;
        }

        int sOffset = rStart + rLen + 1;
        int sLen = der[sOffset];
        int sStart = sOffset + 1;
        while (sLen > 32 && der[sStart] == 0) {
            sStart++;
            sLen--;
        }

        byte[] raw = new byte[64];
        System.arraycopy(der, rStart, raw, 32 - rLen, rLen);
        System.arraycopy(der, sStart, raw, 64 - sLen, sLen);
        return raw;
    }
}
