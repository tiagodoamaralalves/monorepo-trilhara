package br.com.trilhara.nota.dto;

/**
 * Intervalo realçado dentro do {@code snippet} de busca (D-30).
 *
 * <p>Os offsets são índices de code unit UTF-16 do {@code snippet} devolvido no mesmo
 * resultado — exatamente o que {@code String.prototype.slice} consome no frontend. O backend
 * nunca envia HTML: só o intervalo.</p>
 *
 * @param inicio índice inicial inclusivo
 * @param fim    índice final exclusivo
 */
public record DestaqueDTO(int inicio, int fim) {
}
