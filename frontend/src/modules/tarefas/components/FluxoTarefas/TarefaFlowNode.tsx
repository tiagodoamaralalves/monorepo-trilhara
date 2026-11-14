import React from 'react';
import { Handle, Position, useStore, type NodeProps } from '@xyflow/react';
import {
  ArrowLeft,
  ArrowRight,
  Check,
  Clock,
  Columns2,
  Link2,
  Pencil,
  Route,
  X
} from 'lucide-react';
import { Badge, Checkbox } from '@shared/design-system';
import {
  contarConexoesFluxo,
  ZOOM_LIMITE_BOLINHA,
  type TarefaFlowNode as TarefaFlowNodeType
} from './fluxoLayout';
import styles from './TarefaFlowNode.module.css';

const selectZoomBaixo = (state: { transform: [number, number, number] }) =>
  state.transform[2] < ZOOM_LIMITE_BOLINHA;

export const TarefaFlowNode: React.FC<NodeProps<TarefaFlowNodeType>> = ({
  data,
  selected
}) => {
  const zoomAbaixoLimite = useStore(selectZoomBaixo);
  const {
    tarefa,
    modoBolinhaForcado,
    emFoco,
    nomeLista,
    corLista,
    onAlternarConclusao,
    onAbrirConexoes,
    onEditarTarefa,
    onRemoverDoFluxo
  } = data;

  const emModoBolinha = modoBolinhaForcado || zoomAbaixoLimite;
  const qtdAntecessoras = tarefa.antecessorasIds?.length ?? 0;
  const qtdSucessoras = tarefa.sucessorasIds?.length ?? 0;
  const qtdPares = tarefa.paresIds?.length ?? 0;
  const totalConexoes = contarConexoesFluxo(tarefa);

  const prioridade = String(tarefa.prioridade ?? '').toUpperCase();
  const isAlta = prioridade === 'ALTA' || prioridade === 'URGENTE';
  const isMedia = prioridade === 'MEDIA';

  const handles = (
    <>
      <Handle
        id="antecessora"
        type="target"
        position={Position.Left}
        className={styles.handle}
        title="Antecessora"
      />
      <Handle
        id="sucessora"
        type="source"
        position={Position.Right}
        className={styles.handle}
        title="Sucessora"
      />
      <Handle
        id="par-topo"
        type="target"
        position={Position.Top}
        className={`${styles.handle} ${styles.handlePar}`}
        title="Par (paralelo)"
      />
      <Handle
        id="par-base"
        type="source"
        position={Position.Bottom}
        className={`${styles.handle} ${styles.handlePar}`}
        title="Par (paralelo)"
      />
    </>
  );

  if (emModoBolinha) {
    const dotClasses = [
      styles.dotButton,
      tarefa.concluida
        ? styles.dotButtonDone
        : isAlta
          ? styles.dotButtonAlta
          : isMedia
            ? styles.dotButtonMedia
            : '',
      emFoco || selected ? styles.dotButtonFocused : ''
    ]
      .filter(Boolean)
      .join(' ');

    return (
      <div className={styles.dotNode} data-testid={`flow-node-dot-${tarefa.id}`}>
        {handles}
        <div className={styles.dotOrbWrap}>
          <button
            type="button"
            className={`${dotClasses} nodrag`}
            onClick={(e) => {
              e.stopPropagation();
              onAlternarConclusao(tarefa.id, !tarefa.concluida);
            }}
            title={`${tarefa.titulo} — clique para ${tarefa.concluida ? 'reabrir' : 'concluir'}`}
            aria-label={`${tarefa.concluida ? 'Reabrir' : 'Concluir'} tarefa ${tarefa.titulo}`}
          >
            <Check size={19} strokeWidth={tarefa.concluida ? 3 : 2} aria-hidden="true" />
          </button>
          {totalConexoes > 0 && (
            <span className={styles.dotCountCorner} aria-hidden="true">
              {totalConexoes}
            </span>
          )}
        </div>
        <span
          className={`${styles.dotLabel} ${tarefa.concluida ? styles.dotLabelDone : ''}`}
          title={tarefa.titulo}
        >
          {tarefa.titulo}
        </span>
      </div>
    );
  }

  const accentClass = tarefa.concluida
    ? styles.accentDone
    : isAlta
      ? styles.accentAlta
      : isMedia
        ? styles.accentMedia
        : '';

  const surfaceToneClass = tarefa.concluida
    ? styles.cardDone
    : isAlta
      ? styles.cardAlta
      : isMedia
        ? styles.cardMedia
        : styles.cardPadrao;

  const statusPillClass = tarefa.concluida
    ? styles.statusPillDone
    : isAlta
      ? styles.statusPillAlta
      : isMedia
        ? styles.statusPillMedia
        : qtdPares > 0
          ? styles.statusPillPar
          : styles.statusPillBase;

  const statusLabel = tarefa.concluida
    ? 'Concluída'
    : isAlta
      ? 'Prioridade Alta'
      : isMedia
        ? 'Prioridade Média'
        : qtdPares > 0
          ? 'Paralelo'
          : 'Etapa';

  const cardClasses = [
    styles.card,
    surfaceToneClass,
    selected ? styles.cardSelected : '',
    emFoco ? styles.cardFocused : ''
  ]
    .filter(Boolean)
    .join(' ');

  const concluidasSub = (tarefa.subtarefas ?? []).filter((s) => s.concluida).length;
  const totalSub = (tarefa.subtarefas ?? []).length;
  const pctSub = totalSub > 0 ? Math.round((concluidasSub / totalSub) * 100) : 0;

  return (
    <div
      className={cardClasses}
      data-testid={`flow-node-card-${tarefa.id}`}
      onDoubleClick={(e) => {
        e.stopPropagation();
        onEditarTarefa?.(tarefa);
      }}
    >
      {handles}
      <div className={`${styles.accentBar} ${accentClass}`} />

      <div className={styles.cardBody}>
        <div className={styles.topStatusRow}>
          <span className={`${styles.statusPill} ${statusPillClass}`}>
            <span className={styles.statusDot} aria-hidden="true" />
            <span>{statusLabel}</span>
          </span>

          <div className={`${styles.quickActions} nodrag`}>
            {onEditarTarefa && (
              <button
                type="button"
                className={styles.actionBtn}
                onClick={(e) => {
                  e.stopPropagation();
                  onEditarTarefa(tarefa);
                }}
                title="Editar tarefa"
                aria-label={`Editar tarefa ${tarefa.titulo}`}
              >
                <Pencil size={13} />
              </button>
            )}
            <button
              type="button"
              className={`${styles.actionBtn} ${styles.actionBtnDanger}`}
              onClick={(e) => {
                e.stopPropagation();
                onRemoverDoFluxo(tarefa.id);
              }}
              title="Tirar do fluxo (mantém a tarefa salva)"
              aria-label={`Remover ${tarefa.titulo} do fluxo`}
            >
              <X size={13} />
            </button>
          </div>
        </div>

        <div className={styles.cardHeader}>
          <div className={styles.checkTitleRow}>
            <div className="nodrag nopan">
              <Checkbox
                checked={tarefa.concluida}
                onChange={(e) => onAlternarConclusao(tarefa.id, e.target.checked)}
                aria-label={`Marcar "${tarefa.titulo}" como ${tarefa.concluida ? 'não concluída' : 'concluída'}`}
              />
            </div>
            <p className={`${styles.title} ${tarefa.concluida ? styles.titleDone : ''}`}>
              {tarefa.titulo}
            </p>
          </div>
        </div>

        {(nomeLista || tarefa.horario) && (
          <div className={styles.metaRow}>
            <div className={styles.badgesGroup}>
              {nomeLista && (
                <span className={styles.listaTag}>
                  <span
                    className={styles.listaDot}
                    style={{ background: corLista || 'var(--primary)' }}
                  />
                  <span className={styles.listaNome}>{nomeLista}</span>
                </span>
              )}

              {tarefa.horario && (
                <Badge variant="default" size="sm">
                  <Clock size={10} aria-hidden="true" />
                  <span>{tarefa.horario}</span>
                </Badge>
              )}
            </div>
          </div>
        )}

        {totalSub > 0 && (
          <div className={styles.subProgressWrap}>
            <div className={styles.subProgressTrack} aria-hidden="true">
              <div
                className={`${styles.subProgressFill} ${concluidasSub === totalSub ? styles.subProgressFillDone : ''}`}
                style={{ width: `${pctSub}%` }}
              />
            </div>
            <span className={styles.subProgressText}>
              {concluidasSub}/{totalSub}
            </span>
          </div>
        )}

        <div className={styles.dragHintRow}>
          <span className="nodrag">
            <button
              type="button"
              className={styles.connectPillBtn}
              onClick={(e) => {
                e.stopPropagation();
                onAbrirConexoes(tarefa);
              }}
              aria-label={`Conectar tarefa ${tarefa.titulo}`}
            >
              <Link2 size={11} aria-hidden="true" />
              <span>Conectar</span>
            </button>
          </span>

          {totalConexoes > 0 ? (
            <div className={styles.direcaoBadges} title={`${totalConexoes} conexões no fluxo`}>
              {qtdAntecessoras > 0 && (
                <span className={styles.dirChip} title={`${qtdAntecessoras} antecessora(s)`}>
                  <ArrowLeft size={10} aria-hidden="true" />
                  {qtdAntecessoras}
                </span>
              )}
              {qtdPares > 0 && (
                <span className={`${styles.dirChip} ${styles.dirChipPar}`} title={`${qtdPares} par(es) paralelo(s)`}>
                  <Columns2 size={10} aria-hidden="true" />
                  {qtdPares}
                </span>
              )}
              {qtdSucessoras > 0 && (
                <span className={styles.dirChip} title={`${qtdSucessoras} sucessora(s)`}>
                  {qtdSucessoras}
                  <ArrowRight size={10} aria-hidden="true" />
                </span>
              )}
            </div>
          ) : (
            <span className={styles.conexoesResumo} title="Arraste uma alça para conectar">
              <Route size={12} aria-hidden="true" />
              <span>0</span>
            </span>
          )}
        </div>
      </div>
    </div>
  );
};
