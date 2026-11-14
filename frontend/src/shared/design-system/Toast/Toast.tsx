import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { AlertCircle, CheckCircle2, Info, TriangleAlert, X } from 'lucide-react';
import { Button } from '../Button/Button';
import styles from './Toast.module.css';

interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
}

type ToastMessage = string | Error | ProblemDetail;
type ToastVariant = 'success' | 'error' | 'warning' | 'info';

/** Duração padrão de um toast. */
const TOAST_DURACAO_MS = 5000;

/** Máximo de toasts empilhados ao mesmo tempo. */
export const TOAST_MAXIMO = 3;

interface ToastAction {
  rotulo: string;
  aoAcionar: () => void;
}

interface ToastOptions {
  /** Ação opcional, tipicamente "Desfazer". */
  acao?: ToastAction;
  /** Sobrescreve a duração padrão de 5 s. */
  duracaoMs?: number;
}

interface ToastItem {
  id: number;
  variant: ToastVariant;
  message: string;
  acao?: ToastAction;
  duracaoMs: number;
}

export interface ToastContextValue {
  success: (message: ToastMessage, options?: ToastOptions) => void;
  error: (message: ToastMessage, options?: ToastOptions) => void;
  warning: (message: ToastMessage, options?: ToastOptions) => void;
  info: (message: ToastMessage, options?: ToastOptions) => void;
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
  const sequenciaRef = useRef(0);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const enqueue = useCallback((variant: ToastVariant, message: ToastMessage, options?: ToastOptions) => {
    sequenciaRef.current += 1;
    const novo: ToastItem = {
      id: sequenciaRef.current,
      variant,
      message: getMessage(message),
      acao: options?.acao,
      duracaoMs: options?.duracaoMs ?? TOAST_DURACAO_MS
    };
    setToasts((current) => [...current, novo].slice(-TOAST_MAXIMO));
  }, []);

  const value = useMemo<ToastContextValue>(() => ({
    success: (message, options) => enqueue('success', message, options),
    error: (message, options) => enqueue('error', message, options),
    warning: (message, options) => enqueue('warning', message, options),
    info: (message, options) => enqueue('info', message, options)
  }), [enqueue]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        className={styles.region}
        role="region"
        aria-label="Notificações"
        aria-live="polite"
        data-testid="toast-region"
      >
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
  const remainingRef = useRef(toast.duracaoMs);
  const startTimeRef = useRef(0);

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

  const handleAcao = () => {
    toast.acao?.aoAcionar();
    onDismiss(toast.id);
  };

  return (
    <div
      className={`${styles.toast} ${styles[toast.variant]}`}
      role="status"
      data-testid="toast"
      aria-live="polite"
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
      onFocus={handleFocus}
      onBlur={handleBlur}
    >
      <span className={styles.icon}>{children}</span>
      <span className={styles.message}>{toast.message}</span>
      {toast.acao && (
        <Button type="button" variant="ghost" size="sm" onClick={handleAcao}>
          {toast.acao.rotulo}
        </Button>
      )}
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
