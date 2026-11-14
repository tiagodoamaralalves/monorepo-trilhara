package br.com.trilhara.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TokenService {

    private static final String ALGORITMO_HMAC = "HmacSHA256";
    private static final Base64.Encoder B64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper mapper;
    private final byte[] secretBytes;
    private final String issuer;
    private final String audience;
    private final long accessExpirationSeconds;
    private final long clockSkewSeconds;

    public TokenService(
            ObjectMapper mapper,
            @ConfigProperty(name = "trilhara.auth.jwt.secret") String secret,
            @ConfigProperty(name = "trilhara.auth.jwt.issuer", defaultValue = "https://trilhara.com.br") String issuer,
            @ConfigProperty(name = "trilhara.auth.jwt.audience", defaultValue = "trilhara-web") String audience,
            @ConfigProperty(name = "trilhara.auth.jwt.access-expiration-seconds", defaultValue = "900") long accessExpirationSeconds,
            @ConfigProperty(name = "trilhara.auth.jwt.clock-skew-seconds", defaultValue = "30") long clockSkewSeconds) {
        this.mapper = mapper;
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("A chave secreta JWT (AUTH_JWT_SECRET) deve possuir pelo menos 32 bytes (256 bits).");
        }
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.issuer = issuer;
        this.audience = audience;
        this.accessExpirationSeconds = accessExpirationSeconds;
        this.clockSkewSeconds = Math.max(0, clockSkewSeconds);
    }

    public long getAccessExpirationSeconds() {
        return accessExpirationSeconds;
    }

    public String gerarAccessToken(Usuario usuario) {
        return gerarAccessToken(usuario, Instant.now(), accessExpirationSeconds);
    }

    public String gerarAccessToken(Usuario usuario, Instant emitidoEm, long duracaoSegundos) {
        try {
            Map<String, Object> header = Map.of(
                    "alg", "HS256",
                    "typ", "JWT");

            long iat = emitidoEm.getEpochSecond();
            long exp = emitidoEm.plusSeconds(duracaoSegundos).getEpochSecond();
            List<String> roles = usuario.roles().stream().map(Role::name).toList();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("iss", issuer);
            payload.put("aud", audience);
            payload.put("sub", usuario.id());
            payload.put("email", usuario.email());
            payload.put("roles", roles);
            payload.put("groups", roles);
            payload.put("iat", iat);
            payload.put("nbf", iat);
            payload.put("exp", exp);
            payload.put("jti", UUID.randomUUID().toString());
            if (usuario.senhaAlteradaEm() != null) {
                payload.put(CLAIM_PWD_IAT, usuario.senhaAlteradaEm().toEpochMilli());
            }

            String headerB64 = B64_ENCODER.encodeToString(mapper.writeValueAsBytes(header));
            String payloadB64 = B64_ENCODER.encodeToString(mapper.writeValueAsBytes(payload));
            String conteudoAssinado = headerB64 + "." + payloadB64;
            String assinaturaB64 = B64_ENCODER.encodeToString(assinar(conteudoAssinado));

            return conteudoAssinado + "." + assinaturaB64;
        } catch (Exception exception) {
            throw new IllegalStateException("Erro ao gerar access token JWT.", exception);
        }
    }

    private static final String CLAIM_PWD_IAT = "pwd_iat";

    public ClaimsToken validarAccessToken(String token) {
        return validarAccessToken(token, Instant.now());
    }

    public ClaimsToken validarAccessToken(String token, Instant agora) {
        if (token == null || token.isBlank()) {
            throw new TokenInvalidoException("Token de acesso ausente.");
        }
        String[] partes = token.split("\\.");
        if (partes.length != 3) {
            throw new TokenInvalidoException("Formato de token JWT inválido.");
        }

        try {
            validarCabecalhoEAssinatura(partes);
            JsonNode payloadNode = mapper.readTree(B64_DECODER.decode(partes[1]));
            String sub = validarClaimsIdentidade(payloadNode);
            long[] tempos = validarClaimsTemporais(payloadNode, agora);

            List<Role> roles = extrairRoles(payloadNode);
            String email = payloadNode.path("email").asText("");
            String jti = payloadNode.path("jti").asText("");
            Long pwdIat = payloadNode.hasNonNull(CLAIM_PWD_IAT) ? payloadNode.get(CLAIM_PWD_IAT).asLong() : null;

            return new ClaimsToken(
                    sub,
                    email,
                    List.copyOf(roles),
                    jti,
                    Instant.ofEpochSecond(tempos[0]),
                    Instant.ofEpochSecond(tempos[1]),
                    pwdIat != null ? Instant.ofEpochMilli(pwdIat) : null);
        } catch (TokenInvalidoException exception) {
            throw exception;
        } catch (Exception _) {
            throw new TokenInvalidoException("Token de acesso inválido ou malformado.");
        }
    }

    private void validarCabecalhoEAssinatura(String[] partes) throws Exception {
        byte[] headerBytes = B64_DECODER.decode(partes[0]);
        JsonNode headerNode = mapper.readTree(headerBytes);
        String alg = headerNode.path("alg").asText("");
        String typ = headerNode.path("typ").asText("");
        if (!"HS256".equals(alg) || !"JWT".equals(typ)) {
            throw new TokenInvalidoException("Algoritmo ou cabeçalho JWT não suportado.");
        }

        String conteudoAssinado = partes[0] + "." + partes[1];
        byte[] assinaturaRecebida = B64_DECODER.decode(partes[2]);
        byte[] assinaturaEsperada = assinar(conteudoAssinado);

        if (!MessageDigest.isEqual(assinaturaEsperada, assinaturaRecebida)) {
            throw new TokenInvalidoException("Assinatura do token JWT inválida.");
        }
    }

    private String validarClaimsIdentidade(JsonNode payloadNode) {
        String iss = payloadNode.path("iss").asText("");
        if (!issuer.equals(iss)) {
            throw new TokenInvalidoException("Emissor (iss) do token inválido.");
        }

        String aud = payloadNode.path("aud").asText("");
        if (!audience.equals(aud)) {
            throw new TokenInvalidoException("Audiência (aud) do token inválida.");
        }

        String sub = payloadNode.path("sub").asText("");
        if (sub.isBlank()) {
            throw new TokenInvalidoException("Identificador do usuário (sub) ausente no token.");
        }
        return sub;
    }

    private long[] validarClaimsTemporais(JsonNode payloadNode, Instant agora) {
        if (!payloadNode.hasNonNull("exp") || !payloadNode.hasNonNull("iat")) {
            throw new TokenInvalidoException("Claims temporais obrigatórias ausentes no token.");
        }

        long epochAgora = agora.getEpochSecond();
        long exp = payloadNode.get("exp").asLong();
        long iat = payloadNode.get("iat").asLong();
        long nbf = payloadNode.path("nbf").asLong(iat);

        if (epochAgora > exp) {
            throw new TokenInvalidoException("Token de acesso expirado.");
        }
        if (epochAgora + clockSkewSeconds < nbf || epochAgora + clockSkewSeconds < iat) {
            throw new TokenInvalidoException("Token de acesso com timestamp futuro inválido.");
        }
        return new long[] { iat, exp };
    }

    private static List<Role> extrairRoles(JsonNode payloadNode) {
        List<Role> roles = new ArrayList<>();
        JsonNode rolesNode = payloadNode.path("roles");
        if (rolesNode.isArray()) {
            for (JsonNode r : rolesNode) {
                adicionarRoleSeValida(roles, r.asText());
            }
        }
        if (roles.isEmpty()) {
            roles.add(Role.USER);
        }
        return roles;
    }

    private static void adicionarRoleSeValida(List<Role> roles, String roleTexto) {
        try {
            roles.add(Role.valueOf(roleTexto));
        } catch (IllegalArgumentException _) {
            // Ignora role desconhecida
        }
    }

    private byte[] assinar(String dados) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(ALGORITMO_HMAC);
        mac.init(new SecretKeySpec(secretBytes, ALGORITMO_HMAC));
        return mac.doFinal(dados.getBytes(StandardCharsets.UTF_8));
    }

    public record ClaimsToken(
            String usuarioId,
            String email,
            List<Role> roles,
            String jti,
            Instant emitidoEm,
            Instant expiraEm,
            Instant senhaAlteradaEm) {
    }

    public static class TokenInvalidoException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public TokenInvalidoException(String message) {
            super(message);
        }
    }
}

