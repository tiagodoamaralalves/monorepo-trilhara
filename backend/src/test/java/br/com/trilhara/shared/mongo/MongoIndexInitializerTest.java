package br.com.trilhara.shared.mongo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

@QuarkusTest
@DisplayName("FASE 1 - Inicializador Centralizado e Idempotente das 19 Coleções do MongoDB")
class MongoIndexInitializerTest {

    private static final String BANCO_TESTE = "trilhara_fase1_indexes_test";

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
    @DisplayName("Deve criar idempotentemente todos os índices das 19 coleções em banco vazio")
    void deveCriarIndicesDas19ColecoesEmBancoVazio() {
        // Primeira execução: cria tudo em banco vazio
        assertDoesNotThrow(() -> indexInitializer.inicializarIndices(testDb));

        // Tabela esperada de coleções e seus índices mínimos
        Map<String, List<String>> indicesEsperadosPorColecao = Map.ofEntries(
                Map.entry("usuarios", List.of("idx_usuarios_email_unique", "idx_usuarios_criadoEm_desc")),
                Map.entry("refresh_tokens", List.of("idx_refresh_tokens_tokenHash_unique", "idx_refresh_tokens_usuario_familia", "idx_refresh_tokens_expiraEm")),
                Map.entry("tokens_recuperacao_senha", List.of("idx_tokens_recuperacao_tokenHash_unique", "idx_tokens_recuperacao_usuario", "idx_tokens_recuperacao_expiraEm")),
                Map.entry("tokens_confirmacao_email", List.of("idx_tokens_confirmacao_tokenHash_unique", "idx_tokens_confirmacao_usuario", "idx_tokens_confirmacao_expiraEm")),
                Map.entry("editais", List.of("idx_editais_status_esfera_atualizadoEm")),
                Map.entry("trilhas", List.of("idx_trilhas_usuario_atualizadoEm", "idx_trilhas_usuario_origemEdital", "idx_trilhas_usuario_cronogramaAtivo")),
                Map.entry("disciplinas", List.of("idx_disciplinas_usuario_trilha_ordem")),
                Map.entry("progresso_topicos", List.of("idx_progresso_topicos_unico")),
                Map.entry("cronogramas", List.of("idx_cronogramas_usuario_trilha_modalidade_unique", "idx_cronogramas_usuario_trilha_ativo_unique")),
                Map.entry("blocos_cronograma", List.of("idx_blocos_usuario_trilha_cronograma_versao_data_ordem_unique", "idx_blocos_usuario_data_concluido")),
                Map.entry("sessoes", List.of("idx_sessoes_usuario_trilha_data_criado_id", "idx_sessoes_usuario_trilha_disciplina_data", "idx_sessoes_usuario_chaveIdempotencia_unique")),
                Map.entry("listas_tarefas", List.of("idx_listas_tarefas_usuario_deletedAt_ordem")),
                Map.entry("series_recorrentes", List.of("idx_series_recorrentes_usuario_deletedAt_ativa")),
                Map.entry("tarefas", List.of("idx_tarefas_usuario_deleted_lista_concluida_ordem", "idx_tarefas_usuario_deleted_concluida_vencimento", "idx_tarefas_usuario_serie_dataOriginal_unique")),
                Map.entry("cadernos", List.of("idx_cadernos_usuario_deletedAt_atualizadoEm", "idx_cadernos_usuario_origemTrilha_unique")),
                Map.entry("secoes", List.of("idx_secoes_usuario_caderno_deletedAt_ordem", "idx_secoes_usuario_caderno_origemDisciplina")),
                Map.entry("notas", List.of("idx_notas_usuario_secao_deletedAt_ordem", "idx_notas_usuario_caderno_deletedAt", "idx_notas_usuario_deletedAt_text")),
                Map.entry("push_subscriptions", List.of("idx_push_subscriptions_usuario", "idx_push_subscriptions_endpoint_unique")),
                Map.entry("lembretes_push", List.of("idx_lembretes_push_estado_instanteEntregaUtc", "idx_lembretes_push_usuario_tarefa"))
        );

        assertEquals(19, indicesEsperadosPorColecao.size(), "O sistema deve inicializar exatamente as 19 coleções especificadas");

        for (Map.Entry<String, List<String>> entry : indicesEsperadosPorColecao.entrySet()) {
            String colecao = entry.getKey();
            List<String> esperados = entry.getValue();

            Set<String> nomesExistentes = new HashSet<>();
            for (Document docIndice : testDb.getCollection(colecao).listIndexes()) {
                nomesExistentes.add(docIndice.getString("name"));
            }

            for (String esperado : esperados) {
                assertTrue(nomesExistentes.contains(esperado),
                        String.format("Índice esperado '%s' ausente na coleção '%s'. Encontrados: %s", esperado, colecao, nomesExistentes));
            }
        }

        // Segunda execução: deve ser 100% idempotente sem gerar erros nem duplicidade
        assertDoesNotThrow(() -> indexInitializer.inicializarIndices(testDb));
    }

    @Test
    @DisplayName("Deve detectar conflito e lançar exceção se índice existente tiver chave diferente com mesmo nome")
    void deveDetectarConflitoDeChaveComMesmoNome() {
        // Cria previamente um índice com nome de destino mas chave diferente
        testDb.getCollection("usuarios").createIndex(
                Indexes.ascending("nome"),
                new IndexOptions().name("idx_usuarios_email_unique")
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> indexInitializer.inicializarIndices(testDb));

        assertTrue(ex.getMessage().contains("Conflito de chave") || ex.getMessage().contains("idx_usuarios_email_unique"),
                "Deveria reportar conflito com diagnóstico claro");
    }

    @Test
    @DisplayName("Deve detectar conflito e lançar exceção se chave já indexada sob nome legado diferente")
    void deveDetectarConflitoDeMesmaChaveComNomeDiferente() {
        // Simula índice legado criado sem nome customizado pelo MongoDB
        testDb.getCollection("usuarios").createIndex(
                Indexes.ascending("email"),
                new IndexOptions().name("email_1").unique(true)
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> indexInitializer.inicializarIndices(testDb));

        assertTrue(ex.getMessage().contains("Conflito de índice detectado") || ex.getMessage().contains("email_1"),
                "Deveria reportar índice conflitante com mesmo conjunto de chaves");
    }

    @Test
    @DisplayName("Readiness Health Check deve reportar UP quando índices inicializados")
    void deveReportarUpNoHealthCheck() {
        assertTrue(indexInitializer.isInicializadoSucesso());
    }
}
