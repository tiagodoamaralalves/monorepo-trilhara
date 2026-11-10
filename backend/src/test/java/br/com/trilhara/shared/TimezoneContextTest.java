package br.com.trilhara.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;

class TimezoneContextTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

    @Test
    @DisplayName("Deve resolver America/Sao_Paulo e hojeLocal correspondente")
    void deveResolverTimezoneSaoPaulo() {
        TimezoneContext context = new TimezoneContext("America/Sao_Paulo");
        assertEquals(SAO_PAULO, context.zoneId());
        assertEquals(LocalDate.now(SAO_PAULO), context.hojeLocal());
    }

    @Test
    @DisplayName("Deve resolver Asia/Tokyo e hojeLocal correspondente")
    void deveResolverTimezoneTokyo() {
        TimezoneContext context = new TimezoneContext("Asia/Tokyo");
        assertEquals(TOKYO, context.zoneId());
        assertEquals(LocalDate.now(TOKYO), context.hojeLocal());
    }

    @Test
    @DisplayName("Deve diferenciar hojeLocal entre Sao Paulo e Tokyo em instante de transição")
    void deveDiferenciarHojeLocalComClockFixo() {
        // 2026-11-09 22:00:00 UTC -> 19:00 em SP (09/11) e 07:00 em Tóquio (10/11)
        Instant instant = Instant.parse("2026-11-09T22:00:00Z");
        Clock fixedClock = Clock.fixed(instant, ZoneOffset.UTC);

        TimezoneContext contextSp = new TimezoneContext("America/Sao_Paulo", fixedClock);
        TimezoneContext contextTokyo = new TimezoneContext("Asia/Tokyo", fixedClock);

        assertEquals(LocalDate.of(2026, 11, 9), contextSp.hojeLocal());
        assertEquals(LocalDate.of(2026, 11, 10), contextTokyo.hojeLocal());
    }

    @Test
    @DisplayName("Deve usar fallback para America/Sao_Paulo quando cabeçalho for inválido")
    void deveUsarFallbackParaTimezoneInvalido() {
        TimezoneContext context = new TimezoneContext("Fuso/Inexistente_123");
        assertEquals(SAO_PAULO, context.zoneId());
        assertEquals(LocalDate.now(SAO_PAULO), context.hojeLocal());
    }

    @Test
    @DisplayName("Deve usar fallback para America/Sao_Paulo quando cabeçalho for ausente ou vazio")
    void deveUsarFallbackParaTimezoneAusenteOuVazio() {
        TimezoneContext contextNull = new TimezoneContext((String) null);
        assertEquals(SAO_PAULO, contextNull.zoneId());
        assertEquals(LocalDate.now(SAO_PAULO), contextNull.hojeLocal());

        TimezoneContext contextBlank = new TimezoneContext("   ");
        assertEquals(SAO_PAULO, contextBlank.zoneId());
        assertEquals(LocalDate.now(SAO_PAULO), contextBlank.hojeLocal());
    }

    @Test
    @DisplayName("Deve ler X-Timezone a partir de HttpHeaders injetado")
    void deveLerDoHttpHeaders() {
        HttpHeaders httpHeaders = new HttpHeaders() {
            @Override
            public List<String> getRequestHeader(String name) { return null; }

            @Override
            public String getHeaderString(String name) {
                return TimezoneContext.HEADER_TIMEZONE.equalsIgnoreCase(name) ? "Asia/Tokyo" : null;
            }

            @Override
            public MultivaluedMap<String, String> getRequestHeaders() { return null; }

            @Override
            public List<MediaType> getAcceptableMediaTypes() { return null; }

            @Override
            public List<Locale> getAcceptableLanguages() { return null; }

            @Override
            public MediaType getMediaType() { return null; }

            @Override
            public Locale getLanguage() { return null; }

            @Override
            public Map<String, Cookie> getCookies() { return null; }

            @Override
            public Date getDate() { return null; }

            @Override
            public int getLength() { return 0; }
        };

        TimezoneContext context = new TimezoneContext(httpHeaders);
        assertEquals(TOKYO, context.zoneId());
        assertNotNull(context.hojeLocal());
    }
}
