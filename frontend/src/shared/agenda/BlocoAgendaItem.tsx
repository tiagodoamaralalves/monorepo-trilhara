import React from 'react';
import { BookOpen, Check } from 'lucide-react';
import { Badge, DisciplineLabel } from '../design-system';
import styles from './AgendaCard.module.css';

type StatusDataBloco = 'ATRASADO' | 'HOJE' | 'FUTURO';

export interface BlocoAgendaItemProps {
  disciplinaNome: string;
  disciplinaCor?: string;
  duracaoMinutos: number;
  trilhaNome?: string;
  data?: string;
  statusData?: StatusDataBloco;
  selecionado?: boolean;
  onConcluir: () => void;
  onDefinirFoco?: () => void;
}

const STATUS_BADGE: Record<
  StatusDataBloco,
  { variant: 'danger' | 'primary' | 'neutral'; label: string }
> = {
  ATRASADO: { variant: 'danger', label: 'Atrasado' },
  HOJE: { variant: 'primary', label: 'Hoje' },
  FUTURO: { variant: 'neutral', label: 'Futuro' }
};

export const BlocoAgendaItem: React.FC<BlocoAgendaItemProps> = ({
  disciplinaNome,
  disciplinaCor,
  duracaoMinutos,
  trilhaNome,
  data,
  statusData = 'HOJE',
  selecionado = false,
  onConcluir,
  onDefinirFoco
}) => {
  const badge = STATUS_BADGE[statusData] ?? STATUS_BADGE.HOJE;

  return (
    <article className={`${styles.card} ${selecionado ? styles.cardSelected : ''}`}>
      <div className={styles.cardRow}>
        <div className={styles.cardLead}>
          <button
            type="button"
            className={styles.blocoCheckBtn}
            onClick={(e) => {
              e.stopPropagation();
              onConcluir();
            }}
            title="Concluir bloco e registrar sessão"
            aria-label={`Concluir bloco de ${disciplinaNome}`}
          >
            <Check size={13} />
          </button>

          <div className={styles.cardTitleWrap}>
            {onDefinirFoco ? (
              <button
                type="button"
                className={styles.cardTrigger}
                onClick={onDefinirFoco}
                title="Definir como foco"
                aria-label="Definir como foco"
              >
                <DisciplineLabel name={disciplinaNome} color={disciplinaCor} />
              </button>
            ) : (
              <DisciplineLabel name={disciplinaNome} color={disciplinaCor} />
            )}
          </div>
        </div>

        <div className={styles.cardMeta}>
          <Badge variant="purple" size="md">
            <BookOpen size={12} aria-hidden="true" />
            <span>Bloco de estudo</span>
          </Badge>

          {trilhaNome && (
            <Badge variant="default" size="md">
              {trilhaNome}
            </Badge>
          )}

          {data && (
            <Badge variant="default" size="md">
              {data}
            </Badge>
          )}

          <Badge variant="secondary" size="md">
            {duracaoMinutos} min
          </Badge>

          <Badge variant={badge.variant} size="md">
            {badge.label}
          </Badge>
        </div>
      </div>
    </article>
  );
};
