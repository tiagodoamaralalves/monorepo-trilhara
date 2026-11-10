import React from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from '../Button/Button';
import styles from './Pagination.module.css';

export interface PaginationProps {
  currentPage: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  ariaLabel: string;
  summary?: React.ReactNode;
  disabled?: boolean;
  showPageLabel?: boolean;
  className?: string;
}

export const Pagination: React.FC<PaginationProps> = ({
  currentPage,
  totalPages,
  onPageChange,
  ariaLabel,
  summary,
  disabled = false,
  showPageLabel = true,
  className = ''
}) => {
  const hasPages = totalPages > 0;
  const page = Math.max(1, currentPage);
  const classNames = [styles.pagination, className].filter(Boolean).join(' ');

  return (
    <nav className={classNames} aria-label={ariaLabel}>
      {summary !== undefined && <span className={styles.summary}>{summary}</span>}
      <div className={styles.actions}>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          leftIcon={<ChevronLeft size={15} />}
          disabled={disabled || !hasPages || page <= 1}
          onClick={() => onPageChange(Math.max(1, page - 1))}
        >
          Anterior
        </Button>
        {showPageLabel && (
          <span className={styles.pageLabel} aria-live="polite">
            Página {page} de {totalPages}
          </span>
        )}
        <Button
          type="button"
          variant="secondary"
          size="sm"
          rightIcon={<ChevronRight size={15} />}
          disabled={disabled || !hasPages || page >= totalPages}
          onClick={() => onPageChange(Math.min(totalPages, page + 1))}
        >
          Próxima
        </Button>
      </div>
    </nav>
  );
};
