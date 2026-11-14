package br.com.trilhara.cronograma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import br.com.trilhara.trilha.Disciplina;
import jakarta.ws.rs.BadRequestException;

class GeradorBlocosCronogramaTest {

    private static final ObjectId USUARIO_ID = new ObjectId();
    private static final ObjectId TRILHA_ID = new ObjectId();
    private static final ObjectId CRONOGRAMA_ID = new ObjectId();

    private static Disciplina disciplina(String nome, int peso, int ordem) {
        return new Disciplina(
                new ObjectId(),
                USUARIO_ID,
                TRILHA_ID,
                nome,
                "#6366F1",
                peso,
                ordem,
                List.of(),
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    void sequencialComBlocosPorDiaSemanaVariadosGeraQuantidadeIndividualPorDia() {
        List<Disciplina> disciplinas = List.of(
                disciplina("Português", 2, 0),
                disciplina("Direito", 1, 1)
        );
        // 2026-04-06 é segunda-feira (dia 1) até 2026-04-12 domingo (dia 7)
        LocalDate segunda = LocalDate.of(2026, 4, 6);
        LocalDate domingo = LocalDate.of(2026, 4, 12);

        List<BlocoCronograma> blocos = GeradorBlocosCronograma.gerar(
                new GeradorBlocosCronograma.ParametrosGeracao(
                        USUARIO_ID,
                        TRILHA_ID,
                        CRONOGRAMA_ID,
                        1L,
                        ModalidadeCronograma.SEQUENCIAL,
                        50,
                        segunda,
                        domingo,
                        List.of(1, 2, 5),
                        null,
                        Map.of(1, 3, 2, 1, 5, 2),
                        disciplinas,
                        null
                )
        );

        assertEquals(6, blocos.size());
        Map<Integer, Long> contagemPorDia = blocos.stream()
                .collect(Collectors.groupingBy(b -> b.data().getDayOfWeek().getValue(), Collectors.counting()));
        assertEquals(3L, contagemPorDia.get(1));
        assertEquals(1L, contagemPorDia.get(2));
        assertEquals(2L, contagemPorDia.get(5));
        assertEquals(3, contagemPorDia.size());
    }

    @Test
    void ponderadoComBlocosPorDiaSemanaVariadosRespeitaTotalEContagemPorDia() {
        List<Disciplina> disciplinas = List.of(
                disciplina("Constitucional", 4, 0),
                disciplina("Ética", 2, 1)
        );
        LocalDate segunda = LocalDate.of(2026, 4, 6);
        LocalDate domingo = LocalDate.of(2026, 4, 12);

        List<BlocoCronograma> blocos = GeradorBlocosCronograma.gerar(
                new GeradorBlocosCronograma.ParametrosGeracao(
                        USUARIO_ID,
                        TRILHA_ID,
                        CRONOGRAMA_ID,
                        1L,
                        ModalidadeCronograma.PONDERADO,
                        60,
                        segunda,
                        domingo,
                        List.of(1, 3),
                        null,
                        Map.of(1, 4, 3, 2),
                        disciplinas,
                        null
                )
        );

        assertEquals(6, blocos.size());
        Map<Integer, Long> contagemPorDia = blocos.stream()
                .collect(Collectors.groupingBy(b -> b.data().getDayOfWeek().getValue(), Collectors.counting()));
        assertEquals(4L, contagemPorDia.get(1));
        assertEquals(2L, contagemPorDia.get(3));

        long blocosConstitucional = blocos.stream()
                .filter(b -> "Constitucional".equals(b.disciplinaNomeSnapshot()))
                .count();
        long blocosEtica = blocos.stream()
                .filter(b -> "Ética".equals(b.disciplinaNomeSnapshot()))
                .count();
        assertEquals(4L, blocosConstitucional);
        assertEquals(2L, blocosEtica);
    }

    @Test
    void compatibilidadeUniformeSemMapaMantemBlocosPorDiaUniforme() {
        List<Disciplina> disciplinas = List.of(disciplina("Matemática", 1, 0));
        LocalDate segunda = LocalDate.of(2026, 4, 6);
        LocalDate domingo = LocalDate.of(2026, 4, 12);

        List<BlocoCronograma> blocos = GeradorBlocosCronograma.gerar(
                new GeradorBlocosCronograma.ParametrosGeracao(
                        USUARIO_ID,
                        TRILHA_ID,
                        CRONOGRAMA_ID,
                        1L,
                        ModalidadeCronograma.SEQUENCIAL,
                        45,
                        segunda,
                        domingo,
                        List.of(1, 3),
                        2,
                        null,
                        disciplinas,
                        null
                )
        );

        assertEquals(4, blocos.size());
    }

    @Test
    void rejeitaMapaSemNenhumDiaComBlocosPositivos() {
        List<Disciplina> disciplinas = List.of(disciplina("História", 1, 0));
        LocalDate segunda = LocalDate.of(2026, 4, 6);

        assertThrows(BadRequestException.class, () -> GeradorBlocosCronograma.gerar(
                new GeradorBlocosCronograma.ParametrosGeracao(
                        USUARIO_ID,
                        TRILHA_ID,
                        CRONOGRAMA_ID,
                        1L,
                        ModalidadeCronograma.SEQUENCIAL,
                        50,
                        segunda,
                        segunda.plusDays(6),
                        List.of(),
                        null,
                        Map.of(1, 0, 2, 0),
                        disciplinas,
                        null
                )
        ));
    }
}
