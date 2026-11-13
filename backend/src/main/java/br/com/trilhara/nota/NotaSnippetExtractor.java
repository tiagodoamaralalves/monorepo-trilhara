package br.com.trilhara.nota;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import br.com.trilhara.nota.dto.DestaqueDTO;
import br.com.trilhara.shared.erro.CodigoErro;
import br.com.trilhara.shared.erro.ProblemDetails;
import br.com.trilhara.shared.erro.ProblemDetailsFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Extração de snippet de busca de Notas (D-30).
 *
 * <p>O backend devolve <strong>texto puro</strong>: nunca gera tags, nunca escapa o conteúdo
 * do usuário e nunca retorna o Markdown completo. O realce é comunicado por
 * {@code destaques}, com offsets em unidades UTF-16 do próprio snippet — exatamente o que
 * {@code String.prototype.slice} consome no frontend, que renderiza nós de texto React e
 * {@code <mark>}.</p>
 *
 * <p>A comparação é insensível a caixa e acentuação sem destruir os offsets: cada trecho é
 * localizado no texto "dobrado" (NFD sem marcas, minúsculo) e a posição é traduzida de volta
 * para o índice original. A implementação anterior comparava com o texto normalizado e
 * fatiava o texto original, deslocando a janela em consultas acentuadas.</p>
 */
@ApplicationScoped
public class NotaSnippetExtractor {

    /** Tamanho máximo do snippet, incluindo as reticências (D-30a). */
    public static final int LIMITE_SNIPPET = 180;

    /** Contexto antes do primeiro termo encontrado. */
    private static final int CONTEXTO_ANTES = 60;

    private static final String RETICENCIAS = "...";

    private static final Set<String> STOP_WORDS_PT = Set.of(
            "de", "a", "o", "que", "e", "do", "da", "em", "um", "para", "com", "nao", "não",
            "uma", "os", "no", "se", "na", "por", "mais", "as", "dos", "como", "mas", "ao",
            "ele", "das", "à", "seu", "sua", "ou", "quando", "muito", "ja", "já", "eu",
            "tambem", "também", "so", "só", "pelo", "pela", "ate", "até", "isso", "ela", "entre",
            "depois", "sem", "mesmo", "aos", "seus", "quem", "nas", "me", "esse", "eles",
            "voce", "você", "essa", "num", "nem", "suas", "meu", "minha", "numa", "pelos",
            "elas", "qual", "nos", "nós", "lhe", "deles", "essas", "esses", "pelas", "este",
            "dele", "tu", "te", "voces", "vocês", "vos", "lhes", "meus", "minhas", "teu",
            "tua", "teus", "tuas", "nosso", "nossa", "nossos", "nossas", "dela", "delas",
            "esta", "estes", "estas", "aquele", "aquela", "aqueles", "aquelas", "isto", "aquilo"
    );

    /**
     * Snippet pronto para a resposta: texto puro e intervalos realçados.
     *
     * @param texto     trecho de texto puro, no máximo {@value #LIMITE_SNIPPET} caracteres
     * @param destaques intervalos {@code [inicio, fim)} em UTF-16 dentro de {@code texto}
     */
    public record SnippetBusca(String texto, List<DestaqueDTO> destaques) {
    }

    /** Valida a consulta de busca: ao menos um termo com 2+ caracteres não stop word. */
    public void validarQueryBusca(String termoBusca) {
        if (termoBusca == null || termoBusca.isBlank()) {
            throw erroQueryInvalida();
        }

        String limpo = termoBusca.trim();
        if (limpo.length() < 2) {
            throw erroQueryInvalida();
        }

        boolean temTermoSignificativo = false;
        for (String token : tokensPesquisaveis(limpo)) {
            if (!STOP_WORDS_PT.contains(token)) {
                temTermoSignificativo = true;
                break;
            }
        }

        if (!temTermoSignificativo) {
            throw erroQueryInvalida();
        }
    }

    /** Normaliza para comparação (sem acento, minúsculas). Não usar para fatiar texto. */
    public String normalizarTexto(String texto) {
        return dobrar(texto);
    }

    /**
     * Extrai o snippet e os destaques de uma página.
     *
     * @param conteudoMarkdown Markdown armazenado (nunca devolvido inteiro)
     * @param termoBusca       consulta já validada
     * @param tituloNota       fallback quando a página não tem conteúdo textual
     */
    public SnippetBusca extrairSnippet(String conteudoMarkdown, String termoBusca, String tituloNota) {
        if (termoBusca == null || termoBusca.isBlank()) {
            throw erroQueryInvalida();
        }

        String texto = limparMarkdown(conteudoMarkdown);
        if (texto.isBlank()) {
            String fallback = tituloNota != null ? tituloNota : "";
            return new SnippetBusca(truncarSimples(fallback), List.of());
        }

        List<int[]> ocorrencias = localizarOcorrencias(texto, tokensPesquisaveis(termoBusca));
        if (ocorrencias.isEmpty()) {
            return new SnippetBusca(truncarSimples(texto), List.of());
        }

        int primeiroInicio = ocorrencias.get(0)[0];
        int inicio = Math.clamp(primeiroInicio - CONTEXTO_ANTES, 0, texto.length());
        boolean temPrefixo = inicio > 0;
        int orcamento = LIMITE_SNIPPET
                - (temPrefixo ? RETICENCIAS.length() : 0)
                - RETICENCIAS.length();
        int fim = Math.min(texto.length(), inicio + Math.max(1, orcamento));
        boolean temSufixo = fim < texto.length();

        String trecho = texto.substring(inicio, fim);
        int brancoInicial = 0;
        while (brancoInicial < trecho.length() && Character.isWhitespace(trecho.charAt(brancoInicial))) {
            brancoInicial++;
        }
        int brancoFinal = 0;
        while (brancoFinal < trecho.length() - brancoInicial
                && Character.isWhitespace(trecho.charAt(trecho.length() - 1 - brancoFinal))) {
            brancoFinal++;
        }
        String corpo = trecho.substring(brancoInicial, trecho.length() - brancoFinal);

        String prefixo = temPrefixo ? RETICENCIAS : "";
        String sufixo = temSufixo ? RETICENCIAS : "";
        String snippet = prefixo + corpo + sufixo;

        int deslocamento = prefixo.length() - inicio - brancoInicial;
        List<DestaqueDTO> destaques = new ArrayList<>();
        for (int[] ocorrencia : ocorrencias) {
            int de = ocorrencia[0] + deslocamento;
            int ate = ocorrencia[1] + deslocamento;
            if (de >= prefixo.length() && ate <= prefixo.length() + corpo.length() && de < ate) {
                destaques.add(new DestaqueDTO(de, ate));
            }
        }

        return new SnippetBusca(snippet, List.copyOf(destaques));
    }

    // ---------------------------------------------------------------------
    // Internos
    // ---------------------------------------------------------------------

    private static String dobrar(String valor) {
        if (valor == null) {
            return "";
        }
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    /** Tokens da consulta sem pontuação; descarta vazios e repetidos preservando a ordem. */
    private static List<String> tokensPesquisaveis(String termoBusca) {
        Set<String> unicos = new LinkedHashSet<>();
        for (String bruto : termoBusca.trim().split("[\\s\\p{Punct}]+")) {
            String dobrado = dobrar(bruto);
            if (!dobrado.isBlank() && dobrado.length() >= 2) {
                unicos.add(dobrado);
            }
        }
        return List.copyOf(unicos);
    }

    /**
     * Localiza todos os termos no texto ignorando caixa e acentuação, devolvendo posições
     * <strong>do texto original</strong>. Cada caractere dobrado guarda o índice original de
     * onde veio, então termos acentuados não deslocam a janela.
     */
    private static List<int[]> localizarOcorrencias(String texto, List<String> tokens) {
        if (tokens.isEmpty()) {
            return List.of();
        }

        StringBuilder dobrado = new StringBuilder(texto.length());
        int[] mapa = new int[texto.length()];
        Map<Integer, Integer> fimPorInicio = new HashMap<>();

        int i = 0;
        while (i < texto.length()) {
            int pontoDeCodigo = texto.codePointAt(i);
            int largura = Character.charCount(pontoDeCodigo);
            String original = texto.substring(i, i + largura);
            String dobradoTrecho = dobrar(original);
            int inicioOriginal = i;
            for (int k = 0; k < dobradoTrecho.length(); k++) {
                mapa[dobrado.length()] = inicioOriginal;
                dobrado.append(dobradoTrecho.charAt(k));
            }
            fimPorInicio.put(inicioOriginal, inicioOriginal + largura);
            i += largura;
        }

        String alvo = dobrado.toString();
        List<int[]> encontrados = new ArrayList<>();
        for (String token : tokens) {
            int de = alvo.indexOf(token);
            while (de >= 0) {
                int inicioOriginal = mapa[de];
                int ultimoCharDobrado = de + token.length() - 1;
                int ultimoOriginal = mapa[ultimoCharDobrado];
                int fimOriginal = fimPorInicio.getOrDefault(ultimoOriginal, ultimoOriginal + 1);
                if (inicioOriginal < fimOriginal && fimOriginal <= texto.length()) {
                    encontrados.add(new int[] { inicioOriginal, fimOriginal });
                }
                de = alvo.indexOf(token, de + 1);
            }
        }

        encontrados.sort((a, b) -> Integer.compare(a[0], b[0]));
        return encontrados;
    }

    private String truncarSimples(String texto) {
        if (texto == null) {
            return "";
        }
        String limpo = texto.trim();
        if (limpo.length() <= LIMITE_SNIPPET) {
            return limpo;
        }
        return limpo.substring(0, LIMITE_SNIPPET - RETICENCIAS.length()).trim() + RETICENCIAS;
    }

    /**
     * Remove marcação do Markdown mantendo o texto legível. Não remove nem escapa caracteres
     * digitados pelo usuário: a resposta é texto puro e o frontend renderiza como nó de texto.
     */
    private String limparMarkdown(String conteudoMarkdown) {
        if (conteudoMarkdown == null) {
            return "";
        }
        return conteudoMarkdown
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("`[^`]*+`", " ")
                .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ")
                .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*[>*+-]\\s+", " ")
                .replaceAll("[*_~]", "")
                .replaceAll("\\|", " ")
                .replaceAll("[\\t\\n\\r]+", " ")
                .replaceAll(" {2,}", " ")
                .trim();
    }

    private WebApplicationException erroQueryInvalida() {
        ProblemDetails problem = ProblemDetailsFactory.criar(
                CodigoErro.INVALID_SEARCH_QUERY,
                "A consulta de busca deve conter pelo menos um termo significativo com 2 ou mais "
                        + "caracteres (stop words isoladas não são pesquisáveis).",
                "/api/notas/busca"
        );
        return new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                        .type(ProblemDetails.MEDIA_TYPE)
                        .entity(problem)
                        .build()
        );
    }
}
