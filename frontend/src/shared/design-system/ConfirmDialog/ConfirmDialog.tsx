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
  /** `danger` é o padrão para destruição. */
  variant?: 'danger' | 'primary' | 'warning';
  /**
 * Exige que o usuário digite o valor exato antes de habilitar a ação destrutiva (* recursos grandes, como excluir trilha, usam o padrão GitHub de confirmação por nome).
 */
  confirmacaoPorTexto?: {
    valor: string;
    rotulo: string;
  };
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
  const [textoDigitado, setTextoDigitado] = useState('');
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
    setTextoDigitado('');
  }, [confirmando]);

  // Foco inicial sempre no botão seguro (Cancelar), nunca na ação destrutiva.
  useEffect(() => {
    if (!current) return;
    setTextoDigitado('');
    const frame = requestAnimationFrame(() => botaoSeguroRef.current?.focus());
    return () => cancelAnimationFrame(frame);
  }, [current]);

  const definirConfirmando = useCallback((valor: boolean) => setConfirmando(valor), []);

  const value = useMemo(
    () => ({ confirm, definirConfirmando, estaConfirmando: confirmando }),
    [confirm, definirConfirmando, confirmando]
  );

  const variant = current?.variant ?? 'danger';
  const exigeTexto = Boolean(current?.confirmacaoPorTexto);
  const textoConfere = !exigeTexto
    || (current?.confirmacaoPorTexto?.valor ?? '').trim() === textoDigitado.trim();
  const campoTextoId = 'confirm-dialog-texto';

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
                disabled={!textoConfere}
                onClick={() => finish(true)}
              >
                {current.confirmText || 'Confirmar'}
              </Button>
            </>
          }
        >
          <p className={styles.description}>{current.description}</p>
          {exigeTexto && (
            <div className={styles.confirmacaoTexto}>
              <label className={styles.confirmacaoLabel} htmlFor={campoTextoId}>
                {current.confirmacaoPorTexto?.rotulo}
              </label>
              <input
                id={campoTextoId}
                className={styles.confirmacaoInput}
                type="text"
                value={textoDigitado}
                autoComplete="off"
                onChange={(evento) => setTextoDigitado(evento.target.value)}
                aria-describedby={`${campoTextoId}-ajuda`}
                disabled={confirmando}
              />
              <span id={`${campoTextoId}-ajuda`} className={styles.confirmacaoAjuda}>
                A ação só é liberada com o nome exato, sem espaços extras.
              </span>
            </div>
          )}
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

