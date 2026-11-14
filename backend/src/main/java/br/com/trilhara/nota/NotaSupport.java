package br.com.trilhara.nota;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.ConfigProvider;

import br.com.trilhara.nota.dto.CadernoResponse;
import br.com.trilhara.nota.dto.NotaDetalheResponse;
import br.com.trilhara.nota.dto.NotaMetadataResponse;
import br.com.trilhara.nota.dto.PaginaNotasResponse;
import br.com.trilhara.nota.dto.ResultadoBuscaNotaDTO;
import br.com.trilhara.nota.dto.SecaoResponse;
import br.com.trilhara.shared.erro.CodigoErro;
import br.com.trilhara.shared.erro.ProblemDetails;
import br.com.trilhara.shared.erro.ProblemDetailsFactory;
import br.com.trilhara.shared.erro.ValidationException;
import br.com.trilhara.shared.mongo.BsonDates;
import br.com.trilhara.trilha.TrilhaRepository;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Utilitários de validação, paginação e mapeamento DTO do domínio Notas.
 */
final class NotaSupport {

    static final String TEXTO_0284C7 = "#0284c7";
    static final String VALOR_CADERNO = "CADERNO";
    static final String VALOR_SECAO = "SECAO";
    static final String VALOR_NOTA = "NOTA";
    static final String TITULO_PADRAO_CADERNO = "Caderno";
    static final String TITULO_PADRAO_SECAO = "Seção";
    static final String TITULO_RESTAURADAS = "Restauradas";
    static final String TEXTO_CADERNO_NAO_ENCONTRADO = "Caderno não encontrado";
    static final String TEXTO_SECAO_NAO_ENCONTRADA = "Seção não encontrada";
    static final String TEXTO_PAGINA_NAO_ENCONTRADA = "Página não encontrada";

    private NotaSupport() {
    }

    static int lerIntConfig(String chave, int padrao) {
        return ConfigProvider.getConfig()
                .getOptionalValue(chave, Integer.class)
                .orElse(padrao);
    }

    static Instant calcularExpiracaoLixeira(Instant agora) {
        return agora.plus(NotaService.DIAS_RETENCAO_LIXEIRA, ChronoUnit.DAYS);
    }

    static String validarTitulo(String bruto, String obrigatorio, String excedido, int maximo) {
        if (bruto == null || bruto.isBlank()) {
            throw new ValidationException("titulo", obrigatorio);
        }
        String titulo = bruto.trim();
        if (titulo.length() > maximo) {
            throw new ValidationException("titulo", excedido);
        }
        return titulo;
    }

    static void validarMarkdown(String markdown) {
        if (markdown == null) {
            return;
        }
        if (markdown.getBytes(StandardCharsets.UTF_8).length > NotaService.MAX_MARKDOWN_BYTES) {
            throw new ValidationException(
                    "conteudoMarkdown", "O conteúdo da página excede o limite máximo permitido de 512 KB.");
        }
        for (int i = 0; i < markdown.length(); i++) {
            char caractere = markdown.charAt(i);
            if (caractere == '\t' || caractere == '\n' || caractere == '\r') {
                continue;
            }
            boolean controle = (caractere < 0x20) || (caractere >= 0x7F && caractere <= 0x9F);
            if (controle) {
                throw new ValidationException(
                        "conteudoMarkdown",
                        "O conteúdo da página contém caracteres de controle não permitidos.");
            }
        }
    }

    static void validarPaginacao(int pagina, int tamanho) {
        if (pagina < 1 || tamanho < 1 || tamanho > NotaService.TAMANHO_MAXIMO_PAGINA) {
            ProblemDetails problem = ProblemDetailsFactory.criar(
                    CodigoErro.INVALID_PAGINATION,
                    "Parâmetros de paginação inválidos. 'pagina' deve ser >= 1 e 'tamanho' deve "
                            + "estar entre 1 e " + NotaService.TAMANHO_MAXIMO_PAGINA + ".",
                    null);
            throw new WebApplicationException(
                    Response.status(problem.status())
                            .type(ProblemDetails.MEDIA_TYPE)
                            .entity(problem)
                            .build());
        }
    }

    static int totalPaginas(long total, int tamanho) {
        return total == 0 ? 1 : (int) Math.ceil((double) total / tamanho);
    }

    static String corOuPadrao(String cor) {
        return cor != null && !cor.isBlank() ? cor : TEXTO_0284C7;
    }

    static String iconeOuPadrao(String icone) {
        return icone != null && !icone.isBlank() ? icone : "book-open";
    }

    static String tituloDisponivel(String desejado, Collection<String> ocupados) {
        if (!ocupados.contains(desejado)) {
            return desejado;
        }
        int sufixo = 2;
        while (ocupados.contains(desejado + " (" + sufixo + ")")) {
            sufixo++;
        }
        return desejado + " (" + sufixo + ")";
    }

    static boolean estaPurgado(Instant deletedAt, Instant expiresAt, Instant agora) {
        return deletedAt != null && expiresAt != null && expiresAt.isBefore(agora);
    }

    static void validarPrazoLixeira(Instant expiresAt, Instant agora) {
        if (expiresAt != null && expiresAt.isBefore(agora)) {
            ProblemDetails problem = ProblemDetailsFactory.criar(
                    CodigoErro.TRASH_ITEM_EXPIRED,
                    "O item da lixeira expirou após o período de 30 dias ou não foi encontrado.",
                    "/api/notas/lixeira");
            throw new WebApplicationException(
                    Response.status(Response.Status.NOT_FOUND)
                            .type(ProblemDetails.MEDIA_TYPE)
                            .entity(problem)
                            .build());
        }
    }

    static String resolverNomeTrilha(TrilhaRepository trilhaRepository, ObjectId usuarioId, ObjectId trilhaId) {
        if (trilhaId == null) {
            return null;
        }
        return trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                .map(doc -> doc.getString("nome"))
                .orElse(null);
    }

    static Map<ObjectId, String> resolverNomesTrilhas(
            TrilhaRepository trilhaRepository, ObjectId usuarioId, List<Caderno> cadernos) {
        Map<ObjectId, String> nomes = new HashMap<>();
        for (Caderno caderno : cadernos) {
            ObjectId trilhaId = caderno.origemTrilhaId();
            if (trilhaId != null && !nomes.containsKey(trilhaId)) {
                nomes.put(trilhaId, resolverNomeTrilha(trilhaRepository, usuarioId, trilhaId));
            }
        }
        return nomes;
    }

    static CadernoResponse paraCadernoResponse(
            Caderno caderno, long totalSecoes, long totalPaginas, String trilhaNome) {
        return new CadernoResponse(
                caderno.idHex(),
                caderno.titulo(),
                caderno.cor(),
                caderno.icone(),
                caderno.origemTrilhaIdHex(),
                trilhaNome,
                totalSecoes,
                totalPaginas,
                caderno.criadoEm(),
                caderno.atualizadoEm());
    }

    static SecaoResponse paraSecaoResponse(Secao secao, long totalPaginas) {
        return new SecaoResponse(
                secao.idHex(),
                secao.cadernoIdHex(),
                secao.titulo(),
                secao.ordem(),
                secao.origemDisciplinaIdHex(),
                totalPaginas,
                secao.criadoEm(),
                secao.atualizadoEm());
    }

    static PaginaNotasResponse paraPaginaNotasResponse(
            List<Nota> notas, int pagina, int tamanho, long total) {
        List<NotaMetadataResponse> itens = notas.stream()
                .map(nota -> new NotaMetadataResponse(
                        nota.idHex(),
                        nota.cadernoIdHex(),
                        nota.secaoIdHex(),
                        nota.titulo(),
                        nota.ordem(),
                        nota.topicoOrigemId(),
                        nota.criadoEm(),
                        nota.atualizadoEm()))
                .toList();
        return new PaginaNotasResponse(itens, pagina, tamanho, total, totalPaginas(total, tamanho));
    }

    static NotaDetalheResponse paraNotaDetalheResponse(Nota nota) {
        return new NotaDetalheResponse(
                nota.idHex(),
                nota.cadernoIdHex(),
                nota.secaoIdHex(),
                nota.titulo(),
                nota.conteudoMarkdown(),
                nota.ordem(),
                nota.topicoOrigemId(),
                nota.criadoEm(),
                nota.atualizadoEm());
    }

    static ResultadoBuscaNotaDTO paraResultadoBusca(
            Document documento,
            String cadernoTitulo,
            String secaoTitulo,
            NotaSnippetExtractor snippetExtractor,
            String termoBusca) {
        ObjectId notaId = documento.getObjectId("_id");
        ObjectId cadernoId = documento.getObjectId("cadernoId");
        ObjectId secaoId = documento.getObjectId("secaoId");
        String titulo = documento.getString("titulo");
        var snippet = snippetExtractor.extrairSnippet(
                documento.getString("conteudoMarkdown"), termoBusca, titulo);
        return new ResultadoBuscaNotaDTO(
                notaId != null ? notaId.toHexString() : null,
                titulo,
                cadernoId != null ? cadernoId.toHexString() : null,
                cadernoTitulo,
                secaoId != null ? secaoId.toHexString() : null,
                secaoTitulo,
                BsonDates.lerInstant(documento, "atualizadoEm"),
                snippet.texto(),
                snippet.destaques());
    }
}
