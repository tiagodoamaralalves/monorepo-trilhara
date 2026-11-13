import { useState } from 'react';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { Modal } from '../../shared/design-system/Modal/Modal';
import { FormModal } from '../../shared/design-system/FormModal/FormModal';
import { ConfirmProvider, useConfirm } from '../../shared/design-system/ConfirmDialog/ConfirmDialog';
import { GlobalSessionModal } from '../../shared/design-system/GlobalSessionModal/GlobalSessionModal';
import {
  GlobalSessionModalProvider,
  useGlobalSessionModal
} from '../../shared/design-system/GlobalSessionModal/GlobalSessionModalContext';
import { ToastProvider } from '../../shared/design-system/Toast/Toast';
import { AuthContext, AuthContextValue } from '../../shared/auth/AuthContext';
import { StudyTimerProvider } from '../../shared/timers/estudoTimer';

/**
 * Acessibilidade dos modais (D-40 / WCAG 2.2 SC 2.1.2 e 2.4.3).
 *
 * Cobre *focus trap*, fechamento por `Escape` bloqueado durante salvamento e
 * devolução determinística de foco ao elemento disparador em `Modal`,
 * `FormModal`, `ConfirmDialog` e `GlobalSessionModal`.
 */

const mockAuthValue = (usuarioId: string): AuthContextValue => ({
  usuario: {
    id: usuarioId,
    nome: 'Aluno A11y',
    email: 'a11y@trilhara.com.br',
    emailVerificado: true,
    ativo: true,
    criadoEm: '2026-11-09T00:00:00Z',
    papeis: ['USER'],
    preferencias: {
      tema: 'dark',
      disponibilidadeSemanalMinutos: [0, 0, 0, 0, 0, 0, 0],
      padraoLembreteMinutosAntes: 15
    }
  },
  isAuthenticated: true,
  carregando: false,
  isAuthModalOpen: false,
  abrirAuthModal: vi.fn(),
  fecharAuthModal: vi.fn(),
  isContaModalOpen: false,
  abrirContaModal: vi.fn(),
  fecharContaModal: vi.fn(),
  excluirConta: vi.fn(),
  login: vi.fn(),
  cadastrar: vi.fn(),
  logout: vi.fn(),
  verificarSessao: vi.fn(),
  setUsuario: vi.fn()
});

const ModalHarness: React.FC<{ isSaving?: boolean }> = ({ isSaving = false }) => {
  const [open, setOpen] = useState(false);
  return (
    <div>
      <button data-testid="open-modal-btn" onClick={() => setOpen(true)}>
        Abrir Modal
      </button>
      <Modal
        isOpen={open}
        isSaving={isSaving}
        onClose={() => setOpen(false)}
        title="Modal Acessibilidade"
      >
        <input data-testid="modal-input-1" placeholder="Campo 1" />
        <button data-testid="modal-inside-btn">Ação Interna</button>
      </Modal>
    </div>
  );
};

const FormModalHarness: React.FC<{ loading: boolean }> = ({ loading }) => {
  const [open, setOpen] = useState(true);
  return (
    <FormModal
      isOpen={open}
      loading={loading}
      onClose={() => setOpen(false)}
      onSubmit={(e) => e.preventDefault()}
      title="Form Modal Acessibilidade"
      submitText="Salvar"
    >
      <input data-testid="form-input" placeholder="Dado" />
    </FormModal>
  );
};

const ConfirmHarness: React.FC = () => {
  const confirm = useConfirm();
  const [resultado, setResultado] = useState<string>('pendente');

  const handleClick = async () => {
    const ok = await confirm.confirm({
      title: 'Confirmação Crítica',
      description: 'Deseja prosseguir com a operação?'
    });
    setResultado(ok ? 'confirmado' : 'cancelado');
  };

  return (
    <div>
      <button data-testid="open-confirm-btn" onClick={handleClick}>
        Pedir Confirmação
      </button>
      <span data-testid="confirm-resultado">{resultado}</span>
    </div>
  );
};

const GlobalSessionHarness: React.FC = () => {
  const { abrirModal } = useGlobalSessionModal();

  return (
    <div>
      <button
        data-testid="open-global-session-btn"
        onClick={(e) =>
          abrirModal({
            modo: 'MANUAL',
            triggerElement: e.currentTarget
          })
        }
      >
        Iniciar Sessão
      </button>
      <GlobalSessionModal />
    </div>
  );
};

describe('Acessibilidade dos modais — focus trap, Escape e restauração de foco', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Modal: fechamento por Escape e restauração de foco', () => {
    it('fecha com Escape quando isSaving == false e devolve o foco ao disparador', () => {
      render(<ModalHarness isSaving={false} />);

      const triggerBtn = screen.getByTestId('open-modal-btn');
      triggerBtn.focus();
      expect(document.activeElement).toBe(triggerBtn);

      fireEvent.click(triggerBtn);
      expect(screen.getByRole('dialog')).toBeInTheDocument();

      fireEvent.keyDown(window, { key: 'Escape' });
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

      expect(document.activeElement).toBe(triggerBtn);
    });

    it('NÃO fecha com Escape quando isSaving == true', () => {
      render(<ModalHarness isSaving={true} />);

      fireEvent.click(screen.getByTestId('open-modal-btn'));
      expect(screen.getByRole('dialog')).toBeInTheDocument();

      fireEvent.keyDown(window, { key: 'Escape' });
      expect(screen.getByRole('dialog')).toBeInTheDocument();

      expect(screen.getByRole('button', { name: /fechar/i })).toBeDisabled();
    });

    it('mantém o foco preso dentro do Modal ao navegar com Tab e Shift+Tab', () => {
      render(<ModalHarness isSaving={false} />);

      fireEvent.click(screen.getByTestId('open-modal-btn'));
      const dialog = screen.getByRole('dialog');
      expect(dialog).toBeInTheDocument();

      const focusable = dialog.querySelectorAll<HTMLElement>(
        'button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])'
      );
      expect(focusable.length).toBeGreaterThanOrEqual(2);

      const firstElement = focusable[0];
      const lastElement = focusable[focusable.length - 1];

      lastElement.focus();
      expect(document.activeElement).toBe(lastElement);

      fireEvent.keyDown(window, { key: 'Tab', shiftKey: false });
      expect(document.activeElement).toBe(firstElement);

      firstElement.focus();
      fireEvent.keyDown(window, { key: 'Tab', shiftKey: true });
      expect(document.activeElement).toBe(lastElement);
    });
  });

  describe('FormModal: propagação de isSaving={isLoading}', () => {
    it('impede fechamento por Escape enquanto salva (isLoading == true)', () => {
      const { rerender } = render(<FormModalHarness loading={true} />);

      expect(screen.getByRole('dialog')).toBeInTheDocument();

      fireEvent.keyDown(window, { key: 'Escape' });
      expect(screen.getByRole('dialog')).toBeInTheDocument();

      rerender(<FormModalHarness loading={false} />);
      fireEvent.keyDown(window, { key: 'Escape' });
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
  });

  describe('ConfirmDialog: cancelamento por Escape e restauração de foco', () => {
    it('fecha com Escape, resolve como cancelado e devolve o foco ao disparador', async () => {
      render(
        <ConfirmProvider>
          <ConfirmHarness />
        </ConfirmProvider>
      );

      const triggerBtn = screen.getByTestId('open-confirm-btn');
      triggerBtn.focus();
      fireEvent.click(triggerBtn);

      expect(screen.getByText('Confirmação Crítica')).toBeInTheDocument();

      fireEvent.keyDown(window, { key: 'Escape' });
      await act(async () => {});

      expect(screen.queryByText('Confirmação Crítica')).not.toBeInTheDocument();
      expect(screen.getByTestId('confirm-resultado').textContent).toBe('cancelado');
      expect(document.activeElement).toBe(triggerBtn);
    });
  });

  describe('GlobalSessionModal: focus trap e restauração de foco', () => {
    it('abre, fecha com Escape e devolve o foco ao triggerElement', async () => {
      render(
        <ToastProvider>
          <ConfirmProvider>
            <AuthContext.Provider value={mockAuthValue('user-a11y')}>
              <StudyTimerProvider>
                <GlobalSessionModalProvider>
                  <GlobalSessionHarness />
                </GlobalSessionModalProvider>
              </StudyTimerProvider>
            </AuthContext.Provider>
          </ConfirmProvider>
        </ToastProvider>
      );

      const openBtn = screen.getByTestId('open-global-session-btn');
      openBtn.focus();
      fireEvent.click(openBtn);

      const dialog = screen.getByRole('dialog', { name: /registrar sessão manual/i });
      expect(dialog).toBeInTheDocument();

      fireEvent.keyDown(window, { key: 'Escape' });
      await act(async () => {});

      expect(screen.queryByRole('dialog', { name: /registrar sessão manual/i })).not.toBeInTheDocument();
      expect(document.activeElement).toBe(openBtn);
    });
  });
});

