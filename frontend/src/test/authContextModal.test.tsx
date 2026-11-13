import React, { useState } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AuthProvider, useAuth } from '../shared/auth/AuthContext';
import { AUTH_REQUIRED_EVENT } from '../shared/auth/api';
import { ToastProvider } from '../shared/design-system/Toast/Toast';

const ComponenteFilhoComFormulario: React.FC = () => {
  const [rascunho, setRascunho] = useState<string>('texto que não pode ser perdido');
  const { isAuthModalOpen } = useAuth();

  return (
    <div>
      <input
        aria-label="Rascunho de Nota"
        value={rascunho}
        onChange={(e) => setRascunho(e.target.value)}
      />
      <div data-testid="estado-modal">{isAuthModalOpen ? 'MODAL_ABERTO' : 'MODAL_FECHADO'}</div>
    </div>
  );
};

describe('AuthContext: reabertura do modal em 401 sem desmontar a árvore', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('deve abrir o AuthModal sobre a tela sem recarregar nem desmontar o formulário em edição', async () => {
    const user = userEvent.setup();
    render(
      <ToastProvider>
        <AuthProvider>
          <ComponenteFilhoComFormulario />
        </AuthProvider>
      </ToastProvider>
    );

    const input = screen.getByLabelText('Rascunho de Nota') as HTMLInputElement;
    expect(input.value).toBe('texto que não pode ser perdido');
    expect(screen.getByTestId('estado-modal')).toHaveTextContent('MODAL_FECHADO');

    // Usuário altera o rascunho
    await user.type(input, ' - alterado pelo usuário');
    expect(input.value).toBe('texto que não pode ser perdido - alterado pelo usuário');

    // Simula disparo do evento de 401 (falha de refresh)
    act(() => {
      window.dispatchEvent(new CustomEvent(AUTH_REQUIRED_EVENT));
    });

    // O modal deve estar aberto sobre a tela
    expect(screen.getByTestId('estado-modal')).toHaveTextContent('MODAL_ABERTO');

    // O input continua montado com exatamente o mesmo valor, sem perda de dados!
    expect(input.value).toBe('texto que não pode ser perdido - alterado pelo usuário');
  });
});
