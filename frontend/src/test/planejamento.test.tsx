import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import {
  GlobalSessionModal,
  GlobalSessionModalProvider,
  ConfirmProvider,
  ToastProvider,
  hojeISO,
} from '../shared/design-system';
import { StudyTimerProvider } from '../shared/timers/estudoTimer';
import { AuthContext, type AuthContextValue } from '../shared/auth/AuthContext';
import * as apiModule from '../modules/estudos/services/api';
import { PlanejamentoView, type PlanejamentoProps } from '../modules/estudos/views/PlanejamentoView';
import type { TrilhaDetalheDTO } from '../modules/estudos/types/trilhara';

const authContextMock: AuthContextValue = {
  usuario: null,
  isAuthenticated: false,
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
  setUsuario: vi.fn(),
};

vi.mock('../modules/estudos/services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof apiModule>();
  return { ...actual, request: vi.fn() };
});

const requestMock = apiModule.request as unknown as ReturnType<typeof vi.fn>;

function trilha(overrides: Partial<TrilhaDetalheDTO> = {}): TrilhaDetalheDTO {
  return {
    id: 'trilha-1',
    nome: 'Trilha Auditor',
    dataProva: '2026-12-15',
    disciplinas: [
      { id: 'd1', nome: 'Português', cor: '#111', peso: 3, topicos: [] },
      { id: 'd2', nome: 'Direito', cor: '#222', peso: 2, topicos: [] }
    ],
    topicosConcluidosIds: [],
    ...overrides
  };
}

function cronograma(overrides: Record<string, unknown> = {}) {
  return {
    id: 'crono-1',
    trilhaId: 'trilha-1',
    modalidade: 'SEQUENCIAL',
    ativo: true,
    versaoAtiva: 1,
    duracaoBlocoMinutos: 60,
    tipoPeriodo: 'ATE_PROVA',
    dataInicio: '2026-11-12',
    dataFim: '2026-12-15',
    diasAtivosSemana: [1, 2, 3, 4, 5],
    blocosPorDia: 2,
    disciplinasParticipantesIds: ['d1', 'd2'],
    quantidadeBlocos: 30,
    cargaMinutosPorDia: [120, 120, 120, 120, 120, 0, 0],
    estadoPublicacao: 'ATIVO',
    ...overrides
  };
}

const DISPONIBILIDADE_ZERO = {
  disponibilidadeSemanalMinutos: [0, 0, 0, 0, 0, 0, 0],
  cargaAtivaMinutosPorDia: [0, 0, 0, 0, 0, 0, 0],
  sobrecargaPorDia: [false, false, false, false, false, false, false]
};

function renderView(trilhaProp: TrilhaDetalheDTO, extraProps: Partial<PlanejamentoProps> = {}) {
  return render(
    <AuthContext.Provider value={authContextMock}>
      <ToastProvider>
        <ConfirmProvider>
          <StudyTimerProvider>
            <GlobalSessionModalProvider>
              <PlanejamentoView trilha={trilhaProp} {...extraProps} />
              <GlobalSessionModal />
            </GlobalSessionModalProvider>
          </StudyTimerProvider>
        </ConfirmProvider>
      </ToastProvider>
    </AuthContext.Provider>
  );
}

function mockPadrao(overrides: { cronogramas?: unknown[]; blocos?: unknown; atrasados?: number } = {}) {
  requestMock.mockImplementation(async (path: string) => {
    if (path === '/trilhas/trilha-1/cronogramas') return overrides.cronogramas ?? [cronograma()];
    if (path === '/usuarios/me/disponibilidade-semanal') return DISPONIBILIDADE_ZERO;
    if (path.includes('statusData=ATRASADO')) {
      const total = overrides.atrasados ?? 0;
      return { itens: [], pagina: 1, tamanho: 1, totalItens: total, totalPaginas: Math.max(1, total) };
    }
    if (path.startsWith('/trilhas/trilha-1/cronogramas/') && path.includes('/blocos')) {
      return overrides.blocos ?? { itens: [], pagina: 1, tamanho: 20, totalItens: 0, totalPaginas: 1 };
    }
    if (path === '/trilhas/trilha-1') return trilha();
    return {};
  });
}

describe('Planejamento de cronogramas', () => {
  beforeEach(() => {
    requestMock.mockReset();
    mockPadrao();
  });

  it('permite selecionar qualquer modalidade, carrega existente com aviso de substituição e alterna para criação em livre', async () => {
    renderView(trilha());
    fireEvent.click(await screen.findByRole('button', { name: 'Novo cronograma' }));

    const modalidadeTrigger = await screen.findByRole('combobox', { name: 'Modalidade' });
    fireEvent.click(modalidadeTrigger);

    for (const nome of ['Sequencial', 'Ponderado', 'Manual']) {
      expect(await screen.findByRole('option', { name: nome })).not.toHaveAttribute(
        'aria-disabled',
        'true'
      );
    }

    fireEvent.click(screen.getByRole('option', { name: 'Sequencial' }));

    expect(await screen.findByRole('heading', { name: 'Editar cronograma' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Excluir cronograma' })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(
      'Já existe um cronograma Sequencial nesta trilha. Ao salvar, o existente será substituído'
    );
    expect(screen.getByLabelText('Duração do bloco (minutos)')).toHaveValue(60);

    fireEvent.click(screen.getByRole('button', { name: /Manual/i }));
    expect(await screen.findByRole('heading', { name: 'Novo cronograma' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Excluir cronograma' })).not.toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('ao editar cronograma existente de modalidade já cadastrada exige confirmação de substituição e envia PUT', async () => {
    renderView(trilha());
    fireEvent.click(await screen.findByRole('button', { name: 'Novo cronograma' }));
    fireEvent.click(await screen.findByRole('button', { name: /Sequencial/i }));

    fireEvent.click(await screen.findByRole('button', { name: 'Salvar e gerar blocos' }));

    expect(await screen.findByText('Substituir cronograma existente?')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Substituir e gerar blocos' }));

    await waitFor(() => {
      expect(requestMock).toHaveBeenCalledWith(
        '/trilhas/trilha-1/cronogramas/crono-1',
        expect.objectContaining({ method: 'PUT' })
      );
    });
  });

  it('período padrão é "Até a data da prova" apenas com dataProva futura', async () => {
    renderView(trilha({ dataProva: '2099-01-01' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Novo cronograma' }));

    const periodoTrigger = await screen.findByRole('combobox', { name: 'Período' });
    expect(periodoTrigger).toHaveTextContent('Até a data da prova');

    fireEvent.click(periodoTrigger);
    expect(screen.getByRole('option', { name: 'Até a data da prova' })).not.toHaveAttribute(
      'aria-disabled',
      'true'
    );
  });

  it('sem dataProva futura a opção "Até a data da prova" fica desabilitada e o padrão é 1 mês', async () => {
    renderView(trilha({ dataProva: '2020-01-01' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Novo cronograma' }));

    const periodoTrigger = await screen.findByRole('combobox', { name: 'Período' });
    await waitFor(() => expect(periodoTrigger).toHaveTextContent('1 mês'));

    fireEvent.click(periodoTrigger);
    expect(screen.getByRole('option', { name: 'Até a data da prova' })).toHaveAttribute(
      'aria-disabled',
      'true'
    );
  });

  it('exibe aviso de horizonte quando a prova está a mais de 366 dias', async () => {
    renderView(trilha({ dataProva: '2099-01-01' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Novo cronograma' }));

    expect(await screen.findByText(/Serão gerados os primeiros 366 dias/)).toBeInTheDocument();
  });

  it('substitui a lista de blocos pelo calendário mensal/semanal e carrega blocos do intervalo visível', async () => {
    const hoje = hojeISO();
    mockPadrao({
      blocos: {
        itens: [
          {
            id: 'b1',
            trilhaId: 'trilha-1',
            cronogramaId: 'crono-1',
            disciplinaId: 'd1',
            disciplinaNome: 'Português',
            disciplinaCor: '#111',
            data: hoje,
            ordem: 1,
            duracaoMinutos: 50,
            concluido: false,
            statusData: 'HOJE',
          },
        ],
        pagina: 1,
        tamanho: 100,
        totalItens: 1,
        totalPaginas: 1,
      },
    });

    renderView(trilha());

    expect(await screen.findByRole('group', { name: 'Calendário mensal' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Pendentes' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Concluídos' })).not.toBeInTheDocument();

    await waitFor(() => {
      const chamadas = requestMock.mock.calls.map((c) => String(c[0]));
      expect(chamadas.some((p) => p.includes('/blocos') && p.includes('dataInicio=') && p.includes('dataFim='))).toBe(true);
    });

    fireEvent.click(screen.getByRole('button', { name: 'Semanal' }));
    expect(await screen.findByLabelText('Calendário semanal')).toBeInTheDocument();
  });

  it('ao clicar no dia no calendário abre modal com Concluir (abre registro de sessão) e Estudar este bloco (vai para cronômetro), sem Nova tarefa neste dia', async () => {
    const hoje = hojeISO();
    const onIniciarEstudo = vi.fn();
    mockPadrao({
      blocos: {
        itens: [
          {
            id: 'b1',
            trilhaId: 'trilha-1',
            cronogramaId: 'crono-1',
            disciplinaId: 'd1',
            disciplinaNome: 'Português',
            disciplinaCor: '#111',
            data: hoje,
            ordem: 1,
            duracaoMinutos: 50,
            concluido: false,
            statusData: 'HOJE',
          },
          {
            id: 'b2',
            trilhaId: 'trilha-1',
            cronogramaId: 'crono-1',
            disciplinaId: 'd2',
            disciplinaNome: 'Direito',
            disciplinaCor: '#222',
            data: hoje,
            ordem: 2,
            duracaoMinutos: 50,
            concluido: true,
            statusData: 'HOJE',
          },
        ],
        pagina: 1,
        tamanho: 100,
        totalItens: 2,
        totalPaginas: 1,
      },
    });

    renderView(trilha(), { onIniciarEstudo });

    const [ano, mes, dia] = hoje.split('-').map(Number);
    const dataFormatada = new Date(ano, mes - 1, dia).toLocaleDateString('pt-BR');
    const diaCard = await screen.findByRole('button', {
      name: new RegExp(`${dataFormatada.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}, 2 blocos`),
    });
    fireEvent.click(diaCard);

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).queryByRole('button', { name: /Nova tarefa neste dia/i })).not.toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Concluir' })).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Estudar este bloco' })).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Reabrir' })).toBeInTheDocument();

    fireEvent.click(within(dialog).getByRole('button', { name: 'Estudar este bloco' }));
    expect(onIniciarEstudo).toHaveBeenCalledWith(
      'd1',
      expect.objectContaining({ blocoId: 'b1', disciplinaId: 'd1', duracaoMinutos: 50 })
    );

    // Reabre o modal do dia e clica em Concluir para abrir o GlobalSessionModal
    fireEvent.click(diaCard);
    const dialog2 = await screen.findByRole('dialog');
    fireEvent.click(within(dialog2).getByRole('button', { name: 'Concluir' }));

    expect(await screen.findByRole('dialog', { name: 'Concluir Bloco de Cronograma' })).toBeInTheDocument();
  });

  it('alternar aba de cronograma ativa automaticamente e move Excluir para dentro da edição', async () => {
    mockPadrao({
      cronogramas: [
        cronograma({ id: 'crono-1', modalidade: 'SEQUENCIAL', ativo: true }),
        cronograma({ id: 'crono-2', modalidade: 'PONDERADO', ativo: false }),
      ],
    });
    renderView(trilha());

    const tabPonderado = await screen.findByRole('tab', { name: 'Ponderado' });
    expect(screen.queryByRole('button', { name: 'Ativar' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Excluir' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Excluir cronograma' })).not.toBeInTheDocument();

    fireEvent.click(tabPonderado);
    await waitFor(() => {
      const patchAtivar = requestMock.mock.calls.find(
        (c) =>
          c[0] === '/trilhas/trilha-1/cronogramas/crono-2/ativar' &&
          (c[1] as RequestInit)?.method === 'PATCH'
      );
      expect(patchAtivar).toBeDefined();
    });

    fireEvent.click(screen.getByRole('button', { name: 'Editar cronograma' }));
    expect(await screen.findByRole('button', { name: 'Excluir cronograma' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Salvar e gerar blocos' })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Cancelar' }).length).toBeGreaterThanOrEqual(1);
  });

  it('exibe guia intuitivo das modalidades no formulário e no modal explicativo', async () => {
    renderView(trilha());

    fireEvent.click(await screen.findByRole('button', { name: 'Entender modalidades' }));
    const modalGuia = await screen.findByRole('dialog', { name: /Como funcionam as modalidades/i });
    expect(within(modalGuia).getByText('Sequencial')).toBeInTheDocument();
    expect(within(modalGuia).getByText('Ponderado')).toBeInTheDocument();
    expect(within(modalGuia).getByText('Manual')).toBeInTheDocument();
    fireEvent.click(within(modalGuia).getAllByRole('button', { name: 'Fechar' })[0]);

    fireEvent.click(screen.getByRole('button', { name: 'Novo cronograma' }));
    const grupoGuia = await screen.findByRole('group', { name: 'Explicação das modalidades de cronograma' });
    const cardManual = within(grupoGuia).getByRole('button', { name: /Manual/i });
    fireEvent.click(cardManual);
    expect(screen.getByRole('combobox', { name: 'Modalidade' })).toHaveTextContent('Manual');
  });

  it('aba exibe cronograma ativo sem pílulas soltas de Ativo ou contagem de 3 e mantém Entender modalidades na linha das abas', async () => {
    renderView(trilha());

    const tab = await screen.findByRole('tab', { name: /Sequencial · Ativo/ });
    expect(tab).toBeInTheDocument();
    expect(screen.queryByText('Ativo')).not.toBeInTheDocument();
    expect(screen.queryByText(/de 3 cronogramas/i)).not.toBeInTheDocument();

    const btnModalidades = screen.getByRole('button', { name: 'Entender modalidades' });
    expect(btnModalidades.parentElement).toContainElement(screen.getByRole('tablist'));
  });

  it('ao clicar em Novo cronograma pré-seleciona os 7 dias da semana', async () => {
    renderView(trilha());
    fireEvent.click(await screen.findByRole('button', { name: 'Novo cronograma' }));

    for (const dia of [
      'Segunda-feira',
      'Terça-feira',
      'Quarta-feira',
      'Quinta-feira',
      'Sexta-feira',
      'Sábado',
      'Domingo',
    ]) {
      expect(await screen.findByRole('checkbox', { name: dia })).toBeChecked();
    }
  });

  it('contagem de atrasados usa totalItens de statusData=ATRASADO&tamanho=1', async () => {
    mockPadrao({ atrasados: 7 });
    renderView(trilha());

    expect(
      await screen.findByRole('button', { name: 'Concluir atrasados sem sessão (7)' })
    ).toBeInTheDocument();
  });

  it('não renderiza seletor de trilha nem controles HTML nativos (select ou input date)', async () => {
    const { container } = renderView(trilha());
    await screen.findByRole('button', { name: 'Novo cronograma' });

    expect(screen.queryByLabelText(/Trilha/i)).not.toBeInTheDocument();
    expect(container.querySelector('#trilhaSelect')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Novo cronograma' }));
    await screen.findByRole('combobox', { name: 'Modalidade' });

    expect(container.querySelector('select')).toBeNull();
    expect(container.querySelector('input[type="date"]')).toBeNull();
  });

  it('permite configurar blocos por dia da semana individualmente e exibe total de horas abaixo de cada dia', async () => {
    renderView(trilha());
    fireEvent.click(await screen.findByRole('button', { name: 'Novo cronograma' }));

    const duracaoInput = await screen.findByLabelText('Duração do bloco (minutos)');
    fireEvent.change(duracaoInput, { target: { value: '50' } });

    const blocosSegunda = screen.getByLabelText('Blocos em Segunda-feira');
    fireEvent.change(blocosSegunda, { target: { value: '3' } });

    const blocosTerca = screen.getByLabelText('Blocos em Terça-feira');
    fireEvent.change(blocosTerca, { target: { value: '2' } });

    expect(screen.getByTestId('horas-dia-1')).toHaveTextContent('2h30m');
    expect(screen.getByTestId('horas-dia-2')).toHaveTextContent('1h40m');

    fireEvent.click(screen.getByLabelText('Sexta-feira'));
    expect(screen.getByTestId('horas-dia-5')).toHaveTextContent('0h');
  });

  it('envia blocosPorDiaSemana individualizados ao salvar novo cronograma', async () => {
    const getImpl = requestMock.getMockImplementation() as (
      path: string,
      opts?: RequestInit
    ) => Promise<unknown>;
    requestMock.mockImplementation((path: string, opts?: RequestInit) => {
      if (opts?.method === 'POST' && path === '/trilhas/trilha-1/cronogramas') {
        return Promise.resolve({
          id: 'c2',
          trilhaId: 'trilha-1',
          modalidade: 'PONDERADO',
          periodo: 'UM_MES',
          status: 'RASCUNHO',
          duracaoBlocoMinutos: 50,
          blocosPorDia: 3,
          diasAtivosSemana: [1, 2],
          blocosPorDiaSemana: { 1: 3, 2: 1 },
          dataInicio: '2026-04-01',
          dataFim: '2026-05-01',
          dataPublicacao: null,
        });
      }
      return getImpl(path, opts);
    });

    renderView(trilha());
    fireEvent.click(await screen.findByRole('button', { name: 'Novo cronograma' }));

    fireEvent.change(await screen.findByLabelText('Duração do bloco (minutos)'), {
      target: { value: '50' },
    });
    fireEvent.change(screen.getByLabelText('Blocos em Segunda-feira'), {
      target: { value: '3' },
    });
    fireEvent.change(screen.getByLabelText('Blocos em Terça-feira'), {
      target: { value: '1' },
    });

    fireEvent.click(screen.getByRole('button', { name: 'Criar cronograma' }));

    await waitFor(() => {
      const postCall = requestMock.mock.calls.find(
        (c) => c[0] === '/trilhas/trilha-1/cronogramas' && (c[1] as RequestInit)?.method === 'POST'
      );
      expect(postCall).toBeDefined();
      const body = JSON.parse(String((postCall![1] as RequestInit).body));
      expect(body.blocosPorDiaSemana['1']).toBe(3);
      expect(body.blocosPorDiaSemana['2']).toBe(1);
    });
  });
});
