import React, { useState, useMemo } from 'react';
import {
  ArrowLeft,
  ArrowRight,
  CalendarDays,
  Check,
  Undo2,
  Edit2,
  Plus
} from 'lucide-react';
import { Button, Modal, Badge, DisciplineLabel } from '@shared/design-system';
import { Tarefa } from '../../types/tarefa';
import {
  hojeISO,
  dataLocal,
  parseDataLocal,
  adicionarDias,
  criarGradeMes,
  criarGradeSemana,
  NOMES_DIAS
} from '../../utils/date';
import styles from './CalendarView.module.css';

export interface CalendarViewProps {
  tarefas: Tarefa[];
  onAlternarTarefa: (id: string, concluida: boolean) => void;
  onAdicionarNoDia: (dataISO: string) => void;
  onEditarTarefa?: (tarefa: Tarefa) => void;
}

type VisualizacaoCalendario = 'mensal' | 'semanal';

export const CalendarView: React.FC<CalendarViewProps> = ({
  tarefas,
  onAlternarTarefa,
  onAdicionarNoDia,
  onEditarTarefa
}) => {
  const hoje = hojeISO();

  const [visualizacaoCalendario, setVisualizacaoCalendario] =
    useState<VisualizacaoCalendario>(() =>
      typeof window !== 'undefined' &&
      window.matchMedia('(max-width: 760px)').matches
        ? 'semanal'
        : 'mensal'
    );

  const [mesVisivel, setMesVisivel] = useState(() => {
    const dataHoje = new Date();
    return new Date(dataHoje.getFullYear(), dataHoje.getMonth(), 1);
  });

  const [inicioSemana, setInicioSemana] = useState(hoje);
  const [diaSelecionado, setDiaSelecionado] = useState<string | null>(null);

  const diasDoMes = useMemo(() => criarGradeMes(mesVisivel), [mesVisivel]);
  const diasDaSemana = useMemo(() => criarGradeSemana(inicioSemana), [inicioSemana]);

  const tarefasPorData = useMemo(() => {
    const mapa = new Map<string, Tarefa[]>();
    for (const t of tarefas) {
      const data = t.dataVencimento || t.data;
      if (data) {
        const lista = mapa.get(data) || [];
        lista.push(t);
        mapa.set(data, lista);
      }
    }
    return mapa;
  }, [tarefas]);

  const tarefasDoDiaSelecionado = useMemo(() => {
    if (!diaSelecionado) return [];
    return tarefasPorData.get(diaSelecionado) || [];
  }, [diaSelecionado, tarefasPorData]);

  const trocarMes = (delta: number) => {
    setMesVisivel((mes) => new Date(mes.getFullYear(), mes.getMonth() + delta, 1));
  };

  const trocarSemana = (delta: number) => {
    setInicioSemana((inicio) => adicionarDias(inicio, delta * 7));
  };

  const irParaHojeNoCalendario = () => {
    const dataHoje = parseDataLocal(hoje);
    setMesVisivel(new Date(dataHoje.getFullYear(), dataHoje.getMonth(), 1));
    setInicioSemana(hoje);
  };

  const abrirDia = (data: string) => {
    setDiaSelecionado(data);
  };

  const getCorTarefa = (t: Tarefa): string => {
    if (t.disciplinaCor) return t.disciplinaCor;
    if (t.origem === 'ciclo' || t.disciplinaNome) {
      return 'var(--purple)';
    }
    const p = String(t.prioridade || '').toUpperCase();
    if (p === 'ALTA' || p === 'URGENTE') return 'var(--red, #ef4444)';
    if (p === 'MEDIA') return 'var(--yellow, #eab308)';
    if (p === 'BAIXA') return 'var(--blue, #3b82f6)';
    return 'var(--primary)';
  };

  return (
    <section className={styles.calendarSection} aria-labelledby="calendar-title">
      {/* ── BARRA DE CONTROLE DO CALENDÁRIO (TOOLBAR) ── */}
      <div className={styles.calendarToolbar}>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => (visualizacaoCalendario === 'mensal' ? trocarMes(-1) : trocarSemana(-1))}
          aria-label={visualizacaoCalendario === 'mensal' ? 'Mês anterior' : 'Período anterior'}
        >
          <ArrowLeft size={16} />
        </Button>

        <h3 id="calendar-title">
          {visualizacaoCalendario === 'mensal'
            ? mesVisivel.toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' })
            : `${diasDaSemana[0].toLocaleDateString('pt-BR', { day: 'numeric', month: 'short' })} – ${diasDaSemana[6].toLocaleDateString(
                'pt-BR',
                {
                  day: 'numeric',
                  month: 'short',
                  year: 'numeric'
                }
              )}`}
        </h3>

        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => (visualizacaoCalendario === 'mensal' ? trocarMes(1) : trocarSemana(1))}
          aria-label={visualizacaoCalendario === 'mensal' ? 'Próximo mês' : 'Próximo período'}
        >
          <ArrowRight size={16} />
        </Button>

        <Button
          type="button"
          variant="secondary"
          size="sm"
          onClick={irParaHojeNoCalendario}
        >
          Hoje
        </Button>

        <div className={styles.calendarViewToggle} role="group" aria-label="Visualização do calendário">
          <Button
            type="button"
            variant="secondary"
            size="sm"
            selected={visualizacaoCalendario === 'mensal'}
            onClick={() => setVisualizacaoCalendario('mensal')}
            aria-pressed={visualizacaoCalendario === 'mensal'}
          >
            Mensal
          </Button>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            selected={visualizacaoCalendario === 'semanal'}
            onClick={() => setVisualizacaoCalendario('semanal')}
            aria-pressed={visualizacaoCalendario === 'semanal'}
          >
            Semanal
          </Button>
        </div>
      </div>

      {/* ── GRADE MENSAL OU LISTA SEMANAL ── */}
      {visualizacaoCalendario === 'mensal' ? (
        <div className={styles.calendarGrid} role="group" aria-label="Calendário mensal">
          {NOMES_DIAS.map((dia) => (
            <span key={dia} className={styles.calendarWeekday} aria-hidden="true">
              {dia}
            </span>
          ))}

          {diasDoMes.map((dia) => {
            const data = dataLocal(dia);
            const tarefasDoDia = tarefasPorData.get(data) || [];
            const doMesAtual = dia.getMonth() === mesVisivel.getMonth();
            const quantidadeVisivel = 3;

            return (
              <Button
                type="button"
                variant="ghost"
                key={data}
                className={[
                  styles.calendarDay,
                  doMesAtual ? '' : styles.outsideMonth,
                  data === hoje ? styles.today : '',
                  diaSelecionado === data ? styles.selectedDay : ''
                ]
                  .filter(Boolean)
                  .join(' ')}
                onClick={() => abrirDia(data)}
                aria-label={`${dia.toLocaleDateString('pt-BR')}, ${tarefasDoDia.length} tarefas`}
                selected={diaSelecionado === data}
                aria-pressed={diaSelecionado === data}
              >
                <span className={styles.calendarDate}>{dia.getDate()}</span>
                <span className={styles.calendarSubjects}>
                  {tarefasDoDia.slice(0, quantidadeVisivel).map((tarefa) => (
                    <span className={styles.calendarSubject} key={tarefa.id}>
                      <DisciplineLabel
                        className={`${styles.calendarSubjectLabel} ${
                          tarefa.concluida ? styles.calendarSubjectLabelCompleted : ''
                        }`}
                        name={tarefa.titulo}
                        color={getCorTarefa(tarefa)}
                      />
                    </span>
                  ))}
                  {tarefasDoDia.length > quantidadeVisivel && (
                    <span className={styles.calendarMore}>
                      +{tarefasDoDia.length - quantidadeVisivel}
                    </span>
                  )}
                </span>
              </Button>
            );
          })}
        </div>
      ) : (
        <div className={styles.weekCalendar} aria-label="Calendário semanal">
          {diasDaSemana.map((dia) => {
            const data = dataLocal(dia);
            const tarefasDoDia = tarefasPorData.get(data) || [];

            return (
              <button
                type="button"
                className={[
                  styles.weekDay,
                  data === hoje ? styles.weekDayToday : ''
                ]
                  .filter(Boolean)
                  .join(' ')}
                key={data}
                onClick={() => abrirDia(data)}
                aria-label={`Ver detalhes de ${dia.toLocaleDateString('pt-BR')}`}
              >
                <span className={styles.weekDayHeader}>
                  <span className={styles.weekDayTitle}>
                    {dia.toLocaleDateString('pt-BR', {
                      weekday: 'long',
                      day: 'numeric',
                      month: 'long'
                    })}
                  </span>
                  {data === hoje && <span className={styles.todayLabel}>Hoje</span>}
                  {tarefasDoDia.length > 0 && (
                    <span className={styles.weekDayCount}>
                      {tarefasDoDia.length} {tarefasDoDia.length === 1 ? 'tarefa' : 'tarefas'}
                    </span>
                  )}
                </span>
                {tarefasDoDia.length > 0 ? (
                  <span className={styles.weekDaySubjects}>
                    {tarefasDoDia.map((tarefa) => (
                      <span key={tarefa.id}>
                        <DisciplineLabel
                          className={tarefa.concluida ? styles.calendarSubjectLabelCompleted : undefined}
                          name={tarefa.titulo}
                          color={getCorTarefa(tarefa)}
                        />
                      </span>
                    ))}
                  </span>
                ) : (
                  <span className={styles.weekDayEmpty}>Sem tarefas planejadas</span>
                )}
              </button>
            );
          })}
        </div>
      )}

      {/* ── MODAL DE DETALHES DO DIA SELECIONADO ── */}
      <Modal
        isOpen={diaSelecionado !== null}
        onClose={() => setDiaSelecionado(null)}
        title={
          diaSelecionado
            ? parseDataLocal(diaSelecionado).toLocaleDateString('pt-BR', {
                weekday: 'long',
                day: 'numeric',
                month: 'long'
              })
            : 'Tarefas do dia'
        }
        subtitle={
          diaSelecionado
            ? `${tarefasDoDiaSelecionado.length} ${
                tarefasDoDiaSelecionado.length === 1 ? 'tarefa' : 'tarefas'
              } · ${tarefasDoDiaSelecionado.filter((t) => t.concluida).length} concluídas.`
            : ''
        }
        icon={<CalendarDays size={20} />}
        maxWidth="lg"
        footer={
          <div className={styles.modalFooterActions}>
            <Button
              type="button"
              variant="primary"
              size="md"
              leftIcon={<Plus size={16} />}
              onClick={() => {
                if (diaSelecionado) {
                  onAdicionarNoDia(diaSelecionado);
                  setDiaSelecionado(null);
                }
              }}
            >
              Nova tarefa neste dia
            </Button>
            <Button
              type="button"
              variant="secondary"
              size="md"
              onClick={() => setDiaSelecionado(null)}
            >
              Fechar
            </Button>
          </div>
        }
      >
        {tarefasDoDiaSelecionado.length > 0 ? (
          <div className={styles.dayBlockList}>
            {tarefasDoDiaSelecionado.map((tarefa) => {
              const isCiclo = tarefa.origem === 'ciclo';
              const subtarefasConcluidas =
                tarefa.subtarefas?.filter((st) => st.concluida).length || 0;
              const totalSubtarefas = tarefa.subtarefas?.length || 0;

              return (
                <article className={styles.dayBlock} key={tarefa.id}>
                  <div>
                    <div className={styles.dayBlockHeader}>
                      <Badge variant={tarefa.concluida ? 'success' : 'neutral'} size="sm">
                        {tarefa.concluida ? 'Concluída' : 'Pendente'}
                      </Badge>
                      {isCiclo && (
                        <Badge variant="secondary" size="sm">
                          Ciclo de Estudo
                        </Badge>
                      )}
                      {tarefa.prioridade && (
                        <Badge
                          variant={
                            tarefa.prioridade === 'ALTA'
                              ? 'danger'
                              : tarefa.prioridade === 'MEDIA'
                              ? 'warning'
                              : 'neutral'
                          }
                          size="sm"
                        >
                          Prioridade {tarefa.prioridade}
                        </Badge>
                      )}
                    </div>

                    <div className={styles.dayBlockSubject}>
                      <DisciplineLabel
                        name={tarefa.titulo}
                        color={getCorTarefa(tarefa)}
                      />
                    </div>

                    {tarefa.horario && (
                      <span className={styles.dayBlockMeta}>Horário: {tarefa.horario}</span>
                    )}
                    {totalSubtarefas > 0 && (
                      <span className={styles.dayBlockMeta}>
                        {subtarefasConcluidas} / {totalSubtarefas} subtarefas concluídas
                      </span>
                    )}
                    {tarefa.observacoes && (
                      <p className={styles.dayBlockNotes}>{tarefa.observacoes}</p>
                    )}
                  </div>

                  <div className={styles.dayBlockActions}>
                    <Button
                      type="button"
                      variant={tarefa.concluida ? 'warning' : 'success'}
                      size="md"
                      leftIcon={tarefa.concluida ? <Undo2 size={16} /> : <Check size={16} />}
                      onClick={() => onAlternarTarefa(tarefa.id, !tarefa.concluida)}
                      aria-pressed={tarefa.concluida}
                    >
                      {tarefa.concluida ? 'Desmarcar' : 'Concluir'}
                    </Button>
                    {!isCiclo && onEditarTarefa && (
                      <Button
                        type="button"
                        variant="secondary"
                        size="md"
                        leftIcon={<Edit2 size={16} />}
                        onClick={() => {
                          onEditarTarefa(tarefa);
                          setDiaSelecionado(null);
                        }}
                      >
                        Editar
                      </Button>
                    )}
                  </div>
                </article>
              );
            })}
          </div>
        ) : (
          <div className={styles.emptyDayContainer}>
            <p className={styles.weekDayEmpty}>Nenhuma tarefa agendada para este dia.</p>
            <Button
              type="button"
              variant="primary"
              size="md"
              leftIcon={<Plus size={16} />}
              onClick={() => {
                if (diaSelecionado) {
                  onAdicionarNoDia(diaSelecionado);
                  setDiaSelecionado(null);
                }
              }}
            >
              Adicionar tarefa neste dia
            </Button>
          </div>
        )}
      </Modal>
    </section>
  );
};
