package br.com.trilhara.push;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Monta o payload de notificação e valida a URL de destino contra uma allowlist.
 *
 * <p>O payload é {@code {lembreteId, tarefaId, titulo, url}} — nada além disso. O {@code titulo}
 * é truncado em {@link #LIMITE_TITULO} caracteres (por <em>code point</em>, nunca cortando um
 * par substituto) e o {@code url} é absoluto, derivado de {@code trilhara.app.tarefas-url}.</p>
 *
 * <p>A serialização é manual (4 campos string, dois deles hex de 24 caracteres) para não usar
 * {@code ObjectMapper} fora dos DTOs de API — o mesmo motivo pelo qual o gate de legado proíbe
 * serialização de documento.</p>
 */
@ApplicationScoped
public class PushPayloadFactory {

    private static final Logger LOG = Logger.getLogger(PushPayloadFactory.class);

    static final int LIMITE_TITULO = 80;
    static final String TITULO_PADRAO = "Lembrete de tarefa";

    private final String tarefasUrl;
    private final Set<String> origensPermitidas;

    @Inject
    public PushPayloadFactory(
            @ConfigProperty(name = "trilhara.app.tarefas-url", defaultValue = "http://localhost/") String tarefasUrl,
            @ConfigProperty(name = "trilhara.app.tarefas-url-allowlist",
                    defaultValue = "http://localhost,http://localhost:5173") String allowlist) {
        this.origensPermitidas = new HashSet<>();
        if (allowlist != null && !allowlist.isBlank()) {
            Arrays.stream(allowlist.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(PushPayloadFactory::origemNormalizada)
                    .filter(s -> s != null)
                    .forEach(this.origensPermitidas::add);
        }
        this.tarefasUrl = validarUrlBase(tarefasUrl);
    }

    /**
 * URL absoluta e validada da tarefa. Falha de configuração é erro de boot, não de request:
 * uma URL fora da allowlist permitiria sequestrar o clique da notificação.
 */
    private String validarUrlBase(String url) {
        String candidata = url == null || url.isBlank() ? "http://localhost/" : url.trim();
        URI uri;
        try {
            uri = URI.create(candidata);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("trilhara.app.tarefas-url inválida: " + candidata, e);
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalStateException("trilhara.app.tarefas-url exige scheme e host: " + candidata);
        }
        if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
            throw new IllegalStateException("trilhara.app.tarefas-url exige http ou https: " + candidata);
        }
        String origem = origemNormalizada(candidata);
        if (origem == null || !origensPermitidas.contains(origem)) {
            throw new IllegalStateException("trilhara.app.tarefas-url fora da allowlist ("
                    + origem + "): configure trilhara.app.tarefas-url-allowlist");
        }
        String base = uri.getScheme() + "://" + uri.getAuthority();
        return base.endsWith("/") ? base : base + "/";
    }

    /** URL absoluta do deep link {@code ?tarefaId=}. */
    public String urlDaTarefa(String tarefaIdHex) {
        return tarefasUrl + "?tarefaId=" + tarefaIdHex;
    }

    /**
 * JSON do payload. Os dois IDs vêm de {@code ObjectId.toHexString} (hex apenas) e o título
 * passa por {@link #escaparJson}; a URL já é validada no boot.
 */
    public String payloadJson(String lembreteIdHex, String tarefaIdHex, String titulo) {
        return "{\"lembreteId\":\"" + lembreteIdHex + "\""
                + ",\"tarefaId\":\"" + tarefaIdHex + "\""
                + ",\"titulo\":\"" + escaparJson(truncar(titulo)) + "\""
                + ",\"url\":\"" + escaparJson(urlDaTarefa(tarefaIdHex)) + "\"}";
    }

    /** Trunca em {@value #LIMITE_TITULO} code points, sem quebrar pares substitutos. */
    static String truncar(String titulo) {
        if (titulo == null || titulo.isBlank()) {
            return TITULO_PADRAO;
        }
        String limpo = titulo.trim();
        if (limpo.codePointCount(0, limpo.length()) <= LIMITE_TITULO) {
            return limpo;
        }
        int fim = limpo.offsetByCodePoints(0, LIMITE_TITULO);
        return limpo.substring(0, fim);
    }

    /** Escapa o mínimo obrigatório de JSON, inclusive caracteres de controle. */
    static String escaparJson(String valor) {
        StringBuilder sb = new StringBuilder(valor.length() + 8);
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String origemNormalizada(String url) {
        try {
            URI uri = URI.create(url.trim());
            if (uri.getScheme() == null || uri.getAuthority() == null) {
                return null;
            }
            return (uri.getScheme() + "://" + uri.getAuthority()).toLowerCase();
        } catch (IllegalArgumentException _) {
            LOG.warnf("Entrada inválida na allowlist de URL de tarefas: %s", url);
            return null;
        }
    }
}
