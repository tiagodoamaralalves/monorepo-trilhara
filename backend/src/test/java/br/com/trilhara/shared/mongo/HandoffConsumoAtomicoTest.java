package br.com.trilhara.shared.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * R2 — handoff SSO na coleção privada {@code tokens_handoff_sso} (D-08) com consumo
 * atômico de uso único.
 */
@QuarkusTest
@DisplayName("R2 - Handoff SSO: tokens_handoff_sso e consumo atômico")
class HandoffConsumoAtomicoTest {

    private static final String BANCO_TESTE = "trilhara_r2_handoff_test";
    private static final String COLECAO = ColecaoTrilhara.TOKENS_HANDOFF_SSO.nome();

    @Inject
    MongoClient mongoClient;

    @Inject
    MongoIndexInitializer indexInitializer;

    private MongoDatabase testDb;

    @BeforeEach
    void setUp() {
        testDb = mongoClient.getDatabase(BANCO_TESTE);
        testDb.drop();
        indexInitializer.inicializarIndices(testDb);
    }

    @AfterEach
    void tearDown() {
        if (testDb != null) {
            testDb.drop();
        }
    }

    @Test
    @DisplayName("tokens_handoff_sso: codigoHash único, usuarioId e TTL imediato em expiraEm")
    void deveTerIndicesCanonicos() {
        Document ttl = null;
        List<String> nomes = new ArrayList<>();
        for (Document doc : testDb.getCollection(COLECAO).listIndexes()) {
            nomes.add(doc.getString("name"));
            if ("idx_tokens_handoff_sso_expiraEm".equals(doc.getString("name"))) {
                ttl = doc;
            }
        }

        assertTrue(nomes.contains("uk_tokens_handoff_sso_codigoHash"));
        assertTrue(nomes.contains("idx_tokens_handoff_sso_usuarioId"));
        assertNotNull(ttl, "Índice TTL de expiraEm deve existir");
        assertEquals(0L, ((Number) ttl.get("expireAfterSeconds")).longValue(),
                "TTL deve ser imediato sobre expiraEm");
    }

    @Test
    @DisplayName("consumo atômico: dois consumos paralelos produzem exatamente um sucesso")
    void doisConsumosParalelosProduzemUmSucesso() throws Exception {
        String codigoHash = "hash-" + new ObjectId().toHexString();
        inserirCodigo(codigoHash, new ObjectId(), Instant.now().plusSeconds(60));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        List<Callable<Boolean>> tarefas = List.of(
                () -> {
                    largada.await();
                    return consumirAtomico(codigoHash);
                },
                () -> {
                    largada.await();
                    return consumirAtomico(codigoHash);
                });

        try {
            List<Future<Boolean>> futuros = new ArrayList<>();
            tarefas.forEach(t -> futuros.add(executor.submit(t)));
            largada.countDown();

            int sucessos = 0;
            for (Future<Boolean> futuro : futuros) {
                if (futuro.get()) {
                    sucessos++;
                }
            }
            assertEquals(1, sucessos, "Exatamente um consumo deve vencer a corrida");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("código expirado não é consumido")
    void codigoExpiradoNaoEhConsumido() {
        String codigoHash = "hash-expirado-" + new ObjectId().toHexString();
        inserirCodigo(codigoHash, new ObjectId(), Instant.now().minusSeconds(5));
        assertTrue(!consumirAtomico(codigoHash), "Código expirado não pode ser consumido");
    }

    @Test
    @DisplayName("código já consumido não é reutilizado")
    void codigoJaConsumidoNaoEhReutilizado() {
        String codigoHash = "hash-unico-" + new ObjectId().toHexString();
        inserirCodigo(codigoHash, new ObjectId(), Instant.now().plusSeconds(60));
        assertTrue(consumirAtomico(codigoHash));
        assertTrue(!consumirAtomico(codigoHash), "Replay deve ser rejeitado");
    }

    private void inserirCodigo(String codigoHash, ObjectId usuarioId, Instant expiraEm) {
        testDb.getCollection(COLECAO).insertOne(new Document("_id", new ObjectId())
                .append("usuarioId", usuarioId)
                .append("codigoHash", codigoHash)
                .append("origemDestino", "http://localhost:5174/")
                .append("criadoEm", BsonDates.toBsonDate(Instant.now()))
                .append("expiraEm", BsonDates.toBsonDate(expiraEm))
                .append("consumidoEm", null));
    }

    /** Espelha o filtro de HandoffService.consumirHandoff (findOneAndUpdate atômico). */
    private boolean consumirAtomico(String codigoHash) {
        Instant agora = Instant.now();
        Document doc = testDb.getCollection(COLECAO).findOneAndUpdate(
                Filters.and(
                        Filters.eq("codigoHash", codigoHash),
                        Filters.eq("consumidoEm", null),
                        Filters.gt("expiraEm", BsonDates.toBsonDate(agora))),
                Updates.set("consumidoEm", BsonDates.toBsonDate(agora)));
        return doc != null;
    }
}
