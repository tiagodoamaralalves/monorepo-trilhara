package br.com.trilhara.shared.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;

import org.bson.Document;
import org.bson.types.ObjectId;
import br.com.trilhara.shared.erro.InvalidIdentifierException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


@DisplayName("Utilitários BSON e Round-Trip de Tipos (ObjectId, Instant e LocalDate)")
class BsonRoundTripTest {

    @Test
    @DisplayName("BsonIds: deve converter string hexadecimal válida para ObjectId")
    void deveConverterHexValidoParaObjectId() {
        String hex = "654321654321654321654321";
        ObjectId id = BsonIds.parse(hex, "usuarioId");
        assertNotNull(id);
        assertEquals(hex, id.toHexString());
        assertEquals(hex, BsonIds.toHex(id));
        assertTrue(BsonIds.isValid(hex));
    }

    @Test
    @DisplayName("BsonIds.parse: deve lançar InvalidIdentifierException (400) para id malformado ou nulo")
    void deveLancarInvalidIdentifierParaIdInvalido() {
        InvalidIdentifierException exNulo = assertThrows(
                InvalidIdentifierException.class,
                () -> BsonIds.parse(null, "trilhaId"));
        assertEquals("trilhaId", exNulo.getCampo());

        InvalidIdentifierException exInvalido = assertThrows(
                InvalidIdentifierException.class,
                () -> BsonIds.parse("id-curto", "disciplinaId"));
        assertEquals("disciplinaId", exInvalido.getCampo());

        assertFalse(BsonIds.isValid("invalido"));
        assertFalse(BsonIds.isValid(null));
    }

    @Test
    @DisplayName("BsonIds.parse: deve aceitar hexadecimal válido de 24 caracteres")
    void deveAceitarHexValido() {
        ObjectId valido = new ObjectId();
        assertEquals(valido, BsonIds.parse(valido.toHexString(), "id"));
    }

    @Test
    @DisplayName("BsonIds: novoIdHex deve retornar representação hexadecimal de 24 caracteres")
    void deveGerarNovoIdHexValido() {
        String novo = BsonIds.novoIdHex();
        assertNotNull(novo);
        assertEquals(24, novo.length());
        assertTrue(ObjectId.isValid(novo));
    }

    @Test
    @DisplayName("BsonDates: Instant ↔ java.util.Date (BSON Date nativo)")
    void deveFazerRoundTripDeInstantComoBsonDate() {
        Instant agora = Instant.parse("2026-11-09T14:30:00Z");
        Date bsonDate = BsonDates.toBsonDate(agora);
        assertNotNull(bsonDate);
        assertEquals(agora.toEpochMilli(), bsonDate.getTime());

        Instant recuperado = BsonDates.toInstant(bsonDate);
        assertEquals(agora, recuperado);

        assertNull(BsonDates.toBsonDate(null));
        assertNull(BsonDates.toInstant((Date) null));

        Document doc = new Document("criadoEm", bsonDate);
        assertEquals(agora, BsonDates.lerInstant(doc, "criadoEm"));
        assertNull(BsonDates.lerInstant(doc, "campoInexistente"));
    }

    @Test
    @DisplayName("BsonDates: LocalDate ↔ String ISO 'yyyy-MM-dd' sem deslocamento de fuso")
    void deveFazerRoundTripDeLocalDateComoStringIso() {
        LocalDate hoje = LocalDate.of(2026, 11, 9);
        String iso = BsonDates.toIso(hoje);
        assertEquals("2026-11-09", iso);

        LocalDate recuperado = BsonDates.toLocalDate(iso);
        assertEquals(hoje, recuperado);

        assertNull(BsonDates.toIso(null));
        assertNull(BsonDates.toLocalDate((String) null));

        Document doc = new Document("dataVencimento", "2026-11-09");
        assertEquals(hoje, BsonDates.lerLocalDate(doc, "dataVencimento"));
        assertNull(BsonDates.lerLocalDate(doc, "campoInexistente"));

        assertThrows(IllegalArgumentException.class, () -> BsonDates.toLocalDate("data-invalida"));
    }

    @Test
    @DisplayName("Mapeamento direto em Document BSON sem serialização JSON intermediária")
    void deveMapearDocumentoBsonDiretamente() {
        ObjectId docId = new ObjectId();
        Instant agora = Instant.parse("2026-11-09T15:00:00Z");
        LocalDate dataEvento = LocalDate.of(2026, 12, 1);

        Document doc = new Document("_id", docId)
                .append("usuarioId", new ObjectId("654321654321654321654321"))
                .append("criadoEm", BsonDates.toBsonDate(agora))
                .append("dataEvento", BsonDates.toIso(dataEvento))
                .append("concluido", true);

        assertEquals(docId, doc.getObjectId("_id"));
        assertEquals("654321654321654321654321", doc.getObjectId("usuarioId").toHexString());
        assertEquals(agora, BsonDates.lerInstant(doc, "criadoEm"));
        assertEquals(dataEvento, BsonDates.lerLocalDate(doc, "dataEvento"));
        assertTrue(doc.getBoolean("concluido"));
    }
}
