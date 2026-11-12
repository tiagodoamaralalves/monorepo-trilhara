import { request } from '../auth/api';

export type PushPermissionState = 'granted' | 'denied' | 'default' | 'unsupported';

export interface PushConfig {
  habilitado: boolean;
  publicKey?: string;
  padraoMinutosAntes: number;
}

export interface AtualizarPreferenciasPushResult {
  padraoMinutosAntes: number;
  lembretesRecalculados: number;
}

export function isPushSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    'serviceWorker' in navigator &&
    'PushManager' in window &&
    'Notification' in window
  );
}

export function getPushPermissionState(): PushPermissionState {
  if (!isPushSupported()) {
    return 'unsupported';
  }
  return Notification.permission;
}

export function isIOSNonStandalone(): boolean {
  if (typeof window === 'undefined' || typeof navigator === 'undefined') {
    return false;
  }
  const isIOS =
    /iPad|iPhone|iPod/.test(navigator.userAgent) ||
    (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  const isStandalone =
    (typeof window.matchMedia === 'function' && window.matchMedia('(display-mode: standalone)').matches) ||
    Boolean((navigator as Navigator & { standalone?: boolean }).standalone);
  return isIOS && !isStandalone;
}

function urlB64ToUint8Array(base64String: string): Uint8Array {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const rawData = window.atob(base64);
  const outputArray = new Uint8Array(rawData.length);
  for (let i = 0; i < rawData.length; ++i) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray;
}

export const pushService = {
  isPushSupported,
  getPushPermissionState,
  isIOSNonStandalone,

  async obterConfig(): Promise<PushConfig> {
    return request<PushConfig>('/push/config');
  },

  async atualizarPreferenciaGlobal(padraoMinutosAntes: number): Promise<AtualizarPreferenciasPushResult> {
    return request<AtualizarPreferenciasPushResult>('/push/preferencias', {
      method: 'PUT',
      body: JSON.stringify({ padraoMinutosAntes })
    });
  },

  async registrarInscricao(subscription: PushSubscription): Promise<void> {
    const subJson = subscription.toJSON();
    await request('/push/subscriptions', {
      method: 'POST',
      body: JSON.stringify({
        endpoint: subscription.endpoint,
        keys: subJson.keys,
        userAgent: typeof navigator !== 'undefined' ? navigator.userAgent : 'Unknown'
      })
    });
  },

  async removerInscricao(endpoint: string): Promise<void> {
    await request(`/push/subscriptions?endpoint=${encodeURIComponent(endpoint)}`, {
      method: 'DELETE'
    });
  },

  async ativarNotificacoes(publicKey?: string): Promise<{ sucesso: boolean; mensagem?: string }> {
    if (!isPushSupported()) {
      return { sucesso: false, mensagem: 'Seu navegador não suporta notificações Web Push.' };
    }

    if (isIOSNonStandalone()) {
      return {
        sucesso: false,
        mensagem: 'No iOS, para receber notificações push você precisa adicionar o Trilhara à Tela de Início primeiro.'
      };
    }

    try {
      const permissao = await Notification.requestPermission();
      if (permissao !== 'granted') {
        return { sucesso: false, mensagem: 'Permissão de notificação negada no navegador.' };
      }

      const reg = await navigator.serviceWorker.register('/sw.js');
      await navigator.serviceWorker.ready;

      let sub = await reg.pushManager.getSubscription();
      if (!sub && publicKey) {
        sub = await reg.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: urlB64ToUint8Array(publicKey) as unknown as BufferSource
        });
      }

      if (sub) {
        await this.registrarInscricao(sub);
      }

      return { sucesso: true };
    } catch (e) {
      return { sucesso: false, mensagem: e instanceof Error ? e.message : 'Falha ao ativar notificações.' };
    }
  }
};

