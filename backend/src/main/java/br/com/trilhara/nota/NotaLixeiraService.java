package br.com.trilhara.nota;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.bson.Document;
import org.bson.types.ObjectId;

import br.com.trilhara.nota.dto.BuscaNotasResponse;
import br.com.trilhara.nota.dto.ItemLixeiraResponse;
import br.com.trilhara.nota.dto.LixeiraNotasResponse;
import br.com.trilhara.nota.dto.RealocacaoDestinoDTO;
import br.com.trilhara.nota.dto.RestauracaoLixeiraResponse;
import br.com.trilhara.nota.dto.ResultadoBuscaNotaDTO;
import br.com.trilhara.shared.erro.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

/**
 * Busca global e operações de lixeira hierárquica do domínio Notas.
 */
@ApplicationScoped
public class NotaLixeiraService {

    private final CadernoRepository cadernoRepository;
    private final SecaoRepository secaoRepository;
    private final NotaRepository notaRepository;
    private final NotaSnippetExtractor snippetExtractor;

    @Inject
    public NotaLixeiraService(
            CadernoRepository cadernoRepository,
            SecaoRepository secaoRepository,
            NotaRepository notaRepository,
            NotaSnippetExtractor snippetExtractor) {
        this.cadernoRepository = cadernoRepository;
        this.secaoRepository = secaoRepository;
        this.notaRepository = notaRepository;
        this.snippetExtractor = snippetExtractor;
    }

    /**
     * Busca textual global sobre as páginas ativas do usuário.
     */
    public BuscaNotasResponse buscarGlobal(ObjectId usuarioId, String termoBusca, int pagina, int tamanho) {
        snippetExtractor.validarQueryBusca(termoBusca);
        NotaSupport.validarPaginacao(pagina, tamanho);

        List<Document> documentos = notaRepository.buscaGlobal(usuarioId, termoBusca, pagina, tamanho);
        long total = notaRepository.contarBuscaGlobal(usuarioId, termoBusca);

        Map<ObjectId, String> cadernoTitulos = new HashMap<>();
        Map<ObjectId, String> secaoTitulos = new HashMap<>();
        List<ResultadoBuscaNotaDTO> resultados = new ArrayList<>();

        for (Document documento : documentos) {
            ObjectId cadernoId = documento.getObjectId("cadernoId");
            ObjectId secaoId = documento.getObjectId("secaoId");

            String cadernoTitulo = cadernoTitulos.computeIfAbsent(cadernoId, id ->
                    cadernoRepository.buscarQualquerStatus(usuarioId, id)
                            .map(Caderno::titulo)
                            .orElse(NotaSupport.TITULO_PADRAO_CADERNO));
            String secaoTitulo = secaoTitulos.computeIfAbsent(secaoId, id ->
                    secaoRepository.buscarQualquerStatus(usuarioId, id)
                            .map(Secao::titulo)
                            .orElse(NotaSupport.TITULO_PADRAO_SECAO));

            resultados.add(NotaSupport.paraResultadoBusca(
                    documento, cadernoTitulo, secaoTitulo, snippetExtractor, termoBusca));
        }

        return new BuscaNotasResponse(
                resultados, pagina, tamanho, total, NotaSupport.totalPaginas(total, tamanho));
    }

    /**
     * Lixeira hierárquica paginada, ordenada por {@code deletedAt} DESC e {@code _id} DESC.
     */
    public LixeiraNotasResponse listarLixeira(ObjectId usuarioId, int pagina, int tamanho) {
        NotaSupport.validarPaginacao(pagina, tamanho);
        Instant agora = Instant.now();

        long totalCadernos = cadernoRepository.contarLixeira(usuarioId, agora);
        long totalSecoes = secaoRepository.contarLixeira(usuarioId, agora);
        long totalNotas = notaRepository.contarLixeira(usuarioId, agora);
        long total = totalCadernos + totalSecoes + totalNotas;

        int deslocamento = (pagina - 1) * tamanho;
        int limitePorTipo = Math.min(deslocamento + tamanho, NotaService.TAMANHO_MAXIMO_PAGINA * 100);
        List<ItemLixeiraResponse> itens = new ArrayList<>();

        cadernoRepository.listarLixeira(usuarioId, agora, 1, limitePorTipo)
                .forEach(c -> itens.add(new ItemLixeiraResponse(
                        NotaSupport.VALOR_CADERNO,
                        c.idHex(),
                        c.titulo(),
                        "",
                        c.idHex(),
                        null,
                        c.loteExclusaoId(),
                        c.deletedAt(),
                        c.expiresAt())));
        secaoRepository.listarLixeira(usuarioId, agora, 1, limitePorTipo)
                .forEach(s -> itens.add(paraItemLixeiraSecao(usuarioId, s)));
        notaRepository.listarLixeira(usuarioId, agora, 1, limitePorTipo)
                .forEach(n -> itens.add(paraItemLixeiraNota(usuarioId, n)));

        itens.sort((a, b) -> {
            int porData = b.deletedAt().compareTo(a.deletedAt());
            return porData == 0 ? b.id().compareTo(a.id()) : porData;
        });

        List<ItemLixeiraResponse> paginaItens = deslocamento >= itens.size()
                ? List.of()
                : itens.subList(deslocamento, Math.min(itens.size(), deslocamento + tamanho));

        return new LixeiraNotasResponse(
                List.copyOf(paginaItens),
                pagina,
                tamanho,
                total,
                NotaSupport.totalPaginas(total, tamanho));
    }

    public RestauracaoLixeiraResponse restaurarItem(ObjectId usuarioId, String tipo, ObjectId id) {
        String tipoNormalizado = tipo != null ? tipo.trim().toUpperCase(Locale.ROOT) : "";
        Instant agora = Instant.now();

        return switch (tipoNormalizado) {
            case NotaSupport.VALOR_CADERNO -> restaurarCaderno(usuarioId, id, agora);
            case NotaSupport.VALOR_SECAO -> restaurarSecao(usuarioId, id, agora);
            case NotaSupport.VALOR_NOTA -> restaurarNota(usuarioId, id, agora);
            default -> throw new ValidationException(
                    "tipo", "Tipo de item da lixeira inválido: " + tipo);
        };
    }

    private RestauracaoLixeiraResponse restaurarCaderno(ObjectId usuarioId, ObjectId cadernoId, Instant agora) {
        Caderno caderno = cadernoRepository.buscarQualquerStatus(usuarioId, cadernoId)
                .filter(c -> c.deletedAt() != null)
                .orElseThrow(() -> new NotFoundException(NotaSupport.TEXTO_CADERNO_NAO_ENCONTRADO));
        NotaSupport.validarPrazoLixeira(caderno.expiresAt(), agora);

        cadernoRepository.restaurar(usuarioId, cadernoId);
        if (caderno.loteExclusaoId() != null) {
            secaoRepository.restaurarPorLoteECaderno(usuarioId, cadernoId, caderno.loteExclusaoId());
            notaRepository.restaurarPorLoteECaderno(usuarioId, cadernoId, caderno.loteExclusaoId());
        }

        return new RestauracaoLixeiraResponse(
                "Caderno e itens associados restaurados com sucesso.",
                NotaSupport.VALOR_CADERNO,
                cadernoId.toHexString(),
                cadernoId.toHexString(),
                null,
                false,
                null);
    }

    private RestauracaoLixeiraResponse restaurarSecao(ObjectId usuarioId, ObjectId secaoId, Instant agora) {
        Secao secao = secaoRepository.buscarQualquerStatus(usuarioId, secaoId)
                .filter(s -> s.deletedAt() != null)
                .orElseThrow(() -> new NotFoundException(NotaSupport.TEXTO_SECAO_NAO_ENCONTRADA));
        NotaSupport.validarPrazoLixeira(secao.expiresAt(), agora);

        Optional<Caderno> cadernoPai = cadernoRepository.buscarQualquerStatus(usuarioId, secao.cadernoId());
        boolean cadernoPurgado = cadernoPai.isEmpty()
                || NotaSupport.estaPurgado(cadernoPai.get().deletedAt(), cadernoPai.get().expiresAt(), agora);

        ObjectId cadernoDestinoId;
        RealocacaoDestinoDTO realocadaPara = null;
        boolean ancestralRestaurado = false;

        if (cadernoPurgado) {
            Caderno cadernoRestauradas = obterOuCriarCadernoRestauradas(usuarioId);
            cadernoDestinoId = cadernoRestauradas.id();
            Secao secaoRestauradas = obterOuCriarSecaoRestauradas(usuarioId, cadernoDestinoId);
            Secao movida = new Secao(
                    secao.id(),
                    usuarioId,
                    cadernoDestinoId,
                    NotaSupport.tituloDisponivel(
                            secao.titulo(),
                            secaoRepository.titulosAtivosPorCaderno(usuarioId, cadernoDestinoId)),
                    secaoRestauradas.ordem(),
                    null,
                    null,
                    null,
                    null,
                    secao.criadoEm(),
                    Instant.now());
            secaoRepository.atualizar(movida);
            realocadaPara = new RealocacaoDestinoDTO(
                    cadernoDestinoId.toHexString(), secaoRestauradas.idHex());
        } else {
            cadernoDestinoId = cadernoPai.get().id();
            secaoRepository.restaurar(usuarioId, secaoId);
            if (cadernoPai.get().deletedAt() != null) {
                cadernoRepository.restaurar(usuarioId, cadernoDestinoId);
                ancestralRestaurado = true;
            }
        }

        if (secao.loteExclusaoId() != null) {
            notaRepository.restaurarPorLoteESecao(usuarioId, secaoId, secao.loteExclusaoId());
        }

        return new RestauracaoLixeiraResponse(
                realocadaPara != null
                        ? "Seção restaurada e movida para o caderno \"Restauradas\" porque o caderno "
                                + "ancestral já foi excluído definitivamente."
                        : "Seção restaurada com sucesso.",
                NotaSupport.VALOR_SECAO,
                secaoId.toHexString(),
                cadernoDestinoId.toHexString(),
                secaoId.toHexString(),
                ancestralRestaurado,
                realocadaPara);
    }

    private RestauracaoLixeiraResponse restaurarNota(ObjectId usuarioId, ObjectId notaId, Instant agora) {
        Nota nota = notaRepository.buscarQualquerStatus(usuarioId, notaId)
                .filter(n -> n.deletedAt() != null)
                .orElseThrow(() -> new NotFoundException(NotaSupport.TEXTO_PAGINA_NAO_ENCONTRADA));
        NotaSupport.validarPrazoLixeira(nota.expiresAt(), agora);

        Optional<Secao> secaoPai = secaoRepository.buscarQualquerStatus(usuarioId, nota.secaoId());
        Optional<Caderno> cadernoPai = cadernoRepository.buscarQualquerStatus(usuarioId, nota.cadernoId());

        boolean secaoPurgada = secaoPai.isEmpty()
                || NotaSupport.estaPurgado(secaoPai.get().deletedAt(), secaoPai.get().expiresAt(), agora);
        boolean cadernoPurgado = cadernoPai.isEmpty()
                || NotaSupport.estaPurgado(cadernoPai.get().deletedAt(), cadernoPai.get().expiresAt(), agora);

        boolean ancestralRestaurado = false;
        RealocacaoDestinoDTO realocadaPara = null;

        if (cadernoPurgado) {
            realocadaPara = realocarNotaParaCadernoRestauradas(usuarioId, nota);
        } else if (secaoPurgada) {
            ancestralRestaurado = restaurarCadernoPaiSeNecessario(usuarioId, cadernoPai.get());
            realocadaPara = realocarNotaParaSecaoRestauradas(usuarioId, nota, cadernoPai.get().id());
        } else {
            ancestralRestaurado = restaurarNotaEAncestrais(usuarioId, notaId, cadernoPai.get(), secaoPai.get());
        }

        String mensagem = mensagemRestauracaoNota(realocadaPara != null, ancestralRestaurado);
        return new RestauracaoLixeiraResponse(
                mensagem,
                NotaSupport.VALOR_NOTA,
                notaId.toHexString(),
                realocadaPara != null ? realocadaPara.cadernoId() : nota.cadernoIdHex(),
                realocadaPara != null ? realocadaPara.secaoId() : nota.secaoIdHex(),
                ancestralRestaurado,
                realocadaPara);
    }

    private RealocacaoDestinoDTO realocarNotaParaCadernoRestauradas(ObjectId usuarioId, Nota nota) {
        Caderno cadernoRestauradas = obterOuCriarCadernoRestauradas(usuarioId);
        Secao secaoRestauradas = obterOuCriarSecaoRestauradas(usuarioId, cadernoRestauradas.id());
        notaRepository.atualizar(notaComDestino(
                nota, usuarioId, cadernoRestauradas.id(), secaoRestauradas.id()));
        return new RealocacaoDestinoDTO(cadernoRestauradas.idHex(), secaoRestauradas.idHex());
    }

    private RealocacaoDestinoDTO realocarNotaParaSecaoRestauradas(
            ObjectId usuarioId, Nota nota, ObjectId cadernoPaiId) {
        Secao secaoRestauradas = obterOuCriarSecaoRestauradas(usuarioId, cadernoPaiId);
        notaRepository.atualizar(notaComDestino(
                nota, usuarioId, cadernoPaiId, secaoRestauradas.id()));
        return new RealocacaoDestinoDTO(cadernoPaiId.toHexString(), secaoRestauradas.idHex());
    }

    private boolean restaurarCadernoPaiSeNecessario(ObjectId usuarioId, Caderno cadernoPai) {
        if (cadernoPai.deletedAt() != null) {
            cadernoRepository.restaurar(usuarioId, cadernoPai.id());
            return true;
        }
        return false;
    }

    private boolean restaurarNotaEAncestrais(
            ObjectId usuarioId, ObjectId notaId, Caderno cadernoPai, Secao secaoPai) {
        notaRepository.restaurar(usuarioId, notaId);
        boolean ancestralRestaurado = restaurarCadernoPaiSeNecessario(usuarioId, cadernoPai);
        if (secaoPai.deletedAt() != null) {
            secaoRepository.restaurar(usuarioId, secaoPai.id());
            ancestralRestaurado = true;
        }
        return ancestralRestaurado;
    }

    private static String mensagemRestauracaoNota(boolean realocada, boolean ancestralRestaurado) {
        if (realocada) {
            return "Página restaurada e movida para \"Restauradas\" porque a seção ou o caderno "
                    + "ancestral já foi excluído definitivamente.";
        }
        if (ancestralRestaurado) {
            return "Página e seus ancestrais restaurados com sucesso.";
        }
        return "Página restaurada com sucesso.";
    }

    private Nota notaComDestino(Nota nota, ObjectId usuarioId, ObjectId cadernoId, ObjectId secaoId) {
        return new Nota(
                nota.id(),
                usuarioId,
                cadernoId,
                secaoId,
                nota.titulo(),
                nota.conteudoMarkdown(),
                notaRepository.proximaOrdem(usuarioId, secaoId),
                nota.topicoOrigemId(),
                null,
                null,
                null,
                nota.criadoEm(),
                Instant.now());
    }

    private Secao obterOuCriarSecaoRestauradas(ObjectId usuarioId, ObjectId cadernoId) {
        Optional<Secao> existente = secaoRepository.listarPorCaderno(usuarioId, cadernoId)
                .stream()
                .filter(s -> s.titulo() != null && s.titulo().startsWith(NotaSupport.TITULO_RESTAURADAS))
                .findFirst();
        if (existente.isPresent() && NotaSupport.TITULO_RESTAURADAS.equals(existente.get().titulo())) {
            return existente.get();
        }

        Instant agora = Instant.now();
        Secao nova = new Secao(
                new ObjectId(),
                usuarioId,
                cadernoId,
                NotaSupport.tituloDisponivel(
                        NotaSupport.TITULO_RESTAURADAS,
                        secaoRepository.titulosAtivosPorCaderno(usuarioId, cadernoId)),
                secaoRepository.proximaOrdem(usuarioId, cadernoId),
                null,
                null,
                null,
                null,
                agora,
                agora);
        secaoRepository.inserir(nova);
        return nova;
    }

    private Caderno obterOuCriarCadernoRestauradas(ObjectId usuarioId) {
        for (Caderno caderno : cadernoRepository.listarAtivos(usuarioId)) {
            if (NotaSupport.TITULO_RESTAURADAS.equals(caderno.titulo())) {
                return caderno;
            }
        }

        Instant agora = Instant.now();
        Caderno novo = new Caderno(
                new ObjectId(),
                usuarioId,
                NotaSupport.tituloDisponivel(
                        NotaSupport.TITULO_RESTAURADAS,
                        cadernoRepository.titulosAtivos(usuarioId)),
                NotaSupport.TEXTO_0284C7,
                "archive",
                null,
                null,
                null,
                null,
                agora,
                agora);
        cadernoRepository.inserir(novo);
        return novo;
    }

    public Map<String, Long> esvaziarLixeira(ObjectId usuarioId) {
        long cadernos = cadernoRepository.esvaziarLixeira(usuarioId);
        long secoes = secaoRepository.esvaziarLixeira(usuarioId);
        long notas = notaRepository.esvaziarLixeira(usuarioId);
        return Map.of(
                "cadernosRemovidos", cadernos,
                "secoesRemovidas", secoes,
                "notasRemovidas", notas);
    }

    public int purgarExpiradasGlobal(Instant agora) {
        long notas = notaRepository.purgarExpirados(agora);
        long secoes = secaoRepository.purgarExpirados(agora);
        long cadernos = cadernoRepository.purgarExpirados(agora);
        return (int) (notas + secoes + cadernos);
    }

    private ItemLixeiraResponse paraItemLixeiraSecao(ObjectId usuarioId, Secao secao) {
        String cadernoTitulo = cadernoRepository.buscarQualquerStatus(usuarioId, secao.cadernoId())
                .map(Caderno::titulo)
                .orElse(NotaSupport.TITULO_PADRAO_CADERNO);
        return new ItemLixeiraResponse(
                NotaSupport.VALOR_SECAO,
                secao.idHex(),
                secao.titulo(),
                cadernoTitulo,
                secao.cadernoIdHex(),
                secao.idHex(),
                secao.loteExclusaoId(),
                secao.deletedAt(),
                secao.expiresAt());
    }

    private ItemLixeiraResponse paraItemLixeiraNota(ObjectId usuarioId, Nota nota) {
        String cadernoTitulo = cadernoRepository.buscarQualquerStatus(usuarioId, nota.cadernoId())
                .map(Caderno::titulo)
                .orElse(NotaSupport.TITULO_PADRAO_CADERNO);
        String secaoTitulo = secaoRepository.buscarQualquerStatus(usuarioId, nota.secaoId())
                .map(Secao::titulo)
                .orElse(NotaSupport.TITULO_PADRAO_SECAO);
        return new ItemLixeiraResponse(
                NotaSupport.VALOR_NOTA,
                nota.idHex(),
                nota.titulo(),
                cadernoTitulo + " › " + secaoTitulo,
                nota.cadernoIdHex(),
                nota.secaoIdHex(),
                nota.loteExclusaoId(),
                nota.deletedAt(),
                nota.expiresAt());
    }
}
