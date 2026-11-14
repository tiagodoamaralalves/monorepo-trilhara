import { request } from '../auth/api';

export type PushPermissionState = 'granted' | 'denied' | 'default' | 'unsupported' | 'ios-nao-instalado';

export interface PushConfig {
  habilitado: boolean;
  publicKey?: string;
  padraoMinutosAntes: number;
}

export interface AtualizarPreferenciasPushResult {
  padraoMinutosAntes: number;
  lembretesRecalculados: number;
}

/** Escopo do Service Worker: a raiz da origem do app Tarefas. */
const SW_URL = '/sw.js';
const SW_SCOPE = '/';
/** Timeout do DELETE de inscrição no logout: o logout nunca espera mais que isso. */
const TIMEOUT_DESINSCRICAO_MS = 2000;

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
  if (isIOSNonStandalone()) {
    return 'ios-nao-instalado';
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

  /**
 * Registra (ou reutiliza) o Service Worker **na raiz da origem**.
 *
 * <p>Somente o app Tarefas chama este método — os demais módulos nunca registram SW, de modo
 * que o escopo `/` pertence a uma única origem de aplicação.</p>
 */
  async registrarServiceWorker(): Promise<ServiceWorkerRegistration> {
    const existente = await navigator.serviceWorker.getRegistration(SW_SCOPE);
    if (existente) {
      return existente;
    }
    return navigator.serviceWorker.register(SW_URL, { scope: SW_SCOPE });
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

  /** Remove a inscrição do dispositivo no backend (body `{ endpoint }`, ). */
  async removerInscricao(endpoint: string): Promise<void> {
    await request('/push/subscriptions', {
      method: 'DELETE',
      body: JSON.stringify({ endpoint })
    });
  },

  /**
 * Remove a inscrição deste dispositivo, best-effort: qualquer falha ou demora acima de 2 s é
 * ignorada, porque o logout nunca pode bloquear.
 */
  async removerInscricaoDoDispositivo(): Promise<boolean> {
    if (!isPushSupported()) {
      return false;
    }
    try {
      const reg = await navigator.serviceWorker.getRegistration(SW_SCOPE);
      const sub = await reg?.pushManager.getSubscription();
      if (!sub) {
        return false;
      }
      await Promise.race([
        this.removerInscricao(sub.endpoint),
        new Promise((resolve) => setTimeout(resolve, TIMEOUT_DESINSCRICAO_MS))
      ]);
      await sub.unsubscribe().catch(() => undefined);
      return true;
    } catch {
      // Best-effort: o logout segue independentemente do resultado.
      return false;
    }
  },

  /**
 * Ativa as notificações neste dispositivo. O pedido de permissão acontece **apenas** aqui,
 * sempre em reação a um clique explícito do usuário.
 */
  async ativarNotificacoes(publicKey?: string): Promise<{ sucesso: boolean; mensagem?: string }> {
    if (!isPushSupported()) {
      return { sucesso: false, mensagem: 'Este navegador não suporta notificações. Suas tarefas funcionam normalmente.' };
    }

    if (isIOSNonStandalone()) {
      return {
        sucesso: false,
        mensagem:
          'Para receber notificações no iPhone/iPad, toque em Compartilhar → Adicionar à Tela de Início e abra o Trilhara por lá.'
      };
    }

    try {
      const permissao = await Notification.requestPermission();
      if (permissao !== 'granted') {
        return { sucesso: false, mensagem: 'As notificações estão bloqueadas nas configurações do navegador.' };
      }

      const reg = await this.registrarServiceWorker();
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

