package br.com.trilhara.sessao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.trilhara.shared.TimezoneContext;

/**
 * Definição normativa de streak.
 *
 * <p>O streak não "quebra" enquanto o dia corrente não terminou: quando não há sessão hoje, a
 * contagem termina em ontem. A borda 23h30 em {@code America/Sao_Paulo} é exercitada com
 * {@link Clock} fixo, sem depender do relógio da máquina.</p>
 */
class StreakCalculatorTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Test
    @DisplayName("Deve contar dias consecutivos terminando hoje")
    void deveContarTerminandoHoje() {
        LocalDate hoje = LocalDate.of(2026, 11, 12);
        Set<LocalDate> dias = new HashSet<>();
        dias.add(hoje);
        dias.add(hoje.minusDays(1));
        dias.add(hoje.minusDays(2));

        assertEquals(3, StreakCalculator.calcular(dias, hoje));
    }

    @Test
    @DisplayName("Deve terminar ontem quando não há sessão hoje")
    void deveTerminarOntemSemSessaoHoje() {
        LocalDate hoje = LocalDate.of(2026, 11, 12);
        Set<LocalDate> dias = new HashSet<>();
        dias.add(hoje.minusDays(1));
        dias.add(hoje.minusDays(2));
        dias.add(hoje.minusDays(3));

        assertEquals(3, StreakCalculator.calcular(dias, hoje));
    }

    @Test
    @DisplayName("Deve valer zero sem sessão hoje nem ontem")
    void deveZerarSemHojeNemOntem() {
        LocalDate hoje = LocalDate.of(2026, 11, 12);
        Set<LocalDate> dias = new HashSet<>();
        dias.add(hoje.minusDays(2));

        assertEquals(0, StreakCalculator.calcular(dias, hoje));
    }

    @Test
    @DisplayName("Deve valer zero com conjunto vazio ou hoje nulo")
    void deveZerarComEntradasAusentes() {
        assertEquals(0, StreakCalculator.calcular(Set.of(), LocalDate.of(2026, 11, 12)));
        assertEquals(0, StreakCalculator.calcular(null, LocalDate.of(2026, 11, 12)));
        assertEquals(0, StreakCalculator.calcular(Set.of(LocalDate.of(2026, 11, 12)), null));
    }

    @Test
    @DisplayName("Borda 23h30 em America/Sao_Paulo deve contar o dia local corrente")
    void deveContarDiaLocalAs23h30EmSaoPaulo() {
        // 2026-11-13T02:30:00Z == 2026-11-12 23:30 em America/Sao_Paulo (UTC-3).
        Instant instante = Instant.parse("2026-11-13T02:30:00Z");
        TimezoneContext contexto = new TimezoneContext("America/Sao_Paulo", Clock.fixed(instante, ZoneOffset.UTC));

        LocalDate hojeLocal = contexto.hojeLocal();
        assertEquals(LocalDate.of(2026, 11, 12), hojeLocal,
                "23h30 em São Paulo ainda pertence ao dia 12, não ao dia 13 em UTC");

        Set<LocalDate> dias = new HashSet<>();
        dias.add(hojeLocal);
        dias.add(hojeLocal.minusDays(1));

        assertEquals(2, StreakCalculator.calcular(dias, hojeLocal));
        assertEquals(SAO_PAULO, contexto.zoneId());
    }

    @Test
    @DisplayName("Sessão registrada em 23h30 conta no dia local e mantém o streak")
    void deveManterStreakQuandoUltimaSessaoFoiAs23h30() {
        Instant instante = Instant.parse("2026-11-13T02:30:00Z");
        LocalDate hojeLocal = new TimezoneContext("America/Sao_Paulo", Clock.fixed(instante, ZoneOffset.UTC))
                .hojeLocal();

        // Sessões de hoje (23h30 local) e dos dois dias anteriores.
        Set<LocalDate> dias = new HashSet<>();
        dias.add(hojeLocal);
        dias.add(hojeLocal.minusDays(1));
        dias.add(hojeLocal.minusDays(2));

        assertEquals(3, StreakCalculator.calcular(dias, hojeLocal));
        assertEquals(3, StreakCalculator.calcular(dias, hojeLocal.plusDays(1)),
                "no dia seguinte (ainda sem sessão) o streak continua válido terminando ontem");
    }
}
