package br.com.trilhara.tarefa;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Fase6RecorrenciaEngineTest {

    private final RecorrenciaEngine engine = new RecorrenciaEngine(null, null);

    @Test
    @DisplayName("Recorrência diária: gera datas no intervalo correto")
    void deveGerarRecorrenciaDiaria() {
        LocalDate inicio = LocalDate.of(2026, 3, 1);
        SerieRecorrente serie = criarSerie(
                "DIARIO", 2, null, null, null, null, null, "NUNCA", inicio, null, null, null, 0);

        List<LocalDate> datas = engine.calcularDatasCandidatas(serie, LocalDate.of(2026, 3, 10));
        assertEquals(List.of(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 3),
                LocalDate.of(2026, 3, 5),
                LocalDate.of(2026, 3, 7),
                LocalDate.of(2026, 3, 9)
        ), datas);
    }

    @Test
    @DisplayName("Recorrência semanal: gera apenas os dias da semana selecionados")
    void deveGerarRecorrenciaSemanal() {
        // 2026-03-02 é Segunda-feira
        LocalDate inicio = LocalDate.of(2026, 3, 2);
        // Segunda (1), Quarta (3) e Sexta (5) a cada 1 semana
        SerieRecorrente serie = criarSerie(
                "SEMANAL", 1, List.of(1, 3, 5), null, null, null, null, "NUNCA", inicio, null, null, null, 0);

        List<LocalDate> datas = engine.calcularDatasCandidatas(serie, LocalDate.of(2026, 3, 8));
        assertEquals(List.of(
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 4),
                LocalDate.of(2026, 3, 6)
        ), datas);
    }

    @Test
    @DisplayName("Recorrência mensal dia 31: ajusta para último dia do mês em fevereiro não bissexto (28) e bissexto (29)")
    void deveAjustarUltimoDiaDoMesEmFevereiro() {
        // Fevereiro de 2025 não é bissexto -> 28 dias
        LocalDate inicio2025 = LocalDate.of(2025, 1, 31);
        SerieRecorrente serie2025 = criarSerie(
                "MENSAL", 1, null, "DIA_DO_MES", 31, null, null, "NUNCA", inicio2025, null, null, null, 0);

        List<LocalDate> datas2025 = engine.calcularDatasCandidatas(serie2025, LocalDate.of(2025, 4, 1));
        assertEquals(List.of(
                LocalDate.of(2025, 1, 31),
                LocalDate.of(2025, 2, 28), // Clamped to 28!
                LocalDate.of(2025, 3, 31)
        ), datas2025);

        // Fevereiro de 2024 foi bissexto -> 29 dias
        LocalDate inicio2024 = LocalDate.of(2024, 1, 31);
        SerieRecorrente serie2024 = criarSerie(
                "MENSAL", 1, null, "DIA_DO_MES", 31, null, null, "NUNCA", inicio2024, null, null, null, 0);

        List<LocalDate> datas2024 = engine.calcularDatasCandidatas(serie2024, LocalDate.of(2024, 3, 1));
        assertEquals(List.of(
                LocalDate.of(2024, 1, 31),
                LocalDate.of(2024, 2, 29) // Clamped to 29!
        ), datas2024);
    }

    @Test
    @DisplayName("Recorrência mensal ordinal: ordinal -1 obtém a última ocorrência do dia da semana no mês")
    void deveCalcularUltimaOcorrenciaDoDiaDaSemana() {
        // Última sexta-feira do mês (dia 5)
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        SerieRecorrente serie = criarSerie(
                "MENSAL", 1, null, "ORDINAL_SEMANA", null, -1, 5, "NUNCA", inicio, null, null, null, 0);

        List<LocalDate> datas = engine.calcularDatasCandidatas(serie, LocalDate.of(2026, 3, 31));
        // Janeiro 2026: 30 (última sexta)
        // Fevereiro 2026: 27 (última sexta)
        // Março 2026: 27 (última sexta)
        assertEquals(List.of(
                LocalDate.of(2026, 1, 30),
                LocalDate.of(2026, 2, 27),
                LocalDate.of(2026, 3, 27)
        ), datas);
    }

    @Test
    @DisplayName("Recorrência mensal ordinal: ordinal 2 obtém a segunda segunda-feira do mês")
    void deveCalcularSegundaOcorrenciaDoDiaDaSemana() {
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        // Segunda segunda-feira (ordinal = 2, dia = 1)
        SerieRecorrente serie = criarSerie(
                "MENSAL", 1, null, "ORDINAL_SEMANA", null, 2, 1, "NUNCA", inicio, null, null, null, 0);

        List<LocalDate> datas = engine.calcularDatasCandidatas(serie, LocalDate.of(2026, 3, 31));
        // Jan 2026: segundas são 5, 12, 19, 26 -> segunda é 12
        // Fev 2026: segundas são 2, 9, 16, 23 -> segunda é 9
        // Mar 2026: segundas são 2, 9, 16, 23, 30 -> segunda é 9
        assertEquals(List.of(
                LocalDate.of(2026, 1, 12),
                LocalDate.of(2026, 2, 9),
                LocalDate.of(2026, 3, 9)
        ), datas);
    }

    private SerieRecorrente criarSerie(
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
            LocalDate geradoAte,
            int totalGeradas) {
        return new SerieRecorrente(
                new ObjectId(),
                new ObjectId(),
                null,
                "Tarefa Teste",
                null,
                PrioridadeTarefa.MEDIA,
                null,
                List.of(),
                frequencia,
                intervalo,
                diasDaSemana,
                modoMensal,
                diaDoMes,
                ordinalSemana,
                diaSemanaOrdinal,
                tipoTermino,
                dataInicio,
                dataFim,
                maxOcorrencias,
                geradoAte,
                totalGeradas,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now()
        );
    }
}
