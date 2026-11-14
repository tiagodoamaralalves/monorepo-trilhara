import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { HubView } from '../modules/estudos/views/HubView';
import type { TrilhaResumoDTO } from '../modules/estudos/types/trilhara';

const trilha: TrilhaResumoDTO = {
  id: 't-1',
  nome: 'Receita Federal',
  cargo: 'Auditor Fiscal',
  banca: 'FGV',
  dataProva: '2026-12-01',
  totalDisciplinas: 3,
  totalTopicos: 30,
  topicosConcluidos: 9,
  percentualProgresso: 30
};

function montarProps(overrides: Partial<React.ComponentProps<typeof HubView>> = {}) {
  return {
    trilhas: undefined as TrilhaResumoDTO[] | undefined,
    status: 'sucesso' as const,
    trilhaAtivaId: undefined,
    onSelecionarTrilha: vi.fn(),
    onNavegar: vi.fn(),
    onAbrirNovaTrilha: vi.fn(),
    onEditarTrilha: vi.fn(),
    onRecarregar: vi.fn(),
    ...overrides
  };
}

/**
 * Hub: estados de `Skeleton card` na primeira carga, `EmptyState` só com 2xx vazio e
 * `SectionErrorCard` com retry em falha recuperável.
 */
describe('Hub — estados de carregamento, vazio e erro', () => {
  it('exibe skeleton de cards durante a primeira carga', async () => {
    render(<HubView {...montarProps({ status: 'carregando' })} />);

    // Skeleton só aparece após 200 ms (useDelayedFlag) e cada card é um Skeleton próprio.
    await waitFor(() => {
      expect(screen.getAllByTestId('skeleton')).toHaveLength(3);
    }, { timeout: 3000 });
    expect(screen.getByTestId('hub-skeleton')).toBeInTheDocument();
    expect(screen.queryByTestId('empty-state')).not.toBeInTheDocument();
  });

  it('lista cards com os contadores do resumo quando há trilhas', () => {
    render(<HubView {...montarProps({ trilhas: [trilha] })} />);

    expect(screen.getByText('Receita Federal')).toBeInTheDocument();
    expect(screen.getByText('30% (9/30)')).toBeInTheDocument();
    expect(screen.queryByTestId('empty-state')).not.toBeInTheDocument();
  });

  it('exibe progresso por disciplinas quando a trilha não possui tópicos ou é mista', () => {
    const semTopicos: TrilhaResumoDTO = {
      ...trilha,
      id: 't-sem',
      nome: 'Trilha Sem Tópicos',
      totalDisciplinas: 10,
      disciplinasConcluidas: 9,
      temDisciplinaSemTopico: true,
      totalTopicos: 0,
      topicosConcluidos: 0,
      percentualProgresso: 90
    };
    const mista: TrilhaResumoDTO = {
      ...trilha,
      id: 't-mista',
      nome: 'Trilha Mista',
      totalDisciplinas: 11,
      disciplinasConcluidas: 1,
      temDisciplinaSemTopico: true,
      totalTopicos: 20,
      topicosConcluidos: 20,
      percentualProgresso: 33.3
    };

    render(<HubView {...montarProps({ trilhas: [semTopicos, mista] })} />);

    expect(screen.getByText('90% (9/10 disc.)')).toBeInTheDocument();
    expect(screen.getByText('33% (1/11 disc. · 20/20 ass.)')).toBeInTheDocument();
  });

  it('exibe EmptyState "Crie sua primeira trilha" com ação primária quando a resposta é vazia', () => {
    const props = montarProps({ trilhas: [] });
    render(<HubView {...props} />);

    expect(screen.getByTestId('empty-state')).toBeInTheDocument();
    expect(screen.getByText('Crie sua primeira trilha')).toBeInTheDocument();
    const vazio = screen.getByTestId('empty-state');
    fireEvent.click(within(vazio).getByRole('button', { name: /Nova trilha/i }));
    expect(props.onAbrirNovaTrilha).toHaveBeenCalled();
  });

  it('exibe SectionErrorCard com retry quando a listagem falha', () => {
    const props = montarProps({ status: 'erro' });
    render(<HubView {...props} />);

    expect(screen.getByTestId('section-error-card')).toBeInTheDocument();
    expect(screen.getByText('Não foi possível carregar')).toBeInTheDocument();
    expect(screen.getByText('Verifique sua conexão e tente novamente.')).toBeInTheDocument();
    expect(screen.queryByTestId('empty-state')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Tentar novamente/i }));
    expect(props.onRecarregar).toHaveBeenCalledTimes(1);
  });

  it('nunca renderiza EmptyState antes da primeira resposta', () => {
    render(<HubView {...montarProps({ status: 'carregando' })} />);
    expect(screen.queryByTestId('empty-state')).not.toBeInTheDocument();
  });
});
