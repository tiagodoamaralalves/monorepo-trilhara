import '@testing-library/jest-dom';
import { vi } from 'vitest';

/**
 * Stub de `window.matchMedia` para o ambiente jsdom.
 *
 * jsdom não implementa `matchMedia`. Vários componentes de produção passaram a
 * consultá-lo (`useReducedMotion`, `AppSidebar`/`data-bottom-nav`) e, sem
 * um stub global, cada teste precisaria registrar o seu — e qualquer teste que
 * montasse o shell quebraria com `window.matchMedia is not a function`.
 *
 * O padrão é "não casa" (desktop, sem movimento reduzido). Testes que precisam
 * de outro comportamento sobrescrevem com `vi.stubGlobal`/`Object.defineProperty`.
 */
if (typeof window !== 'undefined' && typeof window.matchMedia !== 'function') {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    configurable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn()
    }))
  });
}

