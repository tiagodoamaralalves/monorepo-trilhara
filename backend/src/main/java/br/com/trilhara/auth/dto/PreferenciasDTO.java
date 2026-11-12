package br.com.trilhara.auth.dto;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Preferências do usuário expostas ao cliente (D-10).
 *
 * @param tema                          {@code dark}, {@code light} ou {@code system}
 * @param disponibilidadeSemanalMinutos 7 inteiros (Seg–Dom), 0 a 1440
 * @param padraoLembreteMinutosAntes    padrão global de lembrete de tarefas
 */
@Schema(name = "PreferenciasDTO", description = "Preferências persistidas do usuário")
public record PreferenciasDTO(
        @Schema(description = "Tema de interface", enumeration = { "dark", "light", "system" })
        String tema,

        @Schema(description = "Minutos disponíveis por dia da semana (Seg–Dom)")
        List<Integer> disponibilidadeSemanalMinutos,

        @Schema(description = "Minutos de antecedência padrão dos lembretes")
        Integer padraoLembreteMinutosAntes) {

    public static final String TEMA_PADRAO = "dark";
    public static final List<Integer> DISPONIBILIDADE_PADRAO = List.of(0, 0, 0, 0, 0, 0, 0);
    public static final int PADRAO_LEMBRETE_PADRAO = 15;
}
