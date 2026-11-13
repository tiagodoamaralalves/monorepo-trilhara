type WindowComPush = Window & { PushManager?: unknown; Notification?: unknown };
type NavigatorComStandalone = Navigator & { standalone?: boolean };

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

import { pushService } from '../shared/services/pushService';
import { TaskModal } from '../modules/tarefas/components/TaskModal/TaskModal';
import { ConfiguracaoLembreteModal } from '../modules/tarefas/components/ConfiguracaoLembreteModal/ConfiguracaoLembreteModal';

/**
 * R11 — UI de lembretes: badges informativos no `TaskModal` e estados do
 * `ConfiguracaoLembreteModal` (D-36 / Anexo B).
 *
 * <p>A detecção de suporte/permissão e o SW têm suítes próprias (`pushService.test.ts`,
 * `sw.test.ts`); aqui testamos apenas o comportamento da interface.</p>
 */
describe('UI de lembretes push', () => {
  const originalNotification = window.Notification;

  beforeEach(() => {
    vi.restoreAllMocks();
    (window as WindowComPush).PushManager = function () {};
    Object.defineProperty(navigator, 'serviceWorker', {
      value: {
        getRegistration: vi.fn().mockResolvedValue(undefined),
        register: vi.fn(),
        ready: Promise.resolve({
          pushManager: { getSubscription: vi.fn().mockResolvedValue(null), subscribe: vi.fn() }
        })
      },
      configurable: true,
      writable: true
    });
    (window as WindowComPush).Notification = { permission: 'default', requestPermission: vi.fn() };
  });

  afterEach(() => {
    (window as WindowComPush).Notification = originalNotification;
    delete (window as WindowComPush).PushManager;
  });


  describe('TaskModal: badges informativos', () => {
    it('exibe badge informativo discreto quando push for unsupported sem bloquear salvamento', async () => {
      delete (window as WindowComPush).Notification;

      const onSalvar = vi.fn();
      render(
        <TaskModal
          isOpen={true}
          onClose={vi.fn()}
          onSalvar={onSalvar}
        />
      );

      // Preenche título e horário
      const inputTitulo = screen.getByLabelText(/Título/i);
      fireEvent.change(inputTitulo, { target: { value: 'Comprar Livro' } });

      const inputHorario = screen.getByLabelText(/Horário/i);
      fireEvent.change(inputHorario, { target: { value: '14:30' } });

      // Badge informativo deve ser exibido
      expect(screen.getByTestId('badge-push-unsupported')).toBeInTheDocument();
      expect(screen.getByText(/Navegador sem suporte a Web Push/i)).toBeInTheDocument();

      // Salvar a tarefa não pode ser bloqueado
      const form = inputTitulo.closest('form')!;
      fireEvent.submit(form);

      await waitFor(() => {
        expect(onSalvar).toHaveBeenCalledTimes(1);
        expect(onSalvar).toHaveBeenCalledWith(
          expect.objectContaining({
            titulo: 'Comprar Livro',
            horario: '14:30'
          })
        );
      });
    });

    it('exibe badge informativo discreto quando a permissão estiver denied', async () => {
      (window as WindowComPush).Notification = { permission: 'denied' };

      const onSalvar = vi.fn();
      render(
        <TaskModal
          isOpen={true}
          onClose={vi.fn()}
          onSalvar={onSalvar}
        />
      );

      const inputTitulo = screen.getByLabelText(/Título/i);
      fireEvent.change(inputTitulo, { target: { value: 'Estudo com Lembrete' } });

      const inputHorario = screen.getByLabelText(/Horário/i);
      fireEvent.change(inputHorario, { target: { value: '09:00' } });

      expect(screen.getByTestId('badge-push-denied')).toBeInTheDocument();
      expect(screen.getByText(/Notificações bloqueadas nas permissões do navegador/i)).toBeInTheDocument();

      // Salva normalmente
      const form = inputTitulo.closest('form')!;
      fireEvent.submit(form);

      await waitFor(() => {
        expect(onSalvar).toHaveBeenCalledTimes(1);
      });
    });

    it('no iOS Safari exige clique do usuário para revelar a instrução de Adicionar à Tela de Início', async () => {
      (window as WindowComPush).Notification = { permission: 'default' };
      Object.defineProperty(window.navigator, 'userAgent', {
        value: 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_4 like Mac OS X) Mobile/15E148 Safari/604.1',
        configurable: true
      });
      (window.navigator as NavigatorComStandalone).standalone = false;
      window.matchMedia = vi.fn().mockReturnValue({ matches: false });

      render(
        <TaskModal
          isOpen={true}
          onClose={vi.fn()}
          onSalvar={vi.fn()}
        />
      );

      const inputHorario = screen.getByLabelText(/Horário/i);
      fireEvent.change(inputHorario, { target: { value: '18:00' } });

      // Botão de como ativar está visível, mas prompt detalhado ainda não
      const btnPrompt = screen.getByTestId('btn-push-ios-prompt');
      expect(btnPrompt).toBeInTheDocument();
      expect(screen.queryByTestId('prompt-ios-standalone')).not.toBeInTheDocument();

      // Ao clicar, exibe instrução de Adicionar à Tela de Início
      fireEvent.click(btnPrompt);
      expect(screen.getByTestId('prompt-ios-standalone')).toBeInTheDocument();
      expect(screen.getByText(/Adicionar à Tela de Início/i)).toBeInTheDocument();
    });
  });

  describe('ConfiguracaoLembreteModal: preferência global', () => {
    it('permite selecionar novo padrão de minutos e salvar', async () => {
      vi.spyOn(pushService, 'obterConfig').mockResolvedValue({
        habilitado: true,
        publicKey: 'BEl123...',
        padraoMinutosAntes: 15
      });
      const spySalvar = vi.spyOn(pushService, 'atualizarPreferenciaGlobal').mockResolvedValue({
        padraoMinutosAntes: 30,
        lembretesRecalculados: 2
      });

      const onSalvo = vi.fn();
      render(
        <ConfiguracaoLembreteModal
          isOpen={true}
          onClose={vi.fn()}
          onSalvo={onSalvo}
        />
      );

      await waitFor(() => {
        expect(screen.getByText(/Lembrete Padrão Global/i)).toBeInTheDocument();
      });

      const btnSalvar = screen.getByTestId('btn-salvar-preferencias-push');
      fireEvent.click(btnSalvar);

      await waitFor(() => {
        expect(spySalvar).toHaveBeenCalledWith(15);
        expect(onSalvo).toHaveBeenCalledWith(15);
      });
    });
  });
});
