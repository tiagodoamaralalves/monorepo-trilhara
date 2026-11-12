import React from 'react';
import { DisciplineLabel } from './DisciplineLabel';
import styles from './DisciplineRow.module.css';

export interface DisciplineRowProps {
  name: string;
  color?: string;
  topicCount?: number;
  topicButtonClassName?: string;
  onClick?: () => void;
  peso?: number;
  pesoContent?: React.ReactNode;
  actions?: React.ReactNode;
  nameContent?: React.ReactNode;
  className?: string;
}

export const DisciplineRow: React.FC<DisciplineRowProps> = ({
  name,
  color,
  topicCount,
  topicButtonClassName,
  onClick,
  peso,
  pesoContent,
  actions,
  nameContent,
  className = ''
}) => {
  const hasTopics = typeof topicCount === 'number';
  const isActive = Boolean(hasTopics && topicCount > 0);

  return (
    <li className={`${styles.disciplineItem} ${onClick ? styles.disciplineItemClickable : ''} ${className}`}>
      <span
        className={styles.disciplineRowContent}
        role={onClick ? 'button' : undefined}
        tabIndex={onClick ? 0 : undefined}
        onClick={onClick}
        onKeyDown={(event) => {
          if (!onClick || (event.key !== 'Enter' && event.key !== ' ')) return;
          event.preventDefault();
          onClick();
        }}
      >
      {nameContent ?? <DisciplineLabel name={name} color={color} className={styles.disciplineName} />}

      {hasTopics && (
        <span
          className={`${isActive ? styles.topicCountButtonActive : styles.topicCountButton} ${topicButtonClassName ?? ''}`}
        >
          {topicCount} {topicCount === 1 ? 'assunto' : 'assuntos'}
        </span>
      )}

      {pesoContent ?? (typeof peso === 'number' && (
        <span className={styles.disciplinePeso}>peso {peso}x</span>
      ))}

        {actions && <div className={styles.itemActions}>{actions}</div>}
      </span>
    </li>
  );
};

export interface TopicRowProps {
  index: number;
  name: string;
  leading?: React.ReactNode;
  content?: React.ReactNode;
  actions?: React.ReactNode;
  onClick?: () => void;
  className?: string;
}

export const TopicRow: React.FC<TopicRowProps> = ({
  index,
  name,
  leading,
  content,
  actions,
  onClick,
  className = ''
}) => {
  const isEditing = Boolean(content);
  const isClickable = Boolean(onClick && !isEditing);

  return (
    <li className={`${styles.topicItem} ${isClickable ? styles.topicItemClickable : ''} ${className}`}>
      <span
        className={styles.topicRowContent}
        role={isClickable ? 'button' : undefined}
        tabIndex={isClickable ? 0 : undefined}
        onClick={isClickable ? onClick : undefined}
        onKeyDown={(event) => {
          if (!isClickable) return;
          if (event.target !== event.currentTarget) return;
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            onClick?.();
          }
        }}
      >
      {leading}
      <span className={styles.topicNumber}>{index + 1}.</span>
      {content ?? <span className={styles.topicText}>{name}</span>}
        {actions && (
          <div className={styles.itemActions} onClickCapture={(event) => event.stopPropagation()}>
            {actions}
          </div>
        )}
      </span>
    </li>
  );
};
