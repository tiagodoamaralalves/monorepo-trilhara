import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { SyncStatusBadge } from '../shared/design-system';

/**
 * `SyncStatusBadge` (F8/D-31) — quatro estados, rótulos exatos do Anexo B e retry.
 */
describe('SyncStatusBadge — estados de sincronização', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('salvo: rótulo "Salvo" com role="status" e aria-live="polite"', () => {
    render(<SyncStatusBadge status="salvo" />);
    const badge = screen.getByRole('status');
    expect(badge).toHaveAttribute('aria-live', 'polite');
    expect(badge).toHaveAttribute('data-status', 'salvo');
    expect(screen.getByText('Salvo')).toBeInTheDocument();
  });

  it('salvando: rótulo "Salvando..."', () => {
    render(<SyncStatusBadge status="salvando" />);
    expect(screen.getByText('Salvando...')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveAttribute('data-status', 'salvando');
  });

  it('alteracoes-locais: rótulo "Alterações locais pendentes"', () => {
    render(<SyncStatusBadge status="alteracoes-locais" />);
    expect(screen.getByText('Alterações locais pendentes')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveAttribute('data-status', 'alteracoes-locais');
  });

  it('erro: rótulo "Erro ao salvar" e botão "Tentar novamente" acionável', () => {
    const aoTentarNovamente = vi.fn();
    render(<SyncStatusBadge status="erro" onRetry={aoTentarNovamente} />);

    expect(screen.getByText('Erro ao salvar')).toBeInTheDocument();
    const botao = screen.getByRole('button', { name: /tentar novamente/i });
    fireEvent.click(botao);
    expect(aoTentarNovamente).toHaveBeenCalledTimes(1);
  });

  it('erro sem onRetry não renderiza botão de retry', () => {
    render(<SyncStatusBadge status="erro" />);
    expect(screen.queryByRole('button', { name: /tentar novamente/i })).not.toBeInTheDocument();
  });
});
