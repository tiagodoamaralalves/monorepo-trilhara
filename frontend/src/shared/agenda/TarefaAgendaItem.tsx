import React, { useState } from 'react';
import {
  Clock,
  Trash2,
  ListChecks,
  Repeat,
  ChevronDown,
  ChevronUp,
  Route
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
  /** Em Tarefas, abre o fluxo ou modal rápido de conexões da tarefa. */
  onAbrirFluxo?: (tarefa: TarefaDTO) => void;
  /** Substitui a pílula padrão de data/status (ex.: tarefas locais no navegador). */
  statusBadge?: React.ReactNode;
  /** Oculta a pílula de prioridade (útil para tarefas rápidas locais sem prioridade configurada). */
  ocultarPrioridade?: boolean;
  /** Ação adicional exibida na lateral direita antes do botão excluir (ex.: "Salvar em tarefas"). */
  acaoExtra?: React.ReactNode;
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
  onAbrirFluxo,
  statusBadge,
  ocultarPrioridade = false,
  acaoExtra
}) => {
  const [expandirSubtarefas, setExpandirSubtarefas] = useState(false);

  const hoje = hojeISO();
  const dataVencimento = tarefa.dataVencimento;
  const dataRelativa = dataVencimento ? descreverDataRelativa(dataVencimento) : null;
  const isRecorrente = Boolean(tarefa.serieId && !tarefa.desvinculadaDaSerie);

  const subtarefas = tarefa.subtarefas || [];
  const totalSubtarefas = subtarefas.length;
  const concluidasSubtarefas = subtarefas.filter((s) => s.concluida).length;

  const totalConexoesFluxo =
    (tarefa.antecessorasIds?.length ?? 0) +
    (tarefa.sucessorasIds?.length ?? 0) +
    (tarefa.paresIds?.length ?? 0);
  const emFluxo = Boolean(tarefa.noFluxo) || totalConexoesFluxo > 0;

  const pilulaStatus = (): React.ReactNode => {
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

  const clickable = Boolean(onEditar);
  const classes = [styles.card, tarefa.concluida ? styles.cardDone : ''].filter(Boolean).join(' ');

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
                onClick={() => onEditar?.(tarefa)}
                title="Editar tarefa"
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
          {statusBadge !== undefined ? statusBadge : pilulaStatus()}

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

          {!ocultarPrioridade && pilulaPrioridade()}

          {onAbrirFluxo && (
            <button
              type="button"
              className={`${styles.flowPill} ${emFluxo ? styles.flowPillActive : ''}`}
              onClick={(e) => {
                e.stopPropagation();
                onAbrirFluxo(tarefa);
              }}
              title={
                totalConexoesFluxo > 0
                  ? `Abrir tarefa no fluxo (${totalConexoesFluxo} ${totalConexoesFluxo === 1 ? 'conexão' : 'conexões'})`
                  : emFluxo
                    ? 'Abrir tarefa no fluxo'
                    : 'Conectar ao fluxo de tarefas'
              }
              aria-label={
                totalConexoesFluxo > 0
                  ? `Fluxo da tarefa ${tarefa.titulo}: ${totalConexoesFluxo} ${totalConexoesFluxo === 1 ? 'conexão' : 'conexões'}`
                  : `Conectar tarefa ${tarefa.titulo} ao fluxo`
              }
            >
              <Route size={13} aria-hidden="true" />
              {totalConexoesFluxo > 0 && <span>{totalConexoesFluxo}</span>}
            </button>
          )}

          {acaoExtra}

          {onExcluir && (
            <Button
              type="button"
              variant="danger"
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
            <Checkbox
              key={sub.id}
              className={styles.subtaskCheckbox}
              label={sub.titulo}
              strikeThroughOnChecked
              checked={sub.concluida}
              onChange={() => onAlternarSubtarefa?.(tarefa.id, sub.id, !sub.concluida)}
              aria-label={`Marcar subtarefa ${sub.titulo} como ${
                sub.concluida ? 'pendente' : 'concluída'
              }`}
            />
          ))}
        </div>
      )}
    </article>
  );
};
