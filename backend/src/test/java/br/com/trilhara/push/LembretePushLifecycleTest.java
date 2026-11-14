package br.com.trilhara.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.trilhara.auth.Role;
import br.com.trilhara.auth.UserRepository;
import br.com.trilhara.auth.Usuario;
import br.com.trilhara.tarefa.ModoLembrete;
import br.com.trilhara.tarefa.SerieRecorrenteService;
import br.com.trilhara.tarefa.Tarefa;
import br.com.trilhara.tarefa.TarefaLixeiraService;
import br.com.trilhara.tarefa.TarefaRepository;
import br.com.trilhara.tarefa.TarefaService;
import br.com.trilhara.tarefa.dto.CriarSerieRecorrenteRequest;
import br.com.trilhara.tarefa.dto.CriarTarefaRequest;
import br.com.trilhara.tarefa.dto.SerieRecorrenteResponse;
import br.com.trilhara.tarefa.dto.TarefaResponse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * ciclo de vida do agendamento.
 *
 * <p>Todas as datas são calculadas a partir de {@code Instant.now} porque a elegibilidade
 * exige {@code instanteEntregaUtc > agora}: data fixa no passado tornaria qualquer cenário inelegível.</p>
 */
@QuarkusTest
class LembretePushLifecycleTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Inject
    TarefaService tarefaService;

    @Inject
    SerieRecorrenteService serieRecorrenteService;

    @Inject
    TarefaLixeiraService tarefaLixeiraService;

    @Inject
    TarefaRepository tarefaRepository;

    @Inject
    LembretePushService lembretePushService;

    @Inject
    LembretePushRepository lembretePushRepository;

    @Inject
    UserRepository userRepository;

    private ObjectId usuarioId;
    private String usuarioIdStr;

    @BeforeEach
    void setUp() {
        usuarioId = new ObjectId();
        usuarioIdStr = usuarioId.toHexString();

        Usuario usuario = new Usuario(
                usuarioIdStr,
                "Teste Push Lifecycle",
                "push-" + UUID.randomUUID() + "@teste.com",
                "hash",
                List.of(Role.USER),
                true, true, 0, null, null, Instant.now(), Instant.now());
        userRepository.inserir(usuario);
    }

    /** Data local de amanhã no fuso do agendamento (sempre no futuro). */
    private static LocalDate amanha() {
        return ZonedDateTime.now(FUSO).toLocalDate().plusDays(1);
    }

    private CriarTarefaRequest request(String titulo, String horario, ModoLembrete modo, Integer minutos) {
        return new CriarTarefaRequest(
                null, titulo, null, "MEDIA",
                amanha().toString(), horario, null,
                modo, minutos, FUSO.getId(), 1);
    }

    private TarefaResponse criar(String titulo, String horario, ModoLembrete modo, Integer minutos) {
        return tarefaService.criarTarefa(request(titulo, horario, modo, minutos), usuarioIdStr);
    }

    private LembretePush versaoVigente(ObjectId tarefaId) {
        return lembretePushRepository.buscarVersaoVigente(usuarioId, tarefaId).orElseThrow();
    }

    @Test
    @DisplayName("semAlertaNaoAgenda: SEM_ALERTA nunca cria versão de lembrete")
    void semAlertaNaoAgenda() {
        TarefaResponse tarefa = criar("Sem alerta", "10:00", ModoLembrete.SEM_ALERTA, null);

        assertTrue(lembretePushRepository
                .buscarVersaoVigente(usuarioId, new ObjectId(tarefa.id())).isEmpty());
    }

    @Test
    @DisplayName("herdarAcompanhaPadrao: tarefa HERDAR segue o padrão global do usuário")
    void herdarAcompanhaPadrao() {
        TarefaResponse tarefa = criar("Herdeira", "10:00", ModoLembrete.HERDAR, null);
        ObjectId tarefaId = new ObjectId(tarefa.id());

        // Padrão default é 15 min: 10:00 local (UTC-3) menos 15 min = 12:45 UTC.
        assertEquals(Instant.parse(amanha() + "T12:45:00Z"), versaoVigente(tarefaId).instanteEntregaUtc());

        lembretePushService.atualizarPreferenciaGlobal(usuarioId, 30);

        // Após o novo padrão (30 min) a versão vigente é reagendada para 12:30 UTC.
        assertEquals(Instant.parse(amanha() + "T12:30:00Z"), versaoVigente(tarefaId).instanteEntregaUtc());
    }

    @Test
    @DisplayName("personalizadoNaoAcompanhaPadrao: override local sobrevive à mudança do padrão")
    void personalizadoNaoAcompanhaPadrao() {
        TarefaResponse tarefa = criar("Override", "10:00", ModoLembrete.PERSONALIZADO, 5);
        ObjectId tarefaId = new ObjectId(tarefa.id());

        assertEquals(Instant.parse(amanha() + "T12:55:00Z"), versaoVigente(tarefaId).instanteEntregaUtc());

        lembretePushService.atualizarPreferenciaGlobal(usuarioId, 60);

        // 5 minutos antes permanece: 13:00 UTC - 5 min = 12:55 UTC.
        assertEquals(Instant.parse(amanha() + "T12:55:00Z"), versaoVigente(tarefaId).instanteEntregaUtc());
    }

    @Test
    @DisplayName("removerHorarioCancelaEPreservaModo: suspender cancela o push e mantém a escolha")
    void removerHorarioCancelaEPreservaModo() {
        TarefaResponse criada = criar("Suspensa", "10:00", ModoLembrete.PERSONALIZADO, 30);
        ObjectId tarefaId = new ObjectId(criada.id());
        assertEquals(LembretePush.ESTADO_PENDENTE, versaoVigente(tarefaId).estado());

        // Remove o horário persistindo a tarefa sem `horario`, mantendo modo/minutos.
        Tarefa existente = tarefaRepository.buscar(usuarioId, tarefaId).orElseThrow();
        Tarefa semHorario = new Tarefa(
                existente.id(), existente.usuarioId(), existente.listaId(), existente.serieId(),
                existente.dataOriginalOcorrencia(), existente.desvinculadaDaSerie(), existente.titulo(),
                existente.descricao(), existente.prioridade(), existente.dataVencimento(), null,
                existente.estado(), existente.concluida(), existente.concluidaEm(), existente.ordem(),
                existente.subtarefas(), existente.modoLembrete(), existente.lembreteMinutosAntes(),
                existente.fusoAgendamento(), existente.deletedAt(), existente.expiresAt(),
                existente.loteExclusaoId(), existente.noFluxo(), existente.sucessorasIds(),
                existente.paresIds(), existente.posicaoFluxo(), existente.criadoEm(), Instant.now());
        tarefaRepository.atualizar(semHorario);

        lembretePushService.agendarOuAtualizarLembrete(semHorario, null, null, null);

        assertEquals(LembretePush.ESTADO_CANCELADO, versaoVigente(tarefaId).estado(),
                "Remover o horário suspende o lembrete pendente.");
        Tarefa relida = tarefaRepository.buscar(usuarioId, tarefaId).orElseThrow();
        assertEquals(ModoLembrete.PERSONALIZADO, relida.modoLembrete(),
                "A escolha do usuário é preservada na suspensão.");
        assertEquals(30, relida.lembreteMinutosAntes());
    }

    @Test
    @DisplayName("redefinirHorarioReagendaNovaVersao: voltar a definir horário cria nova versão futuro")
    void redefinirHorarioReagendaNovaVersao() {
        TarefaResponse criada = criar("Reagendada", "10:00", ModoLembrete.PERSONALIZADO, 30);
        ObjectId tarefaId = new ObjectId(criada.id());
        long versaoInicial = versaoVigente(tarefaId).versaoAgendamento();

        Tarefa existente = tarefaRepository.buscar(usuarioId, tarefaId).orElseThrow();
        Tarefa comNovoHorario = new Tarefa(
                existente.id(), existente.usuarioId(), existente.listaId(), existente.serieId(),
                existente.dataOriginalOcorrencia(), existente.desvinculadaDaSerie(), existente.titulo(),
                existente.descricao(), existente.prioridade(), existente.dataVencimento(), "11:00",
                existente.estado(), existente.concluida(), existente.concluidaEm(), existente.ordem(),
                existente.subtarefas(), existente.modoLembrete(), existente.lembreteMinutosAntes(),
                existente.fusoAgendamento(), existente.deletedAt(), existente.expiresAt(),
                existente.loteExclusaoId(), existente.noFluxo(), existente.sucessorasIds(),
                existente.paresIds(), existente.posicaoFluxo(), existente.criadoEm(), Instant.now());
        tarefaRepository.atualizar(comNovoHorario);

        lembretePushService.agendarOuAtualizarLembrete(comNovoHorario, null, null, null);

        LembretePush vigente = versaoVigente(tarefaId);
        assertEquals(LembretePush.ESTADO_PENDENTE, vigente.estado());
        assertEquals(versaoInicial + 1, vigente.versaoAgendamento(), "Cada mudança relevante cria versão nova.");
        // 11:00 local (UTC-3) menos 30 min = 13:30 UTC.
        assertEquals(Instant.parse(amanha() + "T13:30:00Z"), vigente.instanteEntregaUtc());
    }

    @Test
    @DisplayName("concluirCancela: concluir a tarefa cancela o lembrete pendente")
    void concluirCancela() {
        TarefaResponse criada = criar("Concluída", "10:00", ModoLembrete.HERDAR, null);
        ObjectId tarefaId = new ObjectId(criada.id());
        assertEquals(LembretePush.ESTADO_PENDENTE, versaoVigente(tarefaId).estado());

        tarefaService.atualizarConclusao(criada.id(), true, usuarioIdStr);

        assertEquals(LembretePush.ESTADO_CANCELADO, versaoVigente(tarefaId).estado());
    }

    @Test
    @DisplayName("lixeiraCancela: mover para a lixeira cancela o lembrete pendente")
    void lixeiraCancela() {
        TarefaResponse criada = criar("Na lixeira", "10:00", ModoLembrete.HERDAR, null);
        ObjectId tarefaId = new ObjectId(criada.id());
        assertEquals(LembretePush.ESTADO_PENDENTE, versaoVigente(tarefaId).estado());

        tarefaService.excluirTarefa(criada.id(), null, usuarioIdStr);

        assertEquals(LembretePush.ESTADO_CANCELADO, versaoVigente(tarefaId).estado());
    }

    @Test
    @DisplayName("restaurarReagendaSeFuturo: restaurar da lixeira reagenda quando a entrega é futura")
    void restaurarReagendaSeFuturo() {
        TarefaResponse criada = criar("Restaurável", "10:00", ModoLembrete.HERDAR, null);
        ObjectId tarefaId = new ObjectId(criada.id());

        tarefaService.excluirTarefa(criada.id(), null, usuarioIdStr);
        assertEquals(LembretePush.ESTADO_CANCELADO, versaoVigente(tarefaId).estado());

        tarefaLixeiraService.restaurarTarefaDaLixeira(criada.id(), usuarioIdStr);

        LembretePush vigente = versaoVigente(tarefaId);
        assertEquals(LembretePush.ESTADO_PENDENTE, vigente.estado(),
                "Restauração com entrega futura volta a agendar.");
        assertTrue(vigente.instanteEntregaUtc().isAfter(Instant.now()));
    }

    @Test
    @DisplayName("ocorrenciaRecorrenteHerdaModoDaSerie: ocorrência materializada herda o modo e agenda")
    void ocorrenciaRecorrenteHerdaModoDaSerie() {
        CriarSerieRecorrenteRequest serie = new CriarSerieRecorrenteRequest(
                null, "Série com lembrete", null, "MEDIA", "10:00", List.of(), "DIARIO", 1,
                List.of(), null, null, null, null, "EM_DATA",
                amanha().toString(), amanha().plusDays(30).toString(), null,
                ModoLembrete.PERSONALIZADO, 60, FUSO.getId(), null);


        SerieRecorrenteResponse criada = serieRecorrenteService.criarSerie(serie, usuarioIdStr);

        List<TarefaResponse> ocorrencias = tarefaService
                .listarTarefas(usuarioIdStr, null, 1, 100)
                .itens().stream()
                .filter(t -> criada.id().equals(t.serieId()))
                .toList();

        assertTrue(!ocorrencias.isEmpty(), "A série deve materializar ao menos uma ocorrência futura.");
        TarefaResponse primeira = ocorrencias.get(0);
        assertEquals(ModoLembrete.PERSONALIZADO, primeira.modoLembrete(),
                "A ocorrência herda o modo de lembrete da série.");
        assertEquals(60, primeira.lembreteMinutosAntes());

        assertEquals(LembretePush.ESTADO_PENDENTE, versaoVigente(new ObjectId(primeira.id())).estado(),
                "Ocorrência materializada também gera lembrete.");
    }
}
