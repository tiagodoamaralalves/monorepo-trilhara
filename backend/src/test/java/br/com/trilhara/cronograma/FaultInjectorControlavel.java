package br.com.trilhara.cronograma;

import java.util.EnumSet;
import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/**
 * Implementação controlável de {@link FaultInjector} disponível <strong>apenas</strong> no
 * classpath de teste. Substitui {@link FaultInjectorNoOp} via {@code @Alternative}.
 *
 * <p>Uso típico: {@code injector.falharNa(EtapaPublicacao.TROCA_PONTEIRO)} antes de disparar a
 * requisição; a próxima publicação lança {@link FalhaInjetada} exatamente nessa etapa.</p>
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class FaultInjectorControlavel implements FaultInjector {

    /** Falha deliberada usada para exercitar os caminhos de recuperação. */
    public static class FalhaInjetada extends RuntimeException {
        private final EtapaPublicacao etapa;

        public FalhaInjetada(EtapaPublicacao etapa) {
            super("Falha injetada na etapa " + etapa.numero() + " (" + etapa.name() + ").");
            this.etapa = etapa;
        }

        public EtapaPublicacao getEtapa() {
            return etapa;
        }
    }

    private final Set<EtapaPublicacao> etapasComFalha = EnumSet.noneOf(EtapaPublicacao.class);

    /** Quantas vezes cada etapa já foi atingida (para provar idempotência do replays). */
    @Override
    public void antesDe(EtapaPublicacao etapa) {
        if (etapasComFalha.contains(etapa)) {
            throw new FalhaInjetada(etapa);
        }
    }

    public void falharNa(EtapaPublicacao etapa) {
        etapasComFalha.add(etapa);
    }

    @Override
    public void limpar() {
        etapasComFalha.clear();
    }
}
