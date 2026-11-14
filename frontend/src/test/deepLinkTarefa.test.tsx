import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import {
  ToastProvider,
  ConfirmProvider,
  GlobalSessionModalProvider,
  GlobalSessionModal
} from '../shared/design-system';
import { AuthContext, AuthContextValue } from '../shared/auth/AuthContext';
import { StudyTimerProvider } from '../shared/timers/estudoTimer';
import * as apiModule from '../shared/auth/api';
import { ApiError } from '../shared/auth/api';
import TarefasApp from '../modules/tarefas/TarefasApp';

vi.mock('../shared/auth/api', async (importOriginal) => {
  const actual = await importOriginal<typeof apiModule>();
  return { ...actual, request: vi.fn() };
});

import { request } from '../shared/auth/api';

const USUARIO = '6af20000000000000000000a';
const TAREFA_ID = '6af2000000000000000000b1';

function authValue(): AuthContextValue {
  return {
    usuario: {
      id: USUARIO,
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
    },
    isAuthenticated: true,
    carregando: false,
    isAuthModalOpen: false,
    abrirAuthModal: vi.fn(),
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

function montar() {
  return render(
    <ToastProvider>
      <ConfirmProvider>
        <AuthContext.Provider value={authValue()}>
          <StudyTimerProvider>
            <GlobalSessionModalProvider>
              <TarefasApp />
              <GlobalSessionModal />
            </GlobalSessionModalProvider>
          </StudyTimerProvider>
        </AuthContext.Provider>
      </ConfirmProvider>
    </ToastProvider>
  );
}

describe('deep link ?tarefaId=', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.history.replaceState(null, '', `/?tarefaId=${TAREFA_ID}`);
    vi.mocked(request).mockImplementation(async (caminho: string) => {
      if (caminho === '/tarefas') {
        return { itens: [], pagina: 1, tamanho: 50, totalItens: 0, totalPaginas: 1 };
      }
      if (caminho === '/listas-tarefas' || caminho === '/tarefas/listas') {
        return [];
      }
      if (caminho === '/trilhas') {
        return [];
      }
      return {};
    });
  });

  afterEach(() => {
    window.history.replaceState(null, '', '/');
    vi.restoreAllMocks();
  });

  it('abre o TaskModal da tarefa referenciada e limpa o parâmetro', async () => {
    let busca = 0;
    vi.mocked(request).mockImplementation(async (caminho: string) => {
      if (caminho === `/tarefas/${TAREFA_ID}`) {
        busca++;
        return {
          id: TAREFA_ID,
          titulo: 'Revisar constitucional',
          prioridade: 'ALTA',
          concluida: false,
          estado: 'PENDENTE',
          dataVencimento: '2026-12-01',
          horario: '09:00',
          ordem: 1,
          modoLembrete: 'HERDAR'
        };
      }
      if (caminho === '/tarefas') {
        return { itens: [], pagina: 1, tamanho: 50, totalItens: 0, totalPaginas: 1 };
      }
      return [];
    });

    montar();

    await waitFor(() => {
      expect(screen.getByDisplayValue('Revisar constitucional')).toBeInTheDocument();
    });
    expect(busca).toBe(1);
    expect(window.location.search).not.toContain('tarefaId=');
  });

  it('404 exibe o toast normativo e limpa o parâmetro', async () => {
    vi.mocked(request).mockImplementation(async (caminho: string) => {
      if (caminho === `/tarefas/${TAREFA_ID}`) {
        throw new ApiError({ status: 404, code: 'NOT_FOUND', detail: 'Tarefa não encontrada.' });
      }
      if (caminho === '/tarefas') {
        return { itens: [], pagina: 1, tamanho: 50, totalItens: 0, totalPaginas: 1 };
      }
      return [];
    });

    montar();

    await waitFor(() => {
      expect(screen.getByText('Tarefa não encontrada ou excluída')).toBeInTheDocument();
    });
    expect(window.location.search).not.toContain('tarefaId=');
  });

  it('sem ?tarefaId= nenhuma busca individual é feita', async () => {
    window.history.replaceState(null, '', '/');
    montar();

    await waitFor(() => {
      expect(request).toHaveBeenCalled();
    });
    const chamadas = vi.mocked(request).mock.calls.map(([caminho]) => String(caminho));
    expect(chamadas.some((c) => c.includes('/tarefas/6af2'))).toBe(false);
  });
});

