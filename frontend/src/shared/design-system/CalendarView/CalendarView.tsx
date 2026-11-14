import React, { useState, useMemo, useEffect, useRef } from 'react';
import {
  ArrowLeft,
  ArrowRight,
  CalendarDays,
  Plus
} from 'lucide-react';
import { Button } from '../Button/Button';
import { Modal } from '../Modal/Modal';
import { Badge, type BadgeProps } from '../Badge/Badge';
import { DisciplineLabel } from '../DisciplineLabel/DisciplineLabel';
import {
  hojeISO,
  dataLocal,
  parseDataLocal,
  adicionarDias,
  criarGradeMes,
  criarGradeSemana,
  NOMES_DIAS
} from './dateUtils';
import styles from './CalendarView.module.css';

export type VisualizacaoCalendario = 'mensal' | 'semanal';

export interface CalendarBadge {
  label: string;
  variant?: BadgeProps['variant'];
}

export interface CalendarItem {
  id: string;
  data: string; // yyyy-MM-dd
  titulo: string;
  cor: string;
  concluido: boolean;
  badges?: CalendarBadge[];
  metadados?: string[];
  observacoes?: string;
}

interface CalendarViewLabels {
  itemSingular?: string;
  itemPlural?: string;
  concluidoSingular?: string;
  concluidoPlural?: string;
  pendenteLabel?: string;
  concluidoLabel?: string;
  emptyWeekLabel?: string;
  emptyDayLabel?: string;
  defaultModalTitle?: string;
  adicionarNoDiaLabel?: string;
}

export interface CalendarVisibleRange {
  dataInicio: string;
  dataFim: string;
  modo: VisualizacaoCalendario;
}

export interface SharedCalendarViewProps<T extends CalendarItem = CalendarItem> {
  items: T[];
  onVisibleRangeChange?: (range: CalendarVisibleRange) => void;
  renderItemActions?: (item: T, helpers: { fecharModalDia: () => void }) => React.ReactNode;
  onAdicionarNoDia?: (dataISO: string) => void;
  labels?: CalendarViewLabels;
  ariaLabel?: string;
}

export function SharedCalendarView<T extends CalendarItem = CalendarItem>({
  items,
  onVisibleRangeChange,
  renderItemActions,
  onAdicionarNoDia,
  labels,
  ariaLabel = 'Calendário'
}: SharedCalendarViewProps<T>) {
  const hoje = hojeISO();

  const itemSingular = labels?.itemSingular ?? 'tarefa';
  const itemPlural = labels?.itemPlural ?? 'tarefas';
  const concluidoSingular = labels?.concluidoSingular ?? 'concluídas';
  const concluidoPlural = labels?.concluidoPlural ?? 'concluídas';
  const pendenteLabel = labels?.pendenteLabel ?? 'Pendente';
  const concluidoLabel = labels?.concluidoLabel ?? 'Concluída';
  const emptyWeekLabel = labels?.emptyWeekLabel ?? 'Sem tarefas planejadas';
  const emptyDayLabel = labels?.emptyDayLabel ?? 'Nenhuma tarefa agendada para este dia.';
  const defaultModalTitle = labels?.defaultModalTitle ?? 'Tarefas do dia';
  const adicionarNoDiaLabel = labels?.adicionarNoDiaLabel ?? 'Nova tarefa neste dia';

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

  const dataInicioVisivel = useMemo(
    () =>
      visualizacaoCalendario === 'mensal'
        ? dataLocal(diasDoMes[0])
        : dataLocal(diasDaSemana[0]),
    [visualizacaoCalendario, diasDoMes, diasDaSemana]
  );

  const dataFimVisivel = useMemo(
    () =>
      visualizacaoCalendario === 'mensal'
        ? dataLocal(diasDoMes[diasDoMes.length - 1])
        : dataLocal(diasDaSemana[diasDaSemana.length - 1]),
    [visualizacaoCalendario, diasDoMes, diasDaSemana]
  );

  const onVisibleRangeChangeRef = useRef(onVisibleRangeChange);

  useEffect(() => {
    onVisibleRangeChangeRef.current = onVisibleRangeChange;
  }, [onVisibleRangeChange]);

  useEffect(() => {
    onVisibleRangeChangeRef.current?.({
      dataInicio: dataInicioVisivel,
      dataFim: dataFimVisivel,
      modo: visualizacaoCalendario
    });
  }, [dataInicioVisivel, dataFimVisivel, visualizacaoCalendario]);

  const itemsPorData = useMemo(() => {
    const mapa = new Map<string, T[]>();
    for (const item of items) {
      if (item.data) {
        const lista = mapa.get(item.data) || [];
        lista.push(item);
        mapa.set(item.data, lista);
      }
    }
    return mapa;
  }, [items]);

  const itemsDoDiaSelecionado = useMemo(() => {
    if (!diaSelecionado) return [];
    return itemsPorData.get(diaSelecionado) || [];
  }, [diaSelecionado, itemsPorData]);

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

  const fecharModalDia = () => {
    setDiaSelecionado(null);
  };

  const concluidosCount = itemsDoDiaSelecionado.filter((item) => item.concluido).length;

  return (
    <section className={styles.calendarSection} aria-label={ariaLabel}>
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
            const itemsDoDia = itemsPorData.get(data) || [];
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
                aria-label={`${dia.toLocaleDateString('pt-BR')}, ${itemsDoDia.length} ${itemsDoDia.length === 1 ? itemSingular : itemPlural}`}
                selected={diaSelecionado === data}
                aria-pressed={diaSelecionado === data}
              >
                <span className={styles.calendarDate}>{dia.getDate()}</span>
                <span className={styles.calendarSubjects}>
                  {itemsDoDia.slice(0, quantidadeVisivel).map((item) => (
                    <span className={styles.calendarSubject} key={item.id}>
                      <DisciplineLabel
                        className={`${styles.calendarSubjectLabel} ${
                          item.concluido ? styles.calendarSubjectLabelCompleted : ''
                        }`}
                        name={item.titulo}
                        color={item.cor}
                      />
                    </span>
                  ))}
                  {itemsDoDia.length > quantidadeVisivel && (
                    <span className={styles.calendarMore}>
                      +{itemsDoDia.length - quantidadeVisivel}
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
            const itemsDoDia = itemsPorData.get(data) || [];

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
                  {itemsDoDia.length > 0 && (
                    <span className={styles.weekDayCount}>
                      {itemsDoDia.length} {itemsDoDia.length === 1 ? itemSingular : itemPlural}
                    </span>
                  )}
                </span>
                {itemsDoDia.length > 0 ? (
                  <span className={styles.weekDaySubjects}>
                    {itemsDoDia.map((item) => (
                      <span key={item.id}>
                        <DisciplineLabel
                          className={item.concluido ? styles.calendarSubjectLabelCompleted : undefined}
                          name={item.titulo}
                          color={item.cor}
                        />
                      </span>
                    ))}
                  </span>
                ) : (
                  <span className={styles.weekDayEmpty}>{emptyWeekLabel}</span>
                )}
              </button>
            );
          })}
        </div>
      )}

      {/* ── MODAL DE DETALHES DO DIA SELECIONADO ── */}
      <Modal
        isOpen={diaSelecionado !== null}
        onClose={fecharModalDia}
        title={
          diaSelecionado
            ? parseDataLocal(diaSelecionado).toLocaleDateString('pt-BR', {
                weekday: 'long',
                day: 'numeric',
                month: 'long'
              })
            : defaultModalTitle
        }
        subtitle={
          diaSelecionado
            ? `${itemsDoDiaSelecionado.length} ${
                itemsDoDiaSelecionado.length === 1 ? itemSingular : itemPlural
              } · ${concluidosCount} ${concluidosCount === 1 ? concluidoSingular : concluidoPlural}.`
            : ''
        }
        icon={<CalendarDays size={20} />}
        maxWidth="lg"
        footer={
          <div
            className={`${styles.modalFooterActions} ${
              !onAdicionarNoDia ? styles.modalFooterActionsSingle : ''
            }`}
          >
            {onAdicionarNoDia && (
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
                {adicionarNoDiaLabel}
              </Button>
            )}
            <Button
              type="button"
              variant="secondary"
              size="md"
              onClick={fecharModalDia}
            >
              Fechar
            </Button>
          </div>
        }
      >
        {itemsDoDiaSelecionado.length > 0 ? (
          <div className={styles.dayBlockList}>
            {itemsDoDiaSelecionado.map((item) => (
              <article className={styles.dayBlock} key={item.id}>
                <div>
                  <div className={styles.dayBlockHeader}>
                    <Badge variant={item.concluido ? 'success' : 'neutral'} size="sm">
                      {item.concluido ? concluidoLabel : pendenteLabel}
                    </Badge>
                    {item.badges?.map((badge, idx) => (
                      <Badge key={`${item.id}-badge-${idx}`} variant={badge.variant ?? 'secondary'} size="sm">
                        {badge.label}
                      </Badge>
                    ))}
                  </div>

                  <div className={styles.dayBlockSubject}>
                    <DisciplineLabel
                      name={item.titulo}
                      color={item.cor}
                    />
                  </div>

                  {item.metadados?.map((meta, idx) => (
                    <span key={`${item.id}-meta-${idx}`} className={styles.dayBlockMeta}>
                      {meta}
                    </span>
                  ))}
                  {item.observacoes && (
                    <p className={styles.dayBlockNotes}>{item.observacoes}</p>
                  )}
                </div>

                {renderItemActions && (
                  <div className={styles.dayBlockActions}>
                    {renderItemActions(item, { fecharModalDia })}
                  </div>
                )}
              </article>
            ))}
          </div>
        ) : (
          <div className={styles.emptyDayContainer}>
            <p className={styles.weekDayEmpty}>{emptyDayLabel}</p>
            {onAdicionarNoDia && (
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
            )}
          </div>
        )}
      </Modal>
    </section>
  );
}
