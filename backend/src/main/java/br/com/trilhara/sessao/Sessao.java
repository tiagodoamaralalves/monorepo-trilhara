package br.com.trilhara.sessao;

import java.time.Instant;
import java.time.LocalDate;

import org.bson.types.ObjectId;

/**
 * Entidade normalizada de Sessão de Estudo.
 * 
 * Fonte de verdade única para tempo de estudo (duracaoSegundos) e questões/acertos.
 * Conforme REQUISITOS-REESTRUTURACAO-DADOS.md e PLANO-IMPLEMENTACAO-REESTRUTURACAO.md.
 */
public record Sessao(
        ObjectId id,
        ObjectId usuarioId,
        ObjectId trilhaId,
        ObjectId disciplinaId,
        String trilhaNomeSnapshot,
        String disciplinaNomeSnapshot,
        String disciplinaCorSnapshot,
        LocalDate dataSessao,
        int duracaoSegundos,
        int questoes,
        int acertos,
        String chaveIdempotencia,
        String requestHash,
        Instant deletedAt,
        Instant criadoEm,
        Instant atualizadoEm
) {

    public int erros() {
        return Math.max(0, questoes - acertos);
    }

    public double percentualAcerto() {
        return questoes > 0 ? (acertos * 100.0 / questoes) : 0.0;
    }
}
