import React, { useState } from 'react';
import {
  Clock,
  Trash2,
  Edit2,
  ListChecks,
  Repeat,
  SkipForward,
  ChevronDown,
  ChevronUp
} from 'lucide-react';
import { Checkbox, Badge, Button } from '../design-system';
import { descreverDataRelativa } from '../format';
import { TarefaDTO } from '../contracts/domainContracts';
import styles from './TarefaAgendaItem.module.css';

/** `confortavel` em Tarefas; `compacta` no espelho do Pomodoro. */
type DensidadeItemAgenda = 'confortavel' | 'compacta';

export interface TarefaAgendaItemProps {
  tarefa: TarefaDTO;
  densidade?: DensidadeItemAgenda;
  /** Ações de edição/exclusão/pular só aparecem no modo Tarefas. */
  onEditar?: (tarefa: TarefaDTO) => void;
  onExcluir?: (tarefa: TarefaDTO) => void;
  onPular?: (id: string) => void;
  onAlternarSubtarefa?: (tarefaId: string, subtarefaId: string, concluida: boolean) => void;
  /** Conclusão é sempre obrigatória: é a mesma ação em Tarefas e Pomodoro. */
  onAlternar: (id: string, concluida: boolean) => void;
  /**
   * Em Pomodoro o item inteiro é um botão de contexto visual ("Definir como foco").
   * Em Tarefas o título abre a edição.
   */
  onSelecionar?: (tarefa: TarefaDTO) => void;
  selecionado?: boolean;
}

/**
 * Linha única de tarefa manual da Visão Hoje (D-23/D-46).
 *
 * Vive em `shared/agenda` porque Tarefas e Pomodoro precisam renderizar exatamente o mesmo
 * item; a densidade muda apenas tipografia e espaçamento, nunca o conteúdo.
 */
export const TarefaAgendaItem: React.FC<TarefaAgendaItemProps> = ({
  tarefa,
  densidade = 'confortavel',
  onEditar,
  onExcluir,
  onPular,
  onAlternarSubtarefa,
  onAlternar,
  onSelecionar,
  selecionado = false
}) => {
  const [expandirSubtarefas, setExpandirSubtarefas] = useState(false);
  const compacta = densidade === 'compacta';

  const dataVencimento = tarefa.dataVencimento;
  const dataRelativa = dataVencimento ? descreverDataRelativa(dataVencimento) : null;
  const isRecorrente = Boolean(tarefa.serieId && !tarefa.desvinculadaDaSerie);

  const subtarefas = tarefa.subtarefas || [];
  const totalSubtarefas = subtarefas.length;
  const concluidasSubtarefas = subtarefas.filter((s) => s.concluida).length;

  const rotuloPrioridade = (): React.ReactNode => {
    const prioridade = String(tarefa.prioridade || '').toUpperCase();
    switch (prioridade) {
      case 'ALTA':
      case 'URGENTE':
        return (
          <Badge variant="warning" size="sm" dot>
            Alta
          </Badge>
        );
      case 'MEDIA':
        return (
          <Badge variant="primary" size="sm">
            Média
          </Badge>
        );
      case 'BAIXA':
      case 'NENHUMA':
        return (
          <Badge variant="neutral" size="sm">
            Baixa
          </Badge>
        );
      default:
        return null;
    }
  };

  const classes = [
    styles.item,
    compacta ? styles.compacta : '',
    selecionado ? styles.selecionado : '',
    tarefa.concluida ? styles.concluida : ''
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={classes}>
      <div className={styles.left}>
        <div className={styles.checkboxWrapper}>
          <Checkbox
            checked={tarefa.concluida}
            onChange={() => onAlternar(tarefa.id, !tarefa.concluida)}
            aria-label={`Concluir tarefa ${tarefa.titulo}`}
          />
        </div>

        <div className={styles.info}>
          <div className={styles.titleRow}>
            <button
              type="button"
              className={`${styles.title} ${styles.titleButton} ${tarefa.concluida ? styles.titleDone : ''}`}
              onClick={() => (onSelecionar ? onSelecionar(tarefa) : onEditar?.(tarefa))}
              title={onSelecionar ? 'Definir como foco' : 'Ver detalhes e editar tarefa'}
            >
              {tarefa.titulo}
            </button>
          </div>

          {!compacta && tarefa.descricao && (
            <p className={styles.descricao}>{tarefa.descricao}</p>
          )}

          <div className={styles.metaRow}>
            {isRecorrente && (
              <Badge variant="purple" size="sm" title="Tarefa recorrente gerada automaticamente">
                <Repeat size={11} style={{ marginRight: '4px' }} />
                Recorrente
              </Badge>
            )}

            {totalSubtarefas > 0 && onAlternarSubtarefa && (
              <button
                type="button"
                className={`${styles.subtaskProgress} ${
                  concluidasSubtarefas === totalSubtarefas ? styles.subtaskAllDone : ''
                }`}
                onClick={(e) => {
                  e.stopPropagation();
                  setExpandirSubtarefas((prev) => !prev);
                }}
                title="Expandir/recolher subtarefas"
              >
                <ListChecks size={13} />
                <span>
                  {concluidasSubtarefas}/{totalSubtarefas}
                </span>
                {expandirSubtarefas ? <ChevronUp size={11} /> : <ChevronDown size={11} />}
              </button>
            )}

            {rotuloPrioridade()}

            {tarefa.horario && (
              <span className={styles.metaText}>
                <Clock size={12} />
                <span>{tarefa.horario}</span>
              </span>
            )}

            {dataRelativa && (
              <span
                className={`${styles.metaDate} ${
                  dataRelativa.isAtrasada && !tarefa.concluida ? styles.dateAtrasada : ''
                }`}
              >
                {dataRelativa.label}
              </span>
            )}
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
        </div>
      </div>

      {!compacta && (onPular || onEditar || onExcluir) && (
        <div className={styles.actions}>
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
          {onEditar && (
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
          )}
          {onExcluir && (
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
          )}
        </div>
      )}
    </div>
  );
};
