import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  GlobalSessionModalProvider,
  useGlobalSessionModal,
  GlobalSessionModal
} from '../shared/design-system/GlobalSessionModal';
import { ToastProvider } from '../shared/design-system/Toast/Toast';
import { ConfirmProvider } from '../shared/design-system/ConfirmDialog/ConfirmDialog';
import { AuthContext, AuthContextValue } from '../shared/auth/AuthContext';
import { StudyTimerProvider, useStudyTimer } from '../shared/timers/estudoTimer';
import { ApiError } from '../shared/auth/api';
import * as apiModule from '../shared/auth/api';
import * as barramento from '../shared/sync/barramento';
import type { GlobalSessionModalOpenParams } from '../shared/contracts/domainContracts';

vi.mock('../shared/auth/api', async (importOriginal) => {
  const actual = await importOriginal<typeof apiModule>();
  return { ...actual, request: vi.fn() };
});

const requestMock = vi.mocked(apiModule.request);

const authValue = (usuarioId: string | null): AuthContextValue => ({
  usuario: usuarioId
    ? {
        id: usuarioId,
        nome: 'Aluno',
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

/** Botões que abrem o modal nos 4 modos + leitura do cronômetro. */
const Abertura: React.FC = () => {
  const { abrirModal } = useGlobalSessionModal();
  const timer = useStudyTimer();
  const abrir = (params: GlobalSessionModalOpenParams) => (e: React.MouseEvent) =>
    abrirModal({ ...params, triggerElement: e.currentTarget as HTMLElement });

  return (
    <div>
      <span data-testid="timer-segundos">{timer.seconds}</span>
      <span data-testid="timer-rodando">{timer.running ? 'SIM' : 'NAO'}</span>
      <button
        data-testid="abrir-estudos-timer"
        onClick={abrir({
          modo: 'ESTUDOS_TIMER',
          trilhaId: '6af2000000000000000000a1',
          disciplinaId: '6af2000000000000000000d1',
          duracaoInicialSegundos: 1537,
          trilhaNome: 'Receita Federal',
          disciplinaNome: 'Constitucional'
        })}
      >
        abrir estudos timer
      </button>
      <button
        data-testid="abrir-bloco-direto"
        onClick={abrir({
          modo: 'BLOCO_DIRETO',
          trilhaId: '6af2000000000000000000a1',
          disciplinaId: '6af2000000000000000000d1',
          blocoId: '6af2000000000000000000b1',
          duracaoInicialSegundos: 1500,
          trilhaNome: 'Receita Federal',
          disciplinaNome: 'Constitucional'
        })}
      >
        abrir bloco direto
      </button>
      <button
        data-testid="abrir-bloco-timer"
        onClick={abrir({
          modo: 'BLOCO_TIMER',
          trilhaId: '6af2000000000000000000a1',
          disciplinaId: '6af2000000000000000000d1',
          blocoId: '6af2000000000000000000b1',
          duracaoInicialSegundos: 900
        })}
      >
        abrir bloco timer
      </button>
      <button data-testid="abrir-manual" onClick={abrir({ modo: 'MANUAL' })}>
        abrir manual
      </button>
    </div>
  );
};

function montar(usuarioId: string | null = 'user-modal') {
  return render(
    <ToastProvider>
      <ConfirmProvider>
        <AuthContext.Provider value={authValue(usuarioId)}>
          <StudyTimerProvider>
            <GlobalSessionModalProvider>
              <Abertura />
              <GlobalSessionModal />
            </GlobalSessionModalProvider>
          </StudyTimerProvider>
        </AuthContext.Provider>
      </ConfirmProvider>
    </ToastProvider>
  );
}

describe('globalSessionModal (R6/D-22)', () => {
  beforeEach(() => {
    localStorage.clear();
    requestMock.mockReset();
    requestMock.mockResolvedValue([]);
    barramento.__resetarParaTeste();
    vi.restoreAllMocks();
    barramento.definirUsuarioCorrente('user-modal');
  });

  afterEach(() => {
    localStorage.clear();
    requestMock.mockReset();
    vi.restoreAllMocks();
  });

  it('abre em ESTUDOS_TIMER com título, tempo medido e rótulo fixo', async () => {
    const user = userEvent.setup();
    montar();

    await user.click(screen.getByTestId('abrir-estudos-timer'));

    expect(screen.getByRole('dialog', { name: /Registrar Sessão de Estudo/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Salvar sessão$/i })).toBeInTheDocument();
    expect(screen.getByTestId('tempo-medido')).toHaveTextContent('Tempo medido: 25 min 37 s');
    expect(screen.getByLabelText('Horas')).toHaveAttribute('inputmode', 'numeric');
  });

  it('abre em BLOCO_DIRETO com o botão de concluir sem sessão', async () => {
    const user = userEvent.setup();
    montar();

    await user.click(screen.getByTestId('abrir-bloco-direto'));

    expect(screen.getByRole('dialog', { name: /Concluir Bloco de Cronograma/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Concluir sem salvar sessão/i })).toBeInTheDocument();
  });

  it('abre em BLOCO_TIMER e MANUAL com os títulos esperados', async () => {
    const user = userEvent.setup();
    montar();

    await user.click(screen.getByTestId('abrir-bloco-timer'));
    expect(screen.getByRole('dialog', { name: /Concluir Bloco de Estudo/i })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /^Cancelar$/i }));

    await user.click(screen.getByTestId('abrir-manual'));
    expect(screen.getByRole('dialog', { name: /Registrar Sessão Manual/i })).toBeInTheDocument();
  });

  it('"Concluir sem salvar sessão" aparece apenas em modos com bloco', async () => {
    const user = userEvent.setup();
    montar();

    await user.click(screen.getByTestId('abrir-estudos-timer'));
    expect(screen.queryByRole('button', { name: /Concluir sem salvar sessão/i })).toBeNull();
    await user.click(screen.getByRole('button', { name: /^Cancelar$/i }));

    await user.click(screen.getByTestId('abrir-bloco-timer'));
    expect(screen.getByRole('button', { name: /Concluir sem salvar sessão/i })).toBeInTheDocument();
  });

  it('cancelar mantém o cronômetro pausado com o tempo acumulado', async () => {
    const user = userEvent.setup();
    const base = 1_700_000_000_000;
    const chave = 'trilhara:u:user-modal:study_timer';
    localStorage.setItem(
      chave,
      JSON.stringify({
        trilhaId: 'trilha-1',
        disciplinaId: 'disc-1',
        mode: 'progressivo',
        accumulatedElapsedMs: 600_000,
        runningSince: null,
        startedAt: new Date(base).toISOString(),
        breakType: null,
        breakEndsAt: null,
        pausas: { short: 5, long: 15 },
        updatedAt: base
      })
    );
    vi.spyOn(Date, 'now').mockReturnValue(base + 60_000);

    montar();
    expect(screen.getByTestId('timer-segundos')).toHaveTextContent('600');

    await user.click(screen.getByTestId('abrir-estudos-timer'));
    await user.click(screen.getByRole('button', { name: /^Cancelar$/i }));

    expect(screen.getByTestId('timer-rodando')).toHaveTextContent('NAO');
    // O tempo acumulado não é perdido ao cancelar (D-22.1).
    expect(screen.getByTestId('timer-segundos')).toHaveTextContent('600');
    const salvo = JSON.parse(localStorage.getItem(chave) ?? '{}');
    expect(salvo.runningSince).toBeNull();
    expect(salvo.accumulatedElapsedMs).toBe(600_000);
  });

  it('fechar com campos alterados pede confirmação de descarte', async () => {
    const user = userEvent.setup();
    montar();

    await user.click(screen.getByTestId('abrir-estudos-timer'));
    await user.type(screen.getByLabelText('Minutos'), '5');
    await user.click(screen.getByRole('button', { name: /^Cancelar$/i }));

    const dialogo = await screen.findByRole('dialog', { name: /Descartar registro\?/i });
    expect(
      within(dialogo).getByRole('button', { name: /Continuar editando/i })
    ).toBeInTheDocument();
    expect(within(dialogo).getByRole('button', { name: /^Descartar$/i })).toBeInTheDocument();
  });

  it('40 s exibe erro inline de duração mínima', async () => {
    const user = userEvent.setup();
    montar();

    await user.click(screen.getByTestId('abrir-manual'));
    const horas = screen.getByLabelText('Horas');
    await user.clear(horas);
    await user.type(horas, '0');
    const minutos = screen.getByLabelText('Minutos');
    await user.clear(minutos);
    await user.type(minutos, '0');

    await user.click(screen.getByRole('button', { name: /^Salvar sessão$/i }));

    expect(
      await screen.findByText(/A duração mínima para registrar uma sessão é de 1 minuto/)
    ).toBeInTheDocument();
  });

  it('acertos maiores que questões exibem erro inline', async () => {
    const user = userEvent.setup();
    montar();

    await user.click(screen.getByTestId('abrir-estudos-timer'));
    await user.type(screen.getByLabelText(/Questões Feitas/i), '10');
    await user.type(screen.getByLabelText(/Acertos/i), '11');

    await user.click(screen.getByRole('button', { name: /^Salvar sessão$/i }));

    expect(
      await screen.findByText(/O número de acertos não pode ser maior que o total de questões/i)
    ).toBeInTheDocument();
  });

  it('offline mantém os campos preenchidos e exibe banner de conexão', async () => {
    const user = userEvent.setup();
    requestMock.mockReset();
    requestMock.mockRejectedValue(
      new ApiError({ status: 0, code: 'NETWORK_ERROR', detail: 'Falha de rede' })
    );

    montar();
    await user.click(screen.getByTestId('abrir-estudos-timer'));
    await user.type(screen.getByLabelText(/Questões Feitas/i), '20');

    await user.click(screen.getByRole('button', { name: /^Salvar sessão$/i }));

    expect(
      await screen.findByText(/Sem conexão\. Seus dados foram mantidos\./i)
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/Questões Feitas/i)).toHaveValue(20);
  });

  it('salvar com sucesso zera o timer e remove a chave', async () => {
    const user = userEvent.setup();
    const chave = 'trilhara:u:user-modal:study_timer';
    localStorage.setItem(
      chave,
      JSON.stringify({
        trilhaId: 'trilha-1',
        disciplinaId: 'disc-1',
        mode: 'progressivo',
        accumulatedElapsedMs: 600_000,
        runningSince: null,
        startedAt: new Date().toISOString(),
        breakType: null,
        breakEndsAt: null,
        pausas: { short: 5, long: 15 },
        updatedAt: Date.now()
      })
    );
    requestMock.mockReset();
    requestMock.mockResolvedValue({ id: 'sess-nova' });

    montar();
    await user.click(screen.getByTestId('abrir-estudos-timer'));
    await user.click(screen.getByRole('button', { name: /^Salvar sessão$/i }));

    await waitFor(() => expect(localStorage.getItem(chave)).toBeNull());
    expect(screen.getByTestId('timer-segundos')).toHaveTextContent('0');
  });
});
