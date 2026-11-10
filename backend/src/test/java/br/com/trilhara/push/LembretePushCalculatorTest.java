package br.com.trilhara.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LembretePushCalculatorTest {

    @Test
    @DisplayName("Deve calcular instanteEntregaUtc corretamente a partir de LocalDate, LocalTime e ZoneId")
    void deveCalcularInstanteEntregaUtcCorretamente() {
        LocalDate data = LocalDate.of(2026, 6, 15);
        LocalTime horario = LocalTime.of(14, 30);
        ZoneId zoneId = ZoneId.of("America/Sao_Paulo"); // UTC-3

        // 15 minutos antes: 14:15 local -> 17:15 UTC
        Instant instante15 = LembretePushCalculator.calcularInstanteEntregaUtc(data, horario, zoneId, 15);
        assertEquals(Instant.parse("2026-06-15T17:15:00Z"), instante15);

        // No horário (0 minutos antes): 14:30 local -> 17:30 UTC
        Instant instante0 = LembretePushCalculator.calcularInstanteEntregaUtc(data, horario, zoneId, 0);
        assertEquals(Instant.parse("2026-06-15T17:30:00Z"), instante0);

        // 60 minutos antes: 13:30 local -> 16:30 UTC
        Instant instante60 = LembretePushCalculator.calcularInstanteEntregaUtc(data, horario, zoneId, 60);
        assertEquals(Instant.parse("2026-06-15T16:30:00Z"), instante60);
    }

    @Test
    @DisplayName("Horário em gap DST (inexistente) deve lançar InvalidLocalDatetimeException com HTTP 400")
    void horarioEmGapDstDeveLancarExcecao() {
        // Em America/New_York no dia 08/03/2026 às 02:00 os relógios avançam para 03:00 (02:30 não existe)
        LocalDate data = LocalDate.of(2026, 3, 8);
        LocalTime horario = LocalTime.of(2, 30);
        ZoneId zoneId = ZoneId.of("America/New_York");

        InvalidLocalDatetimeException ex = assertThrows(
                InvalidLocalDatetimeException.class,
                () -> LembretePushCalculator.calcularInstanteEntregaUtc(data, horario, zoneId, 15)
        );

        assertNotNull(ex.getResponse());
        assertEquals(400, ex.getResponse().getStatus());
        assertTrue(ex.getMessage().contains("gap DST") || ex.getMessage().contains("não existe"));
    }

    @Test
    @DisplayName("Horário em overlap DST (duplicado) deve escolher deterministicamente a ocorrência anterior (horário de verão)")
    void horarioEmOverlapDstDeveEscolherOcorrenciaAnterior() {
        // Em America/New_York no dia 01/11/2026 às 02:00 os relógios voltam para 01:00 (01:30 ocorre duas vezes: EDT UTC-4 e EST UTC-5)
        LocalDate data = LocalDate.of(2026, 11, 1);
        LocalTime horario = LocalTime.of(1, 30);
        ZoneId zoneId = ZoneId.of("America/New_York");

        // Overlap com offset anterior (EDT UTC-4): 01:30 EDT = 05:30 UTC. 15 min antes = 05:15 UTC.
        // Se escolhesse offset posterior (EST UTC-5): 01:30 EST = 06:30 UTC. 15 min antes = 06:15 UTC.
        Instant instanteCalculado = LembretePushCalculator.calcularInstanteEntregaUtc(data, horario, zoneId, 15);
        assertEquals(Instant.parse("2026-11-01T05:15:00Z"), instanteCalculado);
    }
}
