import { AuthStorage } from './authStorage';
import { AuthResponse, UsuarioAutenticado } from './types';
import { obterTimezoneNavegador } from './api';

export type ModuloApp = 'estudos' | 'tarefas' | 'pomodoro' | 'notas';

export function obterModuloAtual(): ModuloApp {
  if (typeof window === 'undefined') return 'estudos';

  const { pathname, port, hostname } = window.location;

  if (port === '5174' || pathname.startsWith('/estudos') || hostname.startsWith('estudos.')) {
    return 'estudos';
  }
  if (port === '5173' || pathname.startsWith('/tarefas') || hostname.startsWith('tarefas.')) {
    return 'tarefas';
  }
  if (port === '5175' || pathname.startsWith('/pomodoro') || hostname.startsWith('pomodoro.')) {
    return 'pomodoro';
  }
  if (port === '5176' || pathname.startsWith('/notas') || hostname.startsWith('notas.')) {
    return 'notas';
  }

  return 'estudos';
}

export function isAmbienteLocal(): boolean {
  if (typeof window === 'undefined') return false;
  const host = window.location.hostname.toLowerCase();
  return host === 'localhost' || host === '127.0.0.1' || host.endsWith('.localhost');
}

export function obterUrlAppEstudos(): string {
  if (typeof window === 'undefined') return '/estudos/';

  const { hostname, port, protocol, pathname } = window.location;

  if (port === '5173' || port === '5174' || port === '5175' || port === '5176') {
    return `${protocol}//${hostname}:5174/`;
  }

  if (pathname.startsWith('/tarefas') || pathname.startsWith('/pomodoro') || pathname.startsWith('/notas')) {
    return `${protocol}//${hostname}${port ? `:${port}` : ''}/estudos/`;
  }

  if (hostname.startsWith('tarefas.') || hostname.startsWith('pomodoro.') || hostname.startsWith('notas.')) {
    const novoHost = hostname.replace(/^(tarefas|pomodoro|notas)\./, 'estudos.');
    return `${protocol}//${novoHost}${port ? `:${port}` : ''}/`;
  }

  return '/estudos/';
}

export function obterUrlAppTarefas(): string {
  if (typeof window === 'undefined') return '/tarefas/';

  const { hostname, port, protocol, pathname } = window.location;

  if (port === '5173' || port === '5174' || port === '5175' || port === '5176') {
    return `${protocol}//${hostname}:5173/`;
  }

  if (pathname.startsWith('/estudos') || pathname.startsWith('/pomodoro') || pathname.startsWith('/notas')) {
    return `${protocol}//${hostname}${port ? `:${port}` : ''}/tarefas/`;
  }

  if (hostname.startsWith('estudos.') || hostname.startsWith('pomodoro.') || hostname.startsWith('notas.')) {
    const novoHost = hostname.replace(/^(estudos|pomodoro|notas)\./, 'tarefas.');
    return `${protocol}//${novoHost}${port ? `:${port}` : ''}/`;
  }

  return '/tarefas/';
}

export function obterUrlAppPomodoro(): string {
  if (typeof window === 'undefined') return '/pomodoro/';

  const { hostname, port, protocol, pathname } = window.location;

  if (port === '5173' || port === '5174' || port === '5175' || port === '5176') {
    return `${protocol}//${hostname}:5175/`;
  }

  if (pathname.startsWith('/estudos') || pathname.startsWith('/tarefas') || pathname.startsWith('/notas')) {
    return `${protocol}//${hostname}${port ? `:${port}` : ''}/pomodoro/`;
  }

  if (hostname.startsWith('estudos.') || hostname.startsWith('tarefas.') || hostname.startsWith('notas.')) {
    const novoHost = hostname.replace(/^(estudos|tarefas|notas)\./, 'pomodoro.');
    return `${protocol}//${novoHost}${port ? `:${port}` : ''}/`;
  }

  return '/pomodoro/';
}

export function obterUrlAppNotas(): string {
  if (typeof window === 'undefined') return '/notas/';

  const { hostname, port, protocol, pathname } = window.location;

  if (port === '5173' || port === '5174' || port === '5175' || port === '5176') {
    return `${protocol}//${hostname}:5176/`;
  }

  if (pathname.startsWith('/estudos') || pathname.startsWith('/tarefas') || pathname.startsWith('/pomodoro')) {
    return `${protocol}//${hostname}${port ? `:${port}` : ''}/notas/`;
  }

  if (hostname.startsWith('estudos.') || hostname.startsWith('tarefas.') || hostname.startsWith('pomodoro.')) {
    const novoHost = hostname.replace(/^(estudos|tarefas|pomodoro)\./, 'notas.');
    return `${protocol}//${novoHost}${port ? `:${port}` : ''}/`;
  }

  return '/notas/';
}

/**
 * Inicia a transição SSO para outro módulo Trilhara.
 * - Em produção: navega diretamente, pois o cookie SSO compartilhado (.trilhara.com.br) autentica a sessão.
 * - Em localhost: se autenticado, emite código de handoff de uso único via POST /api/auth/handoff.
 */
export async function iniciarHandoffParaApp(urlDestino: string): Promise<string> {
  if (typeof window === 'undefined') return urlDestino;

  // Se não estiver em ambiente local ou não estiver autenticado, navega direto
  const token = AuthStorage.obterAccessToken();
  if (!isAmbienteLocal() || !token) {
    return urlDestino;
  }

  try {
    const res = await fetch('/api/auth/handoff', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
        'X-Timezone': obterTimezoneNavegador()
      },
      body: JSON.stringify({ destino: urlDestino })
    });

    if (res.ok) {
      const data = (await res.json()) as { codigo: string };
      if (data?.codigo) {
        const urlObj = new URL(urlDestino, window.location.origin);
        urlObj.hash = `sso_code=${encodeURIComponent(data.codigo)}`;
        return urlObj.toString();
      }
    }
  } catch {
    // Fallback gracioso para URL original em caso de falha de rede
  }

  return urlDestino;
}

/**
 * Consome o código opaco de handoff presente no fragmento da URL.
 * Limpa imediatamente o fragmento da URL para que o código nunca apareça em histórico/logs.
 */
export async function consumirHandoffSsoDaUrl(): Promise<boolean> {
  if (typeof window === 'undefined') return false;

  const url = new URL(window.location.href);
  const hash = url.hash.replace(/^#/, '');
  if (!hash) return false;

  const params = new URLSearchParams(hash);
  const ssoCode = params.get('sso_code');

  if (ssoCode && ssoCode.trim()) {
    // 1. Limpa o fragmento da URL imediatamente
    params.delete('sso_code');
    const novoHash = params.toString() ? `#${params.toString()}` : '';
    window.history.replaceState(null, '', `${url.pathname}${url.search}${novoHash}`);

    try {
      const res = await fetch('/api/auth/handoff/consume', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Timezone': obterTimezoneNavegador()
        },
        credentials: 'include',
        body: JSON.stringify({ codigo: ssoCode.trim() })
      });

      if (res.ok) {
        const dados = (await res.json()) as AuthResponse;
        if (dados?.accessToken && dados?.usuario) {
          AuthStorage.salvarSessao(dados);
          return true;
        }
      }
    } catch {
      return false;
    }
  }

  // Fallback para handoff legado se houver
  return consumirTokenHandoffDaUrl();
}

/**
 * Aceita apenas URLs cujo origin pertença aos apps do ecossistema Trilhara.
 */
export function sanitizarReturnUrl(returnUrl: string | null): string | null {
  if (!returnUrl || typeof window === 'undefined') return null;
  try {
    const destino = new URL(returnUrl, window.location.origin);
    if (destino.protocol !== 'https:' && destino.protocol !== 'http:') return null;

    if (destino.origin === window.location.origin) {
      return destino.toString();
    }

    const origensPermitidas = new Set(
      [obterUrlAppEstudos(), obterUrlAppTarefas(), obterUrlAppPomodoro(), obterUrlAppNotas()].map(
        (u) => new URL(u, window.location.origin).origin
      )
    );
    if (origensPermitidas.has(destino.origin)) {
      return destino.toString();
    }

    const prefixosOficiais = ['estudos.', 'tarefas.', 'notas.', 'pomodoro.'];
    const extrairDominioBase = (hostname: string) => {
      const lower = hostname.toLowerCase();
      for (const prefixo of prefixosOficiais) {
        if (lower.startsWith(prefixo)) {
          return lower.slice(prefixo.length);
        }
      }
      return lower;
    };

    const baseAtual = extrairDominioBase(window.location.hostname);
    const baseDestino = extrairDominioBase(destino.hostname);

    if (baseAtual && baseAtual === baseDestino) {
      const destinoLower = destino.hostname.toLowerCase();
      const ehOficial = destinoLower === baseDestino || prefixosOficiais.some((p) => destinoLower.startsWith(p));
      if (ehOficial) {
        return destino.toString();
      }
    }

    return null;
  } catch {
    return null;
  }
}

/**
 * Legado / fallback silencioso
 */
export function gerarUrlComTokenHandoff(
  targetUrl: string,
  sessao: { accessToken: string; usuario: UsuarioAutenticado }
): string {
  const url = new URL(targetUrl, typeof window !== 'undefined' ? window.location.origin : 'http://localhost');
  const params = new URLSearchParams();
  params.set('auth_token', sessao.accessToken);
  params.set('auth_user', JSON.stringify(sessao.usuario));
  url.hash = params.toString();
  return url.toString();
}

export function consumirTokenHandoffDaUrl(): boolean {
  if (typeof window === 'undefined') return false;

  const url = new URL(window.location.href);
  const params = new URLSearchParams(url.hash.replace(/^#/, ''));
  const authToken = params.get('auth_token');
  const authUserRaw = params.get('auth_user');
  if (!authToken || !authUserRaw || typeof authToken !== 'string' || !authToken.trim()) return false;

  window.history.replaceState(null, '', `${url.pathname}${url.search}`);

  try {
    const usuario = JSON.parse(authUserRaw) as UsuarioAutenticado;
    if (!usuario || typeof usuario !== 'object' || !usuario.id || !usuario.email) return false;
    AuthStorage.salvarSessao({
      accessToken: authToken.trim(),
      tokenType: 'Bearer',
      expiresIn: 0,
      usuario
    });
    return true;
  } catch {
    return false;
  }
}
