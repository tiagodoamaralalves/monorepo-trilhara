import React from 'react';
import { QuickAddInput } from '../design-system';
import styles from './QuickAddTask.module.css';

export interface QuickAddTaskProps {
  onAdicionar: (titulo: string) => Promise<boolean> | boolean;
  disabled?: boolean;
  className?: string;
}

export const QuickAddTask: React.FC<QuickAddTaskProps> = ({
  onAdicionar,
  disabled,
  className = ''
}) => {
  return (
    <QuickAddInput
      className={`${styles.wrapper} ${className}`.trim()}
      onAdd={onAdicionar}
      placeholder="Adicionar uma tarefa... (Pressione Enter para salvar)"
      buttonTitle="Adicionar tarefa (Enter)"
      buttonAriaLabel="Adicionar tarefa"
      disabled={disabled}
    />
  );
};
