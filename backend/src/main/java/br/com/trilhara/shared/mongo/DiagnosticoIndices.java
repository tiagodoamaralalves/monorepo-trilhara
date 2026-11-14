package br.com.trilhara.shared.mongo;

import java.util.List;

/**
 * Diagnóstico da reconciliação de índices.
 *
 * @param conflitos índices declarados cujo nome existe com chave/opções divergentes, cuja
 * mesma chave já existe sob outro nome ou que falharam ao ser criados
 * @param drift índices com prefixo {@code idx_}/{@code uk_} presentes no banco e não
 * declarados na tabela canônica (reportados, nunca removidos)
 */
public record DiagnosticoIndices(List<ConflitoIndice> conflitos, List<String> drift) {

    public DiagnosticoIndices {
        conflitos = conflitos == null ? List.of() : List.copyOf(conflitos);
        drift = drift == null ? List.of() : List.copyOf(drift);
    }

    public static DiagnosticoIndices naoExecutado() {
        return new DiagnosticoIndices(List.of(), List.of());
    }

    /** {@code true} quando todos os índices declarados existem e estão compatíveis. */
    public boolean saudavel() {
        return conflitos.isEmpty();
    }
}
