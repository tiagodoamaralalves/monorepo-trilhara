import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { AlertCircle, CheckCircle2, Info, TriangleAlert, X } from 'lucide-react';
import { Button } from '../Button/Button';
import styles from './Toast.module.css';

export interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
}

export type ToastMessage = string | Error | ProblemDetail;
export type ToastVariant = 'success' | 'error' | 'warning' | 'info';

interface ToastItem {
  id: number;
  variant: ToastVariant;
  message: string;
}

export interface ToastContextValue {
  success: (message: ToastMessage) => void;
  error: (message: ToastMessage) => void;
  warning: (message: ToastMessage) => void;
  info: (message: ToastMessage) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

function getMessage(message: ToastMessage): string {
  if (typeof message === 'string') return message;
  if (message instanceof Error) return message.message;
  return message.detail || message.title || 'Ocorreu um erro inesperado.';
}

const variantIcons = {
  success: CheckCircle2,
  error: AlertCircle,
  warning: TriangleAlert,
  info: Info
};

export const ToastProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const enqueue = useCallback((variant: ToastVariant, message: ToastMessage) => {
    setToasts((current) => [
      ...current,
      { id: Date.now() + Math.random(), variant, message: getMessage(message) }
    ]);
  }, []);

  const value = useMemo<ToastContextValue>(() => ({
    success: (message) => enqueue('success', message),
    error: (message) => enqueue('error', message),
    warning: (message) => enqueue('warning', message),
    info: (message) => enqueue('info', message)
  }), [enqueue]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className={styles.region} role="region" aria-label="Notificações">
        {toasts.map((toast) => {
          const Icon = variantIcons[toast.variant];
          return (
            <ToastNotice key={toast.id} toast={toast} onDismiss={dismiss}>
              <Icon size={18} aria-hidden="true" />
            </ToastNotice>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
};

interface ToastNoticeProps {
  toast: ToastItem;
  onDismiss: (id: number) => void;
  children: React.ReactNode;
}

const ToastNotice: React.FC<ToastNoticeProps> = ({ toast, onDismiss, children }) => {
  const [isPaused, setIsPaused] = useState(false);
  const defaultDuration = toast.variant === 'error' ? 5000 : 3200;
  const remainingRef = useRef(defaultDuration);
  const startTimeRef = useRef(Date.now());

  useEffect(() => {
    if (isPaused) {
      const elapsed = Date.now() - startTimeRef.current;
      remainingRef.current = Math.max(1000, remainingRef.current - elapsed);
      return;
    }

    startTimeRef.current = Date.now();
    const timer = window.setTimeout(() => onDismiss(toast.id), remainingRef.current);
    return () => window.clearTimeout(timer);
  }, [isPaused, onDismiss, toast.id]);

  const handleMouseEnter = () => setIsPaused(true);
  const handleMouseLeave = () => setIsPaused(false);
  const handleFocus = () => setIsPaused(true);
  const handleBlur = (e: React.FocusEvent<HTMLDivElement>) => {
    if (!e.currentTarget.contains(e.relatedTarget as Node | null)) {
      setIsPaused(false);
    }
  };

  return (
    <div
      className={`${styles.toast} ${styles[toast.variant]}`}
      role={toast.variant === 'error' ? 'alert' : 'status'}
      aria-live={toast.variant === 'error' ? 'assertive' : 'polite'}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
      onFocus={handleFocus}
      onBlur={handleBlur}
    >
      <span className={styles.icon}>{children}</span>
      <span className={styles.message}>{toast.message}</span>
      <Button
        type="button"
        variant="ghost"
        size="sm"
        iconOnly
        className={styles.close}
        onClick={() => onDismiss(toast.id)}
        aria-label="Dispensar notificação"
      >
        <X size={15} />
      </Button>
    </div>
  );
};

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) throw new Error('useToast deve ser usado dentro de ToastProvider.');
  return context;
}
