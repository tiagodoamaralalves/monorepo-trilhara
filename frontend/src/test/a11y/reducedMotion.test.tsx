import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { useReducedMotion } from '../../shared/hooks/useReducedMotion';
import { alertarTitulo, restaurarTituloAba } from '../../shared/services/alertaTitulo';

/**
 * `prefers-reduced-motion` (WCAG 2.2 §12.1.5) — R12.
 *
 * A F11 só tinha a media query em CSS, o que não impede animação dirigida por JS
 * (a alternância do título da aba). Aqui: `useReducedMotion()` lê o
 * `matchMedia` e, quando `reduce` está ativo, `alertarTitulo` **não alterna** o
 * título — ele fica com a mensagem estática, ainda informando o término.
 */

function stubMatchMedia(matches: boolean) {
  window.matchMedia = ((query: string) => ({
    matches,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false
  })) as unknown as typeof window.matchMedia;
}

describe('useReducedMotion', () => {
  const original = window.matchMedia;

  afterEach(() => {
    window.matchMedia = original;
  });

  it('retorna true quando a preferência de movimento reduzido está ativa', () => {
    stubMatchMedia(true);
    const { result } = renderHook(() => useReducedMotion());
    expect(result.current).toBe(true);
  });

  it('retorna false quando o usuário não registrou a preferência', () => {
    stubMatchMedia(false);
    const { result } = renderHook(() => useReducedMotion());
    expect(result.current).toBe(false);
  });

  it('degrada para false quando matchMedia não existe', () => {
    // Ambiente sem matchMedia (jsdom antigo / SSR simulado).
    Object.defineProperty(window, 'matchMedia', { value: undefined, writable: true, configurable: true });
    const { result } = renderHook(() => useReducedMotion());
    expect(result.current).toBe(false);
  });
});

describe('alertaTitulo — título da aba com e sem movimento reduzido', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    document.title = 'Trilhara';
  });

  afterEach(() => {
    restaurarTituloAba();
    vi.useRealTimers();
    document.title = 'Trilhara';
  });

  it('com movimento reduzido, o título NÃO alterna e permanece informando a mensagem', () => {
    alertarTitulo('Pausa curta finalizada!', { reduzirMovimento: true });
    expect(document.title).toBe('⏰ Pausa curta finalizada!');

    // Nenhuma alternância ao longo do tempo (era o comportamento anterior).
    act(() => {
      vi.advanceTimersByTime(900);
    });
    expect(document.title).toBe('⏰ Pausa curta finalizada!');

    act(() => {
      vi.advanceTimersByTime(900 * 5);
    });
    expect(document.title).toBe('⏰ Pausa curta finalizada!');
  });

  it('sem movimento reduzido, o título alterna e depois é restaurado', () => {
    alertarTitulo('Tempo de foco concluído!', { reduzirMovimento: false });

    // 1ª iteração exibe o título original…
    act(() => {
      vi.advanceTimersByTime(900);
    });
    expect(document.title).toBe('Trilhara');

    // …2ª iteração exibe a mensagem (alternância real).
    act(() => {
      vi.advanceTimersByTime(900);
    });
    expect(document.title).toBe('⏰ Tempo de foco concluído!');

    // Após o limite de iterações, volta ao original.
    act(() => {
      vi.advanceTimersByTime(900 * 30);
    });
    expect(document.title).toBe('Trilhara');
  });

  it('restaura o título ao focar a janela', () => {
    alertarTitulo('Meta de estudo concluída!', { reduzirMovimento: false });
    // 1ª iteração restaura o original, 2ª aplica a mensagem: o título alterna.
    act(() => {
      vi.advanceTimersByTime(900 * 2);
    });
    expect(document.title).toBe('⏰ Meta de estudo concluída!');

    act(() => {
      window.dispatchEvent(new Event('focus'));
    });
    expect(document.title).toBe('Trilhara');
  });

  it('restaurarTituloAba é idempotente', () => {
    alertarTitulo('Pausa longa finalizada!', { reduzirMovimento: true });
    restaurarTituloAba();
    expect(document.title).toBe('Trilhara');
    restaurarTituloAba();
    expect(document.title).toBe('Trilhara');
  });
});
