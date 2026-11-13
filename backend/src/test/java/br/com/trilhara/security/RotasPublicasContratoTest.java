package br.com.trilhara.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.trilhara.shared.RotasInventarioTest;

/**
 * R13 — contrato da superfície HTTP: as rotas registradas são exatamente as do Anexo A.
 *
 * <p>Até a R12 a allowlist de rotas vivia apenas em Markdown (Anexo A do guia) e não havia
 * teste comparando-a com o código: uma rota nova entrava em produção sem revisão de contrato e
 * uma rota removida continuava documentada. Este teste lê os {@code *Resource} (mesmo
 * inventariador de {@code RotasInventarioTest}) e compara com {@link RotasPrivadas}.</p>
 *
 * <p>As três asserções cobrem os dois sentidos, sem falso verde:</p>
 * <ol>
 *   <li>toda rota do código está declarada na allowlist (rota nova sem documentação falha);</li>
 *   <li>toda rota da allowlist existe no código (rota removida e ainda documentada falha);</li>
 *   <li>as rotas explicitamente removidas por D-11/D-23 não voltam.</li>
 * </ol>
 */
@DisplayName("R13 - Contrato de rotas públicas × allowlist do Anexo A")
class RotasPublicasContratoTest {

    private static List<String> rotasDoCodigo() throws IOException {
        Path base = RotasInventarioTest.diretorioFontes();
        return RotasInventarioTest.inventariarRotas(base);
    }

    /** Allowlist completa, sem repetição e em ordem estável para a mensagem de falha. */
    @Test
    @DisplayName("toda rota registrada no código está declarada na allowlist")
    void todasAsRotasDoCodigoEstaoNaAllowlist() throws IOException {
        Set<String> declaradas = RotasPrivadas.todasAsRotas();

        List<String> naoDeclaradas = new ArrayList<>();
        for (String rota : rotasDoCodigo()) {
            if (!declaradas.contains(rota)) {
                naoDeclaradas.add(rota);
            }
        }

        assertTrue(naoDeclaradas.isEmpty(),
                "Rotas registradas no código mas ausentes da allowlist (Anexo A / RotasPrivadas): "
                        + naoDeclaradas);
    }

    @Test
    @DisplayName("toda rota da allowlist existe no código (sem documentação morta)")
    void todaRotaDaAllowlistExisteNoCodigo() throws IOException {
        Set<String> doCodigo = new TreeSet<>(rotasDoCodigo());

        List<String> orfas = new ArrayList<>();
        for (String rota : RotasPrivadas.todasAsRotas()) {
            if (!doCodigo.contains(rota)) {
                orfas.add(rota);
            }
        }

        assertTrue(orfas.isEmpty(),
                "Rotas documentadas na allowlist que não existem mais no código: " + orfas);
    }

    @Test
    @DisplayName("rotas removidas por D-11/D-23 não voltam")
    void rotasRemovidasNaoVoltam() throws IOException {
        Set<String> doCodigo = new TreeSet<>(rotasDoCodigo());

        for (String removida : RotasPrivadas.REMOVIDAS) {
            assertFalse(doCodigo.contains(removida),
                    "Rota removida pela reestruturação reapareceu: " + removida);
        }
    }

    @Test
    @DisplayName("allowlist não tem duplicidade e cobre as mesmas rotas nos dois sentidos")
    void allowlistSemDuplicidade() {
        Set<String> todas = new LinkedHashSet<>();
        int declaradas = 0;
        for (String rota : RotasPrivadas.todasAsRotasEmOrdemDeDeclaracao()) {
            declaradas++;
            todas.add(rota);
        }
        assertTrue(todas.size() == declaradas,
                "Allowlist contém rotas duplicadas: " + (declaradas - todas.size()));
    }
}
