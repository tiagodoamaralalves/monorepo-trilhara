package br.com.trilhara.sessao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.MongoWriteException;

import br.com.trilhara.sessao.dto.AtualizarSessaoRequest;
import br.com.trilhara.sessao.dto.EstatisticasTrilhaDTO;
import br.com.trilhara.sessao.dto.PaginaSessoesDTO;
import br.com.trilhara.sessao.dto.RegistrarSessaoRequest;
import br.com.trilhara.sessao.dto.RemocaoSessoesDisciplinaResponse;
import br.com.trilhara.sessao.dto.SessaoDTO;
import br.com.trilhara.shared.TimezoneContext;
import br.com.trilhara.shared.UsuarioAtual;
import br.com.trilhara.shared.erro.CodigoErro;
import br.com.trilhara.shared.erro.DomainConflictException;
import br.com.trilhara.shared.erro.ProblemDetails;
import br.com.trilhara.shared.erro.ProblemDetailsFactory;
import br.com.trilhara.shared.mongo.BsonIds;
import br.com.trilhara.trilha.Disciplina;
import br.com.trilhara.trilha.DisciplinaRepository;
import br.com.trilhara.trilha.TrilhaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class SessaoService {

    public record RegistroResultado(SessaoDTO sessao, boolean criada) {}

    private final SessaoRepository repository;
    private final TrilhaRepository trilhaRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final UsuarioAtual usuarioAtual;
    private final TimezoneContext timezoneContext;

    public SessaoService(
            SessaoRepository repository,
            TrilhaRepository trilhaRepository,
            DisciplinaRepository disciplinaRepository,
            UsuarioAtual usuarioAtual,
            TimezoneContext timezoneContext) {
        this.repository = repository;
        this.trilhaRepository = trilhaRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.usuarioAtual = usuarioAtual;
        this.timezoneContext = timezoneContext;
    }

    public RegistroResultado registrar(
            String trilhaIdStr,
            String idempotencyKeyHeader,
            RegistrarSessaoRequest request) {
        ObjectId usuarioId = usuarioAtual.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");
        String path = "/api/trilhas/" + trilhaIdStr + "/sessoes";

        // 1. Validação do cabeçalho de Idempotência
        if (idempotencyKeyHeader == null || idempotencyKeyHeader.isBlank()) {
            throw badRequest("MISSING_IDEMPOTENCY_KEY", "O cabeçalho Idempotency-Key é obrigatório.", path);
        }
        String chave = idempotencyKeyHeader.trim();
        if (chave.length() > 128) {
            throw badRequest("INVALID_IDEMPOTENCY_KEY", "A chave de idempotência não pode exceder 128 caracteres.", path);
        }
        if (request.chaveIdempotencia() != null && !request.chaveIdempotencia().isBlank()) {
            if (!request.chaveIdempotencia().trim().equals(chave)) {
                throw badRequest("IDEMPOTENCY_KEY_MISMATCH",
                        "O valor de chaveIdempotencia no corpo diverge do cabeçalho Idempotency-Key.", path);
            }
        }

        // 2. Validação da trilha e disciplina
        Document trilhaDoc = trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException("Trilha não encontrada"));

        ObjectId disciplinaId = BsonIds.parse(request.disciplinaId(), "disciplinaId");
        // Referência de corpo (D-06d): disciplina de outra trilha ou de outro usuário é
        // 400 VALIDATION_ERROR em errors.disciplinaId, nunca 404 — o 404 revelaria existência.
        Disciplina disciplina = disciplinaRepository.buscarPorId(usuarioId, trilhaId, disciplinaId)
                .orElseThrow(() -> badRequestCampo(
                        "disciplinaId",
                        "A disciplina informada não pertence a esta trilha.",
                        "A disciplina informada não pertence a esta trilha.",
                        path));

        // 3. Validação dos campos funcionais
        if (request.duracaoSegundos() < 60 || request.duracaoSegundos() > 86400) {
            throw badRequestCampo(
                    "duracaoSegundos",
                    "A duração deve estar entre 60 e 86400 segundos.",
                    "A duração deve ser de no mínimo 60 segundos e no máximo 86400 segundos (24 horas).",
                    path);
        }

        int questoes = request.questoesEfetivas();
        int acertos = request.acertosEfetivos();
        if (questoes < 0) {
            throw badRequestCampo(
                    "questoes",
                    "O total de questões deve ser maior ou igual a zero.",
                    "O total de questões deve ser maior ou igual a zero.",
                    path);
        }
        if (acertos < 0 || acertos > questoes) {
            throw badRequestCampo(
                    "acertos",
                    "O total de acertos deve estar entre 0 e o total de questões.",
                    "O total de acertos deve ser entre 0 e o total de questões.",
                    path);
        }

        LocalDate dataSessao;
        try {
            dataSessao = LocalDate.parse(request.dataSessao());
        } catch (DateTimeParseException e) {
            throw badRequestCampo(
                    "dataSessao",
                    "Use o formato ISO yyyy-MM-dd.",
                    "Formato de data inválido. Use o padrão ISO yyyy-MM-dd.",
                    path);
        }

        LocalDate hojeLocal = timezoneContext.hojeLocal();
        if (dataSessao.isAfter(hojeLocal)) {
            throw badRequestCampo(
                    "dataSessao",
                    "A data da sessão não pode ser futura.",
                    "A data da sessão não pode ser futura em relação ao fuso horário local.",
                    path);
        }

        // 4. Cálculo do hash dos parâmetros funcionais
        String requestHash = calcularRequestHash(trilhaId, disciplinaId, dataSessao.toString(),
                request.duracaoSegundos(), questoes, acertos);

        // 5. Verificação de idempotência no repositório
        Optional<Document> docExistenteOpt = repository.buscarDocumentoPorChaveIdempotencia(usuarioId, chave);
        if (docExistenteOpt.isPresent()) {
            Document doc = docExistenteOpt.get();
            if (doc.get("deletedAt") != null) {
                throw new DomainConflictException(CodigoErro.IDEMPOTENCY_RESULT_REMOVED,
                        "Esta sessão foi excluída e sua chave de idempotência não pode ser reutilizada.");
            }
            String existingHash = doc.getString("requestHash");
            if (requestHash.equals(existingHash)) {
                Sessao s = repository.paraSessao(doc);
                ObjectId primeiraId = repository.buscarPrimeiraSessaoDaTrilha(usuarioId, trilhaId)
                        .map(Sessao::id).orElse(null);
                boolean editavel = s.id().equals(primeiraId);
                return new RegistroResultado(paraDTO(s, disciplina.nome(), disciplina.cor(), false, editavel), false);
            } else {
                throw new DomainConflictException(CodigoErro.IDEMPOTENCY_KEY_CONFLICT,
                        "Chave de idempotência já utilizada com dados diferentes.");
            }
        }

        // 6. Inserção de nova sessão
        ObjectId sessaoId = new ObjectId();
        Instant agora = Instant.now();
        String trilhaNome = trilhaDoc.getString("nome");
        String discNome = disciplina.nome();
        String discCor = disciplina.cor();

        Sessao nova = new Sessao(
                sessaoId,
                usuarioId,
                trilhaId,
                disciplinaId,
                trilhaNome,
                discNome,
                discCor,
                dataSessao,
                request.duracaoSegundos(),
                questoes,
                acertos,
                chave,
                requestHash,
                null,
                agora,
                agora
        );

        try {
            repository.inserir(nova);
        } catch (MongoWriteException e) {
            if (e.getError().getCode() == 11000) {
                // Concorrência detectada: reavalia idempotência
                Document doc = repository.buscarDocumentoPorChaveIdempotencia(usuarioId, chave)
                        .orElseThrow(() -> e);
                if (doc.get("deletedAt") != null) {
                    throw new DomainConflictException(CodigoErro.IDEMPOTENCY_RESULT_REMOVED,
                            "Esta sessão foi excluída e sua chave de idempotência não pode ser reutilizada.");
                }
                String existingHash = doc.getString("requestHash");
                if (requestHash.equals(existingHash)) {
                    Sessao s = repository.paraSessao(doc);
                    ObjectId primeiraId = repository.buscarPrimeiraSessaoDaTrilha(usuarioId, trilhaId)
                            .map(Sessao::id).orElse(null);
                    boolean editavel = s.id().equals(primeiraId);
                    return new RegistroResultado(paraDTO(s, discNome, discCor, false, editavel), false);
                } else {
                    throw new DomainConflictException(CodigoErro.IDEMPOTENCY_KEY_CONFLICT,
                            "Chave de idempotência já utilizada com dados diferentes.");
                }
            }
            throw e;
        }

        ObjectId primeiraId = repository.buscarPrimeiraSessaoDaTrilha(usuarioId, trilhaId)
                .map(Sessao::id).orElse(null);
        boolean editavel = sessaoId.equals(primeiraId);
        return new RegistroResultado(paraDTO(nova, discNome, discCor, false, editavel), true);
    }

    public SessaoDTO atualizar(
            String trilhaIdStr,
            String sessaoIdStr,
            AtualizarSessaoRequest request) {
        ObjectId usuarioId = usuarioAtual.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");
        ObjectId sessaoId = BsonIds.parse(sessaoIdStr, "sessaoId");
        String path = "/api/trilhas/" + trilhaIdStr + "/sessoes/" + sessaoIdStr;

        trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException("Trilha não encontrada"));

        // A sessão mais recente da trilha é estritamente a número 1 em (dataSessao DESC, criadoEm DESC, _id DESC)
        Optional<Sessao> primeiraOpt = repository.buscarPrimeiraSessaoDaTrilha(usuarioId, trilhaId);
        if (primeiraOpt.isEmpty() || !primeiraOpt.get().id().equals(sessaoId)) {
            // Verifica se a sessão existe na trilha
            Optional<Sessao> sessaoExistente = repository.buscarPorId(usuarioId, trilhaId, sessaoId);
            if (sessaoExistente.isEmpty()) {
                throw new NotFoundException("Sessão não encontrada.");
            }
            throw new DomainConflictException(CodigoErro.LATEST_SESSION_ONLY,
                    "Apenas a sessão mais recente da trilha pode ser editada.");
        }

        Sessao sessaoAlvo = primeiraOpt.get();

        if (request.duracaoSegundos() < 60 || request.duracaoSegundos() > 86400) {
            throw badRequestCampo(
                    "duracaoSegundos",
                    "A duração deve estar entre 60 e 86400 segundos.",
                    "A duração deve ser de no mínimo 60 segundos e no máximo 86400 segundos (24 horas).",
                    path);
        }

        int questoes = request.questoesEfetivas();
        int acertos = request.acertosEfetivos();
        if (questoes < 0) {
            throw badRequestCampo(
                    "questoes",
                    "O total de questões deve ser maior ou igual a zero.",
                    "O total de questões deve ser maior ou igual a zero.",
                    path);
        }
        if (acertos < 0 || acertos > questoes) {
            throw badRequestCampo(
                    "acertos",
                    "O total de acertos deve estar entre 0 e o total de questões.",
                    "O total de acertos deve ser entre 0 e o total de questões.",
                    path);
        }

        Instant agora = Instant.now();
        repository.atualizar(usuarioId, trilhaId, sessaoId, request.duracaoSegundos(), questoes, acertos, agora);

        // Resolve estado da disciplina (ativa vs removida)
        Optional<Disciplina> discOpt = disciplinaRepository.buscarPorId(usuarioId, trilhaId, sessaoAlvo.disciplinaId());
        boolean disciplinaRemovida = discOpt.isEmpty();
        String discNome = discOpt.map(Disciplina::nome).orElse(sessaoAlvo.disciplinaNomeSnapshot());
        String discCor = discOpt.map(Disciplina::cor).orElse(sessaoAlvo.disciplinaCorSnapshot());

        int erros = Math.max(0, questoes - acertos);
        double percentual = questoes > 0 ? (acertos * 100.0 / questoes) : 0.0;

        return new SessaoDTO(
                BsonIds.toHex(sessaoId),
                BsonIds.toHex(trilhaId),
                BsonIds.toHex(sessaoAlvo.disciplinaId()),
                discNome,
                discCor,
                disciplinaRemovida,
                sessaoAlvo.dataSessao().toString(),
                request.duracaoSegundos(),
                questoes,
                acertos,
                erros,
                percentual,
                true,
                sessaoAlvo.chaveIdempotencia(),
                sessaoAlvo.criadoEm(),
                agora
        );
    }

    public void excluirIndividual(String trilhaIdStr, String sessaoIdStr) {
        ObjectId usuarioId = usuarioAtual.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");
        ObjectId sessaoId = BsonIds.parse(sessaoIdStr, "sessaoId");

        trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException("Trilha não encontrada"));

        Optional<Sessao> sessaoOpt = repository.buscarPorId(usuarioId, trilhaId, sessaoId);
        if (sessaoOpt.isEmpty()) {
            throw new NotFoundException("Sessão não encontrada.");
        }

        repository.marcarTombstone(usuarioId, trilhaId, sessaoId, Instant.now());
    }

    public RemocaoSessoesDisciplinaResponse excluirSessoesDaDisciplina(
            String trilhaIdStr,
            String disciplinaIdStr) {
        ObjectId usuarioId = usuarioAtual.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");
        ObjectId disciplinaId = BsonIds.parse(disciplinaIdStr, "disciplinaId");

        trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException("Trilha não encontrada"));

        return repository.excluirSessoesDaDisciplina(usuarioId, trilhaId, disciplinaId);
    }

    public PaginaSessoesDTO listarPaginado(
            String trilhaIdStr,
            String disciplinaIdStr,
            String dataInicioStr,
            String dataFimStr,
            Integer pagina,
            Integer tamanho) {
        ObjectId usuarioId = usuarioAtual.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");
        String path = "/api/trilhas/" + trilhaIdStr + "/sessoes";

        trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException("Trilha não encontrada"));

        int p = (pagina != null) ? pagina : 1;
        int t = (tamanho != null) ? tamanho : 20;
        if (p < 1 || t < 1 || t > 100) {
            throw badRequest("INVALID_PAGINATION",
                    "Parâmetros de paginação inválidos. 'pagina' deve ser >= 1 e 'tamanho' deve estar entre 1 e 100.", path);
        }

        ObjectId disciplinaId = null;
        if (disciplinaIdStr != null && !disciplinaIdStr.isBlank()) {
            disciplinaId = BsonIds.parse(disciplinaIdStr, "disciplinaId");
        }

        LocalDate dataInicio = null;
        if (dataInicioStr != null && !dataInicioStr.isBlank()) {
            try {
                dataInicio = LocalDate.parse(dataInicioStr);
            } catch (DateTimeParseException e) {
                throw badRequest("INVALID_DATE", "Formato inválido de dataInicio. Use yyyy-MM-dd.", path);
            }
        }

        LocalDate dataFim = null;
        if (dataFimStr != null && !dataFimStr.isBlank()) {
            try {
                dataFim = LocalDate.parse(dataFimStr);
            } catch (DateTimeParseException e) {
                throw badRequest("INVALID_DATE", "Formato inválido de dataFim. Use yyyy-MM-dd.", path);
            }
        }

        if (dataInicio != null && dataFim != null && dataInicio.isAfter(dataFim)) {
            throw badRequest("INVALID_DATE_RANGE", "A data inicial não pode ser posterior à data final.", path);
        }

        ObjectId primeiraSessaoId = repository.buscarPrimeiraSessaoDaTrilha(usuarioId, trilhaId)
                .map(Sessao::id).orElse(null);

        Map<ObjectId, Disciplina> disciplinasAtivas = disciplinaRepository.listarPorTrilha(usuarioId, trilhaId)
                .stream().collect(Collectors.toMap(Disciplina::id, d -> d));

        return repository.listarPaginado(
                new SessaoRepository.FiltroPaginacao(usuarioId, trilhaId, disciplinaId, dataInicio, dataFim, p, t),
                primeiraSessaoId,
                disciplinasAtivas
        );
    }

    public EstatisticasTrilhaDTO obterEstatisticas(
            String trilhaIdStr,
            String periodo,
            String dataInicioStr,
            String dataFimStr) {
        ObjectId usuarioId = usuarioAtual.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");
        String path = "/api/trilhas/" + trilhaIdStr + "/estatisticas";

        trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException("Trilha não encontrada"));

        LocalDate dataInicio = null;
        LocalDate dataFim = null;

        if ((dataInicioStr != null && !dataInicioStr.isBlank()) || (dataFimStr != null && !dataFimStr.isBlank())) {
            if (dataInicioStr == null || dataInicioStr.isBlank() || dataFimStr == null || dataFimStr.isBlank()) {
                throw badRequest("INVALID_DATE_RANGE",
                        "Ambas as datas 'dataInicio' e 'dataFim' devem ser fornecidas para intervalo personalizado.", path);
            }
            try {
                dataInicio = LocalDate.parse(dataInicioStr);
                dataFim = LocalDate.parse(dataFimStr);
            } catch (DateTimeParseException e) {
                throw badRequest("INVALID_DATE", "Formato de data inválido. Use yyyy-MM-dd.", path);
            }
            if (dataInicio.isAfter(dataFim)) {
                throw badRequest("INVALID_DATE_RANGE", "A data inicial não pode ser posterior à data final.", path);
            }
        } else {
            LocalDate hojeLocal = timezoneContext.hojeLocal();
            String p = (periodo != null && !periodo.isBlank()) ? periodo.trim().toLowerCase() : "7d";
            switch (p) {
                case "7d" -> {
                    dataInicio = hojeLocal.minusDays(6);
                    dataFim = hojeLocal;
                }
                case "14d" -> {
                    dataInicio = hojeLocal.minusDays(13);
                    dataFim = hojeLocal;
                }
                case "30d" -> {
                    dataInicio = hojeLocal.minusDays(29);
                    dataFim = hojeLocal;
                }
                case "180d" -> {
                    dataInicio = hojeLocal.minusDays(179);
                    dataFim = hojeLocal;
                }
                case "tudo" -> {
                    dataInicio = null;
                    dataFim = null;
                }
                default -> throw badRequest("INVALID_PERIOD",
                        "Período inválido. Valores aceitos: 7d, 14d, 30d, 180d, tudo.", path);
            }
        }

        List<Disciplina> disciplinasTrilha = disciplinaRepository.listarPorTrilha(usuarioId, trilhaId);
        return repository.obterEstatisticas(usuarioId, trilhaId, dataInicio, dataFim, timezoneContext.hojeLocal(), disciplinasTrilha);
    }

    /**
     * Hash funcional do payload de criação (SHA-256 sobre JSON canônico com chaves ordenadas).
     *
     * <p>O JSON canônico é montado em {@link java.util.LinkedHashMap} com a ordem fixa
     * {@code trilhaId, disciplinaId, dataSessao, duracaoSegundos, questoes, acertos}. A ordem de
     * inserção não depende de reflexão, {@code ObjectMapper} ou configuração de ambiente, então o
     * mesmo payload funcional sempre produz o mesmo hash — condição para reconhecer replay
     * idempotente e distinguir de conflito de chave.</p>
     */
    public static String calcularRequestHash(
            ObjectId trilhaId,
            ObjectId disciplinaId,
            String dataSessao,
            int duracaoSegundos,
            int questoes,
            int acertos) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("trilhaId", trilhaId.toHexString());
        payload.put("disciplinaId", disciplinaId.toHexString());
        payload.put("dataSessao", dataSessao);
        payload.put("duracaoSegundos", duracaoSegundos);
        payload.put("questoes", questoes);
        payload.put("acertos", acertos);
        String canonical = jsonCanonico(payload);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }

    /**
     * Serializa o payload em JSON canônico: chaves na ordem de inserção do
     * {@link LinkedHashMap} e valores escalares sem espaço. {@code null} vira {@code null} JSON
     * (não string vazia), para que payloads distintos nunca colidam.
     */
    private static String jsonCanonico(Map<String, Object> payload) {
        StringBuilder sb = new StringBuilder(128).append('{');
        boolean primeiro = true;
        for (Map.Entry<String, Object> entrada : payload.entrySet()) {
            if (!primeiro) {
                sb.append(',');
            }
            primeiro = false;
            sb.append('"').append(entrada.getKey()).append("\":");
            Object valor = entrada.getValue();
            if (valor == null) {
                sb.append("null");
            } else if (valor instanceof Number) {
                sb.append(valor);
            } else {
                sb.append('"').append(escaparJson(String.valueOf(valor))).append('"');
            }
        }
        return sb.append('}').toString();
    }

    private static String escaparJson(String valor) {
        StringBuilder sb = new StringBuilder(valor.length() + 8);
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private SessaoDTO paraDTO(
            Sessao s,
            String discNome,
            String discCor,
            boolean disciplinaRemovida,
            boolean editavel) {
        return new SessaoDTO(
                BsonIds.toHex(s.id()),
                BsonIds.toHex(s.trilhaId()),
                BsonIds.toHex(s.disciplinaId()),
                discNome,
                discCor,
                disciplinaRemovida,
                s.dataSessao() != null ? s.dataSessao().toString() : null,
                s.duracaoSegundos(),
                s.questoes(),
                s.acertos(),
                s.erros(),
                s.percentualAcerto(),
                editavel,
                s.chaveIdempotencia(),
                s.criadoEm(),
                s.atualizadoEm()
        );
    }

    private WebApplicationException badRequest(String code, String detail, String instance) {
        ProblemDetails problem = ProblemDetailsFactory.criar(codigoDoErro(code), detail, instance);
        return new WebApplicationException(
                Response.status(problem.status())
                        .type(ProblemDetails.MEDIA_TYPE)
                        .entity(problem)
                        .build()
        );
    }

    /**
     * Erro de payload com extensão {@code errors} preenchida (D-04).
     *
     * <p>Toda referência inválida vinda no corpo ou todo campo funcional fora de faixa responde
     * {@code 400 VALIDATION_ERROR} com a chave do campo, para que o cliente destaque o input. A
     * referência cruzada ({@code disciplinaId} de outra trilha ou de outro usuário) segue a mesma
     * regra: não é recurso do path, então não vira {@code 404}.</p>
     */
    private WebApplicationException badRequestCampo(String campo, String mensagem, String detail, String instance) {
        ProblemDetails problem = ProblemDetailsFactory.criarCampo(
                CodigoErro.VALIDATION_ERROR, detail, instance, campo, mensagem);
        return new WebApplicationException(
                Response.status(problem.status())
                        .type(ProblemDetails.MEDIA_TYPE)
                        .entity(problem)
                        .build()
        );
    }

    /**
     * Mapeia os códigos de validação de sessão para o catálogo fechado (D-04).
     * Todo erro de payload é {@code VALIDATION_ERROR}; apenas a paginação e o
     * descasamento de idempotência têm código próprio.
     */
    private static CodigoErro codigoDoErro(String code) {
        if (code == null) {
            return CodigoErro.VALIDATION_ERROR;
        }
        return switch (code) {
            case "INVALID_PAGINATION" -> CodigoErro.INVALID_PAGINATION;
            case "MISSING_IDEMPOTENCY_KEY" -> CodigoErro.IDEMPOTENCY_KEY_REQUIRED;
            case "IDEMPOTENCY_KEY_MISMATCH" -> CodigoErro.IDEMPOTENCY_KEY_MISMATCH;
            default -> CodigoErro.VALIDATION_ERROR;
        };
    }
}
