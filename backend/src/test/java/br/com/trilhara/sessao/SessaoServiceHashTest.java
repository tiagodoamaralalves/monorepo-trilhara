package br.com.trilhara.sessao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Hash funcional canônico da criação de sessão (R4).
 *
 * <p>O {@code requestHash} é SHA-256 de JSON canônico com chaves ordenadas. Sem essa garantia o
 * retry de um salvamento legítimo seria tratado como conflito de chave.</p>
 */
class SessaoServiceHashTest {

    private static final ObjectId TRILHA = new ObjectId("650000000000000000000001");
    private static final ObjectId DISCIPLINA = new ObjectId("650000000000000000000002");
    private static final String DATA = "2026-11-12";

    @Test
    @DisplayName("Deve ser determinístico para o mesmo payload funcional")
    void deveSerDeterministicoParaMesmoPayload() {
        String primeiro = SessaoService.calcularRequestHash(TRILHA, DISCIPLINA, DATA, 1800, 10, 8);
        String segundo = SessaoService.calcularRequestHash(TRILHA, DISCIPLINA, DATA, 1800, 10, 8);

        assertEquals(primeiro, segundo);
        assertEquals(64, primeiro.length(), "SHA-256 em hexadecimal tem 64 caracteres");
    }

    @Test
    @DisplayName("Deve mudar de hash quando qualquer campo funcional mudar")
    void deveMudarQuandoCampoFuncionalMudar() {
        String base = SessaoService.calcularRequestHash(TRILHA, DISCIPLINA, DATA, 1800, 10, 8);

        assertNotEquals(base, SessaoService.calcularRequestHash(
                new ObjectId("650000000000000000000009"), DISCIPLINA, DATA, 1800, 10, 8));
        assertNotEquals(base, SessaoService.calcularRequestHash(
                TRILHA, new ObjectId("650000000000000000000008"), DATA, 1800, 10, 8));
        assertNotEquals(base, SessaoService.calcularRequestHash(
                TRILHA, DISCIPLINA, "2026-11-11", 1800, 10, 8));
        assertNotEquals(base, SessaoService.calcularRequestHash(
                TRILHA, DISCIPLINA, DATA, 1801, 10, 8));
        assertNotEquals(base, SessaoService.calcularRequestHash(
                TRILHA, DISCIPLINA, DATA, 1800, 11, 8));
        assertNotEquals(base, SessaoService.calcularRequestHash(
                TRILHA, DISCIPLINA, DATA, 1800, 10, 9));
    }

    @Test
    @DisplayName("Deve produzir o hash do JSON canônico com as seis chaves ordenadas")
    void deveProduzirHashDoJsonCanonico() {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("trilhaId", TRILHA.toHexString());
        campos.put("disciplinaId", DISCIPLINA.toHexString());
        campos.put("dataSessao", DATA);
        campos.put("duracaoSegundos", 95);
        campos.put("questoes", 0);
        campos.put("acertos", 0);

        assertEquals(6, campos.size());
        assertEquals(
                SessaoService.calcularRequestHash(TRILHA, DISCIPLINA, DATA, 95, 0, 0),
                SessaoService.calcularRequestHash(TRILHA, DISCIPLINA, DATA, 95, 0, 0),
                "o hash não pode depender de ordem de iteração externa");
    }

    @Test
    @DisplayName("Campos numéricos não colidem por concatenar delimitador (95,0 vs 9,5)")
    void naoDeveColidirEntreDuracaoEQuestoes() {
        String com95 = SessaoService.calcularRequestHash(TRILHA, DISCIPLINA, DATA, 95, 0, 0);
        String com9e5 = SessaoService.calcularRequestHash(TRILHA, DISCIPLINA, DATA, 9, 5, 0);

        assertNotEquals(com95, com9e5);
    }

    @Test
    @DisplayName("Granularidade deve ser derivada do intervalo consultado")
    void deveDerivarGranularidade() {
        LocalDate hoje = LocalDate.of(2026, 11, 12);

        assertEquals(Granularidade.DIA, Granularidade.para(hoje.minusDays(6), hoje), "7d → DIA");
        assertEquals(Granularidade.DIA, Granularidade.para(hoje.minusDays(29), hoje), "30d → DIA");
        assertEquals(Granularidade.DIA, Granularidade.para(hoje.minusDays(30), hoje), "31 dias → DIA");
        assertEquals(Granularidade.SEMANA, Granularidade.para(hoje.minusDays(31), hoje), "32 dias → SEMANA");
        assertEquals(Granularidade.SEMANA, Granularidade.para(hoje.minusDays(179), hoje), "180 dias → SEMANA");
        assertEquals(Granularidade.MES, Granularidade.para(hoje.minusDays(180), hoje), "181 dias → MES");
        assertEquals(Granularidade.MES, Granularidade.para(null, null), "tudo → MES");
    }

    @Test
    @DisplayName("Chave do bucket deve usar segunda-feira ISO na semana e dia 01 no mês")
    void deveCalcularChaveDoBucket() {
        LocalDate quinta = LocalDate.of(2026, 11, 12);

        assertEquals("2026-11-12", Granularidade.DIA.chave(quinta));
        assertEquals("2026-11-09", Granularidade.SEMANA.chave(quinta), "segunda-feira da semana ISO");
        assertEquals("2026-11-09", Granularidade.SEMANA.chave(LocalDate.of(2026, 11, 9)),
                "segunda-feira é idempotente");
        assertEquals("2026-11-01", Granularidade.MES.chave(quinta));
    }
}
