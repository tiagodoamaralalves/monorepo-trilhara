import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import fs from 'fs';
import path from 'path';

import {
  isPushSupported,
  getPushPermissionState,
  isIOSNonStandalone,
  pushService
} from '../shared/services/pushService';
import { TaskModal } from '../modules/tarefas/components/TaskModal/TaskModal';
import { ConfiguracaoLembreteModal } from '../modules/tarefas/components/ConfiguracaoLembreteModal/ConfiguracaoLembreteModal';

describe('FASE 10 — Web Push e Notificações de Tarefas', () => {
  const originalNotification = window.Notification;
  const originalNavigator = window.navigator;

  beforeEach(() => {
    vi.restoreAllMocks();
    (window as any).PushManager = function() {};
    Object.defineProperty(navigator, 'serviceWorker', {
      value: {
        ready: Promise.resolve({
          pushManager: {
            getSubscription: vi.fn().mockResolvedValue(null),
            subscribe: vi.fn()
          }
        })
      },
      configurable: true,
      writable: true
    });
    (window as any).Notification = { permission: 'default', requestPermission: vi.fn() };
  });

  afterEach(() => {
    (window as any).Notification = originalNotification;
    delete (window as any).PushManager;
  });

  describe('1. Suporte e Estados de Permissão do Navegador', () => {
    it('deve identificar ausência de suporte quando Notification ou ServiceWorker estiver ausente', () => {
      // Simula ausência de Notification
      delete (window as any).Notification;
      expect(isPushSupported()).toBe(false);
      expect(getPushPermissionState()).toBe('unsupported');
    });

    it('deve reportar estados de permissão default, granted e denied', () => {
      (window as any).Notification = { permission: 'default' };
      expect(getPushPermissionState()).toBe('default');

      (window as any).Notification = { permission: 'granted' };
      expect(getPushPermissionState()).toBe('granted');

      (window as any).Notification = { permission: 'denied' };
      expect(getPushPermissionState()).toBe('denied');
    });

    it('deve identificar dispositivo iOS fora do modo standalone', () => {
      const originalUserAgent = window.navigator.userAgent;

      // Simula iPhone Safari
      Object.defineProperty(window.navigator, 'userAgent', {
        value: 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_5 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1',
        configurable: true
      });
      (window.navigator as any).standalone = false;
      window.matchMedia = vi.fn().mockImplementation((query) => ({
        matches: false,
        media: query
      }));

      expect(isIOSNonStandalone()).toBe(true);

      // Simula modo Standalone instalado na Tela de Início
      window.matchMedia = vi.fn().mockImplementation((query) => ({
        matches: query === '(display-mode: standalone)',
        media: query
      }));
      expect(isIOSNonStandalone()).toBe(false);

      Object.defineProperty(window.navigator, 'userAgent', {
        value: originalUserAgent,
        configurable: true
      });
    });
  });

  describe('2. TaskModal: Seletor de Lembrete e Badges Informativos', () => {
    it('exibe badge informativo discreto quando push for unsupported sem bloquear salvamento', async () => {
      delete (window as any).Notification;

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
      (window as any).Notification = { permission: 'denied' };

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
      (window as any).Notification = { permission: 'default' };
      Object.defineProperty(window.navigator, 'userAgent', {
        value: 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_4 like Mac OS X) Mobile/15E148 Safari/604.1',
        configurable: true
      });
      (window.navigator as any).standalone = false;
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

  describe('3. Service Worker e Ação de Clique da Notificação', () => {
    it('o service worker em sw.js foca ou abre a janela da tarefa SEM concluir a tarefa', () => {
      const swPath = path.resolve(__dirname, '../../public/sw.js');
      expect(fs.existsSync(swPath)).toBe(true);

      const swContent = fs.readFileSync(swPath, 'utf-8');

      // Escuta os eventos push e notificationclick
      expect(swContent).toContain("self.addEventListener('push'");
      expect(swContent).toContain("self.addEventListener('notificationclick'");

      // A notificação abre a URL da tarefa ou foca janela aberta
      expect(swContent).toContain('clients.matchAll');
      expect(swContent).toContain('client.focus()');
      expect(swContent).toContain('/tarefas?tarefaId=');

      // Garante que NENHUMA rota ou chamada de conclusão de tarefa ocorre no clique
      expect(swContent).not.toContain('/conclusao');
      expect(swContent).not.toContain('concluir');
      expect(swContent).not.toContain('concluida: true');
    });

    it('o manifest.webmanifest possui display standalone e configurações obrigatórias para iOS', () => {
      const manifestPath = path.resolve(__dirname, '../../public/manifest.webmanifest');
      expect(fs.existsSync(manifestPath)).toBe(true);

      const manifestContent = JSON.parse(fs.readFileSync(manifestPath, 'utf-8'));
      expect(manifestContent.display).toBe('standalone');
      expect(manifestContent.name).toBe('Trilhara');
      expect(manifestContent.short_name).toBe('Trilhara');
      expect(manifestContent.start_url).toBe('/');
    });
  });

  describe('4. ConfiguracaoLembreteModal: Preferência Global', () => {
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
