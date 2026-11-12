import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  ConfirmProvider,
  GlobalSessionModalProvider,
  ToastProvider
} from '../shared/design-system';
import { DashboardView } from '../modules/estudos/views/DashboardView';
import type { TrilhaDetalheDTO } from '../modules/estudos/types/trilhara';
import { EstudosService } from '../modules/estudos/services/estudosService';

const trilha: TrilhaDetalheDTO = {
  id: 't-1',
  nome: 'Receita Federal',
  cargo: 'Auditor',
  banca: 'FGV',
  dataProva: '2026-12-01',
  disciplinas: [
    { id: 'd-1', nome: 'Português', cor: '#4A6B82', peso: 1, ordem: 0, topicos: [] }
  ],
  topicosConcluidosIds: []
};

function estatisticas(overrides: Record<string, unknown> = {}) {
  return {
    totalSegundos: 3600,
    totalSessoes: 2,
    totalQuestoes: 10,
    totalAcertos: 8,
    totalErros: 2,
    percentualAcertoGlobal: 80,
    porDisciplina: [
      {
        disciplinaId: 'd-1',
        nome: 'Português',
        cor: '#4A6B82',
        disciplinaRemovida: false,
        totalSegundos: 3600,
        totalSessoes: 2,
        totalQuestoes: 10,
        totalAcertos: 8,
        totalErros: 2,
        percentualAcerto: 80
      }
    ],
    serieTemporal: [{ data: '2026-11-12', totalSegundos: 3600, totalQuestoes: 10, totalAcertos: 8 }],
    streakDias: 2,
    granularidade: 'DIA',
    ...overrides
  };
}

function paginaSessoes(overrides: Record<string, unknown> = {}) {
  return {
    itens: [
      {
        id: 's-2',
        trilhaId: 't-1',
        disciplinaId: 'd-1',
        disciplinaNome: 'Português',
        disciplinaCor: '#4A6B82',
        disciplinaRemovida: false,
        dataSessao: '2026-11-12',
        duracaoSegundos: 1800,
        questoes: 5,
        acertos: 4,
        erros: 1,
        percentualAcerto: 80,
        editavel: true
      }
    ],
    pagina: 1,
    tamanho: 20,
    total: 2,
    totalPaginas: 1,
    ...overrides
  };
}

function renderizar() {
  return render(
    <ToastProvider>
      <ConfirmProvider>
        <GlobalSessionModalProvider>
          <DashboardView trilha={trilha} onIrParaDisciplinas={vi.fn()} />
        </GlobalSessionModalProvider>
      </ConfirmProvider>
    </ToastProvider>
  );
}

describe('R4 — Dashboard de Estudos: estados, grupo removido e D-38', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('exibe Skeleton chart + row na primeira carga, nunca EmptyState antes da resposta', async () => {
    vi.useFakeTimers();
    vi.spyOn(EstudosService, 'obterEstatisticas').mockReturnValue(new Promise(() => {}) as never);
    vi.spyOn(EstudosService, 'listarSessoesPaginado').mockReturnValue(new Promise(() => {}) as never);

    renderizar();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(250);
    });

    expect(screen.getByTestId('dashboard-skeleton')).toBeInTheDocument();
    expect(screen.queryByTestId('empty-state')).not.toBeInTheDocument();
    expect(screen.queryByTestId('section-error-card')).not.toBeInTheDocument();
  });

  it('exibe SectionErrorCard com retry quando o carregamento falha', async () => {
    vi.spyOn(EstudosService, 'obterEstatisticas').mockRejectedValue(new Error('falhou'));
    vi.spyOn(EstudosService, 'listarSessoesPaginado').mockRejectedValue(new Error('falhou'));

    renderizar();

    expect(await screen.findByTestId('section-error-card')).toBeInTheDocument();
    expect(screen.getByText('Não foi possível carregar')).toBeInTheDocument();
    expect(screen.getByText('Verifique sua conexão e tente novamente.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Tentar novamente/i })).toBeInTheDocument();
    expect(screen.queryByTestId('empty-state')).not.toBeInTheDocument();
  });

  it('exibe EmptyState "Nenhuma sessão neste período" com ação "Registrar sessão manual"', async () => {
    vi.spyOn(EstudosService, 'obterEstatisticas').mockResolvedValue(estatisticas({
      totalSegundos: 0,
      totalSessoes: 0,
      totalQuestoes: 0,
      totalAcertos: 0,
      totalErros: 0,
      percentualAcertoGlobal: 0,
      porDisciplina: [],
      serieTemporal: [],
      streakDias: 0
    }) as never);
    vi.spyOn(EstudosService, 'listarSessoesPaginado').mockResolvedValue(
      paginaSessoes({ itens: [], total: 0 }) as never
    );

    renderizar();

    expect(await screen.findByTestId('empty-state')).toBeInTheDocument();
    expect(screen.getByText('Nenhuma sessão neste período')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Registrar sessão manual/i })).toBeInTheDocument();
  });

  it('agrupa disciplina removida com badge de aviso e pede confirmação com contagem e tempo', async () => {
    vi.spyOn(EstudosService, 'obterEstatisticas').mockResolvedValue(estatisticas({
      porDisciplina: [
        {
          disciplinaId: 'd-removida',
          nome: 'Estatística',
          cor: '#D97706',
          disciplinaRemovida: true,
          totalSegundos: 5400,
          totalSessoes: 3,
          totalQuestoes: 0,
          totalAcertos: 0,
          totalErros: 0,
          percentualAcerto: 0
        }
      ]
    }) as never);
    vi.spyOn(EstudosService, 'listarSessoesPaginado').mockResolvedValue(paginaSessoes() as never);
    const excluirSpy = vi.spyOn(EstudosService, 'excluirSessoesDisciplina').mockResolvedValue(
      { sessoesRemovidas: 3, totalSegundosRemovidos: 5400 } as never
    );

    renderizar();

    expect((await screen.findAllByText('Disciplina removida')).length).toBeGreaterThan(0);

    await userEvent.click(screen.getByRole('button', { name: /Apagar histórico desta disciplina/i }));

    const dialogo = await screen.findByRole('dialog');
    expect(dialogo.textContent).toContain('3 sessões');
    expect(dialogo.textContent).toContain('1 h 30 min');
    expect(excluirSpy).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: /Cancelar/i }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(excluirSpy).not.toHaveBeenCalled();
  });

  it('confirma exclusão de sessão individual com o texto D-38', async () => {
    vi.spyOn(EstudosService, 'obterEstatisticas').mockResolvedValue(estatisticas() as never);
    vi.spyOn(EstudosService, 'listarSessoesPaginado').mockResolvedValue(paginaSessoes() as never);
    const excluirSpy = vi.spyOn(EstudosService, 'excluirSessao').mockResolvedValue(undefined as never);

    renderizar();

    await userEvent.click(await screen.findByRole('button', { name: /Excluir/i }));

    const dialogo = await screen.findByRole('dialog');
    expect(dialogo.textContent).toContain('Esta ação não pode ser desfeita.');
    expect(excluirSpy).not.toHaveBeenCalled();
  });
});
