package br.com.trilhara.nota;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bson.types.ObjectId;

import br.com.trilhara.nota.dto.AtualizarCadernoRequest;
import br.com.trilhara.nota.dto.AtualizarNotaRequest;
import br.com.trilhara.nota.dto.AtualizarSecaoRequest;
import br.com.trilhara.nota.dto.CadernoImpactoExclusaoResponse;
import br.com.trilhara.nota.dto.CadernoResponse;
import br.com.trilhara.nota.dto.CriarCadernoRequest;
import br.com.trilhara.nota.dto.CriarNotaRequest;
import br.com.trilhara.nota.dto.CriarSecaoRequest;
import br.com.trilhara.nota.dto.ExclusaoLixeiraResponse;
import br.com.trilhara.nota.dto.NotaDetalheResponse;
import br.com.trilhara.nota.dto.PaginaNotasResponse;
import br.com.trilhara.nota.dto.SecaoImpactoExclusaoResponse;
import br.com.trilhara.nota.dto.SecaoResponse;
import br.com.trilhara.nota.dto.SincronizacaoTrilhaResponse;
import br.com.trilhara.shared.erro.ResourceLimitExceededException;
import br.com.trilhara.shared.erro.ValidationException;
import br.com.trilhara.trilha.TrilhaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

/**
 * Serviço do domínio Notas (cadernos, seções, páginas e sincronização).
 *
 * <p>Três coleções independentes ({@code cadernos}, {@code secoes}, {@code notas}) com
 * {@code usuarioId} em todas elas. Nenhum documento embute listas de seções ou páginas.</p>
 */
@ApplicationScoped
public class NotaService {

    /** Limite validado de Markdown por página. */
    public static final int MAX_MARKDOWN_BYTES = 524288;

    /** Retenção da lixeira em dias. */
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
    private final NotaSincronizacaoService sincronizacaoService;
    private final TrilhaRepository trilhaRepository;

    @Inject
    public NotaService(
            CadernoRepository cadernoRepository,
            SecaoRepository secaoRepository,
            NotaRepository notaRepository,
            NotaSincronizacaoService sincronizacaoService,
            TrilhaRepository trilhaRepository) {
        this.cadernoRepository = cadernoRepository;
        this.secaoRepository = secaoRepository;
        this.notaRepository = notaRepository;
        this.sincronizacaoService = sincronizacaoService;
        this.trilhaRepository = trilhaRepository;

        this.maxCadernosPorUsuario = NotaSupport.lerIntConfig("trilhara.notas.max-cadernos-por-usuario", 500);
        this.maxSecoesPorCaderno = NotaSupport.lerIntConfig("trilhara.notas.max-secoes-por-caderno", 500);
        this.maxPaginasPorSecao = NotaSupport.lerIntConfig("trilhara.notas.max-paginas-por-secao", 2000);
    }

    // =========================================================================
    // 1. CADERNOS
    // =========================================================================

    public List<CadernoResponse> listarCadernos(ObjectId usuarioId, ObjectId origemTrilhaId) {
        List<Caderno> cadernos = cadernoRepository.listarAtivos(
                usuarioId, origemTrilhaId, CadernoRepository.LIMITE_LISTAGEM);
        if (cadernos.isEmpty()) {
            return List.of();
        }

        List<ObjectId> ids = cadernos.stream().map(Caderno::id).toList();
        Map<ObjectId, Long> secoesPorCaderno = secaoRepository.contarPorCadernos(usuarioId, ids);
        Map<ObjectId, Long> paginasPorCaderno = notaRepository.contarPorCadernosEmLote(usuarioId, ids);
        Map<ObjectId, String> nomesTrilhas = NotaSupport.resolverNomesTrilhas(
                trilhaRepository, usuarioId, cadernos);

        return cadernos.stream()
                .map(c -> NotaSupport.paraCadernoResponse(
                        c,
                        secoesPorCaderno.getOrDefault(c.id(), 0L),
                        paginasPorCaderno.getOrDefault(c.id(), 0L),
                        c.origemTrilhaId() != null ? nomesTrilhas.get(c.origemTrilhaId()) : null))
                .toList();
    }

    public CadernoResponse buscarCaderno(ObjectId usuarioId, ObjectId cadernoId) {
        Caderno caderno = cadernoRepository.buscarAtivo(usuarioId, cadernoId)
                .orElseThrow(() -> new NotFoundException(NotaSupport.TEXTO_CADERNO_NAO_ENCONTRADO));

        long totalSecoes = secaoRepository.contarPorCaderno(usuarioId, cadernoId);
        long totalPaginas = notaRepository.contarPorCaderno(usuarioId, cadernoId);
        return NotaSupport.paraCadernoResponse(
                caderno,
                totalSecoes,
                totalPaginas,
                NotaSupport.resolverNomeTrilha(trilhaRepository, usuarioId, caderno.origemTrilhaId()));
    }

    public CadernoResponse criarCaderno(ObjectId usuarioId, CriarCadernoRequest request) {
        String titulo = NotaSupport.validarTitulo(
                request.titulo(),
                "O título do caderno é obrigatório.",
                "O título do caderno deve ter no máximo 120 caracteres.",
                120);
        if (cadernoRepository.contarAtivos(usuarioId) >= maxCadernosPorUsuario) {
            throw new ResourceLimitExceededException(
                    "Limite de " + maxCadernosPorUsuario + " cadernos por usuário atingido.");
        }

        Instant agora = Instant.now();
        Caderno novo = new Caderno(
                new ObjectId(),
                usuarioId,
                titulo,
                NotaSupport.corOuPadrao(request.cor()),
                NotaSupport.iconeOuPadrao(request.icone()),
                null,
                null,
                null,
                null,
                agora,
                agora);
        cadernoRepository.inserir(novo);

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
                agora);
        secaoRepository.inserir(secaoPadrao);

        return NotaSupport.paraCadernoResponse(novo, 1, 0, null);
    }

    public CadernoResponse atualizarCaderno(
            ObjectId usuarioId, ObjectId cadernoId, AtualizarCadernoRequest request) {
        Caderno existente = cadernoRepository.buscarAtivo(usuarioId, cadernoId)
                .orElseThrow(() -> new NotFoundException(NotaSupport.TEXTO_CADERNO_NAO_ENCONTRADO));

        String titulo = request.titulo() != null && !request.titulo().isBlank()
                ? NotaSupport.validarTitulo(
                        request.titulo(),
                        "O título do caderno é obrigatório.",
                        "O título do caderno deve ter no máximo 120 caracteres.",
                        120)
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
                Instant.now());
        cadernoRepository.atualizar(atualizado);

        long totalSecoes = secaoRepository.contarPorCaderno(usuarioId, cadernoId);
        long totalPaginas = notaRepository.contarPorCaderno(usuarioId, cadernoId);
        return NotaSupport.paraCadernoResponse(
                atualizado,
                totalSecoes,
                totalPaginas,
                NotaSupport.resolverNomeTrilha(trilhaRepository, usuarioId, atualizado.origemTrilhaId()));
    }

    public CadernoImpactoExclusaoResponse obterImpactoExclusaoCaderno(ObjectId usuarioId, ObjectId cadernoId) {
        Caderno caderno = cadernoRepository.buscarAtivo(usuarioId, cadernoId)
                .orElseThrow(() -> new NotFoundException(NotaSupport.TEXTO_CADERNO_NAO_ENCONTRADO));

        long secoes = secaoRepository.contarPorCaderno(usuarioId, cadernoId);
        long paginas = notaRepository.contarPorCaderno(usuarioId, cadernoId);
        return new CadernoImpactoExclusaoResponse(caderno.idHex(), caderno.titulo(), secoes, paginas);
    }

    public ExclusaoLixeiraResponse deletarCaderno(ObjectId usuarioId, ObjectId cadernoId) {
        cadernoRepository.buscarAtivo(usuarioId, cadernoId)
                .orElseThrow(() -> new NotFoundException(NotaSupport.TEXTO_CADERNO_NAO_ENCONTRADO));

        Instant agora = Instant.now();
        Instant expiresAt = NotaSupport.calcularExpiracaoLixeira(agora);
        String loteExclusaoId = UUID.randomUUID().toString();

        long secoesAfetadas = secaoRepository.moverPorCadernoParaLixeira(
                usuarioId, cadernoId, loteExclusaoId, agora, expiresAt);
        long notasAfetadas = notaRepository.moverPorCadernoParaLixeira(
                usuarioId, cadernoId, loteExclusaoId, agora, expiresAt);
        cadernoRepository.moverParaLixeira(usuarioId, cadernoId, loteExclusaoId, agora, expiresAt);

        return new ExclusaoLixeiraResponse(
                cadernoId.toHexString(), NotaSupport.VALOR_CADERNO, secoesAfetadas, notasAfetadas, loteExclusaoId);
    }

    // =========================================================================
    // 2. SEÇÕES
    // =========================================================================

    public List<SecaoResponse> listarSecoes(ObjectId usuarioId, ObjectId cadernoId) {
        cadernoRepository.buscarAtivo(usuarioId, cadernoId)
                .orElseThrow(() -> new NotFoundException(NotaSupport.TEXTO_CADERNO_NAO_ENCONTRADO));

        List<Secao> secoes = secaoRepository.listarPorCaderno(
                usuarioId, cadernoId, SecaoRepository.LIMITE_LISTAGEM);
        if (secoes.isEmpty()) {
            return List.of();
        }

        List<ObjectId> ids = secoes.stream().map(Secao::id).toList();
        Map<ObjectId, Long> paginasPorSecao = notaRepository.contarPorSecoesEmLote(usuarioId, ids);
        return secoes.stream()
                .map(s -> NotaSupport.paraSecaoResponse(s, paginasPorSecao.getOrDefault(s.id(), 0L)))
                .toList();
    }

    public SecaoResponse buscarSecao(ObjectId usuarioId, ObjectId secaoId) {
        Secao secao = secaoRepository.buscarAtiva(usuarioId, secaoId)
                .orElseThrow(() -> new NotFoundException(NotaSupport.TEXTO_SECAO_NAO_ENCONTRADA));
        return NotaSupport.paraSecaoResponse(secao, notaRepository.contarPorSecao(usuarioId, secaoId));
    }

    public SecaoResponse criarSecao(ObjectId usuarioId, ObjectId cadernoId, CriarSecaoRequest request) {
        cadernoRepository.buscarAtivo(usuarioId, cadernoId)
                .orElseThrow(() -> new NotFoundException(NotaSupport.TEXTO_CADERNO_NAO_ENCONTRADO));

        String titulo = NotaSupport.validarTitulo(
                request.titulo(),
                "O título da seção é obrigatório.",
                "O título da seção deve ter no máximo 120 caracteres.",
                120);
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
                agora);
        secaoRepository.inserir(nova);
        return NotaSupport.paraSecaoResponse(nova, 0);
    }

    public SecaoResponse atualizarSecao(
            ObjectId usuarioId, ObjectId secaoId, AtualizarSecaoRequest request) {
        Secao existente = secaoRepository.buscarAtiva(usuarioId, secaoId)
                .orElseThrow(() -> new NotFoundException(NotaSupport.TEXTO_SECAO_NAO_ENCONTRADA));

        String titulo = request.titulo() != null && !request.titulo().isBlank()
                ? NotaSupport.validarTitulo(
                        request.titulo(),
                        "O título da seção é obrigatório.",
                        "O título da seção deve ter no máximo 120 caracteres.",
                        120)
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
                Instant.now());
        secaoRepository.atualizar(atualizada);
        return NotaSupport.paraSecaoResponse(atualizada, notaRepository.contarPorSecao(usuarioId, secaoId));
    }

    public SecaoImpactoExclusaoResponse obterImpactoExclusaoSecao(ObjectId usuarioId, ObjectId secaoId) {
        Secao secao = secaoRepository.buscarAtiva(usuarioId, secaoId)
                .orElseThrow(() -> new NotFoundException(NotaSupport.TEXTO_SECAO_NAO_ENCONTRADA));
        return new SecaoImpactoExclusaoResponse(
                secao.idHex(), secao.titulo(), notaRepository.contarPorSecao(usuarioId, secaoId));
    }

    public ExclusaoLixeiraResponse deletarSecao(ObjectId usuarioId, ObjectId secaoId) {
        secaoRepository.buscarAtiva(usuarioId, secaoId)
                .orElseThrow(() -> new NotFoundException(NotaSupport.TEXTO_SECAO_NAO_ENCONTRADA));

        Instant agora = Instant.now();
        Instant expiresAt = NotaSupport.calcularExpiracaoLixeira(agora);
        String loteExclusaoId = UUID.randomUUID().toString();

        long notasAfetadas = notaRepository.moverPorSecaoParaLixeira(
                usuarioId, secaoId, loteExclusaoId, agora, expiresAt);
        secaoRepository.moverParaLixeira(usuarioId, secaoId, loteExclusaoId, agora, expiresAt);
        return new ExclusaoLixeiraResponse(
                secaoId.toHexString(), NotaSupport.VALOR_SECAO, 1, notasAfetadas, loteExclusaoId);
    }

    // =========================================================================
    // 3. PÁGINAS (NOTAS)
    // =========================================================================

    public PaginaNotasResponse listarNotasMetadados(
            ObjectId usuarioId, ObjectId cadernoId, ObjectId secaoId, int pagina, int tamanho) {
        NotaSupport.validarPaginacao(pagina, tamanho);
        secaoDoCaderno(usuarioId, cadernoId, secaoId);

        List<Nota> notas = notaRepository.listarMetadadosPorSecao(
                usuarioId, cadernoId, secaoId, pagina, tamanho);
        long total = notaRepository.contarPorSecao(usuarioId, secaoId);

        return NotaSupport.paraPaginaNotasResponse(notas, pagina, tamanho, total);
    }

    public NotaDetalheResponse buscarNota(ObjectId usuarioId, ObjectId notaId) {
        Nota nota = notaRepository.buscarAtiva(usuarioId, notaId)
                .orElseThrow(() -> new NotFoundException(NotaSupport.TEXTO_PAGINA_NAO_ENCONTRADA));
        return NotaSupport.paraNotaDetalheResponse(nota);
    }

    public NotaDetalheResponse criarNota(
            ObjectId usuarioId, ObjectId cadernoId, ObjectId secaoId, CriarNotaRequest request) {
        secaoDoCaderno(usuarioId, cadernoId, secaoId);

        String titulo = NotaSupport.validarTitulo(
                request.titulo(),
                "O título da página é obrigatório.",
                "O título da página deve ter no máximo 200 caracteres.",
                200);
        String markdown = request.conteudoMarkdown() != null ? request.conteudoMarkdown() : "";
        NotaSupport.validarMarkdown(markdown);

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
                agora);
        notaRepository.inserir(nova);
        return NotaSupport.paraNotaDetalheResponse(nova);
    }

    public NotaDetalheResponse atualizarNota(
            ObjectId usuarioId, ObjectId notaId, AtualizarNotaRequest request) {
        Nota existente = notaRepository.buscarAtiva(usuarioId, notaId)
                .orElseThrow(() -> new NotFoundException(NotaSupport.TEXTO_PAGINA_NAO_ENCONTRADA));

        if (request.titulo() == null && request.conteudoMarkdown() == null && request.ordem() == null) {
            throw new ValidationException(
                    "titulo", "Pelo menos um campo deve ser fornecido para atualização da página.");
        }

        String titulo = request.titulo() != null
                ? NotaSupport.validarTitulo(
                        request.titulo(),
                        "O título da página não pode ser vazio.",
                        "O título da página deve ter no máximo 200 caracteres.",
                        200)
                : existente.titulo();

        String markdown = request.conteudoMarkdown() != null
                ? request.conteudoMarkdown()
                : existente.conteudoMarkdown();
        NotaSupport.validarMarkdown(markdown);

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
                Instant.now());
        notaRepository.atualizar(atualizada);
        return NotaSupport.paraNotaDetalheResponse(atualizada);
    }

    public ExclusaoLixeiraResponse deletarNota(ObjectId usuarioId, ObjectId notaId) {
        notaRepository.buscarAtiva(usuarioId, notaId)
                .orElseThrow(() -> new NotFoundException(NotaSupport.TEXTO_PAGINA_NAO_ENCONTRADA));

        Instant agora = Instant.now();
        Instant expiresAt = NotaSupport.calcularExpiracaoLixeira(agora);
        String loteExclusaoId = UUID.randomUUID().toString();

        notaRepository.moverParaLixeira(usuarioId, notaId, loteExclusaoId, agora, expiresAt);
        return new ExclusaoLixeiraResponse(
                notaId.toHexString(), NotaSupport.VALOR_NOTA, 0, 1, loteExclusaoId);
    }

    // =========================================================================
    // 4. IMPORTAÇÃO E SINCRONIZAÇÃO
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

    private Secao secaoDoCaderno(ObjectId usuarioId, ObjectId cadernoId, ObjectId secaoId) {
        cadernoRepository.buscarAtivo(usuarioId, cadernoId)
                .orElseThrow(() -> new NotFoundException(NotaSupport.TEXTO_CADERNO_NAO_ENCONTRADO));
        Secao secao = secaoRepository.buscarAtiva(usuarioId, secaoId)
                .orElseThrow(() -> new NotFoundException(NotaSupport.TEXTO_SECAO_NAO_ENCONTRADA));
        if (!cadernoId.equals(secao.cadernoId())) {
            throw new NotFoundException(NotaSupport.TEXTO_SECAO_NAO_ENCONTRADA);
        }
        return secao;
    }
}
