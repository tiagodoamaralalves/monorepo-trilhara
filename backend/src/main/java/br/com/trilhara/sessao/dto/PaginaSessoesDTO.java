package br.com.trilhara.sessao.dto;

import java.util.List;

public record PaginaSessoesDTO(
        List<SessaoDTO> itens,
        int pagina,
        int tamanho,
        long totalItens,
        int totalPaginas
) {
}
