package br.com.trilhara.sessao.dto;

import java.time.Instant;

public record SessaoDTO(
        String id,
        String trilhaId,
        String disciplinaId,
        String disciplinaNome,
        String disciplinaCor,
        boolean disciplinaRemovida,
        String dataSessao,
        int duracaoSegundos,
        int questoes,
        int acertos,
        int erros,
        double percentualAcerto,
        boolean editavel,
        String chaveIdempotencia,
        Instant criadoEm,
        Instant atualizadoEm
) {
}
