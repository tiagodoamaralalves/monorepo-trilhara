package br.com.trilhara.support;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Captura linhas de log da JVM durante um teste (R10/D-35c).
 *
 * <p>Anexado ao logger raiz, acumula as mensagens formatadas de todos os loggers. Usado por
 * {@code AccountDeletionIntegrationTest#logsNaoContemUsuarioId} para provar que nenhum
 * identificador pessoal vaza nos logs da exclusão de conta.</p>
 *
 * <p>Uso: {@code try (CapturadorDeLogs logs = CapturadorDeLogs.iniciar()) { ... }}</p>
 */
public final class CapturadorDeLogs extends Handler implements AutoCloseable {

    private final Logger raiz;
    private final List<String> linhas = new ArrayList<>();

    private CapturadorDeLogs(Logger raiz) {
        this.raiz = raiz;
        setLevel(Level.ALL);
    }

    /** Anexa o capturador ao logger raiz. */
    public static CapturadorDeLogs iniciar() {
        Logger raiz = Logger.getLogger("");
        CapturadorDeLogs capturador = new CapturadorDeLogs(raiz);
        raiz.addHandler(capturador);
        return capturador;
    }

    @Override
    public void publish(LogRecord registro) {
        if (registro == null) {
            return;
        }
        synchronized (linhas) {
            linhas.add(formatar(registro));
        }
    }

    private static String formatar(LogRecord registro) {
        String mensagem = registro.getMessage();
        Object[] parametros = registro.getParameters();
        if (mensagem != null && parametros != null && parametros.length > 0) {
            try {
                mensagem = java.text.MessageFormat.format(mensagem, parametros);
            } catch (IllegalArgumentException e) {
                // Formato com placeholders incompatíveis: mantém a mensagem original.
            }
        }
        StringBuilder sb = new StringBuilder();
        if (registro.getLoggerName() != null) {
            sb.append(registro.getLoggerName()).append(' ');
        }
        sb.append(mensagem != null ? mensagem : "");
        if (registro.getThrown() != null) {
            sb.append(' ').append(registro.getThrown());
        }
        return sb.toString();
    }

    @Override
    public void flush() {
        // Nada a descarregar: o buffer é em memória.
    }

    /** Linhas capturadas até o momento (cópia). */
    public List<String> linhas() {
        synchronized (linhas) {
            return List.copyOf(linhas);
        }
    }

    /** Texto integral capturado, útil para asserts de ausência de substrings. */
    public String textoCompleto() {
        return String.join("\n", linhas());
    }

    @Override
    public void close() {
        raiz.removeHandler(this);
        synchronized (linhas) {
            linhas.clear();
        }
    }
}
