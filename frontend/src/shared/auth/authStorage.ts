import { AuthResponse, UsuarioAutenticado } from './types';
import { registrarFalha } from '../diagnostico';

export const ACCESS_TOKEN_KEY = 'trilhara_auth_access_token';
export const USER_STORAGE_KEY = 'trilhara_auth_user';

export const AUTH_CHANGE_EVENT = 'trilhara:auth-change';
export const AUTH_LOGOUT_EVENT = 'trilhara:auth:logout';
export const AUTH_CONTA_EXCLUIDA_EVENT = 'trilhara:auth:conta-excluida';
const BROADCAST_CHANNEL_NAME = 'trilhara_auth_channel';

let broadcastChannel: BroadcastChannel | null = null;
if (typeof window !== 'undefined' && 'BroadcastChannel' in window) {
  try {
    broadcastChannel = new BroadcastChannel(BROADCAST_CHANNEL_NAME);
    broadcastChannel.onmessage = (event) => {
      if (event.data?.type === 'AUTH_SYNC') {
        window.dispatchEvent(new CustomEvent(AUTH_CHANGE_EVENT, { detail: { fromBroadcast: true } }));
      } else if (event.data?.type === 'AUTH_LOGOUT') {
        window.dispatchEvent(new CustomEvent(AUTH_LOGOUT_EVENT, { detail: { usuarioId: event.data.usuarioId } }));
        window.dispatchEvent(new CustomEvent(AUTH_CHANGE_EVENT, { detail: { fromBroadcast: true } }));
      } else if (event.data?.type === 'ACCOUNT_DELETED') {
        if (event.data.usuarioId) {
          limparDadosLocaisDoUsuario(event.data.usuarioId, false);
        }
      }
    };
  } catch {
    broadcastChannel = null;
  }
}

/**
 * Remove todas as chaves trilhara:u:${usuarioId}:* na origem atual e chaves de sessão.
 * Conta excluída remove timers e rascunhos locais dessa conta e notifica abas da mesma origem.
 */
export function limparDadosLocaisDoUsuario(usuarioId: string, emitirBroadcast: boolean = true): void {
  if (!usuarioId) return;

  try {
    const prefixo = `trilhara:u:${usuarioId}:`;

    // 1. Remover chaves do localStorage com o prefixo isolado do usuário
    if (typeof window !== 'undefined' && window.localStorage) {
      const chavesParaRemover: string[] = [];
      for (let i = 0; i < window.localStorage.length; i++) {
        const chave = window.localStorage.key(i);
        if (chave && chave.startsWith(prefixo)) {
          chavesParaRemover.push(chave);
        }
      }
      chavesParaRemover.forEach((chave) => window.localStorage.removeItem(chave));
    }

    // 2. Remover também do sessionStorage se houver chaves com mesmo prefixo
    if (typeof window !== 'undefined' && window.sessionStorage) {
      try {
        const chavesSession: string[] = [];
        for (let i = 0; i < window.sessionStorage.length; i++) {
          const chave = window.sessionStorage.key(i);
          if (chave && chave.startsWith(prefixo)) {
            chavesSession.push(chave);
          }
        }
        chavesSession.forEach((chave) => window.sessionStorage.removeItem(chave));
      } catch {
        // Ignora erro
      }
    }

    // 3. Limpar a sessão
    AuthStorage.limparSessao();

    // 4. Notificar abas da mesma origem via eventos locais e BroadcastChannel
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent(AUTH_CONTA_EXCLUIDA_EVENT, { detail: { usuarioId } }));
      if (emitirBroadcast && broadcastChannel) {
        try {
          broadcastChannel.postMessage({ type: 'ACCOUNT_DELETED', usuarioId, timestamp: Date.now() });
        } catch {
          // Ignora erro
        }
      }
    }
  } catch (err) {
    registrarFalha('auth.limparDadosLocais', err);
  }
}

function notificarMudancaAuth(): void {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(AUTH_CHANGE_EVENT));
    if (broadcastChannel) {
      try {
        broadcastChannel.postMessage({ type: 'AUTH_SYNC', timestamp: Date.now() });
      } catch {
        // Ignora erro de postMessage
      }
    }
  }
}

export const AuthStorage = {
  obterAccessToken(): string | null {
    try {
      return window.localStorage.getItem(ACCESS_TOKEN_KEY);
    } catch {
      return null;
    }
  },

  obterUsuario(): UsuarioAutenticado | null {
    try {
      const raw = window.localStorage.getItem(USER_STORAGE_KEY);
      if (!raw) return null;
      return JSON.parse(raw) as UsuarioAutenticado;
    } catch {
      return null;
    }
  },

  salvarSessao(resposta: AuthResponse): void {
    try {
      window.localStorage.setItem(ACCESS_TOKEN_KEY, resposta.accessToken);
      if (resposta.usuario) {
        window.localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(resposta.usuario));
      }
      notificarMudancaAuth();
    } catch {
      // Ignora erro em ambiente sem storage
    }
  },

  atualizarUsuario(usuario: UsuarioAutenticado): void {
    try {
      window.localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(usuario));
      notificarMudancaAuth();
    } catch {
      // Ignora erro
    }
  },

  limparSessao(): void {
    try {
      const usuarioAtual = this.obterUsuario();
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent(AUTH_LOGOUT_EVENT, { detail: { usuarioId: usuarioAtual?.id } }));
        if (broadcastChannel) {
          try {
            broadcastChannel.postMessage({ type: 'AUTH_LOGOUT', usuarioId: usuarioAtual?.id, timestamp: Date.now() });
          } catch {
            // Ignora erro
          }
        }
      }

      window.localStorage.removeItem(ACCESS_TOKEN_KEY);
      // Remove resquício de versões antigas que persistiam o refresh token
      window.localStorage.removeItem('trilhara_auth_refresh_token');
      window.localStorage.removeItem(USER_STORAGE_KEY);

      // Remove chaves de contexto e preferências de estudo
      window.localStorage.removeItem('trilhara_estudos_ativa_id');
      window.localStorage.removeItem('trilhara_mostrar_ciclo_tarefas');

      // Remove chaves de planejamento v1
      const chavesParaRemover: string[] = [];
      for (let i = 0; i < window.localStorage.length; i++) {
        const chave = window.localStorage.key(i);
        if (chave && chave.startsWith('trilhara_planejamento_v1:')) {
          chavesParaRemover.push(chave);
        }
      }
      chavesParaRemover.forEach((chave) => window.localStorage.removeItem(chave));

      // Limpa sessionStorage de edital pendente
      try {
        window.sessionStorage.removeItem('trilhara:pending_import_edital_id');
      } catch {
        // Ignora erro
      }

      notificarMudancaAuth();
    } catch {
      // Ignora erro
    }
  },

  limparDadosLocaisDoUsuario(usuarioId: string): void {
    limparDadosLocaisDoUsuario(usuarioId);
  }
};

