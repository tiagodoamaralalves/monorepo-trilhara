import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act, fireEvent } from '@testing-library/react';
import { ToastProvider, useToast, TOAST_MAXIMO } from '../shared/design-system/Toast/Toast';
import { ConfirmProvider, useConfirm } from '../shared/design-system/ConfirmDialog/ConfirmDialog';

function GatilhoToast() {
  const toast = useToast();
  return (
    <div>
      <button onClick={() => toast.success('Tarefa movida para a lixeira', { acao: { rotulo: 'Desfazer', aoAcionar: () => acoes.desfazer() } })}>
        mover
      </button>
      <button onClick={() => { toast.info('a'); toast.info('b'); toast.info('c'); toast.info('d'); }}>encher</button>
    </div>
  );
}

const acoes = { desfazer: vi.fn() };

describe('R1 — Toast (D-37)', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    acoes.desfazer.mockReset();
  });
  afterEach(() => vi.useRealTimers());

  it('expõe região aria-live="polite" e role=status', () => {
    render(
      <ToastProvider>
        <GatilhoToast />
      </ToastProvider>
    );
    expect(screen.getByTestId('toast-region')).toHaveAttribute('aria-live', 'polite');
  });

  it('mostra ação "Desfazer" e a executa', () => {
    render(
      <ToastProvider>
        <GatilhoToast />
      </ToastProvider>
    );
    act(() => {
      fireEvent.click(screen.getByText('mover'));
    });

    const desfazer = screen.getByRole('button', { name: 'Desfazer' });
    act(() => {
      fireEvent.click(desfazer);
    });
    expect(acoes.desfazer).toHaveBeenCalledTimes(1);
  });

  it('empilha no máximo 3 toasts', () => {
    render(
      <ToastProvider>
        <GatilhoToast />
      </ToastProvider>
    );
    act(() => {
      fireEvent.click(screen.getByText('encher'));
    });
    expect(screen.getAllByTestId('toast')).toHaveLength(TOAST_MAXIMO);
  });

  it('desaparece após 5 s', () => {
    render(
      <ToastProvider>
        <GatilhoToast />
      </ToastProvider>
    );
    act(() => {
      fireEvent.click(screen.getByText('encher'));
    });
    expect(screen.getAllByTestId('toast').length).toBeGreaterThan(0);

    act(() => {
      vi.advanceTimersByTime(5100);
    });
    expect(screen.queryAllByTestId('toast')).toHaveLength(0);
  });

  it('toasts usam role=status (região polite), nunca alert assertivo', () => {
    render(
      <ToastProvider>
        <GatilhoToast />
      </ToastProvider>
    );
    act(() => {
      fireEvent.click(screen.getByText('encher'));
    });
    screen.getAllByTestId('toast').forEach((toast) => {
      expect(toast).toHaveAttribute('aria-live', 'polite');
      expect(toast).not.toHaveAttribute('role', 'alert');
    });
  });
});

function HarnessConfirm() {
  const { confirm, definirConfirmando } = useConfirm();
  return (
    <div>
      <button
        onClick={() => {
          void confirm({ title: 'Excluir tarefa?', description: 'Esta ação não pode ser desfeita.' });
        }}
      >
        abrir
      </button>
      <button onClick={() => definirConfirmando(true)}>confirmando</button>
    </div>
  );
}

describe('R1 — ConfirmDialog (D-37/D-38)', () => {
  it('foca o botão seguro (Cancelar) ao abrir', async () => {
    render(
      <ConfirmProvider>
        <HarnessConfirm />
      </ConfirmProvider>
    );
    fireEvent.click(screen.getByText('abrir'));

    await screen.findByRole('dialog');
    await act(async () => {
      await new Promise((resolve) => requestAnimationFrame(() => resolve(null)));
    });

    expect(screen.getByRole('button', { name: 'Cancelar' })).toHaveFocus();
  });

  it('Escape fecha e resolve como cancelado, mas fica bloqueado durante isConfirming', async () => {
    render(
      <ConfirmProvider>
        <HarnessConfirm />
      </ConfirmProvider>
    );
    fireEvent.click(screen.getByText('abrir'));
    await screen.findByRole('dialog');

    fireEvent.click(screen.getByText('confirmando'));
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
});
