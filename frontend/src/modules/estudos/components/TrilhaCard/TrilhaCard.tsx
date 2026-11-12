import React from 'react';
import styles from './TrilhaCard.module.css';

interface TrilhaCardMetrica {
  label: string;
  value: React.ReactNode;
  tone?: 'urgente' | 'sucesso';
}

export interface TrilhaCardProps {
  nome: string;
  subtitulo: string;
  detalhe?: string;
  selo?: React.ReactNode;
  acao?: React.ReactNode;
  metricas: TrilhaCardMetrica[];
  ativo?: boolean;
  onClick: () => void;
}

export const TrilhaGrid: React.FC<{ children: React.ReactNode; className?: string }> = ({
  children,
  className = ''
}) => (
  <div className={`${styles.grid} ${className}`}>{children}</div>
);

export const TrilhaCardTag: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <span className={styles.tagAtiva}>{children}</span>
);

export const trilhaCardStyles = styles;

export const TrilhaCard: React.FC<TrilhaCardProps> = ({
  nome,
  subtitulo,
  detalhe,
  selo,
  acao,
  metricas,
  ativo = false,
  onClick
}) => (
  <article
    className={`${styles.card} ${ativo ? styles.cardAtivo : ''}`}
    aria-current={ativo ? 'true' : undefined}
  >
    <div className={styles.cardHeader}>
      <div className={styles.cardTopRow}>
        <div className={styles.bancaBadgeRow}>{selo}</div>
        <div className={styles.topRightActions}>{acao}</div>
      </div>
      <h3 className={styles.trilhaNome}>
        <button type="button" className={styles.cardLink} onClick={onClick}>
          {nome}
        </button>
      </h3>
      <p className={styles.cargoText}>{subtitulo}</p>
      {detalhe && <span className={styles.esferaText}>{detalhe}</span>}
    </div>

    <div className={styles.metricsGrid}>
      {metricas.map((metrica) => (
        <div key={metrica.label} className={styles.metricItem}>
          <span className={styles.metricLabel}>{metrica.label}</span>
          <span
            className={[
              styles.metricVal,
              metrica.tone === 'urgente' ? styles.valUrgente : '',
              metrica.tone === 'sucesso' ? styles.metricSuccess : ''
            ].filter(Boolean).join(' ')}
          >
            {metrica.value}
          </span>
        </div>
      ))}
    </div>
  </article>
);
