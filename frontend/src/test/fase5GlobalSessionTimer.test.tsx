import { act } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { sessaoGlobalService } from '../shared/services/sessaoGlobalService';
import {
  StudyTimerProvider,
  useStudyTimer,
  getStudyTimerStorageKey
} from '../modules/estudos/context/StudyTimerContext';
import {
  GlobalSessionModalProvider,
  useGlobalSessionModal,
  GlobalSessionModal
} from '../shared/design-system/GlobalSessionModal';
import { ToastProvider } from '../shared/design-system/Toast/Toast';
import { AuthContext, AuthContextValue } from '../shared/auth/AuthContext';
import { getPomodoroTimerStorageKey } from '../modules/pomodoro/PomodoroApp';
import * as apiModule from '../shared/auth/api';
import type { SessaoRegistradaDTO } from '../shared/contracts/domainContracts';

const mockAuthValue = (usuarioId: string | null = 'user-1'): AuthContextValue => ({
  usuario: usuarioId
    ? {
        id: usuarioId,
        nome: `User ${usuarioId}`,
        email: `${usuarioId}@test.com`,
        emailVerificado: true,
        ativo: true,
        criadoEm: '2026-11-09T00:00:00Z',
        papeis: ['USER'], preferencias: { tema: 'dark', disponibilidadeSemanalMinutos: [0,0,0,0,0,0,0], padraoLembreteMinutosAntes: 15 }
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

describe('FASE 5 - Frontend Shared & Timers', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  describe('A. sessaoGlobalService', () => {
    it('deve registrar sessão enviando chaveIdempotencia no header Idempotency-Key e no corpo', async () => {
      const requestSpy = vi.spyOn(apiModule, 'request').mockResolvedValueOnce({
        id: 'sess-101',
        trilhaId: 'trilha-1',
        disciplinaId: 'disc-1',
        duracaoSegundos: 120,
        dataSessao: '2026-11-09',
        chaveIdempotencia: 'idemp-uuid-v4'
      });

      const res = await sessaoGlobalService.registrarSessao(
        'trilha-1',
        {
          disciplinaId: 'disc-1',
          dataSessao: '2026-11-09',
          duracaoSegundos: 120,
          questoes: 10,
          acertos: 9,
          chaveIdempotencia: 'idemp-uuid-v4'
        },
        'idemp-uuid-v4'
      );

      expect(requestSpy).toHaveBeenCalledWith('/trilhas/trilha-1/sessoes', {
        method: 'POST',
        headers: {
          'Idempotency-Key': 'idemp-uuid-v4'
        },
        body: JSON.stringify({
          disciplinaId: 'disc-1',
          dataSessao: '2026-11-09',
          duracaoSegundos: 120,
          questoes: 10,
          acertos: 9,
          chaveIdempotencia: 'idemp-uuid-v4'
        })
      });
      expect(res.id).toBe('sess-101');
    });

    it('deve executar fluxo em 2 etapas com sucesso total', async () => {
      const requestSpy = vi
        .spyOn(apiModule, 'request')
        .mockResolvedValueOnce({
          id: 'sess-101',
          trilhaId: 'trilha-1',
          disciplinaId: 'disc-1',
          duracaoSegundos: 1800,
          dataSessao: '2026-11-09'
        }) // Etapa 1: POST /trilhas/{id}/sessoes
        .mockResolvedValueOnce({
          id: 'bloco-55',
          trilhaId: 'trilha-1',
          cronogramaId: 'crono-1',
          concluido: true
        }); // Etapa 2: PATCH /blocos/{id}/conclusao

      const resultado = await sessaoGlobalService.executarFluxoDuasEtapas({
        trilhaId: 'trilha-1',
        blocoId: 'bloco-55',
        payload: {
          disciplinaId: 'disc-1',
          dataSessao: '2026-11-09',
          duracaoSegundos: 1800,
          chaveIdempotencia: 'idemp-uuid-2'
        },
        chaveIdempotencia: 'idemp-uuid-2'
      });

      expect(requestSpy).toHaveBeenCalledTimes(2);
      expect(resultado.sessao.id).toBe('sess-101');
      expect(resultado.bloco.id).toBe('bloco-55');
      expect(resultado.bloco.concluido).toBe(true);
      expect(resultado.sessaoEstavaPreviamenteSalva).toBe(false);
    });

    it('deve suportar retry exclusivo da Etapa 2 quando a sessão já tiver sido salva', async () => {
      const sessaoPrevia: SessaoRegistradaDTO = {
        id: 'sess-salva-1',
        trilhaId: 'trilha-1',
        disciplinaId: 'disc-1',
        duracaoSegundos: 1800,
        dataSessao: '2026-11-09',
        criadoEm: '2026-11-09T12:00:00Z'
      };

      const requestSpy = vi.spyOn(apiModule, 'request').mockResolvedValueOnce({
        id: 'bloco-55',
        concluido: true
      });

      const resultado = await sessaoGlobalService.executarFluxoDuasEtapas({
        trilhaId: 'trilha-1',
        blocoId: 'bloco-55',
        payload: {
          disciplinaId: 'disc-1',
          dataSessao: '2026-11-09',
          duracaoSegundos: 1800
        },
        sessaoJaSalva: sessaoPrevia
      });

      // Apenas a Etapa 2 (PATCH /blocos/bloco-55/conclusao) deve ser chamada!
      expect(requestSpy).toHaveBeenCalledTimes(1);
      expect(requestSpy).toHaveBeenCalledWith('/blocos/bloco-55/conclusao', {
        method: 'PATCH',
        body: JSON.stringify({ concluido: true })
      });
      expect(resultado.sessao.id).toBe('sess-salva-1');
      expect(resultado.sessaoEstavaPreviamenteSalva).toBe(true);
    });

    it('deve permitir concluir bloco diretamente sem salvar sessão', async () => {
      const requestSpy = vi.spyOn(apiModule, 'request').mockResolvedValueOnce({
        id: 'bloco-88',
        concluido: true
      });

      const bloco = await sessaoGlobalService.concluirBloco('bloco-88', true);
      expect(requestSpy).toHaveBeenCalledWith('/blocos/bloco-88/conclusao', {
        method: 'PATCH',
        body: JSON.stringify({ concluido: true })
      });
      expect(bloco.concluido).toBe(true);
    });
  });

  describe('B. StudyTimerContext e Isolamento de Timers por Conta', () => {
    const TestTimerComponent: React.FC<{ timerId?: string }> = ({ timerId = 'trilha-1' }) => {
      const timer = useStudyTimer(timerId);

      return (
        <div>
          <div data-testid="seconds">{timer.seconds}</div>
          <div data-testid="running">{timer.running ? 'RUNNING' : 'PAUSED'}</div>
          <div data-testid="trilha-id">{timer.trilhaId}</div>
          <button data-testid="start-btn" onClick={() => timer.start()}>
            Start
          </button>
          <button data-testid="pause-btn" onClick={() => timer.pause()}>
            Pause
          </button>
          <button data-testid="reset-btn" onClick={() => timer.reset()}>
            Reset
          </button>
        </div>
      );
    };

    it('deve isolar chaves em localStorage entre Usuário A e Usuário B', () => {
      const keyUserA = getStudyTimerStorageKey('user-A');
      const keyUserB = getStudyTimerStorageKey('user-B');

      expect(keyUserA).toBe('trilhara:u:user-A:study_timer');
      expect(keyUserB).toBe('trilhara:u:user-B:study_timer');

      localStorage.setItem(
        keyUserA,
        JSON.stringify({
          trilhaId: 'trilha-A',
          accumulatedElapsedMs: 120_000,
          runningSince: null,
          updatedAt: Date.now()
        })
      );

      // Usuário B não possui dados no storage dele
      expect(localStorage.getItem(keyUserB)).toBeNull();

      // Renderiza com contexto do Usuário B
      render(
        <AuthContext.Provider value={mockAuthValue('user-B')}>
          <StudyTimerProvider>
            <TestTimerComponent timerId="trilha-B" />
          </StudyTimerProvider>
        </AuthContext.Provider>
      );

      expect(screen.getByTestId('seconds')).toHaveTextContent('0');
      expect(screen.getByTestId('running')).toHaveTextContent('PAUSED');
    });

    it('deve pausar automaticamente no logout e acumular tempo decorrido até aquele instante', () => {
      const userA = 'user-alpha';
      const keyUserA = getStudyTimerStorageKey(userA);

      render(
        <AuthContext.Provider value={mockAuthValue(userA)}>
          <StudyTimerProvider>
            <TestTimerComponent timerId="trilha-alpha" />
          </StudyTimerProvider>
        </AuthContext.Provider>
      );

      // Inicia o timer
      act(() => {
        screen.getByTestId('start-btn').click();
      });

      expect(screen.getByTestId('running')).toHaveTextContent('RUNNING');

      // Simula passagem de 5 segundos no relógio
      const agora = Date.now();
      vi.spyOn(Date, 'now').mockReturnValue(agora + 5000);

      // Dispara evento de logout
      act(() => {
        window.dispatchEvent(
          new CustomEvent('trilhara:auth:logout', { detail: { usuarioId: userA } })
        );
      });

      // Timer na memória foi limpo/resetado
      expect(screen.getByTestId('running')).toHaveTextContent('PAUSED');

      // Em localStorage, a chave do usuário foi gravada com runningSince = null e tempo acumulado >= 5000ms
      const salvo = JSON.parse(localStorage.getItem(keyUserA) || '{}');
      expect(salvo.runningSince).toBeNull();
      expect(salvo.accumulatedElapsedMs).toBeGreaterThanOrEqual(5000);
    });

    it('deve restaurar o timer pausado ao fazer login novamente com a mesma conta', () => {
      const userA = 'user-retorno';
      const keyUserA = getStudyTimerStorageKey(userA);

      // Pré-popula storage do Usuário A com 150 segundos
      localStorage.setItem(
        keyUserA,
        JSON.stringify({
          trilhaId: 'trilha-retorno',
          disciplinaId: 'disc-retorno',
          mode: 'progressivo',
          accumulatedElapsedMs: 150_000,
          runningSince: null,
          startedAt: new Date().toISOString(),
          updatedAt: Date.now()
        })
      );

      render(
        <AuthContext.Provider value={mockAuthValue(userA)}>
          <StudyTimerProvider>
            <TestTimerComponent timerId="trilha-retorno" />
          </StudyTimerProvider>
        </AuthContext.Provider>
      );

      // Deve carregar exatamente 150 segundos em estado pausado
      expect(screen.getByTestId('seconds')).toHaveTextContent('150');
      expect(screen.getByTestId('running')).toHaveTextContent('PAUSED');
    });

    it('deve calcular tempo decorrido por diferença de timestamp absoluto sem drift por perda de foco', () => {
      const baseTime = 1000000;
      vi.spyOn(Date, 'now').mockReturnValue(baseTime);

      render(
        <AuthContext.Provider value={mockAuthValue('user-drift')}>
          <StudyTimerProvider>
            <TestTimerComponent />
          </StudyTimerProvider>
        </AuthContext.Provider>
      );

      act(() => {
        screen.getByTestId('start-btn').click();
      });

      expect(screen.getByTestId('seconds')).toHaveTextContent('0');

      // Avança 72 segundos no tempo real
      vi.spyOn(Date, 'now').mockReturnValue(baseTime + 72000);

      // Pausa e confere
      act(() => {
        screen.getByTestId('pause-btn').click();
      });

      expect(screen.getByTestId('seconds')).toHaveTextContent('72');
    });
  });

  describe('C. PomodoroApp Timer Isolado', () => {
    it('deve isolar a chave de storage por usuário e anon', () => {
      expect(getPomodoroTimerStorageKey('user-1')).toBe('trilhara:u:user-1:pomodoro_timer');
      // D-21: visitante usa namespace anônimo separado (`trilhara:anon:`), fora do escopo por usuário.
      expect(getPomodoroTimerStorageKey(null)).toBe('trilhara:anon:pomodoro_timer');
      expect(getPomodoroTimerStorageKey(undefined)).toBe('trilhara:anon:pomodoro_timer');
    });
  });

  describe('D. GlobalSessionModal e Contratos de UI', () => {
    const TestModalTriggerComponent: React.FC = () => {
      const { abrirModal } = useGlobalSessionModal();

      return (
        <div>
          <button
            data-testid="open-modal-estudos"
            onClick={() =>
              abrirModal({
                modo: 'ESTUDOS_TIMER',
                trilhaId: 'trilha-1',
                disciplinaId: 'disc-1',
                disciplinaNome: 'Direito Constitucional',
                duracaoInicialSegundos: 120
              })
            }
          >
            Abrir Estudos Timer
          </button>
          <button
            data-testid="open-modal-bloco-direto"
            onClick={() =>
              abrirModal({
                modo: 'BLOCO_DIRETO',
                trilhaId: 'trilha-1',
                disciplinaId: 'disc-1',
                blocoId: 'bloco-10',
                disciplinaNome: 'Português',
                duracaoInicialSegundos: 1800
              })
            }
          >
            Abrir Bloco Direto
          </button>
        </div>
      );
    };

    it('deve exibir botão "Concluir sem salvar sessão" exclusivamente em BLOCO_DIRETO', async () => {
      const user = userEvent.setup();

      render(
        <ToastProvider>
          <GlobalSessionModalProvider>
            <TestModalTriggerComponent />
            <GlobalSessionModal />
          </GlobalSessionModalProvider>
        </ToastProvider>
      );

      // 1. Abrir modo ESTUDOS_TIMER: não deve ter "Concluir sem salvar sessão"
      await user.click(screen.getByTestId('open-modal-estudos'));
      expect(screen.getByText('Registrar Sessão de Estudo')).toBeInTheDocument();
      expect(screen.queryByText('Concluir sem salvar sessão')).not.toBeInTheDocument();

      // Fechar modal
      await user.click(screen.getByText('Cancelar'));

      // 2. Abrir modo BLOCO_DIRETO: DEVE conter "Concluir sem salvar sessão"
      await user.click(screen.getByTestId('open-modal-bloco-direto'));
      expect(screen.getByText('Concluir Bloco de Cronograma')).toBeInTheDocument();
      expect(screen.getByText('Concluir sem salvar sessão')).toBeInTheDocument();
    });

    it('deve validar duração mínima de 60 segundos ao tentar salvar', async () => {
      const user = userEvent.setup();

      render(
        <ToastProvider>
          <GlobalSessionModalProvider>
            <TestModalTriggerComponent />
            <GlobalSessionModal />
          </GlobalSessionModalProvider>
        </ToastProvider>
      );

      await user.click(screen.getByTestId('open-modal-estudos'));

      // Altera horas e minutos para 0
      const horasInput = screen.getByLabelText('Horas');
      const minutosInput = screen.getByLabelText('Minutos');

      await user.clear(horasInput);
      await user.type(horasInput, '0');
      await user.clear(minutosInput);
      await user.type(minutosInput, '0');

      await user.click(screen.getByRole('button', { name: /Salvar Sessão/i }));

      expect(
        screen.getByText('A duração mínima para registrar uma sessão é de 1 minuto (60 segundos).')
      ).toBeInTheDocument();
    });

    it('deve calcular Erros e Aproveitamento (%) em tempo real', async () => {
      const user = userEvent.setup();

      render(
        <ToastProvider>
          <GlobalSessionModalProvider>
            <TestModalTriggerComponent />
            <GlobalSessionModal />
          </GlobalSessionModalProvider>
        </ToastProvider>
      );

      await user.click(screen.getByTestId('open-modal-estudos'));

      const questoesInput = screen.getByLabelText(/Questões Feitas/i);
      const acertosInput = screen.getByLabelText(/Acertos/i);

      await user.type(questoesInput, '20');
      await user.type(acertosInput, '16');

      expect(screen.getByText('Erros')).toBeInTheDocument();
      expect(screen.getByText('4')).toBeInTheDocument(); // 20 - 16 = 4
      expect(screen.getByText('80.0%')).toBeInTheDocument(); // 16 / 20 = 80.0%
    });

    it('deve exibir aviso e botão de retry da Etapa 2 em caso de falha parcial', async () => {
      const user = userEvent.setup();

      // Etapa 1 sucede:
      vi.spyOn(sessaoGlobalService, 'registrarSessao').mockResolvedValueOnce({
        id: 'sess-sucesso-1',
        trilhaId: 'trilha-1',
        disciplinaId: 'disc-1',
        duracaoSegundos: 1800,
        dataSessao: '2026-11-09',
        criadoEm: '2026-11-09T10:00:00Z'
      });

      // Etapa 2 falha na conclusão do bloco:
      vi.spyOn(sessaoGlobalService, 'concluirBloco').mockRejectedValueOnce(
        new Error('Network error ao concluir bloco')
      );

      render(
        <ToastProvider>
          <GlobalSessionModalProvider>
            <TestModalTriggerComponent />
            <GlobalSessionModal />
          </GlobalSessionModalProvider>
        </ToastProvider>
      );

      await user.click(screen.getByTestId('open-modal-bloco-direto'));

      // Clica em "Salvar sessão e concluir bloco"
      const submitBtn = screen.getByRole('button', { name: /Salvar sessão e concluir bloco/i });
      await user.click(submitBtn);

      // Deve exibir o aviso de ação parcial
      await waitFor(() => {
        expect(
          screen.getByText(
            'A sessão de estudo foi salva com sucesso, mas não foi possível marcar o bloco como concluído.'
          )
        ).toBeInTheDocument();
      });

      // O botão principal mudou para "Tentar concluir bloco novamente"
      expect(
        screen.getByRole('button', { name: /Tentar concluir bloco novamente/i })
      ).toBeInTheDocument();
    });
  });
});
