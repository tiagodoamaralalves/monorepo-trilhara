package br.com.trilhara.sessao;

import java.time.LocalDate;
import java.util.Set;

/**
 * Cálculo de sequência de dias consecutivos de estudo (D-14).
 *
 * <p>Norma: {@code streakDias} é o número de dias de calendário consecutivos com ao menos uma
 * sessão, terminando em {@code hojeLocal} quando há sessão hoje; caso contrário, terminando em
 * {@code hojeLocal - 1}. Sem sessão em nenhum dos dois dias, o resultado é {@code 0}.</p>
 *
 * <p>É calculado sobre <strong>todas</strong> as sessões da trilha (independente do filtro de
 * período) e recebe o "hoje" já resolvido no fuso do usuário, para que a borda de 23h30 em
 * {@code America/Sao_Paulo} seja testável com relógio fixo.</p>
 */
public final class StreakCalculator {

    private StreakCalculator() {
    }

    /**
     * @param diasComEstudo dias de calendário que possuem ao menos uma sessão válida
     * @param hojeLocal     data de calendário de "hoje" no fuso do usuário; {@code null} zera o cálculo
     * @return dias consecutivos terminando em hoje ou ontem
     */
    public static int calcular(Set<LocalDate> diasComEstudo, LocalDate hojeLocal) {
        if (diasComEstudo == null || diasComEstudo.isEmpty() || hojeLocal == null) {
            return 0;
        }

        LocalDate cursor = hojeLocal;
        if (!diasComEstudo.contains(cursor)) {
            LocalDate ontem = hojeLocal.minusDays(1);
            if (!diasComEstudo.contains(ontem)) {
                return 0;
            }
            cursor = ontem;
        }

        int streakDias = 0;
        while (cursor != null && diasComEstudo.contains(cursor)) {
            streakDias++;
            cursor = cursor.minusDays(1);
        }
        return streakDias;
    }
}
