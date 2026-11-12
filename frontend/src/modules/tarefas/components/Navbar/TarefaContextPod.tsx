import React from 'react';
import { Plus, CalendarCheck2 } from 'lucide-react';
import { Button, useSidebar } from '@shared/design-system';
import { FiltroVisualizacao } from '../../types/tarefa';
import { NAV_ITEMS_TAREFAS } from './navigationConfig';
import styles from './TarefaContextPod.module.css';

export interface TarefaContextPodProps {
  onAbrirNovaTarefa: () => void;
  filtroAtivo: FiltroVisualizacao;
  listaSelecionadaId?: string;
  onNavegar: (filtro: FiltroVisualizacao) => void;
  onSelecionarLista: (id?: string) => void;
  contadores: {
    hoje: number;
    concluidasHoje?: number;
    totalHoje?: number;
    proximas: number;
    concluidas: number;
    total: number;
  };
}

export const TarefaContextPod: React.FC<TarefaContextPodProps> = ({
  onAbrirNovaTarefa,
  filtroAtivo,
  listaSelecionadaId,
  onNavegar,
  onSelecionarLista,
  contadores
}) => {
  const { collapsed } = useSidebar();

  const dataHoje = new Date();
  const dataHojeFormatada = dataHoje.toLocaleDateString('pt-BR', {
    weekday: 'short',
    day: 'numeric',
    month: 'short'
  });

  const concluidasHoje = contadores.concluidasHoje ?? 0;
  const totalHoje = contadores.totalHoje ?? (contadores.hoje + concluidasHoje);
  const percentualProgresso = totalHoje > 0 ? Math.round((concluidasHoje / totalHoje) * 100) : 0;

  const isFiltroAtivo = (id: FiltroVisualizacao) => {
    return filtroAtivo === id && !listaSelecionadaId;
  };

  const handleSelectFiltro = (id: FiltroVisualizacao) => {
    onSelecionarLista(undefined);
    onNavegar(id);
  };

  const getBadgeCount = (id: FiltroVisualizacao): number | undefined => {
    if (id === 'hoje') return contadores.hoje > 0 ? contadores.hoje : undefined;
    if (id === 'proximas') return contadores.proximas > 0 ? contadores.proximas : undefined;
    if (id === 'concluidas') return contadores.concluidas > 0 ? contadores.concluidas : undefined;
    if (id === 'todas') return contadores.total > 0 ? contadores.total : undefined;
    return undefined;
  };

  if (collapsed) {
    return (
      <div className={styles.podCollapsed}>
        <button
          type="button"
          className={styles.collapsedBtn}
          onClick={onAbrirNovaTarefa}
          title="Nova tarefa"
          aria-label="Nova tarefa"
        >
          <Plus size={18} />
        </button>

        {/* Card compacto de Tarefas do dia */}
        <button
          type="button"
          className={`${styles.collapsedCardBtn} ${isFiltroAtivo('hoje') ? styles.collapsedFilterActive : ''}`}
          onClick={() => handleSelectFiltro('hoje')}
          title={`Tarefas do dia: ${contadores.hoje} pendentes`}
          aria-label="Tarefas do dia"
        >
          <CalendarCheck2 size={18} />
        </button>

        {/* Ícones dos Filtros */}
        <div className={styles.filtrosList}>
          {NAV_ITEMS_TAREFAS.map((item) => {
            const Icon = item.icon;
            const ativo = isFiltroAtivo(item.id);
            const badge = getBadgeCount(item.id);

            return (
              <button
                key={item.id}
                type="button"
                className={`${styles.collapsedFilterItem} ${ativo ? styles.collapsedFilterActive : ''}`}
                onClick={() => handleSelectFiltro(item.id)}
                title={badge ? `${item.label} (${badge})` : item.label}
                aria-label={item.label}
              >
                <Icon size={18} />
              </button>
            );
          })}
        </div>
      </div>
    );
  }

  return (
    <div className={styles.pod}>
      {/* ── BOTÃO DE CRIAÇÃO PRINCIPAL ── */}
      <Button
        variant="primary"
        size="sm"
        leftIcon={<Plus size={15} />}
        fullWidth
        className={styles.actionBtn}
        onClick={onAbrirNovaTarefa}
      >
        Nova Tarefa
      </Button>

      {/* ── CARD "TAREFAS DO DIA" (cartão de tarefas do dia) ── */}
      <div
        role="button"
        tabIndex={0}
        className={`${styles.tarefasDoDiaCard} ${isFiltroAtivo('hoje') ? styles.tarefasDoDiaCardAtivo : ''}`}
        onClick={() => handleSelectFiltro('hoje')}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            handleSelectFiltro('hoje');
          }
        }}
        title="Visualizar tarefas de hoje"
        aria-label="Visualizar tarefas de hoje"
      >
        <div className={styles.tarefasDoDiaTop}>
          <div className={styles.tarefasDoDiaHeader}>
            <div className={styles.tarefasDoDiaIconWrap}>
              <CalendarCheck2 size={15} />
            </div>
            <span className={styles.tarefasDoDiaTitle}>Tarefas do dia</span>
          </div>
          <span className={styles.tarefasDoDiaDate}>{dataHojeFormatada}</span>
        </div>

        <div className={styles.tarefasDoDiaMeta}>
          <div className={styles.tarefasDoDiaStats}>
            {totalHoje === 0 ? (
              <span>Sem tarefas programadas</span>
            ) : (
              <>
                <span>
                  <strong>{contadores.hoje}</strong> {contadores.hoje === 1 ? 'pendente' : 'pendentes'}
                </span>
                {concluidasHoje > 0 && (
                  <span className={styles.tarefasDoDiaConcluidas}>
                    • {concluidasHoje} {concluidasHoje === 1 ? 'feita' : 'feitas'}
                  </span>
                )}
              </>
            )}
          </div>

          {totalHoje > 0 && (
            <div className={styles.progressBarBg} aria-hidden="true">
              <div
                className={styles.progressBarFill}
                style={{ width: `${percentualProgresso}%` }}
              />
            </div>
          )}
        </div>
      </div>

      {/* ── SEÇÃO DE FILTROS (INVERTIDO CONFORME SOLICITAÇÃO) ── */}
      <div className={styles.filtrosSection}>
        <span className={styles.filtrosHeader}>Filtros</span>

        <div className={styles.filtrosList}>
          {NAV_ITEMS_TAREFAS.map((item) => {
            const Icon = item.icon;
            const ativo = isFiltroAtivo(item.id);
            const badge = getBadgeCount(item.id);

            return (
              <button
                key={item.id}
                type="button"
                className={`${styles.filterItem} ${ativo ? styles.filterItemActive : ''}`}
                onClick={() => handleSelectFiltro(item.id)}
                title={item.title}
                aria-current={ativo ? 'page' : undefined}
              >
                <span className={styles.filterIconWrap}>
                  <Icon size={17} />
                </span>
                <span className={styles.filterLabel}>{item.label}</span>
                {badge !== undefined && (
                  <span className={styles.filterBadge}>{badge}</span>
                )}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
};
