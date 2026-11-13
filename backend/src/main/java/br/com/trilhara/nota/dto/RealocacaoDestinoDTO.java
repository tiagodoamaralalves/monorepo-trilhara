package br.com.trilhara.nota.dto;

/**
 * Destino físico de um item restaurado da lixeira (D-34).
 *
 * @param cadernoId caderno onde o item passou a viver
 * @param secaoId   seção onde o item passou a viver ({@code null} para caderno)
 */
public record RealocacaoDestinoDTO(String cadernoId, String secaoId) {
}
