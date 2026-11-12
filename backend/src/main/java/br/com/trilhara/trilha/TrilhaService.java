package br.com.trilhara.trilha;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.ConfigProvider;

import br.com.trilhara.cronograma.BlocoCronogramaRepository;
import br.com.trilhara.edital.Edital;
import br.com.trilhara.edital.EditalRepository;
import br.com.trilhara.edital.StatusEdital;
import br.com.trilhara.sessao.SessaoRepository;
import br.com.trilhara.shared.TimezoneContext;
import br.com.trilhara.shared.erro.CodigoErro;
import br.com.trilhara.shared.erro.DomainConflictException;
import br.com.trilhara.shared.erro.ResourceLimitExceededException;
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
import br.com.trilhara.trilha.dto.SalvarTrilhaRequest;
import br.com.trilhara.trilha.dto.TopicoDTO;
import br.com.trilhara.trilha.dto.TrilhaDetalheDTO;
import br.com.trilhara.trilha.dto.TrilhaResumoDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class TrilhaService {

    private static final String COR_PADRAO_TRILHA = "#8F9E8B";
    private static final String ERRO_TRILHA_NAO_ENCONTRADA = "Trilha não encontrada";
    private static final String CAMPO_CARGO = "cargo";
    private static final String CAMPO_AREA_CARGO = "areaCargo";
    private static final String MSG_CONFLITO_LEASE = "Geração de cronograma ou modificação concorrente em andamento para esta trilha.";

    public static final int MAX_TRILHAS_PADRAO = 50;
    public static final int MAX_DISCIPLINAS_POR_TRILHA = 100;
    public static final int MAX_TOPICOS_POR_DISCIPLINA = 500;

    private final TrilhaRepository repository;
    private final DisciplinaRepository disciplinaRepository;
    private final ProgressoTopicoRepository progressoRepository;
    private final EditalRepository editalRepository;
    private final SessaoRepository sessaoRepository;
    private final BlocoCronogramaRepository blocoCronogramaRepository;
    private final TimezoneContext timezoneContext;
    private final int maxTrilhasPorUsuario;

    @Inject
    public TrilhaService(
            TrilhaRepository repository,
            DisciplinaRepository disciplinaRepository,
            ProgressoTopicoRepository progressoRepository,
            EditalRepository editalRepository,
            SessaoRepository sessaoRepository,
            BlocoCronogramaRepository blocoCronogramaRepository,
            TimezoneContext timezoneContext) {
        this.repository = repository;
        this.disciplinaRepository = disciplinaRepository;
        this.progressoRepository = progressoRepository;
        this.editalRepository = editalRepository;
        this.sessaoRepository = sessaoRepository;
        this.blocoCronogramaRepository = blocoCronogramaRepository;
        this.timezoneContext = timezoneContext;
        this.maxTrilhasPorUsuario = ConfigProvider.getConfig()
                .getOptionalValue("trilhara.trilhas.max-por-usuario", Integer.class)
                .orElse(MAX_TRILHAS_PADRAO);
    }

    // =========================================================================
    // FASE 2: ENDPOINTS MODERNOS E NORMALIZADOS
    // =========================================================================

    public List<TrilhaResumoDTO> listarResumo() {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        List<Document> documentos = repository.listarEntidades(usuarioId);
        List<TrilhaResumoDTO> resumos = new ArrayList<>();

        for (Document doc : documentos) {
            ObjectId trilhaId = doc.getObjectId("_id");
            List<Disciplina> disciplinas = disciplinaRepository.listarPorTrilha(usuarioId, trilhaId);
            int totalDisciplinas = disciplinas.size();
            int totalTopicos = disciplinas.stream().mapToInt(d -> d.topicos().size()).sum();
            int topicosConcluidos = (int) progressoRepository.contarConcluidosPorTrilha(usuarioId, trilhaId);
            double pct = totalTopicos > 0 ? ((double) topicosConcluidos / totalTopicos) * 100.0 : 0.0;

            CronogramaAtivoResumoDTO cronoResumo = obterCronogramaAtivoResumo(doc, usuarioId);
            ObjectId origemEditalId = doc.getObjectId("origemEditalId");
            String cargo = doc.getString(CAMPO_CARGO);
            if (cargo == null) {
                cargo = doc.getString(CAMPO_AREA_CARGO);
            }

            resumos.add(new TrilhaResumoDTO(
                    trilhaId.toHexString(),
                    usuarioId.toHexString(),
                    usuarioId.toHexString(),
                    doc.getString("nome"),
                    cargo,
                    cargo,
                    doc.getString("banca"),
                    doc.getString("dataProva"),
                    doc.getString("cor"),
                    origemEditalId != null ? origemEditalId.toHexString() : null,
                    totalDisciplinas,
                    totalTopicos,
                    topicosConcluidos,
                    pct,
                    cronoResumo,
                    BsonDates.toInstant(doc, "criadoEm"),
                    BsonDates.toInstant(doc, "atualizadoEm")
            ));
        }
        return resumos;
    }

    public TrilhaDetalheDTO buscarDetalhe(String trilhaIdStr) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");

        Document doc = repository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

        List<Disciplina> disciplinas = disciplinaRepository.listarPorTrilha(usuarioId, trilhaId);
        Set<String> concluidosTrilha = progressoRepository.listarTopicosConcluidosIds(usuarioId, trilhaId);

        int totalDisciplinas = disciplinas.size();
        int totalTopicos = 0;
        int topicosConcluidos = concluidosTrilha.size();

        List<DisciplinaDTO> disciplinasDTO = new ArrayList<>();
        Map<String, ProgressoTopicoDTO> progressoMap = new HashMap<>();

        for (String cId : concluidosTrilha) {
            progressoMap.put(cId, new ProgressoTopicoDTO(true, null));
        }

        for (Disciplina d : disciplinas) {
            int dTotal = d.topicos().size();
            totalTopicos += dTotal;
            int dConcluidos = 0;
            List<TopicoDTO> topicosDTO = new ArrayList<>();

            for (TopicoEmbutido t : d.topicos()) {
                boolean conc = concluidosTrilha.contains(t.id());
                if (conc) {
                    dConcluidos++;
                }
                topicosDTO.add(new TopicoDTO(t.id(), t.nome(), t.ordem(), conc));
            }

            double dPct = dTotal > 0 ? ((double) dConcluidos / dTotal) * 100.0 : 0.0;
            disciplinasDTO.add(new DisciplinaDTO(
                    d.id().toHexString(),
                    trilhaId.toHexString(),
                    d.nome(),
                    d.cor(),
                    d.peso(),
                    d.ordem(),
                    d.cadernoLink(),
                    topicosDTO,
                    dTotal,
                    dConcluidos,
                    dPct
            ));
        }

        double percentualGeral = totalTopicos > 0 ? ((double) topicosConcluidos / totalTopicos) * 100.0 : 0.0;
        CronogramaAtivoResumoDTO cronoResumo = obterCronogramaAtivoResumo(doc, usuarioId);
        ObjectId origemEditalId = doc.getObjectId("origemEditalId");
        String cargo = doc.getString(CAMPO_CARGO);
        if (cargo == null) {
            cargo = doc.getString(CAMPO_AREA_CARGO);
        }

        return new TrilhaDetalheDTO(
                trilhaId.toHexString(),
                usuarioId.toHexString(),
                usuarioId.toHexString(),
                doc.getString("nome"),
                cargo,
                cargo,
                doc.getString("banca"),
                doc.getString("dataProva"),
                doc.getString("cor"),
                origemEditalId != null ? origemEditalId.toHexString() : null,
                totalDisciplinas,
                totalTopicos,
                topicosConcluidos,
                percentualGeral,
                cronoResumo,
                disciplinasDTO,
                concluidosTrilha,
                progressoMap,
                BsonDates.toInstant(doc, "criadoEm"),
                BsonDates.toInstant(doc, "atualizadoEm")
        );
    }

    public TrilhaDetalheDTO criarTrilha(CriarTrilhaRequest request) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();

        if (repository.contar(usuarioId) >= maxTrilhasPorUsuario) {
            throw new ResourceLimitExceededException(
                    "Limite de " + maxTrilhasPorUsuario + " trilhas por usuário atingido.");
        }

        boolean temEdital = request.editalId() != null && !request.editalId().isBlank();
        boolean temDisciplinasIniciais = !request.obterDisciplinasEfetivas().isEmpty();

        if (temEdital && temDisciplinasIniciais) {
            throw new BadRequestException("A API não aceita simultaneamente payload de edital e disciplinas iniciais.");
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

    private void criarTrilhaPorEdital(ObjectId usuarioId, ObjectId trilhaId, CriarTrilhaRequest request, Instant agora) {
        Edital edital = editalRepository.buscar(request.editalId())
                .filter(item -> item.status() == StatusEdital.PUBLICADA)
                .orElseThrow(() -> new NotFoundException("Edital publicado não encontrado"));

        String nome = (request.nome() != null && !request.nome().isBlank()) ? request.nome().trim() : edital.nome();
        String cargo = request.obterCargoEfetivo() != null ? request.obterCargoEfetivo() : juntarAreaECargo(edital.area(), edital.cargo());
        String banca = (request.banca() != null && !request.banca().isBlank()) ? request.banca().trim() : edital.banca();
        LocalDate dataProva = request.dataProva() != null ? request.dataProva() : edital.dataProva();
        String cor = (request.cor() != null && !request.cor().isBlank()) ? request.cor().trim() : COR_PADRAO_TRILHA;
        ObjectId origemEditalId = BsonIds.isValid(edital.id()) ? new ObjectId(edital.id()) : null;

        repository.inserir(new TrilhaRepository.DadosInsercaoTrilha(
                usuarioId, trilhaId, nome, cargo, banca, dataProva, cor, origemEditalId, edital.versao(), null, agora
        ));

        List<Disciplina> disciplinasNorm = construirDisciplinasDoEdital(usuarioId, trilhaId, edital, agora);
        disciplinaRepository.inserirEmLote(disciplinasNorm);
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
                    null,
                    topicos,
                    agora,
                    agora
            ));
        }
        return disciplinasNorm;
    }

    private void criarTrilhaManual(ObjectId usuarioId, ObjectId trilhaId, CriarTrilhaRequest request, Instant agora) {
        String nome = request.nome().trim();
        String cargo = request.obterCargoEfetivo();
        String banca = request.banca() != null ? request.banca().trim() : null;
        LocalDate dataProva = request.dataProva();
        String cor = (request.cor() != null && !request.cor().isBlank()) ? request.cor().trim() : COR_PADRAO_TRILHA;

        repository.inserir(new TrilhaRepository.DadosInsercaoTrilha(
                usuarioId, trilhaId, nome, cargo, banca, dataProva, cor, null, 0, null, agora
        ));

        List<CriarDisciplinaRequest> iniciais = request.obterDisciplinasEfetivas();
        if (!iniciais.isEmpty()) {
            if (iniciais.size() > MAX_DISCIPLINAS_POR_TRILHA) {
                throw new ResourceLimitExceededException("A trilha pode ter no máximo " + MAX_DISCIPLINAS_POR_TRILHA + " disciplinas.");
            }
            List<Disciplina> disciplinasNorm = construirDisciplinasManuais(usuarioId, trilhaId, iniciais, agora);
            disciplinaRepository.inserirEmLote(disciplinasNorm);
        }
    }

    private List<Disciplina> construirDisciplinasManuais(ObjectId usuarioId, ObjectId trilhaId, List<CriarDisciplinaRequest> iniciais, Instant agora) {
        List<Disciplina> disciplinasNorm = new ArrayList<>();
        for (int ordem = 0; ordem < iniciais.size(); ordem++) {
            disciplinasNorm.add(construirDisciplinaManual(usuarioId, trilhaId, iniciais.get(ordem), ordem, agora));
        }
        return disciplinasNorm;
    }

    private Disciplina construirDisciplinaManual(
            ObjectId usuarioId, ObjectId trilhaId, CriarDisciplinaRequest request, int ordem, Instant agora) {
        validarLimiteTopicos(request);
        return new Disciplina(
                obterIdDisciplina(request),
                usuarioId,
                trilhaId,
                request.nome().trim(),
                request.cor(),
                request.peso() != null ? request.peso() : 1,
                ordem,
                request.cadernoLink(),
                construirTopicosManuais(request.topicos()),
                agora,
                agora);
    }

    private void validarLimiteTopicos(CriarDisciplinaRequest request) {
        if (request.topicos() != null && request.topicos().size() > MAX_TOPICOS_POR_DISCIPLINA) {
            throw new ResourceLimitExceededException("Cada disciplina pode ter no máximo " + MAX_TOPICOS_POR_DISCIPLINA + " tópicos.");
        }
    }

    private ObjectId obterIdDisciplina(CriarDisciplinaRequest request) {
        return request.id() != null && BsonIds.isValid(request.id()) ? new ObjectId(request.id()) : new ObjectId();
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

    public TrilhaDetalheDTO atualizarMetadados(String trilhaIdStr, AtualizarMetadadosTrilhaRequest request) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");

        Document doc = repository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

        String nome = request.nome().trim();
        String cargo = request.obterCargoEfetivo();
        String banca = request.banca() != null ? request.banca().trim() : null;
        LocalDate dataProva = request.dataProva();
        String cor = (request.cor() != null && !request.cor().isBlank()) ? request.cor().trim() : doc.getString("cor");

        repository.atualizarMetadados(usuarioId, trilhaId, new TrilhaRepository.MetadadosTrilha(
                nome, cargo, banca, dataProva, cor, Instant.now()
        ));
        return buscarDetalhe(trilhaIdStr);
    }

    public void excluirTrilha(String trilhaIdStr) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");

        repository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

        String leaseToken = UUID.randomUUID().toString();
        Instant expiraEm = Instant.now().plusSeconds(30);
        boolean adquiriu = repository.adquirirLease(usuarioId, trilhaId, leaseToken, expiraEm);
        if (!adquiriu) {
            throw new DomainConflictException(CodigoErro.SCHEDULE_GENERATION_IN_PROGRESS, MSG_CONFLITO_LEASE);
        }

        try {
            // 1. Remove progresso de tópicos
            progressoRepository.excluirPorTrilha(usuarioId, trilhaId);

            // 2. Busca disciplinas da trilha ANTES de remover disciplinas para desvincular notas/cadernos corretamente!
            List<Disciplina> disciplinasTrilha = disciplinaRepository.listarPorTrilha(usuarioId, trilhaId);
            List<ObjectId> discIds = new ArrayList<>();
            List<String> topicoIds = new ArrayList<>();
            for (Disciplina d : disciplinasTrilha) {
                discIds.add(d.id());
                if (d.topicos() != null) {
                    for (TopicoEmbutido t : d.topicos()) {
                        topicoIds.add(t.id());
                    }
                }
            }

            // 3. Remove disciplinas da trilha
            disciplinaRepository.excluirTodasDaTrilha(usuarioId, trilhaId);

            // 4. Remove sessões de estudo da trilha
            sessaoRepository.excluirPorTrilha(usuarioId, trilhaId);

            // 5. Desvincula cadernos, seções, notas e remove cronogramas, blocos, planejamento
            repository.desvincularDadosTrilhaENotas(usuarioId, trilhaId, discIds, topicoIds);

            // 6. Remove a trilha por último
            repository.excluir(usuarioId, trilhaId);
        } catch (Exception e) {
            repository.liberarLease(usuarioId, trilhaId, leaseToken);
            throw e;
        }
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

        if (request.topicos() != null && request.topicos().size() > MAX_TOPICOS_POR_DISCIPLINA) {
            throw new ResourceLimitExceededException("Cada disciplina pode ter no máximo " + MAX_TOPICOS_POR_DISCIPLINA + " tópicos.");
        }

        int ordem = (int) disciplinaRepository.contarPorTrilha(usuarioId, trilhaId);
        Instant agora = Instant.now();
        ObjectId disciplinaId = new ObjectId();

        List<TopicoEmbutido> topicos = new ArrayList<>();
        int tOrdem = 0;
        if (request.topicos() != null) {
            for (CriarTopicoRequest tReq : request.topicos()) {
                String tId = (tReq.id() != null && BsonIds.isValid(tReq.id())) ? tReq.id() : BsonIds.novoIdHex();
                topicos.add(new TopicoEmbutido(tId, tReq.nome().trim(), tReq.ordem() != null ? tReq.ordem() : tOrdem++));
            }
        }

        Disciplina disciplina = new Disciplina(
                disciplinaId,
                usuarioId,
                trilhaId,
                request.nome().trim(),
                request.cor(),
                request.peso() != null ? request.peso() : 1,
                ordem,
                request.cadernoLink(),
                topicos,
                agora,
                agora
        );

        disciplinaRepository.inserir(disciplina);

        List<TopicoDTO> topicosDTO = topicos.stream()
                .map(t -> new TopicoDTO(t.id(), t.nome(), t.ordem(), false))
                .toList();

        return new DisciplinaDTO(
                disciplinaId.toHexString(),
                trilhaId.toHexString(),
                disciplina.nome(),
                disciplina.cor(),
                disciplina.peso(),
                disciplina.ordem(),
                disciplina.cadernoLink(),
                topicosDTO,
                topicos.size(),
                0,
                0.0
        );
    }

    public DisciplinaDTO atualizarDisciplina(String trilhaIdStr, String disciplinaIdStr, AtualizarDisciplinaRequest request) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");
        ObjectId disciplinaId = BsonIds.parse(disciplinaIdStr, "disciplinaId");

        Disciplina existente = disciplinaRepository.buscarPorId(usuarioId, trilhaId, disciplinaId)
                .orElseThrow(() -> new NotFoundException("Disciplina não encontrada"));

        List<TopicoEmbutido> novosTopicos = processarNovosTopicos(usuarioId, trilhaId, existente, request.topicos());

        Disciplina atualizada = new Disciplina(
                disciplinaId,
                usuarioId,
                trilhaId,
                request.nome().trim(),
                request.cor() != null ? request.cor() : existente.cor(),
                request.peso() != null ? request.peso() : existente.peso(),
                existente.ordem(),
                request.cadernoLink() != null ? request.cadernoLink() : existente.cadernoLink(),
                novosTopicos,
                existente.criadoEm(),
                Instant.now()
        );

        disciplinaRepository.atualizar(atualizada);
        blocoCronogramaRepository.atualizarSnapshotsDisciplina(
                usuarioId, trilhaId, disciplinaId, atualizada.nome(), atualizada.cor());

        Set<String> concluidosTrilha = progressoRepository.listarTopicosConcluidosIds(usuarioId, trilhaId);
        int dConcluidos = 0;
        List<TopicoDTO> topicosDTO = new ArrayList<>();
        for (TopicoEmbutido t : novosTopicos) {
            boolean conc = concluidosTrilha.contains(t.id());
            if (conc) {
                dConcluidos++;
            }
            topicosDTO.add(new TopicoDTO(t.id(), t.nome(), t.ordem(), conc));
        }
        double pct = !novosTopicos.isEmpty() ? ((double) dConcluidos / novosTopicos.size()) * 100.0 : 0.0;

        return new DisciplinaDTO(
                disciplinaId.toHexString(),
                trilhaId.toHexString(),
                atualizada.nome(),
                atualizada.cor(),
                atualizada.peso(),
                atualizada.ordem(),
                atualizada.cadernoLink(),
                topicosDTO,
                novosTopicos.size(),
                dConcluidos,
                pct
        );
    }

    private List<TopicoEmbutido> processarNovosTopicos(ObjectId usuarioId, ObjectId trilhaId, Disciplina existente, List<CriarTopicoRequest> topicosReq) {
        if (topicosReq == null) {
            return existente.topicos();
        }
        if (topicosReq.size() > MAX_TOPICOS_POR_DISCIPLINA) {
            throw new ResourceLimitExceededException("Cada disciplina pode ter no máximo " + MAX_TOPICOS_POR_DISCIPLINA + " tópicos.");
        }

        Set<String> idsAtuais = existente.topicos().stream()
                .map(TopicoEmbutido::id)
                .collect(Collectors.toSet());

        List<TopicoEmbutido> novosTopicos = new ArrayList<>();
        int tOrdem = 0;
        for (CriarTopicoRequest tReq : topicosReq) {
            String tId = tReq.id();
            if (tId != null && !tId.isBlank()) {
                if (!idsAtuais.contains(tId)) {
                    throw new BadRequestException("Tópico com ID desconhecido: " + tId);
                }
            } else {
                tId = BsonIds.novoIdHex();
            }
            novosTopicos.add(new TopicoEmbutido(tId, tReq.nome().trim(), tReq.ordem() != null ? tReq.ordem() : tOrdem++));
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
            throw new BadRequestException("A reordenação deve conter todas as disciplinas ativas da trilha exatamente uma vez.");
        }

        Set<String> conjuntoRecebido = new HashSet<>(idsRecebidos);
        Set<String> conjuntoAtivo = ativas.stream().map(Disciplina::idHex).collect(Collectors.toSet());

        if (conjuntoRecebido.size() != ativas.size() || !conjuntoRecebido.equals(conjuntoAtivo)) {
            throw new BadRequestException("A reordenação deve conter todas as disciplinas ativas da trilha exatamente uma vez.");
        }

        for (int i = 0; i < idsRecebidos.size(); i++) {
            ObjectId dId = new ObjectId(idsRecebidos.get(i));
            disciplinaRepository.atualizarOrdem(usuarioId, trilhaId, dId, i);
        }
    }

    public void excluirDisciplina(String trilhaIdStr, String disciplinaIdStr) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");
        ObjectId disciplinaId = BsonIds.parse(disciplinaIdStr, "disciplinaId");

        disciplinaRepository.buscarPorId(usuarioId, trilhaId, disciplinaId)
                .orElseThrow(() -> new NotFoundException("Disciplina não encontrada"));

        String leaseToken = UUID.randomUUID().toString();
        Instant expiraEm = Instant.now().plusSeconds(30);
        boolean adquiriu = repository.adquirirLease(usuarioId, trilhaId, leaseToken, expiraEm);
        if (!adquiriu) {
            throw new DomainConflictException(CodigoErro.SCHEDULE_GENERATION_IN_PROGRESS, MSG_CONFLITO_LEASE);
        }

        try {
            // 1. Remove a disciplina
            disciplinaRepository.excluir(usuarioId, trilhaId, disciplinaId);

            // 2. Remove progresso dos tópicos dessa disciplina
            progressoRepository.excluirPorDisciplina(usuarioId, trilhaId, disciplinaId);

            // 3. Remove blocos futuros pendentes dessa disciplina dos cronogramas (REQUISITOS §5.3 / PLANO 4.2 D.2)
            blocoCronogramaRepository.excluirBlocosFuturosPendentesDisciplina(
                    usuarioId, trilhaId, disciplinaId, timezoneContext.hojeLocal());

            // 4. Sessões de estudo são PRESERVADAS por padrão (REQUISITOS §6.2)
        } finally {
            repository.liberarLease(usuarioId, trilhaId, leaseToken);
        }
    }

    public void marcarTopicoConcluido(String trilhaIdStr, String topicoId) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");

        repository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

        Disciplina disciplina = disciplinaRepository.buscarPorTopicoId(usuarioId, trilhaId, topicoId)
                .orElseThrow(() -> new NotFoundException("Tópico não encontrado na trilha"));

        progressoRepository.marcarConcluido(usuarioId, trilhaId, disciplina.id(), topicoId);
    }

    public void desmarcarTopicoConcluido(String trilhaIdStr, String topicoId) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, "trilhaId");

        repository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

        progressoRepository.desmarcarConcluido(usuarioId, trilhaId, topicoId);
    }

    // =========================================================================
    // MÉTODOS DE COMPATIBILIDADE COM FASE 0, 1 E TESTES LEGADOS
    // =========================================================================

    public List<Trilha> listar() {
        return repository.listar(timezoneContext.donoId());
    }

    public Optional<Trilha> buscar(String id) {
        return repository.buscar(timezoneContext.donoId(), id);
    }

    public Trilha criar(SalvarTrilhaRequest request) {
        var detalhe = criarTrilha(new CriarTrilhaRequest(
                request.nome(),
                request.areaCargo(),
                request.areaCargo(),
                request.banca(),
                request.dataProva(),
                COR_PADRAO_TRILHA,
                null,
                converterDisciplinasParaCriacao(request.disciplinas()),
                null
        ));
        return repository.buscar(timezoneContext.donoId(), detalhe.id())
                .orElseThrow(() -> new NotFoundException("Trilha criada não encontrada"));
    }

    private List<CriarDisciplinaRequest> converterDisciplinasParaCriacao(List<SalvarTrilhaRequest.DisciplinaRequest> disciplinas) {
        if (disciplinas == null) {
            return List.of();
        }
        return disciplinas.stream()
                .map(disciplina -> new CriarDisciplinaRequest(
                        disciplina.id(),
                        disciplina.nome(),
                        disciplina.cor(),
                        disciplina.peso(),
                        disciplina.cadernoLink(),
                        converterTopicosParaCriacao(disciplina.topicos())))
                .toList();
    }

    private List<CriarTopicoRequest> converterTopicosParaCriacao(List<Edital.Topico> topicos) {
        if (topicos == null) {
            return List.of();
        }
        return topicos.stream()
                .map(topico -> new CriarTopicoRequest(topico.id(), topico.nome(), null))
                .toList();
    }

    public Trilha importar(String editalId) {
        var detalhe = criarTrilha(new CriarTrilhaRequest(
                null, null, null, null, null, null, editalId, null, null
        ));
        return repository.buscar(timezoneContext.donoId(), detalhe.id())
                .orElseThrow(() -> new NotFoundException("Trilha importada não encontrada"));
    }

    public Trilha atualizar(String id, SalvarTrilhaRequest request) {
        atualizarMetadados(id, new AtualizarMetadadosTrilhaRequest(
                request.nome(), request.areaCargo(), request.areaCargo(), request.banca(), request.dataProva(), null
        ));
        return repository.buscar(timezoneContext.donoId(), id)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));
    }

    public Trilha atualizarMetadados(String id, AtualizacaoMetadadosTrilha dados) {
        atualizarMetadados(id, new AtualizarMetadadosTrilhaRequest(
                dados.nome(), dados.areaCargo(), dados.areaCargo(), dados.banca(), dados.dataProva(), null
        ));
        return repository.buscar(timezoneContext.donoId(), id)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));
    }

    public Trilha atualizarProgresso(String id, String topicoId, Boolean done) {
        if (done == null) {
            throw new BadRequestException("O valor de conclusão do tópico é obrigatório.");
        }
        if (Boolean.TRUE.equals(done)) {
            marcarTopicoConcluido(id, topicoId);
        } else {
            desmarcarTopicoConcluido(id, topicoId);
        }
        return repository.buscar(timezoneContext.donoId(), id)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));
    }

    public boolean excluir(String id) {
        try {
            excluirTrilha(id);
            return true;
        } catch (NotFoundException _) {
            return false;
        }
    }

    private CronogramaAtivoResumoDTO obterCronogramaAtivoResumo(Document doc, ObjectId usuarioId) {
        ObjectId cronoId = doc.getObjectId("cronogramaAtivoId");
        if (cronoId == null) {
            return null;
        }
        Optional<Document> cronoDocOpt = repository.buscarCronogramaResumo(usuarioId, cronoId);
        if (cronoDocOpt.isEmpty()) {
            return null;
        }
        Document cronoDoc = cronoDocOpt.get();
        return new CronogramaAtivoResumoDTO(
                cronoId.toHexString(),
                cronoDoc.getString("modalidade"),
                cronoDoc.getString("dataInicio"),
                cronoDoc.getString("dataFim")
        );
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
