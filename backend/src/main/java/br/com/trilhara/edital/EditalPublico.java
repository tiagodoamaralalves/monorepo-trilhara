package br.com.trilhara.edital;

import java.time.LocalDate;
import java.util.List;

public record EditalPublico(
        String id,
        String nome,
        String area,
        String cargo,
        String orgao,
        String banca,
        Esfera esfera,
        String uf,
        LocalDate inicioInscricao,
        LocalDate fimInscricao,
        LocalDate dataProva,
        List<Edital.Disciplina> disciplinas) {

    static EditalPublico de(Edital edital) {
        return new EditalPublico(
                edital.id(), edital.nome(), edital.area(), edital.cargo(), edital.orgao(), edital.banca(),
                edital.esfera(), edital.uf(), edital.inicioInscricao(), edital.fimInscricao(),
                edital.dataProva(), edital.disciplinas());
    }
}
