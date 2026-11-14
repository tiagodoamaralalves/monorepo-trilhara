package br.com.trilhara.auth;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RateLimitService {

    private static final int LIMITE_CHAVES = 10_000;

    /** exclusão de conta / verificação de senha — 5 tentativas por 15 minutos/usuário. */
    private static final int LIMITE_EXCLUSAO_CONTA = 5;
    private static final long JANELA_EXCLUSAO_CONTA_SEGUNDOS = 15 * 60;

    /** login/refresh/handoff — 10 por minuto por IP. */
    private static final int LIMITE_AUTH_POR_IP = 10;
    /** `handoff/consume` — janela mais larga que a emissão (20/min/IP). */
    private static final int LIMITE_HANDOFF_CONSUMO_POR_IP = 20;
    /** busca de notas — 60 por minuto por usuário. */
    private static final int LIMITE_BUSCA_NOTAS = 60;
    /** criação de trilha/cronograma — 30 por minuto por usuário. */
    private static final int LIMITE_CRIACAO_ESTRUTURA = 30;
    private static final long JANELA_PADRAO_SEGUNDOS = 60;

    private final Map<String, Deque<Instant>> janelasPorChave = new ConcurrentHashMap<>();
    private final int maxTentativasLogin;
    private final int maxTentativasCadastro;
    private final long janelaSegundos;

    public RateLimitService(
            @ConfigProperty(name = "trilhara.auth.ratelimit.login-max", defaultValue = "10") int maxTentativasLogin,
            @ConfigProperty(name = "trilhara.auth.ratelimit.cadastro-max", defaultValue = "10") int maxTentativasCadastro,
            @ConfigProperty(name = "trilhara.auth.ratelimit.window-seconds", defaultValue = "60") long janelaSegundos) {
        this.maxTentativasLogin = Math.max(1, maxTentativasLogin);
        this.maxTentativasCadastro = Math.max(1, maxTentativasCadastro);
        this.janelaSegundos = Math.max(1, janelaSegundos);
    }

    public void verificarLimiteLogin(String ip, String emailNormalizado) {
        registrarEVerificar("login:ip:" + normalizar(ip), maxTentativasLogin);
        if (emailNormalizado != null && !emailNormalizado.isBlank()) {
            registrarEVerificar("login:email:" + emailNormalizado, maxTentativasLogin);
        }
    }

    public void verificarLimiteCadastro(String ip) {
        registrarEVerificar("cadastro:ip:" + normalizar(ip), maxTentativasCadastro);
    }

    public void verificarLimiteRecuperacaoSenha(String ip, String emailNormalizado) {
        registrarEVerificar("recuperacao:ip:" + normalizar(ip), Math.max(3, maxTentativasCadastro / 2));
        if (emailNormalizado != null && !emailNormalizado.isBlank()) {
            registrarEVerificar("recuperacao:email:" + emailNormalizado, 5);
        }
    }

    public void verificarLimiteResetSenha(String ip) {
        registrarEVerificar("reset:ip:" + normalizar(ip), Math.max(3, maxTentativasCadastro / 2));
    }

    public void verificarLimiteConfirmacaoEmail(String ip) {
        registrarEVerificar("confirmacao:ip:" + normalizar(ip), maxTentativasCadastro);
    }

    /**
 * {@code DELETE /api/auth/conta} e qualquer verificação de senha nesse fluxo são
 * limitados a <strong>5 tentativas por 15 minutos por usuário</strong>, independentes da
 * janela global de login/cadastro.
 *
 * <p>Protege a senha atual contra brute-force por meio do endpoint de exclusão, que aceita
 * reautenticação server-side.</p>
 */
    public void verificarLimiteExclusaoConta(String usuarioId) {
        registrarEVerificar("exclusao-conta:usuario:" + normalizar(usuarioId), LIMITE_EXCLUSAO_CONTA,
                JANELA_EXCLUSAO_CONTA_SEGUNDOS);
    }

    public void limparTentativasLogin(String ip, String emailNormalizado) {
        if (emailNormalizado != null && !emailNormalizado.isBlank()) {
            janelasPorChave.remove("login:email:" + emailNormalizado);
        }
    }

    public void limparTudo() {
        janelasPorChave.clear();
    }

    // ========================================================================
    // limitadores exigidos pela decisão e ausentes 
    // ========================================================================

    /**
 * {@code POST /api/auth/refresh} — <strong>10/min por IP</strong>.
 *
 * <p>O refresh é a rota mais chamada da renovação de sessão (single-flight por aba, 5 s
 * de throttle) e não tinha limite algum; um atacante com refresh token roubado podia
 * forçar rotações em paralelo até disparar a revogação por reuso da vítima.</p>
 */
    public void verificarLimiteRefresh(String ip) {
        registrarEVerificar("refresh:ip:" + normalizar(ip), LIMITE_AUTH_POR_IP, JANELA_PADRAO_SEGUNDOS);
    }

    /**
 * {@code POST /api/auth/handoff} (emissão) — <strong>10/min por IP</strong>.
 *
 * <p>Cada emissão grava um código persistido; sem limite, o endpoint autenticado vira
 * vetor de escrita em massa em {@code tokens_handoff_sso}.</p>
 */
    public void verificarLimiteHandoffEmissao(String ip) {
        registrarEVerificar("handoff-emissao:ip:" + normalizar(ip), LIMITE_AUTH_POR_IP,
                JANELA_PADRAO_SEGUNDOS);
    }

    /**
 * {@code POST /api/auth/handoff/consume} — <strong>20/min por IP</strong>.
 *
 * <p>Limite mais alto que a emissão porque o consumo pode ser tentado por várias abas
 * simultâneas da origem de destino. Não é um limite de adivinhação do código (o código é
 * opaco e de uso único), e sim de contenção de carga.</p>
 */
    public void verificarLimiteHandoffConsumo(String ip) {
        registrarEVerificar("handoff-consumo:ip:" + normalizar(ip), LIMITE_HANDOFF_CONSUMO_POR_IP,
                JANELA_PADRAO_SEGUNDOS);
    }

    /** {@code GET /api/notas/busca} — <strong>60/min por usuário</strong>. */
    public void verificarLimiteBuscaNotas(String usuarioId) {
        registrarEVerificar("busca-notas:usuario:" + normalizar(usuarioId), LIMITE_BUSCA_NOTAS,
                JANELA_PADRAO_SEGUNDOS);
    }

    /** {@code POST /api/trilhas} — <strong>30/min por usuário</strong>. */
    public void verificarLimiteCriacaoTrilha(String usuarioId) {
        registrarEVerificar("criacao-trilha:usuario:" + normalizar(usuarioId), LIMITE_CRIACAO_ESTRUTURA,
                JANELA_PADRAO_SEGUNDOS);
    }

    /** {@code POST /api/trilhas/{trilhaId}/cronogramas} — <strong>30/min por usuário</strong>. */
    public void verificarLimiteCriacaoCronograma(String usuarioId) {
        registrarEVerificar("criacao-cronograma:usuario:" + normalizar(usuarioId), LIMITE_CRIACAO_ESTRUTURA,
                JANELA_PADRAO_SEGUNDOS);
    }

    private void registrarEVerificar(String chave, int limiteMaximo) {
        registrarEVerificar(chave, limiteMaximo, janelaSegundos);
    }

    /** Variante com janela própria por chave. */
    private void registrarEVerificar(String chave, int limiteMaximo, long janelaDaChaveSegundos) {
        Instant agora = Instant.now();
        Instant corte = agora.minusSeconds(janelaDaChaveSegundos);
        if (janelasPorChave.size() >= LIMITE_CHAVES) {
            janelasPorChave.entrySet().removeIf(e -> {
                Deque<Instant> q = e.getValue();
                synchronized (q) {
                    return q.isEmpty() || q.peekLast().isBefore(corte);
                }
            });
            if (janelasPorChave.size() >= LIMITE_CHAVES && !janelasPorChave.containsKey(chave)) {
                throw new RateLimitExceededException(
                        "Muitas requisições simultâneas no sistema. Aguarde alguns instantes antes de tentar novamente.",
                        janelaDaChaveSegundos);
            }
        }

        Deque<Instant> fila = janelasPorChave.computeIfAbsent(chave, k -> new ArrayDeque<>());
        synchronized (fila) {
            while (!fila.isEmpty() && fila.peekFirst().isBefore(corte)) {
                fila.pollFirst();
            }
            if (fila.size() >= limiteMaximo) {
                throw new RateLimitExceededException(
                        "Muitas tentativas em sequência. Aguarde alguns instantes antes de tentar novamente.",
                        janelaDaChaveSegundos);
            }
            fila.addLast(agora);
        }
    }


    private static String normalizar(String valor) {
        return valor == null || valor.isBlank() ? "desconhecido" : valor.trim().toLowerCase();
    }

    public static class RateLimitExceededException extends RuntimeException {
        private final long retryAfterSeconds;

        public RateLimitExceededException(String message, long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}

