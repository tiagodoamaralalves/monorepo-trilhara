package br.com.trilhara.shared;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Inventário executável das rotas JAX-RS registradas (R0, item 3 do checklist).
 *
 * <p>Lê o código-fonte de todas as classes {@code *Resource} em
 * {@code src/main/java} e imprime verbo + caminho completo. Não é teste de
 * contrato de runtime (isso é {@code RotasPublicasContratoTest}, em fase
 * posterior): aqui garantimos que o inventário é gerado, é estável e que nenhum
 * recurso fica fora por falta de {@code @Path}.</p>
 *
 * <p>Saída usada como insumo do Anexo A e do relatório da R0. O diretório base é
 * resolvido a partir de {@code user.dir} (backend/) com fallback para
 * {@code TRILHARA_RAIZ}, exportada por {@code scripts/env-gate.sh}.</p>
 */
@DisplayName("R0 - Inventário executável de rotas JAX-RS (*Resource)")
class RotasInventarioTest {

    private static final Pattern PATH_CLASSE =
            Pattern.compile("@Path\\(\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern PATH_METODO =
            Pattern.compile("@Path\\(\\s*\"([^\"]*)\"\\s*\\)");
    private static final Pattern VERBO =
            Pattern.compile("@(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\\b");

    private static Path diretorioFontes() {
        List<Path> candidatos = new ArrayList<>();
        candidatos.add(Paths.get(System.getProperty("user.dir"), "src", "main", "java"));
        String raiz = System.getenv("TRILHARA_RAIZ");
        if (raiz != null && !raiz.isBlank()) {
            candidatos.add(Paths.get(raiz, "backend", "src", "main", "java"));
        }
        candidatos.add(Paths.get("backend", "src", "main", "java"));
        for (Path c : candidatos) {
            if (Files.isDirectory(c)) {
                return c;
            }
        }
        throw new IllegalStateException(
                "Diretório de fontes não encontrado. Execute a partir de backend/ ou export TRILHARA_RAIZ.");
    }

    private static String normalizar(String trecho) {
        String t = trecho.trim();
        if (t.isEmpty()) {
            return "";
        }
        if (!t.startsWith("/")) {
            t = "/" + t;
        }
        while (t.length() > 1 && t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private static String juntar(String base, String sub) {
        if (sub.isEmpty()) {
            return base;
        }
        return base.equals("/") ? sub : base + sub;
    }

    static List<String> inventariarRotas(Path base) throws IOException {
        List<Path> recursos;
        try (Stream<Path> s = Files.walk(base)) {
            recursos = s.filter(p -> p.getFileName().toString().endsWith("Resource.java"))
                    .sorted()
                    .toList();
        }

        List<String> rotas = new ArrayList<>();
        for (Path arquivo : recursos) {
            List<String> linhas = Files.readAllLines(arquivo);
            String baseClasse = null;
            for (String linha : linhas) {
                Matcher m = PATH_CLASSE.matcher(linha);
                if (m.find()) {
                    baseClasse = normalizar(m.group(1));
                    break;
                }
            }
            if (baseClasse == null) {
                continue;
            }

            for (int i = 0; i < linhas.size(); i++) {
                List<String> verbos = new ArrayList<>();
                int cursor = i;
                while (cursor < linhas.size()) {
                    Matcher v = VERBO.matcher(linhas.get(cursor));
                    boolean achou = false;
                    while (v.find()) {
                        verbos.add(v.group(1));
                        achou = true;
                    }
                    if (!achou) {
                        break;
                    }
                    cursor++;
                }
                if (verbos.isEmpty()) {
                    continue;
                }
                String sub = "";
                int janela = Math.min(cursor + 2, linhas.size());
                for (int j = cursor; j < janela; j++) {
                    Matcher pm = PATH_METODO.matcher(linhas.get(j));
                    if (pm.find()) {
                        sub = normalizar(pm.group(1));
                        break;
                    }
                }
                String caminho = juntar(baseClasse, sub);
                for (String v : verbos) {
                    rotas.add(v + " " + caminho);
                }
            }
        }
        rotas.sort(Comparator.naturalOrder());
        return rotas;
    }

    @Test
    @DisplayName("Deve inventariar todas as rotas das classes *Resource e imprimir verbo + caminho")
    void deveInventariarRotas() throws IOException {
        Path base = diretorioFontes();
        List<String> rotas = inventariarRotas(base);

        System.out.println("===== INVENTARIO DE ROTAS JAX-RS (" + rotas.size() + " metodos) =====");
        rotas.forEach(System.out::println);
        System.out.println("===== FIM DO INVENTARIO =====");

        assertFalse(rotas.isEmpty(), "O inventário não pode ficar vazio (extração quebrada).");
        assertTrue(rotas.size() >= 100,
                "Esperado pelo menos 100 métodos (baseline R0 = 108). Encontrado: " + rotas.size());
    }

    @Test
    @DisplayName("Nenhuma classe *Resource pode ficar sem @Path de classe")
    void todoRecursoDeveTerPathDeClasse() throws IOException {
        Path base = diretorioFontes();
        List<String> semPath = new ArrayList<>();
        try (Stream<Path> s = Files.walk(base)) {
            for (Path p : s.filter(f -> f.getFileName().toString().endsWith("Resource.java")).toList()) {
                String conteudo = Files.readString(p);
                if (!PATH_CLASSE.matcher(conteudo).find()) {
                    semPath.add(p.getFileName().toString());
                }
            }
        }
        if (!semPath.isEmpty()) {
            fail("Recursos sem @Path de classe (ficariam fora do inventário/Anexo A): " + semPath);
        }
    }

    @Test
    @DisplayName("Rotas baseline de cronograma/blocos/disponibilidade devem constar do inventário")
    void rotasBaselineDevemEstarPresentes() throws IOException {
        List<String> rotas = inventariarRotas(diretorioFontes());
        List<String> obrigatorias = List.of(
                "GET /api/blocos/hoje",
                "PATCH /api/blocos/{blocoId}/conclusao",
                "GET /api/usuarios/me/disponibilidade-semanal",
                "PUT /api/usuarios/me/disponibilidade-semanal");
        for (String rota : obrigatorias) {
            assertTrue(rotas.contains(rota),
                    "Rota obrigatória ausente do inventário: " + rota + "\nInventário: " + rotas);
        }
    }
}
