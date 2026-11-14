package br.com.trilhara.shared.mongo;

/**
 * Divergência entre um índice declarado e o estado do banco.
 *
 * @param colecao coleção afetada
 * @param indice nome canônico do índice
 * @param esperado descrição do índice declarado (chave, unique, TTL, filtro parcial)
 * @param encontrado descrição do que existe no banco
 */
public record ConflitoIndice(String colecao, String indice, String esperado, String encontrado) {

    @Override
    public String toString() {
        return colecao + "." + indice + " — esperado [" + esperado + "] encontrado [" + encontrado + "]";
    }
}
