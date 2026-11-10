package br.com.trilhara.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.trilhara.auth.Role;
import br.com.trilhara.auth.UserRepository;
import br.com.trilhara.auth.Usuario;
import br.com.trilhara.tarefa.Tarefa;
import br.com.trilhara.tarefa.TarefaRepository;
import br.com.trilhara.tarefa.TarefaService;
import br.com.trilhara.tarefa.dto.CriarTarefaRequest;
import br.com.trilhara.tarefa.dto.TarefaResponse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class LembretePushLifecycleTest {

    @Inject
    TarefaService tarefaService;

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
                true,
                true,
                0,
                null,
                null,
                Instant.now(),
                Instant.now()
        );
        userRepository.inserir(usuario);
    }

    @Test
    @DisplayName("Cancelamento ao concluir e reagendamento ao reabrir tarefa")
    void deveCancelarAoConcluirEReagendarAoReabrir() {
        CriarTarefaRequest request = new CriarTarefaRequest(
                null,
                "Estudar Direito Administrativo",
                "Foco em atos administrativos",
                "ALTA",
                "2026-06-15",
                "14:00",
                null,
                15,
                false,
                "America/Sao_Paulo",
                1
        );

        TarefaResponse criada = tarefaService.criarTarefa(request, usuarioIdStr);
        ObjectId tarefaId = new ObjectId(criada.id());

        var lembreteOpt = lembretePushRepository.buscarPorTarefa(usuarioId, tarefaId);
        assertTrue(lembreteOpt.isPresent());
        assertEquals(LembretePush.ESTADO_PENDENTE, lembreteOpt.get().estado());

        // Concluir tarefa cancela o lembrete
        tarefaService.atualizarConclusao(criada.id(), true, usuarioIdStr);
        var lembreteConcluida = lembretePushRepository.buscarPorTarefa(usuarioId, tarefaId);
        assertTrue(lembreteConcluida.isPresent());
        assertEquals(LembretePush.ESTADO_CANCELADO, lembreteConcluida.get().estado());

        // Reabrir tarefa reagenda o lembrete
        tarefaService.atualizarConclusao(criada.id(), false, usuarioIdStr);
        var lembreteReaberta = lembretePushRepository.buscarPorTarefa(usuarioId, tarefaId);
        assertTrue(lembreteReaberta.isPresent());
        assertEquals(LembretePush.ESTADO_PENDENTE, lembreteReaberta.get().estado());
    }

    @Test
    @DisplayName("Cancelamento ao mover tarefa para a lixeira")
    void deveCancelarAoMoverParaLixeira() {
        CriarTarefaRequest request = new CriarTarefaRequest(
                null,
                "Tarefa para exclusao",
                null,
                "MEDIA",
                "2026-06-15",
                "16:00",
                null,
                15,
                true,
                "America/Sao_Paulo",
                1
        );

        TarefaResponse criada = tarefaService.criarTarefa(request, usuarioIdStr);
        ObjectId tarefaId = new ObjectId(criada.id());

        assertTrue(lembretePushRepository.buscarPorTarefa(usuarioId, tarefaId).isPresent());

        // Mover para lixeira
        tarefaService.excluirTarefa(criada.id(), "ESTA", usuarioIdStr);

        var lembreteLixeira = lembretePushRepository.buscarPorTarefa(usuarioId, tarefaId);
        assertTrue(lembreteLixeira.isPresent());
        assertEquals(LembretePush.ESTADO_CANCELADO, lembreteLixeira.get().estado());
    }

    @Test
    @DisplayName("Suspensão ao remover horário preservando preferência, e reagendamento ao redefinir horário")
    void deveSuspenderAoRemoverHorarioEReagendarAoRedefinir() {
        CriarTarefaRequest request = new CriarTarefaRequest(
                null,
                "Tarefa com horario temporario",
                null,
                "BAIXA",
                "2026-06-15",
                "10:00",
                null,
                30,
                false,
                "America/Sao_Paulo",
                1
        );

        TarefaResponse criada = tarefaService.criarTarefa(request, usuarioIdStr);
        ObjectId tarefaId = new ObjectId(criada.id());

        // Remove o horário da tarefa
        // Para atualizar explicitamente horario como null, chamamos atualizarTarefa passando horario = null
        Tarefa tarefaExistente = tarefaRepository.buscar(usuarioId, tarefaId).get();
        Tarefa semHorario = new Tarefa(
                tarefaExistente.id(),
                tarefaExistente.usuarioId(),
                tarefaExistente.listaId(),
                null,
                null,
                false,
                tarefaExistente.titulo(),
                tarefaExistente.descricao(),
                tarefaExistente.prioridade(),
                tarefaExistente.dataVencimento(),
                null, // horário removido
                tarefaExistente.estado(),
                tarefaExistente.concluida(),
                tarefaExistente.concluidaEm(),
                tarefaExistente.ordem(),
                tarefaExistente.subtarefas(),
                tarefaExistente.lembreteMinutosAntes(),
                tarefaExistente.usaPadraoGlobalLembrete(),
                tarefaExistente.fusoAgendamento(),
                tarefaExistente.deletedAt(),
                tarefaExistente.expiresAt(),
                tarefaExistente.loteExclusaoId(),
                tarefaExistente.criadoEm(),
                Instant.now()
        );
        tarefaRepository.atualizar(semHorario);
        lembretePushService.agendarOuAtualizarLembrete(semHorario, null, null, null);

        // Lembrete suspenso (CANCELADO)
        var lembreteSuspenso = lembretePushRepository.buscarPorTarefa(usuarioId, tarefaId);
        assertTrue(lembreteSuspenso.isPresent());
        assertEquals(LembretePush.ESTADO_CANCELADO, lembreteSuspenso.get().estado());

        // Tarefa preserva a preferência
        Tarefa tRecuperada = tarefaRepository.buscar(usuarioId, tarefaId).get();
        assertEquals(30, tRecuperada.lembreteMinutosAntes());
        assertFalse(tRecuperada.usaPadraoGlobalLembrete());

        // Volta a definir horário
        Tarefa comHorario = new Tarefa(
                tRecuperada.id(),
                tRecuperada.usuarioId(),
                tRecuperada.listaId(),
                null,
                null,
                false,
                tRecuperada.titulo(),
                tRecuperada.descricao(),
                tRecuperada.prioridade(),
                tRecuperada.dataVencimento(),
                "15:00",
                tRecuperada.estado(),
                tRecuperada.concluida(),
                tRecuperada.concluidaEm(),
                tRecuperada.ordem(),
                tRecuperada.subtarefas(),
                tRecuperada.lembreteMinutosAntes(),
                tRecuperada.usaPadraoGlobalLembrete(),
                tRecuperada.fusoAgendamento(),
                tRecuperada.deletedAt(),
                tRecuperada.expiresAt(),
                tRecuperada.loteExclusaoId(),
                tRecuperada.criadoEm(),
                Instant.now()
        );
        tarefaRepository.atualizar(comHorario);
        lembretePushService.agendarOuAtualizarLembrete(comHorario, null, null, null);

        var lembreteReagendado = lembretePushRepository.buscarPorTarefa(usuarioId, tarefaId);
        assertTrue(lembreteReagendado.isPresent());
        assertEquals(LembretePush.ESTADO_PENDENTE, lembreteReagendado.get().estado());
        assertEquals(30, lembreteReagendado.get().minutosAntes());
    }

    @Test
    @DisplayName("Atualização de herdeiras: mudar padrão global atualiza apenas tarefas que usam o padrão, preservando overrides locais")
    void deveAtualizarApenasTarefasHerdeirasAoMudarPadraoGlobal() {
        // Tarefa 1: usa padrao global (herdeira)
        CriarTarefaRequest reqHerdeira = new CriarTarefaRequest(
                null,
                "Tarefa Herdeira",
                null,
                "MEDIA",
                "2026-06-15",
                "10:00",
                null,
                15,
                true, // usaPadraoGlobalLembrete = true
                "America/Sao_Paulo",
                1
        );
        TarefaResponse herdeira = tarefaService.criarTarefa(reqHerdeira, usuarioIdStr);
        ObjectId herdeiraId = new ObjectId(herdeira.id());

        // Tarefa 2: override local explícito (não-herdeira)
        CriarTarefaRequest reqOverride = new CriarTarefaRequest(
                null,
                "Tarefa Com Override Local",
                null,
                "ALTA",
                "2026-06-15",
                "11:00",
                null,
                5, // 5 min antes explicitamente
                false, // usaPadraoGlobalLembrete = false
                "America/Sao_Paulo",
                2
        );
        TarefaResponse override = tarefaService.criarTarefa(reqOverride, usuarioIdStr);
        ObjectId overrideId = new ObjectId(override.id());

        // Altera preferência global do usuário para 30 minutos antes
        long recalculados = lembretePushService.atualizarPreferenciaGlobal(usuarioId, 30);
        assertTrue(recalculados >= 1);

        // Herdeira deve ter sido recalculada para 30 minutos antes
        var lembreteHerdeira = lembretePushRepository.buscarPorTarefa(usuarioId, herdeiraId).get();
        assertEquals(30, lembreteHerdeira.minutosAntes());
        // 10:00 local (UTC-3) = 13:00 UTC. 30 min antes = 12:30 UTC = 2026-06-15T12:30:00Z
        assertEquals(Instant.parse("2026-06-15T12:30:00Z"), lembreteHerdeira.instanteEntregaUtc());

        // Tarefa com override NÃO deve ter sido alterada
        var lembreteOverride = lembretePushRepository.buscarPorTarefa(usuarioId, overrideId).get();
        assertEquals(5, lembreteOverride.minutosAntes());
        // 11:00 local (UTC-3) = 14:00 UTC. 5 min antes = 13:55 UTC = 2026-06-15T13:55:00Z
        assertEquals(Instant.parse("2026-06-15T13:55:00Z"), lembreteOverride.instanteEntregaUtc());
    }
}
