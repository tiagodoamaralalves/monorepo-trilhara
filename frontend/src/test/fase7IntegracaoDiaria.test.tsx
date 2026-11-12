import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import {
  agendaHojeService,
  agruparAtrasadas
} from '../shared/services/agendaHojeService';
import {
  SectionErrorCard,
  GlobalSessionModalProvider,
  GlobalSessionModal,
  ToastProvider,
  ConfirmProvider
} from '../shared/design-system';
import { AuthContext, AuthContextValue } from '../shared/auth/AuthContext';
import { StudyTimerProvider } from '../shared/timers/estudoTimer';
import * as apiModule from '../shared/auth/api';
import { TarefaService } from '../modules/tarefas/services/tarefaService';
import PomodoroApp from '../modules/pomodoro/PomodoroApp';
import TarefasApp from '../modules/tarefas/TarefasApp';
import { BlocoHojeDTO, TarefasHojeDTO, TarefaDTO } from '../shared/contracts/domainContracts';

// Mock do módulo de API request central
vi.mock('../shared/auth/api', async (importOriginal) => {
  const actual = await importOriginal<typeof apiModule>();
  return {
    ...actual,
    request: vi.fn()
  };
});

const mockAuthValue = (usuarioId: string | null = 'user-fase7'): AuthContextValue => ({
  usuario: usuarioId
    ? {
        id: usuarioId,
        nome: 'Aluno Teste',
        email: 'aluno@trilhara.com.br',
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

describe('FASE 7 — Integração Diária: SectionErrorCard, AgendaHojeService, Listas Virtuais e Espelho Pomodoro', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    agendaHojeService.resetarParaTestes();
  });

  describe('1. AgendaHojeService — Combinação, Ordenação Determinística e Agrupamento de Séries', () => {
    it('agrupa ocorrências atrasadas da mesma série sob a mais recente', () => {
      const atrasadas: TarefaDTO[] = [
        {
          id: 'tar-antiga',
          titulo: 'Exercícios Constitucional - Aula 1',
          serieId: 'serie-const',
          dataVencimento: '2026-11-01',
          prioridade: 'MEDIA',
          concluida: false
        },
        {
          id: 'tar-recente',
          titulo: 'Exercícios Constitucional - Aula 2',
          serieId: 'serie-const',
          dataVencimento: '2026-11-05',
          prioridade: 'MEDIA',
          concluida: false
        },
        {
          id: 'tar-avulsa',
          titulo: 'Pagar inscrição do concurso',
          dataVencimento: '2026-11-03',
          prioridade: 'ALTA',
          concluida: false
        }
      ];

      const grupos = agruparAtrasadas(atrasadas);

      // Deve ter 2 grupos: a avulsa e o grupo da série
      expect(grupos).toHaveLength(2);

      const grupoSerie = grupos.find((g) => g.principal.serieId === 'serie-const');
      expect(grupoSerie).toBeDefined();
      expect(grupoSerie!.principal.id).toBe('tar-recente'); // Mais recente é principal
      expect(grupoSerie!.anteriores).toHaveLength(1);
      expect(grupoSerie!.anteriores[0].id).toBe('tar-antiga'); // Anterior agrupada
    });

    it('combina e ordena deterministamente: Atrasadas, Hoje (blocos + manuais) e Sem Data', async () => {
      const mockTarefasHoje: TarefasHojeDTO = {
        hojeLocal: '2026-11-09',
        atrasadas: [
          {
            id: 'atr-1',
            titulo: 'Revisão Atrasada',
            dataVencimento: '2026-11-08',
            prioridade: 'ALTA',
            ordem: 0,
            concluida: false
          }
        ],
        hoje: [
          {
            id: 'man-sem-hora',
            titulo: 'Tarefa de Hoje Sem Horário',
            dataVencimento: '2026-11-09',
            prioridade: 'MEDIA',
            ordem: 2,
            concluida: false
          },
          {
            id: 'man-com-hora',
            titulo: 'Tarefa de Hoje Com Horário 09:00',
            dataVencimento: '2026-11-09',
            horario: '09:00',
            prioridade: 'MEDIA',
            ordem: 1,
            concluida: false
          }
        ],
        semData: [
          {
            id: 'sd-1',
            titulo: 'Backlog Alta Prioridade',
            prioridade: 'ALTA',
            ordem: 1,
            concluida: false
          },
          {
            id: 'sd-2',
            titulo: 'Backlog Média Prioridade',
            prioridade: 'MEDIA',
            ordem: 0,
            concluida: false
          }
        ]
      };

      const mockBlocosHoje: BlocoHojeDTO[] = [
        {
          blocoId: 'bloco-b',
          trilhaId: 'trilha-2',
          cronogramaId: 'crono-2',
          disciplinaId: 'disc-2',
          disciplinaNome: 'Português',
          disciplinaCor: '#10b981',
          trilhaNome: 'Receita Federal',
          data: '2026-11-09',
          ordem: 1,
          duracaoMinutos: 50,
          concluido: false
        },
        {
          blocoId: 'bloco-a',
          trilhaId: 'trilha-1',
          cronogramaId: 'crono-1',
          disciplinaId: 'disc-1',
          disciplinaNome: 'Direito Constitucional',
          disciplinaCor: '#0ea5e9',
          trilhaNome: 'Polícia Federal',
          data: '2026-11-09',
          ordem: 1,
          duracaoMinutos: 60,
          concluido: false
        }
      ];

      vi.mocked(apiModule.request).mockImplementation(async (path: string) => {
        if (path === '/tarefas/hoje') return mockTarefasHoje;
        if (path === '/blocos/hoje') return mockBlocosHoje;
        return [];
      });

      const agenda = await agendaHojeService.recarregar();

      expect(agenda.hojeLocal).toBe('2026-11-09');
      expect(agenda.erroTarefas).toBeNull();
      expect(agenda.erroEstudos).toBeNull();

      // Seção 1: Atrasadas
      expect(agenda.atrasadas).toHaveLength(1);
      expect(agenda.atrasadas[0].id).toBe('atr-1');

      // Seção 2: Hoje - Blocos ordenados por trilhaNome ASC ('Polícia Federal' antes de 'Receita Federal')
      expect(agenda.blocosHoje).toHaveLength(2);
      expect(agenda.blocosHoje[0].trilhaNome).toBe('Polícia Federal');
      expect(agenda.blocosHoje[1].trilhaNome).toBe('Receita Federal');

      // Seção 2: Hoje - Tarefas manuais com horário vem primeiro (NULLS LAST)
      expect(agenda.tarefasHoje).toHaveLength(2);
      expect(agenda.tarefasHoje[0].id).toBe('man-com-hora');
      expect(agenda.tarefasHoje[1].id).toBe('man-sem-hora');

      // Seção 3: Sem Data - Ordenadas por prioridade DESC (ALTA antes de MEDIA)
      expect(agenda.semData).toHaveLength(2);
      expect(agenda.semData[0].id).toBe('sd-1');
      expect(agenda.semData[1].id).toBe('sd-2');
    });

    it('resiliência a falha parcial: se GET /api/blocos/hoje falhar, entrega tarefas manuais e preenche erroEstudos', async () => {
      const mockTarefasHoje: TarefasHojeDTO = {
        hojeLocal: '2026-11-09',
        atrasadas: [],
        hoje: [
          {
            id: 'tar-ok',
            titulo: 'Tarefa Manual Ativa',
            dataVencimento: '2026-11-09',
            prioridade: 'MEDIA',
            concluida: false
          }
        ],
        semData: []
      };

      vi.mocked(apiModule.request).mockImplementation(async (path: string) => {
        if (path === '/tarefas/hoje') return mockTarefasHoje;
        if (path === '/blocos/hoje') throw new Error('Serviço de Estudos Indisponível (503)');
        return [];
      });

      const agenda = await agendaHojeService.recarregar();

      expect(agenda.erroEstudos).toBeDefined();
      expect(agenda.erroEstudos?.message).toContain('503');
      expect(agenda.erroTarefas).toBeNull();
      expect(agenda.tarefasHoje).toHaveLength(1);
      expect(agenda.tarefasHoje[0].id).toBe('tar-ok');
      expect(agenda.blocosHoje).toHaveLength(0); // Não quebra a agenda geral
    });
  });

  describe('2. Componente SectionErrorCard', () => {
    it('renderiza título, mensagem clara e dispara onRetry ao clicar no botão', () => {
      const onRetryMock = vi.fn();

      render(
        <SectionErrorCard
          titulo="Erro ao carregar blocos de estudo"
          descricao="Falha de conexão com a API de Estudos."
          onRetry={onRetryMock}
        />
      );

      expect(screen.getByTestId('section-error-card')).toBeInTheDocument();
      expect(screen.getByText('Erro ao carregar blocos de estudo')).toBeInTheDocument();
      expect(screen.getByText('Falha de conexão com a API de Estudos.')).toBeInTheDocument();

      const btnRetry = screen.getByRole('button', { name: /tentar novamente/i });
      fireEvent.click(btnRetry);
      expect(onRetryMock).toHaveBeenCalledTimes(1);
    });
  });

  describe('3. Falha Simulada em UI — Exibe Tarefas Manuais e SectionErrorCard com Retry', () => {
    it('em PomodoroApp, falha simulada de estudos exibe SectionErrorCard enquanto tarefas manuais aparecem', async () => {
      const mockTarefasHoje: TarefasHojeDTO = {
        hojeLocal: '2026-11-09',
        atrasadas: [],
        hoje: [
          {
            id: 'tar-pomo-1',
            titulo: 'Ler resumo de Direito Administrativo',
            dataVencimento: '2026-11-09',
            prioridade: 'MEDIA',
            concluida: false
          }
        ],
        semData: []
      };

      vi.mocked(apiModule.request).mockImplementation(async (path: string) => {
        if (path === '/tarefas/hoje') return mockTarefasHoje;
        if (path === '/blocos/hoje') throw new Error('Falha de rede em estudos');
        return [];
      });

      render(
        <ToastProvider>
          <ConfirmProvider>
            <AuthContext.Provider value={mockAuthValue('user-pomo')}>
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

      // Tarefa manual deve ser renderizada normalmente
      await waitFor(() => {
        expect(screen.getByText('Ler resumo de Direito Administrativo')).toBeInTheDocument();
      });

      // SectionErrorCard deve ser renderizado para a seção de blocos de estudo com botão de retry
      await waitFor(() => {
        expect(screen.getByText(/Erro ao carregar blocos de estudos/i)).toBeInTheDocument();
      });

      const retryBtn = screen.getByRole('button', { name: /tentar novamente/i });
      expect(retryBtn).toBeInTheDocument();
    });
  });

  describe('4. Delimitação de Contrato: Conclusão de Tarefa Manual vs Bloco de Estudo', () => {
    it('concluir tarefa manual em PomodoroApp NÃO abre GlobalSessionModal', async () => {
      const mockTarefasHoje: TarefasHojeDTO = {
        hojeLocal: '2026-11-09',
        atrasadas: [],
        hoje: [
          {
            id: 'tar-manual-1',
            titulo: 'Resolver 20 questões de RLM',
            dataVencimento: '2026-11-09',
            prioridade: 'MEDIA',
            concluida: false
          }
        ],
        semData: []
      };

      const spyAlternar = vi.spyOn(TarefaService, 'alternarConclusao').mockResolvedValue({
        id: 'tar-manual-1',
        titulo: 'Resolver 20 questões de RLM',
        concluida: true,
        prioridade: 'MEDIA',
        origem: 'MANUAL',
        criadoEm: '2026-11-09T12:00:00Z',
        atualizadoEm: '2026-11-09T12:00:00Z'
      });

      vi.mocked(apiModule.request).mockImplementation(async (path: string) => {
        if (path === '/tarefas/hoje') return mockTarefasHoje;
        if (path === '/blocos/hoje') return [];
        return [];
      });

      render(
        <ToastProvider>
          <ConfirmProvider>
            <AuthContext.Provider value={mockAuthValue('user-pomo-2')}>
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

      await waitFor(() => {
        expect(screen.getByText('Resolver 20 questões de RLM')).toBeInTheDocument();
      });

      // Clica no botão de concluir tarefa
      const btnConcluir = screen.getByRole('button', {
        name: /concluir tarefa resolver 20 questões de rlm/i
      });
      fireEvent.click(btnConcluir);

      // Deve chamar o service de Tarefas
      await waitFor(() => {
        expect(spyAlternar).toHaveBeenCalledWith('tar-manual-1', true);
      });

      // NÃO deve abrir o modal de registro de sessão (GlobalSessionModal)
      expect(screen.queryByText(/Registrar Sessão de Estudo/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/Salvar Sessão e Concluir/i)).not.toBeInTheDocument();
    });

    it('concluir bloco de estudo em PomodoroApp ABRE o GlobalSessionModal (modo BLOCO_DIRETO)', async () => {
      const mockBloco: BlocoHojeDTO = {
        blocoId: 'bloco-estudo-99',
        trilhaId: 'trilha-auditor',
        cronogramaId: 'crono-auditor',
        disciplinaId: 'disc-direito-trib',
        disciplinaNome: 'Direito Tributário',
        disciplinaCor: '#e11d48',
        trilhaNome: 'Auditor Fiscal',
        data: '2026-11-09',
        ordem: 1,
        duracaoMinutos: 45,
        concluido: false
      };

      vi.mocked(apiModule.request).mockImplementation(async (path: string) => {
        if (path === '/tarefas/hoje') return { hojeLocal: '2026-11-09', atrasadas: [], hoje: [], semData: [] };
        if (path === '/blocos/hoje') return [mockBloco];
        return [];
      });

      render(
        <ToastProvider>
          <ConfirmProvider>
            <AuthContext.Provider value={mockAuthValue('user-pomo-3')}>
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

      await waitFor(() => {
        expect(screen.getByText('Direito Tributário')).toBeInTheDocument();
      });

      // Clica no botão de concluir bloco
      const btnConcluirBloco = screen.getByRole('button', {
        name: /concluir bloco de direito tributário/i
      });
      fireEvent.click(btnConcluirBloco);

      // DEVE abrir o GlobalSessionModal com as opções de BLOCO_DIRETO
      await waitFor(() => {
        expect(screen.getByText(/Concluir Bloco de Cronograma/i)).toBeInTheDocument();
      });

      // Em modo BLOCO_DIRETO, exibe a opção de concluir sem salvar sessão
      // D-22.3: o rótulo do botão principal é fixo ("Salvar sessão").
      expect(screen.getByText(/Concluir sem salvar sessão/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /^Salvar sessão$/i })).toBeInTheDocument();
    });

    it('visitante não autenticado em PomodoroApp vê cronômetro funcional e card convidativo', () => {
      render(
        <ToastProvider>
          <ConfirmProvider>
            <AuthContext.Provider value={mockAuthValue(null)}>
              <StudyTimerProvider>
              <GlobalSessionModalProvider>
                <PomodoroApp />
              </GlobalSessionModalProvider>
              </StudyTimerProvider>
            </AuthContext.Provider>
          </ConfirmProvider>
        </ToastProvider>
      );

      // Cronômetro funcional presente
      expect(screen.getByRole('timer')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /iniciar foco/i })).toBeInTheDocument();

      // Painel lateral com card convidativo
      expect(screen.getByText(/Organize seus estudos e tarefas/i)).toBeInTheDocument();
      expect(
        screen.getByText(/Entre na sua conta para acompanhar tarefas diárias/i)
      ).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /entrar na conta/i })).toBeInTheDocument();
    });
  });

  describe('5. Lista Virtual por Trilha em Tarefas (TarefasApp)', () => {
    it('renderiza Lista Virtual da Trilha com badges de status, botão de concluir atrasadas e modal de sessão ao concluir bloco', async () => {
      const mockTrilhas = [
        {
          id: 'trilha-policia',
          nome: 'Polícia Federal - Agente',
          cor: '#0ea5e9',
          cronogramaAtivoResumo: {
            cronogramaAtivoId: 'crono-pf-1',
            modalidade: 'SEQUENCIAL',
            dataInicio: '2026-11-01',
            dataFim: '2026-11-30'
          }
        }
      ];

      const mockBlocosPaginados = {
        pagina: 1,
        tamanho: 50,
        totalItens: 3,
        totalPaginas: 1,
        itens: [
          {
            id: 'bloco-atrasado',
            trilhaId: 'trilha-policia',
            cronogramaId: 'crono-pf-1',
            disciplinaId: 'disc-dir-penal',
            disciplinaNome: 'Direito Penal',
            disciplinaCor: '#ef4444',
            data: '2026-11-05',
            ordem: 1,
            duracaoMinutos: 40,
            concluido: false,
            statusData: 'ATRASADO'
          },
          {
            id: 'bloco-hoje',
            trilhaId: 'trilha-policia',
            cronogramaId: 'crono-pf-1',
            disciplinaId: 'disc-rlm',
            disciplinaNome: 'RLM',
            disciplinaCor: '#f59e0b',
            data: '2026-11-09',
            ordem: 2,
            duracaoMinutos: 50,
            concluido: false,
            statusData: 'HOJE'
          },
          {
            id: 'bloco-futuro',
            trilhaId: 'trilha-policia',
            cronogramaId: 'crono-pf-1',
            disciplinaId: 'disc-portugues',
            disciplinaNome: 'Língua Portuguesa',
            disciplinaCor: '#10b981',
            data: '2026-11-12',
            ordem: 1,
            duracaoMinutos: 60,
            concluido: false,
            statusData: 'FUTURO'
          }
        ]
      };

      vi.mocked(apiModule.request).mockImplementation(async (path: string) => {
        if (path === '/trilhas') return mockTrilhas;
        if (path.includes('/blocos?concluido=false')) return mockBlocosPaginados;
        if (path === '/listas-tarefas') return [];
        if (path.startsWith('/tarefas')) return [];
        if (path === '/blocos/hoje') return [];
        return [];
      });

      render(
        <ToastProvider>
          <ConfirmProvider>
            <AuthContext.Provider value={mockAuthValue('user-tarefas-virtual')}>
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

      // Aguarda carregar as trilhas na sidebar
      await waitFor(() => {
        expect(screen.getByText('Polícia Federal - Agente')).toBeInTheDocument();
      });

      // Clica na trilha na sidebar para abrir a Lista Virtual
      const btnTrilha = screen.getByTitle('Trilha: Polícia Federal - Agente');
      fireEvent.click(btnTrilha);

      // Deve exibir os blocos pendentes com seus respectivos badges
      await waitFor(() => {
        expect(screen.getByText('Direito Penal')).toBeInTheDocument();
        expect(screen.getByText('RLM')).toBeInTheDocument();
        expect(screen.getByText('Língua Portuguesa')).toBeInTheDocument();
      });

      expect(screen.getByText('Atrasado')).toBeInTheDocument();
      expect(screen.getAllByText('Hoje').length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText('Futuro')).toBeInTheDocument();

      // Como há 1 bloco atrasado, deve exibir o botão de lote
      expect(
        screen.getByRole('button', { name: /concluir todas as atrasadas sem salvar \(1\)/i })
      ).toBeInTheDocument();

      // Clica para concluir um bloco -> deve abrir o GlobalSessionModal
      const btnConcluir = screen.getByRole('button', { name: /concluir bloco de direito penal/i });
      fireEvent.click(btnConcluir);

      await waitFor(() => {
        expect(screen.getByText(/Concluir Bloco de Cronograma/i)).toBeInTheDocument();
      });
      expect(screen.getByText(/Concluir sem salvar sessão/i)).toBeInTheDocument();
    });
  });
});
