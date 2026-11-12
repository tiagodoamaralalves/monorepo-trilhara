package br.com.trilhara.cronograma;

/**
 * Ponto único de injeção de falhas nas etapas da publicação versionada (D-18c).
 *
 * <p>A produção usa {@link FaultInjectorNoOp}. O classpath de teste substitui o bean por uma
 * implementação controlável, de modo que <strong>não existe hook estático</strong> no código de
 * produção.</p>
 */
public interface FaultInjector {

    /**
     * Chamado imediatamente antes de cada etapa; uma implementação de teste pode lançar aqui.
     *
     * @param etapa etapa prestes a executar
     */
    void antesDe(EtapaPublicacao etapa);

    /** Nenhuma falha injetada em nenhuma etapa. */
    void limpar();
}
