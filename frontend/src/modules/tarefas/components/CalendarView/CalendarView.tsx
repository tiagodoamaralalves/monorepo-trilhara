import React, { useMemo } from 'react';
import { Check, Undo2, Edit2 } from 'lucide-react';
import {
  Button,
  SharedCalendarView,
  type CalendarItem,
  type CalendarBadge
} from '@shared/design-system';
import { Tarefa } from '../../types/tarefa';

export interface CalendarViewProps {
  tarefas: Tarefa[];
  onAlternarTarefa: (id: string, concluida: boolean) => void;
  onAdicionarNoDia: (dataISO: string) => void;
  onEditarTarefa?: (tarefa: Tarefa) => void;
}

interface TarefaCalendarItem extends CalendarItem {
  tarefa: Tarefa;
  vinculoEstudos: boolean;
}

function getCorTarefa(t: Tarefa): string {
  if (t.disciplinaCor) return t.disciplinaCor;
  if (t.disciplinaNome) {
    return 'var(--purple)';
  }
  const p = String(t.prioridade || '').toUpperCase();
  if (p === 'ALTA' || p === 'URGENTE') return 'var(--red, #ef4444)';
  if (p === 'MEDIA') return 'var(--yellow, #eab308)';
  if (p === 'BAIXA') return 'var(--blue, #3b82f6)';
  return 'var(--primary)';
}

export const CalendarView: React.FC<CalendarViewProps> = ({
  tarefas,
  onAlternarTarefa,
  onAdicionarNoDia,
  onEditarTarefa
}) => {
  const items = useMemo<TarefaCalendarItem[]>(() => {
    return tarefas.map((tarefa) => {
      const vinculoEstudos = Boolean(tarefa.disciplinaNome);
      const badges: CalendarBadge[] = [];
      if (vinculoEstudos) {
        badges.push({ label: 'Bloco de cronograma', variant: 'secondary' });
      }
      if (tarefa.prioridade) {
        badges.push({
          label: `Prioridade ${tarefa.prioridade}`,
          variant:
            tarefa.prioridade === 'ALTA'
              ? 'danger'
              : tarefa.prioridade === 'MEDIA'
              ? 'warning'
              : 'neutral'
        });
      }

      const metadados: string[] = [];
      if (tarefa.horario) {
        metadados.push(`Horário: ${tarefa.horario}`);
      }
      const totalSubtarefas = tarefa.subtarefas?.length || 0;
      if (totalSubtarefas > 0) {
        const subtarefasConcluidas =
          tarefa.subtarefas?.filter((st) => st.concluida).length || 0;
        metadados.push(`${subtarefasConcluidas} / ${totalSubtarefas} subtarefas concluídas`);
      }

      return {
        id: tarefa.id,
        data: tarefa.dataVencimento || tarefa.data || '',
        titulo: tarefa.titulo,
        cor: getCorTarefa(tarefa),
        concluido: tarefa.concluida,
        badges,
        metadados,
        observacoes: tarefa.observacoes,
        tarefa,
        vinculoEstudos
      };
    });
  }, [tarefas]);

  return (
    <SharedCalendarView<TarefaCalendarItem>
      items={items}
      onAdicionarNoDia={onAdicionarNoDia}
      ariaLabel="Calendário de tarefas"
      renderItemActions={(item, { fecharModalDia }) => (
        <>
          <Button
            type="button"
            variant={item.tarefa.concluida ? 'warning' : 'success'}
            size="md"
            leftIcon={item.tarefa.concluida ? <Undo2 size={16} /> : <Check size={16} />}
            onClick={() => onAlternarTarefa(item.tarefa.id, !item.tarefa.concluida)}
            aria-pressed={item.tarefa.concluida}
          >
            {item.tarefa.concluida ? 'Desmarcar' : 'Concluir'}
          </Button>
          {!item.vinculoEstudos && onEditarTarefa && (
            <Button
              type="button"
              variant="secondary"
              size="md"
              leftIcon={<Edit2 size={16} />}
              onClick={() => {
                onEditarTarefa(item.tarefa);
                fecharModalDia();
              }}
            >
              Editar
            </Button>
          )}
        </>
      )}
    />
  );
};
