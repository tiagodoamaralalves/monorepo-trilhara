import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Background,
  BackgroundVariant,
  ConnectionMode,
  Controls,
  MiniMap,
  Panel,
  ReactFlow,
  ReactFlowProvider,
  useEdgesState,
  useNodesState,
  useReactFlow,
  useStore,
  type Connection,
  type Edge,
  type Node,
  type OnNodeDrag,
  type OnReconnect
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import {
  CircleDot,
  LayoutGrid,
  Maximize2,
  Plus,
  Route,
  Sparkles
} from 'lucide-react';
import { Button, EmptyState, ToggleGroup } from '@shared/design-system';
import type {
  ListaTarefas,
  Tarefa,
  TipoConexaoFluxo
} from '../../types/tarefa';
import { FluxoTarefasModal } from './FluxoTarefasModal';
import { TarefaFlowEdge } from './TarefaFlowEdge';
import { TarefaFlowNode } from './TarefaFlowNode';
import {
  autoOrganizarPosicoes,
  construirGrafoFluxo,
  resolverConexaoPorHandles,
  tarefaPertenceAoFluxo,
  ZOOM_LIMITE_BOLINHA,
  type TarefaFlowEdge as TarefaFlowEdgeType,
  type TarefaFlowNode as TarefaFlowNodeType
} from './fluxoLayout';
import styles from './FluxoTarefasView.module.css';

export interface FluxoTarefasViewProps {
  todasTarefas: Tarefa[];
  listas: ListaTarefas[];
  tarefaFocadaId: string | null;
  onAlternarConclusao: (id: string, concluida: boolean) => void;
  onEditarTarefa: (tarefa: Tarefa) => void;
  onAbrirConexoes: (tarefa: Tarefa) => void;
  onAdicionarVariasAoFluxo: (tarefaIds: string[]) => Promise<void>;
  onRemoverDoFluxo: (tarefaId: string) => Promise<void>;
  onConectar: (origemId: string, alvoId: string, tipo: TipoConexaoFluxo) => Promise<void>;
  onDesconectar: (origemId: string, alvoId: string) => Promise<void>;
  onAtualizarPosicao: (tarefaId: string, x: number, y: number) => Promise<void>;
  onAtualizarPosicoesEmLote: (posicoes: Array<{ tarefaId: string; x: number; y: number }>) => Promise<void>;
}

const NODE_TYPES = {
  tarefa: TarefaFlowNode
};

const EDGE_TYPES = {
  conexao: TarefaFlowEdge
};

const OPCOES_VISAO = [
  { value: 'auto', label: 'Cartões', icon: <LayoutGrid size={14} aria-hidden="true" /> },
  { value: 'bolinhas', label: 'Bolinhas', icon: <CircleDot size={14} aria-hidden="true" /> }
] as const;

type ModoVisao = (typeof OPCOES_VISAO)[number]['value'];

const selectZoomAtual = (state: { transform: [number, number, number] }) => state.transform[2];

function corMiniMapNode(node: Node): string {
  const tarefa = (node as TarefaFlowNodeType).data?.tarefa;
  if (!tarefa) return 'var(--primary)';
  if (tarefa.concluida) return 'var(--green)';
  const p = String(tarefa.prioridade ?? '').toUpperCase();
  if (p === 'ALTA' || p === 'URGENTE') return 'var(--red)';
  if (p === 'MEDIA') return 'var(--yellow)';
  return 'var(--primary)';
}

const FluxoCanvas: React.FC<FluxoTarefasViewProps> = ({
  todasTarefas,
  listas,
  tarefaFocadaId,
  onAlternarConclusao,
  onEditarTarefa,
  onAbrirConexoes,
  onAdicionarVariasAoFluxo,
  onRemoverDoFluxo,
  onConectar,
  onDesconectar,
  onAtualizarPosicao,
  onAtualizarPosicoesEmLote
}) => {
  const [modoVisao, setModoVisao] = useState<ModoVisao>('auto');
  const [modalTrazerAberto, setModalTrazerAberto] = useState(false);
  const [organizando, setOrganizando] = useState(false);
  const ultimoFocoRef = useRef<string | null>(null);

  const zoomAtual = useStore(selectZoomAtual);
  const { fitView, setCenter } = useReactFlow<TarefaFlowNodeType, TarefaFlowEdgeType>();

  const tarefasFluxo = useMemo(
    () => todasTarefas.filter((t) => tarefaPertenceAoFluxo(t)),
    [todasTarefas]
  );

  const concluidasNoFluxo = useMemo(
    () => tarefasFluxo.filter((t) => t.concluida).length,
    [tarefasFluxo]
  );

  const listasPorId = useMemo(
    () => new Map(listas.map((l) => [l.id, { nome: l.nome, cor: l.cor }])),
    [listas]
  );

  const handleDesconectar = useCallback(
    (origemId: string, alvoId: string) => {
      void onDesconectar(origemId, alvoId);
    },
    [onDesconectar]
  );

  const handleRemoverDoFluxo = useCallback(
    (tarefaId: string) => {
      void onRemoverDoFluxo(tarefaId);
    },
    [onRemoverDoFluxo]
  );

  const grafo = useMemo(
    () =>
      construirGrafoFluxo({
        tarefas: tarefasFluxo,
        modoBolinhaForcado: modoVisao === 'bolinhas',
        tarefaFocadaId,
        listasPorId,
        onAlternarConclusao,
        onAbrirConexoes,
        onEditarTarefa,
        onRemoverDoFluxo: handleRemoverDoFluxo,
        onDesconectar: handleDesconectar
      }),
    [
      tarefasFluxo,
      modoVisao,
      tarefaFocadaId,
      listasPorId,
      onAlternarConclusao,
      onAbrirConexoes,
      onEditarTarefa,
      handleRemoverDoFluxo,
      handleDesconectar
    ]
  );

  const [nodes, setNodes, onNodesChange] = useNodesState<TarefaFlowNodeType>(grafo.nodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<TarefaFlowEdgeType>(grafo.edges);

  useEffect(() => {
    setNodes(grafo.nodes);
    setEdges(grafo.edges);
  }, [grafo, setNodes, setEdges]);

  useEffect(() => {
    if (!tarefaFocadaId) {
      ultimoFocoRef.current = null;
      return;
    }
    if (ultimoFocoRef.current === tarefaFocadaId) return;
    const alvo = grafo.nodes.find((n) => n.id === tarefaFocadaId);
    if (!alvo) return;
    ultimoFocoRef.current = tarefaFocadaId;
    setCenter(alvo.position.x + 132, alvo.position.y + 72, { zoom: 1, duration: 350 });
  }, [tarefaFocadaId, grafo.nodes, setCenter]);

  const handleConnect = useCallback(
    (connection: Connection) => {
      if (!connection.source || !connection.target) return;
      const resolvido = resolverConexaoPorHandles(
        connection.source,
        connection.target,
        connection.sourceHandle,
        connection.targetHandle
      );
      if (!resolvido) return;
      void onConectar(resolvido.origemId, resolvido.alvoId, resolvido.tipo);
    },
    [onConectar]
  );

  const handleReconnect: OnReconnect<TarefaFlowEdgeType> = useCallback(
    (oldEdge, newConnection) => {
      if (!newConnection.source || !newConnection.target) return;
      const nova = resolverConexaoPorHandles(
        newConnection.source,
        newConnection.target,
        newConnection.sourceHandle,
        newConnection.targetHandle
      );
      if (!nova) return;

      const oldOrigem = oldEdge.data?.origemId ?? oldEdge.source;
      const oldAlvo = oldEdge.data?.alvoId ?? oldEdge.target;

      if (
        oldOrigem === nova.origemId &&
        oldAlvo === nova.alvoId &&
        oldEdge.data?.tipo === nova.tipo
      ) {
        return;
      }

      void (async () => {
        await onDesconectar(oldOrigem, oldAlvo);
        try {
          await onConectar(nova.origemId, nova.alvoId, nova.tipo);
        } catch {
          const fallbackTipo: TipoConexaoFluxo = oldEdge.data?.tipo === 'PAR' ? 'PAR' : 'SUCESSORA';
          await onConectar(oldOrigem, oldAlvo, fallbackTipo).catch(() => undefined);
        }
      })();
    },
    [onConectar, onDesconectar]
  );

  const handleEdgesDelete = useCallback(
    (deletedEdges: Edge[]) => {
      for (const edge of deletedEdges) {
        const data = (edge as TarefaFlowEdgeType).data;
        const origem = data?.origemId ?? edge.source;
        const alvo = data?.alvoId ?? edge.target;
        void onDesconectar(origem, alvo);
      }
    },
    [onDesconectar]
  );

  const handleNodeDragStop: OnNodeDrag<TarefaFlowNodeType> = useCallback(
    (_event, node) => {
      void onAtualizarPosicao(node.id, Math.round(node.position.x), Math.round(node.position.y));
    },
    [onAtualizarPosicao]
  );

  const handleAutoOrganizar = async () => {
    if (tarefasFluxo.length === 0 || organizando) return;
    setOrganizando(true);
    try {
      const posicoes = autoOrganizarPosicoes(tarefasFluxo);
      const lista = Object.entries(posicoes).map(([tarefaId, pos]) => ({
        tarefaId,
        x: pos.x,
        y: pos.y
      }));
      if (lista.length > 0) {
        await onAtualizarPosicoesEmLote(lista);
      }
      fitView({ padding: 0.25, duration: 300 });
    } finally {
      setOrganizando(false);
    }
  };

  const zoomPercent = Math.round(zoomAtual * 100);
  const emZoomBolinha = modoVisao === 'bolinhas' || zoomAtual < ZOOM_LIMITE_BOLINHA;

  return (
    <div className={styles.wrapper} data-testid="fluxo-tarefas-view">
      <div className={styles.canvasShell}>
        {tarefasFluxo.length === 0 ? (
          <div className={styles.emptyWrap}>
            <EmptyState
              icone={<Route size={42} />}
              titulo="Seu fluxo de tarefas está vazio"
              descricao="Traga tarefas para a tela infinita ou clique no ícone de rota em qualquer card para conectar antecessoras, pares e sucessoras."
              acaoPrimaria={
                <Button
                  type="button"
                  variant="primary"
                  size="md"
                  leftIcon={<Plus size={16} />}
                  onClick={() => setModalTrazerAberto(true)}
                >
                  Trazer tarefas para o fluxo
                </Button>
              }
            />
            <div className={styles.emptyLegendRow}>
              <span className={styles.legendItem}>
                <span className={styles.legendLineSeq} aria-hidden="true" />
                <span>Antecessora → Sucessora</span>
              </span>
              <span className={styles.legendItem}>
                <span className={styles.legendLinePar} aria-hidden="true" />
                <span>Pares paralelos (mesma coluna)</span>
              </span>
            </div>
          </div>
        ) : (
          <ReactFlow
            nodes={nodes}
            edges={edges}
            nodeTypes={NODE_TYPES}
            edgeTypes={EDGE_TYPES}
            connectionMode={ConnectionMode.Loose}
            edgesReconnectable
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={handleConnect}
            onReconnect={handleReconnect}
            onEdgesDelete={handleEdgesDelete}
            onNodeDragStop={handleNodeDragStop}
            minZoom={0.18}
            maxZoom={1.75}
            fitView
            fitViewOptions={{ padding: 0.28 }}
            panOnDrag
            zoomOnPinch
          >
            <Background variant={BackgroundVariant.Dots} gap={24} size={1.35} />

            <Panel position="top-left">
              <div className={styles.hudIsland}>
                <Button
                  type="button"
                  variant="primary"
                  size="sm"
                  leftIcon={<Plus size={15} />}
                  onClick={() => setModalTrazerAberto(true)}
                >
                  Trazer tarefa
                </Button>

                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  leftIcon={<Sparkles size={14} />}
                  loading={organizando}
                  onClick={handleAutoOrganizar}
                >
                  Auto-organizar
                </Button>

                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  leftIcon={<Maximize2 size={14} />}
                  onClick={() => fitView({ padding: 0.25, duration: 300 })}
                >
                  Centralizar
                </Button>
              </div>
            </Panel>

            <Panel position="top-right">
              <div className={styles.hudIsland}>
                <span className={styles.hudStatsPill}>
                  <span className={styles.hudStatsDot} aria-hidden="true" />
                  <span>
                    {concluidasNoFluxo}/{tarefasFluxo.length} concluídas
                  </span>
                </span>

                <span
                  className={`${styles.hudZoomTag} ${emZoomBolinha ? styles.hudZoomDotMode : ''}`}
                  title="Zoom atual do canvas (abaixo de 65% alterna para bolinhas)"
                >
                  {zoomPercent}%
                </span>

                <ToggleGroup
                  options={OPCOES_VISAO}
                  value={modoVisao}
                  onChange={setModoVisao}
                  ariaLabel="Modo de visualização do fluxo"
                />
              </div>
            </Panel>

            <Panel position="bottom-center">
              <div className={styles.bottomLegendPill}>
                <span className={styles.legendItem}>
                  <span className={styles.legendLineSeq} aria-hidden="true" />
                  <span>Fluxo pendente</span>
                </span>
                <span className={styles.legendItem}>
                  <span className={styles.legendLineDone} aria-hidden="true" />
                  <span>Etapa concluída</span>
                </span>
                <span className={styles.legendItem}>
                  <span className={styles.legendLinePar} aria-hidden="true" />
                  <span>Par paralelo</span>
                </span>
                <span className={styles.legendDivider} aria-hidden="true" />
                <span>Arraste as alças ou diminua o zoom para bolinhas</span>
              </div>
            </Panel>

            <Controls showInteractive={false} />
            <MiniMap
              pannable
              zoomable
              bgColor="transparent"
              maskColor="color-mix(in srgb, var(--bg) 78%, transparent)"
              maskStrokeColor="var(--primary-light)"
              maskStrokeWidth={1.5}
              nodeColor={corMiniMapNode}
              nodeStrokeColor="var(--surface-1)"
              nodeStrokeWidth={2}
              nodeBorderRadius={7}
            />
          </ReactFlow>
        )}
      </div>

      <FluxoTarefasModal
        isOpen={modalTrazerAberto}
        modo="trazer"
        todasTarefas={todasTarefas}
        listas={listas}
        onClose={() => setModalTrazerAberto(false)}
        onAdicionarMultiplas={onAdicionarVariasAoFluxo}
      />
    </div>
  );
};

export const FluxoTarefasView: React.FC<FluxoTarefasViewProps> = (props) => (
  <ReactFlowProvider>
    <FluxoCanvas {...props} />
  </ReactFlowProvider>
);
