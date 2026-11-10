import React, { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import { Button } from '../Button/Button';
import { Modal } from '../Modal/Modal';
import styles from './ConfirmDialog.module.css';

export interface ConfirmOptions {
  title: string;
  description: string;
  confirmText?: string;
  cancelText?: string;
  variant?: 'danger' | 'primary' | 'warning';
}

interface ConfirmRequest extends ConfirmOptions {
  resolve: (confirmed: boolean) => void;
}

interface ConfirmContextValue {
  confirm: (options: ConfirmOptions) => Promise<boolean>;
}

export const ConfirmContext = createContext<ConfirmContextValue | null>(null);

export const ConfirmProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
  const pendingRef = useRef<ConfirmRequest[]>([]);
  const [current, setCurrent] = useState<ConfirmRequest | null>(null);

  const confirm = useCallback((options: ConfirmOptions) => new Promise<boolean>((resolve) => {
    const request = { ...options, resolve };
    pendingRef.current = [...pendingRef.current, request];
    setCurrent((active) => active || request);
  }), []);

  const finish = useCallback((confirmed: boolean) => {
    const [active, ...pending] = pendingRef.current;
    if (!active) return;
    active.resolve(confirmed);
    pendingRef.current = pending;
    setCurrent(pending[0] || null);
  }, []);

  const value = useMemo(() => ({ confirm }), [confirm]);

  return (
    <ConfirmContext.Provider value={value}>
      {children}
      {current && (
        <Modal
          isOpen
          onClose={() => finish(false)}
          title={current.title}
          icon={<AlertTriangle size={21} />}
          initialFocus="first"
          footer={
            <>
              <Button
                type="button"
                variant="secondary"
                autoFocus={current.variant === 'danger'}
                onClick={() => finish(false)}
              >
                {current.cancelText || 'Cancelar'}
              </Button>
              <Button
                type="button"
                variant={current.variant || 'primary'}
                onClick={() => finish(true)}
              >
                {current.confirmText || 'Confirmar'}
              </Button>
            </>
          }
        >
          <p className={styles.description}>{current.description}</p>
        </Modal>
      )}
    </ConfirmContext.Provider>
  );
};

export function useConfirm(): ConfirmContextValue {
  const context = useContext(ConfirmContext);
  if (!context) throw new Error('useConfirm deve ser usado dentro de ConfirmProvider.');
  return context;
}
