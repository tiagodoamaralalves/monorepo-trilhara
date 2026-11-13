import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ContaMenuButton } from '../shared/design-system/ContaMenuButton/ContaMenuButton';
import { AppSidebar } from '../shared/design-system/AppSidebar/AppSidebar';
import { AuthContext, AuthContextValue } from '../shared/auth/AuthContext';
import { ToastProvider } from '../shared/design-system/Toast/Toast';

const USUARIO = {
  id: '6af20000000000000000000a',
  nome: 'Aluno Teste',
  email: 'aluno@trilhara.com.br',
  papeis: ['USER'] as ('USER' | 'ADMIN')[],
  preferencias: {
    tema: 'dark' as const,
    disponibilidadeSemanalMinutos: [0, 0, 0, 0, 0, 0, 0],
    padraoLembreteMinutosAntes: 15
  },
  emailVerificado: true,
  ativo: true,
  criadoEm: '2026-11-09T00:00:00Z'
};

function authValue(abrirContaModal = vi.fn()): AuthContextValue {
  return {
    usuario: USUARIO,
    isAuthenticated: true,
    carregando: false,
    isAuthModalOpen: false,
    abrirAuthModal: vi.fn(),
    fecharAuthModal: vi.fn(),
    isContaModalOpen: false,
    abrirContaModal,
    fecharContaModal: vi.fn(),
    excluirConta: vi.fn(),
    login: vi.fn(),
    cadastrar: vi.fn(),
    logout: vi.fn(),
    verificarSessao: vi.fn(),
    setUsuario: vi.fn()
  };
}

const BRAND = { title: 'Trilhara', logoSrc: '/favicon.svg', titleAttr: 'Trilhara' };

describe('R10 — entrada de Conta centralizada (D-35f)', () => {
  it('a AppSidebar expõe a entrada de Conta no desktop e no mobile sem prop de ação', async () => {
    const user = userEvent.setup();
    const abrirContaModal = vi.fn();

    render(
      <ToastProvider>
        <AuthContext.Provider value={authValue(abrirContaModal)}>
          <AppSidebar
            brand={BRAND}
            user={{ nome: USUARIO.nome, email: USUARIO.email }}
            onLogout={vi.fn()}
          />
        </AuthContext.Provider>
      </ToastProvider>
    );

    const entradas = screen.getAllByRole('button', { name: 'Conta' });
    // Rodapé (desktop) + header mobile: a entrada existe nos dois pontos de montagem.
    expect(entradas.length).toBeGreaterThanOrEqual(2);

    await user.click(entradas[0]);
    expect(abrirContaModal).toHaveBeenCalledTimes(1);
  });

  it('a AppSidebar não exibe entrada de Conta para visitante', () => {
    const visitante: AuthContextValue = {
      ...authValue(),
      usuario: null,
      isAuthenticated: false
    };

    render(
      <ToastProvider>
        <AuthContext.Provider value={visitante}>
          <AppSidebar brand={BRAND} user={null} onLogin={vi.fn()} />
        </AuthContext.Provider>
      </ToastProvider>
    );

    expect(screen.queryByRole('button', { name: 'Conta' })).not.toBeInTheDocument();
  });

  /**
   * O Pomodoro não usa `AppSidebar`: monta o mesmo `ContaMenuButton` no header próprio.
   * Este caso cobre o quarto módulo (RI-36).
   */
  it('o header do Pomodoro (autenticado) expõe a mesma entrada de Conta', async () => {
    const user = userEvent.setup();
    const abrirContaModal = vi.fn();

    render(
      <AuthContext.Provider value={authValue(abrirContaModal)}>
        <ContaMenuButton variant="header" />
      </AuthContext.Provider>
    );

    const botao = screen.getByRole('button', { name: 'Conta' });
    expect(botao).toHaveAttribute('data-variant', 'header');

    await user.click(botao);
    expect(abrirContaModal).toHaveBeenCalledTimes(1);
  });

  it.each([
    ['Estudos', 'sidebar'],
    ['Tarefas', 'sidebar'],
    ['Notas', 'sidebar'],
    ['Pomodoro', 'header']
  ])('a entrada de Conta existe no módulo %s (variant %s)', (_, variant) => {
    render(
      <AuthContext.Provider value={authValue()}>
        <ContaMenuButton variant={variant as 'sidebar' | 'header'} nome={USUARIO.nome} />
      </AuthContext.Provider>
    );

    expect(screen.getByRole('button', { name: 'Conta' })).toBeInTheDocument();
  });
});
