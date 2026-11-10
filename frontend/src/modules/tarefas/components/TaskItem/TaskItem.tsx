import React, { useState } from 'react';
import { Clock, Trash2, Edit2, ListChecks, Repeat, SkipForward, ChevronDown, ChevronUp } from 'lucide-react';
import { Checkbox, Badge, Button } from '@shared/design-system';
import { Tarefa, Subtarefa } from '../../types/tarefa';
import { formatarDataRelativa } from '../../utils/date';
import styles from './TaskItem.module.css';

export interface TaskItemProps {
  tarefa: Tarefa;
  onAlternar: (id: string, concluida: boolean) => void;
  onEditar: (tarefa: Tarefa) => void;
  onExcluir: (tarefa: Tarefa) => void;
  onPular?: (id: string) => void;
  onAlternarSubtarefa?: (tarefaId: string, subtarefaId: string, concluida: boolean) => void;
}

export const TaskItem: React.FC<TaskItemProps> = ({
  tarefa,
  onAlternar,
  onEditar,
  onExcluir,
  onPular,
  onAlternarSubtarefa
}) => {
  const [expandirSubtarefas, setExpandirSubtarefas] = useState(false);
  const dataVencimento = tarefa.dataVencimento || tarefa.data;
  const dataRelativa = dataVencimento ? formatarDataRelativa(dataVencimento) : null;
  const isRecorrente = Boolean(tarefa.serieId && !tarefa.desvinculadaDaSerie);

  // Subtarefas
  const subtarefas = tarefa.subtarefas || [];
  const totalSubtarefas = subtarefas.length;
  const concluidasSubtarefas = subtarefas.filter((s) => s.concluida).length;

  const getPriorityBadge = () => {
    const p = String(tarefa.prioridade || '').toUpperCase();
    switch (p) {
      case 'ALTA':
      case 'URGENTE':
        return <Badge variant="warning" size="sm" dot>Alta</Badge>;
      case 'MEDIA':
        return <Badge variant="primary" size="sm">Média</Badge>;
      case 'BAIXA':
      case 'NENHUMA':
        return <Badge variant="neutral" size="sm">Baixa</Badge>;
      default:
        return null;
    }
  };

  const handleRowClick = (e: React.MouseEvent) => {
    if ((e.target as HTMLElement).closest('input[type="checkbox"], button, .subtaskAction')) {
      return;
    }
    onEditar(tarefa);
  };

  return (
    <div
      className={`${styles.item} ${tarefa.concluida ? styles.concluida : ''}`}
      onClick={handleRowClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onEditar(tarefa);
        }
      }}
      title="Clique para ver detalhes e editar"
    >
      <div className={styles.left}>
        <div
          className={styles.checkboxWrapper}
          onClick={(e) => e.stopPropagation()}
        >
          <Checkbox
            checked={tarefa.concluida}
            onChange={() => onAlternar(tarefa.id, !tarefa.concluida)}
            aria-label={`Marcar ${tarefa.titulo} como ${tarefa.concluida ? 'pendente' : 'concluída'}`}
          />
        </div>

        <div className={styles.info}>
          <div className={styles.titleRow}>
            <span className={`${styles.title} ${tarefa.concluida ? styles.titleDone : ''}`}>
              {tarefa.titulo}
            </span>
          </div>

          {(tarefa.observacoes || tarefa.descricao) && (
            <p className={styles.descricao}>
              {tarefa.observacoes || tarefa.descricao}
            </p>
          )}

          {/* Metadados: Subtarefas, Prioridade, Horário, Data, Recorrência */}
          <div className={styles.metaRow}>
            {isRecorrente && (
              <Badge variant="purple" size="sm" title="Tarefa recorrente gerada automaticamente">
                <Repeat size={11} style={{ marginRight: '4px' }} />
                Recorrente
              </Badge>
            )}

            {totalSubtarefas > 0 && (
              <button
                type="button"
                className={`subtaskAction ${styles.subtaskProgress} ${concluidasSubtarefas === totalSubtarefas ? styles.subtaskAllDone : ''}`}
                onClick={(e) => {
                  e.stopPropagation();
                  setExpandirSubtarefas((prev) => !prev);
                }}
                title="Expandir/recolher subtarefas"
                style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}
              >
                <ListChecks size={13} />
                <span>{concluidasSubtarefas}/{totalSubtarefas}</span>
                {expandirSubtarefas ? <ChevronUp size={11} /> : <ChevronDown size={11} />}
              </button>
            )}

            {getPriorityBadge()}

            {tarefa.horario && (
              <span className={styles.metaText}>
                <Clock size={12} />
                <span>{tarefa.horario}</span>
              </span>
            )}

            {dataRelativa && (
              <span className={`${styles.metaDate} ${dataRelativa.isAtrasada && !tarefa.concluida ? styles.dateAtrasada : ''}`}>
                {dataRelativa.label}
              </span>
            )}
          </div>

          {/* Lista de subtarefas expansível */}
          {expandirSubtarefas && totalSubtarefas > 0 && (
            <div
              className="subtaskAction"
              style={{
                marginTop: '8px',
                paddingLeft: '8px',
                borderLeft: '2px solid var(--border-subtle, #e5e7eb)',
                display: 'flex',
                flexDirection: 'column',
                gap: '4px'
              }}
              onClick={(e) => e.stopPropagation()}
            >
              {subtarefas.map((sub) => (
                <label
                  key={sub.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                    fontSize: '0.85rem',
                    cursor: 'pointer',
                    color: sub.concluida ? 'var(--text-muted, #9ca3af)' : 'inherit',
                    textDecoration: sub.concluida ? 'line-through' : 'none'
                  }}
                >
                  <input
                    type="checkbox"
                    checked={sub.concluida}
                    onChange={() => onAlternarSubtarefa && onAlternarSubtarefa(tarefa.id, sub.id, !sub.concluida)}
                  />
                  <span>{sub.titulo}</span>
                </label>
              ))}
            </div>
          )}
        </div>
      </div>

      <div className={styles.actions} onClick={(e) => e.stopPropagation()}>
        {!tarefa.concluida && onPular && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            iconOnly
            className={styles.actionBtn}
            onClick={() => onPular(tarefa.id)}
            title="Pular esta ocorrência"
            aria-label="Pular tarefa"
          >
            <SkipForward size={15} />
          </Button>
        )}
        <Button
          type="button"
          variant="ghost"
          size="sm"
          iconOnly
          className={styles.actionBtn}
          onClick={() => onEditar(tarefa)}
          title="Editar tarefa"
          aria-label="Editar tarefa"
        >
          <Edit2 size={15} />
        </Button>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          iconOnly
          className={`${styles.actionBtn} ${styles.deleteBtn}`}
          onClick={() => onExcluir(tarefa)}
          title="Excluir tarefa"
          aria-label="Excluir tarefa"
        >
          <Trash2 size={15} />
        </Button>
      </div>
    </div>
  );
};
