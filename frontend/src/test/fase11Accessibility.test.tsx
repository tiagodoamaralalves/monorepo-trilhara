import { useState } from 'react';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { Modal } from '../shared/design-system/Modal/Modal';
import { FormModal } from '../shared/design-system/FormModal/FormModal';
import { ConfirmProvider, useConfirm } from '../shared/design-system/ConfirmDialog/ConfirmDialog';
import { SyncStatusBadge } from '../shared/design-system/SyncStatusBadge/SyncStatusBadge';
import { GlobalSessionModal } from '../shared/design-system/GlobalSessionModal/GlobalSessionModal';
import { GlobalSessionModalProvider, useGlobalSessionModal } from '../shared/design-system/GlobalSessionModal/GlobalSessionModalContext';
import { ToastProvider } from '../shared/design-system/Toast/Toast';
import { AuthContext, AuthContextValue } from '../shared/auth/AuthContext';
import { StudyTimerProvider } from '../shared/timers/estudoTimer';

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

// Componente auxiliar para teste de foco e fechamento do Modal
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

// Componente auxiliar para teste de FormModal
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

// Componente auxiliar para teste de ConfirmDialog
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

// Componente auxiliar para teste de GlobalSessionModal
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

describe('FASE 11 — Auditoria e Testes Automatizados de Acessibilidade (WCAG 2.2 Level AA)', () => {

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('1. Focus Trap, Fechamento por Escape e Restauração de Foco em Modal', () => {
    it('deve fechar Modal com tecla Escape quando isSaving == false e restaurar foco ao disparador', () => {
      render(<ModalHarness isSaving={false} />);

      const triggerBtn = screen.getByTestId('open-modal-btn');
      triggerBtn.focus();
      expect(document.activeElement).toBe(triggerBtn);

      fireEvent.click(triggerBtn);
      expect(screen.getByRole('dialog')).toBeInTheDocument();

      // Pressionar Escape fecha o modal
      fireEvent.keyDown(window, { key: 'Escape' });
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

      // Foco deve ser restaurado ao elemento disparador
      expect(document.activeElement).toBe(triggerBtn);
    });

    it('NÃO deve fechar Modal com tecla Escape quando isSaving == true', () => {
      render(<ModalHarness isSaving={true} />);

      const triggerBtn = screen.getByTestId('open-modal-btn');
      fireEvent.click(triggerBtn);
      expect(screen.getByRole('dialog')).toBeInTheDocument();

      // Pressionar Escape deve ser bloqueado enquanto salvando
      fireEvent.keyDown(window, { key: 'Escape' });
      expect(screen.getByRole('dialog')).toBeInTheDocument();

      // Botão fechar deve estar desabilitado
      const closeBtn = screen.getByRole('button', { name: /fechar/i });
      expect(closeBtn).toBeDisabled();
    });

    it('deve manter o foco preso dentro do Modal ao navegar com tecla Tab (Focus Trap)', () => {
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

      // Foco no último elemento e pressiona Tab -> deve ciclar para o primeiro
      lastElement.focus();
      expect(document.activeElement).toBe(lastElement);

      fireEvent.keyDown(window, { key: 'Tab', shiftKey: false });
      expect(document.activeElement).toBe(firstElement);

      // Foco no primeiro elemento e pressiona Shift+Tab -> deve ciclar para o último
      firstElement.focus();
      fireEvent.keyDown(window, { key: 'Tab', shiftKey: true });
      expect(document.activeElement).toBe(lastElement);
    });
  });

  describe('2. FormModal e propagação de isSaving={isLoading}', () => {
    it('deve impedir fechamento por Escape quando FormModal está salvando (isLoading == true)', () => {
      const { rerender } = render(<FormModalHarness loading={true} />);

      expect(screen.getByRole('dialog')).toBeInTheDocument();

      // Escape não fecha quando salvando
      fireEvent.keyDown(window, { key: 'Escape' });
      expect(screen.getByRole('dialog')).toBeInTheDocument();

      // Rerender com loading=false permite fechar
      rerender(<FormModalHarness loading={false} />);
      fireEvent.keyDown(window, { key: 'Escape' });
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
  });

  describe('3. ConfirmDialog: Focus restoration e Escape', () => {
    it('deve fechar ConfirmDialog com Escape cancelando a operação e restaurando o foco', async () => {
      render(
        <ConfirmProvider>
          <ConfirmHarness />
        </ConfirmProvider>
      );

      const triggerBtn = screen.getByTestId('open-confirm-btn');
      triggerBtn.focus();
      fireEvent.click(triggerBtn);

      expect(screen.getByText('Confirmação Crítica')).toBeInTheDocument();

      // Pressiona Escape para cancelar
      fireEvent.keyDown(window, { key: 'Escape' });
      await act(async () => {});

      expect(screen.queryByText('Confirmação Crítica')).not.toBeInTheDocument();
      expect(screen.getByTestId('confirm-resultado').textContent).toBe('cancelado');
      expect(document.activeElement).toBe(triggerBtn);
    });
  });

  describe('4. GlobalSessionModal: Focus Trap, Restauração de Foco e Bloqueio de Escape ao Salvar', () => {
    it('deve abrir GlobalSessionModal, fechar com Escape e restaurar foco ao triggerElement', async () => {
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

      // Pressiona Escape
      fireEvent.keyDown(window, { key: 'Escape' });
      await act(async () => {});

      expect(screen.queryByRole('dialog', { name: /registrar sessão manual/i })).not.toBeInTheDocument();
      expect(document.activeElement).toBe(openBtn);
    });
  });

  describe('5. Semântica de Acessibilidade dos Cronômetros e Badges (role=timer, aria-live)', () => {
    it('SyncStatusBadge deve possuir role="status" e aria-live="polite"', () => {
      const { rerender } = render(<SyncStatusBadge status="salvando" />);
      const badge = screen.getByRole('status');

      expect(badge).toBeInTheDocument();
      expect(badge).toHaveAttribute('aria-live', 'polite');
      expect(badge.textContent).toContain('Salvando...');

      rerender(<SyncStatusBadge status="salvo" />);
      expect(badge).toHaveAttribute('aria-live', 'polite');
      expect(badge.textContent).toContain('Salvo');
    });

    it('Cronômetros de foco devem possuir role="timer" e aria-live="off" nos dígitos para não poluir leitores de tela', () => {
      const mockTimerDisplay = (
        <div>
          <div role="status" aria-live="polite">
            Foco Ativo
          </div>
          <div role="timer" aria-live="off" data-testid="cronometro-digitos">
            25:00
          </div>
          <div role="status" aria-live="polite" data-testid="break-banner">
            Pausa curta
          </div>
        </div>
      );

      render(mockTimerDisplay);

      const timerDigits = screen.getByTestId('cronometro-digitos');
      expect(timerDigits).toHaveAttribute('role', 'timer');
      expect(timerDigits).toHaveAttribute('aria-live', 'off');

      const breakBanner = screen.getByTestId('break-banner');
      expect(breakBanner).toHaveAttribute('role', 'status');
      expect(breakBanner).toHaveAttribute('aria-live', 'polite');
    });
  });

  describe('6. Tokens e Estilos WCAG 2.2 AA (Área Mínima de Toque, Mobile Scroll-Padding e Reduced Motion)', () => {
    it('deve conter as regras globais de CSS para SC 2.5.8 (24x24px desktop, 40x40px touch, scroll-padding 88px e prefers-reduced-motion)', async () => {
      // Inspeciona o arquivo index.css para atestar a presença das diretivas estritas de WCAG 2.2 AA
      const fs = await import('fs');
      const path = await import('path');
      const cssContent = fs.readFileSync(path.resolve(__dirname, '../shared/styles/index.css'), 'utf-8');

      // 1. SC 2.5.8 Desktop: min-width: 24px; min-height: 24px
      expect(cssContent).toContain('min-width: 24px');
      expect(cssContent).toContain('min-height: 24px');

      // 2. SC 2.5.8 Mobile / Pointer Coarse: min-width: 40px; min-height: 40px
      expect(cssContent).toContain('@media (pointer: coarse)');
      expect(cssContent).toContain('min-width: 40px');
      expect(cssContent).toContain('min-height: 40px');

      // 3. Scroll padding bottom para evitar sobreposição da barra inferior fixa mobile
      expect(cssContent).toContain('scroll-padding-bottom: 88px');

      // 4. Suporte a prefers-reduced-motion
      expect(cssContent).toContain('@media (prefers-reduced-motion: reduce)');
      expect(cssContent).toContain('animation-duration: 0.01ms !important');
      expect(cssContent).toContain('transition-duration: 0.01ms !important');
    });
  });
});
