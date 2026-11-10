import React, { useMemo } from 'react';
import { Pencil } from 'lucide-react';
import { Button, Select } from '@shared/design-system';
import { Trilha } from '../../types/trilhara';
import { diasAte } from '../../utils/format';
import styles from './TrilhaPod.module.css';

export interface MobileTrilhaControlsProps {
  trilhas: Trilha[];
  trilhaAtiva?: Trilha | null;
  onSelecionarTrilha: (id: string) => void;
  onAbrirEditarTrilha: () => void;
}

export const MobileTrilhaControls: React.FC<MobileTrilhaControlsProps> = ({
  trilhas,
  trilhaAtiva,
  onSelecionarTrilha,
  onAbrirEditarTrilha
}) => {
  const prazo = diasAte(trilhaAtiva?.dataProva);
  const trilhaOptions = useMemo(
    () =>
      trilhas.map((trilha) => ({
        value: trilha.id,
        label: trilha.nome
      })),
    [trilhas]
  );

  if (trilhas.length === 0) return null;

  return (
    <>
      <div className={styles.mobileSelectWrap}>
        <Select
          value={trilhaAtiva?.id || ''}
          onChange={onSelecionarTrilha}
          className={styles.mobileSelect}
          options={trilhaOptions}
          aria-label="Trilha ativa"
        />
      </div>

      {trilhaAtiva?.dataProva && (
        <button
          type="button"
          className={`${styles.prazoBadge} ${prazo.isUrgente ? styles.prazoUrgente : ''}`}
          onClick={onAbrirEditarTrilha}
          title="Editar data da prova"
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
    </>
  );
};

