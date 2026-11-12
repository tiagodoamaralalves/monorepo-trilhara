import { AuthResponse, UsuarioAutenticado } from './types';
import { registrarFalha } from '../diagnostico';

/**
 * Sessão em memória (D-09).
 *
 * O access token e o perfil vivem **somente** em variáveis privadas deste módulo. Nada de
 * `localStorage`/`sessionStorage`: o refresh token é cookie HttpOnly e o boot restaura a
 * sessão via `POST /api/auth/refresh` (single-flight em `api.ts`) seguido de
 * `GET /api/auth/me`.
 */
export const AUTH_CHANGE_EVENT = 'trilhara:auth-change';
const AUTH_LOGOUT_EVENT = 'trilhara:auth:logout';
export const AUTH_CONTA_EXCLUIDA_EVENT = 'trilhara:auth:conta-excluida';
const BROADCAST_CHANNEL_NAME = 'trilhara_auth_channel';

let accessTokenEmMemoria: string | null = null;
let usuarioEmMemoria: UsuarioAutenticado | null = null;

let broadcastChannel: BroadcastChannel | null = null;
if (typeof window !== 'undefined' && 'BroadcastChannel' in window) {
  try {
    broadcastChannel = new BroadcastChannel(BROADCAST_CHANNEL_NAME);
    broadcastChannel.onmessage = (event) => {
      if (event.data?.type === 'AUTH_SYNC') {
        window.dispatchEvent(new CustomEvent(AUTH_CHANGE_EVENT, { detail: { fromBroadcast: true } }));
      } else if (event.data?.type === 'AUTH_LOGOUT') {
        // Sessão de outra aba da mesma origem deixou de existir: espelha localmente.
        accessTokenEmMemoria = null;
        usuarioEmMemoria = null;
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
 * Remove todas as chaves `trilhara:u:${usuarioId}:*` da origem atual e a sessão em memória.
 * Conta excluída remove timers e rascunhos locais dessa conta e notifica abas da mesma origem.
 */
export function limparDadosLocaisDoUsuario(usuarioId: string, emitirBroadcast: boolean = true): void {
  if (!usuarioId) return;

  try {
    const prefixo = `trilhara:u:${usuarioId}:`;

    if (typeof window !== 'undefined' && window.localStorage) {
      const chavesParaRemover: string[] = [];
      for (let i = 0; i < window.localStorage.length; i++) {
        const chave = window.localStorage.key(i);
        if (chave && (chave.startsWith(prefixo) || chave.startsWith('trilhara_auth_'))) {
          chavesParaRemover.push(chave);
        }
      }
      chavesParaRemover.forEach((chave) => window.localStorage.removeItem(chave));
    }

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
        // Ignora indisponibilidade do sessionStorage
      }
    }

    AuthStorage.limparSessao();

    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent(AUTH_CONTA_EXCLUIDA_EVENT, { detail: { usuarioId } }));
      if (emitirBroadcast && broadcastChannel) {
        try {
          broadcastChannel.postMessage({ type: 'ACCOUNT_DELETED', usuarioId, timestamp: Date.now() });
        } catch {
          // Ignora erro de postMessage
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
  /** Access token em memória; nunca persistido. */
  obterAccessToken(): string | null {
    return accessTokenEmMemoria;
  },

  /** Perfil em memória; nunca persistido. */
  obterUsuario(): UsuarioAutenticado | null {
    return usuarioEmMemoria;
  },

  /** Guarda a sessão recebida de login/cadastro/refresh/handoff (somente em memória). */
  salvarSessao(resposta: AuthResponse): void {
    accessTokenEmMemoria = resposta.accessToken ?? null;
    if (resposta.usuario) {
      usuarioEmMemoria = resposta.usuario;
    }
    notificarMudancaAuth();
  },

  /** Atualiza o perfil em memória (ex.: e-mail confirmado, tema alterado). */
  atualizarUsuario(usuario: UsuarioAutenticado): void {
    usuarioEmMemoria = usuario;
    notificarMudancaAuth();
  },

  /** Encerra a sessão em memória e notifica abas da mesma origem. */
  limparSessao(): void {
    const usuarioAtual = usuarioEmMemoria;
    accessTokenEmMemoria = null;
    usuarioEmMemoria = null;

    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent(AUTH_LOGOUT_EVENT, { detail: { usuarioId: usuarioAtual?.id } }));
      if (broadcastChannel) {
        try {
          broadcastChannel.postMessage({
            type: 'AUTH_LOGOUT',
            usuarioId: usuarioAtual?.id,
            timestamp: Date.now()
          });
        } catch {
          // Ignora erro de postMessage
        }
      }
      try {
        window.sessionStorage.removeItem('trilhara:pending_import_edital_id');
      } catch {
        // Ignora indisponibilidade do sessionStorage
      }
    }

    notificarMudancaAuth();
  },

  limparDadosLocaisDoUsuario(usuarioId: string): void {
    limparDadosLocaisDoUsuario(usuarioId);
  },

  /** Apenas para testes: reinicia o estado em memória do módulo. */
  __resetarParaTeste(): void {
    accessTokenEmMemoria = null;
    usuarioEmMemoria = null;
  }
};
