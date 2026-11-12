import { AuthResponse, UsuarioAutenticado } from './types';
import { registrarFalha } from '../diagnostico';
import { assinar, definirUsuarioCorrente, publicar } from '../sync/barramento';

/**
 * Sessão em memória (D-09).
 *
 * O access token e o perfil vivem **somente** em variáveis privadas deste módulo. Nada de
 * `localStorage`/`sessionStorage`: o refresh token é cookie HttpOnly e o boot restaura a
 * sessão via `POST /api/auth/refresh` (single-flight em `api.ts`) seguido de
 * `GET /api/auth/me`.
 */
export const AUTH_CHANGE_EVENT = 'trilhara:auth-change';
/** Logout local ou propagado por outra aba (D-21.d). */
const AUTH_LOGOUT_EVENT = 'trilhara:logout';
/** Conta excluída nesta aba ou em outra (D-21.d). */
export const AUTH_CONTA_EXCLUIDA_EVENT = 'trilhara:conta-excluida';

let accessTokenEmMemoria: string | null = null;
let usuarioEmMemoria: UsuarioAutenticado | null = null;

// Sincronização entre abas pelo barramento único (D-21), sem canal próprio.
assinar('SESSAO_ENCERRADA', (mensagem) => {
  accessTokenEmMemoria = null;
  usuarioEmMemoria = null;
  window.dispatchEvent(
    new CustomEvent(AUTH_LOGOUT_EVENT, { detail: { usuarioId: mensagem.usuarioId } })
  );
  window.dispatchEvent(new CustomEvent(AUTH_CHANGE_EVENT, { detail: { fromBroadcast: true } }));
});

assinar('CONTA_EXCLUIDA', (mensagem) => {
  const usuarioId = mensagem.usuarioId;
  if (usuarioId) limparDadosLocaisDoUsuario(usuarioId, false);
});


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
      if (emitirBroadcast) {
        // Outra aba ainda pode ter a conta em memória: `usuarioId` explícito no envelope.
        publicar('CONTA_EXCLUIDA', null, usuarioId);
      }
    }
  } catch (err) {
    registrarFalha('auth.limparDadosLocais', err);
  }
}

function notificarMudancaAuth(): void {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(AUTH_CHANGE_EVENT));
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
      definirUsuarioCorrente(resposta.usuario.id);
    }
    notificarMudancaAuth();
  },

  /** Atualiza o perfil em memória (ex.: e-mail confirmado, tema alterado). */
  atualizarUsuario(usuario: UsuarioAutenticado): void {
    usuarioEmMemoria = usuario;
    definirUsuarioCorrente(usuario.id);
    notificarMudancaAuth();
  },

  /** Encerra a sessão em memória e notifica abas da mesma origem. */
  limparSessao(): void {
    const usuarioAtual = usuarioEmMemoria;
    accessTokenEmMemoria = null;
    usuarioEmMemoria = null;

    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent(AUTH_LOGOUT_EVENT, { detail: { usuarioId: usuarioAtual?.id } }));
      if (usuarioAtual?.id) {
        publicar('SESSAO_ENCERRADA', null, usuarioAtual.id);
      }
      definirUsuarioCorrente(null);
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
