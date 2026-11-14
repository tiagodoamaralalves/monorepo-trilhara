import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { act, render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  ToastProvider,
  ConfirmProvider,
  GlobalSessionModalProvider,
  GlobalSessionModal
} from '../shared/design-system';
import { AuthContext, AuthContextValue } from '../shared/auth/AuthContext';
import { StudyTimerProvider } from '../shared/timers/estudoTimer';
import * as apiModule from '../shared/auth/api';
import PomodoroApp, {
  getPomodoroTimerStorageKey
} from '../modules/pomodoro/PomodoroApp';
import {
  POMODORO_LOCAL_TASKS_STORAGE_KEY,
  getPomodoroAgendaExpandidaStorageKey
} from '../modules/pomodoro/tarefasLocaisStorage';
import { TarefaService } from '../modules/tarefas/services/tarefaService';
import { agendaHojeService } from '../shared/agenda/agendaHojeService';
import { hojeISO } from '../shared/format';

vi.mock('../shared/auth/api', async (importOriginal) => {
  const actual = await importOriginal<typeof apiModule>();
  return { ...actual, request: vi.fn() };
});

const USUARIO = '6af20000000000000000000a';
const abrirAuthModalSpy = vi.fn();

function authValue(usuarioId: string | null): AuthContextValue {
  return {
    usuario: usuarioId
      ? {
          id: usuarioId,
          nome: 'Aluno Teste',
          email: 'aluno@trilhara.com.br',
          emailVerificado: true,
          ativo: true,
          criadoEm: '2026-11-09T00:00:00Z',
          papeis: ['USER'],
          preferencias: {
            tema: 'dark',
            disponibilidadeSemanalMinutos: [0, 0, 0, 0, 0, 0, 0],
            padraoLembreteMinutosAntes: 15
          }
        }
      : null,
    isAuthenticated: usuarioId !== null,
    carregando: false,
    isAuthModalOpen: false,
    abrirAuthModal: abrirAuthModalSpy,
    fecharAuthModal: vi.fn(),
    isContaModalOpen: false,
    abrirContaModal: vi.fn(),
    fecharContaModal: vi.fn(),
    excluirConta: vi.fn(),
    login: vi.fn(),
    cadastrar: vi.fn(),
    logout: vi.fn(),
    verificarSessao: vi.fn(),
    setUsuario: vi.fn()
  };
}

function montar(usuarioId: string | null) {
  return render(
    <ToastProvider>
      <ConfirmProvider>
        <AuthContext.Provider value={authValue(usuarioId)}>
          <StudyTimerProvider>
            <GlobalSessionModalProvider>
              <PomodoroApp />
              <GlobalSessionModal />
            </GlobalSessionModalProvider>
          </StudyTimerProvider>
        </AuthContext.Provider>
      </ConfirmProvider>
    </ToastProvider>
  );
}

const tarefaHoje = {
  id: 'tar-manual-1',
  titulo: 'Resolver 20 questões de RLM',
  dataVencimento: '2026-11-09',
  prioridade: 'MEDIA' as const,
  concluida: false
};

describe('pomodoroAgenda', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    agendaHojeService.resetarParaTestes();
  });

  afterEach(() => {
    localStorage.clear();
    vi.useRealTimers();
  });

  it('concluirTarefaNaoPausaOTimerNemAbreModal', async () => {
    const spyAlternar = vi
      .spyOn(TarefaService, 'alternarConclusao')
      .mockResolvedValue({ ...tarefaHoje, concluida: true, criadoEm: '', atualizadoEm: '' });

    vi.mocked(apiModule.request).mockImplementation(async (caminho: string) => {
      if (caminho === '/tarefas/hoje') {
        return { hojeLocal: '2026-11-09', atrasadas: [], hoje: [tarefaHoje], semData: [] };
      }
      if (caminho === '/blocos/hoje') return [];
      return [];
    });

    montar(USUARIO);

    await waitFor(() => {
      expect(screen.getByText('Resolver 20 questões de RLM')).toBeInTheDocument();
    });

    // Inicia o foco e confirma que o timer está rodando.
    fireEvent.click(screen.getByRole('button', { name: /iniciar foco/i }));
    expect(screen.getByText(/foco em andamento/i)).toBeInTheDocument();

    fireEvent.click(
      screen.getByRole('checkbox', { name: /concluir tarefa resolver 20 questões de rlm/i })
    );

    await waitFor(() => {
      expect(spyAlternar).toHaveBeenCalledWith('tar-manual-1', true);
    });

    // O cronômetro continua rodando: concluir tarefa nunca pausa o timer (§8).
    expect(screen.getByText(/foco em andamento/i)).toBeInTheDocument();
    // E nenhum modal de sessão é aberto.
    expect(screen.queryByText(/Registrar Sessão de Estudo/i)).not.toBeInTheDocument();
  });

  it('visitanteVeAvisoDiscretoEEntrarNaContaAbreAuthModal', () => {
    vi.mocked(apiModule.request).mockImplementation(async () => []);

    montar(null);

    expect(screen.getByRole('timer')).toBeInTheDocument();
    expect(
      screen.getByText(/Tarefas salvas apenas neste navegador\. Entre para sincronizar com sua conta\./i)
    ).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText(/Adicionar uma tarefa\.\.\. \(Pressione Enter para salvar\)/i)
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /entrar na conta/i }));
    expect(abrirAuthModalSpy).toHaveBeenCalledTimes(1);
  });

  it('visitanteCriaConcluiEExcluiTarefaLocalNoNavegador', async () => {
    const user = userEvent.setup();
    vi.mocked(apiModule.request).mockImplementation(async () => []);

    montar(null);

    const input = screen.getByPlaceholderText(
      /Adicionar uma tarefa\.\.\. \(Pressione Enter para salvar\)/i
    );
    await user.type(input, 'Ler resumo de Direito Administrativo{Enter}');

    expect(screen.getByText('Ler resumo de Direito Administrativo')).toBeInTheDocument();
    expect(screen.getByText('No navegador')).toBeInTheDocument();

    const salvas = JSON.parse(localStorage.getItem(POMODORO_LOCAL_TASKS_STORAGE_KEY) ?? '[]');
    expect(salvas).toHaveLength(1);
    expect(salvas[0]).toMatchObject({
      titulo: 'Ler resumo de Direito Administrativo',
      concluida: false
    });

    await user.click(
      screen.getByRole('checkbox', {
        name: /concluir tarefa ler resumo de direito administrativo/i
      })
    );
    const aposConcluir = JSON.parse(localStorage.getItem(POMODORO_LOCAL_TASKS_STORAGE_KEY) ?? '[]');
    expect(aposConcluir[0].concluida).toBe(true);

    await user.click(
      screen.getByRole('button', {
        name: /excluir tarefa ler resumo de direito administrativo/i
      })
    );
    expect(screen.queryByText('Ler resumo de Direito Administrativo')).not.toBeInTheDocument();
    expect(localStorage.getItem(POMODORO_LOCAL_TASKS_STORAGE_KEY)).toBeNull();
  });

  it('aoLogarTarefaLocalExibePilulaESalvaNaConta', async () => {
    const user = userEvent.setup();
    const hoje = hojeISO();
    localStorage.setItem(
      POMODORO_LOCAL_TASKS_STORAGE_KEY,
      JSON.stringify([
        {
          id: 'local-1',
          titulo: 'Revisar jurisprudência',
          concluida: false,
          criadaEm: '2026-11-14T12:00:00Z'
        }
      ])
    );

    const spyCriar = vi.spyOn(TarefaService, 'criarTarefa').mockResolvedValue({
      ...tarefaHoje,
      id: 'srv-1',
      titulo: 'Revisar jurisprudência',
      dataVencimento: hoje,
      criadoEm: '',
      atualizadoEm: ''
    });

    let tarefaSalva = false;
    vi.mocked(apiModule.request).mockImplementation(async (caminho: string) => {
      if (caminho === '/tarefas/hoje') {
        return {
          hojeLocal: hoje,
          atrasadas: [],
          hoje: tarefaSalva ? [{ ...tarefaHoje, id: 'srv-1', titulo: 'Revisar jurisprudência' }] : [],
          semData: []
        };
      }
      if (caminho === '/blocos/hoje') return [];
      return [];
    });

    montar(USUARIO);

    expect(screen.getByText('Revisar jurisprudência')).toBeInTheDocument();
    const pilulaSalvar = screen.getByRole('button', { name: /salvar em tarefas/i });

    tarefaSalva = true;
    await user.click(pilulaSalvar);

    await waitFor(() => {
      expect(spyCriar).toHaveBeenCalledWith({
        titulo: 'Revisar jurisprudência',
        dataVencimento: hoje,
        data: hoje,
        prioridade: 'MEDIA'
      });
    });

    expect(localStorage.getItem(POMODORO_LOCAL_TASKS_STORAGE_KEY)).toBeNull();
    await waitFor(() => {
      expect(screen.queryByRole('button', { name: /salvar em tarefas/i })).not.toBeInTheDocument();
    });
  });

  it('cardDeTarefasEhRecolhivelEPersistePreferenciaNoNavegador', async () => {
    const user = userEvent.setup();
    vi.mocked(apiModule.request).mockImplementation(async () => []);

    montar(null);

    const toggle = screen.getByRole('button', { name: /agenda de hoje/i });
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(
      screen.getByPlaceholderText(/Adicionar uma tarefa\.\.\. \(Pressione Enter para salvar\)/i)
    ).toBeInTheDocument();

    await user.click(toggle);

    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(
      screen.queryByPlaceholderText(/Adicionar uma tarefa\.\.\. \(Pressione Enter para salvar\)/i)
    ).not.toBeInTheDocument();
    expect(localStorage.getItem(getPomodoroAgendaExpandidaStorageKey(null))).toBe('false');
  });

  it('botaoDeAlarmeNaoApareceNoTopoEConfiguracaoSalvaNoModal', async () => {
    const user = userEvent.setup();
    vi.mocked(apiModule.request).mockImplementation(async () => []);

    montar(null);

    expect(screen.queryByRole('button', { name: /alarmes ativos|alarmes mudos/i })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /configurar pausas e alarmes/i }));

    const checkboxSom = screen.getByRole('checkbox', { name: /alarme sonoro/i });
    expect(checkboxSom).toBeChecked();

    await user.click(checkboxSom);
    expect(checkboxSom).not.toBeChecked();

    await user.click(screen.getByRole('button', { name: /salvar configurações/i }));

    const salvo = JSON.parse(localStorage.getItem(getPomodoroTimerStorageKey(null)) ?? '{}');
    expect(salvo.somAtivo).toBe(false);
  });

  it('clicarEmTarefaEmPomodoroNaoExibeCardDeFocoSelecionado', async () => {
    vi.mocked(apiModule.request).mockImplementation(async (caminho: string) => {
      if (caminho === '/tarefas/hoje') {
        return { hojeLocal: '2026-11-09', atrasadas: [], hoje: [tarefaHoje], semData: [] };
      }
      if (caminho === '/blocos/hoje') return [];
      return [];
    });

    montar(USUARIO);

    await waitFor(() => {
      expect(screen.getByText('Resolver 20 questões de RLM')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Resolver 20 questões de RLM'));

    expect(screen.queryByText('Foco Selecionado')).not.toBeInTheDocument();
    expect(screen.queryByTitle('Definir como foco')).not.toBeInTheDocument();
  });

  it('criaTarefaRapidaParaHojeEmPomodoro', async () => {
    const user = userEvent.setup();
    const hoje = hojeISO();
    const spyCriar = vi.spyOn(TarefaService, 'criarTarefa').mockResolvedValue({
      ...tarefaHoje,
      id: 'nova-1',
      titulo: 'Revisar Direito Constitucional',
      dataVencimento: hoje,
      criadoEm: '',
      atualizadoEm: ''
    });

    vi.mocked(apiModule.request).mockImplementation(async (caminho: string) => {
      if (caminho === '/tarefas/hoje') {
        return { hojeLocal: hoje, atrasadas: [], hoje: [], semData: [] };
      }
      if (caminho === '/blocos/hoje') return [];
      return [];
    });

    montar(USUARIO);

    const input = await screen.findByPlaceholderText(
      /Adicionar uma tarefa\.\.\. \(Pressione Enter para salvar\)/i
    );
    await user.type(input, 'Revisar Direito Constitucional{Enter}');

    await waitFor(() => {
      expect(spyCriar).toHaveBeenCalledWith({
        titulo: 'Revisar Direito Constitucional',
        dataVencimento: hoje,
        data: hoje,
        prioridade: 'MEDIA'
      });
    });
  });

  it('excluiTarefaEmPomodoro', async () => {
    const user = userEvent.setup();
    const spyExcluir = vi.spyOn(TarefaService, 'excluirTarefa').mockResolvedValue(undefined);

    vi.mocked(apiModule.request).mockImplementation(async (caminho: string) => {
      if (caminho === '/tarefas/hoje') {
        return {
          hojeLocal: '2026-11-09',
          atrasadas: [],
          hoje: [tarefaHoje],
          semData: []
        };
      }
      if (caminho === '/blocos/hoje') return [];
      return [];
    });

    montar(USUARIO);

    await waitFor(() => {
      expect(screen.getByText('Resolver 20 questões de RLM')).toBeInTheDocument();
    });

    await user.click(
      screen.getByRole('button', { name: /excluir tarefa resolver 20 questões de rlm/i })
    );
    await user.click(await screen.findByRole('button', { name: /Mover para Lixeira/i }));

    await waitFor(() => {
      expect(spyExcluir).toHaveBeenCalledWith('tar-manual-1');
    });
  });

  it('botoesDePausaFicamDesabilitadosQuandoCronometroParadoEAtivosQuandoIniciadoOuPausado', () => {
    vi.useFakeTimers();
    vi.mocked(apiModule.request).mockImplementation(async () => []);

    montar(null);

    const btnCurta = screen.getByRole('button', { name: /pausa curta/i });
    const btnLonga = screen.getByRole('button', { name: /pausa longa/i });
    expect(btnCurta).toBeDisabled();
    expect(btnLonga).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: /iniciar foco/i }));
    expect(btnCurta).toBeEnabled();
    expect(btnLonga).toBeEnabled();

    act(() => {
      vi.advanceTimersByTime(2000);
    });

    fireEvent.click(screen.getByRole('button', { name: /pausar/i }));
    expect(btnCurta).toBeEnabled();
    expect(btnLonga).toBeEnabled();

    fireEvent.click(btnCurta);
    expect(btnCurta).toBeDisabled();
    expect(btnLonga).toBeDisabled();
  });

  it('concluirTarefaLocalMoveParaSessaoConcluidasComSeparador', async () => {
    const user = userEvent.setup();
    vi.mocked(apiModule.request).mockImplementation(async () => []);

    montar(null);

    const input = screen.getByPlaceholderText(
      /Adicionar uma tarefa\.\.\. \(Pressione Enter para salvar\)/i
    );
    await user.type(input, 'Ler 20 páginas{Enter}');

    expect(screen.queryByRole('separator', { name: 'Concluídas' })).not.toBeInTheDocument();

    const checkbox = screen.getByRole('checkbox', { name: /concluir tarefa ler 20 páginas/i });
    await user.click(checkbox);

    expect(screen.getByRole('separator', { name: 'Concluídas' })).toBeInTheDocument();
    expect(checkbox).toBeChecked();
  });

  it('agendaAutenticadaExibeTarefasConcluidasHojeAbaixoDeConcluidas', async () => {
    const hoje = hojeISO();
    vi.mocked(apiModule.request).mockImplementation(async (caminho: string) => {
      if (caminho === '/tarefas/hoje') {
        return {
          hojeLocal: hoje,
          atrasadas: [],
          hoje: [],
          semData: [],
          concluidasHoje: [
            {
              ...tarefaHoje,
              id: 'tar-concluida-1',
              titulo: 'Simulado finalizado',
              dataVencimento: hoje,
              concluida: true,
              estado: 'CONCLUIDA',
              prioridade: 'ALTA'
            }
          ]
        };
      }
      if (caminho === '/blocos/hoje') return [];
      return [];
    });

    montar(USUARIO);

    await waitFor(() => {
      expect(screen.getByText('Simulado finalizado')).toBeInTheDocument();
    });
    expect(screen.getByRole('separator', { name: 'Concluídas' })).toBeInTheDocument();
    expect(screen.getByText('Hoje')).toBeInTheDocument();
    expect(screen.getByText('Alta')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /excluir tarefa simulado finalizado/i })
    ).toBeInTheDocument();
  });
});
