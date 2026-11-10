package br.com.trilhara.cronograma.dto;

public record BlocoHojeDTO(
        String blocoId,
        String trilhaId,
        String cronogramaId,
        String disciplinaId,
        String disciplinaNome,
        String disciplinaCor,
        String trilhaNome,
        String data,
        int ordem,
        int duracaoMinutos,
        boolean concluido,
        String statusData) {

    public BlocoHojeDTO(
            String blocoId,
            String trilhaId,
            String cronogramaId,
            String disciplinaId,
            String disciplinaNome,
            String disciplinaCor,
            String trilhaNome,
            String data,
            int ordem,
            int duracaoMinutos,
            boolean concluido) {
        this(blocoId, trilhaId, cronogramaId, disciplinaId, disciplinaNome, disciplinaCor, trilhaNome, data, ordem, duracaoMinutos, concluido, "hoje");
    }

    public String id() {
        return blocoId;
    }
}
