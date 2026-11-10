package br.com.trilhara.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import br.com.trilhara.auth.TokenService.TokenInvalidoException;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RefreshTokenService {

    private static final Logger LOG = Logger.getLogger(RefreshTokenService.class);
    private static final int TAMANHO_TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final RefreshTokenRepository repository;
    private final long expiracaoDias;
    private final long gracePeriodSeconds;

    public RefreshTokenService(
            RefreshTokenRepository repository,
            @ConfigProperty(name = "trilhara.auth.refresh.expiration-days", defaultValue = "30") long expiracaoDias,
            @ConfigProperty(name = "trilhara.auth.refresh.grace-period-seconds", defaultValue = "10") long gracePeriodSeconds) {
        this.repository = repository;
        this.expiracaoDias = Math.max(1, expiracaoDias);
        this.gracePeriodSeconds = gracePeriodSeconds;
    }

    public long getExpiracaoSegundos() {
        return expiracaoDias * 24 * 3600;
    }

    public ParRefreshToken emitirNovo(String usuarioId, String userAgent, String ip) {
        Instant agora = Instant.now();
        String familiaId = UUID.randomUUID().toString();
        return criarEPersistirToken(usuarioId, familiaId, userAgent, ip, agora);
    }

    /**
     * Realiza a rotação atômica do Refresh Token.
     * Se detectar tentativa de reutilização de um token já rotacionado ou revogado,
     * revoga imediatamente toda a família de tokens (proteção contra roubo de sessão / replay attack).
     */
    public ParRefreshToken rotacionar(String tokenPuro, String userAgent, String ip) {
        if (tokenPuro == null || tokenPuro.isBlank()) {
            throw new TokenInvalidoException("Refresh token é obrigatório.");
        }
        String hash = calcularHashSha256(tokenPuro);
        RefreshToken atualInicial = repository.buscarPorHash(hash)
                .orElseThrow(() -> new TokenInvalidoException("Refresh token inválido."));

        synchronized (atualInicial.familiaId().intern()) {
            RefreshToken atual = repository.buscarPorHash(hash)
                    .orElseThrow(() -> new TokenInvalidoException("Refresh token inválido."));

            Instant agora = Instant.now();

            if (atual.estaRevogado()) {
                if (gracePeriodSeconds > 0
                        && "ROTACIONADO".equals(atual.motivoRevogacao())
                        && atual.revogadoEm() != null
                        && Math.abs(Duration.between(atual.revogadoEm(), agora).toSeconds()) <= gracePeriodSeconds) {
                    LOG.infof("Grace period multi-aba de refresh token acionado para usuarioId=%s, familiaId=%s, ip=%s.",
                            atual.usuarioId(), atual.familiaId(), ip);
                    return criarEPersistirToken(
                            atual.usuarioId(),
                            atual.familiaId(),
                            userAgent != null ? userAgent : atual.userAgent(),
                            ip != null ? ip : atual.ip(),
                            agora);
                }

                LOG.warnf("ALERTA DE SEGURANCA: Reutilizacao de refresh token detectada para usuarioId=%s, familiaId=%s, ip=%s. Revogando toda a familia de sessao.",
                        atual.usuarioId(), atual.familiaId(), ip);
                repository.revogarFamilia(atual.familiaId(), agora, "REUSO_DETECTADO");
                throw new TokenInvalidoException("Sessão invalidada por segurança devido a reutilização de token.");
            }

            if (atual.estaExpirado(agora)) {
                RefreshToken expirado = new RefreshToken(
                        atual.id(),
                        atual.tokenHash(),
                        atual.familiaId(),
                        atual.usuarioId(),
                        atual.userAgent(),
                        atual.ip(),
                        atual.criadoEm(),
                        atual.ultimoUsoEm(),
                        atual.expiraEm(),
                        agora,
                        null,
                        "EXPIRADO");
                repository.atualizar(expirado);
                throw new TokenInvalidoException("Refresh token expirado.");
            }

            ParRefreshToken novoPar = criarEPersistirToken(
                    atual.usuarioId(),
                    atual.familiaId(),
                    userAgent != null ? userAgent : atual.userAgent(),
                    ip != null ? ip : atual.ip(),
                    agora);

            RefreshToken rotacionado = new RefreshToken(
                    atual.id(),
                    atual.tokenHash(),
                    atual.familiaId(),
                    atual.usuarioId(),
                    atual.userAgent(),
                    atual.ip(),
                    atual.criadoEm(),
                    agora,
                    atual.expiraEm(),
                    agora,
                    novoPar.entidade().tokenHash(),
                    "ROTACIONADO");
            repository.atualizar(rotacionado);

            return novoPar;
        }
    }

    public void revogarPorTokenPuro(String tokenPuro, String usuarioIdEsperado) {
        if (tokenPuro == null || tokenPuro.isBlank()) {
            return;
        }
        String hash = calcularHashSha256(tokenPuro);
        repository.buscarPorHash(hash).ifPresent(token -> {
            if (usuarioIdEsperado != null && !usuarioIdEsperado.equals(token.usuarioId())) {
                throw new SecurityException("Não é permitido revogar token pertencente a outro usuário.");
            }
            repository.revogarFamilia(token.familiaId(), Instant.now(), "LOGOUT");
        });
    }

    public boolean revogarFamiliaDoUsuario(String usuarioId, String familiaId) {
        return repository.revogarFamiliaDoUsuario(usuarioId, familiaId, Instant.now(), "REVOGACAO_MANUAL") > 0;
    }

    public long revogarTodasSessoesDoUsuario(String usuarioId, String motivo) {
        return repository.revogarTodosDoUsuario(usuarioId, Instant.now(), motivo);
    }

    public List<RefreshToken> listarSessoesAtivas(String usuarioId) {
        return repository.listarAtivosPorUsuario(usuarioId, Instant.now());
    }

    public String gerarTokenOpacoAleatorio() {
        byte[] bytes = new byte[TAMANHO_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String calcularHashSha256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponível.", exception);
        }
    }

    private ParRefreshToken criarEPersistirToken(
            String usuarioId,
            String familiaId,
            String userAgent,
            String ip,
            Instant agora) {
        String tokenPuro = gerarTokenOpacoAleatorio();
        String tokenHash = calcularHashSha256(tokenPuro);
        Instant expiraEm = agora.plus(expiracaoDias, ChronoUnit.DAYS);

        RefreshToken entidade = new RefreshToken(
                new ObjectId().toHexString(),
                tokenHash,
                familiaId,
                usuarioId,
                normalizarCabecalho(userAgent, 256),
                normalizarCabecalho(ip, 64),
                agora,
                agora,
                expiraEm,
                null,
                null,
                null);
        repository.inserir(entidade);
        return new ParRefreshToken(tokenPuro, entidade);
    }

    private static String normalizarCabecalho(String valor, int tamanhoMaximo) {
        if (valor == null || valor.isBlank()) {
            return "desconhecido";
        }
        String limpo = valor.trim();
        return limpo.length() <= tamanhoMaximo ? limpo : limpo.substring(0, tamanhoMaximo);
    }

    public record ParRefreshToken(String tokenPuro, RefreshToken entidade) {
    }
}

