package br.com.trilhara.cronograma;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Implementação de produção do {@link FaultInjector}: no-op absoluto (D-18c).
 *
 * <p>Não é {@code @Alternative}: é o bean padrão. O teste substitui por uma
 * {@code @Alternative @Priority} declarada apenas em {@code src/test}, de modo que nenhuma classe
 * de produção conhece o mecanismo de falha.</p>
 */
@ApplicationScoped
public class FaultInjectorNoOp implements FaultInjector {

    @Override
    public void antesDe(EtapaPublicacao etapa) {
        // Produção nunca injeta falhas.
    }

    @Override
    public void limpar() {
        // Nada a limpar.
    }
}
