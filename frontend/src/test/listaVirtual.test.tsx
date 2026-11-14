import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import {
  ToastProvider,
  ConfirmProvider,
  GlobalSessionModalProvider,
  GlobalSessionModal
} from '../shared/design-system';
import { AuthContext, AuthContextValue } from '../shared/auth/AuthContext';
import { StudyTimerProvider } from '../shared/timers/estudoTimer';
import { AgendaHoje } from '../shared/agenda/AgendaHoje';
import { AgendaHojeDerivado } from '../shared/agenda/agendaHojeService';
import * as apiModule from '../shared/auth/api';
import TarefasApp from '../modules/tarefas/TarefasApp';
import { BlocoCronogramaItemDTO, BlocoHojeDTO } from '../shared/contracts/domainContracts';

vi.mock('../shared/auth/api', async (importOriginal) => {
  const actual = await importOriginal<typeof apiModule>();
  return { ...actual, request: vi.fn() };
});

const USUARIO = '6af20000000000000000000a';

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

function montar(ui: React.ReactNode) {
  return render(
    <ToastProvider>
      <ConfirmProvider>
        <AuthContext.Provider value={authValue()}>
          <StudyTimerProvider>
            <GlobalSessionModalProvider>
              {ui}
              <GlobalSessionModal />
            </GlobalSessionModalProvider>
          </StudyTimerProvider>
        </AuthContext.Provider>
      </ConfirmProvider>
    </ToastProvider>
  );
}

function blocoItem(id: string, extras: Partial<BlocoCronogramaItemDTO> = {}): BlocoCronogramaItemDTO {
  return {
    id,
    trilhaId: 'trilha-1',
    cronogramaId: 'crono-1',
    versaoGeracao: 1,
    data: '2026-11-09',
    ordem: 1,
    disciplinaId: 'disc-1',
    disciplinaNome: 'Direito Penal',
    disciplinaCor: '#ef4444',
    duracaoMinutos: 50,
    concluido: false,
    concluidoEm: null,
    statusData: 'HOJE',
    ...extras
  };
}

/** Bloco da Visão Hoje (`BlocoHojeDTO`), distinto do item paginado da lista virtual. */
function blocoHoje(id: string): BlocoHojeDTO {
  return {
    blocoId: id,
    trilhaId: 'trilha-1',
    cronogramaId: 'crono-1',
    disciplinaId: 'disc-1',
    disciplinaNome: 'Direito Penal',
    disciplinaCor: '#ef4444',
    trilhaNome: 'Polícia Federal',
    data: '2026-11-09',
    ordem: 1,
    duracaoMinutos: 50,
    concluido: false
  };
}

function agendaVazia(): AgendaHojeDerivado {
  return {
    hojeLocal: '2026-11-09',
    status: 'sucesso',
    statusTarefas: 'sucesso',
    statusBlocos: 'sucesso',
    atrasadas: [],
    atrasadasAgrupadas: [],
    blocosHoje: [],
    tarefasHoje: [],
    semData: [],
    concluidasHoje: [],
    erroTarefas: null,
    erroBlocos: null,
    vazioTotal: true
  };
}

describe('listaVirtual', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('blocosDaListaVirtualNaoExpoemEditarNemExcluir', () => {
    render(
      <AgendaHoje
        modo="tarefas"
        dados={{ ...agendaVazia(), vazioTotal: false, blocosHoje: [blocoHoje('bloco-1')] }}
        onAlternarTarefa={vi.fn()}
        onConcluirBloco={vi.fn()}
        onRetryTarefas={vi.fn()}
        onRetryBlocos={vi.fn()}
        onEditarTarefa={vi.fn()}
        onExcluirTarefa={vi.fn()}
      />
    );

    const linhaBloco = screen.getByText('Direito Penal').closest('div')!.parentElement!;
    expect(within(linhaBloco).queryByRole('button', { name: /editar/i })).toBeNull();
    expect(within(linhaBloco).queryByRole('button', { name: /excluir/i })).toBeNull();
  });

  it('filtrosGeraisNaoRenderizamBlocos', async () => {
    const trilhas = [
      {
        id: 'trilha-1',
        nome: 'Polícia Federal',
        cronogramaAtivoResumo: { cronogramaAtivoId: 'crono-1', modalidade: 'PADRAO', dataInicio: '2026-11-01', dataFim: '2026-12-01' }
      }
    ];

    vi.mocked(apiModule.request).mockImplementation(async (caminho: string) => {
      if (caminho === '/trilhas') return trilhas;
      if (caminho === '/listas-tarefas') return [];
      if (caminho === '/tarefas/hoje') return { hojeLocal: '2026-11-09', atrasadas: [], hoje: [], semData: [] };
      if (caminho === '/blocos/hoje') return [];
      if (caminho.includes('/blocos?')) {
        return { itens: [blocoItem('bloco-1')], pagina: 1, tamanho: 50, totalItens: 1, totalPaginas: 1 };
      }
      if (caminho.startsWith('/tarefas?')) {
        return { itens: [], pagina: 1, tamanho: 50, totalItens: 0, totalPaginas: 1 };
      }
      return [];
    });

    montar(<TarefasApp />);

    // Filtro "Todas as Tarefas" no menu lateral não inclui blocos do cronograma.
    fireEvent.click(await screen.findByRole('button', { name: /todas as tarefas/i }));

    await waitFor(() => {
      expect(screen.queryByText('Direito Penal')).not.toBeInTheDocument();
    });
  });

  it('contagemDeAtrasadasUsaTotalItensComTamanhoUm', async () => {
    const caminhos: string[] = [];

    vi.mocked(apiModule.request).mockImplementation(async (caminho: string) => {
      caminhos.push(caminho);
      if (caminho === '/trilhas') {
        return [
          {
            id: 'trilha-1',
            nome: 'Polícia Federal',
            cronogramaAtivoResumo: {
              cronogramaAtivoId: 'crono-1',
              modalidade: 'PADRAO',
              dataInicio: '2026-11-01',
              dataFim: '2026-12-01'
            }
          }
        ];
      }
      if (caminho === '/listas-tarefas') return [];
      if (caminho === '/tarefas/hoje') {
        return { hojeLocal: '2026-11-09', atrasadas: [], hoje: [], semData: [] };
      }
      if (caminho === '/blocos/hoje') return [];
      if (caminho.includes('statusData=ATRASADO')) {
        // A página carregada tem 1 bloco, mas o total real é 7.
        return { itens: [blocoItem('bloco-atrasado', { statusData: 'ATRASADO' })], pagina: 1, tamanho: 1, totalItens: 7, totalPaginas: 7 };
      }
      if (caminho.includes('/blocos?')) {
        return { itens: [blocoItem('bloco-1')], pagina: 1, tamanho: 50, totalItens: 1, totalPaginas: 1 };
      }
      if (caminho.startsWith('/tarefas?')) {
        return { itens: [], pagina: 1, tamanho: 50, totalItens: 0, totalPaginas: 1 };
      }
      return [];
    });

    montar(<TarefasApp />);

    fireEvent.click(await screen.findByTitle('Trilha: Polícia Federal'));

    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: /concluir todas as atrasadas sem salvar \(7\)/i })
      ).toBeInTheDocument();
    });

    expect(caminhos).toContain(
      '/trilhas/trilha-1/cronogramas/crono-1/blocos?concluido=false&statusData=ATRASADO&pagina=1&tamanho=1'
    );
  });

  it('carregarMaisAcrescentaAProximaPaginaDeBlocos', async () => {
    vi.mocked(apiModule.request).mockImplementation(async (caminho: string) => {
      if (caminho === '/trilhas') {
        return [
          {
            id: 'trilha-1',
            nome: 'Polícia Federal',
            cronogramaAtivoResumo: {
              cronogramaAtivoId: 'crono-1',
              modalidade: 'PADRAO',
              dataInicio: '2026-11-01',
              dataFim: '2026-12-01'
            }
          }
        ];
      }
      if (caminho === '/listas-tarefas') return [];
      if (caminho === '/tarefas/hoje') {
        return { hojeLocal: '2026-11-09', atrasadas: [], hoje: [], semData: [] };
      }
      if (caminho === '/blocos/hoje') return [];
      if (caminho.includes('statusData=ATRASADO')) {
        return { itens: [], pagina: 1, tamanho: 1, totalItens: 0, totalPaginas: 0 };
      }
      if (caminho.includes('pagina=2')) {
        return {
          itens: [blocoItem('bloco-pagina-2', { disciplinaNome: 'Português' })],
          pagina: 2,
          tamanho: 50,
          totalItens: 2,
          totalPaginas: 2
        };
      }
      if (caminho.includes('/blocos?')) {
        return {
          itens: [blocoItem('bloco-pagina-1')],
          pagina: 1,
          tamanho: 50,
          totalItens: 2,
          totalPaginas: 2
        };
      }
      if (caminho.startsWith('/tarefas?')) {
        return { itens: [], pagina: 1, tamanho: 50, totalItens: 0, totalPaginas: 1 };
      }
      return [];
    });

    montar(<TarefasApp />);

    fireEvent.click(await screen.findByTitle('Trilha: Polícia Federal'));

    await waitFor(() => expect(screen.getByText('Direito Penal')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: /carregar mais blocos/i }));

    await waitFor(() => expect(screen.getByText('Português')).toBeInTheDocument());
    expect(screen.getByText('Direito Penal')).toBeInTheDocument();
  });

  it('resultadoParcialDoLoteExibeToastComTentarNovamente', async () => {
    const payloads: string[] = [];

    vi.mocked(apiModule.request).mockImplementation(async (caminho: string, opcoes?: RequestInit) => {
      if (caminho === '/trilhas') {
        return [
          {
            id: 'trilha-1',
            nome: 'Polícia Federal',
            cronogramaAtivoResumo: {
              cronogramaAtivoId: 'crono-1',
              modalidade: 'PADRAO',
              dataInicio: '2026-11-01',
              dataFim: '2026-12-01'
            }
          }
        ];
      }
      if (caminho === '/listas-tarefas') return [];
      if (caminho === '/tarefas/hoje') {
        return { hojeLocal: '2026-11-09', atrasadas: [], hoje: [], semData: [] };
      }
      if (caminho === '/blocos/hoje') return [];
      if (caminho.endsWith('/concluir-atrasados')) {
        payloads.push(String(opcoes?.body ?? ''));
        return {
          operationId: 'op-1',
          totalProcessados: 3,
          totalConcluidos: 2,
          falhas: [{ blocoId: 'bloco-x', motivo: 'falha' }]
        };
      }
      if (caminho.includes('statusData=ATRASADO')) {
        return { itens: [], pagina: 1, tamanho: 1, totalItens: 1, totalPaginas: 1 };
      }
      if (caminho.includes('/blocos?')) {
        return {
          itens: [blocoItem('bloco-atrasado', { statusData: 'ATRASADO' })],
          pagina: 1,
          tamanho: 50,
          totalItens: 1,
          totalPaginas: 1
        };
      }
      if (caminho.startsWith('/tarefas?')) {
        return { itens: [], pagina: 1, tamanho: 50, totalItens: 0, totalPaginas: 1 };
      }
      return [];
    });

    montar(<TarefasApp />);

    fireEvent.click(await screen.findByTitle('Trilha: Polícia Federal'));

    const botaoLote = await screen.findByRole('button', {
      name: /concluir todas as atrasadas sem salvar \(1\)/i
    });
    fireEvent.click(botaoLote);

    // Confirma o diálogo de quantidade.
    fireEvent.click(await screen.findByRole('button', { name: /concluir atrasados/i }));

    await waitFor(() => {
      expect(screen.getByText('2 concluídos, 1 falharam')).toBeInTheDocument();
    });

    expect(screen.getByRole('button', { name: /tentar novamente/i })).toBeInTheDocument();
    expect(payloads).toHaveLength(1);
    expect(JSON.parse(payloads[0])).toHaveProperty('operationId');
  });

  it('sidebarSemRedundanciaEBarraInferiorMobileComTodosOsFiltros', async () => {
    vi.mocked(apiModule.request).mockImplementation(async (caminho: string) => {
      if (caminho === '/tarefas/hoje') {
        return { hojeLocal: '2026-11-09', atrasadas: [], hoje: [], semData: [], concluidasHoje: [] };
      }
      return [];
    });

    montar(<TarefasApp />);

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: /^Tarefas de Hoje$/i })).toBeInTheDocument()
    );

    // Sem barra superior de tabs redundante
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();

    const sidebar = screen.getByLabelText('Painel lateral de navegação');
    const bottomNav = screen.getByRole('navigation', { name: 'Navegação principal mobile' });

    // Cartões no topo da sidebar ("Hoje" e "Fluxo de Tarefas") sem repetição na seção "Filtros"
    expect(within(sidebar).getByRole('button', { name: 'Hoje' })).toBeInTheDocument();
    expect(within(sidebar).queryByText('Tarefas do dia')).not.toBeInTheDocument();
    expect(within(sidebar).getAllByRole('button', { name: /fluxo de tarefas/i })).toHaveLength(1);
    expect(within(sidebar).getAllByRole('button', { name: /próximos dias/i })).toHaveLength(1);

    // Barra inferior mobile reutilizando AppSidebar com todos os filtros
    expect(within(bottomNav).getByRole('button', { name: 'Hoje' })).toBeInTheDocument();
    expect(within(bottomNav).getByRole('button', { name: 'Próximos' })).toBeInTheDocument();
    expect(within(bottomNav).getByRole('button', { name: 'Fluxo' })).toBeInTheDocument();
    expect(within(bottomNav).getByRole('button', { name: 'Todas' })).toBeInTheDocument();
    expect(within(bottomNav).getByRole('button', { name: 'Lixeira' })).toBeInTheDocument();
  });
});
