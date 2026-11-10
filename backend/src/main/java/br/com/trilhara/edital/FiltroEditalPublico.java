package br.com.trilhara.edital;

public record FiltroEditalPublico(
        String esfera,
        String uf,
        String banca,
        String area,
        String cargo,
        String busca,
        Boolean emAndamento,
        Paginacao paginacao) {
}
