package br.com.trilhara.nota;

import java.text.Normalizer;
import java.util.Set;

import br.com.trilhara.shared.erro.CodigoErro;
import br.com.trilhara.shared.erro.ProblemDetails;
import br.com.trilhara.shared.erro.ProblemDetailsFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class NotaSnippetExtractor {

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

    public void validarQueryBusca(String termoBusca) {
        if (termoBusca == null || termoBusca.trim().isBlank()) {
            throw erroQueryInvalida();
        }

        String limpo = termoBusca.trim();
        if (limpo.length() < 2) {
            throw erroQueryInvalida();
        }

        String[] tokens = limpo.split("[\\s\\p{Punct}]+");
        boolean temTermoSignificativo = false;

        for (String token : tokens) {
            String t = normalizarTexto(token).toLowerCase().trim();
            if (t.length() >= 2 && !STOP_WORDS_PT.contains(t)) {
                temTermoSignificativo = true;
                break;
            }
        }

        if (!temTermoSignificativo) {
            throw erroQueryInvalida();
        }
    }

    public String extrairSnippet(String conteudoMarkdown, String termoBusca, String tituloNota) {
        if (termoBusca == null || termoBusca.isBlank()) {
            throw erroQueryInvalida();
        }

        if (conteudoMarkdown == null || conteudoMarkdown.isBlank()) {
            return tituloNota != null ? tituloNota : "";
        }

        String textoLimpo = limparMarkdown(conteudoMarkdown);
        if (textoLimpo.isBlank()) {
            return tituloNota != null ? tituloNota : "";
        }

        String[] tokens = termoBusca.trim().split("[\\s\\p{Punct}]+");
        String textoNormalizado = normalizarTexto(textoLimpo).toLowerCase();

        PosicaoToken pos = localizarPrimeiroToken(tokens, textoNormalizado);
        if (pos.indice() == -1) {
            int limite = Math.min(180, textoLimpo.length());
            String corte = textoLimpo.substring(0, limite).trim();
            return textoLimpo.length() > 180 ? corte + "..." : corte;
        }

        return construirJanelaSnippet(textoLimpo, pos.indice(), pos.tamanho());
    }

    private String limparMarkdown(String conteudoMarkdown) {
        return conteudoMarkdown
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("`[^`]*+`", " ")
                .replaceAll("#+\\s*", "")
                .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1")
                .replaceAll("[*_~>\\-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record PosicaoToken(int indice, int tamanho) {
    }

    private PosicaoToken localizarPrimeiroToken(String[] tokens, String textoNormalizado) {
        int primeiraPosicao = -1;
        int tokenLen = 0;

        for (String t : tokens) {
            String tNorm = normalizarTexto(t).toLowerCase();
            if (tNorm.length() >= 2) {
                int pos = textoNormalizado.indexOf(tNorm);
                if (pos != -1 && (primeiraPosicao == -1 || pos < primeiraPosicao)) {
                    primeiraPosicao = pos;
                    tokenLen = tNorm.length();
                }
            }
        }
        return new PosicaoToken(primeiraPosicao, tokenLen);
    }

    private String construirJanelaSnippet(String textoLimpo, int primeiraPosicao, int tokenLen) {
        int inicio = Math.clamp(primeiraPosicao - 60, 0, textoLimpo.length());
        int fim = Math.clamp(primeiraPosicao + tokenLen + 100, 0, textoLimpo.length());

        if (fim - inicio > 180) {
            fim = inicio + 180;
        }

        String snippet = textoLimpo.substring(inicio, fim).trim();
        if (inicio > 0) {
            snippet = "..." + snippet;
        }
        if (fim < textoLimpo.length()) {
            snippet = snippet + "...";
        }

        return snippet;
    }

    public String normalizarTexto(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private WebApplicationException erroQueryInvalida() {
        ProblemDetails problem = ProblemDetailsFactory.criar(
                CodigoErro.INVALID_SEARCH_QUERY,
                "A consulta de busca deve conter pelo menos um termo significativo com 2 ou mais caracteres (stop words isoladas não são pesquisáveis).",
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
