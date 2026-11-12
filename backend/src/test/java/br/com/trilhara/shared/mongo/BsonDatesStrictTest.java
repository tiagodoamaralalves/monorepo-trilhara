package br.com.trilhara.shared.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * R2 — {@link BsonDates} estrito (DC-17): tipos canônicos aceitos e tipo inesperado
 * rejeitado com {@link IllegalStateException} (dados legados não são convertidos).
 */
@DisplayName("R2 - BsonDates estrito (DC-17)")
class BsonDatesStrictTest {

    @Test
    @DisplayName("toBsonDate/toInstant: BSON Date nativo, null propaga")
    void deveConverterInstantEDate() {
        Instant instante = Instant.parse("2026-11-12T10:15:30Z");
        Date data = BsonDates.toBsonDate(instante);
        assertEquals(instante.toEpochMilli(), data.getTime());
        assertEquals(instante, BsonDates.toInstant(data));
        assertNull(BsonDates.toBsonDate(null));
        assertNull(BsonDates.toInstant((Date) null));
    }

    @Test
    @DisplayName("toIso/toLocalDate: string ISO yyyy-MM-dd sem deslocamento de fuso")
    void deveConverterLocalDate() {
        LocalDate hoje = LocalDate.of(2026, 11, 12);
        assertEquals("2026-11-12", BsonDates.toIso(hoje));
        assertEquals(hoje, BsonDates.toLocalDate("2026-11-12"));
        assertNull(BsonDates.toIso(null));
        assertNull(BsonDates.toLocalDate((String) null));
        assertThrows(IllegalArgumentException.class, () -> BsonDates.toLocalDate("12/11/2026"));
    }

    @Test
    @DisplayName("lerInstant(Document): aceita BSON Date e rejeita tipo inesperado")
    void lerInstantDeveSerEstrito() {
        Document valido = new Document("criadoEm", BsonDates.toBsonDate(Instant.parse("2026-11-12T00:00:00Z")));
        assertEquals(Instant.parse("2026-11-12T00:00:00Z"), BsonDates.lerInstant(valido, "criadoEm"));
        assertNull(BsonDates.lerInstant(new Document(), "criadoEm"));

        Document legado = new Document("criadoEm", "2026-11-12T00:00:00Z");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> BsonDates.lerInstant(legado, "criadoEm"));
        assertTrue(ex.getMessage().contains("criadoEm"), "mensagem deve citar o campo");
    }

    @Test
    @DisplayName("lerLocalDate(Document): aceita string ISO e rejeita tipo inesperado")
    void lerLocalDateDeveSerEstrito() {
        Document valido = new Document("dataVencimento", "2026-11-12");
        assertEquals(LocalDate.of(2026, 11, 12), BsonDates.lerLocalDate(valido, "dataVencimento"));
        assertNull(BsonDates.lerLocalDate(new Document(), "dataVencimento"));

        Document legado = new Document("dataVencimento", Date.from(Instant.parse("2026-11-12T00:00:00Z")));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> BsonDates.lerLocalDate(legado, "dataVencimento"));
        assertTrue(ex.getMessage().contains("dataVencimento"));
    }

    @Test
    @DisplayName("lerInstant(BsonValue): rejeita tipo inesperado com o tipo BSON no detalhe")
    void lerInstantDeBsonValueDeveSerEstrito() {
        var data = new org.bson.BsonDateTime(Instant.parse("2026-11-12T00:00:00Z").toEpochMilli());
        assertEquals(Instant.parse("2026-11-12T00:00:00Z"), BsonDates.lerInstant(data, "expiraEm"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> BsonDates.lerInstant(new org.bson.BsonString("x"), "expiraEm"));
        assertTrue(ex.getMessage().contains("STRING"));
    }
}
