import React from 'react';
import { Clock, Plus, Route } from 'lucide-react';
import { Button, useSidebar } from '@shared/design-system';
import { FiltroVisualizacao } from '../../types/tarefa';
import styles from './TarefaContextPod.module.css';

export interface TarefaContextPodProps {
  onAbrirNovaTarefa: () => void;
  filtroAtivo: FiltroVisualizacao;
  temListaOuTrilhaAtiva?: boolean;
  onNavegar: (filtro: FiltroVisualizacao) => void;
  contadores: {
    hoje: number;
    concluidasHoje?: number;
    totalHoje?: number;
    noFluxo?: number;
  };
}

export const TarefaContextPod: React.FC<TarefaContextPodProps> = ({
  onAbrirNovaTarefa,
  filtroAtivo,
  temListaOuTrilhaAtiva = false,
  onNavegar,
  contadores
}) => {
  const { collapsed } = useSidebar();

  const dataHojeFormatada = new Date().toLocaleDateString('pt-BR', {
    weekday: 'short',
    day: 'numeric',
    month: 'short'
  });

  const concluidasHoje = contadores.concluidasHoje ?? 0;
  const totalHoje = contadores.totalHoje ?? contadores.hoje + concluidasHoje;
  const percentualProgresso = totalHoje > 0 ? Math.round((concluidasHoje / totalHoje) * 100) : 0;

  const isFiltroAtivo = (id: FiltroVisualizacao) => filtroAtivo === id && !temListaOuTrilhaAtiva;

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

        <button
          type="button"
          className={`${styles.collapsedCardBtn} ${isFiltroAtivo('hoje') ? styles.collapsedCardActive : ''}`}
          onClick={() => onNavegar('hoje')}
          title={`Hoje: ${contadores.hoje} pendentes`}
          aria-label="Hoje"
          aria-current={isFiltroAtivo('hoje') ? 'page' : undefined}
        >
          <Clock size={18} />
        </button>

        <button
          type="button"
          className={`${styles.collapsedCardBtn} ${styles.collapsedFluxoBtn} ${isFiltroAtivo('fluxo') ? styles.collapsedCardActive : ''}`}
          onClick={() => onNavegar('fluxo')}
          title="Abrir Fluxo de Tarefas"
          aria-label="Fluxo de Tarefas"
          aria-current={isFiltroAtivo('fluxo') ? 'page' : undefined}
        >
          <Route size={18} />
        </button>
      </div>
    );
  }

  return (
    <div className={styles.pod}>
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

      {/* Cartão "Hoje" */}
      <div
        role="button"
        tabIndex={0}
        className={`${styles.tarefasDoDiaCard} ${isFiltroAtivo('hoje') ? styles.tarefasDoDiaCardAtivo : ''}`}
        onClick={() => onNavegar('hoje')}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onNavegar('hoje');
          }
        }}
        title="Visualizar tarefas de hoje"
        aria-label="Hoje"
        aria-current={isFiltroAtivo('hoje') ? 'page' : undefined}
      >
        <div className={styles.tarefasDoDiaTop}>
          <div className={styles.tarefasDoDiaHeader}>
            <div className={styles.tarefasDoDiaIconWrap}>
              <Clock size={15} />
            </div>
            <span className={styles.tarefasDoDiaTitle}>Hoje</span>
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

      {/* Cartão "Fluxo de Tarefas" */}
      <button
        type="button"
        className={`${styles.tarefasDoDiaCard} ${styles.fluxoCardSurface} ${isFiltroAtivo('fluxo') ? styles.fluxoCardAtivo : ''}`}
        onClick={() => onNavegar('fluxo')}
        title="Abrir tela infinita do fluxo de tarefas"
        aria-label="Abrir Fluxo de Tarefas"
        aria-current={isFiltroAtivo('fluxo') ? 'page' : undefined}
      >
        <div className={styles.tarefasDoDiaTop}>
          <div className={styles.tarefasDoDiaHeader}>
            <div className={`${styles.tarefasDoDiaIconWrap} ${styles.fluxoCardIconWrap}`}>
              <Route size={15} />
            </div>
            <span className={styles.tarefasDoDiaTitle}>Fluxo de Tarefas</span>
          </div>
          <span className={styles.fluxoCardBadge}>
            {(contadores.noFluxo ?? 0) > 0 ? `${contadores.noFluxo} no grafo` : 'Grafo'}
          </span>
        </div>
        <span className={styles.fluxoCardDesc}>
          Grafo visual com antecessoras, pares e sucessoras
        </span>
      </button>
    </div>
  );
};
