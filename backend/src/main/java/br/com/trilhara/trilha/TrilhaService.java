package br.com.trilhara.trilha;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.ConfigProvider;

import br.com.trilhara.auth.RateLimitService;
import br.com.trilhara.cronograma.BlocoCronogramaRepository;
import br.com.trilhara.cronograma.Cronograma;
import br.com.trilhara.cronograma.CronogramaRepository;
import br.com.trilhara.edital.Edital;
import br.com.trilhara.edital.EditalRepository;
import br.com.trilhara.edital.StatusEdital;
import br.com.trilhara.nota.CadernoRepository;
import br.com.trilhara.sessao.SessaoRepository;
import br.com.trilhara.shared.TimezoneContext;
import br.com.trilhara.shared.erro.ResourceLimitExceededException;
import br.com.trilhara.shared.erro.ValidationException;
import br.com.trilhara.shared.mongo.BsonDates;
import br.com.trilhara.shared.mongo.BsonIds;
import br.com.trilhara.trilha.dto.AtualizarDisciplinaRequest;
import br.com.trilhara.trilha.dto.AtualizarMetadadosTrilhaRequest;
import br.com.trilhara.trilha.dto.CriarDisciplinaRequest;
import br.com.trilhara.trilha.dto.CriarTopicoRequest;
import br.com.trilhara.trilha.dto.CriarTrilhaRequest;
import br.com.trilhara.trilha.dto.CronogramaAtivoResumoDTO;
import br.com.trilhara.trilha.dto.DisciplinaDTO;
import br.com.trilhara.trilha.dto.ProgressoTopicoDTO;
import br.com.trilhara.trilha.dto.ReordenarDisciplinasRequest;
import br.com.trilhara.trilha.dto.TopicoDTO;
import br.com.trilhara.trilha.dto.TrilhaDetalheDTO;
import br.com.trilhara.trilha.dto.TrilhaImpactoExclusaoDTO;
import br.com.trilhara.trilha.dto.TrilhaResumoDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

/**
 * Regras de trilhas, disciplinas, tópicos e progresso (requisitos §4).
 *
 * <p><b>Progresso só existe quando o tópico está concluído</b> (requisitos §4.2): marcar cria ou
 * mantém o registro e desmarcar o remove. O percentual é sempre ponderado pelo total de tópicos,
 * nunca média simples das disciplinas.</p>
 *
 * <p><b>Limites de acervo</b> (50 trilhas por usuário, 100 disciplinas por trilha, 500 tópicos por
 * disciplina) respondem {@code 409 RESOURCE_LIMIT_EXCEEDED}: conflito com o estado atual do
 * usuário, não erro de sintaxe do payload.</p>
 *
 * <p><b>Referência cruzada</b>: toda referência recebida no corpo ({@code editalId},
 * {@code disciplinaIds}, IDs de tópicos) é validada por consulta com {@code usuarioId} e pai
 * correto; falha no corpo é {@code 400 VALIDATION_ERROR} no campo, recurso inexistente no path é
 * {@code 404}.</p>
 */
@ApplicationScoped
public class TrilhaService {

    public static final int MAX_TRILHAS_PADRAO = 50;
    public static final int MAX_DISCIPLINAS_POR_TRILHA = 100;
    public static final int MAX_TOPICOS_POR_DISCIPLINA = 500;

    private static final String COR_PADRAO_TRILHA = "#8F9E8B";
    private static final String ERRO_TRILHA_NAO_ENCONTRADA = "Trilha não encontrada";
    private static final String ERRO_DISCIPLINA_NAO_ENCONTRADA = "Disciplina não encontrada";
    private static final String ERRO_TOPICO_NAO_ENCONTRADO = "Tópico não encontrado na trilha";
    private static final String CAMPO_CARGO = "cargo";
    private static final String CAMPO_AREA_CARGO = "areaCargo";
    private static final String MSG_REORDENACAO_INTEGRA =
            "A reordenação deve conter todas as disciplinas ativas da trilha exatamente uma vez.";
    private static final String MSG_LIMITE_TOPICOS =
            "Cada disciplina pode ter no máximo " + MAX_TOPICOS_POR_DISCIPLINA + " tópicos.";

    private final TrilhaRepository repository;
    private final DisciplinaRepository disciplinaRepository;
    private final ProgressoTopicoRepository progressoRepository;
    private final EditalRepository editalRepository;
    private final SessaoRepository sessaoRepository;
    private final BlocoCronogramaRepository blocoCronogramaRepository;
    private final CronogramaRepository cronogramaRepository;
    private final CadernoRepository cadernoRepository;
    private final LeaseTrilhaService leaseTrilhaService;
    private final TimezoneContext timezoneContext;
    private final RateLimitService rateLimitService;
    private final int maxTrilhasPorUsuario;

    @Inject
    public TrilhaService(
            TrilhaRepository repository,
            DisciplinaRepository disciplinaRepository,
            ProgressoTopicoRepository progressoRepository,
            EditalRepository editalRepository,
            SessaoRepository sessaoRepository,
            BlocoCronogramaRepository blocoCronogramaRepository,
            CronogramaRepository cronogramaRepository,
            CadernoRepository cadernoRepository,
            LeaseTrilhaService leaseTrilhaService,
            TimezoneContext timezoneContext,
            RateLimitService rateLimitService) {
        this.repository = repository;
        this.disciplinaRepository = disciplinaRepository;
        this.progressoRepository = progressoRepository;
        this.editalRepository = editalRepository;
        this.sessaoRepository = sessaoRepository;
        this.blocoCronogramaRepository = blocoCronogramaRepository;
        this.cronogramaRepository = cronogramaRepository;
        this.cadernoRepository = cadernoRepository;
        this.leaseTrilhaService = leaseTrilhaService;
        this.timezoneContext = timezoneContext;
        this.rateLimitService = rateLimitService;
        this.maxTrilhasPorUsuario = ConfigProvider.getConfig()
                .getOptionalValue("trilhara.trilhas.max-por-usuario", Integer.class)
                .orElse(MAX_TRILHAS_PADRAO);
    }

    // =========================================================================
    // LEITURA: RESUMO LEVE (HUB) E DETALHE SOB DEMANDA
    // =========================================================================

    public List<TrilhaResumoDTO> listarResumo() {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        List<Document> documentos = repository.listarEntidades(usuarioId);
        List<TrilhaResumoDTO> resumos = new ArrayList<>();

        for (Document doc : documentos) {
            ObjectId trilhaId = doc.getObjectId("_id");
            List<Disciplina> disciplinas = disciplinaRepository.listarPorTrilha(usuarioId, trilhaId);
            int totalTopicos = disciplinas.stream().mapToInt(d -> d.topicos().size()).sum();
            int topicosConcluidos = (int) progressoRepository.contarConcluidosPorTrilha(usuarioId, trilhaId);
            double pct = totalTopicos > 0 ? ((double) topicosConcluidos / totalTopicos) * 100.0 : 0.0;

            resumos.add(new TrilhaResumoDTO(
                    trilhaId.toHexString(),
                    doc.getString("nome"),
                    cargoDoDocumento(doc),
                    doc.getString("banca"),
                    doc.getString("dataProva"),
                    doc.getString("cor"),
                    hexOuNulo(doc.getObjectId("origemEditalId")),
                    disciplinas.size(),
                    totalTopicos,
                    topicosConcluidos,
                    pct,
                    obterCronogramaAtivoResumo(doc, usuarioId),
                    BsonDates.lerInstant(doc, "criadoEm"),
                    BsonDates.lerInstant(doc, "atualizadoEm")));
        }
        return resumos;
    }

    public TrilhaDetalheDTO buscarDetalhe(String trilhaIdStr) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");

        Document doc = repository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

        return montarDetalhe(doc, usuarioId, trilhaId);
    }

    /**
 * Monta o detalhe a partir do documento da trilha já carregado para o usuário.
 * Não há mapa de progresso no contrato: o cliente usa {@code topicosConcluidosIds}.
 */
    private TrilhaDetalheDTO montarDetalhe(Document doc, ObjectId usuarioId, ObjectId trilhaId) {
        List<Disciplina> disciplinas = disciplinaRepository.listarPorTrilha(usuarioId, trilhaId);
        Set<String> concluidosTrilha = progressoRepository.listarTopicosConcluidosIds(usuarioId, trilhaId);

        int totalTopicos = 0;
        List<DisciplinaDTO> disciplinasDTO = new ArrayList<>();

        for (Disciplina d : disciplinas) {
            int dTotal = d.topicos().size();
            totalTopicos += dTotal;
            int dConcluidos = 0;
            List<TopicoDTO> topicosDTO = new ArrayList<>();

            for (TopicoEmbutido t : d.topicos()) {
                boolean concluido = concluidosTrilha.contains(t.id());
                if (concluido) {
                    dConcluidos++;
                }
                topicosDTO.add(new TopicoDTO(t.id(), t.nome(), t.ordem(), concluido));
            }

            double dPct = dTotal > 0 ? ((double) dConcluidos / dTotal) * 100.0 : 0.0;
            disciplinasDTO.add(new DisciplinaDTO(
                    d.id().toHexString(),
                    trilhaId.toHexString(),
                    d.nome(),
                    d.cor(),
                    d.peso(),
                    d.ordem(),
                    topicosDTO,
                    dTotal,
                    dConcluidos,
                    dPct));
        }

        int topicosConcluidos = concluidosTrilha.size();
        double percentualGeral = totalTopicos > 0 ? ((double) topicosConcluidos / totalTopicos) * 100.0 : 0.0;

        return new TrilhaDetalheDTO(
                trilhaId.toHexString(),
                doc.getString("nome"),
                cargoDoDocumento(doc),
                doc.getString("banca"),
                doc.getString("dataProva"),
                doc.getString("cor"),
                hexOuNulo(doc.getObjectId("origemEditalId")),
                disciplinasDTO.size(),
                totalTopicos,
                topicosConcluidos,
                percentualGeral,
                obterCronogramaAtivoResumo(doc, usuarioId),
                disciplinasDTO,
                concluidosTrilha,
                BsonDates.lerInstant(doc, "criadoEm"),
                BsonDates.lerInstant(doc, "atualizadoEm"));
    }

    private String cargoDoDocumento(Document doc) {
        String cargo = doc.getString(CAMPO_CARGO);
        return cargo != null ? cargo : doc.getString(CAMPO_AREA_CARGO);
    }

    private static String hexOuNulo(ObjectId valor) {
        return valor != null ? valor.toHexString() : null;
    }

    // =========================================================================
    // CRIAÇÃO DA TRILHA (MANUAL OU IMPORTADA DE EDITAL PUBLICADO)
    // =========================================================================

    public TrilhaDetalheDTO criarTrilha(CriarTrilhaRequest request) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();

        // criação de trilha limitada a 30/min por usuário (contenção de escrita em massa).
        rateLimitService.verificarLimiteCriacaoTrilha(usuarioId.toHexString());

        if (repository.contar(usuarioId) >= maxTrilhasPorUsuario) {
            throw new ResourceLimitExceededException(
                    "Limite de " + maxTrilhasPorUsuario + " trilhas por usuário atingido.");
        }

        boolean temEdital = request.editalId() != null && !request.editalId().isBlank();
        boolean temDisciplinasIniciais = !request.disciplinasIniciais().isEmpty();

        if (temEdital && temDisciplinasIniciais) {
            throw new ValidationException("editalId",
                    "Informe apenas um destes campos: editalId ou disciplinasIniciais.");
        }

        ObjectId trilhaId = new ObjectId();
        Instant agora = Instant.now();

        if (temEdital) {
            criarTrilhaPorEdital(usuarioId, trilhaId, request, agora);
        } else {
            criarTrilhaManual(usuarioId, trilhaId, request, agora);
        }

        return buscarDetalhe(trilhaId.toHexString());
    }

    /**
 * Importa o edital como snapshot da trilha (requisito §4.1: nova versão do edital não altera
 * a trilha do usuário). Referência inválida, inexistente ou não publicada é falha do campo
 * {@code editalId} no corpo ({@code 400 VALIDATION_ERROR}), não {@code 404}: o recurso do path
 * é a trilha que está sendo criada.
 */
    private void criarTrilhaPorEdital(ObjectId usuarioId, ObjectId trilhaId, CriarTrilhaRequest request, Instant agora) {
        Edital edital = editalRepository.buscar(request.editalId())
                .filter(item -> item.status() == StatusEdital.PUBLICADA)
                .orElseThrow(() -> new ValidationException("editalId", "Edital publicado não encontrado."));

        if (edital.disciplinas() != null && edital.disciplinas().size() > MAX_DISCIPLINAS_POR_TRILHA) {
            throw new ResourceLimitExceededException(
                    "A trilha pode ter no máximo " + MAX_DISCIPLINAS_POR_TRILHA + " disciplinas.");
        }

        String nome = (request.nome() != null && !request.nome().isBlank()) ? request.nome().trim() : edital.nome();
        String cargo = request.cargo() != null && !request.cargo().isBlank()
                ? request.cargo().trim()
                : juntarAreaECargo(edital.area(), edital.cargo());
        String banca = (request.banca() != null && !request.banca().isBlank()) ? request.banca().trim() : edital.banca();
        LocalDate dataProva = request.dataProva() != null ? request.dataProva() : edital.dataProva();
        String cor = (request.cor() != null && !request.cor().isBlank()) ? request.cor().trim() : COR_PADRAO_TRILHA;
        ObjectId origemEditalId = BsonIds.isValid(edital.id()) ? new ObjectId(edital.id()) : null;

        repository.inserir(new TrilhaRepository.DadosInsercaoTrilha(
                usuarioId, trilhaId, nome, cargo, banca, dataProva, cor, origemEditalId, edital.versao(), null, agora));

        disciplinaRepository.inserirEmLote(construirDisciplinasDoEdital(usuarioId, trilhaId, edital, agora));
    }

    private List<Disciplina> construirDisciplinasDoEdital(ObjectId usuarioId, ObjectId trilhaId, Edital edital, Instant agora) {
        List<Disciplina> disciplinasNorm = new ArrayList<>();
        int ordem = 0;
        for (var editalDisc : edital.disciplinas()) {
            List<TopicoEmbutido> topicos = new ArrayList<>();
            int tOrdem = 0;
            if (editalDisc.topicos() != null) {
                for (var t : editalDisc.topicos()) {
                    topicos.add(new TopicoEmbutido(BsonIds.novoIdHex(), t.nome(), tOrdem++));
                }
            }
            disciplinasNorm.add(new Disciplina(
                    new ObjectId(),
                    usuarioId,
                    trilhaId,
                    editalDisc.nome(),
                    editalDisc.cor(),
                    editalDisc.peso() != null ? editalDisc.peso() : 1,
                    ordem++,
                    topicos,
                    agora,
                    agora));
        }
        return disciplinasNorm;
    }

    private void criarTrilhaManual(ObjectId usuarioId, ObjectId trilhaId, CriarTrilhaRequest request, Instant agora) {
        String cargo = request.cargo() != null && !request.cargo().isBlank() ? request.cargo().trim() : null;
        String banca = request.banca() != null ? request.banca().trim() : null;
        String cor = (request.cor() != null && !request.cor().isBlank()) ? request.cor().trim() : COR_PADRAO_TRILHA;

        List<CriarDisciplinaRequest> iniciais = request.disciplinasIniciais();
        if (iniciais.size() > MAX_DISCIPLINAS_POR_TRILHA) {
            throw new ResourceLimitExceededException(
                    "A trilha pode ter no máximo " + MAX_DISCIPLINAS_POR_TRILHA + " disciplinas.");
        }

        repository.inserir(new TrilhaRepository.DadosInsercaoTrilha(
                usuarioId, trilhaId, request.nome().trim(), cargo, banca, request.dataProva(), cor, null, 0, null, agora));

        if (!iniciais.isEmpty()) {
            disciplinaRepository.inserirEmLote(construirDisciplinasManuais(usuarioId, trilhaId, iniciais, agora));
        }
    }

    private List<Disciplina> construirDisciplinasManuais(
            ObjectId usuarioId, ObjectId trilhaId, List<CriarDisciplinaRequest> iniciais, Instant agora) {
        List<Disciplina> disciplinasNorm = new ArrayList<>();
        for (int ordem = 0; ordem < iniciais.size(); ordem++) {
            disciplinasNorm.add(construirDisciplinaManual(usuarioId, trilhaId, iniciais.get(ordem), ordem, agora));
        }
        return disciplinasNorm;
    }

    private Disciplina construirDisciplinaManual(
            ObjectId usuarioId, ObjectId trilhaId, CriarDisciplinaRequest request, int ordem, Instant agora) {
        validarLimiteTopicos(request.topicos());
        return new Disciplina(
                obterIdDisciplina(request.id()),
                usuarioId,
                trilhaId,
                request.nome().trim(),
                request.cor(),
                request.peso() != null ? request.peso() : 1,
                ordem,
                construirTopicosManuais(request.topicos()),
                agora,
                agora);
    }

    private void validarLimiteTopicos(List<CriarTopicoRequest> topicos) {
        if (topicos != null && topicos.size() > MAX_TOPICOS_POR_DISCIPLINA) {
            throw new ResourceLimitExceededException(MSG_LIMITE_TOPICOS);
        }
    }

    private ObjectId obterIdDisciplina(String id) {
        return id != null && BsonIds.isValid(id) ? new ObjectId(id) : new ObjectId();
    }

    private List<TopicoEmbutido> construirTopicosManuais(List<CriarTopicoRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        List<TopicoEmbutido> topicos = new ArrayList<>();
        int ordemPadrao = 0;
        for (CriarTopicoRequest request : requests) {
            String id = request.id() != null && BsonIds.isValid(request.id()) ? request.id() : BsonIds.novoIdHex();
            int ordem = request.ordem() != null ? request.ordem() : ordemPadrao++;
            topicos.add(new TopicoEmbutido(id, request.nome(), ordem));
        }
        return topicos;
    }

    // =========================================================================
    // METADADOS E IMPACTO DE EXCLUSÃO
    // =========================================================================

    public TrilhaDetalheDTO atualizarMetadados(String trilhaIdStr, AtualizarMetadadosTrilhaRequest request) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");

        Document doc = repository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

        String cor = (request.cor() != null && !request.cor().isBlank()) ? request.cor().trim() : doc.getString("cor");

        repository.atualizarMetadados(usuarioId, trilhaId, new TrilhaRepository.MetadadosTrilha(
                request.nome().trim(),
                request.obterCargoEfetivo(),
                request.banca() != null ? request.banca().trim() : null,
                request.dataProva(),
                cor,
                Instant.now()));
        return buscarDetalhe(trilhaIdStr);
    }

    /**
 * Contagens exibidas na confirmação de exclusão. Cadernos vinculados não são excluídos
 * pela trilha, são apenas desvinculados.
 */
    public TrilhaImpactoExclusaoDTO obterImpactoExclusao(String trilhaIdStr) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");

        repository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

        return new TrilhaImpactoExclusaoDTO(
                disciplinaRepository.contarPorTrilha(usuarioId, trilhaId),
                cronogramaRepository.contarPorTrilha(usuarioId, trilhaId),
                sessaoRepository.contarPorTrilha(usuarioId, trilhaId),
                cadernoRepository.contarAtivosPorOrigemTrilha(usuarioId, trilhaId));
    }

    /**
 * Exclui a trilha e tudo que depende dela, sob o lease da trilha.
 *
 * <p><b>Não há transação multi-documento</b> — a sequência é:</p>
 * <ol>
 * <li>remove {@code progresso_topicos} da trilha;</li>
 * <li>fotografa IDs de disciplinas e tópicos e remove {@code disciplinas} da trilha;</li>
 * <li>remove {@code sessoes} da trilha (histórico apagado com a trilha, ação irreversível
 * );</li>
 * <li>remove {@code cronogramas}, {@code blocos_cronograma} e planejamento herdado,
 * desvinculando cadernos, seções e notas;</li>
 * <li>remove a trilha por último.</li>
 * </ol>
 *
 * <p>Se um passo falhar, o lease é liberado e os passos já concluídos permanecem aplicados
 * (efeito parcial). Repetir a operação com o mesmo ID responde {@code 404} porque a trilha já
 * não existe; não há estado intermediário que impeça a limpeza posterior das coleções filhas,
 * que deixam de ser alcançáveis sem a trilha.</p>
 */
    public void excluirTrilha(String trilhaIdStr) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");

        repository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

        leaseTrilhaService.executarComLease(usuarioId, trilhaId, () -> {
            progressoRepository.excluirPorTrilha(usuarioId, trilhaId);

            List<Disciplina> disciplinasTrilha = disciplinaRepository.listarPorTrilha(usuarioId, trilhaId);
            List<ObjectId> discIds = new ArrayList<>();
            List<String> topicoIds = new ArrayList<>();
            for (Disciplina d : disciplinasTrilha) {
                discIds.add(d.id());
                for (TopicoEmbutido t : d.topicos()) {
                    topicoIds.add(t.id());
                }
            }

            disciplinaRepository.excluirTodasDaTrilha(usuarioId, trilhaId);
            sessaoRepository.excluirPorTrilha(usuarioId, trilhaId);
            blocoCronogramaRepository.excluirPorTrilha(usuarioId, trilhaId);
            cronogramaRepository.excluirPorTrilha(usuarioId, trilhaId);
            repository.desvincularDadosTrilhaENotas(usuarioId, trilhaId, discIds, topicoIds);
            repository.excluir(usuarioId, trilhaId);
        });
    }

    // =========================================================================
    // CRUD GRANULAR DE DISCIPLINAS E TÓPICOS
    // =========================================================================

    public DisciplinaDTO criarDisciplina(String trilhaIdStr, CriarDisciplinaRequest request) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");

        repository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

        if (disciplinaRepository.contarPorTrilha(usuarioId, trilhaId) >= MAX_DISCIPLINAS_POR_TRILHA) {
            throw new ResourceLimitExceededException(
                    "Limite de " + MAX_DISCIPLINAS_POR_TRILHA + " disciplinas por trilha atingido.");
        }

        validarLimiteTopicos(request.topicos());

        long existentes = disciplinaRepository.contarPorTrilha(usuarioId, trilhaId);
        Instant agora = Instant.now();
        Disciplina disciplina = new Disciplina(
                obterIdDisciplina(request.id()),
                usuarioId,
                trilhaId,
                request.nome().trim(),
                request.cor(),
                request.peso() != null ? request.peso() : 1,
                (int) existentes,
                construirTopicosManuais(request.topicos()),
                agora,
                agora);

        disciplinaRepository.inserir(disciplina);

        List<TopicoDTO> topicosDTO = disciplina.topicos().stream()
                .map(t -> new TopicoDTO(t.id(), t.nome(), t.ordem(), false))
                .toList();

        return new DisciplinaDTO(
                disciplina.idHex(),
                trilhaId.toHexString(),
                disciplina.nome(),
                disciplina.cor(),
                disciplina.peso(),
                disciplina.ordem(),
                topicosDTO,
                topicosDTO.size(),
                0,
                0.0);
    }

    public DisciplinaDTO atualizarDisciplina(String trilhaIdStr, String disciplinaIdStr, AtualizarDisciplinaRequest request) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");
        ObjectId disciplinaId = BsonIds.parse(disciplinaIdStr, "disciplinaId");

        Disciplina existente = disciplinaRepository.buscarPorId(usuarioId, trilhaId, disciplinaId)
                .orElseThrow(() -> new NotFoundException(ERRO_DISCIPLINA_NAO_ENCONTRADA));

        List<TopicoEmbutido> novosTopicos = processarNovosTopicos(usuarioId, trilhaId, existente, request.topicos());

        Disciplina atualizada = new Disciplina(
                disciplinaId,
                usuarioId,
                trilhaId,
                request.nome().trim(),
                request.cor() != null ? request.cor() : existente.cor(),
                request.peso() != null ? request.peso() : existente.peso(),
                existente.ordem(),
                novosTopicos,
                existente.criadoEm(),
                Instant.now());

        disciplinaRepository.atualizar(atualizada);
        // Regra normativa §5.3: renomear/alterar cor atualiza o snapshot apenas nos cronogramas
        // ATIVOS da trilha, sem tocar disciplinaId.
        List<ObjectId> cronogramasAtivosIds = cronogramaRepository.listarPorTrilha(usuarioId, trilhaId)
                .stream()
                .filter(Cronograma::ativo)
                .map(Cronograma::id)
                .toList();
        blocoCronogramaRepository.atualizarSnapshotsDisciplina(
                usuarioId, trilhaId, disciplinaId, atualizada.nome(), atualizada.cor(), cronogramasAtivosIds);

        Set<String> concluidosTrilha = progressoRepository.listarTopicosConcluidosIds(usuarioId, trilhaId);
        int dConcluidos = 0;
        List<TopicoDTO> topicosDTO = new ArrayList<>();
        for (TopicoEmbutido t : novosTopicos) {
            boolean concluido = concluidosTrilha.contains(t.id());
            if (concluido) {
                dConcluidos++;
            }
            topicosDTO.add(new TopicoDTO(t.id(), t.nome(), t.ordem(), concluido));
        }
        double pct = !novosTopicos.isEmpty() ? ((double) dConcluidos / novosTopicos.size()) * 100.0 : 0.0;

        return new DisciplinaDTO(
                disciplinaId.toHexString(),
                trilhaId.toHexString(),
                atualizada.nome(),
                atualizada.cor(),
                atualizada.peso(),
                atualizada.ordem(),
                topicosDTO,
                novosTopicos.size(),
                dConcluidos,
                pct);
    }

    /**
 * Reconcilia a lista de tópicos enviada com a persistida. IDs desconhecidos são referência
 * inválida no corpo ({@code 400 VALIDATION_ERROR} em {@code topicos}), nunca {@code 404};
 * tópicos removidos têm o progresso apagado (requisito §4.2).
 */
    private List<TopicoEmbutido> processarNovosTopicos(
            ObjectId usuarioId, ObjectId trilhaId, Disciplina existente, List<CriarTopicoRequest> topicosReq) {
        if (topicosReq == null) {
            return existente.topicos();
        }
        validarLimiteTopicos(topicosReq);

        Set<String> idsAtuais = existente.topicos().stream()
                .map(TopicoEmbutido::id)
                .collect(Collectors.toSet());

        List<TopicoEmbutido> novosTopicos = new ArrayList<>();
        int ordemPadrao = 0;
        for (CriarTopicoRequest tReq : topicosReq) {
            String topicoId = tReq.id();
            if (topicoId != null && !topicoId.isBlank()) {
                if (!idsAtuais.contains(topicoId)) {
                    throw new ValidationException("topicos", "Tópico com ID desconhecido: " + topicoId + ".");
                }
            } else {
                topicoId = BsonIds.novoIdHex();
            }
            int ordem = tReq.ordem() != null ? tReq.ordem() : ordemPadrao++;
            novosTopicos.add(new TopicoEmbutido(topicoId, tReq.nome().trim(), ordem));
        }

        Set<String> idsNovos = novosTopicos.stream().map(TopicoEmbutido::id).collect(Collectors.toSet());
        Set<String> idsRemovidos = idsAtuais.stream().filter(id -> !idsNovos.contains(id)).collect(Collectors.toSet());
        if (!idsRemovidos.isEmpty()) {
            progressoRepository.excluirPorTopicos(usuarioId, trilhaId, idsRemovidos);
        }
        return novosTopicos;
    }

    public void reordenarDisciplinas(String trilhaIdStr, ReordenarDisciplinasRequest request) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");

        repository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

        List<Disciplina> ativas = disciplinaRepository.listarPorTrilha(usuarioId, trilhaId);
        List<String> idsRecebidos = request.disciplinaIds();

        if (idsRecebidos == null || idsRecebidos.size() != ativas.size()) {
            throw new ValidationException("disciplinaIds", MSG_REORDENACAO_INTEGRA);
        }

        List<ObjectId> ordemValidada = new ArrayList<>(idsRecebidos.size());
        for (String id : idsRecebidos) {
            ordemValidada.add(BsonIds.parse(id, "disciplinaId"));
        }

        Set<String> conjuntoRecebido = new HashSet<>(idsRecebidos);
        Set<String> conjuntoAtivo = ativas.stream().map(Disciplina::idHex).collect(Collectors.toSet());

        if (conjuntoRecebido.size() != ativas.size() || !conjuntoRecebido.equals(conjuntoAtivo)) {
            throw new ValidationException("disciplinaIds", MSG_REORDENACAO_INTEGRA);
        }

        for (int i = 0; i < ordemValidada.size(); i++) {
            disciplinaRepository.atualizarOrdem(usuarioId, trilhaId, ordemValidada.get(i), i);
        }
    }

    /**
 * Exclui uma disciplina sob o lease da trilha.
 *
 * <p><b>Efeitos parciais</b> (sem transação multi-documento), nesta ordem:</p>
 * <ol>
 * <li>remove a disciplina;</li>
 * <li>remove o progresso dos tópicos dessa disciplina;</li>
 * <li>remove os blocos futuros pendentes da disciplina nos cronogramas.</li>
 * </ol>
 * <p>Sessões de estudo são preservadas por padrão (requisitos §6.2) e só são apagadas por ação
 * explícita do usuário.</p>
 */
    public void excluirDisciplina(String trilhaIdStr, String disciplinaIdStr) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");
        ObjectId disciplinaId = BsonIds.parse(disciplinaIdStr, "disciplinaId");

        disciplinaRepository.buscarPorId(usuarioId, trilhaId, disciplinaId)
                .orElseThrow(() -> new NotFoundException(ERRO_DISCIPLINA_NAO_ENCONTRADA));

        leaseTrilhaService.executarComLease(usuarioId, trilhaId, () -> {
            disciplinaRepository.excluir(usuarioId, trilhaId, disciplinaId);
            progressoRepository.excluirPorDisciplina(usuarioId, trilhaId, disciplinaId);
            blocoCronogramaRepository.excluirBlocosFuturosPendentesDisciplina(
                    usuarioId, trilhaId, disciplinaId, timezoneContext.hojeLocal());
        });
    }

    // =========================================================================
    // PROGRESSO DE TÓPICOS
    // =========================================================================

    /**
 * Marca o tópico como concluído de forma idempotente. A disciplina dona do tópico é resolvida
 * dentro da trilha do path <b>e</b> do usuário autenticado: tópico de outra trilha ou de outro
 * usuário responde {@code 404}, sem revelar existência.
 */
    public ProgressoTopicoDTO marcarTopicoConcluido(String trilhaIdStr, String topicoId) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");
        String topicoIdValidado = validarTopicoId(topicoId);

        repository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

        Disciplina disciplina = disciplinaRepository.buscarPorTopicoId(usuarioId, trilhaId, topicoIdValidado)
                .orElseThrow(() -> new NotFoundException(ERRO_TOPICO_NAO_ENCONTRADO));

        progressoRepository.marcarConcluido(usuarioId, trilhaId, disciplina.id(), topicoIdValidado);
        return ProgressoTopicoDTO.concluido(topicoIdValidado, Instant.now());
    }

    /**
 * Desmarca o tópico de forma idempotente ({@code 204} mesmo se já não havia progresso).
 *
 * <p>A trilha precisa existir para o usuário ({@code 404} caso contrário). O tópico não é
 * revalidado porque a remoção é segura por construção: o filtro inclui {@code usuarioId},
 * {@code trilhaId} e {@code topicoId}, então nenhum registro de outro usuário ou de outra
 * trilha pode ser afetado.</p>
 */
    public void desmarcarTopicoConcluido(String trilhaIdStr, String topicoId) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");
        String topicoIdValidado = validarTopicoId(topicoId);

        repository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

        progressoRepository.desmarcarConcluido(usuarioId, trilhaId, topicoIdValidado);
    }

    /** IDs HTTP são hexadecimais de 24 caracteres; malformado é {@code 400 INVALID_IDENTIFIER}. */
    private String validarTopicoId(String topicoId) {
        return BsonIds.parse(topicoId, "topicoId").toHexString();
    }

    private CronogramaAtivoResumoDTO obterCronogramaAtivoResumo(Document doc, ObjectId usuarioId) {
        ObjectId cronogramaAtivoId = doc.getObjectId("cronogramaAtivoId");
        if (cronogramaAtivoId == null) {
            return null;
        }
        Optional<Document> cronograma = repository.buscarCronogramaResumo(usuarioId, cronogramaAtivoId);
        if (cronograma.isEmpty()) {
            return null;
        }
        Document cronoDoc = cronograma.get();
        return new CronogramaAtivoResumoDTO(
                cronogramaAtivoId.toHexString(),
                cronoDoc.getString("modalidade"),
                cronoDoc.getString("dataInicio"),
                cronoDoc.getString("dataFim"));
    }

    private String juntarAreaECargo(String area, String cargo) {
        if (area != null && !area.isBlank() && cargo != null && !cargo.isBlank()) {
            return area.trim() + " - " + cargo.trim();
        }
        if (area != null && !area.isBlank()) {
            return area.trim();
        }
        return cargo != null ? cargo.trim() : null;
    }
}
