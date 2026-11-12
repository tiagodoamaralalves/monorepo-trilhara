import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  aplicarTema,
  chaveCacheTema,
  gravarCacheTema,
  lerCacheTema,
  preferenciaSystem,
  resolverTema,
  temaInicial
} from '../shared/design-system/Theme/useTheme';

describe('Tema (R2/D-10) — cache por usuário, system e primeiro paint', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    vi.restoreAllMocks();
  });

  afterEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    vi.restoreAllMocks();
  });

  it('usa chave por usuário e namespace anônimo separado', () => {
    expect(chaveCacheTema('6af20000000000000000000a')).toBe('trilhara:u:6af20000000000000000000a:tema');
    expect(chaveCacheTema(null)).toBe('trilhara:anon:tema');
    expect(chaveCacheTema(undefined)).toBe('trilhara:anon:tema');

    // Nunca usa a chave global antiga.
    gravarCacheTema('6af20000000000000000000a', 'light');
    expect(localStorage.getItem('trilhara_theme')).toBeNull();
    expect(localStorage.getItem('trilhara:u:6af20000000000000000000a:tema')).toBe('light');
  });

  it('lê apenas valores válidos do cache', () => {
    localStorage.setItem('trilhara:anon:tema', 'neon');
    expect(lerCacheTema(null)).toBeNull();

    localStorage.setItem('trilhara:anon:tema', 'system');
    expect(lerCacheTema(null)).toBe('system');
  });

  it('aplica data-theme resolvendo system via prefers-color-scheme', () => {
    const matchMediaMock = vi.fn().mockImplementation((query: string) => ({
      matches: query.includes('light'),
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn()
    }) as unknown as MediaQueryList);
    Object.defineProperty(window, 'matchMedia', { value: matchMediaMock, writable: true, configurable: true });

    expect(preferenciaSystem()).toBe('light');
    expect(resolverTema('system')).toBe('light');

    aplicarTema('system');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');

    aplicarTema('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  it('temaInicial prioriza o cache do usuário e cai no anônimo/default', () => {
    expect(temaInicial(null)).toBe('dark');

    gravarCacheTema(null, 'light');
    expect(temaInicial(null)).toBe('light');

    gravarCacheTema('6af20000000000000000000a', 'dark');
    expect(temaInicial('6af20000000000000000000a')).toBe('dark');
  });

  it('aplicarTema é seguro quando não há prefers-color-scheme disponível', () => {
    Object.defineProperty(window, 'matchMedia', {
      value: () => {
        throw new Error('sem matchMedia');
      },
      writable: true,
      configurable: true
    });
    expect(preferenciaSystem()).toBe('dark');
    aplicarTema('system');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });
});
