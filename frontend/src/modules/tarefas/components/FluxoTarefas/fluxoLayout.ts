import { MarkerType, type Edge, type Node } from '@xyflow/react';
import type { PosicaoFluxoDTO, Tarefa, TipoConexaoFluxo } from '../../types/tarefa';

export const ZOOM_LIMITE_BOLINHA = 0.65;

const COL_GAP = 320;
const ROW_GAP = 164;
const PADDING_X = 72;
const PADDING_Y = 72;

interface TarefaFlowNodeData extends Record<string, unknown> {
  tarefa: Tarefa;
  modoBolinhaForcado: boolean;
  emFoco: boolean;
  nomeLista?: string;
  corLista?: string;
  onAlternarConclusao: (id: string, concluida: boolean) => void;
  onAbrirConexoes: (tarefa: Tarefa) => void;
  onEditarTarefa?: (tarefa: Tarefa) => void;
  onRemoverDoFluxo: (tarefaId: string) => void;
}

interface TarefaFlowEdgeData extends Record<string, unknown> {
  tipo: 'SUCESSORA' | 'PAR';
  origemId: string;
  alvoId: string;
  origemConcluida?: boolean;
  onDesconectar: (origemId: string, alvoId: string) => void;
}

export type TarefaFlowNode = Node<TarefaFlowNodeData, 'tarefa'>;
export type TarefaFlowEdge = Edge<TarefaFlowEdgeData, 'conexao'>;

export function contarConexoesFluxo(tarefa: Pick<Tarefa, 'antecessorasIds' | 'sucessorasIds' | 'paresIds'>): number {
  return (
    (tarefa.antecessorasIds?.length ?? 0) +
    (tarefa.sucessorasIds?.length ?? 0) +
    (tarefa.paresIds?.length ?? 0)
  );
}

export function tarefaPertenceAoFluxo(
  tarefa: Pick<Tarefa, 'noFluxo' | 'antecessorasIds' | 'sucessorasIds' | 'paresIds'>
): boolean {
  return Boolean(tarefa.noFluxo) || contarConexoesFluxo(tarefa) > 0;
}

/**
 * Resolve qual relação semântica deve ser salva a partir dos handles conectados.
 */
export function resolverConexaoPorHandles(
  sourceId: string,
  targetId: string,
  sourceHandle?: string | null,
  targetHandle?: string | null
): { origemId: string; alvoId: string; tipo: TipoConexaoFluxo } | null {
  if (!sourceId || !targetId || sourceId === targetId) return null;

  if (sourceHandle?.startsWith('par-') || targetHandle?.startsWith('par-')) {
    return { origemId: sourceId, alvoId: targetId, tipo: 'PAR' };
  }

  if (sourceHandle === 'antecessora' && targetHandle !== 'antecessora') {
    return { origemId: sourceId, alvoId: targetId, tipo: 'ANTECESSORA' };
  }

  return { origemId: sourceId, alvoId: targetId, tipo: 'SUCESSORA' };
}

/**
 * Calcula posições automáticas no canvas:
 * - Tarefas ligadas como `PAR` compartilham o mesmo grupo/coluna horizontal e ficam empilhadas verticalmente.
 * - Relações `SUCESSORA` avançam colunas da esquerda para a direita.
 */
export function autoOrganizarPosicoes(tarefas: Tarefa[]): Record<string, PosicaoFluxoDTO> {
  if (tarefas.length === 0) return {};

  const idsPresentes = new Set(tarefas.map((t) => t.id));
  const mapaTarefas = new Map(tarefas.map((t) => [t.id, t]));

  // Union-Find para agrupar tarefas pares na mesma coluna
  const parent = new Map<string, string>();
  for (const t of tarefas) parent.set(t.id, t.id);

  const find = (id: string): string => {
    let curr = id;
    while (parent.get(curr) !== curr) {
      curr = parent.get(curr)!;
    }
    let walk = id;
    while (walk !== curr) {
      const next = parent.get(walk)!;
      parent.set(walk, curr);
      walk = next;
    }
    return curr;
  };

  const union = (a: string, b: string) => {
    const ra = find(a);
    const rb = find(b);
    if (ra !== rb) {
      parent.set(rb, ra < rb ? ra : rb);
      if (ra >= rb) {
        parent.set(ra, rb);
        parent.set(rb, rb);
      }
    }
  };

  for (const t of tarefas) {
    for (const parId of t.paresIds ?? []) {
      if (idsPresentes.has(parId)) {
        union(t.id, parId);
      }
    }
  }

  const grupos = new Map<string, string[]>();
  for (const t of tarefas) {
    const root = find(t.id);
    const arr = grupos.get(root) ?? [];
    arr.push(t.id);
    grupos.set(root, arr);
  }

  // Grafo dirigido entre grupos (antecessor -> sucessor)
  const adjGrupos = new Map<string, Set<string>>();
  const grauEntrada = new Map<string, number>();
  for (const g of grupos.keys()) {
    adjGrupos.set(g, new Set());
    grauEntrada.set(g, 0);
  }

  for (const t of tarefas) {
    const gOrig = find(t.id);
    for (const sucId of t.sucessorasIds ?? []) {
      if (!idsPresentes.has(sucId)) continue;
      const gDest = find(sucId);
      if (gOrig !== gDest && !adjGrupos.get(gOrig)!.has(gDest)) {
        adjGrupos.get(gOrig)!.add(gDest);
        grauEntrada.set(gDest, (grauEntrada.get(gDest) ?? 0) + 1);
      }
    }
  }

  // Ordenação topológica com cálculo da maior profundidade (coluna)
  const colunaGrupo = new Map<string, number>();
  const fila: string[] = [];
  for (const [g, grau] of grauEntrada.entries()) {
    colunaGrupo.set(g, 0);
    if (grau === 0) fila.push(g);
  }

  const processados = new Set<string>();
  while (fila.length > 0) {
    const atual = fila.shift()!;
    processados.add(atual);
    const colAtual = colunaGrupo.get(atual) ?? 0;

    for (const vizinho of adjGrupos.get(atual) ?? []) {
      const proximaCol = Math.max(colunaGrupo.get(vizinho) ?? 0, colAtual + 1);
      colunaGrupo.set(vizinho, proximaCol);
      const novoGrau = (grauEntrada.get(vizinho) ?? 1) - 1;
      grauEntrada.set(vizinho, novoGrau);
      if (novoGrau <= 0) {
        fila.push(vizinho);
      }
    }
  }

  // Agrupa membros por coluna, mantendo tarefas pares juntas verticalmente
  const porColuna = new Map<number, string[][]>();
  const gruposOrdenados = [...grupos.entries()].sort(([ga], [gb]) => {
    const ta = mapaTarefas.get(ga);
    const tb = mapaTarefas.get(gb);
    return (ta?.ordem ?? 0) - (tb?.ordem ?? 0) || ga.localeCompare(gb);
  });

  for (const [g, membros] of gruposOrdenados) {
    const col = colunaGrupo.get(g) ?? 0;
    const membrosOrdenados = [...membros].sort((a, b) => {
      const ta = mapaTarefas.get(a);
      const tb = mapaTarefas.get(b);
      return (ta?.ordem ?? 0) - (tb?.ordem ?? 0) || (ta?.titulo ?? '').localeCompare(tb?.titulo ?? '');
    });
    const listaCol = porColuna.get(col) ?? [];
    listaCol.push(membrosOrdenados);
    porColuna.set(col, listaCol);
  }

  const resultado: Record<string, PosicaoFluxoDTO> = {};
  for (const [col, blocos] of porColuna.entries()) {
    let row = 0;
    for (const membros of blocos) {
      for (const id of membros) {
        resultado[id] = {
          x: PADDING_X + col * COL_GAP,
          y: PADDING_Y + row * ROW_GAP
        };
        row += 1;
      }
    }
  }

  return resultado;
}

export interface ConstruirGrafoFluxoParams {
  tarefas: Tarefa[];
  modoBolinhaForcado: boolean;
  tarefaFocadaId: string | null;
  listasPorId: Map<string, { nome: string; cor?: string }>;
  onAlternarConclusao: (id: string, concluida: boolean) => void;
  onAbrirConexoes: (tarefa: Tarefa) => void;
  onEditarTarefa?: (tarefa: Tarefa) => void;
  onRemoverDoFluxo: (tarefaId: string) => void;
  onDesconectar: (origemId: string, alvoId: string) => void;
}

export function construirGrafoFluxo({
  tarefas,
  modoBolinhaForcado,
  tarefaFocadaId,
  listasPorId,
  onAlternarConclusao,
  onAbrirConexoes,
  onEditarTarefa,
  onRemoverDoFluxo,
  onDesconectar
}: ConstruirGrafoFluxoParams): { nodes: TarefaFlowNode[]; edges: TarefaFlowEdge[] } {
  const posicoesAuto = autoOrganizarPosicoes(tarefas);
  const idsPresentes = new Set(tarefas.map((t) => t.id));

  const nodes: TarefaFlowNode[] = tarefas.map((tarefa) => {
    const lista = tarefa.listaId ? listasPorId.get(tarefa.listaId) : undefined;
    const position = tarefa.posicaoFluxo ?? posicoesAuto[tarefa.id] ?? { x: PADDING_X, y: PADDING_Y };

    return {
      id: tarefa.id,
      type: 'tarefa',
      position,
      data: {
        tarefa,
        modoBolinhaForcado,
        emFoco: tarefa.id === tarefaFocadaId,
        nomeLista: lista?.nome,
        corLista: lista?.cor,
        onAlternarConclusao,
        onAbrirConexoes,
        onEditarTarefa,
        onRemoverDoFluxo
      }
    };
  });

  const posicoesPorId = new Map(nodes.map((n) => [n.id, n.position]));
  const edges: TarefaFlowEdge[] = [];
  const paresAdicionados = new Set<string>();

  for (const tarefa of tarefas) {
    for (const sucId of tarefa.sucessorasIds ?? []) {
      if (!idsPresentes.has(sucId) || sucId === tarefa.id) continue;
      edges.push({
        id: `seq-${tarefa.id}--${sucId}`,
        type: 'conexao',
        source: tarefa.id,
        target: sucId,
        sourceHandle: 'sucessora',
        targetHandle: 'antecessora',
        reconnectable: true,
        markerEnd: {
          type: MarkerType.ArrowClosed,
          width: 15,
          height: 15,
          color: tarefa.concluida ? 'var(--green)' : 'var(--primary)'
        },
        data: {
          tipo: 'SUCESSORA',
          origemId: tarefa.id,
          alvoId: sucId,
          origemConcluida: tarefa.concluida,
          onDesconectar
        }
      });
    }

    for (const parId of tarefa.paresIds ?? []) {
      if (!idsPresentes.has(parId) || parId === tarefa.id) continue;
      const [menor, maior] = tarefa.id < parId ? [tarefa.id, parId] : [parId, tarefa.id];
      const chave = `${menor}--${maior}`;
      if (paresAdicionados.has(chave)) continue;
      paresAdicionados.add(chave);

      const posMenor = posicoesPorId.get(menor);
      const posMaior = posicoesPorId.get(maior);
      const menorAcima = !posMenor || !posMaior || posMenor.y <= posMaior.y;
      const topoId = menorAcima ? menor : maior;
      const baseId = menorAcima ? maior : menor;

      edges.push({
        id: `par-${chave}`,
        type: 'conexao',
        source: topoId,
        target: baseId,
        sourceHandle: 'par-base',
        targetHandle: 'par-topo',
        reconnectable: true,
        data: {
          tipo: 'PAR',
          origemId: topoId,
          alvoId: baseId,
          onDesconectar
        }
      });
    }
  }

  return { nodes, edges };
}
