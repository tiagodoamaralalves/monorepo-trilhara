package br.com.trilhara.cronograma.dto;

import java.util.List;

public record PaginaBlocosDTO(
        List<BlocoCronogramaItemDTO> itens,
        int pagina,
        int tamanho,
        long totalItens,
        int totalPaginas) {
}
