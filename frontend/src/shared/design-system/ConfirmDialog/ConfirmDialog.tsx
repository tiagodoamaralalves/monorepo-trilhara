import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import { Button } from '../Button/Button';
import { Modal } from '../Modal/Modal';
import styles from './ConfirmDialog.module.css';

interface ConfirmOptions {
  title: string;
  description: string;
  confirmText?: string;
  cancelText?: string;
  /** `danger` é o padrão para destruição (D-37/D-38). */
  variant?: 'danger' | 'primary' | 'warning';
}

interface ConfirmRequest extends ConfirmOptions {
  resolve: (confirmed: boolean) => void;
}

interface ConfirmContextValue {
  /** Abre o diálogo; resolve `true` só quando o usuário confirma. */
  confirm: (options: ConfirmOptions) => Promise<boolean>;
  /** Marca a confirmação em andamento: bloqueia Escape e o duplo clique. */
  definirConfirmando: (confirmando: boolean) => void;
  estaConfirmando: boolean;
}

export const ConfirmContext = createContext<ConfirmContextValue | null>(null);

export const ConfirmProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
  const pendingRef = useRef<ConfirmRequest[]>([]);
  const [current, setCurrent] = useState<ConfirmRequest | null>(null);
  const [confirmando, setConfirmando] = useState(false);
  const botaoSeguroRef = useRef<HTMLButtonElement>(null);

  const confirm = useCallback((options: ConfirmOptions) => new Promise<boolean>((resolve) => {
    const request = { ...options, resolve };
    pendingRef.current = [...pendingRef.current, request];
    setCurrent((active) => active || request);
  }), []);

  const finish = useCallback((confirmed: boolean) => {
    if (confirmando) return;
    const [active, ...pending] = pendingRef.current;
    if (!active) return;
    active.resolve(confirmed);
    pendingRef.current = pending;
    setCurrent(pending[0] || null);
    setConfirmando(false);
  }, [confirmando]);

  // Foco inicial sempre no botão seguro (Cancelar), nunca na ação destrutiva.
  useEffect(() => {
    if (!current) return;
    const frame = requestAnimationFrame(() => botaoSeguroRef.current?.focus());
    return () => cancelAnimationFrame(frame);
  }, [current]);

  const definirConfirmando = useCallback((valor: boolean) => setConfirmando(valor), []);

  const value = useMemo(
    () => ({ confirm, definirConfirmando, estaConfirmando: confirmando }),
    [confirm, definirConfirmando, confirmando]
  );

  const variant = current?.variant ?? 'danger';

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
          isSaving={confirmando}
          footer={
            <>
              <Button
                ref={botaoSeguroRef}
                type="button"
                variant="secondary"
                onClick={() => finish(false)}
                disabled={confirmando}
              >
                {current.cancelText || 'Cancelar'}
              </Button>
              <Button
                type="button"
                variant={variant}
                loading={confirmando}
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

