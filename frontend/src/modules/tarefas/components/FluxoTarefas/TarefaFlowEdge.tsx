import React from 'react';
import {
  BaseEdge,
  EdgeLabelRenderer,
  getSmoothStepPath,
  type EdgeProps
} from '@xyflow/react';
import { X } from 'lucide-react';
import type { TarefaFlowEdge as TarefaFlowEdgeType } from './fluxoLayout';
import styles from './TarefaFlowEdge.module.css';

export const TarefaFlowEdge: React.FC<EdgeProps<TarefaFlowEdgeType>> = ({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  markerEnd,
  selected,
  data
}) => {
  const [edgePath, labelX, labelY] = getSmoothStepPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
    borderRadius: 14
  });

  const isPar = data?.tipo === 'PAR';
  const isDone = !isPar && Boolean(data?.origemConcluida);

  const pathClasses = [
    styles.edgePath,
    isPar ? styles.edgePar : '',
    isDone ? styles.edgeDone : '',
    selected
      ? isPar
        ? styles.edgeParSelected
        : isDone
          ? styles.edgeDoneSelected
          : styles.edgeSelected
      : ''
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <>
      <BaseEdge id={id} path={edgePath} markerEnd={markerEnd} className={pathClasses} />
      <EdgeLabelRenderer>
        <div
          className={`${styles.edgeLabelWrap} nodrag nopan`}
          style={{
            transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`
          }}
        >
          {isPar && <span className={styles.tipoTag}>Par</span>}
          {data && (
            <button
              type="button"
              className={`${styles.disconnectBtn} ${selected ? styles.disconnectBtnSelected : ''}`}
              onClick={(e) => {
                e.stopPropagation();
                data.onDesconectar(data.origemId, data.alvoId);
              }}
              title="Desconectar tarefas"
              aria-label={`Desconectar ${isPar ? 'par paralelo' : 'conexão sequencial'}`}
            >
              <X size={12} />
            </button>
          )}
        </div>
      </EdgeLabelRenderer>
    </>
  );
};
