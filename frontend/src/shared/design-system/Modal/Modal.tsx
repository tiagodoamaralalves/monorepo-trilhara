import React, { useEffect, useId, useRef } from 'react';
import { X } from 'lucide-react';
import { Button } from '../Button/Button';
import styles from './Modal.module.css';

export interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  icon?: React.ReactNode;
  iconBadgeStyle?: React.CSSProperties;
  children: React.ReactNode;
  footer?: React.ReactNode;
  maxWidth?: 'sm' | 'md' | 'lg' | 'xl';
  initialFocus?: 'first' | 'autofocus';
  overflowVisible?: boolean;
  className?: string;
  isSaving?: boolean;
}

const openModalStack: symbol[] = [];

export const Modal: React.FC<ModalProps> = ({
  isOpen,
  onClose,
  title,
  subtitle,
  icon,
  iconBadgeStyle,
  children,
  footer,
  maxWidth = 'md',
  initialFocus = 'autofocus',
  overflowVisible = false,
  className = '',
  isSaving = false
}) => {
  const modalIdRef = useRef<symbol>(Symbol('modal'));
  const dialogRef = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);
  const previousFocusRef = useRef<HTMLElement | null>(null);
  const previousOverflowRef = useRef('');
  const titleId = useId();
  const subtitleId = useId();

  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    let focusFrame = 0;
    const modalId = modalIdRef.current;

    const handleKeyDown = (e: KeyboardEvent) => {
      // Só processa se for o modal no topo da pilha
      if (openModalStack[openModalStack.length - 1] !== modalId) return;

      if (e.key === 'Escape' && isOpen) {
        if (isSaving) return;
        e.stopPropagation();
        onCloseRef.current();
        return;
      }

      if (e.key === 'Tab' && dialogRef.current) {
        const focusable = Array.from(
          dialogRef.current.querySelectorAll<HTMLElement>(
            'button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])'
          )
        );
        if (focusable.length === 0) {
          e.preventDefault();
          dialogRef.current.focus();
          return;
        }

        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        const activeElement = document.activeElement;
        const focusIsOutside = !dialogRef.current.contains(activeElement);
        if (e.shiftKey && (activeElement === first || focusIsOutside)) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && (activeElement === last || activeElement === dialogRef.current || focusIsOutside)) {
          e.preventDefault();
          first.focus();
        }
      }
    };

    if (isOpen) {
      openModalStack.push(modalId);
      previousFocusRef.current = document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
      previousOverflowRef.current = document.body.style.overflow;
      document.body.style.overflow = 'hidden';
      window.addEventListener('keydown', handleKeyDown);

      focusFrame = requestAnimationFrame(() => {
        const dialog = dialogRef.current;
        if (!dialog) return;

        // Se algum elemento interno já recebeu autoFocus do React, não rouba o foco
        if (dialog.contains(document.activeElement) && document.activeElement !== dialog) {
          return;
        }

        const autoFocusTarget = dialog.querySelector<HTMLElement>('[autofocus]');
        const target = autoFocusTarget || (initialFocus === 'first'
          ? dialog.querySelector<HTMLElement>('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])')
          : null);
        (target || dialog).focus();
      });
    }

    return () => {
      const idx = openModalStack.indexOf(modalId);
      if (idx !== -1) {
        openModalStack.splice(idx, 1);
      }
      if (openModalStack.length === 0) {
        document.body.style.overflow = previousOverflowRef.current;
      }
      window.removeEventListener('keydown', handleKeyDown);
      if (focusFrame) cancelAnimationFrame(focusFrame);
      if (isOpen && previousFocusRef.current && typeof previousFocusRef.current.focus === 'function') {
        previousFocusRef.current.focus();
      }
    };
  }, [initialFocus, isOpen, isSaving]);

  if (!isOpen) return null;

  return (
    <div
      className={styles.overlay}
      onClick={(e) => {
        if (isSaving) return;
        if (e.target === e.currentTarget) onClose();
      }}
      role="presentation"
    >
      <div
        className={`${styles.box} ${styles[maxWidth]} ${overflowVisible ? styles.overflowVisible : ''} ${className}`}
        role="dialog"
        aria-modal="true"
        ref={dialogRef}
        aria-labelledby={titleId}
        aria-describedby={subtitle ? subtitleId : undefined}
        tabIndex={-1}
      >
        <div className={styles.accentBar} />

        <div className={styles.header}>
          <div className={styles.headerLeft}>
            {icon && (
              <div className={styles.iconBadge} style={iconBadgeStyle}>
                {icon}
              </div>
            )}
            <div className={styles.titleGroup}>
              <h2 className={styles.title} id={titleId}>{title}</h2>
              {subtitle && <div className={styles.subtitle} id={subtitleId}>{subtitle}</div>}
            </div>
          </div>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            iconOnly
            onClick={isSaving ? undefined : onClose}
            disabled={isSaving}
            aria-label="Fechar"
          >
            <X size={20} />
          </Button>
        </div>

        <div className={styles.body}>{children}</div>

        {footer && <div className={styles.footer}>{footer}</div>}
      </div>
    </div>
  );
};
