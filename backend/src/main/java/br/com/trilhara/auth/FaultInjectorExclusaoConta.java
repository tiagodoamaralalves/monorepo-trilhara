package br.com.trilhara.auth;

import br.com.trilhara.shared.mongo.ColecaoTrilhara;

/**
 * Ponto único de injeção de falhas na limpeza da exclusão de conta.
 *
 * <p>A produção usa {@link FaultInjectorExclusaoContaNoOp}. O classpath de teste substitui o
 * bean por uma implementação controlável, de modo que <strong>não existe hook estático nem
 * setter público</strong> no código de produção — o mesmo padrão já adotado em
 * {@code br.com.trilhara.cronograma.FaultInjector}.</p>
 */
public interface FaultInjectorExclusaoConta {

    /**
 * Chamado imediatamente antes de cada coleção ser purgada; uma implementação de teste pode
 * lançar aqui para exercitar a retomada do worker.
 *
 * @param colecao coleção prestes a ser processada
 */
    void antesDe(ColecaoTrilhara colecao);

    /** Nenhuma falha injetada em nenhuma coleção. */
    void limpar();
}
