import React, { useState } from 'react';
import {
  Clock,
  Trash2,
  ListChecks,
  Repeat,
  ChevronDown,
  ChevronUp
} from 'lucide-react';
import { Checkbox, Badge, Button } from '../design-system';
import { descreverDataRelativa, hojeISO } from '../format';
import { TarefaDTO } from '../contracts/domainContracts';
import styles from './AgendaCard.module.css';

export type StatusAgendaTarefa = 'atrasada' | 'hoje' | 'sem-data';

export interface TarefaAgendaItemProps {
  tarefa: TarefaDTO;
  statusAgenda?: StatusAgendaTarefa;
  /** Em Tarefas, clicar no card abre a edição. */
  onEditar?: (tarefa: TarefaDTO) => void;
  /** Em Tarefas e Pomodoro, permite excluir a tarefa. */
  onExcluir?: (tarefa: TarefaDTO) => void;
  onAlternarSubtarefa?: (tarefaId: string, subtarefaId: string, concluida: boolean) => void;
  /** Conclusão é sempre obrigatória em Tarefas e Pomodoro. */
  onAlternar: (id: string, concluida: boolean) => void;
  /** Em Pomodoro, clicar no card define a tarefa como foco visual. */
  onSelecionar?: (tarefa: TarefaDTO) => void;
  selecionado?: boolean;
}

/**
 * Cartão horizontal de tarefa manual (padrão visual idêntico ao de Disciplinas).
 *
 * Compartilhado entre Tarefas e Pomodoro. Pílulas de status, recorrência, subtarefas,
 * horário e prioridade ficam alinhadas horizontalmente à direita.
 */
export const TarefaAgendaItem: React.FC<TarefaAgendaItemProps> = ({
  tarefa,
  statusAgenda,
  onEditar,
  onExcluir,
  onAlternarSubtarefa,
  onAlternar,
  onSelecionar,
  selecionado = false
}) => {
  const [expandirSubtarefas, setExpandirSubtarefas] = useState(false);

  const hoje = hojeISO();
  const dataVencimento = tarefa.dataVencimento;
  const dataRelativa = dataVencimento ? descreverDataRelativa(dataVencimento) : null;
  const isRecorrente = Boolean(tarefa.serieId && !tarefa.desvinculadaDaSerie);

  const subtarefas = tarefa.subtarefas || [];
  const totalSubtarefas = subtarefas.length;
  const concluidasSubtarefas = subtarefas.filter((s) => s.concluida).length;

  const pilulaStatus = (): React.ReactNode => {
    if (tarefa.concluida) {
      return (
        <Badge variant="success" size="md">
          Concluída
        </Badge>
      );
    }

    if (statusAgenda === 'atrasada' || (dataVencimento && dataVencimento < hoje)) {
      return (
        <Badge variant="danger" size="md">
          Atrasada
        </Badge>
      );
    }

    if (statusAgenda === 'hoje' || dataVencimento === hoje) {
      return (
        <Badge variant="primary" size="md">
          Hoje
        </Badge>
      );
    }

    if (statusAgenda === 'sem-data' || !dataVencimento) {
      return (
        <Badge variant="neutral" size="md">
          Sem data
        </Badge>
      );
    }

    return (
      <Badge variant="secondary" size="md">
        {dataRelativa?.label || dataVencimento}
      </Badge>
    );
  };

  const pilulaPrioridade = (): React.ReactNode => {
    const prioridade = String(tarefa.prioridade || '').toUpperCase();
    switch (prioridade) {
      case 'ALTA':
      case 'URGENTE':
        return (
          <Badge variant="warning" size="md" dot>
            Alta
          </Badge>
        );
      case 'MEDIA':
        return (
          <Badge variant="secondary" size="md">
            Média
          </Badge>
        );
      case 'BAIXA':
      case 'NENHUMA':
        return (
          <Badge variant="neutral" size="md">
            Baixa
          </Badge>
        );
      default:
        return null;
    }
  };

  const clickable = Boolean(onSelecionar || onEditar);
  const classes = [
    styles.card,
    selecionado ? styles.cardSelected : '',
    tarefa.concluida ? styles.cardDone : ''
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <article className={classes}>
      <div className={styles.cardRow}>
        <div className={styles.cardLead}>
          <div className={styles.interactiveControl}>
            <Checkbox
              checked={tarefa.concluida}
              onChange={() => onAlternar(tarefa.id, !tarefa.concluida)}
              aria-label={`Concluir tarefa ${tarefa.titulo}`}
            />
          </div>

          <div className={styles.cardTitleWrap}>
            {clickable ? (
              <button
                type="button"
                className={`${styles.cardTrigger} ${styles.cardTitle} ${
                  tarefa.concluida ? styles.cardTitleDone : ''
                }`}
                onClick={() => (onSelecionar ? onSelecionar(tarefa) : onEditar?.(tarefa))}
                title={onSelecionar ? 'Definir como foco' : 'Editar tarefa'}
              >
                {tarefa.titulo}
              </button>
            ) : (
              <span
                className={`${styles.cardTitle} ${tarefa.concluida ? styles.cardTitleDone : ''}`}
              >
                {tarefa.titulo}
              </span>
            )}
          </div>
        </div>

        <div className={styles.cardMeta}>
          {pilulaStatus()}

          {isRecorrente && (
            <Badge variant="purple" size="md" title="Tarefa recorrente gerada automaticamente">
              <Repeat size={12} aria-hidden="true" />
              <span>Recorrente</span>
            </Badge>
          )}

          {totalSubtarefas > 0 && onAlternarSubtarefa && (
            <button
              type="button"
              className={`${styles.subtaskPill} ${
                concluidasSubtarefas === totalSubtarefas ? styles.subtaskAllDone : ''
              }`}
              onClick={(e) => {
                e.stopPropagation();
                setExpandirSubtarefas((prev) => !prev);
              }}
              title="Expandir/recolher subtarefas"
              aria-expanded={expandirSubtarefas}
            >
              <ListChecks size={13} aria-hidden="true" />
              <span>
                {concluidasSubtarefas}/{totalSubtarefas}
              </span>
              {expandirSubtarefas ? (
                <ChevronUp size={11} aria-hidden="true" />
              ) : (
                <ChevronDown size={11} aria-hidden="true" />
              )}
            </button>
          )}

          {tarefa.horario && (
            <Badge variant="default" size="md">
              <Clock size={12} aria-hidden="true" />
              <span>{tarefa.horario}</span>
            </Badge>
          )}

          {pilulaPrioridade()}

          {onExcluir && (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              iconOnly
              className={styles.deleteBtn}
              onClick={(e) => {
                e.stopPropagation();
                onExcluir(tarefa);
              }}
              title="Excluir tarefa"
              aria-label={`Excluir tarefa ${tarefa.titulo}`}
            >
              <Trash2 size={15} />
            </Button>
          )}
        </div>
      </div>

      {expandirSubtarefas && totalSubtarefas > 0 && (
        <div className={styles.subtarefas}>
          {subtarefas.map((sub) => (
            <label
              key={sub.id}
              className={`${styles.subtarefa} ${sub.concluida ? styles.subtarefaConcluida : ''}`}
            >
              <input
                type="checkbox"
                aria-label={`Marcar subtarefa ${sub.titulo} como ${
                  sub.concluida ? 'pendente' : 'concluída'
                }`}
                checked={sub.concluida}
                onChange={() => onAlternarSubtarefa?.(tarefa.id, sub.id, !sub.concluida)}
              />
              <span>{sub.titulo}</span>
            </label>
          ))}
        </div>
      )}
    </article>
  );
};
