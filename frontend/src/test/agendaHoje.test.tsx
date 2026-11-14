import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { AgendaHoje, AgendaHojeProps } from '../shared/agenda/AgendaHoje';
import { AgendaHojeDerivado } from '../shared/agenda/agendaHojeService';
import { BlocoHojeDTO, TarefaDTO } from '../shared/contracts/domainContracts';

function tarefa(id: string, extras: Partial<TarefaDTO> = {}): TarefaDTO {
  return { id, titulo: `Tarefa ${id}`, prioridade: 'MEDIA', concluida: false, ...extras };
}

function bloco(id: string, extras: Partial<BlocoHojeDTO> = {}): BlocoHojeDTO {
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
    concluido: false,
    ...extras
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
    erroTarefas: null,
    erroBlocos: null,
    vazioTotal: true
  };
}

function propsBase(overrides: Partial<AgendaHojeProps> = {}): AgendaHojeProps {
  return {
    modo: 'tarefas',
    dados: agendaVazia(),
    onAlternarTarefa: vi.fn(),
    onConcluirBloco: vi.fn(),
    onRetryTarefas: vi.fn(),
    onRetryBlocos: vi.fn(),
    ...overrides
  };
}

describe('AgendaHoje', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('exibe os cabeçalhos h2 das três seções e os subgrupos de Hoje', () => {
    render(<AgendaHoje {...propsBase()} />);

    expect(screen.getByRole('heading', { level: 2, name: /atrasadas/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: /^hoje/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: /sem data/i })).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { level: 3, name: /blocos de estudo/i })
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 3, name: /^tarefas/i })).toBeInTheDocument();
  });

  it('tudoVazioExibeEmptyStateTudoEmDiaPorHoje', () => {
    render(<AgendaHoje {...propsBase()} />);

    expect(screen.getByText('Tudo em dia por hoje')).toBeInTheDocument();
    expect(screen.getByText('Nenhuma tarefa ou bloco pendente para hoje.')).toBeInTheDocument();
  });

  it('exibe skeleton row após 200 ms por seção no carregamento inicial', () => {
    vi.useFakeTimers();
    render(
      <AgendaHoje
        {...propsBase({
          dados: {
            ...agendaVazia(),
            status: 'carregando',
            statusTarefas: 'carregando',
            statusBlocos: 'carregando',
            vazioTotal: false
          }
        })}
      />
    );

    expect(screen.queryByTestId('skeleton')).not.toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(210);
    });
    expect(screen.getAllByTestId('skeleton').length).toBeGreaterThanOrEqual(1);
  });

  it('erroDeBlocosRenderizaSectionErrorCardInlineSomenteNoSubgrupoBlocos', () => {
    render(
      <AgendaHoje
        {...propsBase({
          dados: {
            ...agendaVazia(),
            vazioTotal: false,
            statusBlocos: 'erro',
            erroBlocos: new Error('Falha de rede'),
            tarefasHoje: [tarefa('hoje-1', { dataVencimento: '2026-11-09' })]
          }
        })}
      />
    );

    const cartoes = screen.getAllByTestId('section-error-card');
    expect(cartoes).toHaveLength(1);
    expect(cartoes[0]).toHaveAttribute('data-variant', 'inline');
    // A fonte sadia continua renderizada.
    expect(screen.getByText('Tarefa hoje-1')).toBeInTheDocument();
  });

  it('erroDeTarefasRenderizaInlineEmAtrasadasTarefasDeHojeESemData', () => {
    render(
      <AgendaHoje
        {...propsBase({
          dados: {
            ...agendaVazia(),
            vazioTotal: false,
            statusTarefas: 'erro',
            erroTarefas: new Error('Falha de rede'),
            blocosHoje: [bloco('bloco-1')]
          }
        })}
      />
    );

    expect(screen.getAllByTestId('section-error-card')).toHaveLength(3);
    expect(screen.getByText('Direito Penal')).toBeInTheDocument();
  });

  it('retry recarrega somente a fonte com falha', () => {
    const onRetryTarefas = vi.fn();
    const onRetryBlocos = vi.fn();

    render(
      <AgendaHoje
        {...propsBase({
          onRetryTarefas,
          onRetryBlocos,
          dados: {
            ...agendaVazia(),
            vazioTotal: false,
            statusBlocos: 'erro',
            erroBlocos: new Error('Falha de rede')
          }
        })}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: /tentar novamente/i }));

    expect(onRetryBlocos).toHaveBeenCalledTimes(1);
    expect(onRetryTarefas).not.toHaveBeenCalled();
  });

  it('concluirTarefaManualNaoAbreModalEChamaAcaoDeTarefas', () => {
    const onAlternarTarefa = vi.fn();
    const onConcluirBloco = vi.fn();

    render(
      <AgendaHoje
        {...propsBase({
          onAlternarTarefa,
          onConcluirBloco,
          dados: {
            ...agendaVazia(),
            vazioTotal: false,
            tarefasHoje: [tarefa('hoje-1', { dataVencimento: '2026-11-09' })]
          }
        })}
      />
    );

    fireEvent.click(screen.getByRole('checkbox', { name: /concluir tarefa tarefa hoje-1/i }));

    expect(onAlternarTarefa).toHaveBeenCalledWith('hoje-1', true);
    expect(onConcluirBloco).not.toHaveBeenCalled();
  });

  it('concluirBlocoChamaHandlerDeBlocoDireto', () => {
    const onConcluirBloco = vi.fn();

    render(
      <AgendaHoje
        {...propsBase({
          onConcluirBloco,
          dados: { ...agendaVazia(), vazioTotal: false, blocosHoje: [bloco('bloco-1')] }
        })}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: /concluir bloco de direito penal/i }));

    expect(onConcluirBloco).toHaveBeenCalledTimes(1);
    expect(onConcluirBloco.mock.calls[0][0].blocoId).toBe('bloco-1');
  });

  it('atrasadasAgrupadasExibemExpansorDeOcorrenciasAnteriores', async () => {
    const principal = tarefa('s-2', { serieId: 'serie', dataVencimento: '2026-11-05' });
    const anterior = tarefa('s-1', { serieId: 'serie', dataVencimento: '2026-11-01' });

    render(
      <AgendaHoje
        {...propsBase({
          dados: {
            ...agendaVazia(),
            vazioTotal: false,
            atrasadas: [principal, anterior],
            atrasadasAgrupadas: [{ principal, anteriores: [anterior] }]
          }
        })}
      />
    );

    expect(screen.queryByText('Tarefa s-1')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /1 ocorrência\(s\) anterior/i }));

    await waitFor(() => {
      expect(screen.getByText('Tarefa s-1')).toBeInTheDocument();
    });
  });

  it('modoPomodoroExpoeDefinirComoFocoSemAcoesDeEdicao', () => {
    const onDefinirFoco = vi.fn();

    render(
      <AgendaHoje
        {...propsBase({
          modo: 'pomodoro',
          onDefinirFoco,
          dados: {
            ...agendaVazia(),
            vazioTotal: false,
            tarefasHoje: [tarefa('hoje-1', { dataVencimento: '2026-11-09' })],
            blocosHoje: [bloco('bloco-1')]
          }
        })}
      />
    );

    // Densidade compacta não expõe ações de editar/excluir/pular.
    expect(screen.queryByRole('button', { name: /editar tarefa/i })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Tarefa hoje-1' }));
    expect(onDefinirFoco).toHaveBeenCalledWith({ tipo: 'TAREFA', id: 'hoje-1' });

    fireEvent.click(screen.getByRole('button', { name: 'Definir como foco' }));
    expect(onDefinirFoco).toHaveBeenCalledWith({ tipo: 'BLOCO', id: 'bloco-1' });
  });
});
