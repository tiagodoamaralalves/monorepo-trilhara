import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

import { AuthService } from '../shared/auth/authService';
import { pushService } from '../shared/services/pushService';
import { AuthStorage } from '../shared/auth/authStorage';

type WindowComPush = Window & { PushManager?: unknown; Notification?: unknown };

/**
 * o logout remove a inscrição push deste dispositivo, best-effort:
 * qualquer falha ou lentidão nunca bloqueia a saída.
 */
describe('logout — desinscrição push do dispositivo', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    AuthStorage.__resetarParaTeste();
    // Superfície mínima de Web Push para o jsdom.
    (window as WindowComPush).PushManager = function () {};
    (window as WindowComPush).Notification = { permission: 'granted', requestPermission: vi.fn() };
    Object.defineProperty(navigator, 'serviceWorker', {
      value: {
        getRegistration: vi.fn().mockResolvedValue({
          pushManager: { getSubscription: vi.fn().mockResolvedValue(null) }
        }),
        register: vi.fn(),
        ready: Promise.resolve(undefined)
      },
      configurable: true,
      writable: true
    });
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({}), { status: 200, headers: { 'Content-Type': 'application/json' } })
    );
  });

  afterEach(() => {
    delete (window as WindowComPush).PushManager;
    delete (window as WindowComPush).Notification;
    vi.restoreAllMocks();
  });

  it('chama DELETE da inscrição antes de revogar a sessão', async () => {
    const remover = vi.spyOn(pushService, 'removerInscricaoDoDispositivo').mockResolvedValue(true);
    const ordem: string[] = [];
    remover.mockImplementation(async () => {
      ordem.push('push');
      return true;
    });

    await AuthService.logout();

    expect(remover).toHaveBeenCalledTimes(1);
    expect(ordem).toContain('push');
  });

  it('falha na desinscrição não bloqueia o logout', async () => {
    vi.spyOn(pushService, 'removerInscricaoDoDispositivo').mockRejectedValue(new Error('rede caiu'));

    await expect(AuthService.logout()).resolves.toBeUndefined();
    expect(AuthStorage.obterAccessToken()).toBeNull();
  });

  it('a remoção do dispositivo tem timeout próprio de 2 s e não propaga erro', async () => {
    vi.useFakeTimers();
    try {
      const requestModulo = await import('../shared/auth/api');
      const requestSpy = vi
        .spyOn(requestModulo, 'request')
        .mockImplementation(() => new Promise(() => undefined) as Promise<never>);

      const unsubscribe = vi.fn().mockResolvedValue(undefined);
      const subscription = { endpoint: 'https://push.test/dispositivo', unsubscribe };
      Object.defineProperty(navigator, 'serviceWorker', {
        value: { getRegistration: vi.fn().mockResolvedValue({ pushManager: { getSubscription: vi.fn().mockResolvedValue(subscription) } }) },
        configurable: true,
        writable: true
      });

      const remocao = pushService.removerInscricaoDoDispositivo();
      await vi.advanceTimersByTimeAsync(2100);

      await expect(remocao).resolves.toBe(true);
      expect(requestSpy).toHaveBeenCalledWith(
        '/push/subscriptions',
        expect.objectContaining({ method: 'DELETE' })
      );
      expect(unsubscribe).toHaveBeenCalled();
    } finally {
      vi.useRealTimers();
    }
  });
});
