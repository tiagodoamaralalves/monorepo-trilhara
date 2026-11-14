package br.com.trilhara.cronograma;

/**
 * Etapas numeradas da publicação versionada de um cronograma.
 *
 * <p>Não existe transação distribuída: entre cada etapa o leitor vê a geração anterior ou a nova
 * completa. {@link FaultInjector} derruba a publicação em uma etapa específica para provar que
 * nenhum estado intermediário é publicado e que o reconciliador converge.</p>
 */
public enum EtapaPublicacao {

    /** 1. Documento do cronograma gravado em {@code PREPARANDO} com a versão preparada. */
    STAGING_DOCUMENTO(1),

    /** 2. Blocos da nova versão inseridos em lotes idempotentes. */
    GERACAO_BLOCOS(2),

    /** 3. Contagem de blocos em staging confere com o esperado. */
    VERIFICACAO_CONTAGEM(3),

    /** 4. Ponteiro da trilha ({@code cronogramaAtivoId} + {@code versaoAtivaCronograma}) trocado. */
    TROCA_PONTEIRO(4),

    /** 5. Flags {@code ativo} reconciliadas (no máximo um ativo por trilha). */
    RECONCILIACAO_FLAGS(5),

    /** 6. Versões antigas de blocos e configuração preparada removidas. */
    LIMPEZA_VERSOES_ANTERIORES(6);

    private final int numero;

    EtapaPublicacao(int numero) {
        this.numero = numero;
    }

    public int numero() {
        return numero;
    }

    /** {@code true} quando a falha acontece depois do ponteiro: exige recuperação explícita. */
    public boolean exigeRecuperacao() {
        return numero >= TROCA_PONTEIRO.numero;
    }
}
