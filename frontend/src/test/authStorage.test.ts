import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { AuthStorage, limparDadosLocaisDoUsuario } from '../shared/auth/authStorage';
import { AuthService } from '../shared/auth/authService';
import type { AuthResponse, UsuarioAutenticado } from '../shared/auth/types';

const usuarioBase: UsuarioAutenticado = {
  id: '6af20000000000000000000a',
  nome: 'Ana Beatriz',
  email: 'ana@trilhara.com.br',
  emailVerificado: true,
  papeis: ['USER'],
  preferencias: { tema: 'dark', disponibilidadeSemanalMinutos: [0, 0, 0, 0, 0, 0, 0], padraoLembreteMinutosAntes: 15 },
  ativo: true,
  criadoEm: '2026-11-12T00:00:00Z'
};

const respostaLogin: AuthResponse = {
  accessToken: 'jwt-em-memoria',
  tokenType: 'Bearer',
  expiresIn: 900,
  usuario: usuarioBase
};

describe('authStorage (R2/D-09) — sessão somente em memória', () => {
  beforeEach(() => {
    AuthStorage.__resetarParaTeste();
    localStorage.clear();
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    AuthStorage.__resetarParaTeste();
    localStorage.clear();
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it('após login não grava token nem usuário em localStorage/sessionStorage', () => {
    AuthStorage.salvarSessao(respostaLogin);

    expect(AuthStorage.obterAccessToken()).toBe('jwt-em-memoria');
    expect(AuthStorage.obterUsuario()?.email).toBe('ana@trilhara.com.br');

    const chavesLocal = Object.keys(localStorage);
    const chavesSession = Object.keys(sessionStorage);

    expect(chavesLocal).not.toContain('trilhara_auth_' + 'token');
    expect(chavesLocal).not.toContain('trilhara_auth_' + 'access_token');
    expect(chavesLocal).not.toContain('trilhara_auth_' + 'refresh_token');
    expect(chavesLocal).not.toContain('trilhara_' + 'user');
    expect(chavesLocal).not.toContain('trilhara_auth_' + 'user');
    expect(chavesSession).not.toContain('trilhara_auth_' + 'access_token');

    expect(chavesLocal.filter((c) => c.startsWith('trilhara_auth'))).toEqual([]);
    expect(chavesSession.filter((c) => c.startsWith('trilhara_auth'))).toEqual([]);
    expect(JSON.stringify(localStorage)).not.toContain('jwt-em-memoria');
  });

  it('limparSessao apaga token e perfil da memória sem tocar em storage', () => {
    AuthStorage.salvarSessao(respostaLogin);
    AuthStorage.limparSessao();

    expect(AuthStorage.obterAccessToken()).toBeNull();
    expect(AuthStorage.obterUsuario()).toBeNull();
    expect(Object.keys(localStorage)).toHaveLength(0);
  });

  it('limparDadosLocaisDoUsuario remove apenas as chaves trilhara:u:{id}:* da conta', () => {
    const userA = '6af20000000000000000000a';
    const userB = '6af20000000000000000000b';
    localStorage.setItem(`trilhara:u:${userA}:nota_draft:1`, '# A');
    localStorage.setItem(`trilhara:u:${userA}:tema`, 'light');
    localStorage.setItem(`trilhara:u:${userB}:nota_draft:2`, '# B');

    limparDadosLocaisDoUsuario(userA);

    expect(localStorage.getItem(`trilhara:u:${userA}:nota_draft:1`)).toBeNull();
    expect(localStorage.getItem(`trilhara:u:${userA}:tema`)).toBeNull();
    expect(localStorage.getItem(`trilhara:u:${userB}:nota_draft:2`)).toBe('# B');
  });

  it('authService não persiste a sessão em storage após login', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify(respostaLogin), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      })
    );

    await AuthService.login({ email: 'ana@trilhara.com.br', senha: 'Senh@Forte2026!' });

    expect(AuthStorage.obterAccessToken()).toBe('jwt-em-memoria');
    expect(Object.keys(localStorage).filter((c) => c.includes('auth'))).toEqual([]);
  });
});
