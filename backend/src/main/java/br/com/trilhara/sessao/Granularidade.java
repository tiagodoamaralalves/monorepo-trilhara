package br.com.trilhara.sessao;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * Granularidade da série temporal das estatísticas (RI-09).
 *
 * <p>Regra de escolha, derivada apenas do intervalo efetivamente consultado:</p>
 * <ul>
 *   <li>período {@code tudo} (sem início/fim) → {@link #MES};</li>
 *   <li>até 31 dias inclusive → {@link #DIA};</li>
 *   <li>até 180 dias inclusive → {@link #SEMANA} (semana ISO, chave = segunda-feira);</li>
 *   <li>acima de 180 dias → {@link #MES} (chave = {@code yyyy-MM-01}).</li>
 * </ul>
 *
 * <p>A troca de granularidade muda apenas o agrupamento da série; os totais globais e por
 * disciplina são sempre calculados sobre as mesmas sessões.</p>
 */
public enum Granularidade {

    DIA,
    SEMANA,
    MES;

    /** Limite superior (em dias inclusivos) do agrupamento diário. */
    public static final int LIMITE_DIA_DIAS = 31;

    /** Limite superior (em dias inclusivos) do agrupamento semanal. */
    public static final int LIMITE_SEMANA_DIAS = 180;

    /**
     * Resolve a granularidade do intervalo.
     *
     * @param dataInicio início do período; {@code null} junto com {@code dataFim} significa "tudo"
     * @param dataFim    fim do período
     */
    public static Granularidade para(LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio == null || dataFim == null) {
            return MES;
        }
        long dias = ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
        if (dias <= LIMITE_DIA_DIAS) {
            return DIA;
        }
        if (dias <= LIMITE_SEMANA_DIAS) {
            return SEMANA;
        }
        return MES;
    }

    /**
     * Chave do bucket à qual a data de calendário pertence.
     *
     * @return {@code yyyy-MM-dd} em {@link #DIA}, segunda-feira ISO em {@link #SEMANA}
     *         e {@code yyyy-MM-01} em {@link #MES}
     */
    public String chave(LocalDate data) {
        return switch (this) {
            case DIA -> data.toString();
            case SEMANA -> data.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toString();
            case MES -> String.format("%04d-%02d-01", data.getYear(), data.getMonthValue());
        };
    }
}
