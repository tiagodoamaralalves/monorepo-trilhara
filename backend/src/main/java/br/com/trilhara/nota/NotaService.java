package br.com.trilhara.nota;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.ConfigProvider;

import br.com.trilhara.nota.dto.AtualizarCadernoRequest;
import br.com.trilhara.nota.dto.AtualizarNotaRequest;
import br.com.trilhara.nota.dto.AtualizarSecaoRequest;
import br.com.trilhara.nota.dto.BuscaNotasResponse;
import br.com.trilhara.nota.dto.CadernoImpactoExclusaoResponse;
import br.com.trilhara.nota.dto.CadernoResponse;
import br.com.trilhara.nota.dto.CriarCadernoRequest;
import br.com.trilhara.nota.dto.CriarNotaRequest;
import br.com.trilhara.nota.dto.CriarSecaoRequest;
import br.com.trilhara.nota.dto.DestaqueDTO;
import br.com.trilhara.nota.dto.ExclusaoLixeiraResponse;
import br.com.trilhara.nota.dto.ItemLixeiraResponse;
import br.com.trilhara.nota.dto.LixeiraNotasResponse;
import br.com.trilhara.nota.dto.NotaDetalheResponse;
import br.com.trilhara.nota.dto.NotaMetadataResponse;
import br.com.trilhara.nota.dto.PaginaNotasResponse;
import br.com.trilhara.nota.dto.RealocacaoDestinoDTO;
import br.com.trilhara.nota.dto.RestauracaoLixeiraResponse;
import br.com.trilhara.nota.dto.ResultadoBuscaNotaDTO;
import br.com.trilhara.nota.dto.SecaoImpactoExclusaoResponse;
import br.com.trilhara.nota.dto.SecaoResponse;
import br.com.trilhara.nota.dto.SincronizacaoTrilhaResponse;
import br.com.trilhara.shared.erro.CodigoErro;
import br.com.trilhara.shared.erro.ProblemDetails;
import br.com.trilhara.shared.erro.ProblemDetailsFactory;
import br.com.trilhara.shared.erro.ResourceLimitExceededException;
import br.com.trilhara.shared.erro.ValidationException;
import br.com.trilhara.shared.mongo.BsonDates;
import br.com.trilhara.trilha.TrilhaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Serviço do domínio Notas (REQUISITOS §10, D-29 a D-34).
 *
 * <p>Três coleções independentes ({@code cadernos}, {@code secoes}, {@code notas}) com
 * {@code usuarioId} em todas elas. Nenhum documento embute listas de seções ou páginas.</p>
 *
 * <p>Regras transversais aplicadas aqui:</p>
 * <ul>
 *   <li>toda leitura/escrita valida {@code usuarioId} e o vínculo pai-filho do path: recurso de
 *       outro usuário, ID inexistente ou seção de outro caderno respondem 404;</li>
 *   <li>paginação por offset com {@code 400 INVALID_PAGINATION} ao sair do intervalo;</li>
 *   <li>limites de acervo respondem {@code 409 RESOURCE_LIMIT_EXCEEDED};</li>
 *   <li>restauração fora do prazo responde {@code 404 TRASH_ITEM_EXPIRED} (D-34).</li>
 * </ul>
 */
@ApplicationScoped
public class NotaService {

    private static final String TEXTO_0284C7 = "#0284c7";
    private static final String VALOR_CADERNO = "CADERNO";
    private static final String VALOR_SECAO = "SECAO";
    private static final String VALOR_NOTA = "NOTA";
    private static final String TITULO_PADRAO_CADERNO = "Caderno";
    private static final String TITULO_PADRAO_SECAO = "Seção";
    private static final String TITULO_RESTAURADAS = "Restauradas";
    private static final String TEXTO_CADERNO_NAO_ENCONTRADO = "Caderno não encontrado";
    private static final String TEXTO_SECAO_NAO_ENCONTRADA = "Seção não encontrada";
    private static final String TEXTO_PAGINA_NAO_ENCONTRADA = "Página não encontrada";

    /** Limite validado de Markdown por página (D-30d). */
    public static final int MAX_MARKDOWN_BYTES = 524288;

    /** Retenção da lixeira em dias (D-34). */
    public static final int DIAS_RETENCAO_LIXEIRA = 30;

    /** Tamanho padrão e máximo das listagens paginadas deste domínio. */
    public static final int TAMANHO_PADRAO_PAGINA = 50;
    public static final int TAMANHO_MAXIMO_PAGINA = 100;

    private final int maxCadernosPorUsuario;
    private final int maxSecoesPorCaderno;
    private final int maxPaginasPorSecao;

    private final CadernoRepository cadernoRepository;
    private final SecaoRepository secaoRepository;
    private final NotaRepository notaRepository;
    private final NotaSnippetExtractor snippetExtractor;
    private final NotaSincronizacaoService sincronizacaoService;
    private final TrilhaRepository trilhaRepository;

    @Inject
    public NotaService(
            CadernoRepository cadernoRepository,
            SecaoRepository secaoRepository,
            NotaRepository notaRepository,
            NotaSnippetExtractor snippetExtractor,
            NotaSincronizacaoService sincronizacaoService,
            TrilhaRepository trilhaRepository) {
        this.cadernoRepository = cadernoRepository;
        this.secaoRepository = secaoRepository;
        this.notaRepository = notaRepository;
        this.snippetExtractor = snippetExtractor;
        this.sincronizacaoService = sincronizacaoService;
        this.trilhaRepository = trilhaRepository;

        var config = ConfigProvider.getConfig();
        this.maxCadernosPorUsuario = config
                .getOptionalValue("trilhara.notas.max-cadernos-por-usuario", Integer.class)
                .orElse(500);
        this.maxSecoesPorCaderno = config
                .getOptionalValue("trilhara.notas.max-secoes-por-caderno", Integer.class)
                .orElse(500);
        this.maxPaginasPorSecao = config
                .getOptionalValue("trilhara.notas.max-paginas-por-secao", Integer.class)
                .orElse(2000);
    }

    // =========================================================================
    // 1. CADERNOS
    // =========================================================================

    /**
     * Cadernos ativos, opcionalmente filtrados pela trilha de origem (D-29).
     *
     * <p>A listagem é limitada a 500 itens e não pagina: é o teto de acervo do próprio usuário.
     * As contagens de seções e páginas vêm de uma agregação por coleção, não de uma consulta por
     * caderno.</p>
     */
    public List<CadernoResponse> listarCadernos(ObjectId usuarioId, ObjectId origemTrilhaId) {
        List<Caderno> cadernos = cadernoRepository.listarAtivos(
                usuarioId, origemTrilhaId, CadernoRepository.LIMITE_LISTAGEM);
        if (cadernos.isEmpty()) {
            return List.of();
        }

        List<ObjectId> ids = cadernos.stream().map(Caderno::id).toList();
        Map<ObjectId, Long> secoesPorCaderno = secaoRepository.contarPorCadernos(usuarioId, ids);
        Map<ObjectId, Long> paginasPorCaderno = notaRepository.contarPorCadernosEmLote(usuarioId, ids);
        Map<ObjectId, String> nomesTrilhas = resolverNomesTrilhas(usuarioId, cadernos);

        return cadernos.stream()
                .map(c -> paraCadernoResponse(
                        c,
                        secoesPorCaderno.getOrDefault(c.id(), 0L),
                        paginasPorCaderno.getOrDefault(c.id(), 0L),
                        c.origemTrilhaId() != null ? nomesTrilhas.get(c.origemTrilhaId()) : null))
                .toList();
    }

    public CadernoResponse buscarCaderno(ObjectId usuarioId, ObjectId cadernoId) {
        Caderno caderno = cadernoRepository.buscarAtivo(usuarioId, cadernoId)
                .orElseThrow(() -> new NotFoundException(TEXTO_CADERNO_NAO_ENCONTRADO));

        long totalSecoes = secaoRepository.contarPorCaderno(usuarioId, cadernoId);
        long totalPaginas = notaRepository.contarPorCaderno(usuarioId, cadernoId);
        return paraCadernoResponse(
                caderno, totalSecoes, totalPaginas, resolverNomeTrilha(usuarioId, caderno.origemTrilhaId()));
    }

    public CadernoResponse criarCaderno(ObjectId usuarioId, CriarCadernoRequest request) {
        String titulo = validarTitulo(request.titulo(), "O título do caderno é obrigatório.",
                "O título do caderno deve ter no máximo 120 caracteres.", 120);
        if (cadernoRepository.contarAtivos(usuarioId) >= maxCadernosPorUsuario) {
            throw new ResourceLimitExceededException(
                    "Limite de " + maxCadernosPorUsuario + " cadernos por usuário atingido.");
        }

        Instant agora = Instant.now();
        Caderno novo = new Caderno(
                new ObjectId(),
                usuarioId,
                titulo,
                corOuPadrao(request.cor()),
                iconeOuPadrao(request.icone()),
                null,
                null,
                null,
                null,
                agora,
                agora
        );
        cadernoRepository.inserir(novo);

        // Seção inicial "Geral": o caderno nasce utilizável, mas pode ficar com zero seções
        // ativas quando a última é movida para a lixeira (REQUISITOS §10.1).
        Secao secaoPadrao = new Secao(
                new ObjectId(),
                usuarioId,
                novo.id(),
                "Geral",
                0,
                null,
                null,
                null,
                null,
                agora,
                agora
        );
        secaoRepository.inserir(secaoPadrao);

        return paraCadernoResponse(novo, 1, 0, null);
    }

    public CadernoResponse atualizarCaderno(
            ObjectId usuarioId, ObjectId cadernoId, AtualizarCadernoRequest request) {
        Caderno existente = cadernoRepository.buscarAtivo(usuarioId, cadernoId)
                .orElseThrow(() -> new NotFoundException(TEXTO_CADERNO_NAO_ENCONTRADO));

        String titulo = request.titulo() != null && !request.titulo().isBlank()
                ? validarTitulo(request.titulo(), "O título do caderno é obrigatório.",
                        "O título do caderno deve ter no máximo 120 caracteres.", 120)
                : existente.titulo();

        Caderno atualizado = new Caderno(
                existente.id(),
                usuarioId,
                titulo,
                request.cor() != null && !request.cor().isBlank() ? request.cor() : existente.cor(),
                request.icone() != null && !request.icone().isBlank() ? request.icone() : existente.icone(),
                existente.origemTrilhaId(),
                existente.deletedAt(),
                existente.expiresAt(),
                existente.loteExclusaoId(),
                existente.criadoEm(),
                Instant.now()
        );
        cadernoRepository.atualizar(atualizado);

        long totalSecoes = secaoRepository.contarPorCaderno(usuarioId, cadernoId);
        long totalPaginas = notaRepository.contarPorCaderno(usuarioId, cadernoId);
        return paraCadernoResponse(
                atualizado, totalSecoes, totalPaginas,
                resolverNomeTrilha(usuarioId, atualizado.origemTrilhaId()));
    }

    /** Impacto da exclusão do caderno (D-33): somente itens ativos entram na conta. */
    public CadernoImpactoExclusaoResponse obterImpactoExclusaoCaderno(ObjectId usuarioId, ObjectId cadernoId) {
        Caderno caderno = cadernoRepository.buscarAtivo(usuarioId, cadernoId)
                .orElseThrow(() -> new NotFoundException(TEXTO_CADERNO_NAO_ENCONTRADO));

        long secoes = secaoRepository.contarPorCaderno(usuarioId, cadernoId);
        long paginas = notaRepository.contarPorCaderno(usuarioId, cadernoId);
        return new CadernoImpactoExclusaoResponse(caderno.idHex(), caderno.titulo(), secoes, paginas);
    }

    public ExclusaoLixeiraResponse deletarCaderno(ObjectId usuarioId, ObjectId cadernoId) {
        cadernoRepository.buscarAtivo(usuarioId, cadernoId)
                .orElseThrow(() -> new NotFoundException(TEXTO_CADERNO_NAO_ENCONTRADO));

        Instant agora = Instant.now();
        Instant expiresAt = agora.plus(DIAS_RETENCAO_LIXEIRA, ChronoUnit.DAYS);
        String loteExclusaoId = UUID.randomUUID().toString();

        long secoesAfetadas = secaoRepository.moverPorCadernoParaLixeira(
                usuarioId, cadernoId, loteExclusaoId, agora, expiresAt);
        long notasAfetadas = notaRepository.moverPorCadernoParaLixeira(
                usuarioId, cadernoId, loteExclusaoId, agora, expiresAt);
        cadernoRepository.moverParaLixeira(usuarioId, cadernoId, loteExclusaoId, agora, expiresAt);

        return new ExclusaoLixeiraResponse(
                cadernoId.toHexString(), VALOR_CADERNO, secoesAfetadas, notasAfetadas, loteExclusaoId);
    }

    // =========================================================================
    // 2. SEÇÕES
    // =========================================================================

    public List<SecaoResponse> listarSecoes(ObjectId usuarioId, ObjectId cadernoId) {
        cadernoRepository.buscarAtivo(usuarioId, cadernoId)
                .orElseThrow(() -> new NotFoundException(TEXTO_CADERNO_NAO_ENCONTRADO));

        List<Secao> secoes = secaoRepository.listarPorCaderno(
                usuarioId, cadernoId, SecaoRepository.LIMITE_LISTAGEM);
        if (secoes.isEmpty()) {
            return List.of();
        }

        List<ObjectId> ids = secoes.stream().map(Secao::id).toList();
        Map<ObjectId, Long> paginasPorSecao = notaRepository.contarPorSecoesEmLote(usuarioId, ids);
        return secoes.stream()
                .map(s -> paraSecaoResponse(s, paginasPorSecao.getOrDefault(s.id(), 0L)))
                .toList();
    }

    public SecaoResponse buscarSecao(ObjectId usuarioId, ObjectId secaoId) {
        Secao secao = secaoRepository.buscarAtiva(usuarioId, secaoId)
                .orElseThrow(() -> new NotFoundException(TEXTO_SECAO_NAO_ENCONTRADA));
        return paraSecaoResponse(secao, notaRepository.contarPorSecao(usuarioId, secaoId));
    }

    public SecaoResponse criarSecao(ObjectId usuarioId, ObjectId cadernoId, CriarSecaoRequest request) {
        cadernoRepository.buscarAtivo(usuarioId, cadernoId)
                .orElseThrow(() -> new NotFoundException(TEXTO_CADERNO_NAO_ENCONTRADO));

        String titulo = validarTitulo(request.titulo(), "O título da seção é obrigatório.",
                "O título da seção deve ter no máximo 120 caracteres.", 120);
        if (secaoRepository.contarAtivas(usuarioId, cadernoId) >= maxSecoesPorCaderno) {
            throw new ResourceLimitExceededException(
                    "Limite de " + maxSecoesPorCaderno + " seções por caderno atingido.");
        }

        Instant agora = Instant.now();
        Secao nova = new Secao(
                new ObjectId(),
                usuarioId,
                cadernoId,
                titulo,
                secaoRepository.proximaOrdem(usuarioId, cadernoId),
                null,
                null,
                null,
                null,
                agora,
                agora
        );
        secaoRepository.inserir(nova);
        return paraSecaoResponse(nova, 0);
    }

    public SecaoResponse atualizarSecao(
            ObjectId usuarioId, ObjectId secaoId, AtualizarSecaoRequest request) {
        Secao existente = secaoRepository.buscarAtiva(usuarioId, secaoId)
                .orElseThrow(() -> new NotFoundException(TEXTO_SECAO_NAO_ENCONTRADA));

        String titulo = request.titulo() != null && !request.titulo().isBlank()
                ? validarTitulo(request.titulo(), "O título da seção é obrigatório.",
                        "O título da seção deve ter no máximo 120 caracteres.", 120)
                : existente.titulo();

        Secao atualizada = new Secao(
                existente.id(),
                usuarioId,
                existente.cadernoId(),
                titulo,
                existente.ordem(),
                existente.origemDisciplinaId(),
                existente.deletedAt(),
                existente.expiresAt(),
                existente.loteExclusaoId(),
                existente.criadoEm(),
                Instant.now()
        );
        secaoRepository.atualizar(atualizada);
        return paraSecaoResponse(atualizada, notaRepository.contarPorSecao(usuarioId, secaoId));
    }

    /** Impacto da exclusão da seção (D-33): somente páginas ativas entram na conta. */
    public SecaoImpactoExclusaoResponse obterImpactoExclusaoSecao(ObjectId usuarioId, ObjectId secaoId) {
        Secao secao = secaoRepository.buscarAtiva(usuarioId, secaoId)
                .orElseThrow(() -> new NotFoundException(TEXTO_SECAO_NAO_ENCONTRADA));
        return new SecaoImpactoExclusaoResponse(
                secao.idHex(), secao.titulo(), notaRepository.contarPorSecao(usuarioId, secaoId));
    }

    public ExclusaoLixeiraResponse deletarSecao(ObjectId usuarioId, ObjectId secaoId) {
        secaoRepository.buscarAtiva(usuarioId, secaoId)
                .orElseThrow(() -> new NotFoundException(TEXTO_SECAO_NAO_ENCONTRADA));

        Instant agora = Instant.now();
        Instant expiresAt = agora.plus(DIAS_RETENCAO_LIXEIRA, ChronoUnit.DAYS);
        String loteExclusaoId = UUID.randomUUID().toString();

        long notasAfetadas = notaRepository.moverPorSecaoParaLixeira(
                usuarioId, secaoId, loteExclusaoId, agora, expiresAt);
        secaoRepository.moverParaLixeira(usuarioId, secaoId, loteExclusaoId, agora, expiresAt);
        return new ExclusaoLixeiraResponse(secaoId.toHexString(), VALOR_SECAO, 1, notasAfetadas, loteExclusaoId);
    }

    // =========================================================================
    // 3. PÁGINAS (NOTAS)
    // =========================================================================

    /**
     * Páginas de uma seção, paginadas e sem {@code conteudoMarkdown}.
     *
     * <p>A seção precisa pertencer ao caderno do path — um ID válido de seção de outro caderno
     * responde 404, nunca expõe conteúdo alheio ao caminho informado.</p>
     */
    public PaginaNotasResponse listarNotasMetadados(
            ObjectId usuarioId, ObjectId cadernoId, ObjectId secaoId, int pagina, int tamanho) {
        validarPaginacao(pagina, tamanho);
        secaoDoCaderno(usuarioId, cadernoId, secaoId);

        List<Nota> notas = notaRepository.listarMetadadosPorSecao(
                usuarioId, cadernoId, secaoId, pagina, tamanho);
        long total = notaRepository.contarPorSecao(usuarioId, secaoId);

        return new PaginaNotasResponse(
                notas.stream().map(this::paraNotaMetadataResponse).toList(),
                pagina,
                tamanho,
                total,
                totalPaginas(total, tamanho));
    }

    /** Endpoint canônico de leitura completa (D-32). */
    public NotaDetalheResponse buscarNota(ObjectId usuarioId, ObjectId notaId) {
        Nota nota = notaRepository.buscarAtiva(usuarioId, notaId)
                .orElseThrow(() -> new NotFoundException(TEXTO_PAGINA_NAO_ENCONTRADA));
        return paraNotaDetalheResponse(nota);
    }

    public NotaDetalheResponse criarNota(
            ObjectId usuarioId, ObjectId cadernoId, ObjectId secaoId, CriarNotaRequest request) {
        secaoDoCaderno(usuarioId, cadernoId, secaoId);

        String titulo = validarTitulo(request.titulo(), "O título da página é obrigatório.",
                "O título da página deve ter no máximo 200 caracteres.", 200);
        String markdown = request.conteudoMarkdown() != null ? request.conteudoMarkdown() : "";
        validarMarkdown(markdown);

        if (notaRepository.contarPorSecao(usuarioId, secaoId) >= maxPaginasPorSecao) {
            throw new ResourceLimitExceededException(
                    "Limite de " + maxPaginasPorSecao + " páginas por seção atingido.");
        }

        int ordem = request.ordem() != null
                ? request.ordem()
                : notaRepository.proximaOrdem(usuarioId, secaoId);
        Instant agora = Instant.now();

        Nota nova = new Nota(
                new ObjectId(),
                usuarioId,
                cadernoId,
                secaoId,
                titulo,
                markdown,
                ordem,
                request.topicoOrigemId(),
                null,
                null,
                null,
                agora,
                agora
        );
        notaRepository.inserir(nova);
        return paraNotaDetalheResponse(nova);
    }

    public NotaDetalheResponse atualizarNota(
            ObjectId usuarioId, ObjectId notaId, AtualizarNotaRequest request) {
        Nota existente = notaRepository.buscarAtiva(usuarioId, notaId)
                .orElseThrow(() -> new NotFoundException(TEXTO_PAGINA_NAO_ENCONTRADA));

        if (request.titulo() == null && request.conteudoMarkdown() == null && request.ordem() == null) {
            throw new ValidationException(
                    "titulo", "Pelo menos um campo deve ser fornecido para atualização da página.");
        }

        String titulo = request.titulo() != null
                ? validarTitulo(request.titulo(), "O título da página não pode ser vazio.",
                        "O título da página deve ter no máximo 200 caracteres.", 200)
                : existente.titulo();

        String markdown = request.conteudoMarkdown() != null
                ? request.conteudoMarkdown()
                : existente.conteudoMarkdown();
        validarMarkdown(markdown);

        Nota atualizada = new Nota(
                existente.id(),
                usuarioId,
                existente.cadernoId(),
                existente.secaoId(),
                titulo,
                markdown,
                request.ordem() != null ? request.ordem() : existente.ordem(),
                existente.topicoOrigemId(),
                existente.deletedAt(),
                existente.expiresAt(),
                existente.loteExclusaoId(),
                existente.criadoEm(),
                Instant.now()
        );
        notaRepository.atualizar(atualizada);
        return paraNotaDetalheResponse(atualizada);
    }

    public ExclusaoLixeiraResponse deletarNota(ObjectId usuarioId, ObjectId notaId) {
        notaRepository.buscarAtiva(usuarioId, notaId)
                .orElseThrow(() -> new NotFoundException(TEXTO_PAGINA_NAO_ENCONTRADA));

        Instant agora = Instant.now();
        Instant expiresAt = agora.plus(DIAS_RETENCAO_LIXEIRA, ChronoUnit.DAYS);
        String loteExclusaoId = UUID.randomUUID().toString();

        notaRepository.moverParaLixeira(usuarioId, notaId, loteExclusaoId, agora, expiresAt);
        return new ExclusaoLixeiraResponse(notaId.toHexString(), VALOR_NOTA, 0, 1, loteExclusaoId);
    }

    // =========================================================================
    // 4. BUSCA GLOBAL (D-30)
    // =========================================================================

    /**
     * Busca textual global sobre as páginas ativas do usuário.
     *
     * <p>Responde snippet em texto puro com offsets de destaque; nunca devolve o Markdown
     * completo nem gera HTML. A ordenação vem do repositório: relevância DESC,
     * {@code atualizadoEm} DESC, {@code _id} DESC.</p>
     */
    public BuscaNotasResponse buscarGlobal(ObjectId usuarioId, String termoBusca, int pagina, int tamanho) {
        snippetExtractor.validarQueryBusca(termoBusca);
        validarPaginacao(pagina, tamanho);

        List<Document> documentos = notaRepository.buscaGlobal(usuarioId, termoBusca, pagina, tamanho);
        long total = notaRepository.contarBuscaGlobal(usuarioId, termoBusca);

        Map<ObjectId, String> cadernoTitulos = new HashMap<>();
        Map<ObjectId, String> secaoTitulos = new HashMap<>();
        List<ResultadoBuscaNotaDTO> resultados = new ArrayList<>();

        for (Document documento : documentos) {
            ObjectId notaId = documento.getObjectId("_id");
            ObjectId cadernoId = documento.getObjectId("cadernoId");
            ObjectId secaoId = documento.getObjectId("secaoId");
            String titulo = documento.getString("titulo");

            String cadernoTitulo = cadernoTitulos.computeIfAbsent(cadernoId, id ->
                    cadernoRepository.buscarQualquerStatus(usuarioId, id)
                            .map(Caderno::titulo)
                            .orElse(TITULO_PADRAO_CADERNO));
            String secaoTitulo = secaoTitulos.computeIfAbsent(secaoId, id ->
                    secaoRepository.buscarQualquerStatus(usuarioId, id)
                            .map(Secao::titulo)
                            .orElse(TITULO_PADRAO_SECAO));

            NotaSnippetExtractor.SnippetBusca snippet = snippetExtractor.extrairSnippet(
                    documento.getString("conteudoMarkdown"), termoBusca, titulo);

            resultados.add(new ResultadoBuscaNotaDTO(
                    notaId != null ? notaId.toHexString() : null,
                    titulo,
                    cadernoId != null ? cadernoId.toHexString() : null,
                    cadernoTitulo,
                    secaoId != null ? secaoId.toHexString() : null,
                    secaoTitulo,
                    BsonDates.lerInstant(documento, "atualizadoEm"),
                    snippet.texto(),
                    snippet.destaques()
            ));
        }

        return new BuscaNotasResponse(resultados, pagina, tamanho, total, totalPaginas(total, tamanho));
    }

    // =========================================================================
    // 5. IMPORTAÇÃO E SINCRONIZAÇÃO (D-29)
    // =========================================================================

    public CadernoResponse importarTrilha(ObjectId usuarioId, ObjectId trilhaId, boolean reimportar) {
        if (cadernoRepository.contarAtivos(usuarioId) >= maxCadernosPorUsuario) {
            throw new ResourceLimitExceededException(
                    "Limite de " + maxCadernosPorUsuario + " cadernos por usuário atingido.");
        }
        return sincronizacaoService.importarTrilha(usuarioId, trilhaId, reimportar);
    }

    public SincronizacaoTrilhaResponse sincronizarTrilha(ObjectId usuarioId, ObjectId cadernoId) {
        return sincronizacaoService.sincronizarTrilha(usuarioId, cadernoId);
    }

    // =========================================================================
    // 6. LIXEIRA HIERÁRQUICA (D-34)
    // =========================================================================

    /**
     * Lixeira hierárquica paginada, ordenada por {@code deletedAt} DESC e {@code _id} DESC.
     *
     * <p>Os três tipos são lidos em páginas próprias e mesclados em memória apenas para a
     * página pedida — nunca a coleção inteira. O {@code breadcrumb} é resolvido aqui porque o
     * item isolado não carrega o contexto hierárquico que a UI precisa exibir.</p>
     */
    public LixeiraNotasResponse listarLixeira(ObjectId usuarioId, int pagina, int tamanho) {
        validarPaginacao(pagina, tamanho);
        Instant agora = Instant.now();

        long totalCadernos = cadernoRepository.contarLixeira(usuarioId, agora);
        long totalSecoes = secaoRepository.contarLixeira(usuarioId, agora);
        long totalNotas = notaRepository.contarLixeira(usuarioId, agora);
        long total = totalCadernos + totalSecoes + totalNotas;

        int deslocamento = (pagina - 1) * tamanho;
        int limitePorTipo = Math.min(deslocamento + tamanho, TAMANHO_MAXIMO_PAGINA * 100);
        List<ItemLixeiraResponse> itens = new ArrayList<>();

        // Cada conjunto já vem ordenado por deletedAt DESC. Para a página N basta carregar os
        // primeiros (deslocamento + tamanho) itens de cada tipo, mesclar e fatiar — o custo é
        // limitado pelo offset pedido, nunca pela coleção inteira.
        cadernoRepository.listarLixeira(usuarioId, agora, 1, limitePorTipo)
                .forEach(c -> itens.add(new ItemLixeiraResponse(
                        VALOR_CADERNO,
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
            return porData != 0 ? porData : b.id().compareTo(a.id());
        });

        List<ItemLixeiraResponse> paginaItens = deslocamento >= itens.size()
                ? List.of()
                : itens.subList(deslocamento, Math.min(itens.size(), deslocamento + tamanho));

        return new LixeiraNotasResponse(
                List.copyOf(paginaItens),
                pagina,
                tamanho,
                total,
                totalPaginas(total, tamanho));
    }

    /**
     * Restaura um item da lixeira conforme D-34.
     *
     * <p>Regras: item inexistente ou que não está na lixeira responde {@code 404 NOT_FOUND};
     * item além dos 30 dias responde {@code 404 TRASH_ITEM_EXPIRED}. A restauração levanta os
     * ancestrais que estavam na lixeira e, quando algum ancestral já foi purgado, realoca o item
     * para a seção/caderno "Restauradas" (com sufixo numérico quando o nome já existe).</p>
     */
    public RestauracaoLixeiraResponse restaurarItem(ObjectId usuarioId, String tipo, ObjectId id) {
        String tipoNormalizado = tipo != null ? tipo.trim().toUpperCase(Locale.ROOT) : "";
        Instant agora = Instant.now();

        return switch (tipoNormalizado) {
            case VALOR_CADERNO -> restaurarCaderno(usuarioId, id, agora);
            case VALOR_SECAO -> restaurarSecao(usuarioId, id, agora);
            case VALOR_NOTA -> restaurarNota(usuarioId, id, agora);
            default -> throw new ValidationException(
                    "tipo", "Tipo de item da lixeira inválido: " + tipo);
        };
    }

    private RestauracaoLixeiraResponse restaurarCaderno(ObjectId usuarioId, ObjectId cadernoId, Instant agora) {
        Caderno caderno = cadernoRepository.buscarQualquerStatus(usuarioId, cadernoId)
                .filter(c -> c.deletedAt() != null)
                .orElseThrow(() -> new NotFoundException(TEXTO_CADERNO_NAO_ENCONTRADO));
        validarPrazoLixeira(caderno.expiresAt(), agora);

        cadernoRepository.restaurar(usuarioId, cadernoId);
        if (caderno.loteExclusaoId() != null) {
            secaoRepository.restaurarPorLoteECaderno(usuarioId, cadernoId, caderno.loteExclusaoId());
            notaRepository.restaurarPorLoteECaderno(usuarioId, cadernoId, caderno.loteExclusaoId());
        }

        return new RestauracaoLixeiraResponse(
                "Caderno e itens associados restaurados com sucesso.",
                VALOR_CADERNO,
                cadernoId.toHexString(),
                cadernoId.toHexString(),
                null,
                false,
                null);
    }

    private RestauracaoLixeiraResponse restaurarSecao(ObjectId usuarioId, ObjectId secaoId, Instant agora) {
        Secao secao = secaoRepository.buscarQualquerStatus(usuarioId, secaoId)
                .filter(s -> s.deletedAt() != null)
                .orElseThrow(() -> new NotFoundException(TEXTO_SECAO_NAO_ENCONTRADA));
        validarPrazoLixeira(secao.expiresAt(), agora);

        Optional<Caderno> cadernoPai = cadernoRepository.buscarQualquerStatus(usuarioId, secao.cadernoId());
        boolean cadernoPurgado = cadernoPai.isEmpty()
                || estaPurgado(cadernoPai.get().deletedAt(), cadernoPai.get().expiresAt(), agora);

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
                    tituloDisponivel(secao.titulo(), secaoRepository.titulosAtivosPorCaderno(usuarioId, cadernoDestinoId)),
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
                VALOR_SECAO,
                secaoId.toHexString(),
                cadernoDestinoId.toHexString(),
                secaoId.toHexString(),
                ancestralRestaurado,
                realocadaPara);
    }

    private RestauracaoLixeiraResponse restaurarNota(ObjectId usuarioId, ObjectId notaId, Instant agora) {
        Nota nota = notaRepository.buscarQualquerStatus(usuarioId, notaId)
                .filter(n -> n.deletedAt() != null)
                .orElseThrow(() -> new NotFoundException(TEXTO_PAGINA_NAO_ENCONTRADA));
        validarPrazoLixeira(nota.expiresAt(), agora);

        Optional<Secao> secaoPai = secaoRepository.buscarQualquerStatus(usuarioId, nota.secaoId());
        Optional<Caderno> cadernoPai = cadernoRepository.buscarQualquerStatus(usuarioId, nota.cadernoId());

        boolean secaoPurgada = secaoPai.isEmpty()
                || estaPurgado(secaoPai.get().deletedAt(), secaoPai.get().expiresAt(), agora);
        boolean cadernoPurgado = cadernoPai.isEmpty()
                || estaPurgado(cadernoPai.get().deletedAt(), cadernoPai.get().expiresAt(), agora);

        boolean ancestralRestaurado = false;
        RealocacaoDestinoDTO realocadaPara = null;

        if (cadernoPurgado) {
            Caderno cadernoRestauradas = obterOuCriarCadernoRestauradas(usuarioId);
            Secao secaoRestauradas = obterOuCriarSecaoRestauradas(usuarioId, cadernoRestauradas.id());
            notaRepository.atualizar(notaComDestino(
                    nota, usuarioId, cadernoRestauradas.id(), secaoRestauradas.id()));
            realocadaPara = new RealocacaoDestinoDTO(
                    cadernoRestauradas.idHex(), secaoRestauradas.idHex());
        } else if (secaoPurgada) {
            // Caderno ativo/restaurável, seção purgada: "Restauradas" dentro do próprio caderno.
            if (cadernoPai.get().deletedAt() != null) {
                cadernoRepository.restaurar(usuarioId, cadernoPai.get().id());
                ancestralRestaurado = true;
            }
            Secao secaoRestauradas = obterOuCriarSecaoRestauradas(usuarioId, cadernoPai.get().id());
            notaRepository.atualizar(notaComDestino(
                    nota, usuarioId, cadernoPai.get().id(), secaoRestauradas.id()));
            realocadaPara = new RealocacaoDestinoDTO(
                    cadernoPai.get().idHex(), secaoRestauradas.idHex());
        } else {
            notaRepository.restaurar(usuarioId, notaId);
            if (cadernoPai.get().deletedAt() != null) {
                cadernoRepository.restaurar(usuarioId, cadernoPai.get().id());
                ancestralRestaurado = true;
            }
            if (secaoPai.get().deletedAt() != null) {
                secaoRepository.restaurar(usuarioId, secaoPai.get().id());
                ancestralRestaurado = true;
            }
        }

        return new RestauracaoLixeiraResponse(
                realocadaPara != null
                        ? "Página restaurada e movida para \"Restauradas\" porque a seção ou o caderno "
                                + "ancestral já foi excluído definitivamente."
                        : ancestralRestaurado
                                ? "Página e seus ancestrais restaurados com sucesso."
                                : "Página restaurada com sucesso.",
                VALOR_NOTA,
                notaId.toHexString(),
                realocadaPara != null ? realocadaPara.cadernoId() : nota.cadernoIdHex(),
                realocadaPara != null ? realocadaPara.secaoId() : nota.secaoIdHex(),
                ancestralRestaurado,
                realocadaPara);
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
                .filter(s -> s.titulo() != null && s.titulo().startsWith(TITULO_RESTAURADAS))
                .findFirst();
        if (existente.isPresent() && TITULO_RESTAURADAS.equals(existente.get().titulo())) {
            return existente.get();
        }

        Instant agora = Instant.now();
        Secao nova = new Secao(
                new ObjectId(),
                usuarioId,
                cadernoId,
                tituloDisponivel(TITULO_RESTAURADAS,
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
            if (TITULO_RESTAURADAS.equals(caderno.titulo())) {
                return caderno;
            }
        }

        Instant agora = Instant.now();
        Caderno novo = new Caderno(
                new ObjectId(),
                usuarioId,
                tituloDisponivel(TITULO_RESTAURADAS, cadernoRepository.titulosAtivos(usuarioId)),
                TEXTO_0284C7,
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

    /**
     * Resolve um título livre na coleção: "Restauradas", "Restauradas (2)", "Restauradas (3)"…
     * (D-34). O sufixo nunca é aplicado ao item original restaurado — só ao container novo.
     */
    static String tituloDisponivel(String desejado, java.util.Collection<String> ocupados) {
        if (!ocupados.contains(desejado)) {
            return desejado;
        }
        int sufixo = 2;
        while (ocupados.contains(desejado + " (" + sufixo + ")")) {
            sufixo++;
        }
        return desejado + " (" + sufixo + ")";
    }

    private boolean estaPurgado(Instant deletedAt, Instant expiresAt, Instant agora) {
        return deletedAt != null && expiresAt != null && expiresAt.isBefore(agora);
    }

    private void validarPrazoLixeira(Instant expiresAt, Instant agora) {
        if (expiresAt != null && expiresAt.isBefore(agora)) {
            throw lancarErroItemLixeiraExpirado();
        }
    }

    private WebApplicationException lancarErroItemLixeiraExpirado() {
        ProblemDetails problem = ProblemDetailsFactory.criar(
                CodigoErro.TRASH_ITEM_EXPIRED,
                "O item da lixeira expirou após o período de 30 dias ou não foi encontrado.",
                "/api/notas/lixeira"
        );
        return new WebApplicationException(
                Response.status(Response.Status.NOT_FOUND)
                        .type(ProblemDetails.MEDIA_TYPE)
                        .entity(problem)
                        .build());
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

    // =========================================================================
    // Auxiliares
    // =========================================================================

    private Secao secaoDoCaderno(ObjectId usuarioId, ObjectId cadernoId, ObjectId secaoId) {
        cadernoRepository.buscarAtivo(usuarioId, cadernoId)
                .orElseThrow(() -> new NotFoundException(TEXTO_CADERNO_NAO_ENCONTRADO));
        Secao secao = secaoRepository.buscarAtiva(usuarioId, secaoId)
                .orElseThrow(() -> new NotFoundException(TEXTO_SECAO_NAO_ENCONTRADA));
        if (!cadernoId.equals(secao.cadernoId())) {
            // Seção existe, mas não neste caderno: 404 sem revelar a existência.
            throw new NotFoundException(TEXTO_SECAO_NAO_ENCONTRADA);
        }
        return secao;
    }

    private String validarTitulo(String bruto, String obrigatorio, String excedido, int maximo) {
        if (bruto == null || bruto.isBlank()) {
            throw new ValidationException("titulo", obrigatorio);
        }
        String titulo = bruto.trim();
        if (titulo.length() > maximo) {
            throw new ValidationException("titulo", excedido);
        }
        return titulo;
    }

    /**
     * Valida o Markdown armazenado (D-30d): tamanho em bytes UTF-8 e ausência de caracteres de
     * controle, exceto tabulação, quebra de linha e retorno de carro.
     */
    void validarMarkdown(String markdown) {
        if (markdown == null) {
            return;
        }
        if (markdown.getBytes(StandardCharsets.UTF_8).length > MAX_MARKDOWN_BYTES) {
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

    private static void validarPaginacao(int pagina, int tamanho) {
        if (pagina < 1 || tamanho < 1 || tamanho > TAMANHO_MAXIMO_PAGINA) {
            ProblemDetails problem = ProblemDetailsFactory.criar(
                    CodigoErro.INVALID_PAGINATION,
                    "Parâmetros de paginação inválidos. 'pagina' deve ser >= 1 e 'tamanho' deve "
                            + "estar entre 1 e " + TAMANHO_MAXIMO_PAGINA + ".",
                    null);
            throw new WebApplicationException(
                    Response.status(problem.status())
                            .type(ProblemDetails.MEDIA_TYPE)
                            .entity(problem)
                            .build());
        }
    }

    private static int totalPaginas(long total, int tamanho) {
        return total == 0 ? 1 : (int) Math.ceil((double) total / tamanho);
    }

    private static String corOuPadrao(String cor) {
        return cor != null && !cor.isBlank() ? cor : TEXTO_0284C7;
    }

    private static String iconeOuPadrao(String icone) {
        return icone != null && !icone.isBlank() ? icone : "book-open";
    }

    /**
     * Nome da trilha vinculada, lido do documento de {@code trilhas}. Atrás do vínculo de
     * origem o nome pode ter mudado em Estudos; a pílula do caderno mostra sempre o valor atual.
     */
    private String resolverNomeTrilha(ObjectId usuarioId, ObjectId trilhaId) {
        if (trilhaId == null) {
            return null;
        }
        return trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                .map(doc -> doc.getString("nome"))
                .orElse(null);
    }

    private Map<ObjectId, String> resolverNomesTrilhas(ObjectId usuarioId, List<Caderno> cadernos) {
        Map<ObjectId, String> nomes = new HashMap<>();
        for (Caderno caderno : cadernos) {
            ObjectId trilhaId = caderno.origemTrilhaId();
            if (trilhaId != null && !nomes.containsKey(trilhaId)) {
                nomes.put(trilhaId, resolverNomeTrilha(usuarioId, trilhaId));
            }
        }
        return nomes;
    }

    private ItemLixeiraResponse paraItemLixeiraSecao(ObjectId usuarioId, Secao secao) {
        String cadernoTitulo = cadernoRepository.buscarQualquerStatus(usuarioId, secao.cadernoId())
                .map(Caderno::titulo)
                .orElse(TITULO_PADRAO_CADERNO);
        return new ItemLixeiraResponse(
                VALOR_SECAO,
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
                .orElse(TITULO_PADRAO_CADERNO);
        String secaoTitulo = secaoRepository.buscarQualquerStatus(usuarioId, nota.secaoId())
                .map(Secao::titulo)
                .orElse(TITULO_PADRAO_SECAO);
        return new ItemLixeiraResponse(
                VALOR_NOTA,
                nota.idHex(),
                nota.titulo(),
                cadernoTitulo + " › " + secaoTitulo,
                nota.cadernoIdHex(),
                nota.secaoIdHex(),
                nota.loteExclusaoId(),
                nota.deletedAt(),
                nota.expiresAt());
    }

    private CadernoResponse paraCadernoResponse(
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

    private SecaoResponse paraSecaoResponse(Secao secao, long totalPaginas) {
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

    private NotaMetadataResponse paraNotaMetadataResponse(Nota nota) {
        return new NotaMetadataResponse(
                nota.idHex(),
                nota.cadernoIdHex(),
                nota.secaoIdHex(),
                nota.titulo(),
                nota.ordem(),
                nota.topicoOrigemId(),
                nota.criadoEm(),
                nota.atualizadoEm());
    }

    private NotaDetalheResponse paraNotaDetalheResponse(Nota nota) {
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
}
