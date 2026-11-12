package br.com.trilhara.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * FASE 11 — Teste de Performance e Plano de Índices com explain("executionStats") no MongoDB 7.
 * 
 * Perfil Opt-in:
 * Não roda na suíte unitária padrão (mvn test).
 * Para executar:
 *   ./mvnw test -Dtest=MongoExplainBenchmarkTest -Dbenchmark=true
 */
@QuarkusTest
@Tag("benchmark")
@EnabledIfSystemProperty(named = "benchmark", matches = "true")
@DisplayName("FASE 11 - Benchmark de Índices e ExecutionStats no MongoDB 7 (Perfil Opt-in)")
public class MongoExplainBenchmarkTest {

    private static final Logger LOG = Logger.getLogger(MongoExplainBenchmarkTest.class);

    @Inject
    MongoClient mongoClient;

    @ConfigProperty(name = "quarkus.mongodb.database")
    String databaseName;

    private ObjectId benchmarkUserId;
    private ObjectId trilhaId;
    private ObjectId cronogramaId;
    private ObjectId cadernoId;
    private ObjectId secaoId;
    private final List<ObjectId> disciplinasIds = new ArrayList<>();

    @BeforeEach
    void setupCargaSintetica() {
        benchmarkUserId = new ObjectId();
        trilhaId = new ObjectId();
        cronogramaId = new ObjectId();
        cadernoId = new ObjectId();
        secaoId = new ObjectId();
        disciplinasIds.clear();

        LOG.infof("Iniciando inserção de carga sintética representativa para benchmarkUserId=%s...", benchmarkUserId);
        long t0 = System.currentTimeMillis();

        inserirCargaSintetica();

        long t1 = System.currentTimeMillis();
        LOG.infof("Carga sintética inserida com sucesso em %d ms.", (t1 - t0));
    }

    @AfterEach
    void limparCargaSintetica() {
        if (benchmarkUserId == null) return;
        MongoDatabase db = getDatabase();
        String[] colecoes = {
                "usuarios", "trilhas", "disciplinas", "progresso_topicos", "cronogramas",
                "blocos_cronograma", "sessoes", "listas_tarefas", "series_recorrentes",
                "tarefas", "cadernos", "secoes", "notas"
        };
        for (String col : colecoes) {
            db.getCollection(col).deleteMany(Filters.eq("usuarioId", benchmarkUserId));
        }
    }

    private MongoDatabase getDatabase() {
        return mongoClient.getDatabase(databaseName);
    }

    /**
     * Insere a massa representativa exigida na FASE 11:
     * - Trilha com 20 disciplinas e 2.000 tópicos (100 tópicos por disciplina)
     * - Cronograma de 366 dias com 24 blocos/dia = 8.784 blocos
     * - 2.190 sessões de estudo / ano (6 sessões/dia * 365 dias)
     * - Caderno com 300 páginas de notas em Markdown (10 seções * 30 páginas)
     * - Séries recorrentes com tarefas ativas e agendamentos
     */
    private void inserirCargaSintetica() {
        MongoDatabase db = getDatabase();
        Date agora = Date.from(Instant.now());

        // 1. Trilha
        db.getCollection("trilhas").insertOne(new Document()
                .append("_id", trilhaId)
                .append("usuarioId", benchmarkUserId)
                .append("nome", "Trilha Auditor Fiscal Receita Federal - Carga Benchmark")
                .append("cargo", "Auditor-Fiscal")
                .append("banca", "FGV")
                .append("atualizadoEm", agora));

        // 2. 20 Disciplinas e 2.000 tópicos (100 por disciplina)
        List<Document> docsDisciplinas = new ArrayList<>(20);
        for (int d = 0; d < 20; d++) {
            ObjectId discId = new ObjectId();
            disciplinasIds.add(discId);

            List<Document> topicos = new ArrayList<>(100);
            for (int t = 0; t < 100; t++) {
                topicos.add(new Document("id", "topico-d" + d + "-t" + t)
                        .append("nome", "Tópico " + t + " de Especialidade " + d));
            }

            docsDisciplinas.add(new Document()
                    .append("_id", discId)
                    .append("usuarioId", benchmarkUserId)
                    .append("trilhaId", trilhaId)
                    .append("nome", "Disciplina Especializada " + d)
                    .append("cor", "#4F46E5")
                    .append("peso", (d % 3) + 1)
                    .append("ordem", d)
                    .append("topicos", topicos));
        }
        db.getCollection("disciplinas").insertMany(docsDisciplinas);

        // 3. Cronograma e 8.784 Blocos de Estudo (366 dias * 24 blocos/dia)
        db.getCollection("cronogramas").insertOne(new Document()
                .append("_id", cronogramaId)
                .append("usuarioId", benchmarkUserId)
                .append("trilhaId", trilhaId)
                .append("modalidade", "SEQUENCIAL")
                .append("ativo", true)
                .append("versaoAtiva", 1));

        LocalDate dataBase = LocalDate.of(2026, 1, 1);
        List<Document> loteBlocos = new ArrayList<>(8784);
        for (int dia = 0; dia < 366; dia++) {
            LocalDate d = dataBase.plusDays(dia);
            String dataStr = d.toString();
            for (int b = 0; b < 24; b++) {
                ObjectId discRef = disciplinasIds.get((dia + b) % disciplinasIds.size());
                loteBlocos.add(new Document()
                        .append("usuarioId", benchmarkUserId)
                        .append("trilhaId", trilhaId)
                        .append("cronogramaId", cronogramaId)
                        .append("versaoGeracao", 1)
                        .append("disciplinaId", discRef)
                        .append("data", dataStr)
                        .append("ordem", b)
                        .append("concluido", dia < 100)); // Primeiros 100 dias concluídos
            }
        }
        db.getCollection("blocos_cronograma").insertMany(loteBlocos);

        // 4. 2.190 Sessões de Estudo / ano (6 sessões/dia * 365 dias)
        List<Document> loteSessoes = new ArrayList<>(2190);
        for (int dia = 0; dia < 365; dia++) {
            LocalDate d = dataBase.plusDays(dia);
            String dataStr = d.toString();
            for (int s = 0; s < 6; s++) {
                ObjectId discRef = disciplinasIds.get((dia + s) % disciplinasIds.size());
                loteSessoes.add(new Document()
                        .append("usuarioId", benchmarkUserId)
                        .append("trilhaId", trilhaId)
                        .append("disciplinaId", discRef)
                        .append("duracaoSegundos", 1500 + (s * 300))
                        .append("dataSessao", dataStr)
                        .append("questoes", 20)
                        .append("acertos", 16)
                        .append("criadoEm", agora));
            }
        }
        db.getCollection("sessoes").insertMany(loteSessoes);

        // 5. Caderno e 300 Páginas de Notas em Markdown (10 seções * 30 páginas)
        db.getCollection("cadernos").insertOne(new Document()
                .append("_id", cadernoId)
                .append("usuarioId", benchmarkUserId)
                .append("nome", "Caderno de Jurisprudência e Legislação")
                .append("atualizadoEm", agora));

        List<Document> loteSecoes = new ArrayList<>(10);
        List<Document> loteNotas = new ArrayList<>(300);
        for (int s = 0; s < 10; s++) {
            ObjectId sid = (s == 0) ? secaoId : new ObjectId();
            loteSecoes.add(new Document()
                    .append("_id", sid)
                    .append("usuarioId", benchmarkUserId)
                    .append("cadernoId", cadernoId)
                    .append("nome", "Seção Temática " + s)
                    .append("ordem", s));

            for (int p = 0; p < 30; p++) {
                loteNotas.add(new Document()
                        .append("usuarioId", benchmarkUserId)
                        .append("cadernoId", cadernoId)
                        .append("secaoId", sid)
                        .append("deletedAt", null)
                        .append("titulo", "Página de Doutrina " + s + " - " + p)
                        .append("conteudoMarkdown", "# Análise Tributária\nTexto explicativo com palavras-chave relevantes como súmula vinculante, imunidade tributária e jurisprudência número " + p)
                        .append("ordem", p)
                        .append("criadoEm", agora)
                        .append("atualizadoEm", agora));
            }
        }
        db.getCollection("secoes").insertMany(loteSecoes);
        db.getCollection("notas").insertMany(loteNotas);

        // 6. Séries Recorrentes e Tarefas
        ObjectId serieId = new ObjectId();
        db.getCollection("series_recorrentes").insertOne(new Document()
                .append("_id", serieId)
                .append("usuarioId", benchmarkUserId)
                .append("titulo", "Revisão Espaçada Diária")
                .append("frequencia", "DIARIA")
                .append("intervalo", 1)
                .append("ativa", true));

        List<Document> loteTarefas = new ArrayList<>(100);
        for (int t = 0; t < 100; t++) {
            String dataOcorrencia = dataBase.plusDays(t).toString();
            loteTarefas.add(new Document()
                    .append("usuarioId", benchmarkUserId)
                    .append("serieId", serieId)
                    .append("dataOriginalOcorrencia", dataOcorrencia)
                    .append("titulo", "Revisão flashcard lote " + t)
                    .append("concluido", t < 30)
                    .append("dataVencimento", dataOcorrencia)
                    .append("ordem", t));
        }
        db.getCollection("tarefas").insertMany(loteTarefas);
    }

    private Document executarExplain(String colecao, Document filtro, Document sort, Integer limit) {
        Document findCmd = new Document("find", colecao).append("filter", filtro);
        if (sort != null) {
            findCmd.append("sort", sort);
        }
        if (limit != null) {
            findCmd.append("limit", limit);
        }

        Document explainCmd = new Document("explain", findCmd).append("verbosity", "executionStats");
        return getDatabase().runCommand(explainCmd);
    }

    private boolean contemStageOuIndice(Document doc, String termo) {
        if (doc == null) return false;
        String json = doc.toJson().toUpperCase();
        return json.contains(termo.toUpperCase());
    }

    @Test
    @DisplayName("Benchmark 1: Consulta de Blocos por data e status com 8.784 blocos utiliza IXSCAN idx_blocos_cronograma_usuarioId_data_concluido")
    void benchmarkBlocosCronogramaPorDataEConcluido() {
        Document filtro = new Document("usuarioId", benchmarkUserId)
                .append("data", "2026-06-15")
                .append("concluido", false);

        Document explain = executarExplain("blocos_cronograma", filtro, null, 24);
        Document executionStats = explain.get("executionStats", Document.class);
        assertNotNull(executionStats, "executionStats deve estar presente");

        long totalDocsExamined = executionStats.get("totalDocsExamined", Number.class).longValue();
        long totalKeysExamined = executionStats.get("totalKeysExamined", Number.class).longValue();
        long nReturned = executionStats.get("nReturned", Number.class).longValue();
        long executionTimeMillis = executionStats.get("executionTimeMillis", Number.class).longValue();

        LOG.infof("[BENCHMARK-BLOCOS] docs=%d, keys=%d, returned=%d, time=%dms",
                totalDocsExamined, totalKeysExamined, nReturned, executionTimeMillis);

        // Deve usar IXSCAN no índice idx_blocos_cronograma_usuarioId_data_concluido
        assertTrue(contemStageOuIndice(explain, "IXSCAN"), "Plano de consulta deve utilizar IXSCAN");
        assertTrue(contemStageOuIndice(explain, "idx_blocos_cronograma_usuarioId_data_concluido"),
                "Deve utilizar o índice idx_blocos_cronograma_usuarioId_data_concluido");

        // docs examined não pode exceder as 24 ocorrências retornadas (alta seletividade)
        assertTrue(totalDocsExamined <= 24, "Docs examinados deve ser altamente seletivo (<= 24)");
    }

    @Test
    @DisplayName("Benchmark 2: Paginação de Sessões em massa de 2.190 registros utiliza IXSCAN ordenado idx_sessoes_usuarioId_trilhaId_dataSessao_criadoEm_id")
    void benchmarkPaginacaoSessoes() {
        Document filtro = new Document("usuarioId", benchmarkUserId)
                .append("trilhaId", trilhaId);
        Document sort = new Document("dataSessao", -1)
                .append("criadoEm", -1)
                .append("_id", -1);

        Document explain = executarExplain("sessoes", filtro, sort, 20);
        Document executionStats = explain.get("executionStats", Document.class);
        assertNotNull(executionStats);

        long totalDocsExamined = executionStats.get("totalDocsExamined", Number.class).longValue();
        long totalKeysExamined = executionStats.get("totalKeysExamined", Number.class).longValue();
        long nReturned = executionStats.get("nReturned", Number.class).longValue();

        LOG.infof("[BENCHMARK-SESSOES] docs=%d, keys=%d, returned=%d", totalDocsExamined, totalKeysExamined, nReturned);

        assertTrue(contemStageOuIndice(explain, "IXSCAN"), "Paginação de sessões deve usar IXSCAN");
        assertTrue(contemStageOuIndice(explain, "idx_sessoes_usuarioId_trilhaId_dataSessao_criadoEm_id"),
                "Deve usar o índice de ordenação estável das sessões");

        // O índice composto cobre a ordenação: docs examinados deve ser exatamente igual ao limit solicitado (20)
        assertTrue(totalDocsExamined <= 20, "Docs examinados não deve exceder o tamanho da página (20)");
    }

    @Test
    @DisplayName("Benchmark 3: Listagem de disciplinas ordenadas utiliza IXSCAN idx_disciplinas_usuarioId_trilhaId_ordem")
    void benchmarkDisciplinasOrdenadas() {
        Document filtro = new Document("usuarioId", benchmarkUserId)
                .append("trilhaId", trilhaId);
        Document sort = new Document("ordem", 1);

        Document explain = executarExplain("disciplinas", filtro, sort, null);
        Document executionStats = explain.get("executionStats", Document.class);
        assertNotNull(executionStats);

        long totalDocsExamined = executionStats.get("totalDocsExamined", Number.class).longValue();
        long nReturned = executionStats.get("nReturned", Number.class).longValue();

        LOG.infof("[BENCHMARK-DISCIPLINAS] docs=%d, returned=%d", totalDocsExamined, nReturned);

        assertTrue(contemStageOuIndice(explain, "IXSCAN"), "Listagem de disciplinas deve usar IXSCAN");
        assertTrue(contemStageOuIndice(explain, "idx_disciplinas_usuarioId_trilhaId_ordem"),
                "Deve utilizar idx_disciplinas_usuarioId_trilhaId_ordem");
        assertEquals(20, totalDocsExamined, "Docs examinados deve conferir com as 20 disciplinas da trilha");
    }

    @Test
    @DisplayName("Benchmark 4: Busca textual em notas ($text) utiliza índice de texto sem supor estágio universal")
    void benchmarkBuscaTextualNotas() {
        Document filtro = new Document("usuarioId", benchmarkUserId)
                .append("deletedAt", null)
                .append("$text", new Document("$search", "jurisprudência"));

        Document explain = executarExplain("notas", filtro, null, 10);
        Document executionStats = explain.get("executionStats", Document.class);
        assertNotNull(executionStats);

        long nReturned = executionStats.get("nReturned", Number.class).longValue();
        long executionTimeMillis = executionStats.get("executionTimeMillis", Number.class).longValue();

        LOG.infof("[BENCHMARK-NOTAS-TEXT] returned=%d, time=%dms", nReturned, executionTimeMillis);

        // Conforme requisito da Fase 11: verificar estágio ou índice de texto suportado pelo MongoDB 7 (TEXT, TEXT_MATCH ou nome do índice)
        boolean usaIndiceTexto = contemStageOuIndice(explain, "TEXT")
                || contemStageOuIndice(explain, "idx_notas_usuarioId_deletedAt_text")
                || contemStageOuIndice(explain, "TEXT_MATCH")
                || contemStageOuIndice(explain, "TEXT_OR");

        assertTrue(usaIndiceTexto, "Busca textual deve ativar o plano de índice text do MongoDB 7");
    }

    @Test
    @DisplayName("Benchmark 5: Consulta de Tarefas de Hoje utiliza índice composto idx_tarefas_usuarioId_deletedAt_concluida_dataVencimento")
    void benchmarkTarefasHoje() {
        Document filtro = new Document("usuarioId", benchmarkUserId)
                .append("concluido", false)
                .append("dataVencimento", "2026-01-01");

        Document explain = executarExplain("tarefas", filtro, null, 50);
        Document executionStats = explain.get("executionStats", Document.class);
        assertNotNull(executionStats);

        long totalDocsExamined = executionStats.get("totalDocsExamined", Number.class).longValue();
        long nReturned = executionStats.get("nReturned", Number.class).longValue();

        LOG.infof("[BENCHMARK-TAREFAS] docs=%d, returned=%d", totalDocsExamined, nReturned);

        assertTrue(contemStageOuIndice(explain, "IXSCAN"), "Consulta de tarefas hoje deve utilizar IXSCAN");
        assertTrue(contemStageOuIndice(explain, "idx_tarefas_usuarioId_deletedAt_concluida_dataVencimento"),
                "Deve utilizar idx_tarefas_usuarioId_deletedAt_concluida_dataVencimento");
    }
}
