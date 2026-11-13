import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

import {
  isPushSupported,
  getPushPermissionState,
  isIOSNonStandalone,
  pushService
} from '../shared/services/pushService';

type WindowComPush = Window & { PushManager?: unknown; Notification?: unknown };
type NavigatorComStandalone = Navigator & { standalone?: boolean };

/**
 * R11 — detecção de suporte, estado de permissão e pedido explícito (D-36 / Anexo B).
 */
describe('pushService — suporte, permissão e registro do SW', () => {
  const originalNotification = window.Notification;

  beforeEach(() => {
    vi.restoreAllMocks();
    (window as WindowComPush).PushManager = function () {};
    (window as WindowComPush).Notification = { permission: 'default', requestPermission: vi.fn() };
    // jsdom não implementa Service Worker: o ambiente de teste fornece a superfície mínima.
    Object.defineProperty(navigator, 'serviceWorker', {
      value: {
        getRegistration: vi.fn().mockResolvedValue(undefined),
        register: vi.fn().mockResolvedValue({ pushManager: { getSubscription: vi.fn().mockResolvedValue(null) } }),
        ready: Promise.resolve(undefined)
      },
      configurable: true,
      writable: true
    });
  });

  afterEach(() => {
    (window as WindowComPush).Notification = originalNotification;
    delete (window as WindowComPush).PushManager;
  });

  it('sem suporte: isPushSupported false e estado unsupported', () => {
    delete (window as WindowComPush).Notification;

    expect(isPushSupported()).toBe(false);
    expect(getPushPermissionState()).toBe('unsupported');
  });

  it('reporta granted, denied e default a partir do navegador', () => {
    (window as WindowComPush).Notification = { permission: 'granted' };
    expect(getPushPermissionState()).toBe('granted');

    (window as WindowComPush).Notification = { permission: 'denied' };
    expect(getPushPermissionState()).toBe('denied');

    (window as WindowComPush).Notification = { permission: 'default' };
    expect(getPushPermissionState()).toBe('default');
  });

  it('iOS em aba comum do Safari reporta ios-nao-instalado', () => {
    const uaSpy = vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)');
    Object.defineProperty(navigator, 'standalone', { value: false, configurable: true });

    expect(isIOSNonStandalone()).toBe(true);
    expect(getPushPermissionState()).toBe('ios-nao-instalado');

    uaSpy.mockRestore();
    delete (navigator as NavigatorComStandalone).standalone;
  });

  it('não pede permissão ao carregar: só no clique explícito', async () => {
    const requestPermission = vi.fn().mockResolvedValue('granted');
    (window as WindowComPush).Notification = { permission: 'default', requestPermission };

    // Nenhuma chamada de leitura de estado pode disparar o prompt.
    getPushPermissionState();
    expect(requestPermission).not.toHaveBeenCalled();

    await pushService.ativarNotificacoes(undefined);
    expect(requestPermission).toHaveBeenCalledTimes(1);
  });
});
