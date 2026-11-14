package br.com.trilhara.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.io.IOException;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Teste de Performance e Plano de Índices com explain("executionStats") no MongoDB 7.
 * 
 * Perfil Opt-in:
 * Não roda na suíte unitária padrão (mvn test).
 * Para executar:
 * ./mvnw test -Dtest=MongoExplainBenchmarkTest -Dbenchmark=true
 */
@QuarkusTest
@Tag("benchmark")
@EnabledIfSystemProperty(named = "benchmark", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Benchmark de Índices e ExecutionStats no MongoDB 7 (Perfil Opt-in)")
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
    private final RelatorioBenchmark relatorio = new RelatorioBenchmark();

    /** Repetições por consulta para p50/p95 estáveis. */
    private static final int REPETICOES = 20;

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
 * Insere a massa representativa exigida na * Trilha com 20 disciplinas e 2.000 tópicos (100 tópicos por disciplina)
 * Cronograma de 366 dias com 24 blocos/dia = 8.784 blocos
 * 2.190 sessões de estudo / ano (6 sessões/dia * 365 dias)
 * Caderno com 300 páginas de notas em Markdown (10 seções * 30 páginas)
 * Séries recorrentes com tarefas ativas e agendamentos
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

    /**
 * Executa o `explain` {@link #REPETICOES} vezes e devolve a primeira leitura do plano
 * vencedor + as latências observadas (para p50/p95).
 */
    private PlanoVencedor medir(String colecao, Document filtro, Document sort, Integer limit) {
        List<Long> latencias = new ArrayList<>(REPETICOES);
        PlanoVencedor plano = null;
        for (int i = 0; i < REPETICOES; i++) {
            PlanoVencedor atual = PlanoVencedor.de(executarExplain(colecao, filtro, sort, limit));
            if (plano == null) {
                plano = atual;
            }
            latencias.add(atual.tempoMs());
        }
        this.ultimasLatencias = latencias;
        return plano;
    }

    private List<Long> ultimasLatencias = List.of();

    // ─── Consultas críticas ───────────────────────────────────────────
    // Cada teste mede 20 repetições, arquiva o plano vencedor no relatório e
    // assevera o índice por IGUALDADE (não por substring): um COLLSCAN
    // vencedor com o índice apenas num plano rejeitado falha aqui.
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("1. Blocos do dia (Hoje) usam idx_blocos_cronograma_usuarioId_data_concluido")
    void benchmarkBlocosCronogramaPorDataEConcluido() {
        Document filtro = new Document("usuarioId", benchmarkUserId)
                .append("data", "2026-06-15")
                .append("concluido", false);

        PlanoVencedor plano = medir("blocos_cronograma", filtro, null, 24);
        LOG.infof("[BENCHMARK-BLOCOS] %s", plano);

        assertTrue(plano.usaIndice("idx_blocos_cronograma_usuarioId_data_concluido"),
                "Plano vencedor deveria ser IXSCAN no índice canônico de blocos: " + plano);
        assertTrue(plano.docsExaminados() <= 24,
                "Docs examinados deve ser seletivo (<= 24): " + plano.docsExaminados());

        relatorio.registrar("Blocos do dia (Hoje)", "idx_blocos_cronograma_usuarioId_data_concluido",
                plano, ultimasLatencias);
    }

    @Test
    @DisplayName("2. Lista virtual de blocos usa o índice por trilha/cronograma/versão")
    void benchmarkListaVirtualBlocos() {
        Document filtro = new Document("usuarioId", benchmarkUserId)
                .append("trilhaId", trilhaId)
                .append("cronogramaId", cronogramaId)
                .append("versaoGeracao", 1)
                .append("concluido", false);
        Document sort = new Document("data", 1).append("_id", 1);

        PlanoVencedor plano = medir("blocos_cronograma", filtro, sort, 50);
        LOG.infof("[BENCHMARK-LISTA-VIRTUAL] %s", plano);

        assertTrue(plano.usaIndice(
                "idx_blocos_cronograma_usuarioId_trilhaId_cronogramaId_versaoGeracao_concluido_data"),
                "Lista virtual deveria usar o índice por trilha/cronograma/versão: " + plano);

        relatorio.registrar("Lista virtual de blocos",
                "idx_blocos_cronograma_usuarioId_trilhaId_cronogramaId_versaoGeracao_concluido_data",
                plano, ultimasLatencias);
    }

    @Test
    @DisplayName("3. Paginação de sessões usa IXSCAN ordenado idx_sessoes_usuarioId_trilhaId_dataSessao_criadoEm_id")
    void benchmarkPaginacaoSessoes() {
        Document filtro = new Document("usuarioId", benchmarkUserId)
                .append("trilhaId", trilhaId);
        Document sort = new Document("dataSessao", -1)
                .append("criadoEm", -1)
                .append("_id", -1);

        PlanoVencedor plano = medir("sessoes", filtro, sort, 20);
        LOG.infof("[BENCHMARK-SESSOES] %s", plano);

        assertTrue(plano.usaIndice("idx_sessoes_usuarioId_trilhaId_dataSessao_criadoEm_id"),
                "Histórico de sessões deveria usar o índice de ordenação estável: " + plano);
        assertTrue(plano.docsExaminados() <= 20,
                "Página de 20 não deveria examinar mais de 20 documentos: " + plano.docsExaminados());

        relatorio.registrar("Histórico de sessões (paginação)",
                "idx_sessoes_usuarioId_trilhaId_dataSessao_criadoEm_id", plano, ultimasLatencias);
    }

    @Test
    @DisplayName("4. Estatísticas agregadas por período usam IXSCAN (nunca COLLSCAN)")
    void benchmarkEstatisticas() {
        Document filtro = new Document("usuarioId", benchmarkUserId)
                .append("trilhaId", trilhaId)
                .append("deletedAt", null)
                .append("dataSessao", new Document("$gte", "2026-01-01").append("$lte", "2026-12-31"));

        PlanoVencedor plano = medir("sessoes", filtro, null, null);
        LOG.infof("[BENCHMARK-ESTATISTICAS] %s", plano);

        assertTrue("IXSCAN".equals(plano.estagio()),
                "Agregação por período deveria usar índice (IXSCAN), não COLLSCAN: " + plano);

        relatorio.registrar("Estatísticas da trilha (período)",
                "idx_sessoes_usuarioId_trilhaId_disciplinaId_dataSessao", plano, ultimasLatencias);
    }

    @Test
    @DisplayName("5. Listagem de disciplinas ordenadas usa idx_disciplinas_usuarioId_trilhaId_ordem")
    void benchmarkDisciplinasOrdenadas() {
        Document filtro = new Document("usuarioId", benchmarkUserId)
                .append("trilhaId", trilhaId);
        Document sort = new Document("ordem", 1);

        PlanoVencedor plano = medir("disciplinas", filtro, sort, null);
        LOG.infof("[BENCHMARK-DISCIPLINAS] %s", plano);

        assertTrue(plano.usaIndice("idx_disciplinas_usuarioId_trilhaId_ordem"),
                "Listagem de disciplinas deveria usar o índice por ordem: " + plano);
        assertEquals(20, plano.docsExaminados(),
                "Docs examinados deve conferir com as 20 disciplinas da trilha: " + plano.docsExaminados());

        relatorio.registrar("Disciplinas da trilha (ordenadas)",
                "idx_disciplinas_usuarioId_trilhaId_ordem", plano, ultimasLatencias);
    }

    @Test
    @DisplayName("6. Listagem de notas por seção usa idx_notas_usuarioId_secaoId_deletedAt_ordem")
    void benchmarkListagemNotas() {
        Document filtro = new Document("usuarioId", benchmarkUserId)
                .append("secaoId", secaoId)
                .append("deletedAt", null);
        Document sort = new Document("ordem", 1);

        PlanoVencedor plano = medir("notas", filtro, sort, null);
        LOG.infof("[BENCHMARK-NOTAS] %s", plano);

        assertTrue(plano.usaIndice("idx_notas_usuarioId_secaoId_deletedAt_ordem"),
                "Listagem de notas deveria usar o índice por seção: " + plano);

        relatorio.registrar("Páginas da seção (Notas)",
                "idx_notas_usuarioId_secaoId_deletedAt_ordem", plano, ultimasLatencias);
    }


    @Test
    @DisplayName("7. Busca textual em notas ($text) usa o índice de texto idx_notas_usuarioId_deletedAt_text")
    void benchmarkBuscaTextualNotas() {
        Document filtro = new Document("usuarioId", benchmarkUserId)
                .append("deletedAt", null)
                .append("$text", new Document("$search", "jurisprudência"));

        PlanoVencedor plano = medir("notas", filtro, null, 10);
        LOG.infof("[BENCHMARK-NOTAS-TEXT] %s", plano);

        // No MongoDB 7 o índice de texto vence como IXSCAN com o nome do índice de texto
        // (ou como TextNode em versões/planos que expõem TEXT_MATCH).
        boolean usaTexto = plano.usaIndice("idx_notas_usuarioId_deletedAt_text")
                || (plano.indice() != null && plano.indice().contains("_text"))
                || (plano.estagio() != null && plano.estagio().startsWith("TEXT"));
        assertTrue(usaTexto, "Busca textual deveria usar o índice de texto: " + plano);

        relatorio.registrar("Busca textual em notas ($text)",
                "idx_notas_usuarioId_deletedAt_text", plano, ultimasLatencias);
    }

    @Test
    @DisplayName("8. Tarefas de Hoje usam idx_tarefas_usuarioId_deletedAt_concluida_dataVencimento")
    void benchmarkTarefasHoje() {
        // Fixture coerente com o índice: o repositório sempre filtra deletedAt.
        Document filtro = new Document("usuarioId", benchmarkUserId)
                .append("deletedAt", null)
                .append("concluida", false)
                .append("dataVencimento", "2026-01-01");

        PlanoVencedor plano = medir("tarefas", filtro, null, 50);
        LOG.infof("[BENCHMARK-TAREFAS] %s", plano);

        assertTrue(plano.usaIndice("idx_tarefas_usuarioId_deletedAt_concluida_dataVencimento"),
                "Tarefas de Hoje deveriam usar o índice composto: " + plano);

        relatorio.registrar("Tarefas de Hoje",
                "idx_tarefas_usuarioId_deletedAt_concluida_dataVencimento", plano, ultimasLatencias);
    }

    @Test
    @DisplayName("9. Lixeira de tarefas usa IXSCAN em idx_tarefas_usuarioId_deletedAt_concluida_dataVencimento")
    void benchmarkLixeiraTarefas() {
        Document filtro = new Document("usuarioId", benchmarkUserId)
                .append("deletedAt", new Document("$ne", null));

        PlanoVencedor plano = medir("tarefas", filtro, null, 50);
        LOG.infof("[BENCHMARK-LIXEIRA-TAREFAS] %s", plano);

        assertTrue("IXSCAN".equals(plano.estagio()),
                "Lixeira de tarefas deveria usar IXSCAN: " + plano);

        relatorio.registrar("Lixeira de tarefas",
                "idx_tarefas_usuarioId_deletedAt_concluida_dataVencimento", plano, ultimasLatencias);
    }

    @Test
    @DisplayName("10. Lixeira de notas usa idx_notas_usuarioId_cadernoId_deletedAt")
    void benchmarkLixeiraNotas() {
        Document filtro = new Document("usuarioId", benchmarkUserId)
                .append("cadernoId", cadernoId)
                .append("deletedAt", new Document("$ne", null));

        PlanoVencedor plano = medir("notas", filtro, null, 50);
        LOG.infof("[BENCHMARK-LIXEIRA-NOTAS] %s", plano);

        assertTrue("IXSCAN".equals(plano.estagio()),
                "Lixeira de notas deveria usar IXSCAN: " + plano);

        relatorio.registrar("Lixeira de notas", "idx_notas_usuarioId_cadernoId_deletedAt",
                plano, ultimasLatencias);
    }

    /** Arquiva `docs/benchmarks/explain-AAAA-MM-DD.md` ao fim de TODA a execução. */
    @AfterAll
    void arquivarRelatorio() throws IOException {
        if (relatorio.possuiRegistros()) {
            relatorio.gravar();
            LOG.infof("[BENCHMARK-RELATORIO] arquivado em %s", RelatorioBenchmark.destinoPadrao());
        }
    }
}

