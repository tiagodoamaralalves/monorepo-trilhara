package br.com.trilhara.shared;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.logging.Logger;

import org.bson.types.ObjectId;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;

/**
 * Contexto de fuso horário da requisição (@RequestScoped).
 * Lê o cabeçalho HTTP X-Timezone com fallback seguro para America/Sao_Paulo.
 */
@RequestScoped
public class TimezoneContext {

    private static final Logger LOG = Logger.getLogger(TimezoneContext.class.getName());

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");
    public static final String HEADER_TIMEZONE = "X-Timezone";

    @Context
    HttpHeaders httpHeaders;

    private ZoneId zoneId;
    private Clock clock;
    private UsuarioAtual usuarioAtual;

    public TimezoneContext() {
    }

    @Inject
    public TimezoneContext(UsuarioAtual usuarioAtual) {
        this.usuarioAtual = usuarioAtual;
    }

    public TimezoneContext(HttpHeaders httpHeaders) {
        this.httpHeaders = httpHeaders;
    }

    public TimezoneContext(String timezoneHeader) {
        definir(timezoneHeader);
    }

    public TimezoneContext(String timezoneHeader, Clock clock) {
        definir(timezoneHeader);
        this.clock = clock;
    }

    public void definir(String header) {
        this.zoneId = resolverZoneId(header);
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public ZoneId zoneId() {
        if (zoneId == null) {
            String header = null;
            if (httpHeaders != null) {
                try {
                    header = httpHeaders.getHeaderString(HEADER_TIMEZONE);
                } catch (Exception _) {
                    header = null;
                }
            }
            zoneId = resolverZoneId(header);
        }
        return zoneId;
    }

    public ObjectId usuarioIdObjectId() {
        if (usuarioAtual == null) {
            throw new IllegalStateException("UsuarioAtual não está disponível neste contexto.");
        }
        return usuarioAtual.usuarioIdObjectId();
    }

    public String donoId() {
        if (usuarioAtual == null) {
            throw new IllegalStateException("UsuarioAtual não está disponível neste contexto.");
        }
        return usuarioAtual.donoId();
    }

    public LocalDate hojeLocal() {
        ZoneId zid = zoneId();
        return clock != null ? LocalDate.now(clock.withZone(zid)) : LocalDate.now(zid);
    }

    public static ZoneId resolverZoneId(String header) {
        if (header == null || header.trim().isEmpty()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(header.trim());
        } catch (Exception _) {
            LOG.fine("Timezone inválido recebido no header " + HEADER_TIMEZONE + ": " + header + ". Usando fallback America/Sao_Paulo.");
            return DEFAULT_ZONE;
        }
    }
}
