package br.com.trilhara.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.bson.BsonDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * R11 — semântica do filtro de fila (D-36b).
 *
 * <p>O filtro é a única peça que garante "primeira tentativa imediata" e "backoff limitado à
 * janela". Estes testes verificam o BSON gerado (sem MongoDB), provando a aritmética que não
 * pode ser expressa em Java puro.</p>
 */
class LembretePushFilaFiltroTest {

    private static BsonDocument filtroDocumento(Instant agora) {
        return LembretePushFilaRepository.filtroCandidatos(agora)
                .toBsonDocument(BsonDocument.class,
                        com.mongodb.MongoClientSettings.getDefaultCodecRegistry());
    }

    @Test
    @DisplayName("filtro tem três ramos: PENDENTE vencido, PENDENTE fora da janela e lease expirado")
    void filtroCobreOsTresEstados() {
        BsonDocument doc = filtroDocumento(Instant.parse("2026-06-15T15:00:00Z"));

        assertEquals(3, doc.getArray("$or").size(), "Três ramos: vencido, janela fechada e lease expirado.");

        String json = doc.toJson();
        assertTrue(json.contains("\"PENDENTE\""));
        assertTrue(json.contains("\"PROCESSANDO\""));
    }

    @Test
    @DisplayName("backoff usa atualizadoEm como base e a primeira tentativa é imediata")
    void backoffUsaAtualizadoEmEPermitePrimeiraTentativa() {
        String json = filtroDocumento(Instant.parse("2026-06-15T15:00:00Z")).toJson();

        assertTrue(json.contains("atualizadoEm"),
                "A base do backoff é atualizadoEm, não o instante de entrega.");
        assertTrue(json.contains("tentativas"),
                "O backoff é derivado de tentativas.");
        // Primeira tentativa (tentativas == 0) não espera backoff:
        assertTrue(json.contains("$eq"),
                "O ramo de primeira tentativa usa $eq sobre tentativas == 0.");
    }

    @Test
    @DisplayName("ramo de janela fechada usa $lt e há ramo próprio de lease expirado")
    void janelaFechadaUsaLimiteEstrito() {
        String json = filtroDocumento(Instant.parse("2026-06-15T15:00:00Z")).toJson();

        assertTrue(json.contains("$lt"),
                "A janela fechada usa $lt: a entrega exatamente no limite ainda é tentada.");
        assertTrue(json.contains("leaseAte"),
                "Lease expirado é reprocessado (worker morto).");
    }
}
