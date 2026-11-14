package br.com.trilhara.nota.dto;

/**
 * Resposta da restauração de um item da lixeira de Notas.
 *
 * <p>{@code realocadaPara} só vem preenchido quando o item não pôde voltar ao lugar original
 * porque algum ancestral já havia sido purgado após os 30 dias: nesse caso a página/seção é
 * movida para a seção/caderno "Restauradas" e a UI usa os IDs devolvidos para navegar até ela.</p>
 *
 * @param realocadaPara destino após a restauração ({@code null} quando o item voltou ao original)
 */
public record RestauracaoLixeiraResponse(
        String mensagem,
        String tipo,
        String id,
        String cadernoId,
        String secaoId,
        boolean ancestralRestaurado,
        RealocacaoDestinoDTO realocadaPara) {
}
