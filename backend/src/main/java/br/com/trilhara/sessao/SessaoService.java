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

    private static final String CAMPO_TRILHA_ID = "trilhaId";
    private static final String PREFIXO_API_TRILHAS = "/api/trilhas/";
    private static final String ERRO_TRILHA_NAO_ENCONTRADA = "Trilha não encontrada";
    private static final String CAMPO_DISCIPLINA_ID = "disciplinaId";
    private static final String CAMPO_DURACAO_SEGUNDOS = "duracaoSegundos";
    private static final String CAMPO_QUESTOES = "questoes";
    private static final String ERRO_QUESTOES_NAO_NEGATIVO = "O total de questões deve ser maior ou igual a zero.";
    private static final String CAMPO_ACERTOS = "acertos";
    private static final String CAMPO_DATA_SESSAO = "dataSessao";
    private static final String CODIGO_INVALID_DATE = "INVALID_DATE";
    private static final String CODIGO_INVALID_DATE_RANGE = "INVALID_DATE_RANGE";

    public record RegistroResultado(SessaoDTO sessao, boolean criada) {}

    private record IntervaloDatas(LocalDate dataInicio, LocalDate dataFim) {}

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
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, CAMPO_TRILHA_ID);
        String path = PREFIXO_API_TRILHAS + trilhaIdStr + "/sessoes";

        String chave = validarChaveIdempotencia(idempotencyKeyHeader, request.chaveIdempotencia(), path);

        Document trilhaDoc = trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

        ObjectId disciplinaId = BsonIds.parse(request.disciplinaId(), CAMPO_DISCIPLINA_ID);
        Disciplina disciplina = disciplinaRepository.buscarPorId(usuarioId, trilhaId, disciplinaId)
                .orElseThrow(() -> badRequestCampo(
                        CAMPO_DISCIPLINA_ID,
                        "A disciplina informada não pertence a esta trilha.",
                        "A disciplina informada não pertence a esta trilha.",
                        path));

        int questoes = request.questoesEfetivas();
        int acertos = request.acertosEfetivos();
        validarMetricasSessao(request.duracaoSegundos(), questoes, acertos, path);
        LocalDate dataSessao = validarDataSessao(request.dataSessao(), path);

        String requestHash = calcularRequestHash(trilhaId, disciplinaId, dataSessao.toString(),
                request.duracaoSegundos(), questoes, acertos);

        Optional<Document> docExistenteOpt = repository.buscarDocumentoPorChaveIdempotencia(usuarioId, chave);
        if (docExistenteOpt.isPresent()) {
            return resolverSessaoIdempotente(
                    usuarioId, trilhaId, docExistenteOpt.get(), requestHash, disciplina.nome(), disciplina.cor());
        }

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
                Document doc = repository.buscarDocumentoPorChaveIdempotencia(usuarioId, chave)
                        .orElseThrow(() -> e);
                return resolverSessaoIdempotente(usuarioId, trilhaId, doc, requestHash, discNome, discCor);
            }
            throw e;
        }

        ObjectId primeiraId = repository.buscarPrimeiraSessaoDaTrilha(usuarioId, trilhaId)
                .map(Sessao::id).orElse(null);
        boolean editavel = sessaoId.equals(primeiraId);
        return new RegistroResultado(paraDTO(nova, discNome, discCor, false, editavel), true);
    }

    private String validarChaveIdempotencia(String idempotencyKeyHeader, String chaveCorpo, String path) {
        if (idempotencyKeyHeader == null || idempotencyKeyHeader.isBlank()) {
            throw badRequest("MISSING_IDEMPOTENCY_KEY", "O cabeçalho Idempotency-Key é obrigatório.", path);
        }
        String chave = idempotencyKeyHeader.trim();
        if (chave.length() > 128) {
            throw badRequest("INVALID_IDEMPOTENCY_KEY", "A chave de idempotência não pode exceder 128 caracteres.", path);
        }
        if (chaveCorpo != null && !chaveCorpo.isBlank() && !chaveCorpo.trim().equals(chave)) {
            throw badRequest("IDEMPOTENCY_KEY_MISMATCH",
                    "O valor de chaveIdempotencia no corpo diverge do cabeçalho Idempotency-Key.", path);
        }
        return chave;
    }

    private void validarMetricasSessao(int duracaoSegundos, int questoes, int acertos, String path) {
        if (duracaoSegundos < 60 || duracaoSegundos > 86400) {
            throw badRequestCampo(
                    CAMPO_DURACAO_SEGUNDOS,
                    "A duração deve estar entre 60 e 86400 segundos.",
                    "A duração deve ser de no mínimo 60 segundos e no máximo 86400 segundos (24 horas).",
                    path);
        }
        if (questoes < 0) {
            throw badRequestCampo(
                    CAMPO_QUESTOES,
                    ERRO_QUESTOES_NAO_NEGATIVO,
                    ERRO_QUESTOES_NAO_NEGATIVO,
                    path);
        }
        if (acertos < 0 || acertos > questoes) {
            throw badRequestCampo(
                    CAMPO_ACERTOS,
                    "O total de acertos deve estar entre 0 e o total de questões.",
                    "O total de acertos deve ser entre 0 e o total de questões.",
                    path);
        }
    }

    private LocalDate validarDataSessao(String dataSessaoStr, String path) {
        LocalDate dataSessao;
        try {
            dataSessao = LocalDate.parse(dataSessaoStr);
        } catch (DateTimeParseException _) {
            throw badRequestCampo(
                    CAMPO_DATA_SESSAO,
                    "Use o formato ISO yyyy-MM-dd.",
                    "Formato de data inválido. Use o padrão ISO yyyy-MM-dd.",
                    path);
        }

        LocalDate hojeLocal = timezoneContext.hojeLocal();
        if (dataSessao.isAfter(hojeLocal)) {
            throw badRequestCampo(
                    CAMPO_DATA_SESSAO,
                    "A data da sessão não pode ser futura.",
                    "A data da sessão não pode ser futura em relação ao fuso horário local.",
                    path);
        }
        return dataSessao;
    }

    private RegistroResultado resolverSessaoIdempotente(
            ObjectId usuarioId,
            ObjectId trilhaId,
            Document doc,
            String requestHash,
            String discNome,
            String discCor) {
        if (doc.get("deletedAt") != null) {
            throw new DomainConflictException(CodigoErro.IDEMPOTENCY_RESULT_REMOVED,
                    "Esta sessão foi excluída e sua chave de idempotência não pode ser reutilizada.");
        }
        String existingHash = doc.getString("requestHash");
        if (!requestHash.equals(existingHash)) {
            throw new DomainConflictException(CodigoErro.IDEMPOTENCY_KEY_CONFLICT,
                    "Chave de idempotência já utilizada com dados diferentes.");
        }
        Sessao s = repository.paraSessao(doc);
        ObjectId primeiraId = repository.buscarPrimeiraSessaoDaTrilha(usuarioId, trilhaId)
                .map(Sessao::id).orElse(null);
        boolean editavel = s.id().equals(primeiraId);
        return new RegistroResultado(paraDTO(s, discNome, discCor, false, editavel), false);
    }

    public SessaoDTO atualizar(
            String trilhaIdStr,
            String sessaoIdStr,
            AtualizarSessaoRequest request) {
        ObjectId usuarioId = usuarioAtual.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, CAMPO_TRILHA_ID);
        ObjectId sessaoId = BsonIds.parse(sessaoIdStr, "sessaoId");
        String path = PREFIXO_API_TRILHAS + trilhaIdStr + "/sessoes/" + sessaoIdStr;

        trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

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

        int questoes = request.questoesEfetivas();
        int acertos = request.acertosEfetivos();
        validarMetricasSessao(request.duracaoSegundos(), questoes, acertos, path);

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
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, CAMPO_TRILHA_ID);
        ObjectId sessaoId = BsonIds.parse(sessaoIdStr, "sessaoId");

        trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

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
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, CAMPO_TRILHA_ID);
        ObjectId disciplinaId = BsonIds.parse(disciplinaIdStr, CAMPO_DISCIPLINA_ID);

        trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

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
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, CAMPO_TRILHA_ID);
        String path = PREFIXO_API_TRILHAS + trilhaIdStr + "/sessoes";

        trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

        int p = (pagina != null) ? pagina : 1;
        int t = (tamanho != null) ? tamanho : 20;
        if (p < 1 || t < 1 || t > 100) {
            throw badRequest("INVALID_PAGINATION",
                    "Parâmetros de paginação inválidos. 'pagina' deve ser >= 1 e 'tamanho' deve estar entre 1 e 100.", path);
        }

        ObjectId disciplinaId = (disciplinaIdStr != null && !disciplinaIdStr.isBlank())
                ? BsonIds.parse(disciplinaIdStr, CAMPO_DISCIPLINA_ID)
                : null;
        LocalDate dataInicio = parsearDataFiltroOpcional(dataInicioStr, "dataInicio", path);
        LocalDate dataFim = parsearDataFiltroOpcional(dataFimStr, "dataFim", path);

        if (dataInicio != null && dataFim != null && dataInicio.isAfter(dataFim)) {
            throw badRequest(CODIGO_INVALID_DATE_RANGE, "A data inicial não pode ser posterior à data final.", path);
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

    private LocalDate parsearDataFiltroOpcional(String dataStr, String nomeCampo, String path) {
        if (dataStr == null || dataStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dataStr);
        } catch (DateTimeParseException _) {
            throw badRequest(CODIGO_INVALID_DATE, "Formato inválido de " + nomeCampo + ". Use yyyy-MM-dd.", path);
        }
    }

    public EstatisticasTrilhaDTO obterEstatisticas(
            String trilhaIdStr,
            String periodo,
            String dataInicioStr,
            String dataFimStr) {
        ObjectId usuarioId = usuarioAtual.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, CAMPO_TRILHA_ID);
        String path = PREFIXO_API_TRILHAS + trilhaIdStr + "/estatisticas";

        trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

        IntervaloDatas intervalo = resolverIntervaloEstatisticas(periodo, dataInicioStr, dataFimStr, path);
        List<Disciplina> disciplinasTrilha = disciplinaRepository.listarPorTrilha(usuarioId, trilhaId);
        return repository.obterEstatisticas(
                usuarioId,
                trilhaId,
                intervalo.dataInicio(),
                intervalo.dataFim(),
                timezoneContext.hojeLocal(),
                disciplinasTrilha);
    }

    private IntervaloDatas resolverIntervaloEstatisticas(
            String periodo, String dataInicioStr, String dataFimStr, String path) {
        boolean temInicio = dataInicioStr != null && !dataInicioStr.isBlank();
        boolean temFim = dataFimStr != null && !dataFimStr.isBlank();
        if (temInicio || temFim) {
            if (!temInicio || !temFim) {
                throw badRequest(CODIGO_INVALID_DATE_RANGE,
                        "Ambas as datas 'dataInicio' e 'dataFim' devem ser fornecidas para intervalo personalizado.", path);
            }
            LocalDate dataInicio;
            LocalDate dataFim;
            try {
                dataInicio = LocalDate.parse(dataInicioStr);
                dataFim = LocalDate.parse(dataFimStr);
            } catch (DateTimeParseException _) {
                throw badRequest(CODIGO_INVALID_DATE, "Formato de data inválido. Use yyyy-MM-dd.", path);
            }
            if (dataInicio.isAfter(dataFim)) {
                throw badRequest(CODIGO_INVALID_DATE_RANGE, "A data inicial não pode ser posterior à data final.", path);
            }
            return new IntervaloDatas(dataInicio, dataFim);
        }

        LocalDate hojeLocal = timezoneContext.hojeLocal();
        String p = (periodo != null && !periodo.isBlank()) ? periodo.trim().toLowerCase() : "7d";
        return switch (p) {
            case "7d" -> new IntervaloDatas(hojeLocal.minusDays(6), hojeLocal);
            case "14d" -> new IntervaloDatas(hojeLocal.minusDays(13), hojeLocal);
            case "30d" -> new IntervaloDatas(hojeLocal.minusDays(29), hojeLocal);
            case "180d" -> new IntervaloDatas(hojeLocal.minusDays(179), hojeLocal);
            case "tudo" -> new IntervaloDatas(null, null);
            default -> throw badRequest("INVALID_PERIOD",
                    "Período inválido. Valores aceitos: 7d, 14d, 30d, 180d, tudo.", path);
        };
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
        payload.put(CAMPO_TRILHA_ID, trilhaId.toHexString());
        payload.put(CAMPO_DISCIPLINA_ID, disciplinaId.toHexString());
        payload.put(CAMPO_DATA_SESSAO, dataSessao);
        payload.put(CAMPO_DURACAO_SEGUNDOS, duracaoSegundos);
        payload.put(CAMPO_QUESTOES, questoes);
        payload.put(CAMPO_ACERTOS, acertos);
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
 * Erro de payload com extensão {@code errors} preenchida.
 *
 * <p>Toda referência inválida vinda no corpo ou cada campo funcional fora de faixa responde
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
 * Mapeia os códigos de validação de sessão para o catálogo fechado.
 * Qualquer erro de payload é {@code VALIDATION_ERROR}; apenas a paginação e o
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
