package br.com.trilhara.nota;


import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bson.Document;
import org.bson.types.ObjectId;

import br.com.trilhara.nota.dto.AtualizarCadernoRequest;
import br.com.trilhara.nota.dto.AtualizarNotaRequest;
import br.com.trilhara.nota.dto.AtualizarSecaoRequest;
import br.com.trilhara.nota.dto.BuscaNotasResponse;
import br.com.trilhara.nota.dto.CadernoResponse;
import br.com.trilhara.nota.dto.CriarCadernoRequest;
import br.com.trilhara.nota.dto.CriarNotaRequest;
import br.com.trilhara.nota.dto.CriarSecaoRequest;
import br.com.trilhara.nota.dto.ExclusaoLixeiraResponse;
import br.com.trilhara.nota.dto.ItemLixeiraResponse;
import br.com.trilhara.nota.dto.NotaDetalheResponse;
import br.com.trilhara.nota.dto.NotaMetadataResponse;
import br.com.trilhara.nota.dto.RestauracaoLixeiraResponse;
import br.com.trilhara.nota.dto.ResultadoBuscaNotaDTO;
import br.com.trilhara.nota.dto.SecaoResponse;
import br.com.trilhara.nota.dto.SincronizacaoTrilhaResponse;
import br.com.trilhara.shared.erro.CodigoErro;
import br.com.trilhara.shared.erro.ProblemDetails;
import br.com.trilhara.shared.erro.ValidationException;
import br.com.trilhara.shared.erro.ProblemDetailsFactory;
import br.com.trilhara.shared.mongo.BsonDates;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class NotaService {

    private static final String TEXTO_0284C7 = "#0284c7";
    private static final String VALOR_CADERNO = "CADERNO";
    private static final String TITULO_PADRAO_CADERNO = "Caderno";
    private static final String TEXTO_CADERNO_N_O_ENCONTRADO = "Caderno não encontrado";
    private static final String ERRO_PAGINA_NAO_ENCONTRADA = "Página não encontrada";
    private static final String TITULO_CADERNO_RESTAURADAS = "Restauradas";
    private static final String VALOR_SECAO = "SECAO";
    private static final String ERRO_SECAO_NAO_ENCONTRADA = "Seção não encontrada";

    public static final int MAX_MARKDOWN_BYTES = 524288; // 512 KB
    public static final int DIAS_RETENCAO_LIXEIRA = 30;

    private final CadernoRepository cadernoRepository;
    private final SecaoRepository secaoRepository;
    private final NotaRepository notaRepository;
    private final NotaSnippetExtractor snippetExtractor;
    private final NotaSincronizacaoService sincronizacaoService;

    @Inject
    public NotaService(
            CadernoRepository cadernoRepository,
            SecaoRepository secaoRepository,
            NotaRepository notaRepository,
            NotaSnippetExtractor snippetExtractor,
            NotaSincronizacaoService sincronizacaoService) {
        this.cadernoRepository = cadernoRepository;
        this.secaoRepository = secaoRepository;
        this.notaRepository = notaRepository;
        this.snippetExtractor = snippetExtractor;
        this.sincronizacaoService = sincronizacaoService;
    }

    // =========================================================================
    // 1. GESTÃO DE CADERNOS (Nível 1)
    // =========================================================================

    public List<CadernoResponse> listarCadernos(ObjectId usuarioId) {
        List<Caderno> cadernos = cadernoRepository.listarAtivos(usuarioId);
        List<CadernoResponse> resposta = new ArrayList<>();

        for (Caderno c : cadernos) {
            long totalSecoes = secaoRepository.contarPorCaderno(usuarioId, c.id());
            long totalNotas = notaRepository.contarPorCaderno(usuarioId, c.id());
            resposta.add(paraCadernoResponse(c, totalSecoes, totalNotas, null));
        }
        return resposta;
    }

    public CadernoResponse buscarCaderno(ObjectId usuarioId, ObjectId cadernoId) {
        Caderno c = cadernoRepository.buscarAtivo(usuarioId, cadernoId)
                .orElseThrow(() -> new NotFoundException(TEXTO_CADERNO_N_O_ENCONTRADO));

        List<Secao> secoes = secaoRepository.listarPorCaderno(usuarioId, cadernoId);
        List<SecaoResponse> secoesResponse = new ArrayList<>();
        for (Secao s : secoes) {
            long totalNotasSecao = notaRepository.contarPorSecao(usuarioId, s.id());
            secoesResponse.add(paraSecaoResponse(s, totalNotasSecao));
        }

        long totalNotas = notaRepository.contarPorCaderno(usuarioId, cadernoId);
        return paraCadernoResponse(c, secoes.size(), totalNotas, secoesResponse);
    }

    public CadernoResponse criarCaderno(ObjectId usuarioId, CriarCadernoRequest request) {
        if (request.titulo() == null || request.titulo().isBlank()) {
            throw new BadRequestException("O título do caderno é obrigatório.");
        }
        String titulo = request.titulo().trim();
        if (titulo.length() > 120) {
            throw new BadRequestException("O título do caderno deve ter no máximo 120 caracteres.");
        }

        Instant agora = Instant.now();
        Caderno novo = new Caderno(
                new ObjectId(),
                usuarioId,
                titulo,
                request.cor() != null && !request.cor().isBlank() ? request.cor() : TEXTO_0284C7,
                request.icone() != null && !request.icone().isBlank() ? request.icone() : "book-open",
                null,
                null,
                null,
                null,
                agora,
                agora
        );

        cadernoRepository.inserir(novo);

        // Cria seção padrão "Geral" para usabilidade imediata
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
        SecaoResponse secaoResp = paraSecaoResponse(secaoPadrao, 0);

        return paraCadernoResponse(novo, 1, 0, List.of(secaoResp));
    }

    public CadernoResponse atualizarCaderno(ObjectId usuarioId, ObjectId cadernoId, AtualizarCadernoRequest request) {
        Caderno existente = cadernoRepository.buscarAtivo(usuarioId, cadernoId)
                .orElseThrow(() -> new NotFoundException(TEXTO_CADERNO_N_O_ENCONTRADO));

        String titulo = request.titulo() != null && !request.titulo().isBlank()
                ? request.titulo().trim()
                : existente.titulo();

        if (titulo.length() > 120) {
            throw new BadRequestException("O título do caderno deve ter no máximo 120 caracteres.");
        }

        String cor = request.cor() != null && !request.cor().isBlank()
                ? request.cor()
                : existente.cor();

        String icone = request.icone() != null && !request.icone().isBlank()
                ? request.icone()
                : existente.icone();

        Instant agora = Instant.now();
        Caderno atualizado = new Caderno(
                existente.id(),
                usuarioId,
                titulo,
                cor,
                icone,
                existente.origemTrilhaId(),
                existente.deletedAt(),
                existente.expiresAt(),
                existente.loteExclusaoId(),
                existente.criadoEm(),
                agora
        );

        cadernoRepository.atualizar(atualizado);

        long totalSecoes = secaoRepository.contarPorCaderno(usuarioId, cadernoId);
        long totalNotas = notaRepository.contarPorCaderno(usuarioId, cadernoId);
        return paraCadernoResponse(atualizado, totalSecoes, totalNotas, null);
    }

    public ExclusaoLixeiraResponse deletarCaderno(ObjectId usuarioId, ObjectId cadernoId) {
        cadernoRepository.buscarAtivo(usuarioId, cadernoId)
                .orElseThrow(() -> new NotFoundException(TEXTO_CADERNO_N_O_ENCONTRADO));

        Instant agora = Instant.now();
        Instant expiresAt = agora.plus(DIAS_RETENCAO_LIXEIRA, ChronoUnit.DAYS);
        String loteExclusaoId = UUID.randomUUID().toString();

        long secoesAfetadas = secaoRepository.moverPorCadernoParaLixeira(usuarioId, cadernoId, loteExclusaoId, agora, expiresAt);
        long notasAfetadas = notaRepository.moverPorCadernoParaLixeira(usuarioId, cadernoId, loteExclusaoId, agora, expiresAt);
        cadernoRepository.moverParaLixeira(usuarioId, cadernoId, loteExclusaoId, agora, expiresAt);

        return new ExclusaoLixeiraResponse(cadernoId.toHexString(), VALOR_CADERNO, secoesAfetadas, notasAfetadas, loteExclusaoId);
    }

    // =========================================================================
    // 2. GESTÃO DE SEÇÕES (Nível 2)
    // =========================================================================

    public List<SecaoResponse> listarSecoes(ObjectId usuarioId, ObjectId cadernoId) {
        cadernoRepository.buscarAtivo(usuarioId, cadernoId)
                .orElseThrow(() -> new NotFoundException(TEXTO_CADERNO_N_O_ENCONTRADO));

        List<Secao> secoes = secaoRepository.listarPorCaderno(usuarioId, cadernoId);
        List<SecaoResponse> resultado = new ArrayList<>();
        for (Secao s : secoes) {
            long totalNotas = notaRepository.contarPorSecao(usuarioId, s.id());
            resultado.add(paraSecaoResponse(s, totalNotas));
        }
        return resultado;
    }

    public SecaoResponse buscarSecao(ObjectId usuarioId, ObjectId secaoId) {
        Secao s = secaoRepository.buscarAtiva(usuarioId, secaoId)
                .orElseThrow(() -> new NotFoundException(ERRO_SECAO_NAO_ENCONTRADA));
        long totalNotas = notaRepository.contarPorSecao(usuarioId, secaoId);
        return paraSecaoResponse(s, totalNotas);
    }

    public SecaoResponse criarSecao(ObjectId usuarioId, ObjectId cadernoId, CriarSecaoRequest request) {
        cadernoRepository.buscarAtivo(usuarioId, cadernoId)
                .orElseThrow(() -> new NotFoundException(TEXTO_CADERNO_N_O_ENCONTRADO));

        if (request.titulo() == null || request.titulo().isBlank()) {
            throw new BadRequestException("O título da seção é obrigatório.");
        }
        String titulo = request.titulo().trim();
        if (titulo.length() > 120) {
            throw new BadRequestException("O título da seção deve ter no máximo 120 caracteres.");
        }

        int ordem = secaoRepository.proximaOrdem(usuarioId, cadernoId);
        Instant agora = Instant.now();

        Secao nova = new Secao(
                new ObjectId(),
                usuarioId,
                cadernoId,
                titulo,
                ordem,
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

    public SecaoResponse atualizarSecao(ObjectId usuarioId, ObjectId secaoId, AtualizarSecaoRequest request) {
        Secao existente = secaoRepository.buscarAtiva(usuarioId, secaoId)
                .orElseThrow(() -> new NotFoundException(ERRO_SECAO_NAO_ENCONTRADA));

        String titulo = request.titulo() != null && !request.titulo().isBlank()
                ? request.titulo().trim()
                : existente.titulo();

        if (titulo.length() > 120) {
            throw new BadRequestException("O título da seção deve ter no máximo 120 caracteres.");
        }

        Instant agora = Instant.now();
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
                agora
        );

        secaoRepository.atualizar(atualizada);
        long totalNotas = notaRepository.contarPorSecao(usuarioId, secaoId);
        return paraSecaoResponse(atualizada, totalNotas);
    }

    public ExclusaoLixeiraResponse deletarSecao(ObjectId usuarioId, ObjectId secaoId) {
        secaoRepository.buscarAtiva(usuarioId, secaoId)
                .orElseThrow(() -> new NotFoundException(ERRO_SECAO_NAO_ENCONTRADA));

        Instant agora = Instant.now();
        Instant expiresAt = agora.plus(DIAS_RETENCAO_LIXEIRA, ChronoUnit.DAYS);
        String loteExclusaoId = UUID.randomUUID().toString();

        long notasAfetadas = notaRepository.moverPorSecaoParaLixeira(usuarioId, secaoId, loteExclusaoId, agora, expiresAt);
        secaoRepository.moverParaLixeira(usuarioId, secaoId, loteExclusaoId, agora, expiresAt);

        return new ExclusaoLixeiraResponse(secaoId.toHexString(), VALOR_SECAO, 1, notasAfetadas, loteExclusaoId);
    }

    // =========================================================================
    // 3. GESTÃO DE NOTAS / PÁGINAS (Nível 3)
    // =========================================================================

    public List<NotaMetadataResponse> listarNotasMetadados(ObjectId usuarioId, ObjectId cadernoId, ObjectId secaoId, int pagina, int tamanho) {
        secaoRepository.buscarAtiva(usuarioId, secaoId)
                .orElseThrow(() -> new NotFoundException(ERRO_SECAO_NAO_ENCONTRADA));

        List<Nota> notas = notaRepository.listarMetadadosPorSecao(usuarioId, cadernoId, secaoId, pagina, tamanho);
        return notas.stream().map(this::paraNotaMetadataResponse).toList();
    }

    public NotaDetalheResponse buscarNota(ObjectId usuarioId, ObjectId notaId) {
        Nota n = notaRepository.buscarAtiva(usuarioId, notaId)
                .orElseThrow(() -> new NotFoundException(ERRO_PAGINA_NAO_ENCONTRADA));
        return paraNotaDetalheResponse(n);
    }

    public NotaDetalheResponse criarNota(ObjectId usuarioId, ObjectId cadernoId, ObjectId secaoId, CriarNotaRequest request) {
        secaoRepository.buscarAtiva(usuarioId, secaoId)
                .orElseThrow(() -> new NotFoundException(ERRO_SECAO_NAO_ENCONTRADA));

        if (request.titulo() == null || request.titulo().isBlank()) {
            throw new BadRequestException("O título da página é obrigatório.");
        }
        String titulo = request.titulo().trim();
        if (titulo.length() > 200) {
            throw new BadRequestException("O título da página deve ter no máximo 200 caracteres.");
        }

        String markdown = request.conteudoMarkdown() != null ? request.conteudoMarkdown() : "";
        validarTamanhoMarkdown(markdown);

        int ordem = request.ordem() != null ? request.ordem() : notaRepository.proximaOrdem(usuarioId, secaoId);
        Instant agora = Instant.now();

        Nota nova = new Nota(
                new ObjectId(),
                usuarioId,
                cadernoId,
                secaoId,
                titulo,
                markdown,
                ordem,
                request.origemTopicoId(),
                null,
                null,
                null,
                agora,
                agora
        );

        notaRepository.inserir(nova);
        return paraNotaDetalheResponse(nova);
    }

    public NotaDetalheResponse atualizarNota(ObjectId usuarioId, ObjectId notaId, AtualizarNotaRequest request) {
        Nota existente = notaRepository.buscarAtiva(usuarioId, notaId)
                .orElseThrow(() -> new NotFoundException(ERRO_PAGINA_NAO_ENCONTRADA));

        if (request.titulo() == null && request.conteudoMarkdown() == null && request.ordem() == null) {
            throw new BadRequestException("Pelo menos um campo deve ser fornecido para atualização da página.");
        }

        String titulo = request.titulo() != null ? request.titulo().trim() : existente.titulo();
        if (titulo.isBlank()) {
            throw new BadRequestException("O título da página não pode ser vazio.");
        }
        if (titulo.length() > 200) {
            throw new BadRequestException("O título da página deve ter no máximo 200 caracteres.");
        }

        String markdown = request.conteudoMarkdown() != null ? request.conteudoMarkdown() : existente.conteudoMarkdown();
        validarTamanhoMarkdown(markdown);

        int ordem = request.ordem() != null ? request.ordem() : existente.ordem();
        Instant agora = Instant.now();

        Nota atualizada = new Nota(
                existente.id(),
                usuarioId,
                existente.cadernoId(),
                existente.secaoId(),
                titulo,
                markdown,
                ordem,
                existente.origemTopicoId(),
                existente.deletedAt(),
                existente.expiresAt(),
                existente.loteExclusaoId(),
                existente.criadoEm(),
                agora
        );

        notaRepository.atualizar(atualizada);
        return paraNotaDetalheResponse(atualizada);
    }

    public ExclusaoLixeiraResponse deletarNota(ObjectId usuarioId, ObjectId notaId) {
        notaRepository.buscarAtiva(usuarioId, notaId)
                .orElseThrow(() -> new NotFoundException(ERRO_PAGINA_NAO_ENCONTRADA));

        Instant agora = Instant.now();
        Instant expiresAt = agora.plus(DIAS_RETENCAO_LIXEIRA, ChronoUnit.DAYS);
        String loteExclusaoId = UUID.randomUUID().toString();

        notaRepository.moverParaLixeira(usuarioId, notaId, loteExclusaoId, agora, expiresAt);
        return new ExclusaoLixeiraResponse(notaId.toHexString(), "NOTA", 0, 1, loteExclusaoId);
    }

    // =========================================================================
    // 4. BUSCA GLOBAL NO BACKEND (Índice de Texto MongoDB PT-BR + Snippets)
    // =========================================================================

    public BuscaNotasResponse buscarGlobal(ObjectId usuarioId, String termoBusca, int pagina, int tamanho) {
        snippetExtractor.validarQueryBusca(termoBusca);

        int pag = Math.max(1, pagina);
        int tam = Math.clamp(tamanho, 1, 100);

        List<Document> docs = notaRepository.buscaGlobal(usuarioId, termoBusca, pag, tam);
        long total = notaRepository.contarBuscaGlobal(usuarioId, termoBusca);

        // Prepara títulos de cadernos e seções para breadcrumbs
        Map<ObjectId, String> cadernoTitulos = new HashMap<>();
        Map<ObjectId, String> secaoTitulos = new HashMap<>();

        List<ResultadoBuscaNotaDTO> resultados = new ArrayList<>();
        for (Document doc : docs) {
            ObjectId notaId = doc.getObjectId("_id");
            ObjectId cadernoId = doc.getObjectId("cadernoId");
            ObjectId secaoId = doc.getObjectId("secaoId");
            String titulo = doc.getString("titulo");
            String markdown = doc.getString("conteudoMarkdown");
            Instant atualizadoEm = BsonDates.lerInstant(doc, "atualizadoEm");

            String cadTitulo = cadernoTitulos.computeIfAbsent(cadernoId, cid -> {
                Optional<Caderno> c = cadernoRepository.buscarQualquerStatus(usuarioId, cid);
                return c.map(Caderno::titulo).orElse(TITULO_PADRAO_CADERNO);
            });

            String secTitulo = secaoTitulos.computeIfAbsent(secaoId, sid -> {
                Optional<Secao> s = secaoRepository.buscarQualquerStatus(usuarioId, sid);
                return s.map(Secao::titulo).orElse("Seção");
            });

            String snippet = snippetExtractor.extrairSnippet(markdown, termoBusca, titulo);

            resultados.add(new ResultadoBuscaNotaDTO(
                    notaId != null ? notaId.toHexString() : null,
                    titulo,
                    cadernoId != null ? cadernoId.toHexString() : null,
                    cadTitulo,
                    secaoId != null ? secaoId.toHexString() : null,
                    secTitulo,
                    atualizadoEm,
                    snippet
            ));
        }

        int totalPaginas = (int) Math.ceil((double) total / tam);
        return new BuscaNotasResponse(resultados, total, pag, tam, totalPaginas);
    }

    private void validarTamanhoMarkdown(String markdown) {
        if (markdown != null) {
            byte[] bytes = markdown.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_MARKDOWN_BYTES) {
                throw new ValidationException("conteudoMarkdown", "O conteúdo da página excede o limite máximo permitido de 512 KB.");
            }
        }
    }

    // =========================================================================
    // 5. IMPORTAÇÃO E SINCRONIZAÇÃO EXPLÍCITA COM ESTUDOS
    // =========================================================================

    public CadernoResponse importarTrilha(ObjectId usuarioId, ObjectId trilhaId) {
        return sincronizacaoService.importarTrilha(usuarioId, trilhaId);
    }

    public SincronizacaoTrilhaResponse sincronizarTrilha(ObjectId usuarioId, ObjectId cadernoId) {
        return sincronizacaoService.sincronizarTrilha(usuarioId, cadernoId);
    }

    // =========================================================================
    // 6. LIXEIRA HIERÁRQUICA DE 30 DIAS E PURGA
    // =========================================================================

    public List<ItemLixeiraResponse> listarLixeira(ObjectId usuarioId, int pagina, int tamanho) {
        Instant agora = Instant.now();
        List<Caderno> cadernos = cadernoRepository.listarLixeira(usuarioId, agora);
        List<Secao> secoes = secaoRepository.listarLixeira(usuarioId, agora);
        List<Nota> notas = notaRepository.listarLixeira(usuarioId, agora);

        List<ItemLixeiraResponse> itens = new ArrayList<>();

        itens.addAll(cadernos.stream().map(c -> new ItemLixeiraResponse(
                VALOR_CADERNO,
                c.idHex(),
                c.titulo(),
                c.loteExclusaoId(),
                c.idHex(),
                c.titulo(),
                null,
                null,
                c.deletedAt(),
                c.expiresAt()
        )).toList());

        for (Secao s : secoes) {
            String cadernoTitulo = cadernoRepository.buscarQualquerStatus(usuarioId, s.cadernoId())
                    .map(Caderno::titulo).orElse(TITULO_PADRAO_CADERNO);
            itens.add(new ItemLixeiraResponse(
                    VALOR_SECAO,
                    s.idHex(),
                    s.titulo(),
                    s.loteExclusaoId(),
                    s.cadernoIdHex(),
                    cadernoTitulo,
                    s.idHex(),
                    s.titulo(),
                    s.deletedAt(),
                    s.expiresAt()
            ));
        }

        for (Nota n : notas) {
            String cadernoTitulo = cadernoRepository.buscarQualquerStatus(usuarioId, n.cadernoId())
                    .map(Caderno::titulo).orElse(TITULO_PADRAO_CADERNO);
            String secaoTitulo = secaoRepository.buscarQualquerStatus(usuarioId, n.secaoId())
                    .map(Secao::titulo).orElse("Seção");

            itens.add(new ItemLixeiraResponse(
                    "NOTA",
                    n.idHex(),
                    n.titulo(),
                    n.loteExclusaoId(),
                    n.cadernoIdHex(),
                    cadernoTitulo,
                    n.secaoIdHex(),
                    secaoTitulo,
                    n.deletedAt(),
                    n.expiresAt()
            ));
        }

        // Ordena por deletedAt DESC
        itens.sort((a, b) -> {
            if (a.deletedAt() == null || b.deletedAt() == null) return 0;
            return b.deletedAt().compareTo(a.deletedAt());
        });

        int limit = Math.clamp(tamanho, 1, 100);
        int skip = Math.max(0, (pagina - 1) * limit);

        if (skip >= itens.size()) {
            return List.of();
        }
        return itens.subList(skip, Math.min(itens.size(), skip + limit));
    }

    public RestauracaoLixeiraResponse restaurarItem(ObjectId usuarioId, String tipo, ObjectId id) {
        String tipoNorm = tipo != null ? tipo.trim().toUpperCase() : "";
        Instant agora = Instant.now();

        switch (tipoNorm) {
            case VALOR_CADERNO:
                return restaurarCaderno(usuarioId, id, agora);
            case VALOR_SECAO:
                return restaurarSecao(usuarioId, id, agora);
            case "NOTA":
                return restaurarNota(usuarioId, id, agora);
            default:
                throw new BadRequestException("Tipo de item da lixeira inválido: " + tipo);
        }
    }

    private RestauracaoLixeiraResponse restaurarCaderno(ObjectId usuarioId, ObjectId cadernoId, Instant agora) {
        Caderno caderno = cadernoRepository.buscarQualquerStatus(usuarioId, cadernoId)
                .orElseThrow(this::lancarErroItemLixeiraExpirado);

        if (caderno.deletedAt() == null || (caderno.expiresAt() != null && caderno.expiresAt().isBefore(agora))) {
            throw lancarErroItemLixeiraExpirado();
        }

        cadernoRepository.restaurar(usuarioId, cadernoId);

        // Restaura somente descendentes que compartilham o mesmo loteExclusaoId
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
                false
        );
    }

    private RestauracaoLixeiraResponse restaurarSecao(ObjectId usuarioId, ObjectId secaoId, Instant agora) {
        Secao secao = secaoRepository.buscarQualquerStatus(usuarioId, secaoId)
                .orElseThrow(this::lancarErroItemLixeiraExpirado);

        if (secao.deletedAt() == null || (secao.expiresAt() != null && secao.expiresAt().isBefore(agora))) {
            throw lancarErroItemLixeiraExpirado();
        }

        boolean ancestralRestaurado = false;
        boolean realocado = false;
        ObjectId cadernoAlvoId = secao.cadernoId();

        Optional<Caderno> cadernoPai = cadernoRepository.buscarQualquerStatus(usuarioId, secao.cadernoId());
        if (cadernoPai.isEmpty()) {
            // Caderno pai já foi purgado definitivamente: realoca para caderno 'Restauradas'
            Caderno restauradas = obterOuCriarCadernoRestauradas(usuarioId);
            cadernoAlvoId = restauradas.id();
            realocado = true;
        } else if (cadernoPai.get().deletedAt() != null) {
            if (cadernoPai.get().expiresAt() != null && cadernoPai.get().expiresAt().isAfter(agora)) {
                // Caderno pai estava na lixeira: restaura automaticamente
                cadernoRepository.restaurar(usuarioId, cadernoPai.get().id());
                ancestralRestaurado = true;
            } else {
                Caderno restauradas = obterOuCriarCadernoRestauradas(usuarioId);
                cadernoAlvoId = restauradas.id();
                realocado = true;
            }
        }

        if (realocado) {
            Secao realocada = new Secao(
                    secao.id(),
                    usuarioId,
                    cadernoAlvoId,
                    secao.titulo(),
                    secaoRepository.proximaOrdem(usuarioId, cadernoAlvoId),
                    null,
                    null,
                    null,
                    null,
                    secao.criadoEm(),
                    Instant.now()
            );
            secaoRepository.atualizar(realocada);
        } else {
            secaoRepository.restaurar(usuarioId, secaoId);
        }

        // Restaura notas filhas da seção com o mesmo lote
        if (secao.loteExclusaoId() != null) {
            notaRepository.restaurarPorLoteESecao(usuarioId, secaoId, secao.loteExclusaoId());
        }

        String msg = resolverMensagemRestauracaoSecao(realocado, ancestralRestaurado);

        return new RestauracaoLixeiraResponse(
                msg,
                VALOR_SECAO,
                secaoId.toHexString(),
                cadernoAlvoId.toHexString(),
                secaoId.toHexString(),
                ancestralRestaurado,
                realocado
        );
    }

    private record DestinoRestauracao(
            ObjectId cadernoId,
            ObjectId secaoId,
            boolean ancestralRestaurado,
            boolean realocado) {
    }

    private DestinoRestauracao resolverDestinoRestauracao(ObjectId usuarioId, Nota nota, Instant agora) {
        Optional<Secao> secaoPai = secaoRepository.buscarQualquerStatus(usuarioId, nota.secaoId());
        Optional<Caderno> cadernoPai = cadernoRepository.buscarQualquerStatus(usuarioId, nota.cadernoId());

        boolean secaoPurgada = secaoPai.isEmpty()
                || (secaoPai.get().deletedAt() != null && secaoPai.get().expiresAt() != null && secaoPai.get().expiresAt().isBefore(agora));
        boolean cadernoPurgado = cadernoPai.isEmpty()
                || (cadernoPai.get().deletedAt() != null && cadernoPai.get().expiresAt() != null && cadernoPai.get().expiresAt().isBefore(agora));

        if (secaoPurgada || cadernoPurgado) {
            Caderno cadRestauradas = obterOuCriarCadernoRestauradas(usuarioId);
            Secao secRestauradas = obterOuCriarSecaoRestauradas(usuarioId, cadRestauradas.id());
            return new DestinoRestauracao(cadRestauradas.id(), secRestauradas.id(), false, true);
        }

        boolean ancestralRestaurado = false;
        if (cadernoPai.get().deletedAt() != null) {
            cadernoRepository.restaurar(usuarioId, cadernoPai.get().id());
            ancestralRestaurado = true;
        }
        if (secaoPai.get().deletedAt() != null) {
            secaoRepository.restaurar(usuarioId, secaoPai.get().id());
            ancestralRestaurado = true;
        }
        return new DestinoRestauracao(nota.cadernoId(), nota.secaoId(), ancestralRestaurado, false);
    }

    private RestauracaoLixeiraResponse restaurarNota(ObjectId usuarioId, ObjectId notaId, Instant agora) {
        Nota nota = notaRepository.buscarQualquerStatus(usuarioId, notaId)
                .orElseThrow(this::lancarErroItemLixeiraExpirado);

        if (nota.deletedAt() == null || (nota.expiresAt() != null && nota.expiresAt().isBefore(agora))) {
            throw lancarErroItemLixeiraExpirado();
        }

        DestinoRestauracao destino = resolverDestinoRestauracao(usuarioId, nota, agora);

        if (destino.realocado()) {
            Nota realocada = new Nota(
                    nota.id(),
                    usuarioId,
                    destino.cadernoId(),
                    destino.secaoId(),
                    nota.titulo(),
                    nota.conteudoMarkdown(),
                    notaRepository.proximaOrdem(usuarioId, destino.secaoId()),
                    nota.origemTopicoId(),
                    null,
                    null,
                    null,
                    nota.criadoEm(),
                    Instant.now()
            );
            notaRepository.atualizar(realocada);
        } else {
            notaRepository.restaurar(usuarioId, notaId);
        }

        String msg = resolverMensagemRestauracaoNota(destino.realocado(), destino.ancestralRestaurado());

        return new RestauracaoLixeiraResponse(
                msg,
                "NOTA",
                notaId.toHexString(),
                destino.cadernoId().toHexString(),
                destino.secaoId().toHexString(),
                destino.ancestralRestaurado(),
                destino.realocado()
        );
    }

    private String resolverMensagemRestauracaoSecao(boolean realocado, boolean ancestralRestaurado) {
        if (realocado) {
            return "Seção restaurada e movida para o caderno 'Restauradas' pois o caderno ancestral foi purgado.";
        }
        return ancestralRestaurado
                ? "Seção e caderno ancestral restaurados com sucesso."
                : "Seção restaurada com sucesso.";
    }

    private String resolverMensagemRestauracaoNota(boolean realocado, boolean ancestralRestaurado) {
        if (realocado) {
            return "Página restaurada e movida para a seção 'Restauradas' pois os ancestrais foram purgados.";
        }
        return ancestralRestaurado
                ? "Página e seus ancestrais restaurados com sucesso."
                : "Página restaurada com sucesso.";
    }

    private Caderno obterOuCriarCadernoRestauradas(ObjectId usuarioId) {
        List<Caderno> ativos = cadernoRepository.listarAtivos(usuarioId);
        for (Caderno c : ativos) {
            if (TITULO_CADERNO_RESTAURADAS.equalsIgnoreCase(c.titulo())) {
                return c;
            }
        }
        Instant agora = Instant.now();
        Caderno novo = new Caderno(
                new ObjectId(),
                usuarioId,
                TITULO_CADERNO_RESTAURADAS,
                TEXTO_0284C7,
                "archive",
                null,
                null,
                null,
                null,
                agora,
                agora
        );
        cadernoRepository.inserir(novo);
        return novo;
    }

    private Secao obterOuCriarSecaoRestauradas(ObjectId usuarioId, ObjectId cadernoId) {
        List<Secao> secoes = secaoRepository.listarPorCaderno(usuarioId, cadernoId);
        for (Secao s : secoes) {
            if (TITULO_CADERNO_RESTAURADAS.equalsIgnoreCase(s.titulo())) {
                return s;
            }
        }
        Instant agora = Instant.now();
        int ordem = secaoRepository.proximaOrdem(usuarioId, cadernoId);
        Secao nova = new Secao(
                new ObjectId(),
                usuarioId,
                cadernoId,
                TITULO_CADERNO_RESTAURADAS,
                ordem,
                null,
                null,
                null,
                null,
                agora,
                agora
        );
        secaoRepository.inserir(nova);
        return nova;
    }

    private WebApplicationException lancarErroItemLixeiraExpirado() {
        ProblemDetails problem = ProblemDetailsFactory.criar(
                CodigoErro.TRASH_ITEM_EXPIRED,
                "O item da lixeira expirou após o período de 30 dias ou não foi encontrado.",
                "/api/notas/lixeira/restaurar"
        );
        return new WebApplicationException(
                Response.status(Response.Status.NOT_FOUND)
                        .type(ProblemDetails.MEDIA_TYPE)
                        .entity(problem)
                        .build()
        );
    }

    public Map<String, Long> esvaziarLixeira(ObjectId usuarioId) {
        long cadernos = cadernoRepository.esvaziarLixeira(usuarioId);
        long secoes = secaoRepository.esvaziarLixeira(usuarioId);
        long notas = notaRepository.esvaziarLixeira(usuarioId);

        return Map.of(
                "cadernosRemovidos", cadernos,
                "secoesRemovidas", secoes,
                "notasRemovidas", notas
        );
    }

    public int purgarExpiradasGlobal(Instant agora) {
        long n = notaRepository.purgarExpirados(agora);
        long s = secaoRepository.purgarExpirados(agora);
        long c = cadernoRepository.purgarExpirados(agora);
        return (int) (n + s + c);
    }

    // =========================================================================
    // MAPEADORES DE DTO
    // =========================================================================

    private CadernoResponse paraCadernoResponse(Caderno c, long totalSecoes, long totalNotas, List<SecaoResponse> secoes) {
        return new CadernoResponse(
                c.idHex(),
                c.usuarioIdHex(),
                c.titulo(),
                c.cor(),
                c.icone(),
                c.origemTrilhaIdHex(),
                c.origemTrilhaId() != null,
                totalSecoes,
                totalNotas,
                secoes,
                c.criadoEm(),
                c.atualizadoEm()
        );
    }

    private SecaoResponse paraSecaoResponse(Secao s, long totalNotas) {
        return new SecaoResponse(
                s.idHex(),
                s.usuarioIdHex(),
                s.cadernoIdHex(),
                s.titulo(),
                s.ordem(),
                s.origemDisciplinaIdHex(),
                totalNotas,
                s.criadoEm(),
                s.atualizadoEm()
        );
    }

    private NotaMetadataResponse paraNotaMetadataResponse(Nota n) {
        return new NotaMetadataResponse(
                n.idHex(),
                n.usuarioIdHex(),
                n.cadernoIdHex(),
                n.secaoIdHex(),
                n.titulo(),
                n.ordem(),
                n.origemTopicoId(),
                n.criadoEm(),
                n.atualizadoEm()
        );
    }

    private NotaDetalheResponse paraNotaDetalheResponse(Nota n) {
        return new NotaDetalheResponse(
                n.idHex(),
                n.usuarioIdHex(),
                n.cadernoIdHex(),
                n.secaoIdHex(),
                n.titulo(),
                n.conteudoMarkdown(),
                n.ordem(),
                n.origemTopicoId(),
                n.criadoEm(),
                n.atualizadoEm()
        );
    }
}
