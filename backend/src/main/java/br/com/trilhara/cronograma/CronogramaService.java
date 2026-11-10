package br.com.trilhara.cronograma;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;

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
import br.com.trilhara.shared.erro.DomainConflictException;
import br.com.trilhara.shared.mongo.BsonDates;
import br.com.trilhara.shared.mongo.BsonIds;
import br.com.trilhara.trilha.Disciplina;
import br.com.trilhara.trilha.DisciplinaRepository;
import br.com.trilhara.trilha.TrilhaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class CronogramaService {

    private static final String VALOR_ATIVO = "ATIVO";
    private static final String ERRO_CRONOGRAMA_NAO_ENCONTRADO = "Cronograma não encontrado";
    private static final String ERRO_TRILHA_NAO_ENCONTRADA = "Trilha não encontrada";

    public record DadosConfiguracao(
            ModalidadeCronograma modalidade,
            int duracaoBlocoMinutos,
            String tipoPeriodo,
            LocalDate dataInicio,
            LocalDate dataFim,
            List<Integer> diasAtivosSemana,
            Integer blocosPorDia,
            List<ObjectId> disciplinasParticipantesIds,
            Map<Integer, List<String>> semanaModeloManual) {
    }

    private final CronogramaRepository cronogramaRepository;
    private final BlocoCronogramaRepository blocoCronogramaRepository;
    private final OperacaoConcluirAtrasadosRepository operacaoConcluirAtrasadosRepository;
    private final TrilhaRepository trilhaRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final UserRepository userRepository;
    private final TimezoneContext timezoneContext;

    @Inject
    public CronogramaService(
            CronogramaRepository cronogramaRepository,
            BlocoCronogramaRepository blocoCronogramaRepository,
            OperacaoConcluirAtrasadosRepository operacaoConcluirAtrasadosRepository,
            TrilhaRepository trilhaRepository,
            DisciplinaRepository disciplinaRepository,
            UserRepository userRepository,
            TimezoneContext timezoneContext) {
        this.cronogramaRepository = cronogramaRepository;
        this.blocoCronogramaRepository = blocoCronogramaRepository;
        this.operacaoConcluirAtrasadosRepository = operacaoConcluirAtrasadosRepository;
        this.trilhaRepository = trilhaRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.userRepository = userRepository;
        this.timezoneContext = timezoneContext;
    }

    public CronogramaDTO criar(String trilhaIdStr, CriarCronogramaRequest request) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.toObjectIdOrNotFound(trilhaIdStr);

        String leaseToken = UUID.randomUUID().toString();
        adquirirLeaseOuFalhar(usuarioId, trilhaId, leaseToken);

        try {
            // 1. Limite de 3 cronogramas por trilha
            if (cronogramaRepository.contarPorTrilha(usuarioId, trilhaId) >= 3) {
                throw new BadRequestException("A trilha já possui o limite máximo de 3 cronogramas.");
            }

            // 2. Conflito de modalidade duplicada na mesma trilha (409 DUPLICATE_MODALITY)
            if (cronogramaRepository.buscarPorModalidade(usuarioId, trilhaId, request.modalidade()).isPresent()) {
                throw new DomainConflictException(
                        "DUPLICATE_MODALITY",
                        "Esta trilha já possui um cronograma na modalidade selecionada.");
            }

            Document trilhaDoc = trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                    .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

            LocalDate dataInicio = request.dataInicio() != null ? request.dataInicio() : timezoneContext.hojeLocal();
            LocalDate dataFim = calcularDataFim(request.tipoPeriodo(), dataInicio, request.dataFim(), trilhaDoc);

            List<Disciplina> participantes = validarDisciplinasParticipantes(
                    usuarioId, trilhaId, request.disciplinasParticipantesIds());

            ObjectId cronogramaId = new ObjectId();
            long versaoNova = 1L;

            // Gerar blocos
            List<BlocoCronograma> blocos = GeradorBlocosCronograma.gerar(
                    new GeradorBlocosCronograma.ParametrosGeracao(
                            usuarioId, trilhaId, cronogramaId, versaoNova,
                            request.modalidade(), request.duracaoBlocoMinutos(),
                            dataInicio, dataFim, request.diasAtivosSemana(),
                            request.blocosPorDia(), participantes, request.semanaModeloManual()));

            Instant agora = Instant.now();
            Document configDoc = montarConfiguracaoDocumento(new DadosConfiguracao(
                    request.modalidade(), request.duracaoBlocoMinutos(), request.tipoPeriodo(),
                    dataInicio, dataFim, request.diasAtivosSemana(), request.blocosPorDia(),
                    participantes.stream().map(Disciplina::id).toList(), request.semanaModeloManual()));

            // Novo cronograma começa inativo durante o staging para respeitar o índice único parcial
            Cronograma novo = new Cronograma(
                    cronogramaId, usuarioId, trilhaId, request.modalidade(),
                    false, versaoNova, null, "PREPARANDO",
                    request.duracaoBlocoMinutos(), request.tipoPeriodo(),
                    dataInicio, dataFim, request.diasAtivosSemana(),
                    request.blocosPorDia(), participantes.stream().map(Disciplina::id).toList(),
                    request.semanaModeloManual(), configDoc, null, agora, agora);

            // Staging e inserção em lotes
            cronogramaRepository.inserir(novo);
            blocoCronogramaRepository.inserirLote(blocos);

            long contagem = blocoCronogramaRepository.contarPorVersao(usuarioId, trilhaId, cronogramaId, versaoNova);
            if (contagem != blocos.size()) {
                throw new IllegalStateException("Falha na geração: quantidade de blocos inseridos difere do esperado.");
            }

            // Atualização atômica do ponteiro na trilha pai
            trilhaRepository.atualizarPonteiroCronogramaAtivo(usuarioId, trilhaId, cronogramaId, versaoNova);

            // Reconcilia flags ativos: desativa anteriores ANTES de marcar o novo ativo para respeitar o índice parcial único
            cronogramaRepository.desativarOutros(usuarioId, trilhaId, cronogramaId);

            Cronograma ativo = new Cronograma(
                    cronogramaId, usuarioId, trilhaId, request.modalidade(),
                    true, versaoNova, null, VALOR_ATIVO,
                    request.duracaoBlocoMinutos(), request.tipoPeriodo(),
                    dataInicio, dataFim, request.diasAtivosSemana(),
                    request.blocosPorDia(), participantes.stream().map(Disciplina::id).toList(),
                    request.semanaModeloManual(), configDoc, null, agora, Instant.now());
            cronogramaRepository.atualizar(ativo);

            return paraDTO(ativo, blocos.size());
        } finally {
            trilhaRepository.liberarLease(usuarioId, trilhaId, leaseToken);
        }
    }

    public CronogramaDTO atualizar(String trilhaIdStr, String cronogramaIdStr, AtualizarCronogramaRequest request) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.toObjectIdOrNotFound(trilhaIdStr);
        ObjectId cronogramaId = BsonIds.toObjectIdOrNotFound(cronogramaIdStr);

        String leaseToken = UUID.randomUUID().toString();
        adquirirLeaseOuFalhar(usuarioId, trilhaId, leaseToken);

        try {
            Cronograma existente = cronogramaRepository.buscarPorId(usuarioId, trilhaId, cronogramaId)
                    .orElseThrow(() -> new NotFoundException(ERRO_CRONOGRAMA_NAO_ENCONTRADO));

            // Conflito de modalidade ao editar pelo lápis (§4.2 B.2)
            if (request.modalidade() != existente.modalidade()
                    && cronogramaRepository.buscarPorModalidadeExceto(usuarioId, trilhaId, request.modalidade(), cronogramaId).isPresent()) {
                throw new DomainConflictException(
                        "DUPLICATE_MODALITY",
                        "Esta trilha já possui um cronograma na modalidade selecionada.");
            }

            Document trilhaDoc = trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                    .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));

            LocalDate dataInicio = request.dataInicio() != null ? request.dataInicio() : timezoneContext.hojeLocal();
            LocalDate dataFim = calcularDataFim(request.tipoPeriodo(), dataInicio, request.dataFim(), trilhaDoc);

            List<Disciplina> participantes = validarDisciplinasParticipantes(
                    usuarioId, trilhaId, request.disciplinasParticipantesIds());

            long versaoNova = existente.versaoAtiva() + 1L;

            // Staging preparado
            Document configPreparada = montarConfiguracaoDocumento(new DadosConfiguracao(
                    request.modalidade(), request.duracaoBlocoMinutos(), request.tipoPeriodo(),
                    dataInicio, dataFim, request.diasAtivosSemana(), request.blocosPorDia(),
                    participantes.stream().map(Disciplina::id).toList(), request.semanaModeloManual()));

            Cronograma preparando = new Cronograma(
                    existente.id(), usuarioId, trilhaId, request.modalidade(),
                    existente.ativo(), existente.versaoAtiva(), versaoNova, "PREPARANDO",
                    request.duracaoBlocoMinutos(), request.tipoPeriodo(),
                    dataInicio, dataFim, request.diasAtivosSemana(),
                    request.blocosPorDia(), participantes.stream().map(Disciplina::id).toList(),
                    request.semanaModeloManual(), existente.configuracaoAtiva(), configPreparada,
                    existente.criadoEm(), Instant.now());
            cronogramaRepository.atualizar(preparando);

            // Gerar e gravar novos blocos
            List<BlocoCronograma> novosBlocos = GeradorBlocosCronograma.gerar(
                    new GeradorBlocosCronograma.ParametrosGeracao(
                            usuarioId, trilhaId, cronogramaId, versaoNova,
                            request.modalidade(), request.duracaoBlocoMinutos(),
                            dataInicio, dataFim, request.diasAtivosSemana(),
                            request.blocosPorDia(), participantes, request.semanaModeloManual()));

            blocoCronogramaRepository.inserirLote(novosBlocos);

            long contagem = blocoCronogramaRepository.contarPorVersao(usuarioId, trilhaId, cronogramaId, versaoNova);
            if (contagem != novosBlocos.size()) {
                throw new IllegalStateException("Falha na geração: contagem de blocos em staging diverge.");
            }

            // Atualização atômica do ponteiro da trilha pai
            trilhaRepository.atualizarPonteiroCronogramaAtivo(usuarioId, trilhaId, cronogramaId, versaoNova);

            // Desativa outros cronogramas da mesma trilha
            cronogramaRepository.desativarOutros(usuarioId, trilhaId, cronogramaId);

            // Publica versão ativa
            Cronograma publicado = new Cronograma(
                    existente.id(), usuarioId, trilhaId, request.modalidade(),
                    true, versaoNova, null, VALOR_ATIVO,
                    request.duracaoBlocoMinutos(), request.tipoPeriodo(),
                    dataInicio, dataFim, request.diasAtivosSemana(),
                    request.blocosPorDia(), participantes.stream().map(Disciplina::id).toList(),
                    request.semanaModeloManual(), configPreparada, null,
                    existente.criadoEm(), Instant.now());
            cronogramaRepository.atualizar(publicado);

            // Limpa versões antigas de blocos deste cronograma
            blocoCronogramaRepository.excluirVersoesDiferentes(usuarioId, trilhaId, cronogramaId, versaoNova);

            return paraDTO(publicado, novosBlocos.size());
        } finally {
            trilhaRepository.liberarLease(usuarioId, trilhaId, leaseToken);
        }
    }

    public CronogramaDTO ativar(String trilhaIdStr, String cronogramaIdStr) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.toObjectIdOrNotFound(trilhaIdStr);
        ObjectId cronogramaId = BsonIds.toObjectIdOrNotFound(cronogramaIdStr);

        String leaseToken = UUID.randomUUID().toString();
        adquirirLeaseOuFalhar(usuarioId, trilhaId, leaseToken);

        try {
            Cronograma cronograma = cronogramaRepository.buscarPorId(usuarioId, trilhaId, cronogramaId)
                    .orElseThrow(() -> new NotFoundException(ERRO_CRONOGRAMA_NAO_ENCONTRADO));

            // Troca atomicamente o ponteiro na trilha
            trilhaRepository.atualizarPonteiroCronogramaAtivo(
                    usuarioId, trilhaId, cronogramaId, cronograma.versaoAtiva());

            // Reconcilia flags ativos
            cronogramaRepository.desativarOutros(usuarioId, trilhaId, cronogramaId);

            Cronograma atualizado = new Cronograma(
                    cronograma.id(), usuarioId, trilhaId, cronograma.modalidade(),
                    true, cronograma.versaoAtiva(), cronograma.versaoPreparada(), VALOR_ATIVO,
                    cronograma.duracaoBlocoMinutos(), cronograma.tipoPeriodo(),
                    cronograma.dataInicio(), cronograma.dataFim(), cronograma.diasAtivosSemana(),
                    cronograma.blocosPorDia(), cronograma.disciplinasParticipantesIds(),
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
        ObjectId trilhaId = BsonIds.toObjectIdOrNotFound(trilhaIdStr);
        ObjectId cronogramaId = BsonIds.toObjectIdOrNotFound(cronogramaIdStr);

        String leaseToken = UUID.randomUUID().toString();
        adquirirLeaseOuFalhar(usuarioId, trilhaId, leaseToken);

        try {
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
                    trilhaRepository.atualizarPonteiroCronogramaAtivo(
                            usuarioId, trilhaId, restante.id(), restante.versaoAtiva());
                    cronogramaRepository.desativarOutros(usuarioId, trilhaId, restante.id());
                    Cronograma promovido = new Cronograma(
                            restante.id(), usuarioId, trilhaId, restante.modalidade(),
                            true, restante.versaoAtiva(), restante.versaoPreparada(), VALOR_ATIVO,
                            restante.duracaoBlocoMinutos(), restante.tipoPeriodo(),
                            restante.dataInicio(), restante.dataFim(), restante.diasAtivosSemana(),
                            restante.blocosPorDia(), restante.disciplinasParticipantesIds(),
                            restante.semanaModeloManual(), restante.configuracaoAtiva(), restante.configuracaoPreparada(),
                            restante.criadoEm(), Instant.now());
                    cronogramaRepository.atualizar(promovido);
                } else {
                    trilhaRepository.atualizarPonteiroCronogramaAtivo(usuarioId, trilhaId, null, null);
                }
            }
        } finally {
            trilhaRepository.liberarLease(usuarioId, trilhaId, leaseToken);
        }
    }

    public List<CronogramaDTO> listar(String trilhaIdStr) {
        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.toObjectIdOrNotFound(trilhaIdStr);

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
            Boolean concluido,
            LocalDate dataInicio,
            LocalDate dataFim,
            int pagina,
            int tamanho) {

        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.toObjectIdOrNotFound(trilhaIdStr);
        ObjectId cronogramaId = BsonIds.toObjectIdOrNotFound(cronogramaIdStr);

        Cronograma cronograma = cronogramaRepository.buscarPorId(usuarioId, trilhaId, cronogramaId)
                .orElseThrow(() -> new NotFoundException(ERRO_CRONOGRAMA_NAO_ENCONTRADO));

        LocalDate hojeLocal = timezoneContext.hojeLocal();

        var paginado = blocoCronogramaRepository.listarPaginado(
                usuarioId, trilhaId, cronogramaId, cronograma.versaoAtiva(),
                concluido, dataInicio, dataFim, pagina, tamanho);

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
        List<ObjectId> cronogramasAtivosIds = new ArrayList<>();

        for (Document td : trilhasDocs) {
            ObjectId cronogramaAtivoId = td.getObjectId("cronogramaAtivoId");
            if (cronogramaAtivoId != null) {
                cronogramasAtivosIds.add(cronogramaAtivoId);
                nomesTrilhasPorCronogramaId.put(cronogramaAtivoId, td.getString("nome"));
            }
        }

        if (cronogramasAtivosIds.isEmpty()) {
            return List.of();
        }

        List<BlocoCronograma> blocos = blocoCronogramaRepository.listarHoje(
                usuarioId, hojeLocal, cronogramasAtivosIds);

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
        ObjectId blocoId = BsonIds.toObjectIdOrNotFound(blocoIdStr);

        BlocoCronograma bloco = blocoCronogramaRepository.buscarPorId(usuarioId, blocoId)
                .orElseThrow(() -> new NotFoundException("Bloco não encontrado"));

        LocalDate hojeLocal = timezoneContext.hojeLocal();

        // Operação idempotente: repetição no mesmo estado não altera timestamps nem concluidoEm
        if (bloco.concluido() == concluido) {
            return paraBlocoDTO(bloco, hojeLocal);
        }

        Instant concluidoEm = concluido ? Instant.now() : null;
        blocoCronogramaRepository.atualizarConclusao(usuarioId, blocoId, concluido, concluidoEm);

        BlocoCronograma atualizado = blocoCronogramaRepository.buscarPorId(usuarioId, blocoId)
                .orElseThrow(() -> new NotFoundException("Bloco não encontrado após atualização"));

        return paraBlocoDTO(atualizado, hojeLocal);
    }

    public ConcluirAtrasadosResponse concluirAtrasados(
            String trilhaIdStr, String cronogramaIdStr, UUID operationId) {

        ObjectId usuarioId = timezoneContext.usuarioIdObjectId();
        ObjectId trilhaId = BsonIds.toObjectIdOrNotFound(trilhaIdStr);
        ObjectId cronogramaId = BsonIds.toObjectIdOrNotFound(cronogramaIdStr);

        // 1. Idempotência por operationId
        var cache = operacaoConcluirAtrasadosRepository.buscarPorOperationId(usuarioId, operationId);
        if (cache.isPresent()) {
            return cache.get();
        }

        // 2. Não executa duas operações do mesmo usuário/trilha/cronograma em paralelo
        if (operacaoConcluirAtrasadosRepository.existeOperacaoEmAndamento(usuarioId, trilhaId, cronogramaId)) {
            throw new DomainConflictException(
                    "OPERATION_IN_PROGRESS",
                    "Uma operação de conclusão de blocos atrasados já está em andamento para este cronograma.");
        }

        Cronograma cronograma = cronogramaRepository.buscarPorId(usuarioId, trilhaId, cronogramaId)
                .orElseThrow(() -> new NotFoundException(ERRO_CRONOGRAMA_NAO_ENCONTRADO));

        operacaoConcluirAtrasadosRepository.registrarInicio(usuarioId, trilhaId, cronogramaId, operationId);

        LocalDate hojeLocal = timezoneContext.hojeLocal();
        List<BlocoCronograma> atrasados = blocoCronogramaRepository.listarAtrasadosPendentes(
                usuarioId, trilhaId, cronogramaId, cronograma.versaoAtiva(), hojeLocal);

        int concluidos = 0;
        List<FalhaBlocoDTO> falhas = new ArrayList<>();
        Instant agora = Instant.now();

        for (BlocoCronograma b : atrasados) {
            try {
                boolean ok = blocoCronogramaRepository.atualizarConclusao(usuarioId, b.id(), true, agora);
                if (ok) {
                    concluidos++;
                } else {
                    falhas.add(new FalhaBlocoDTO(b.idHex(), "FALHA_CONCLUSAO"));
                }
            } catch (Exception _) {
                falhas.add(new FalhaBlocoDTO(b.idHex(), "ERRO_INTERNO"));
            }
        }

        operacaoConcluirAtrasadosRepository.registrarConclusao(
                usuarioId, operationId, atrasados.size(), concluidos, falhas);

        return new ConcluirAtrasadosResponse(operationId, atrasados.size(), concluidos, falhas);
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
            throw new BadRequestException("disponibilidadeSemanalMinutos deve conter exatamente 7 valores.");
        }
        for (Integer m : minutos) {
            if (m == null || m < 0 || m > 1440) {
                throw new BadRequestException("Os minutos devem estar entre 0 e 1440.");
            }
        }

        userRepository.salvarDisponibilidadeSemanal(usuarioId, minutos);
        return obterDisponibilidadeSemanal();
    }

    private List<Integer> calcularCargaAtivaPorDia(ObjectId usuarioId, LocalDate hojeLocal) {
        List<Integer> cargaPorDia = new ArrayList<>(List.of(0, 0, 0, 0, 0, 0, 0));

        List<Document> trilhas = trilhaRepository.listarEntidades(usuarioId);
        for (Document t : trilhas) {
            ObjectId cronogramaAtivoId = t.getObjectId("cronogramaAtivoId");
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
        int blocosDia = c.blocosPorDia() != null ? c.blocosPorDia() : 1;
        int duracao = c.duracaoBlocoMinutos();
        for (Integer dia : c.diasAtivosSemana()) {
            if (dia != null && dia >= 1 && dia <= 7) {
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
        Instant expiraEm = Instant.now().plusSeconds(120);
        boolean adquirido = trilhaRepository.adquirirLease(usuarioId, trilhaId, leaseToken, expiraEm);
        if (!adquirido) {
            trilhaRepository.buscarEntidade(usuarioId, trilhaId)
                    .orElseThrow(() -> new NotFoundException(ERRO_TRILHA_NAO_ENCONTRADA));
            throw new DomainConflictException(
                    "SCHEDULE_GENERATION_IN_PROGRESS",
                    "Uma geração ou alteração de cronograma já está em andamento para esta trilha.");
        }
    }

    private LocalDate calcularDataFim(String tipoPeriodo, LocalDate dataInicio, LocalDate dataFimInformada, Document trilhaDoc) {
        if (dataFimInformada != null) {
            return dataFimInformada;
        }
        if ("ATE_PROVA".equalsIgnoreCase(tipoPeriodo)) {
            LocalDate dataProva = BsonDates.toLocalDate(trilhaDoc.getString("dataProva"));
            if (dataProva != null && !dataProva.isBefore(dataInicio)) {
                return dataProva;
            }
            return dataInicio.plusDays(30);
        }
        if ("UMA_SEMANA".equalsIgnoreCase(tipoPeriodo)) {
            return dataInicio.plusDays(6);
        }
        if ("TRES_MESES".equalsIgnoreCase(tipoPeriodo)) {
            return dataInicio.plusDays(89);
        }
        // Default: 1 mês (30 dias)
        return dataInicio.plusDays(29);
    }

    private List<Disciplina> validarDisciplinasParticipantes(
            ObjectId usuarioId, ObjectId trilhaId, List<String> participantesIdsStr) {
        if (participantesIdsStr == null || participantesIdsStr.isEmpty()) {
            throw new BadRequestException("Ao menos uma disciplina participante deve ser informada.");
        }

        Set<ObjectId> idsDesejados = participantesIdsStr.stream()
                .map(BsonIds::toObjectIdOrNotFound)
                .collect(Collectors.toSet());

        List<Disciplina> ativas = disciplinaRepository.listarPorTrilha(usuarioId, trilhaId);
        List<Disciplina> filtradas = ativas.stream()
                .filter(d -> idsDesejados.contains(d.id()))
                .toList();

        if (filtradas.isEmpty()) {
            throw new BadRequestException("Nenhuma das disciplinas informadas pertence a esta trilha.");
        }
        return filtradas;
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
        config.put("disciplinasParticipantesIds", dados.disciplinasParticipantesIds());
        if (dados.semanaModeloManual() != null) {
            Document mDoc = new Document();
            dados.semanaModeloManual().forEach((k, v) -> mDoc.put(String.valueOf(k), v));
            config.put("semanaModeloManual", mDoc);
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
