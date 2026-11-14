package br.com.trilhara.trilha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrilhaProgressoCalculoTest {

    private static final Instant AGORA = Instant.parse("2026-11-14T12:00:00Z");

    private static Disciplina criarDisciplina(int peso, boolean concluida, String... nomesTopicos) {
        ObjectId trilhaId = new ObjectId();
        List<TopicoEmbutido> topicos = new ArrayList<>();
        for (int i = 0; i < nomesTopicos.length; i++) {
            topicos.add(new TopicoEmbutido(nomesTopicos[i], nomesTopicos[i], i + 1));
        }
        return new Disciplina(
                new ObjectId(),
                new ObjectId(),
                trilhaId,
                "Disciplina",
                "#2563eb",
                peso,
                0,
                topicos,
                concluida,
                AGORA,
                AGORA);
    }

    @Test
    @DisplayName("100 disciplinas peso 1 com 50% de uma concluída resulta em 0.5% de progresso")
    void cemDisciplinasPesoUmComMetadeDeUmaConcluida() {
        List<Disciplina> disciplinas = new ArrayList<>();
        disciplinas.add(criarDisciplina(1, false, "top-1", "top-2"));
        for (int i = 1; i < 100; i++) {
            disciplinas.add(criarDisciplina(1, false));
        }

        var resumo = TrilhaService.calcularResumoProgresso("trilha-1", disciplinas, Set.of("top-1"));

        assertEquals(0.5, resumo.percentualGeral(), 0.0001);
        assertEquals(2, resumo.totalTopicos());
        assertEquals(1, resumo.topicosConcluidos());
        assertEquals(0, resumo.disciplinasConcluidas());
        assertTrue(resumo.temDisciplinaSemTopico());
    }

    @Test
    @DisplayName("10 disciplinas sem tópicos com 9 concluídas resulta em 90%")
    void dezDisciplinasSemTopicosNoveConcluidas() {
        List<Disciplina> disciplinas = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            disciplinas.add(criarDisciplina(1, true));
        }
        disciplinas.add(criarDisciplina(1, false));

        var resumo = TrilhaService.calcularResumoProgresso("trilha-1", disciplinas, Set.of());

        assertEquals(90.0, resumo.percentualGeral(), 0.0001);
        assertEquals(9, resumo.disciplinasConcluidas());
        assertEquals(0, resumo.totalTopicos());
        assertEquals(0, resumo.topicosConcluidos());
        assertTrue(resumo.temDisciplinaSemTopico());
    }

    @Test
    @DisplayName("10 disciplinas sem tópicos pendentes + 1 com tópicos concluída pondera pelo peso em vez de 100%")
    void misturaDisciplinasSemTopicosEComTopicos() {
        List<Disciplina> disciplinas = new ArrayList<>();
        disciplinas.add(criarDisciplina(5, false, "top-a", "top-b"));
        for (int i = 0; i < 10; i++) {
            disciplinas.add(criarDisciplina(1, false));
        }

        var resumo = TrilhaService.calcularResumoProgresso("trilha-1", disciplinas, Set.of("top-a", "top-b"));

        // peso 5 / (10*1 + 5) = 5/15 = 33.3333%
        assertEquals(100.0 / 3.0, resumo.percentualGeral(), 0.0001);
        assertEquals(1, resumo.disciplinasConcluidas());
        assertTrue(resumo.temDisciplinaSemTopico());
    }

    @Test
    @DisplayName("Todas as disciplinas com tópicos define temDisciplinaSemTopico como false")
    void todasDisciplinasComTopicos() {
        List<Disciplina> disciplinas = List.of(
                criarDisciplina(2, false, "t1", "t2"),
                criarDisciplina(3, false, "t3"));

        // D1 peso 2 -> 50%, D2 peso 3 -> 100% => (2*0.5 + 3*1.0)/5 = 4/5 = 80%
        var resumo = TrilhaService.calcularResumoProgresso("trilha-1", disciplinas, Set.of("t1", "t3"));

        assertEquals(80.0, resumo.percentualGeral(), 0.0001);
        assertEquals(1, resumo.disciplinasConcluidas());
        assertFalse(resumo.temDisciplinaSemTopico());
    }

    @Test
    @DisplayName("Trilha vazia retorna 0% sem erros")
    void trilhaVazia() {
        var resumo = TrilhaService.calcularResumoProgresso("trilha-1", List.of(), Set.of());

        assertEquals(0.0, resumo.percentualGeral(), 0.0001);
        assertEquals(0, resumo.disciplinasConcluidas());
        assertFalse(resumo.temDisciplinaSemTopico());
    }
}
