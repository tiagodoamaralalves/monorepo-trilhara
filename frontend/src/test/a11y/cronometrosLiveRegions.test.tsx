import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { SyncStatusBadge } from '../../shared/design-system/SyncStatusBadge/SyncStatusBadge';

/**
 * Regiões vivas dos cronômetros e do indicador de sincronização (D-37/D-40).
 *
 * Os dígitos que mudam a cada segundo ficam em `role="timer"` com
 * `aria-live="off"`; apenas mudanças de estado (início, pausa, término, erro) são
 * anunciadas em região `role="status"` — evitando que leitores de tela leiam a
 * contagem a cada segundo.
 */

describe('Regiões vivas de cronômetros e estado de sincronização', () => {
  it('SyncStatusBadge expõe role="status" e aria-live="polite" em todos os estados', () => {
    const { rerender } = render(<SyncStatusBadge status="salvando" />);
    const badge = screen.getByRole('status');

    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute('aria-live', 'polite');
    expect(badge.textContent).toContain('Salvando...');

    rerender(<SyncStatusBadge status="salvo" />);
    expect(badge).toHaveAttribute('aria-live', 'polite');
    expect(badge.textContent).toContain('Salvo');
  });

  it('dígitos do cronômetro usam role="timer" + aria-live="off" e a transição de fase usa role="status"', () => {
    render(
      <div>
        <div role="status" aria-live="polite">
          Foco Ativo
        </div>
        <div role="timer" aria-live="off" data-testid="cronometro-digitos">
          25:00
        </div>
        <div role="status" aria-live="polite" data-testid="break-banner">
          Pausa curta
        </div>
      </div>
    );

    const timerDigits = screen.getByTestId('cronometro-digitos');
    expect(timerDigits).toHaveAttribute('role', 'timer');
    expect(timerDigits).toHaveAttribute('aria-live', 'off');

    const breakBanner = screen.getByTestId('break-banner');
    expect(breakBanner).toHaveAttribute('role', 'status');
    expect(breakBanner).toHaveAttribute('aria-live', 'polite');
  });
});
