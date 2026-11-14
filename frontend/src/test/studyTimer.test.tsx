import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  StudyTimerProvider,
  useStudyTimer,
  getStudyTimerStorageKey
} from '../shared/timers/estudoTimer';
import { AuthContext, AuthContextValue } from '../shared/auth/AuthContext';
import { ConfirmProvider } from '../shared/design-system/ConfirmDialog/ConfirmDialog';
import {
  definirUsuarioCorrente,
  __receberParaTeste,
  __resetarParaTeste
} from '../shared/sync/barramento';

const mockAuth = (usuarioId: string | null): AuthContextValue => ({
  usuario: usuarioId
    ? {
        id: usuarioId,
        nome: `User ${usuarioId}`,
        email: `${usuarioId}@test.com`,
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
});

const Sonda: React.FC = () => {
  const timer = useStudyTimer();
  return (
    <div>
      <span data-testid="segundos">{timer.seconds}</span>
      <span data-testid="rodando">{timer.running ? 'SIM' : 'NAO'}</span>
      <span data-testid="ativo">{timer.ativo ? 'SIM' : 'NAO'}</span>
      <button data-testid="iniciar" onClick={() => void timer.start()}>
        iniciar
      </button>
      <button data-testid="pausar" onClick={() => timer.pause()}>
        pausar
      </button>
      <button data-testid="resetar" onClick={() => timer.reset()}>
        resetar
      </button>
    </div>
  );
};

function montar(usuarioId: string | null) {
  return render(
    <AuthContext.Provider value={mockAuth(usuarioId)}>
      <ConfirmProvider>
        <StudyTimerProvider>
          <Sonda />
        </StudyTimerProvider>
      </ConfirmProvider>
    </AuthContext.Provider>
  );
}

describe('studyTimer — cronômetro de Estudos exige autenticação', () => {
  beforeEach(() => {
    localStorage.clear();
    __resetarParaTeste();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    localStorage.clear();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('sem usuário fica inativo, ignora o início e não grava nenhuma chave', async () => {
    const user = userEvent.setup();
    montar(null);

    expect(screen.getByTestId('ativo')).toHaveTextContent('NAO');

    await user.click(screen.getByTestId('iniciar'));

    expect(screen.getByTestId('rodando')).toHaveTextContent('NAO');
    expect(Object.keys(localStorage)).toEqual([]);
  });

  it('isola as chaves de Usuário A e Usuário B', () => {
    expect(getStudyTimerStorageKey('user-A')).toBe('trilhara:u:user-A:study_timer');
    expect(getStudyTimerStorageKey('user-B')).toBe('trilhara:u:user-B:study_timer');
    expect(getStudyTimerStorageKey(null)).toBeNull();

    localStorage.setItem(
      'trilhara:u:user-A:study_timer',
      JSON.stringify({ accumulatedElapsedMs: 150_000, runningSince: null, updatedAt: Date.now() })
    );

    montar('user-B');
    expect(screen.getByTestId('segundos')).toHaveTextContent('0');
  });

  it('logout pausa, acumula o tempo e persiste na chave da conta', async () => {
    const user = userEvent.setup();
    const base = 1_700_000_000_000;
    vi.spyOn(Date, 'now').mockReturnValue(base);

    montar('user-alpha');
    await user.click(screen.getByTestId('iniciar'));
    expect(screen.getByTestId('rodando')).toHaveTextContent('SIM');

    vi.spyOn(Date, 'now').mockReturnValue(base + 5_000);
    act(() => {
      window.dispatchEvent(
        new CustomEvent('trilhara:logout', { detail: { usuarioId: 'user-alpha' } })
      );
    });

    expect(screen.getByTestId('rodando')).toHaveTextContent('NAO');
    const salvo = JSON.parse(localStorage.getItem('trilhara:u:user-alpha:study_timer') ?? '{}');
    expect(salvo.runningSince).toBeNull();
    expect(salvo.accumulatedElapsedMs).toBeGreaterThanOrEqual(5_000);
  });

  it('ignora mensagem do barramento com usuarioId diferente', async () => {
    definirUsuarioCorrente('user-A');
    montar('user-A');

    act(() => {
      __receberParaTeste({
        v: 1,
        tipo: 'TIMER_ESTUDOS_ATUALIZADO',
        usuarioId: 'user-B',
        origemAbaId: 'outra-aba',
        payload: { accumulatedElapsedMs: 900_000, runningSince: null, updatedAt: Date.now() }
      });
    });

    expect(screen.getByTestId('segundos')).toHaveTextContent('0');
  });

  it('aplica mensagem do barramento do mesmo usuário', async () => {
    montar('user-A');

    act(() => {
      __receberParaTeste({
        v: 1,
        tipo: 'TIMER_ESTUDOS_ATUALIZADO',
        usuarioId: 'user-A',
        origemAbaId: 'outra-aba',
        payload: {
          trilhaId: 'trilha-1',
          disciplinaId: 'disc-1',
          mode: 'progressivo',
          accumulatedElapsedMs: 300_000,
          runningSince: null,
          startedAt: new Date().toISOString(),
          breakType: null,
          breakEndsAt: null,
          pausas: { short: 5, long: 15 },
          updatedAt: Date.now()
        }
      });
    });

    await waitFor(() => expect(screen.getByTestId('segundos')).toHaveTextContent('300'));
  });

  it('o alarme não abre modal de sessão', async () => {
    const base = 1_700_000_000_000;
    vi.spyOn(Date, 'now').mockReturnValue(base);

    montar('user-alarme');
    await act(async () => {
      screen.getByTestId('iniciar').click();
    });


    // Nenhum diálogo de registro é montado pelo provider do cronômetro.
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('mantém drift ≤ 1 s após 10 min "suspenso" (timestamp absoluto)', async () => {
    const base = 1_700_000_000_000;
    vi.spyOn(Date, 'now').mockReturnValue(base);

    montar('user-drift');
    await act(async () => {
      screen.getByTestId('iniciar').click();
    });

    vi.spyOn(Date, 'now').mockReturnValue(base + 600_000);
    act(() => {
      screen.getByTestId('pausar').click();
    });

    await waitFor(() => {
      const segundos = Number(screen.getByTestId('segundos').textContent);
      expect(Math.abs(segundos - 600)).toBeLessThanOrEqual(1);
    });
  });

  it('reset remove a chave do usuário', async () => {
    const user = userEvent.setup();
    montar('user-reset');
    await user.click(screen.getByTestId('iniciar'));

    expect(localStorage.getItem('trilhara:u:user-reset:study_timer')).not.toBeNull();

    await user.click(screen.getByTestId('resetar'));

    expect(localStorage.getItem('trilhara:u:user-reset:study_timer')).toBeNull();
    expect(screen.getByTestId('segundos')).toHaveTextContent('0');
  });
});
