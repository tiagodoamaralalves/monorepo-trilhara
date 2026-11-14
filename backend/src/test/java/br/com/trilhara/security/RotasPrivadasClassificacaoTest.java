package br.com.trilhara.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import br.com.trilhara.shared.RotasInventarioTest;

/**
 * Guarda de classificação total.
 *
 * <p>Toda rota inventariada pelo {@link RotasInventarioTest} precisa estar
 * classificada em <strong>um</strong> dos três conjuntos de {@link RotasPrivadas}:
 * {@code PUBLICAS}, {@code ADMIN} ou {@code LISTAGENS} — ou ter um caso anti-IDOR
 * com path param. Rota nova sem classificação quebra o build.</p>
 */
@DisplayName("Classificação total das rotas privadas (guard da allowlist)")
class RotasPrivadasClassificacaoTest {

    /** Converte `GET /api/trilhas/{trilhaA}` no template inventariado `GET /api/trilhas/{trilhaId}`. */
    private static final Pattern MARCADOR = Pattern.compile("\\{[a-zA-Z]+A\\}");

    private static String normalizar(String rota) {
        return MARCADOR.matcher(rota).replaceAll("{id}");
    }

    private static String paraTemplate(String rotaInventariada) {
        // O inventário traz `GET /api/trilhas/{trilhaId}`; normalizamos qualquer {…} para {id}.
        return rotaInventariada.replaceAll("\\{[^}]+\\}", "{id}");
    }

    private Set<String> classificadas() {
        Set<String> rotas = new LinkedHashSet<>();
        RotasPrivadas.PUBLICAS.forEach(r -> rotas.add(paraTemplate(r)));
        RotasPrivadas.ADMIN.forEach(r -> rotas.add(paraTemplate(r)));
        RotasPrivadas.PROPRIA_CONTA.forEach(r -> rotas.add(paraTemplate(r)));
        RotasPrivadas.CATALOGO_E_PURGA.forEach(r -> rotas.add(paraTemplate(r)));
        RotasPrivadas.LISTAGENS.forEach(r -> rotas.add(paraTemplate(r)));
        for (RotasPrivadas.Caso caso : RotasPrivadas.casosDeclarados()) {
            rotas.add(paraTemplate(normalizar(caso.metodo() + " " + caso.caminho())));
        }
        return rotas;
    }

    @Test
    @DisplayName("Nenhuma rota inventariada fica sem caso na allowlist")
    void nenhumaRotaFicaSemCaso() throws IOException {
        List<String> inventariadas = RotasInventarioTest.inventariarRotas(RotasInventarioTest.diretorioFontes());
        Set<String> classificadas = classificadas();
        List<String> descobertas = new ArrayList<>();
        for (String rota : inventariadas) {
            if (!classificadas.contains(paraTemplate(rota))) {
                descobertas.add(rota);
            }
        }
        if (!descobertas.isEmpty()) {
            throw new AssertionError(
                    "Rotas privadas sem caso anti-IDOR nem classificação em RotasPrivadas: "
                            + descobertas);
        }
    }

    @Test
    @DisplayName("Não há caso declarado para rota inexistente no inventário")
    void nenhumCasoOrfao() throws IOException {
        List<String> inventariadas = RotasInventarioTest.inventariarRotas(RotasInventarioTest.diretorioFontes())
                .stream().map(RotasPrivadasClassificacaoTest::paraTemplate).toList();
        List<String> orfaos = new ArrayList<>();
        for (RotasPrivadas.Caso caso : RotasPrivadas.casosDeclarados()) {
            String alvo = paraTemplate(normalizar(caso.metodo() + " " + caso.caminho()));
            if (!inventariadas.contains(alvo)) {
                orfaos.add(alvo);
            }
        }
        if (!orfaos.isEmpty()) {
            throw new AssertionError("Casos anti-IDOR apontando para rotas inexistentes: " + orfaos);
        }
    }

    @ParameterizedTest(name = "{0} {1} (espera {3})")
    @MethodSource("br.com.trilhara.security.RotasPrivadas#casos")
    @DisplayName("Allowlist é consumível e não vazia")
    void allowlistConsumivel(String metodo, String caminho, boolean comCorpo,
                             int statusEsperado, String descricao) {
        // Este teste falha se a tabela ficar vazia ou um caso malformado entrar.
        Matcher marcador = Pattern.compile("\\{([a-zA-Z]+)A\\}").matcher(caminho);
        List<String> encontrados = new ArrayList<>();
        while (marcador.find()) {
            encontrados.add(marcador.group(1));
        }
        if (comCorpo && encontrados.isEmpty() && statusEsperado == 400) {
            throw new AssertionError("Caso 400 exige ID no corpo: " + descricao);
        }
        if (!comCorpo && encontrados.isEmpty() && statusEsperado == 404) {
            throw new AssertionError("Caso 404 exige ID no path: " + descricao);
        }
    }
}
