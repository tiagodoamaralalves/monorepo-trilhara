import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import {
  ToastProvider,
  ConfirmProvider,
  GlobalSessionModalProvider,
  GlobalSessionModal
} from '../shared/design-system';
import { AuthContext, AuthContextValue } from '../shared/auth/AuthContext';
import { StudyTimerProvider } from '../shared/timers/estudoTimer';
import * as apiModule from '../shared/auth/api';
import PomodoroApp, { getPomodoroFocoStorageKey } from '../modules/pomodoro/PomodoroApp';
import { TarefaService } from '../modules/tarefas/services/tarefaService';

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

  it('visitanteVeCardEEntrarNaContaAbreAuthModal', () => {
    vi.mocked(apiModule.request).mockImplementation(async () => []);

    montar(null);

    expect(screen.getByRole('timer')).toBeInTheDocument();
    expect(screen.getByText('Entre para ver suas tarefas de hoje')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /entrar na conta/i }));
    expect(abrirAuthModalSpy).toHaveBeenCalledTimes(1);
  });

  it('focoAtualPersisteSomenteIdETipoNaChaveDaConta', async () => {
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

    fireEvent.click(screen.getByRole('button', { name: 'Resolver 20 questões de RLM' }));

    await waitFor(() => {
      expect(screen.getByText('Foco Selecionado')).toBeInTheDocument();
    });

    const chave = getPomodoroFocoStorageKey(USUARIO)!;
    expect(chave).toBe(`trilhara:u:${USUARIO}:pomodoro_foco`);
    expect(JSON.parse(localStorage.getItem(chave)!)).toEqual({
      tipo: 'TAREFA',
      id: 'tar-manual-1'
    });
  });

  it('focoEhDescartadoQuandoOItemSomeDaAgenda', async () => {
    let incluirTarefa = true;
    vi.mocked(apiModule.request).mockImplementation(async (caminho: string) => {
      if (caminho === '/tarefas/hoje') {
        return {
          hojeLocal: '2026-11-09',
          atrasadas: [],
          hoje: incluirTarefa ? [tarefaHoje] : [],
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
    fireEvent.click(screen.getByRole('button', { name: 'Resolver 20 questões de RLM' }));
    await waitFor(() => expect(screen.getByText('Foco Selecionado')).toBeInTheDocument());

    incluirTarefa = false;
    fireEvent.click(screen.getByRole('button', { name: /atualizar agenda/i }));

    await waitFor(() => {
      expect(screen.queryByText('Foco Selecionado')).not.toBeInTheDocument();
    });
    expect(localStorage.getItem(getPomodoroFocoStorageKey(USUARIO)!)).toBeNull();
  });
});
