import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import {
  GlobalSessionModalProvider,
  ConfirmProvider,
  ToastProvider
} from '../shared/design-system';
import * as apiModule from '../modules/estudos/services/api';
import { PlanejamentoView } from '../modules/estudos/views/PlanejamentoView';
import type { TrilhaDetalheDTO } from '../modules/estudos/types/trilhara';

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

function renderView(trilhaProp: TrilhaDetalheDTO) {
  return render(
    <ToastProvider>
      <ConfirmProvider>
        <GlobalSessionModalProvider>
          <PlanejamentoView trilha={trilhaProp} />
        </GlobalSessionModalProvider>
      </ConfirmProvider>
    </ToastProvider>
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
    if (path.startsWith('/trilhas/trilha-1/cronogramas/crono-1/blocos')) {
      return overrides.blocos ?? { itens: [], pagina: 1, tamanho: 20, totalItens: 0, totalPaginas: 1 };
    }
    if (path === '/trilhas/trilha-1') return trilha();
    return {};
  });
}

describe('R5 — Planejamento de cronogramas (D-19/D-20/D-17f)', () => {
  beforeEach(() => {
    requestMock.mockReset();
    mockPadrao();
  });

  it('modalidade já usada fica desabilitada com tooltip da trilha', async () => {
    renderView(trilha());
    fireEvent.click(await screen.findByRole('button', { name: 'Novo cronograma' }));

    const opcao = await screen.findByRole('option', { name: 'Sequencial' });
    expect(opcao).toBeDisabled();
    expect(opcao).toHaveAttribute('title', 'Esta trilha já possui um cronograma Sequencial');
  });

  it('período padrão é "Até a data da prova" apenas com dataProva futura', async () => {
    renderView(trilha({ dataProva: '2099-01-01' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Novo cronograma' }));

    const periodo = (await screen.findByLabelText('Período')) as HTMLSelectElement;
    expect(periodo.value).toBe('ATE_PROVA');
    expect(screen.getByRole('option', { name: 'Até a data da prova' })).toBeEnabled();
  });

  it('sem dataProva futura a opção "Até a data da prova" fica desabilitada e o padrão é 1 mês', async () => {
    renderView(trilha({ dataProva: '2020-01-01' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Novo cronograma' }));

    const periodo = (await screen.findByLabelText('Período')) as HTMLSelectElement;
    await waitFor(() => expect(periodo.value).toBe('UM_MES'));
    expect(screen.getByRole('option', { name: 'Até a data da prova' })).toBeDisabled();
  });

  it('exibe aviso de horizonte quando a prova está a mais de 366 dias', async () => {
    renderView(trilha({ dataProva: '2099-01-01' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Novo cronograma' }));

    expect(await screen.findByText(/Serão gerados os primeiros 366 dias/)).toBeInTheDocument();
  });

  it('seletor Pendentes/Concluídos alterna o filtro enviado ao backend', async () => {
    renderView(trilha());

    const concluidos = await screen.findByRole('button', { name: 'Concluídos' });
    fireEvent.click(concluidos);

    await waitFor(() => {
      const chamadas = requestMock.mock.calls.map((c) => String(c[0]));
      expect(chamadas.some((p) => p.includes('concluido=true'))).toBe(true);
    });
  });

  it('mostra a aba do cronograma com badge Ativo', async () => {
    renderView(trilha());

    expect(await screen.findByRole('tab', { name: /Sequencial · Ativo/ })).toBeInTheDocument();
    expect(screen.getByText('Ativo')).toBeInTheDocument();
  });

  it('contagem de atrasados usa totalItens de statusData=ATRASADO&tamanho=1', async () => {
    mockPadrao({ atrasados: 7 });
    renderView(trilha());

    expect(
      await screen.findByRole('button', { name: 'Concluir atrasados sem sessão (7)' })
    ).toBeInTheDocument();
  });
});
