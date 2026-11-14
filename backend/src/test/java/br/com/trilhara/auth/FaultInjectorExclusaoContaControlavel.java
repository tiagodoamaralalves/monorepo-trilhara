package br.com.trilhara.auth;

import java.util.EnumSet;
import java.util.Set;

import br.com.trilhara.shared.mongo.ColecaoTrilhara;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/**
 * Implementação controlável de {@link FaultInjectorExclusaoConta} disponível <strong>apenas</strong>
 * no classpath de teste. Substitui {@link FaultInjectorExclusaoContaNoOp} via
 * {@code @Alternative}.
 *
 * <p>Uso típico: {@code injector.falharNa(ColecaoTrilhara.NOTAS)} antes de disparar a limpeza; a
 * próxima execução lança {@link FalhaInjetada} exatamente nessa coleção, provando que o worker
 * retoma sem deixar órfãos.</p>
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class FaultInjectorExclusaoContaControlavel implements FaultInjectorExclusaoConta {

    /** Falha deliberada usada para exercitar os caminhos de retomada. */
    public static class FalhaInjetada extends RuntimeException {
        private final ColecaoTrilhara colecao;

        public FalhaInjetada(ColecaoTrilhara colecao) {
            super("Falha injetada na coleção " + colecao.nome() + ".");
            this.colecao = colecao;
        }

        public ColecaoTrilhara getColecao() {
            return colecao;
        }
    }

    private final Set<ColecaoTrilhara> colecoesComFalha = EnumSet.noneOf(ColecaoTrilhara.class);

    @Override
    public void antesDe(ColecaoTrilhara colecao) {
        if (colecoesComFalha.contains(colecao)) {
            throw new FalhaInjetada(colecao);
        }
    }

    /** Programa falha para uma coleção específica. */
    public void falharNa(ColecaoTrilhara colecao) {
        colecoesComFalha.add(colecao);
    }

    @Override
    public void limpar() {
        colecoesComFalha.clear();
    }
}
