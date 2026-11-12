package br.com.trilhara.shared.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * R2 — tabela canônica de índices (D-07).
 *
 * <p>Valida nome, chaves e <strong>todas</strong> as opções (unique, TTL, filtro parcial,
 * idioma/pesos de texto), a idempotência, o reporte de drift e a detecção de conflito de
 * opções (que deve deixar o readiness {@code DOWN}).</p>
 */
@QuarkusTest
@DisplayName("R2 - Índices canônicos das 20 coleções (D-07) e readiness")
class MongoIndexInitializerTest {

    private static final String BANCO_TESTE = "trilhara_r2_indexes_test";

    @Inject
    MongoClient mongoClient;

    @Inject
    MongoIndexInitializer indexInitializer;

    private MongoDatabase testDb;

    @BeforeEach
    void setUp() {
        testDb = mongoClient.getDatabase(BANCO_TESTE);
        testDb.drop();
    }

    @AfterEach
    void tearDown() {
        if (testDb != null) {
            testDb.drop();
        }
    }

    @Test
    @DisplayName("deveCriarExatamenteIndicesCanonicos: nome, chaves e opções de cada índice")
    void deveCriarExatamenteIndicesCanonicos() {
        DiagnosticoIndices diagnostico = indexInitializer.inicializarIndices(testDb);
        assertTrue(diagnostico.saudavel(), "Sem conflitos após criar em banco vazio: " + diagnostico.conflitos());

        for (IndicesCanonicos.IndiceCanonico esperado : IndicesCanonicos.todos()) {
            Document encontrado = buscarIndice(testDb, esperado.colecao().nome(), esperado.nome());
            assertTrue(encontrado != null,
                    "Índice ausente: " + esperado.colecao().nome() + "." + esperado.nome());

            Document chaves = encontrado.get("key", Document.class);
            if (esperado.idioma() == null) {
                assertEquals(
                        normalizar(esperado.chaves().toBsonDocument(org.bson.BsonDocument.class,
                                com.mongodb.MongoClientSettings.getDefaultCodecRegistry()).toJson()),
                        normalizar(chaves.toJson()),
                        "Chaves divergentes em " + esperado.nome());
            } else {
                // Índice de texto: MongoDB guarda {_fts, _ftsx} e os campos em weights.
                assertEquals(1, ((Number) chaves.get("usuarioId")).intValue(), esperado.nome());
                assertEquals(1, ((Number) chaves.get("deletedAt")).intValue(), esperado.nome());
                assertEquals("text", chaves.getString("_fts"), esperado.nome());
                Document pesos = encontrado.get("weights", Document.class);
                assertEquals(10, ((Number) pesos.get("titulo")).intValue(), esperado.nome());
                assertEquals(1, ((Number) pesos.get("conteudoMarkdown")).intValue(), esperado.nome());
            }

            boolean esperadoUnico = esperado.unico();
            boolean encontradoUnico = Boolean.TRUE.equals(encontrado.getBoolean("unique", Boolean.FALSE));
            assertEquals(esperadoUnico, encontradoUnico, "unique divergente em " + esperado.nome());

            Integer ttlEsperado = esperado.ttlSegundos();
            Object ttlBruto = encontrado.get("expireAfterSeconds");
            Long ttlEncontrado = ttlBruto instanceof Number numero ? numero.longValue() : null;
            if (ttlEsperado == null) {
                assertTrue(ttlEncontrado == null, "TTL inesperado em " + esperado.nome());
            } else {
                assertEquals(ttlEsperado.longValue(), ttlEncontrado, "TTL divergente em " + esperado.nome());
            }

            Document filtroEsperado = esperado.filtroParcial() == null ? null
                    : Document.parse(esperado.filtroParcial().toBsonDocument(org.bson.BsonDocument.class,
                            com.mongodb.MongoClientSettings.getDefaultCodecRegistry()).toJson());
            Document filtroEncontrado = encontrado.get("partialFilterExpression", Document.class);
            assertEquals(normalizar(filtroEsperado), normalizar(filtroEncontrado),
                    "partialFilterExpression divergente em " + esperado.nome());

            if (esperado.idioma() != null) {
                assertEquals(esperado.idioma(), encontrado.getString("default_language"),
                        "default_language divergente em " + esperado.nome());
            }
        }
    }

    @Test
    @DisplayName("deveSerIdempotente: segunda execução não cria duplicidade nem conflito")
    void deveSerIdempotente() {
        DiagnosticoIndices primeira = indexInitializer.inicializarIndices(testDb);
        assertTrue(primeira.saudavel());

        DiagnosticoIndices segunda = indexInitializer.inicializarIndices(testDb);
        assertTrue(segunda.saudavel(), "Segunda execução deve permanecer saudável: " + segunda.conflitos());
        assertTrue(segunda.drift().isEmpty(), "Sem drift em execução idempotente: " + segunda.drift());

        for (IndicesCanonicos.IndiceCanonico indice : IndicesCanonicos.todos()) {
            List<String> nomes = new ArrayList<>();
            for (Document doc : testDb.getCollection(indice.colecao().nome()).listIndexes()) {
                nomes.add(doc.getString("name"));
            }
            long ocorrencias = nomes.stream().filter(indice.nome()::equals).count();
            assertEquals(1, ocorrencias, "Índice duplicado: " + indice.nome());
        }
    }

    @Test
    @DisplayName("deveFicarDownComConflitoDeOpcoes: índice prévio com TTL diferente derruba o readiness")
    void deveFicarDownComConflitoDeOpcoes() {
        // Cria previamente o índice de expiração de refresh_tokens SEM TTL (opção divergente).
        testDb.getCollection(ColecaoTrilhara.REFRESH_TOKENS.nome())
                .createIndex(Indexes.ascending("expiraEm"),
                        new IndexOptions().name("idx_refresh_tokens_expiraEm"));

        DiagnosticoIndices diagnostico = indexInitializer.inicializarIndices(testDb);
        assertFalse(diagnostico.saudavel(), "Conflito de TTL deve ser reportado");

        boolean conflitoReportado = diagnostico.conflitos().stream()
                .anyMatch(c -> "idx_refresh_tokens_expiraEm".equals(c.indice())
                        && c.encontrado().contains("expireAfterSeconds"));
        assertTrue(conflitoReportado, "Diagnóstico deve apontar o TTL divergente: " + diagnostico.conflitos());
    }

    @Test
    @DisplayName("deveReportarDriftSemRemover: índice idx_ não declarado vira drift e permanece")
    void deveReportarDriftSemRemover() {
        indexInitializer.inicializarIndices(testDb);
        testDb.getCollection(ColecaoTrilhara.USUARIOS.nome())
                .createIndex(Indexes.ascending("nome"), new IndexOptions().name("idx_usuarios_nome_legado"));

        DiagnosticoIndices diagnostico = indexInitializer.inicializarIndices(testDb);
        assertTrue(diagnostico.drift().contains("usuarios.idx_usuarios_nome_legado"),
                "Drift deve listar o índice não declarado: " + diagnostico.drift());
        assertTrue(buscarIndice(testDb, ColecaoTrilhara.USUARIOS.nome(), "idx_usuarios_nome_legado") != null,
                "Índice em drift nunca é removido automaticamente");
    }

    @Test
    @DisplayName("inventario: 20 coleções no enum e todas com pelo menos um índice declarado")
    void deveCobrirInventarioCompleto() {
        assertEquals(20, ColecaoTrilhara.values().length, "20 coleções = 19 privadas + editais");
        assertEquals(18, ColecaoTrilhara.filhasPrivadas().size(), "18 coleções filhas privadas");
        assertFalse(ColecaoTrilhara.EDITAIS.privada(), "editais é catálogo público");
        assertTrue(ColecaoTrilhara.USUARIOS.privada());

        Set<String> colecoesComIndice = new HashSet<>();
        IndicesCanonicos.todos().forEach(i -> colecoesComIndice.add(i.colecao().nome()));
        for (String nome : ColecaoTrilhara.nomes()) {
            assertTrue(colecoesComIndice.contains(nome), "Coleção sem índice declarado: " + nome);
        }
        assertEquals(IndicesCanonicos.todos().size(), IndicesCanonicos.nomesDeclarados().stream().distinct().count(),
                "Nomes canônicos de índice devem ser únicos");
    }

    private static Document buscarIndice(MongoDatabase db, String colecao, String nome) {
        for (Document doc : db.getCollection(colecao).listIndexes()) {
            if (nome.equals(doc.getString("name"))) {
                return doc;
            }
        }
        return null;
    }

    private static String normalizar(Document doc) {
        return doc == null ? "null" : doc.toJson();
    }

    private static String normalizar(String json) {
        return json == null ? "null" : Document.parse(json).toJson();
    }
}
