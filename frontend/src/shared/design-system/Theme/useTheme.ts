import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { salvarTemaPreferencia } from '../../auth/authService';

export type ThemeMode = 'dark' | 'light' | 'system';
export type ThemeResolvido = 'dark' | 'light';

const STORAGE_THEME_ANON_KEY = 'trilhara:anon:tema';
const THEME_CHANGE_EVENT = 'trilhara:theme-change';

/** Cache local por usuário; visitante usa `trilhara:anon:tema`. */
export function chaveCacheTema(usuarioId?: string | null): string {
  return usuarioId ? `trilhara:u:${usuarioId}:tema` : STORAGE_THEME_ANON_KEY;
}

export function preferenciaSystem(): ThemeResolvido {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return 'dark';
  try {
    return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
  } catch {
    return 'dark';
  }
}

/** Resolve `system` para o tema efetivo que deve ir para `data-theme`. */
export function resolverTema(tema: ThemeMode): ThemeResolvido {
  return tema === 'system' ? preferenciaSystem() : tema;
}

function normalizar(valor: string | null): ThemeMode | null {
  return valor === 'dark' || valor === 'light' || valor === 'system' ? valor : null;
}

/** Lê o cache local (nunca lança: ambiente sem storage retorna `null`). */
export function lerCacheTema(usuarioId?: string | null): ThemeMode | null {
  if (typeof window === 'undefined') return null;
  try {
    return normalizar(window.localStorage.getItem(chaveCacheTema(usuarioId)));
  } catch {
    return null;
  }
}

/** Grava o cache local; falha é silenciosa (preferência continua válida no servidor). */
export function gravarCacheTema(usuarioId: string | null | undefined, tema: ThemeMode): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(chaveCacheTema(usuarioId), tema);
  } catch {
    // Ignora indisponibilidade de storage
  }
}

/** Aplica o tema resolvido em `documentElement`. */
export function aplicarTema(tema: ThemeMode): void {
  if (typeof document === 'undefined') return;
  document.documentElement.setAttribute('data-theme', resolverTema(tema));
}

/**
 * Tema efetivo para o primeiro paint, lido **sincronamente** antes de `createRoot`
 * (a CSP proíbe script inline no `index.html`).
 */
export function temaInicial(usuarioId?: string | null): ThemeMode {
  return lerCacheTema(usuarioId) ?? lerCacheTema(null) ?? 'dark';
}

/** Notifica abas/componentes da mesma origem sobre mudança de tema. */
function notificarTema(tema: ThemeMode): void {
  if (typeof window === 'undefined') return;
  window.dispatchEvent(new CustomEvent<ThemeMode>(THEME_CHANGE_EVENT, { detail: tema }));
}

function ouvirMudancaTema(handler: (tema: ThemeMode) => void): () => void {
  if (typeof window === 'undefined') return () => undefined;
  const listener = (event: Event) => {
    const detalhe = (event as CustomEvent<ThemeMode>).detail;
    handler(detalhe === 'dark' || detalhe === 'light' || detalhe === 'system' ? detalhe : 'dark');
  };
  window.addEventListener(THEME_CHANGE_EVENT, listener);
  return () => window.removeEventListener(THEME_CHANGE_EVENT, listener);
}

function ouvirMudancaSistema(handler: () => void): () => void {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return () => undefined;
  try {
    const media = window.matchMedia('(prefers-color-scheme: light)');
    media.addEventListener('change', handler);
    return () => media.removeEventListener('change', handler);
  } catch {
    return () => undefined;
  }
}


export function useTheme() {
  const { usuario, setUsuario } = useAuth();
  const usuarioId = usuario?.id ?? null;

  const [theme, setThemeState] = useState<ThemeMode>(() => {
    const atual = lerCacheTema(usuarioId) ?? temaInicial(null);
    aplicarTema(atual);
    return atual;
  });

  useEffect(() => {
    aplicarTema(theme);
  }, [theme]);

  // Preferência do servidor manda: aplica e regrava o cache local por usuário.
  useEffect(() => {
    if (!usuario?.preferencias?.tema) return;
    const doServidor = usuario.preferencias.tema;
    setThemeState(doServidor);
    gravarCacheTema(usuario.id, doServidor);
    aplicarTema(doServidor);
  }, [usuario?.id, usuario?.preferencias?.tema]);

  useEffect(() => {
    const cancelarTema = ouvirMudancaTema(setThemeState);
    const cancelarSistema = ouvirMudancaSistema(() => {
      setThemeState((atual) => {
        if (atual === 'system') aplicarTema('system');
        return atual;
      });
    });
    return () => {
      cancelarTema();
      cancelarSistema();
    };
  }, []);

  const setTheme = useCallback(
    (next: ThemeMode) => {
      setThemeState(next);
      gravarCacheTema(usuarioId, next);
      aplicarTema(next);
      notificarTema(next);

      if (usuarioId && usuario) {
        // Otimista: mantém a UI responsiva e sincroniza o servidor em seguida.
        setUsuario({ ...usuario, preferencias: { ...usuario.preferencias, tema: next } });
        void salvarTemaPreferencia(next, usuario, setUsuario);
      }
    },
    [usuarioId, usuario, setUsuario]
  );

  const toggleTheme = useCallback(() => {
    const resolvido = resolverTema(theme);
    setTheme(resolvido === 'dark' ? 'light' : 'dark');
  }, [theme, setTheme]);

  return {
    theme,
    isLight: resolverTema(theme) === 'light',
    isDark: resolverTema(theme) === 'dark',
    setTheme,
    toggleTheme
  };
}

