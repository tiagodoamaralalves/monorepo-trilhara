package br.com.trilhara.tarefa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Regras de calendário do motor de recorrência ( §4).
 *
 * <p>Cobre os casos que o guia exige: virada de ano, semana ISO 53, mês curto e bissexto,
 * ordinal inexistente, ordinal negativo, contagem de {@code APOS_OCORRENCIAS} e a
 * inclusividade de {@code EM_DATA}.</p>
 */
class RecorrenciaEngineTest {

    private final RecorrenciaEngine engine = new RecorrenciaEngine(null, null);

    @ParameterizedTest(name = "DIARIO intervalo {0} de {1} até {2}")
    @CsvSource({
        // Diário com intervalo 3 cruzando 31/12 -> 01/01.
        "3, 2026-12-28, 2027-01-07, 2026-12-28;2026-12-31;2027-01-03;2027-01-06",
        // Diário com intervalo 1 em ano bissexto.
        "1, 2028-02-27, 2028-03-01, 2028-02-27;2028-02-28;2028-02-29;2028-03-01"
    })
    @DisplayName("DIARIO: intervalo fixo cruzando virada de ano e fevereiro bissexto")
    void diarioComIntervaloCruzaFimDeAno(int intervalo, String inicio, String alvo, String esperado) {
        SerieRecorrente serie = serie(
                "DIARIO", intervalo, List.of(), null, null, null, null,
                "NUNCA", LocalDate.parse(inicio), null, null, 0, null);

        assertEquals(datasDe(esperado), engine.calcularDatasCandidatas(serie, LocalDate.parse(alvo)));
    }

    @Test
    @DisplayName("SEMANAL intervalo 2: semana ISO 53 (2026-12-28) com início em quarta-feira")
    void semanalComSemanaIso53EInicioNaQuarta() {
        // 2026-12-30 é quarta-feira e pertence à semana ISO 53 de 2026.
        LocalDate inicio = LocalDate.of(2026, 12, 30);
        SerieRecorrente serie = serie(
                "SEMANAL", 2, List.of(1, 3), null, null, null, null,
                "NUNCA", inicio, null, null, 0, null);

        List<LocalDate> datas = engine.calcularDatasCandidatas(serie, LocalDate.of(2027, 1, 31));

        // Semana 0 (segunda 2026-12-28): quarta 12-30 (a segunda é anterior ao início).
        // Semana 2 (segunda 2027-01-11): 01-11 e 01-13. Semana 4 (segunda 2027-01-25): 01-25 e 01-27.
        assertEquals(List.of(
                LocalDate.of(2026, 12, 30),
                LocalDate.of(2027, 1, 11),
                LocalDate.of(2027, 1, 13),
                LocalDate.of(2027, 1, 25),
                LocalDate.of(2027, 1, 27)), datas);
    }

    @ParameterizedTest(name = "MENSAL dia 31 em {0} gera {1}")
    @CsvSource({
        "2027-01-31, 2027-02-28",
        "2028-01-31, 2028-02-29"
    })
    @DisplayName("MENSAL dia 31: fevereiro não bissexto (2027) e bissexto (2028)")
    void mensalDia31EmFevereiro(String inicio, String esperadoFevereiro) {
        SerieRecorrente serie = serie(
                "MENSAL", 1, List.of(), "DIA_DO_MES", 31, null, null,
                "NUNCA", LocalDate.parse(inicio), null, null, 0, null);

        List<LocalDate> datas = engine.calcularDatasCandidatas(
                serie, LocalDate.parse(esperadoFevereiro));

        assertEquals(List.of(LocalDate.parse(inicio), LocalDate.parse(esperadoFevereiro)), datas);
    }

    @Test
    @DisplayName("MENSAL ORDINAL_SEMANA: ordinal 5 inexistente é ignorado e -1 pega a última sexta")
    void mensalOrdinalInexistenteEUltimaOcorrencia() {
        // Fevereiro de 2027 tem apenas 4 sextas (5, 12, 19, 26): ordinal 5 não gera ocorrência.
        SerieRecorrente quintaSexta = serie(
                "MENSAL", 1, List.of(), "ORDINAL_SEMANA", null, 5, 5,
                "NUNCA", LocalDate.of(2027, 1, 1), null, null, 0, null);
        List<LocalDate> semQuinta = engine.calcularDatasCandidatas(quintaSexta, LocalDate.of(2027, 2, 28));
        assertFalse(semQuinta.contains(LocalDate.of(2027, 2, 26)),
                "O ordinal 5 não existe em fevereiro/2027 e não pode gerar ocorrência");

        // A última sexta (-1) de cada mês é sempre materializada.
        SerieRecorrente ultimaSexta = serie(
                "MENSAL", 1, List.of(), "ORDINAL_SEMANA", null, -1, 5,
                "NUNCA", LocalDate.of(2027, 1, 1), null, null, 0, null);
        List<LocalDate> ultimas = engine.calcularDatasCandidatas(ultimaSexta, LocalDate.of(2027, 3, 31));
        assertEquals(List.of(
                LocalDate.of(2027, 1, 29),
                LocalDate.of(2027, 2, 26),
                LocalDate.of(2027, 3, 26)), ultimas);
    }

    @Test
    @DisplayName("EM_DATA é inclusiva: a data de término entra na janela")
    void terminoEmDataEhInclusivo() {
        LocalDate inicio = LocalDate.of(2027, 5, 1);
        LocalDate fim = LocalDate.of(2027, 5, 5);
        SerieRecorrente serie = serie(
                "DIARIO", 1, List.of(), null, null, null, null,
                "EM_DATA", inicio, fim, null, 0, null);

        List<LocalDate> datas = engine.calcularDatasCandidatas(serie, fim);

        assertEquals(List.of(
                LocalDate.of(2027, 5, 1),
                LocalDate.of(2027, 5, 2),
                LocalDate.of(2027, 5, 3),
                LocalDate.of(2027, 5, 4),
                LocalDate.of(2027, 5, 5)), datas);
        assertTrue(datas.contains(fim), "A data de término é inclusiva");
    }

    @Test
    @DisplayName("APOS_OCORRENCIAS: posições já consumidas (cancelada/editada) contam no limite")
    void terminoAposOcorrenciasConsideraPosicoesConsumidas() {
        LocalDate inicio = LocalDate.of(2027, 6, 1);
        // totalGeradas = 2 e cursor em 06-02: duas posições já consumidas (cancelada + desvinculada).
        // Com maxOcorrencias = 4, restam exatamente duas posições a materializar.
        SerieRecorrente serie = serie(
                "DIARIO", 1, List.of(), null, null, null, null,
                "APOS_OCORRENCIAS", inicio, null, 4, 2, inicio.plusDays(1));

        List<LocalDate> candidatas = engine.calcularDatasCandidatas(serie, LocalDate.of(2027, 6, 10));
        List<LocalDate> datas = engine.aplicarLimiteTermino(serie, candidatas);

        assertEquals(List.of(LocalDate.of(2027, 6, 3), LocalDate.of(2027, 6, 4)), datas);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static SerieRecorrente serie(
            String frequencia,
            int intervalo,
            List<Integer> diasDaSemana,
            String modoMensal,
            Integer diaDoMes,
            Integer ordinalSemana,
            Integer diaSemanaOrdinal,
            String tipoTermino,
            LocalDate dataInicio,
            LocalDate dataFim,
            Integer maxOcorrencias,
            int totalGeradas,
            LocalDate geradoAte) {
        return new SerieRecorrente(
                new ObjectId(), new ObjectId(), null, "Serie Teste", null, PrioridadeTarefa.MEDIA, null,
                List.of(), frequencia, intervalo, diasDaSemana, modoMensal, diaDoMes, ordinalSemana,
                diaSemanaOrdinal, tipoTermino, dataInicio, dataFim, maxOcorrencias, geradoAte, totalGeradas,
                true, ModoLembrete.HERDAR, null, null, null, null, null, null, Instant.now(), Instant.now());
    }

    private static List<LocalDate> datasDe(String listaCsv) {
        return Arrays.stream(listaCsv.split(";")).map(LocalDate::parse).toList();
    }
}
