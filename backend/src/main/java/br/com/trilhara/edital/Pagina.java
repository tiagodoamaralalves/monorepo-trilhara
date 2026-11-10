package br.com.trilhara.edital;

import java.util.List;

public record Pagina<T>(List<T> itens, int page, int size, long total) {
    public Pagina {
        itens = List.copyOf(itens);
    }
}
