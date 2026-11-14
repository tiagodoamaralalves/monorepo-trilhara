package br.com.trilhara.auth;

import br.com.trilhara.shared.mongo.ColecaoTrilhara;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Implementação de produção do {@link FaultInjectorExclusaoConta}: no-op absoluto.
 *
 * <p>Não é {@code @Alternative}: é o bean padrão. O teste substitui por uma
 * {@code @Alternative @Priority} declarada apenas em {@code src/test}, de modo que nenhuma
 * classe de produção conhece o mecanismo de falha.</p>
 */
@ApplicationScoped
public class FaultInjectorExclusaoContaNoOp implements FaultInjectorExclusaoConta {

    @Override
    public void antesDe(ColecaoTrilhara colecao) {
        // Produção nunca injeta falhas.
    }

    @Override
    public void limpar() {
        // Nada a limpar.
    }
}
