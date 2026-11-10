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

    public void limparTentativasLogin(String ip, String emailNormalizado) {
        if (emailNormalizado != null && !emailNormalizado.isBlank()) {
            janelasPorChave.remove("login:email:" + emailNormalizado);
        }
    }

    public void limparTudo() {
        janelasPorChave.clear();
    }

    private void registrarEVerificar(String chave, int limiteMaximo) {
        Instant agora = Instant.now();
        Instant corte = agora.minusSeconds(janelaSegundos);
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
                        janelaSegundos);
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
                        janelaSegundos);
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

