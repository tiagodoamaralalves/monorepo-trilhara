package br.com.trilhara.cronograma.dto;

public record BlocoCronogramaItemDTO(
        String id,
        String trilhaId,
        String cronogramaId,
        long versaoGeracao,
        String data,
        int ordem,
        String disciplinaId,
        String disciplinaNome,
        String disciplinaCor,
        int duracaoMinutos,
        boolean concluido,
        String concluidoEm,
        String statusData,
        String criadoEm,
        String atualizadoEm) {
}
