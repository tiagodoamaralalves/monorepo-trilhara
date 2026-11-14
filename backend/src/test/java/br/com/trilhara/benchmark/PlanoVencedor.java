package br.com.trilhara.benchmark;

import java.util.List;
import java.util.Locale;

import org.bson.Document;

/**
 * Plano vencedor de uma consulta extraído de `explain("executionStats")`.
 *
 * <p>Antes as assertivas procuravam `IXSCAN` e o nome do índice por
 * <em>substring</em> no JSON inteiro do `explain` — o que passa mesmo quando o estágio
 * vencedor é `COLLSCAN` e o índice aparece apenas num plano rejeitado. Aqui o
 * `queryPlanner.winningPlan` é percorrido recursivamente para obter o estágio e o
 * índice <strong>efetivamente vencedores</strong>.</p>
 */
final class PlanoVencedor {

    private final String estagio;
    private final String indice;
    private final long docsExaminados;
    private final long chavesExaminadas;
    private final long retornados;
    private final long tempoMs;

    private PlanoVencedor(String estagio, String indice, long docsExaminados,
                          long chavesExaminadas, long retornados, long tempoMs) {
        this.estagio = estagio;
        this.indice = indice;
        this.docsExaminados = docsExaminados;
        this.chavesExaminadas = chavesExaminadas;
        this.retornados = retornados;
        this.tempoMs = tempoMs;
    }

    static PlanoVencedor de(Document explain) {
        Document planner = explain.get("queryPlanner", Document.class);
        Document winning = planner == null ? null : planner.get("winningPlan", Document.class);
        Estagio encontrado = new Estagio();
        percorrer(winning, encontrado);

        Document stats = explain.get("executionStats", Document.class);
        return new PlanoVencedor(
                encontrado.estagio,
                encontrado.indice,
                numero(stats, "totalDocsExamined"),
                numero(stats, "totalKeysExamined"),
                numero(stats, "nReturned"),
                numero(stats, "executionTimeMillis"));
    }

    private static final class Estagio {
        private String estagio;
        private String indice;
    }

    /**
 * Desce pelo plano. `IXSCAN` é o estágio que interessa; estágios de junção
 * (`SORT`, `FETCH`, `LIMIT`, `PROJECTION_*`) são atravessados.
 */
    private static void percorrer(Document plano, Estagio alvo) {
        if (plano == null) {
            return;
        }
        String estagio = plano.getString("stage");
        if ("IXSCAN".equals(estagio) && alvo.indice == null) {
            alvo.estagio = estagio;
            alvo.indice = plano.getString("indexName");
        } else if (alvo.estagio == null) {
            alvo.estagio = estagio;
        }

        for (String chave : List.of("inputStage", "queryPlan")) {
            Object filho = plano.get(chave);
            if (filho instanceof Document doc) {
                percorrer(doc, alvo);
            }
        }
        if (plano.get("inputStages") instanceof List<?> lista) {
            for (Object item : lista) {
                if (item instanceof Document doc) {
                    percorrer(doc, alvo);
                }
            }
        }
    }

    private static long numero(Document stats, String campo) {
        if (stats == null) {
            return -1;
        }
        Number valor = stats.get(campo, Number.class);
        return valor == null ? -1 : valor.longValue();
    }

    String estagio() {
        return estagio;
    }

    String indice() {
        return indice;
    }

    long docsExaminados() {
        return docsExaminados;
    }

    long chavesExaminadas() {
        return chavesExaminadas;
    }

    long retornados() {
        return retornados;
    }

    long tempoMs() {
        return tempoMs;
    }

    boolean usaIndice(String nome) {
        return "IXSCAN".equals(estagio) && nome.equals(indice);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "stage=%s index=%s docs=%d keys=%d returned=%d timeMs=%d",
                estagio, indice, docsExaminados, chavesExaminadas, retornados, tempoMs);
    }
}
