package br.com.trilhara.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Coletor do relatório markdown do benchmark (R12, META-04).
 *
 * <p>Archivado em `docs/benchmarks/explain-AAAA-MM-DD.md`. A raiz é resolvida como no
 * {@code RotasInventarioTest}: `user.dir` (backend/) com fallback para
 * {@code TRILHARA_RAIZ}.</p>
 */
final class RelatorioBenchmark {

    private final List<String> linhas = new ArrayList<>();
    private final LocalDate data = LocalDate.now();

    /** `true` quando ao menos uma consulta foi registrada (evita arquivo vazio). */
    boolean possuiRegistros() {
        return !linhas.isEmpty();
    }

    void registrar(String consulta, String indiceEsperado, PlanoVencedor plano, List<Long> latencias) {
        linhas.add(String.format(Locale.ROOT,
                "| %s | %s | %s | %s | %d | %d | %d | %s | %s |",
                consulta,
                plano.indice() == null ? "—" : plano.indice(),
                indiceEsperado,
                plano.estagio() == null ? "—" : plano.estagio(),
                plano.chavesExaminadas(),
                plano.docsExaminados(),
                plano.retornados(),
                percentil(latencias, 50),
                percentil(latencias, 95)));
    }

    private static String percentil(List<Long> valores, int p) {
        if (valores == null || valores.isEmpty()) {
            return "—";
        }
        List<Long> ordenados = new ArrayList<>(valores);
        ordenados.sort(Long::compareTo);
        int posicao = (int) Math.ceil((p / 100.0) * ordenados.size()) - 1;
        return ordenados.get(Math.max(0, Math.min(posicao, ordenados.size() - 1))) + " ms";
    }

    void gravar() throws IOException {
        Path destino = destinoPadrao();
        StringBuilder sb = new StringBuilder();
        sb.append("# Benchmark `explain(\"executionStats\")` — MongoDB 7\n\n");
        sb.append("> Gerado por `MongoExplainBenchmarkTest` em ").append(data).append(".\n");
        sb.append("> Execução opt-in: `./mvnw test -Dtest=MongoExplainBenchmarkTest -Dbenchmark=true`.\n\n");
        sb.append("| Consulta | Índice vencedor | Índice esperado | Estágio | keysExamined | docsExamined ");
        sb.append("| nReturned | p50 | p95 |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|\n");
        for (String linha : linhas) {
            sb.append(linha).append('\n');
        }
        sb.append("\n## Carga sintética\n\n");
        sb.append("- 2.190 sessões/ano; trilha com 20 disciplinas e 2.000 tópicos;\n");
        sb.append("  cronograma de 366 dias × 24 blocos = 8.784 blocos; caderno com 300 páginas.\n");
        sb.append("- Latências p50/p95 obtidas com 20 repetições por consulta.\n");
        sb.append("\n## Leitura\n\n");
        sb.append("- `keysExamined == docsExamined == nReturned`: cobertura completa pelo índice.\n");
        sb.append("- `docsExamined >> nReturned`: exame além do necessário (índice pouco seletivo).\n");
        sb.append("- `COLLSCAN` no estágio vencedor: consulta sem índice aplicável.\n");

        Files.createDirectories(destino.getParent());
        Files.writeString(destino, sb.toString(), StandardCharsets.UTF_8);
    }

    static Path destinoPadrao() {
        Path candidato = Paths.get(System.getProperty("user.dir"), "..", "docs", "benchmarks");
        String raiz = System.getenv("TRILHARA_RAIZ");
        if (raiz != null && !raiz.isBlank()) {
            candidato = Paths.get(raiz, "docs", "benchmarks");
        }
        return candidato.resolve("explain-" + LocalDate.now() + ".md");
    }
}
