import React, { useMemo } from 'react';
import { Pencil, Plus, Route } from 'lucide-react';
import { Button, Select, useSidebar } from '@shared/design-system';
import { TrilhaResumoDTO, TrilhaDetalheDTO } from '../../types/trilhara';
import { diasAte } from '../../utils/format';
import styles from './TrilhaPod.module.css';

export interface TrilhaPodProps {
  trilhas: TrilhaResumoDTO[];
  trilhaAtiva?: TrilhaDetalheDTO | null;
  onSelecionarTrilha: (id: string) => void;
  onAbrirEditarTrilha: () => void;
  onAbrirNovaTrilha?: () => void;
}

export const TrilhaPod: React.FC<TrilhaPodProps> = ({
  trilhas,
  trilhaAtiva,
  onSelecionarTrilha,
  onAbrirEditarTrilha,
  onAbrirNovaTrilha
}) => {
  const { collapsed } = useSidebar();

  const prazo = diasAte(trilhaAtiva?.dataProva);
  const trilhaOptions = useMemo(
    () =>
      trilhas.map((trilha) => ({
        value: trilha.id,
        label: trilha.nome
      })),
    [trilhas]
  );

  const progressoTrilha = useMemo(() => {
    if (!trilhaAtiva) return null;
    const total = trilhaAtiva.disciplinas.flatMap((d) => d.topicos).length;
    const concluidos = trilhaAtiva.topicosConcluidosIds.length;
    return { total, concluidos, pct: Math.round(trilhaAtiva.percentualProgresso ?? 0) };
  }, [trilhaAtiva]);

  if (trilhas.length === 0) {
    if (collapsed || !onAbrirNovaTrilha) return null;
    return (
      <div className={styles.emptyTrilhaPod}>
        <p className={styles.emptyTrilhaText}>Nenhuma trilha ativa no momento.</p>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          leftIcon={<Plus size={14} />}
          onClick={onAbrirNovaTrilha}
        >
          Criar Trilha
        </Button>
      </div>
    );
  }

  return (
    <div className={`${styles.trilhaPod} ${collapsed ? styles.trilhaPodCollapsed : ''}`}>
      {collapsed ? (
        <button
          type="button"
          className={styles.trilhaCollapsedButton}
          onClick={onAbrirEditarTrilha}
          title={
            trilhaAtiva
              ? `Trilha ativa: ${trilhaAtiva.nome}. Clique para editar.`
              : 'Selecionar trilha'
          }
          aria-label="Editar trilha ativa"
        >
          <Route size={18} className={styles.selectIcon} />
        </button>
      ) : (
        <>
          <div className={styles.trilhaPodHeader}>
            <span className={styles.trilhaPodLabel}>
              <Route size={12} className={styles.selectIcon} aria-hidden="true" />
              <span>Trilha ativa</span>
            </span>

            <div className={styles.trilhaPodActions}>
              {trilhaAtiva?.dataProva && (
                <button
                  type="button"
                  className={`${styles.prazoBadge} ${
                    prazo.isUrgente ? styles.prazoUrgente : ''
                  }`}
                  onClick={onAbrirEditarTrilha}
                  title="Editar data da prova"
                  aria-label={`Editar trilha. ${prazo.label} até a prova`}
                >
                  {prazo.label}
                </button>
              )}

              {trilhaAtiva && (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  iconOnly
                  className={styles.editTrilhaButton}
                  onClick={onAbrirEditarTrilha}
                  title="Editar trilha ativa"
                  aria-label="Editar trilha ativa"
                >
                  <Pencil size={15} />
                </Button>
              )}
            </div>
          </div>

          <div className={styles.selectWrapper}>
            <Select
              value={trilhaAtiva?.id || ''}
              onChange={onSelecionarTrilha}
              className={styles.selectControl}
              title="Alternar trilha ativa"
              options={trilhaOptions}
              aria-label="Trilha ativa"
            />
          </div>

          {progressoTrilha && (
            <div className={styles.trilhaProgressBlock}>
              <div className={styles.trilhaProgressMeta}>
                <span>Progresso do edital</span>
                <strong>
                  {progressoTrilha.concluidos}/{progressoTrilha.total} ({progressoTrilha.pct}%)
                </strong>
              </div>
              <div className={styles.trilhaProgressBar} aria-hidden="true">
                <span
                  className={styles.trilhaProgressFill}
                  style={{ width: `${progressoTrilha.pct}%` }}
                />
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
};

