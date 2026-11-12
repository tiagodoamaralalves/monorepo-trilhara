import React from 'react';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { AuthProvider, useAuth } from '../shared/auth/AuthContext';
import { AuthStorage } from '../shared/auth/authStorage';
import { ToastProvider } from '../shared/design-system/Toast/Toast';

const perfil = {
  id: '6af20000000000000000000a',
  nome: 'Ana Beatriz',
  email: 'ana@trilhara.com.br',
  emailVerificado: true,
  papeis: ['USER'],
  preferencias: { tema: 'light', disponibilidadeSemanalMinutos: [0, 0, 0, 0, 0, 0, 0], padraoLembreteMinutosAntes: 15 },
  ativo: true,
  criadoEm: '2026-11-12T00:00:00Z'
};

const SondaSessao: React.FC = () => {
  const { usuario, carregando } = useAuth();
  return (
    <div>
      <span data-testid="carregando">{carregando ? 'SIM' : 'NAO'}</span>
      <span data-testid="email">{usuario?.email ?? 'ANONIMO'}</span>
      <span data-testid="tema">{usuario?.preferencias?.tema ?? '-'}</span>
    </div>
  );
};

describe('boot da sessão (R2/D-09) — refresh single-flight e /auth/me', () => {
  const fetchOriginal = globalThis.fetch;

  beforeEach(() => {
    AuthStorage.__resetarParaTeste();
    localStorage.clear();
    sessionStorage.clear();
    vi.restoreAllMocks();
    window.history.replaceState(null, '', '/');
  });

  afterEach(() => {
    globalThis.fetch = fetchOriginal;
    AuthStorage.__resetarParaTeste();
    localStorage.clear();
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it('restaura a sessão por POST /auth/refresh seguido de GET /auth/me', async () => {
    const chamadas: string[] = [];
    globalThis.fetch = vi.fn().mockImplementation(async (url: string, init?: RequestInit) => {
      chamadas.push(`${init?.method ?? 'GET'} ${url}`);
      if (url === '/api/auth/refresh') {
        return new Response(
          JSON.stringify({ accessToken: 'jwt-boot', tokenType: 'Bearer', expiresIn: 900, usuario: perfil }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        );
      }
      if (url === '/api/auth/me') {
        return new Response(JSON.stringify(perfil), {
          status: 200,
          headers: { 'Content-Type': 'application/json' }
        });
      }
      return new Response('{}', { status: 404 });
    }) as typeof fetch;

    render(
      <ToastProvider>
        <AuthProvider>
          <SondaSessao />
        </AuthProvider>
      </ToastProvider>
    );

    await waitFor(() => expect(screen.getByTestId('email')).toHaveTextContent('ana@trilhara.com.br'));
    expect(screen.getByTestId('carregando')).toHaveTextContent('NAO');
    expect(screen.getByTestId('tema')).toHaveTextContent('light');
    expect(chamadas.filter((c) => c === 'POST /api/auth/refresh')).toHaveLength(1);
    expect(chamadas).toContain('GET /api/auth/me');
    // Nada de token/usuário em storage após o boot.
    expect(Object.keys(localStorage).filter((c) => c.includes('auth'))).toEqual([]);
  });

  it('duas chamadas simultâneas de refresh produzem um único POST', async () => {
    let resolvidos = 0;
    globalThis.fetch = vi.fn().mockImplementation(async (url: string) => {
      if (url === '/api/auth/refresh') {
        resolvidos += 1;
        return new Response(
          JSON.stringify({ accessToken: 'jwt-unico', tokenType: 'Bearer', expiresIn: 900, usuario: perfil }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        );
      }
      if (url === '/api/auth/me') {
        return new Response(JSON.stringify(perfil), {
          status: 200,
          headers: { 'Content-Type': 'application/json' }
        });
      }
      return new Response('{}', { status: 404 });
    }) as typeof fetch;

    const { tentarRenovarToken } = await import('../shared/auth/api');
    const [a, b] = await Promise.all([tentarRenovarToken(), tentarRenovarToken()]);

    expect(resolvidos).toBe(1);
    expect(a.accessToken).toBe('jwt-unico');
    expect(b.accessToken).toBe('jwt-unico');
  });

  it('sem cookie válido no boot, permanece anônimo e sem erro', async () => {
    globalThis.fetch = vi.fn().mockImplementation(async () => {
      return new Response(JSON.stringify({ code: 'UNAUTHORIZED' }), { status: 401 });
    }) as typeof fetch;

    render(
      <ToastProvider>
        <AuthProvider>
          <SondaSessao />
        </AuthProvider>
      </ToastProvider>
    );

    await waitFor(() => expect(screen.getByTestId('carregando')).toHaveTextContent('NAO'));
    expect(screen.getByTestId('email')).toHaveTextContent('ANONIMO');
  });
});
