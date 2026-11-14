package br.com.trilhara.cronograma;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;

import br.com.trilhara.auth.RateLimitService;
import br.com.trilhara.auth.UserRepository;
import br.com.trilhara.cronograma.dto.AtualizarCronogramaRequest;
import br.com.trilhara.cronograma.dto.BlocoCronogramaItemDTO;
import br.com.trilhara.cronograma.dto.BlocoHojeDTO;
import br.com.trilhara.cronograma.dto.ConcluirAtrasadosResponse;
import br.com.trilhara.cronograma.dto.CriarCronogramaRequest;
import br.com.trilhara.cronograma.dto.CronogramaDTO;
import br.com.trilhara.cronograma.dto.DisponibilidadeSemanalDTO;
import br.com.trilhara.cronograma.dto.FalhaBlocoDTO;
import br.com.trilhara.cronograma.dto.PaginaBlocosDTO;
import br.com.trilhara.shared.TimezoneContext;
import br.com.trilhara.shared.erro.CodigoErro;
import br.com.trilhara.shared.erro.DomainConflictException;
import br.com.trilhara.shared.erro.ProblemDetails;
import br.com.trilhara.shared.erro.ProblemDetailsFactory;
import br.com.trilhara.shared.erro.ResourceLimitExceededException;
import br.com.trilhara.shared.erro.ValidationException;
import br.com.trilhara.shared.mongo.BsonDates;
import br.com.trilhara.shared.mongo.BsonIds;
import br.com.trilhara.trilha.Disciplina;
import br.com.trilhara.trilha.DisciplinaRepository;
import br.com.trilhara.trilha.LeaseHeartbeat;
import br.com.trilhara.trilha.LeaseTrilhaService;
import br.com.trilhara.trilha.TrilhaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class CronogramaService {

    private static final Logger LOG = Logger.getLogger(CronogramaService.class.getName());

    private static final String VALOR_ATIVO = "ATIVO";
    private static final String VALOR_PREPARANDO = "PREPARANDO";
    private static final String VALOR_RECUPERACAO = "RECUPERACAO_NECESSARIA";
    private static final String ERRO_CRONOGRAMA_NAO_ENCONTRADO = "Cronograma não encontrado";
    private static final String ERRO_TRILHA_NAO_ENCONTRADA = "Trilha não encontrada";
    private static final String ERRO_BLOCO_NAO_ENCONTRADO = "Bloco não encontrado";
    private static final String CAMPO_TRILHA_ID = "trilhaId";
    private static final String CAMPO_CRONOGRAMA_ID = "cronogramaId";
    private static final String CAMPO_CRONOGRAMA_ATIVO_ID = "cronogramaAtivoId";
    private static final String CAMPO_SEMANA_MODELO_MANUAL = "semanaModeloManual";
    private static final String CAMPO_BLOCOS_POR_DIA_SEMANA = "blocosPorDiaSemana";
    private static final String CAMPO_DISCIPLINAS_PARTICIPANTES_IDS = "disciplinasParticipantesIds";
    private static final int MAX_CRONOGRAMAS_POR_TRILHA = 3;
    private static final int MAX_DIAS_HORIZONTE = 366;
    private static final List<String> PERIODOS_SUPORTADOS =
            List.of("ATE_PROVA", "UMA_SEMANA", "UM_MES", "TRES_MESES");

    public record FiltroListagemBlocos(
            Boolean concluido,
            String statusData,
            LocalDate dataInicio,
            LocalDate dataFim,
            int pagina,
            int tamanho) {
    }

    private record ContextoPublicacao(
            ObjectId usuarioId,
            ObjectId trilhaId,
            ObjectId cronogramaId,
            long versaoNova,
            ModalidadeCronograma modalidade,
            int duracaoBlocoMinutos,
            String tipoPeriodo,
            LocalDate dataInicio,
            LocalDate dataFim,
            List<Integer> diasAtivosSemana,
            Integer blocosPorDia,
            Map<Integer, Integer> blocosPorDiaSemana,
            List<Disciplina> participantes,
            Map<Integer, List<String>> semanaModeloManual,
            Document configuracao,
            Instant agora,
            Instant criadoEm) {
    }

    public record DadosConfiguracao(
            ModalidadeCronograma modalidade,
            int duracaoBlocoMinutos,
            String tipoPeriodo,
            LocalDate dataInicio,
            LocalDate dataFim,
            List<Integer> diasAtivosSemana,
            Integer blocosPorDia,
            Map<Integer, Integer> blocosPorDiaSemana,
            List<ObjectId> disciplinasParticipantesIds,
            Map<Integer, List<String>> semanaModeloManual) {
    }

    private final CronogramaRepository cronogramaRepository;
    private final BlocoCronogramaRepository blocoCronogramaRepository;
    private final TrilhaRepository trilhaRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final UserRepository userRepository;
    private final LeaseTrilhaService leaseTrilhaService;
    private final TimezoneContext timezoneContext;
    private final FaultInjector faultInjector;
    private final RateLimitService rateLimitService;

    @Inject
    public CronogramaService(
            CronogramaRepository cronogramaRepository,
            BlocoCronogramaRepository blocoCronogramaRepository,
            TrilhaRepository trilhaRepository,
            DisciplinaRepository disciplinaRepository,
            UserRepository userRepository,
            LeaseTrilhaService leaseTrilhaService,
            TimezoneContext timezoneContext,
            FaultInjector faultInjector,
            RateLimitService rateLimitService) {
        this.cronogramaRepository = cronogramaRepository;
        this.blocoCronogramaRepository = blocoCronogramaRepository;
        this.trilhaRepository = trilhaRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.userRepository = userRepository;
        this.leaseTrilhaService = leaseTrilhaService;
        this.timezoneContext = timezoneContext;
        this.faultInjector = faultInjector;
        this.rateLimitService = rateLimitService;
    }

    public CronogramaDTO criar(String trilhaIdStr, CriarCronogramaRequest request) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();

        // geração de cronograma limitada a 30/min por usuário. Fica antes do lease para
        // que uma rajada não chegue nem a disputar a trava da trilha.
        rateLimitService.verificarLimiteCriacaoCronograma(usuarioId.toHexString());

        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, CAMPO_TRILHA_ID);

        String leaseToken = UUID.randomUUID().toString();
        adquirirLeaseOuFalhar(usuarioId, trilhaId, leaseToken);

        try (LeaseHeartbeat heartbeat = LeaseHeartbeat.iniciar(leaseTrilhaService, usuarioId, trilhaId, leaseToken)) {
            // 1. Limite de 3 cronogramas por trilha
            if (cronogramaRepository.contarPorTrilha(usuarioId, trilhaId) >= MAX_CRONOGRAMAS_POR_TRILHA) {
                throw new ResourceLimitExceededException("A trilha já possui o limite máximo de 3 cronogramas.");
            }

            // 2. Conflito de modalidade duplicada na mesma trilha, antes de qualquer escrita
            exigirModalidadeLivre(usuarioId, trilhaId, request.modalidade(), null);

            Document trilhaDoc = trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                    .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

            LocalDate dataInicio = request.dataInicio() != null ? request.dataInicio() : timezoneContext.hojeLocal();
            LocalDate dataFim = calcularDataFim(request.tipoPeriodo(), dataInicio, request.dataFim(), trilhaDoc);

            List<Disciplina> participantes = validarDisciplinasParticipantes(
                    usuarioId, trilhaId, request.disciplinasParticipantesIds());
            List<Integer> diasAtivosNormalizados = normalizarDiasAtivos(
                    request.diasAtivosSemana(), request.blocosPorDiaSemana());
            validarConfiguracao(request.tipoPeriodo(), diasAtivosNormalizados, request.blocosPorDia(),
                    request.blocosPorDiaSemana(), request.duracaoBlocoMinutos(),
                    request.semanaModeloManual(), participantes);

            ObjectId cronogramaId = new ObjectId();
            long versaoNova = trilhaRepository.proximaVersaoCronograma(usuarioId, trilhaId)
                    .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

            Instant agora = Instant.now();
            Document configDoc = montarConfiguracaoDocumento(new DadosConfiguracao(
                    request.modalidade(), request.duracaoBlocoMinutos(), request.tipoPeriodo(),
                    dataInicio, dataFim, diasAtivosNormalizados, request.blocosPorDia(),
                    request.blocosPorDiaSemana(),
                    participantes.stream().map(Disciplina::id).toList(), request.semanaModeloManual()));

            // ETAPA 1 — documento em PREPARANDO e inativo (respeita o índice parcial único)
            heartbeat.verificarVivo();
            faultInjector.antesDe(EtapaPublicacao.STAGING_DOCUMENTO);
            Cronograma novo = new Cronograma(
                    cronogramaId, usuarioId, trilhaId, request.modalidade(),
                    false, versaoNova, null, VALOR_PREPARANDO,
                    request.duracaoBlocoMinutos(), request.tipoPeriodo(),
                    dataInicio, dataFim, diasAtivosNormalizados,
                    request.blocosPorDia(), request.blocosPorDiaSemana(),
                    participantes.stream().map(Disciplina::id).toList(),
                    request.semanaModeloManual(), null, configDoc, agora, agora);
            cronogramaRepository.inserir(novo);

            return publicar(heartbeat, new ContextoPublicacao(
                    usuarioId, trilhaId, cronogramaId, versaoNova,
                    request.modalidade(), request.duracaoBlocoMinutos(), request.tipoPeriodo(),
                    dataInicio, dataFim, diasAtivosNormalizados, request.blocosPorDia(),
                    request.blocosPorDiaSemana(),
                    participantes, request.semanaModeloManual(), configDoc,
                    agora, novo.criadoEm()));
        } finally {
            trilhaRepository.liberarLease(usuarioId, trilhaId, leaseToken);
        }
    }

    public CronogramaDTO atualizar(String trilhaIdStr, String cronogramaIdStr, AtualizarCronogramaRequest request) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, CAMPO_TRILHA_ID);
        ObjectId cronogramaId = BsonIds.parse(cronogramaIdStr, CAMPO_CRONOGRAMA_ID);

        String leaseToken = UUID.randomUUID().toString();
        adquirirLeaseOuFalhar(usuarioId, trilhaId, leaseToken);

        try (LeaseHeartbeat heartbeat = LeaseHeartbeat.iniciar(leaseTrilhaService, usuarioId, trilhaId, leaseToken)) {
            Cronograma existente = cronogramaRepository.buscarPorId(usuarioId, trilhaId, cronogramaId)
                    .orElseThrow(() -> new NotFoundException(ERRO_CRONOGRAMA_NAO_ENCONTRADO));

            // Conflito de modalidade ANTES de qualquer escrita (§5.2)
            exigirModalidadeLivre(usuarioId, trilhaId, request.modalidade(), cronogramaId);

            Document trilhaDoc = trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                    .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

            LocalDate dataInicio = request.dataInicio() != null ? request.dataInicio() : timezoneContext.hojeLocal();
            LocalDate dataFim = calcularDataFim(request.tipoPeriodo(), dataInicio, request.dataFim(), trilhaDoc);

            List<Disciplina> participantes = validarDisciplinasParticipantes(
                    usuarioId, trilhaId, request.disciplinasParticipantesIds());
            List<Integer> diasAtivosNormalizados = normalizarDiasAtivos(
                    request.diasAtivosSemana(), request.blocosPorDiaSemana());
            validarConfiguracao(request.tipoPeriodo(), diasAtivosNormalizados, request.blocosPorDia(),
                    request.blocosPorDiaSemana(), request.duracaoBlocoMinutos(),
                    request.semanaModeloManual(), participantes);

            long versaoNova = trilhaRepository.proximaVersaoCronograma(usuarioId, trilhaId)
                    .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

            Document configPreparada = montarConfiguracaoDocumento(new DadosConfiguracao(
                    request.modalidade(), request.duracaoBlocoMinutos(), request.tipoPeriodo(),
                    dataInicio, dataFim, diasAtivosNormalizados, request.blocosPorDia(),
                    request.blocosPorDiaSemana(),
                    participantes.stream().map(Disciplina::id).toList(), request.semanaModeloManual()));

            // ETAPA 1 — marca a versão preparada no documento (flags atuais preservadas)
            heartbeat.verificarVivo();
            faultInjector.antesDe(EtapaPublicacao.STAGING_DOCUMENTO);
            Cronograma preparando = new Cronograma(
                    existente.id(), usuarioId, trilhaId, request.modalidade(),
                    existente.ativo(), existente.versaoAtiva(), versaoNova, VALOR_PREPARANDO,
                    request.duracaoBlocoMinutos(), request.tipoPeriodo(),
                    dataInicio, dataFim, diasAtivosNormalizados,
                    request.blocosPorDia(), request.blocosPorDiaSemana(),
                    participantes.stream().map(Disciplina::id).toList(),
                    request.semanaModeloManual(), existente.configuracaoAtiva(), configPreparada,
                    existente.criadoEm(), Instant.now());
            cronogramaRepository.atualizar(preparando);

            return publicar(heartbeat, new ContextoPublicacao(
                    usuarioId, trilhaId, cronogramaId, versaoNova,
                    request.modalidade(), request.duracaoBlocoMinutos(), request.tipoPeriodo(),
                    dataInicio, dataFim, diasAtivosNormalizados, request.blocosPorDia(),
                    request.blocosPorDiaSemana(),
                    participantes, request.semanaModeloManual(), configPreparada,
                    Instant.now(), existente.criadoEm()));
        } finally {
            trilhaRepository.liberarLease(usuarioId, trilhaId, leaseToken);
        }
    }

    public CronogramaDTO ativar(String trilhaIdStr, String cronogramaIdStr) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, CAMPO_TRILHA_ID);
        ObjectId cronogramaId = BsonIds.parse(cronogramaIdStr, CAMPO_CRONOGRAMA_ID);

        String leaseToken = UUID.randomUUID().toString();
        adquirirLeaseOuFalhar(usuarioId, trilhaId, leaseToken);

        try (LeaseHeartbeat heartbeat = LeaseHeartbeat.iniciar(leaseTrilhaService, usuarioId, trilhaId, leaseToken)) {
            Cronograma cronograma = cronogramaRepository.buscarPorId(usuarioId, trilhaId, cronogramaId)
                    .orElseThrow(() -> new NotFoundException(ERRO_CRONOGRAMA_NAO_ENCONTRADO));

            heartbeat.verificarVivo();
            faultInjector.antesDe(EtapaPublicacao.TROCA_PONTEIRO);
            if (!trilhaRepository.atualizarPonteiroCronogramaAtivo(
                    usuarioId, trilhaId, cronogramaId, cronograma.versaoAtiva(), leaseToken)) {
                throw new DomainConflictException(
                        CodigoErro.SCHEDULE_GENERATION_IN_PROGRESS, LeaseTrilhaService.MSG_LEASE_PERDIDO);
            }

            heartbeat.verificarVivo();
            faultInjector.antesDe(EtapaPublicacao.RECONCILIACAO_FLAGS);
            cronogramaRepository.desativarOutros(usuarioId, trilhaId, cronogramaId);

            Cronograma atualizado = new Cronograma(
                    cronograma.id(), usuarioId, trilhaId, cronograma.modalidade(),
                    true, cronograma.versaoAtiva(), cronograma.versaoPreparada(), VALOR_ATIVO,
                    cronograma.duracaoBlocoMinutos(), cronograma.tipoPeriodo(),
                    cronograma.dataInicio(), cronograma.dataFim(), cronograma.diasAtivosSemana(),
                    cronograma.blocosPorDia(), cronograma.blocosPorDiaSemana(),
                    cronograma.disciplinasParticipantesIds(),
                    cronograma.semanaModeloManual(), cronograma.configuracaoAtiva(), cronograma.configuracaoPreparada(),
                    cronograma.criadoEm(), Instant.now());
            cronogramaRepository.atualizar(atualizado);

            long totalBlocos = blocoCronogramaRepository.contarPorVersao(
                    usuarioId, trilhaId, cronogramaId, cronograma.versaoAtiva());
            return paraDTO(atualizado, (int) totalBlocos);
        } finally {
            trilhaRepository.liberarLease(usuarioId, trilhaId, leaseToken);
        }
    }

    public void excluir(String trilhaIdStr, String cronogramaIdStr) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, CAMPO_TRILHA_ID);
        ObjectId cronogramaId = BsonIds.parse(cronogramaIdStr, CAMPO_CRONOGRAMA_ID);

        String leaseToken = UUID.randomUUID().toString();
        adquirirLeaseOuFalhar(usuarioId, trilhaId, leaseToken);

        try (LeaseHeartbeat heartbeat = LeaseHeartbeat.iniciar(leaseTrilhaService, usuarioId, trilhaId, leaseToken)) {
            Cronograma cronograma = cronogramaRepository.buscarPorId(usuarioId, trilhaId, cronogramaId)
                    .orElseThrow(() -> new NotFoundException(ERRO_CRONOGRAMA_NAO_ENCONTRADO));

            boolean eraAtivo = cronograma.ativo();

            // Remove todos os blocos do cronograma
            blocoCronogramaRepository.excluirPorCronograma(usuarioId, trilhaId, cronogramaId);

            // Remove o documento do cronograma
            cronogramaRepository.excluir(usuarioId, trilhaId, cronogramaId);

            // Se o excluído era ativo, promove o mais recente restante
            if (eraAtivo) {
                var restanteOpt = cronogramaRepository.buscarMaisRecenteRestante(usuarioId, trilhaId);
                if (restanteOpt.isPresent()) {
                    Cronograma restante = restanteOpt.get();
                    heartbeat.verificarVivo();
                    if (!trilhaRepository.atualizarPonteiroCronogramaAtivo(
                            usuarioId, trilhaId, restante.id(), restante.versaoAtiva(), leaseToken)) {
                        throw new DomainConflictException(
                                CodigoErro.SCHEDULE_GENERATION_IN_PROGRESS, LeaseTrilhaService.MSG_LEASE_PERDIDO);
                    }
                    cronogramaRepository.desativarOutros(usuarioId, trilhaId, restante.id());
                    Cronograma promovido = new Cronograma(
                            restante.id(), usuarioId, trilhaId, restante.modalidade(),
                            true, restante.versaoAtiva(), restante.versaoPreparada(), VALOR_ATIVO,
                            restante.duracaoBlocoMinutos(), restante.tipoPeriodo(),
                            restante.dataInicio(), restante.dataFim(), restante.diasAtivosSemana(),
                            restante.blocosPorDia(), restante.blocosPorDiaSemana(),
                            restante.disciplinasParticipantesIds(),
                            restante.semanaModeloManual(), restante.configuracaoAtiva(), restante.configuracaoPreparada(),
                            restante.criadoEm(), Instant.now());
                    cronogramaRepository.atualizar(promovido);
                } else {
                    heartbeat.verificarVivo();
                    trilhaRepository.atualizarPonteiroCronogramaAtivo(
                            usuarioId, trilhaId, null, null, leaseToken);
                }
            }
        } finally {
            trilhaRepository.liberarLease(usuarioId, trilhaId, leaseToken);
        }
    }

    public List<CronogramaDTO> listar(String trilhaIdStr) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, CAMPO_TRILHA_ID);

        trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

        List<Cronograma> cronogramas = cronogramaRepository.listarPorTrilha(usuarioId, trilhaId);
        List<CronogramaDTO> dtos = new ArrayList<>();

        for (Cronograma c : cronogramas) {
            long total = blocoCronogramaRepository.contarPorVersao(usuarioId, trilhaId, c.id(), c.versaoAtiva());
            dtos.add(paraDTO(c, (int) total));
        }

        return dtos;
    }

    public PaginaBlocosDTO listarBlocosPaginado(
            String trilhaIdStr,
            String cronogramaIdStr,
            FiltroListagemBlocos filtro) {

        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, CAMPO_TRILHA_ID);
        ObjectId cronogramaId = BsonIds.parse(cronogramaIdStr, CAMPO_CRONOGRAMA_ID);

        if (filtro.pagina() < 1 || filtro.tamanho() < 1 || filtro.tamanho() > BlocoCronogramaRepository.TAMANHO_MAXIMO_PAGINA) {
            throw paginacaoInvalida("'pagina' deve ser >= 1 e 'tamanho' deve estar entre 1 e "
                    + BlocoCronogramaRepository.TAMANHO_MAXIMO_PAGINA + ".");
        }

        String statusNormalizado = normalizarStatusData(filtro.statusData());

        Cronograma cronograma = cronogramaRepository.buscarPorId(usuarioId, trilhaId, cronogramaId)
                .orElseThrow(() -> new NotFoundException(ERRO_CRONOGRAMA_NAO_ENCONTRADO));

        LocalDate hojeLocal = timezoneContext.hojeLocal();

        var paginado = blocoCronogramaRepository.listarPaginado(
                usuarioId,
                new BlocoCronogramaRepository.FiltroPaginacaoBlocos(
                        trilhaId,
                        cronogramaId,
                        cronograma.versaoAtiva(),
                        filtro.concluido(),
                        statusNormalizado,
                        hojeLocal,
                        filtro.dataInicio(),
                        filtro.dataFim(),
                        filtro.pagina(),
                        filtro.tamanho()));

        List<BlocoCronogramaItemDTO> itens = paginado.itens().stream()
                .map(b -> paraBlocoDTO(b, hojeLocal))
                .toList();

        return new PaginaBlocosDTO(
                itens,
                paginado.pagina(),
                paginado.tamanho(),
                paginado.total(),
                paginado.totalPaginas()
        );
    }

    public List<BlocoHojeDTO> listarBlocosHoje() {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        LocalDate hojeLocal = timezoneContext.hojeLocal();

        List<Document> trilhasDocs = trilhaRepository.listarEntidades(usuarioId);
        if (trilhasDocs.isEmpty()) {
            return List.of();
        }

        Map<ObjectId, String> nomesTrilhasPorCronogramaId = new HashMap<>();
        Map<ObjectId, Long> versoesAtivasPorCronograma = new HashMap<>();

        for (Document td : trilhasDocs) {
            ObjectId cronogramaAtivoId = td.getObjectId(CAMPO_CRONOGRAMA_ATIVO_ID);
            if (cronogramaAtivoId != null) {
                Long versao = td.getLong("versaoAtivaCronograma");
                versoesAtivasPorCronograma.put(cronogramaAtivoId, versao != null ? versao : 1L);
                nomesTrilhasPorCronogramaId.put(cronogramaAtivoId, td.getString("nome"));
            }
        }

        if (versoesAtivasPorCronograma.isEmpty()) {
            return List.of();
        }

        // Leitura sempre guiada pelo ponteiro publicado da trilha (id e versão ativa)
        List<BlocoCronograma> blocos = blocoCronogramaRepository.listarHoje(
                usuarioId, hojeLocal, versoesAtivasPorCronograma);

        List<BlocoHojeDTO> dtos = new ArrayList<>();
        for (BlocoCronograma b : blocos) {
            String trilhaNome = nomesTrilhasPorCronogramaId.getOrDefault(b.cronogramaId(), "");
            dtos.add(new BlocoHojeDTO(
                b.idHex(),
                b.trilhaIdHex(),
                b.cronogramaIdHex(),
                b.disciplinaIdHex(),
                b.disciplinaNomeSnapshot(),
                b.disciplinaCorSnapshot(),
                trilhaNome,
                b.dataIso(),
                b.ordem(),
                b.duracaoMinutos(),
                b.concluido()
            ));
        }

        // Ordenação canônica Hoje: trilhaNome ASC, ordem ASC, _id ASC
        dtos.sort(Comparator
                .comparing((BlocoHojeDTO b) -> b.trilhaNome() != null ? b.trilhaNome() : "")
                .thenComparingInt(BlocoHojeDTO::ordem)
                .thenComparing(BlocoHojeDTO::blocoId));

        return Collections.unmodifiableList(dtos);
    }

    public BlocoCronogramaItemDTO atualizarConclusaoBloco(String blocoIdStr, boolean concluido) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId blocoId = BsonIds.parse(blocoIdStr, "blocoId");

        BlocoCronograma bloco = blocoCronogramaRepository.buscarPorId(usuarioId, blocoId)
                .orElseThrow(() -> new NotFoundException(ERRO_BLOCO_NAO_ENCONTRADO));

        LocalDate hojeLocal = timezoneContext.hojeLocal();

        // Bloco de versão não publicada responde 404: leitura/escrita sempre pela versão do
        // ponteiro da trilha, sem revelar staging órfão.
        exigirBlocoPublicado(usuarioId, bloco);

        // Operação idempotente: repetição no mesmo estado não altera timestamps nem concluidoEm
        if (bloco.concluido() == concluido) {
            return paraBlocoDTO(bloco, hojeLocal);
        }

        Instant concluidoEm = concluido ? Instant.now() : null;
        blocoCronogramaRepository.atualizarConclusao(usuarioId, blocoId, concluido, concluidoEm);

        BlocoCronograma atualizado = blocoCronogramaRepository.buscarPorId(usuarioId, blocoId)
                .orElseThrow(() -> new NotFoundException(ERRO_BLOCO_NAO_ENCONTRADO));

        return paraBlocoDTO(atualizado, hojeLocal);
    }

    /**
 * "Concluir atrasados sem sessão" .
 *
 * <p>Operação naturalmente idempotente: setar {@code concluido=true} onde {@code false}. Não há
 * coleção de operações; {@code operationId} existe apenas para correlação de log. O
 * processamento adquire o lease da trilha (mesmo mecanismo da geração) e atualiza bloco a bloco
 * com {@code concluido:false} no filtro, de modo que um retry não reprocessa o que já concluiu.</p>
 */
    public ConcluirAtrasadosResponse concluirAtrasados(
            String trilhaIdStr, String cronogramaIdStr, UUID operationId) {

        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.parse(trilhaIdStr, CAMPO_TRILHA_ID);
        ObjectId cronogramaId = BsonIds.parse(cronogramaIdStr, CAMPO_CRONOGRAMA_ID);

        String leaseToken = UUID.randomUUID().toString();
        // Lease ocupado ⇒ 409 SCHEDULE_GENERATION_IN_PROGRESS
        adquirirLeaseOuFalhar(usuarioId, trilhaId, leaseToken);

        try {
            Cronograma cronograma = cronogramaRepository.buscarPorId(usuarioId, trilhaId, cronogramaId)
                    .orElseThrow(() -> new NotFoundException(ERRO_CRONOGRAMA_NAO_ENCONTRADO));

            LocalDate hojeLocal = timezoneContext.hojeLocal();
            List<BlocoCronograma> atrasados = blocoCronogramaRepository.listarAtrasadosPendentes(
                    usuarioId, trilhaId, cronogramaId, cronograma.versaoAtiva(), hojeLocal);

            int concluidos = 0;
            List<FalhaBlocoDTO> falhas = new ArrayList<>();
            Instant agora = Instant.now();

            for (BlocoCronograma b : atrasados) {
                try {
                    if (blocoCronogramaRepository.concluirSePendente(
                            usuarioId, trilhaId, cronogramaId, b.id(), agora)) {
                        concluidos++;
                    }
                } catch (RuntimeException _) {
                    falhas.add(new FalhaBlocoDTO(b.idHex(), "FALHA_CONCLUSAO"));
                }
            }

            int totalConcluidos = concluidos;
            LOG.info(() -> "concluir-atrasados operationId=" + operationId
                    + " processados=" + atrasados.size() + " concluidos=" + totalConcluidos
                    + " falhas=" + falhas.size());

            return new ConcluirAtrasadosResponse(operationId, atrasados.size(), concluidos, falhas);
        } finally {
            trilhaRepository.liberarLease(usuarioId, trilhaId, leaseToken);
        }
    }

    public DisponibilidadeSemanalDTO obterDisponibilidadeSemanal() {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        LocalDate hojeLocal = timezoneContext.hojeLocal();

        List<Integer> disponibilidade = userRepository.obterDisponibilidadeSemanal(usuarioId);
        List<Integer> cargaAtiva = calcularCargaAtivaPorDia(usuarioId, hojeLocal);

        List<Boolean> sobrecarga = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            sobrecarga.add(cargaAtiva.get(i) > disponibilidade.get(i) && disponibilidade.get(i) > 0);
        }

        return new DisponibilidadeSemanalDTO(disponibilidade, cargaAtiva, sobrecarga);
    }

    public DisponibilidadeSemanalDTO atualizarDisponibilidadeSemanal(List<Integer> minutos) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        if (minutos == null || minutos.size() != 7) {
            throw new ValidationException("disponibilidadeSemanalMinutos",
                    "disponibilidadeSemanalMinutos deve conter exatamente 7 valores.");
        }
        for (Integer m : minutos) {
            if (m == null || m < 0 || m > 1440) {
                throw new ValidationException("disponibilidadeSemanalMinutos",
                        "Os minutos devem estar entre 0 e 1440.");
            }
        }

        userRepository.salvarDisponibilidadeSemanal(usuarioId, minutos);
        return obterDisponibilidadeSemanal();
    }

    private List<Integer> calcularCargaAtivaPorDia(ObjectId usuarioId, LocalDate hojeLocal) {
        List<Integer> cargaPorDia = new ArrayList<>(List.of(0, 0, 0, 0, 0, 0, 0));

        List<Document> trilhas = trilhaRepository.listarEntidades(usuarioId);
        for (Document t : trilhas) {
            ObjectId cronogramaAtivoId = t.getObjectId(CAMPO_CRONOGRAMA_ATIVO_ID);
            if (cronogramaAtivoId != null) {
                cronogramaRepository.buscarPorId(usuarioId, cronogramaAtivoId).ifPresent(c -> {
                    if (c.ativo() && c.dataFim() != null && !c.dataFim().isBefore(hojeLocal)) {
                        List<Integer> cargaCronograma = calcularCargaSemanalCronograma(c);
                        for (int i = 0; i < 7; i++) {
                            cargaPorDia.set(i, cargaPorDia.get(i) + cargaCronograma.get(i));
                        }
                    }
                });
            }
        }
        return cargaPorDia;
    }

    public List<Integer> calcularCargaSemanalCronograma(Cronograma c) {
        List<Integer> carga = new ArrayList<>(List.of(0, 0, 0, 0, 0, 0, 0));
        if (c.modalidade() == ModalidadeCronograma.SEQUENCIAL || c.modalidade() == ModalidadeCronograma.PONDERADO) {
            calcularCargaSequencialOuPonderada(c, carga);
        } else if (c.modalidade() == ModalidadeCronograma.MANUAL && c.semanaModeloManual() != null) {
            calcularCargaManual(c, carga);
        }
        return carga;
    }

    private void calcularCargaSequencialOuPonderada(Cronograma c, List<Integer> carga) {
        int duracao = c.duracaoBlocoMinutos();
        for (Integer dia : c.diasAtivosSemana()) {
            if (dia != null && dia >= 1 && dia <= 7) {
                int blocosDia = (c.blocosPorDiaSemana() != null && c.blocosPorDiaSemana().containsKey(dia))
                        ? c.blocosPorDiaSemana().get(dia)
                        : (c.blocosPorDia() != null ? c.blocosPorDia() : 1);
                carga.set(dia - 1, blocosDia * duracao);
            }
        }
    }

    private void calcularCargaManual(Cronograma c, List<Integer> carga) {
        c.semanaModeloManual().forEach((dia, ids) -> {
            if (dia != null && dia >= 1 && dia <= 7 && ids != null) {
                carga.set(dia - 1, ids.size() * c.duracaoBlocoMinutos());
            }
        });
    }

    private void adquirirLeaseOuFalhar(ObjectId usuarioId, ObjectId trilhaId, String leaseToken) {
        leaseTrilhaService.adquirirOuFalhar(usuarioId, trilhaId, leaseToken);
    }

    private LocalDate calcularDataFim(String tipoPeriodo, LocalDate dataInicio, LocalDate dataFimInformada, Document trilhaDoc) {
        LocalDate limite = dataInicio.plusDays(MAX_DIAS_HORIZONTE - 1L);
        LocalDate calculada;

        if (dataFimInformada != null) {
            calculada = dataFimInformada;
        } else if ("ATE_PROVA".equalsIgnoreCase(tipoPeriodo)) {
            LocalDate dataProva = BsonDates.toLocalDate(trilhaDoc.getString("dataProva"));
            // "Até a prova" depende de dataProva ESTRITAMENTE posterior a dataInicio.
            calculada = dataProva != null && dataProva.isAfter(dataInicio)
                    ? dataProva
                    : dataInicio.plusDays(29);
        } else if ("UMA_SEMANA".equalsIgnoreCase(tipoPeriodo)) {
            calculada = dataInicio.plusDays(6);
        } else if ("TRES_MESES".equalsIgnoreCase(tipoPeriodo)) {
            calculada = dataInicio.plusDays(89);
        } else {
            calculada = dataInicio.plusDays(29);
        }

        if (calculada.isBefore(dataInicio)) {
            throw new ValidationException("dataFim", "A data final não pode ser anterior à data inicial.");
        }
        // Horizonte máximo por geração: 366 dias inclusivos (dataFim <= dataInicio + 365).
        return calculada.isAfter(limite) ? limite : calculada;
    }

    /**
 * Executa as etapas 2..6 da publicação versionada sob o heartbeat do lease.
 *
 * <p>Falha antes da troca do ponteiro deixa o cronograma em {@code PREPARANDO} com staging
 * órfão (o reconciliador limpa). Falha depois da troca marca {@code RECUPERACAO_NECESSARIA}:
 * o leitor pode ver a versão nova, mas flags e staging precisam ser reconciliados de forma
 * idempotente.</p>
 */
    private CronogramaDTO publicar(LeaseHeartbeat heartbeat, ContextoPublicacao ctx) {
        String leaseToken = heartbeat.token();
        boolean ponteiroTrocado = false;
        try {
            // ETAPA 2 — geração e staging dos blocos da nova versão
            heartbeat.verificarVivo();
            faultInjector.antesDe(EtapaPublicacao.GERACAO_BLOCOS);
            List<BlocoCronograma> blocos = GeradorBlocosCronograma.gerar(
                    new GeradorBlocosCronograma.ParametrosGeracao(
                            ctx.usuarioId(), ctx.trilhaId(), ctx.cronogramaId(), ctx.versaoNova(),
                            ctx.modalidade(), ctx.duracaoBlocoMinutos(), ctx.dataInicio(), ctx.dataFim(),
                            ctx.diasAtivosSemana(), ctx.blocosPorDia(), ctx.blocosPorDiaSemana(),
                            ctx.participantes(), ctx.semanaModeloManual()));
            blocoCronogramaRepository.inserirLote(blocos);

            // ETAPA 3 — contagem esperada antes de publicar
            heartbeat.verificarVivo();
            faultInjector.antesDe(EtapaPublicacao.VERIFICACAO_CONTAGEM);
            long contagem = blocoCronogramaRepository.contarPorVersao(
                    ctx.usuarioId(), ctx.trilhaId(), ctx.cronogramaId(), ctx.versaoNova());
            if (contagem != blocos.size()) {
                throw new IllegalStateException(
                        "Falha na geração: contagem de blocos em staging diverge do esperado.");
            }

            // ETAPA 4 — troca atômica e com fencing do ponteiro da trilha
            heartbeat.verificarVivo();
            faultInjector.antesDe(EtapaPublicacao.TROCA_PONTEIRO);
            if (!trilhaRepository.atualizarPonteiroCronogramaAtivo(
                    ctx.usuarioId(), ctx.trilhaId(), ctx.cronogramaId(), ctx.versaoNova(), leaseToken)) {
                throw new DomainConflictException(
                        CodigoErro.SCHEDULE_GENERATION_IN_PROGRESS, LeaseTrilhaService.MSG_LEASE_PERDIDO);
            }
            ponteiroTrocado = true;

            // ETAPA 5 — reconciliação dos flags (no máximo um ativo por trilha)
            heartbeat.verificarVivo();
            faultInjector.antesDe(EtapaPublicacao.RECONCILIACAO_FLAGS);
            cronogramaRepository.desativarOutros(ctx.usuarioId(), ctx.trilhaId(), ctx.cronogramaId());

            Cronograma publicado = new Cronograma(
                    ctx.cronogramaId(), ctx.usuarioId(), ctx.trilhaId(), ctx.modalidade(),
                    true, ctx.versaoNova(), null, VALOR_ATIVO,
                    ctx.duracaoBlocoMinutos(), ctx.tipoPeriodo(), ctx.dataInicio(), ctx.dataFim(), ctx.diasAtivosSemana(),
                    ctx.blocosPorDia(), ctx.blocosPorDiaSemana(), ctx.participantes().stream().map(Disciplina::id).toList(),
                    ctx.semanaModeloManual(), ctx.configuracao(), null,
                    ctx.criadoEm() != null ? ctx.criadoEm() : ctx.agora(), Instant.now());
            cronogramaRepository.atualizar(publicado);

            // ETAPA 6 — limpeza das versões anteriores deste cronograma
            heartbeat.verificarVivo();
            faultInjector.antesDe(EtapaPublicacao.LIMPEZA_VERSOES_ANTERIORES);
            blocoCronogramaRepository.excluirVersoesDiferentes(
                    ctx.usuarioId(), ctx.trilhaId(), ctx.cronogramaId(), ctx.versaoNova());

            return paraDTO(publicado, blocos.size());
        } catch (RuntimeException ex) {
            if (ponteiroTrocado) {
                marcarRecuperacaoNecessaria(ctx.usuarioId(), ctx.trilhaId(), ctx.cronogramaId());
            }
            throw ex;
        }
    }

    private void marcarRecuperacaoNecessaria(ObjectId usuarioId, ObjectId trilhaId, ObjectId cronogramaId) {
        cronogramaRepository.buscarPorId(usuarioId, trilhaId, cronogramaId).ifPresent(c -> {
            Cronograma emRecuperacao = new Cronograma(
                    c.id(), c.usuarioId(), c.trilhaId(), c.modalidade(), c.ativo(),
                    c.versaoAtiva(), c.versaoPreparada(), VALOR_RECUPERACAO,
                    c.duracaoBlocoMinutos(), c.tipoPeriodo(), c.dataInicio(), c.dataFim(),
                    c.diasAtivosSemana(), c.blocosPorDia(), c.blocosPorDiaSemana(), c.disciplinasParticipantesIds(),
                    c.semanaModeloManual(), c.configuracaoAtiva(), c.configuracaoPreparada(),
                    c.criadoEm(), Instant.now());
            cronogramaRepository.atualizar(emRecuperacao);
        });
    }

    /** 409 DUPLICATE_MODALITY quando a modalidade já pertence a outro cronograma da trilha. */
    private void exigirModalidadeLivre(
            ObjectId usuarioId, ObjectId trilhaId, ModalidadeCronograma modalidade, ObjectId cronogramaId) {
        Optional<Cronograma> conflitante = cronogramaId == null
                ? cronogramaRepository.buscarPorModalidade(usuarioId, trilhaId, modalidade)
                : cronogramaRepository.buscarPorModalidadeExceto(usuarioId, trilhaId, modalidade, cronogramaId);
        if (conflitante.isPresent()) {
            throw new DomainConflictException(
                    CodigoErro.DUPLICATE_MODALITY,
                    "Esta trilha já possui um cronograma na modalidade selecionada.");
        }
    }

    /**
 * Bloco fora da versão publicada no ponteiro da trilha responde {@code 404}, sem
 * revelar staging órfão nem versões antigas.
 */
    private void exigirBlocoPublicado(ObjectId usuarioId, BlocoCronograma bloco) {
        Document trilhaDoc = trilhaRepository.buscarEntidade(usuarioId, bloco.trilhaId())
                .orElseThrow(() -> new NotFoundException(ERRO_BLOCO_NAO_ENCONTRADO));
        ObjectId cronogramaAtivoId = trilhaDoc.getObjectId(CAMPO_CRONOGRAMA_ATIVO_ID);
        Long versaoAtiva = trilhaDoc.getLong("versaoAtivaCronograma");
        boolean publicado = cronogramaAtivoId != null
                && cronogramaAtivoId.equals(bloco.cronogramaId())
                && versaoAtiva != null
                && versaoAtiva == bloco.versaoGeracao();
        if (!publicado) {
            throw new NotFoundException(ERRO_BLOCO_NAO_ENCONTRADO);
        }
    }

    /** {@code 400 INVALID_PAGINATION} — pagina/tamanho fora da norma do endpoint. */
    private RuntimeException paginacaoInvalida(String detail) {
        var problem = ProblemDetailsFactory.criar(CodigoErro.INVALID_PAGINATION, detail, null);
        return new WebApplicationException(
                Response.status(problem.status())
                        .type(ProblemDetails.MEDIA_TYPE)
                        .entity(problem)
                        .build());
    }

    /** Valida {@code statusData} contra os três valores derivados de {@code hojeLocal}. */
    private String normalizarStatusData(String statusData) {
        if (statusData == null || statusData.isBlank()) {
            return null;
        }
        String normalizado = statusData.trim().toUpperCase(Locale.ROOT);
        if (!normalizado.equals("ATRASADO") && !normalizado.equals("HOJE") && !normalizado.equals("FUTURO")) {
            throw new ValidationException("statusData", "Valor inválido. Use ATRASADO, HOJE ou FUTURO.");
        }
        return normalizado;
    }

    private static List<Integer> normalizarDiasAtivos(
            List<Integer> diasAtivosSemana, Map<Integer, Integer> blocosPorDiaSemana) {
        if (diasAtivosSemana != null && !diasAtivosSemana.isEmpty()) {
            return diasAtivosSemana;
        }
        if (blocosPorDiaSemana != null && !blocosPorDiaSemana.isEmpty()) {
            return blocosPorDiaSemana.entrySet().stream()
                    .filter(e -> e.getKey() != null && e.getValue() != null && e.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
        }
        return diasAtivosSemana;
    }

    /** Validações de entrada que o Bean Validation não alcança (dependem de estado da trilha). */
    private void validarConfiguracao(
            String tipoPeriodo,
            List<Integer> diasAtivosSemana,
            Integer blocosPorDia,
            Map<Integer, Integer> blocosPorDiaSemana,
            int duracaoBlocoMinutos,
            Map<Integer, List<String>> semanaModeloManual,
            List<Disciplina> participantes) {

        validarPeriodoEDuracao(tipoPeriodo, duracaoBlocoMinutos);
        validarDiasAtivosEBlocosPorDia(diasAtivosSemana, blocosPorDia, blocosPorDiaSemana);

        Set<ObjectId> participantesIds = participantes.stream()
                .map(Disciplina::id)
                .collect(Collectors.toCollection(HashSet::new));
        validarSemanaModeloManual(semanaModeloManual, participantesIds);
    }

    private static void validarPeriodoEDuracao(String tipoPeriodo, int duracaoBlocoMinutos) {
        if (tipoPeriodo != null && !tipoPeriodo.isBlank()
                && PERIODOS_SUPORTADOS.stream().noneMatch(p -> p.equalsIgnoreCase(tipoPeriodo))) {
            throw new ValidationException("tipoPeriodo",
                    "Período inválido. Use ATE_PROVA, UMA_SEMANA, UM_MES ou TRES_MESES.");
        }
        if (duracaoBlocoMinutos < GeradorBlocosCronograma.MIN_DURACAO_MINUTOS
                || duracaoBlocoMinutos > GeradorBlocosCronograma.MAX_DURACAO_MINUTOS) {
            throw new ValidationException("duracaoBlocoMinutos",
                    "A duração do bloco deve estar entre 30 e 240 minutos.");
        }
    }

    private static void validarDiasAtivosEBlocosPorDia(
            List<Integer> diasAtivosSemana, Integer blocosPorDia, Map<Integer, Integer> blocosPorDiaSemana) {
        if (diasAtivosSemana != null) {
            Set<Integer> vistos = new HashSet<>();
            for (Integer dia : diasAtivosSemana) {
                if (dia == null || dia < 1 || dia > 7) {
                    throw new ValidationException("diasAtivosSemana",
                            "Cada dia da semana deve estar entre 1 (segunda) e 7 (domingo).");
                }
                if (!vistos.add(dia)) {
                    throw new ValidationException("diasAtivosSemana", "Não repita o mesmo dia da semana.");
                }
            }
        }
        if (blocosPorDia != null
                && (blocosPorDia < 1 || blocosPorDia > GeradorBlocosCronograma.MAX_BLOCOS_POR_DIA)) {
            throw new ValidationException("blocosPorDia",
                    "A quantidade de blocos por dia deve estar entre 1 e 24.");
        }
        if (blocosPorDiaSemana != null) {
            for (Map.Entry<Integer, Integer> entry : blocosPorDiaSemana.entrySet()) {
                Integer dia = entry.getKey();
                Integer qtd = entry.getValue();
                if (dia == null || dia < 1 || dia > 7) {
                    throw new ValidationException(CAMPO_BLOCOS_POR_DIA_SEMANA,
                            "Cada dia em blocosPorDiaSemana deve estar entre 1 (segunda) e 7 (domingo).");
                }
                if (qtd == null || qtd < 0 || qtd > GeradorBlocosCronograma.MAX_BLOCOS_POR_DIA) {
                    throw new ValidationException(CAMPO_BLOCOS_POR_DIA_SEMANA,
                            "A quantidade de blocos por dia deve estar entre 0 e 24.");
                }
            }
            if (diasAtivosSemana != null) {
                for (Integer dia : diasAtivosSemana) {
                    Integer qtd = blocosPorDiaSemana.containsKey(dia) ? blocosPorDiaSemana.get(dia) : blocosPorDia;
                    if (qtd != null && (qtd < 1 || qtd > GeradorBlocosCronograma.MAX_BLOCOS_POR_DIA)) {
                        throw new ValidationException(CAMPO_BLOCOS_POR_DIA_SEMANA,
                                "Cada dia ativo deve possuir entre 1 e 24 blocos.");
                    }
                }
            }
        }
    }

    private static void validarSemanaModeloManual(
            Map<Integer, List<String>> semanaModeloManual, Set<ObjectId> participantesIds) {
        if (semanaModeloManual == null) {
            return;
        }
        for (Map.Entry<Integer, List<String>> entrada : semanaModeloManual.entrySet()) {
            validarDiaSemanaModelo(entrada.getKey(), entrada.getValue(), participantesIds);
        }
    }

    private static void validarDiaSemanaModelo(
            Integer dia, List<String> ids, Set<ObjectId> participantesIds) {
        if (dia == null || dia < 1 || dia > 7) {
            throw new ValidationException(CAMPO_SEMANA_MODELO_MANUAL,
                    "Os dias da semana-modelo devem estar entre 1 e 7.");
        }
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException(CAMPO_SEMANA_MODELO_MANUAL,
                    "Cada dia da semana-modelo precisa de ao menos uma disciplina.");
        }
        for (String id : ids) {
            ObjectId disciplinaId = BsonIds.parse(id, CAMPO_SEMANA_MODELO_MANUAL);
            if (!participantesIds.contains(disciplinaId)) {
                throw new ValidationException(CAMPO_SEMANA_MODELO_MANUAL,
                        "A semana-modelo só pode usar disciplinas participantes desta trilha.");
            }
        }
    }

    private List<Disciplina> validarDisciplinasParticipantes(
            ObjectId usuarioId, ObjectId trilhaId, List<String> participantesIdsStr) {
        if (participantesIdsStr == null || participantesIdsStr.isEmpty()) {
            throw new ValidationException(CAMPO_DISCIPLINAS_PARTICIPANTES_IDS,
                    "Ao menos uma disciplina participante deve ser informada.");
        }

        Set<ObjectId> idsDesejados = new HashSet<>();
        for (String id : participantesIdsStr) {
            idsDesejados.add(BsonIds.parse(id, CAMPO_DISCIPLINAS_PARTICIPANTES_IDS));
        }

        List<Disciplina> ativas = disciplinaRepository.listarPorTrilha(usuarioId, trilhaId);
        Set<ObjectId> idsDaTrilha = ativas.stream().map(Disciplina::id).collect(Collectors.toSet());

        // Todas as disciplinas informadas precisam pertencer à trilha do usuário (§5.2).
        if (!idsDaTrilha.containsAll(idsDesejados)) {
            throw new ValidationException(CAMPO_DISCIPLINAS_PARTICIPANTES_IDS,
                    "Todas as disciplinas participantes devem pertencer a esta trilha.");
        }

        return ativas.stream().filter(d -> idsDesejados.contains(d.id())).toList();
    }

    private Document montarConfiguracaoDocumento(DadosConfiguracao dados) {
        Document config = new Document();
        config.put("modalidade", dados.modalidade().name());
        config.put("duracaoBlocoMinutos", dados.duracaoBlocoMinutos());
        config.put("tipoPeriodo", dados.tipoPeriodo());
        config.put("dataInicio", dados.dataInicio().toString());
        config.put("dataFim", dados.dataFim().toString());
        config.put("diasAtivosSemana", dados.diasAtivosSemana());
        config.put("blocosPorDia", dados.blocosPorDia());
        if (dados.blocosPorDiaSemana() != null && !dados.blocosPorDiaSemana().isEmpty()) {
            Document bDoc = new Document();
            dados.blocosPorDiaSemana().forEach((k, v) -> bDoc.put(String.valueOf(k), v));
            config.put(CAMPO_BLOCOS_POR_DIA_SEMANA, bDoc);
        }
        config.put(CAMPO_DISCIPLINAS_PARTICIPANTES_IDS, dados.disciplinasParticipantesIds());
        if (dados.semanaModeloManual() != null) {
            Document mDoc = new Document();
            dados.semanaModeloManual().forEach((k, v) -> mDoc.put(String.valueOf(k), v));
            config.put(CAMPO_SEMANA_MODELO_MANUAL, mDoc);
        }
        return config;
    }

    private CronogramaDTO paraDTO(Cronograma c, int quantidadeBlocos) {
        List<Integer> carga = calcularCargaSemanalCronograma(c);

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("modalidade", c.modalidade().name());
        configMap.put("duracaoBlocoMinutos", c.duracaoBlocoMinutos());
        configMap.put("tipoPeriodo", c.tipoPeriodo());
        configMap.put("dataInicio", c.dataInicio().toString());
        configMap.put("dataFim", c.dataFim().toString());
        configMap.put("diasAtivosSemana", c.diasAtivosSemana());
        configMap.put("blocosPorDia", c.blocosPorDia());
        configMap.put("blocosPorDiaSemana", c.blocosPorDiaSemana());
        configMap.put("disciplinasParticipantesIds", c.disciplinasParticipantesIds().stream().map(ObjectId::toHexString).toList());
        configMap.put("semanaModeloManual", c.semanaModeloManual());

        return new CronogramaDTO(
                c.idHex(),
                c.trilhaIdHex(),
                c.modalidade().name(),
                c.ativo(),
                c.versaoAtiva(),
                c.duracaoBlocoMinutos(),
                c.tipoPeriodo(),
                c.dataInicio().toString(),
                c.dataFim().toString(),
                c.diasAtivosSemana(),
                c.blocosPorDia(),
                c.blocosPorDiaSemana(),
                c.disciplinasParticipantesIds().stream().map(ObjectId::toHexString).toList(),
                c.semanaModeloManual(),
                configMap,
                quantidadeBlocos,
                carga,
                c.estadoPublicacao(),
                c.criadoEm() != null ? c.criadoEm().toString() : null,
                c.atualizadoEm() != null ? c.atualizadoEm().toString() : null
        );
    }

    private BlocoCronogramaItemDTO paraBlocoDTO(BlocoCronograma b, LocalDate hojeLocal) {
        String statusData;
        if (b.data().isBefore(hojeLocal)) {
            statusData = "ATRASADO";
        } else if (b.data().isEqual(hojeLocal)) {
            statusData = "HOJE";
        } else {
            statusData = "FUTURO";
        }

        return new BlocoCronogramaItemDTO(
                b.idHex(),
                b.trilhaIdHex(),
                b.cronogramaIdHex(),
                b.versaoGeracao(),
                b.dataIso(),
                b.ordem(),
                b.disciplinaIdHex(),
                b.disciplinaNomeSnapshot(),
                b.disciplinaCorSnapshot(),
                b.duracaoMinutos(),
                b.concluido(),
                b.concluidoEm() != null ? b.concluidoEm().toString() : null,
                statusData,
                b.criadoEm() != null ? b.criadoEm().toString() : null,
                b.atualizadoEm() != null ? b.atualizadoEm().toString() : null
        );
    }
}
