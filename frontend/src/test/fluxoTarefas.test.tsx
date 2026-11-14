import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import type { TarefaDTO } from '@shared/contracts/domainContracts';
import { TarefaAgendaItem } from '@shared/agenda/TarefaAgendaItem';
import type { Tarefa } from '../modules/tarefas/types/tarefa';
import { TarefaService } from '../modules/tarefas/services/tarefaService';
import {
  autoOrganizarPosicoes,
  construirGrafoFluxo,
  resolverConexaoPorHandles
} from '../modules/tarefas/components/FluxoTarefas/fluxoLayout';
import { FluxoTarefasModal } from '../modules/tarefas/components/FluxoTarefas/FluxoTarefasModal';
import { FluxoTarefasView } from '../modules/tarefas/components/FluxoTarefas/FluxoTarefasView';

vi.mock('@shared/auth/api', () => ({
  request: vi.fn()
}));

import { request } from '@shared/auth/api';

class MockResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = MockResizeObserver as unknown as typeof ResizeObserver;
}

function criarTarefa(id: string, titulo: string, extra: Partial<Tarefa> = {}): Tarefa {
  return {
    id,
    titulo,
    prioridade: 'MEDIA',
    concluida: false,
    noFluxo: false,
    sucessorasIds: [],
    antecessorasIds: [],
    paresIds: [],
    posicaoFluxo: null,
    ...extra
  };
}

describe('Fluxo de Tarefas — layout, serviço e UI', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('fluxoLayout helpers', () => {
    it('alinha pares na mesma coluna vertical e avança sucessoras para a direita', () => {
      const t1 = criarTarefa('t1', 'Revisar teoria', {
        noFluxo: true,
        sucessorasIds: ['t2', 't3']
      });
      const t2 = criarTarefa('t2', 'Resolver questões', {
        noFluxo: true,
        antecessorasIds: ['t1'],
        paresIds: ['t3']
      });
      const t3 = criarTarefa('t3', 'Fazer flashcards', {
        noFluxo: true,
        antecessorasIds: ['t1'],
        paresIds: ['t2']
      });
      const t4 = criarTarefa('t4', 'Simulado final', {
        noFluxo: true,
        antecessorasIds: ['t2']
      });

      t2.sucessorasIds = ['t4'];

      const posicoes = autoOrganizarPosicoes([t1, t2, t3, t4]);

      expect(posicoes.t1.x).toBeLessThan(posicoes.t2.x);
      expect(posicoes.t2.x).toBe(posicoes.t3.x);
      expect(posicoes.t2.y).not.toBe(posicoes.t3.y);
      expect(posicoes.t2.x).toBeLessThan(posicoes.t4.x);
    });

    it('deduplica arestas simétricas de pares ao construir o grafo', () => {
      const t1 = criarTarefa('t1', 'Tarefa A', { noFluxo: true, paresIds: ['t2'], sucessorasIds: ['t3'] });
      const t2 = criarTarefa('t2', 'Tarefa B', { noFluxo: true, paresIds: ['t1'] });
      const t3 = criarTarefa('t3', 'Tarefa C', { noFluxo: true, antecessorasIds: ['t1'] });

      const { nodes, edges } = construirGrafoFluxo({
        tarefas: [t1, t2, t3],
        modoBolinhaForcado: false,
        tarefaFocadaId: null,
        listasPorId: new Map(),
        onAlternarConclusao: vi.fn(),
        onAbrirConexoes: vi.fn(),
        onRemoverDoFluxo: vi.fn(),
        onDesconectar: vi.fn()
      });

      expect(nodes).toHaveLength(3);
      expect(edges.filter((e) => e.data?.tipo === 'PAR')).toHaveLength(1);
      expect(edges.filter((e) => e.data?.tipo === 'SUCESSORA')).toHaveLength(1);
    });

    it('resolve semântica dos handles corretamente', () => {
      expect(resolverConexaoPorHandles('a', 'b', 'par-base', 'par-topo')).toEqual({
        origemId: 'a',
        alvoId: 'b',
        tipo: 'PAR'
      });
      expect(resolverConexaoPorHandles('a', 'b', 'antecessora', 'sucessora')).toEqual({
        origemId: 'a',
        alvoId: 'b',
        tipo: 'ANTECESSORA'
      });
      expect(resolverConexaoPorHandles('a', 'b', 'sucessora', 'antecessora')).toEqual({
        origemId: 'a',
        alvoId: 'b',
        tipo: 'SUCESSORA'
      });
      expect(resolverConexaoPorHandles('a', 'a', 'sucessora', 'antecessora')).toBeNull();
    });
  });

  describe('TarefaService fluxo APIs', () => {
    it('chama endpoints corretos de fluxo com métodos e payloads esperados', async () => {
      const mockedRequest = vi.mocked(request);
      mockedRequest.mockResolvedValueOnce([{ id: 't1', titulo: 'T1', prioridade: 'MEDIA', concluida: false, noFluxo: true }]);

      const lista = await TarefaService.listarFluxo();
      expect(mockedRequest).toHaveBeenCalledWith('/tarefas/fluxo');
      expect(lista[0].noFluxo).toBe(true);

      mockedRequest.mockResolvedValueOnce([]);
      await TarefaService.conectarNoFluxo('t1', 't2', 'PAR');
      expect(mockedRequest).toHaveBeenCalledWith('/tarefas/t1/fluxo/conexoes', {
        method: 'POST',
        body: JSON.stringify({ tarefaAlvoId: 't2', tipo: 'PAR' })
      });

      mockedRequest.mockResolvedValueOnce([]);
      await TarefaService.desconectarNoFluxo('t1', 't2');
      expect(mockedRequest).toHaveBeenCalledWith('/tarefas/t1/fluxo/conexoes/t2', {
        method: 'DELETE'
      });

      mockedRequest.mockResolvedValueOnce({ id: 't1', titulo: 'T1', prioridade: 'MEDIA', concluida: false });
      await TarefaService.atualizarPosicaoFluxo('t1', 120, 240);
      expect(mockedRequest).toHaveBeenCalledWith('/tarefas/t1/fluxo/posicao', {
        method: 'PATCH',
        body: JSON.stringify({ x: 120, y: 240 })
      });

      mockedRequest.mockResolvedValueOnce([]);
      await TarefaService.atualizarPosicoesFluxoEmLote([{ tarefaId: 't1', x: 10, y: 20 }]);
      expect(mockedRequest).toHaveBeenCalledWith('/tarefas/fluxo/posicoes', {
        method: 'PATCH',
        body: JSON.stringify({ posicoes: [{ tarefaId: 't1', x: 10, y: 20 }] })
      });
    });
  });

  describe('TarefaAgendaItem pílula de rota', () => {
    it('exibe pílula com contagem quando conectada e não dispara onEditar ao clicar', () => {
      const onEditar = vi.fn();
      const onAbrirFluxo = vi.fn();
      const dto: TarefaDTO = {
        id: 't1',
        titulo: 'Estudar Direito Constitucional',
        prioridade: 'ALTA',
        concluida: false,
        noFluxo: true,
        sucessorasIds: ['t2'],
        paresIds: ['t3']
      };

      render(
        <TarefaAgendaItem
          tarefa={dto}
          onAlternar={vi.fn()}
          onEditar={onEditar}
          onAbrirFluxo={onAbrirFluxo}
        />
      );

      const flowButton = screen.getByRole('button', {
        name: /Fluxo da tarefa Estudar Direito Constitucional: 2 conexões/i
      });
      expect(flowButton).toHaveTextContent('2');

      fireEvent.click(flowButton);
      expect(onAbrirFluxo).toHaveBeenCalledWith(dto);
      expect(onEditar).not.toHaveBeenCalled();
    });

    it('não renderiza pílula de fluxo quando onAbrirFluxo não é fornecido', () => {
      const dto: TarefaDTO = {
        id: 't1',
        titulo: 'Somente Pomodoro',
        prioridade: 'BAIXA',
        concluida: false
      };

      render(<TarefaAgendaItem tarefa={dto} onAlternar={vi.fn()} />);
      expect(screen.queryByRole('button', { name: /fluxo/i })).not.toBeInTheDocument();
    });
  });

  describe('FluxoTarefasModal', () => {
    it('no modo conectar lista conexões existentes, permite selecionar múltiplas tarefas, conectar e desconectar', async () => {
      const onConectarMultiplas = vi.fn().mockResolvedValue(undefined);
      const onDesconectar = vi.fn().mockResolvedValue(undefined);
      const onAbrirTelaFluxo = vi.fn();

      const t1 = criarTarefa('t1', 'Passo 1', { noFluxo: true, paresIds: ['t2'] });
      const t2 = criarTarefa('t2', 'Passo paralelo', { noFluxo: true, paresIds: ['t1'] });
      const t3 = criarTarefa('t3', 'Passo seguinte');
      const t4 = criarTarefa('t4', 'Passo extra');

      render(
        <FluxoTarefasModal
          isOpen
          modo="conectar"
          tarefa={t1}
          todasTarefas={[t1, t2, t3, t4]}
          onClose={vi.fn()}
          onConectarMultiplas={onConectarMultiplas}
          onDesconectar={onDesconectar}
          onAbrirTelaFluxo={onAbrirTelaFluxo}
        />
      );

      expect(screen.getByText('Passo paralelo')).toBeInTheDocument();
      expect(screen.getByText('Par')).toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: /Sucessora/i }));
      fireEvent.click(screen.getByRole('checkbox', { name: 'Passo seguinte' }));
      fireEvent.click(screen.getByRole('checkbox', { name: 'Passo extra' }));
      fireEvent.click(screen.getByRole('button', { name: /Conectar \(2\)/i }));

      await waitFor(() => {
        expect(onConectarMultiplas).toHaveBeenCalledWith('t1', ['t3', 't4'], 'SUCESSORA');
      });

      fireEvent.click(screen.getByRole('button', { name: /Desconectar Passo paralelo/i }));
      await waitFor(() => {
        expect(onDesconectar).toHaveBeenCalledWith('t1', 't2');
      });

      fireEvent.click(screen.getByRole('button', { name: /Abrir na Tela de Fluxo/i }));
      expect(onAbrirTelaFluxo).toHaveBeenCalledWith('t1');
    });

    it('no modo trazer permite selecionar todas visíveis e enviar múltiplas tarefas', async () => {
      const onAdicionarMultiplas = vi.fn().mockResolvedValue(undefined);
      const t1 = criarTarefa('t1', 'Leitura inicial');
      const t2 = criarTarefa('t2', 'Resumo prático');
      const t3 = criarTarefa('t3', 'Já no fluxo', { noFluxo: true });

      render(
        <FluxoTarefasModal
          isOpen
          modo="trazer"
          todasTarefas={[t1, t2, t3]}
          onClose={vi.fn()}
          onAdicionarMultiplas={onAdicionarMultiplas}
        />
      );

      expect(screen.queryByRole('checkbox', { name: 'Já no fluxo' })).not.toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: /Selecionar visíveis/i }));
      fireEvent.click(screen.getByRole('button', { name: /Trazer para o fluxo \(2\)/i }));

      await waitFor(() => {
        expect(onAdicionarMultiplas).toHaveBeenCalledWith(['t1', 't2']);
      });
    });
  });

  describe('FluxoTarefasView', () => {
    it('alterna entre cartões e bolinhas de check e permite auto-organizar', async () => {
      const onAtualizarPosicoesEmLote = vi.fn().mockResolvedValue(undefined);
      const t1 = criarTarefa('t1', 'Base teórica', {
        noFluxo: true,
        concluida: true,
        sucessorasIds: ['t2'],
        posicaoFluxo: { x: 80, y: 80 }
      });
      const t2 = criarTarefa('t2', 'Lista de exercícios', {
        noFluxo: true,
        antecessorasIds: ['t1'],
        posicaoFluxo: { x: 400, y: 80 }
      });

      render(
        <FluxoTarefasView
          todasTarefas={[t1, t2]}
          listas={[]}
          tarefaFocadaId={null}
          onAlternarConclusao={vi.fn()}
          onEditarTarefa={vi.fn()}
          onAbrirConexoes={vi.fn()}
          onAdicionarVariasAoFluxo={vi.fn().mockResolvedValue(undefined)}
          onRemoverDoFluxo={vi.fn().mockResolvedValue(undefined)}
          onConectar={vi.fn().mockResolvedValue(undefined)}
          onDesconectar={vi.fn().mockResolvedValue(undefined)}
          onAtualizarPosicao={vi.fn().mockResolvedValue(undefined)}
          onAtualizarPosicoesEmLote={onAtualizarPosicoesEmLote}
        />
      );

      expect(screen.getByTestId('flow-node-card-t1')).toBeInTheDocument();
      expect(screen.getByTestId('flow-node-card-t2')).toBeInTheDocument();
      expect(document.querySelector('.react-flow__minimap')).toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: /Bolinhas/i }));
      expect(screen.getByTestId('flow-node-dot-t1')).toBeInTheDocument();
      expect(screen.getByTestId('flow-node-dot-t2')).toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: /Auto-organizar/i }));
      await waitFor(() => {
        expect(onAtualizarPosicoesEmLote).toHaveBeenCalledTimes(1);
      });
    });

    it('exibe estado vazio quando nenhuma tarefa está no fluxo e permite selecionar várias tarefas para trazer', async () => {
      const onAdicionarVariasAoFluxo = vi.fn().mockResolvedValue(undefined);
      const t1 = criarTarefa('t1', 'Tarefa fora do fluxo A');
      const t2 = criarTarefa('t2', 'Tarefa fora do fluxo B');

      render(
        <FluxoTarefasView
          todasTarefas={[t1, t2]}
          listas={[]}
          tarefaFocadaId={null}
          onAlternarConclusao={vi.fn()}
          onEditarTarefa={vi.fn()}
          onAbrirConexoes={vi.fn()}
          onAdicionarVariasAoFluxo={onAdicionarVariasAoFluxo}
          onRemoverDoFluxo={vi.fn().mockResolvedValue(undefined)}
          onConectar={vi.fn().mockResolvedValue(undefined)}
          onDesconectar={vi.fn().mockResolvedValue(undefined)}
          onAtualizarPosicao={vi.fn().mockResolvedValue(undefined)}
          onAtualizarPosicoesEmLote={vi.fn().mockResolvedValue(undefined)}
        />
      );

      expect(screen.getByText(/Seu fluxo de tarefas está vazio/i)).toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: /Trazer tarefas para o fluxo/i }));

      fireEvent.click(screen.getByRole('checkbox', { name: 'Tarefa fora do fluxo A' }));
      fireEvent.click(screen.getByRole('checkbox', { name: 'Tarefa fora do fluxo B' }));
      fireEvent.click(screen.getByRole('button', { name: /Trazer para o fluxo \(2\)/i }));

      await waitFor(() => {
        expect(onAdicionarVariasAoFluxo).toHaveBeenCalledWith(['t1', 't2']);
      });
    });
  });
});
