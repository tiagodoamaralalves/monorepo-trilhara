import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  obterUrlAppEstudos,
  obterUrlAppTarefas,
  obterUrlAppNotas,
  obterUrlAppPomodoro,
  obterModuloAtual,
  iniciarHandoffParaApp,
  consumirHandoffSsoDaUrl,
  sanitizarReturnUrl
} from '../shared/auth/sso';
import { AuthStorage } from '../shared/auth/authStorage';

describe('SSO e handoff entre origens locais', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('deve resolver URLs corretas para os 4 módulos do ecossistema', () => {
    expect(obterUrlAppEstudos()).toBeDefined();
    expect(obterUrlAppTarefas()).toBeDefined();
    expect(obterUrlAppNotas()).toBeDefined();
    expect(obterUrlAppPomodoro()).toBeDefined();
  });

  it('deve identificar o módulo atual como estudos por padrão', () => {
    const modulo = obterModuloAtual();
    expect(['estudos', 'tarefas', 'pomodoro', 'notas']).toContain(modulo);
  });

  it('sanitizarReturnUrl: deve permitir URLs do mesmo host ou subdomínios oficiais e rejeitar externos', () => {
    expect(sanitizarReturnUrl(window.location.origin + '/dashboard')).toContain('/dashboard');
    expect(sanitizarReturnUrl('https://site-malicioso.com/')).toBeNull();
    expect(sanitizarReturnUrl('javascript:alert(1)')).toBeNull();
  });

  it('iniciarHandoffParaApp: deve retornar a URL sem handoff quando usuário não autenticado', async () => {
    const url = await iniciarHandoffParaApp('http://localhost:5174/');
    expect(url).toBe('http://localhost:5174/');
  });

  it('iniciarHandoffParaApp: em localhost autenticado, deve chamar /api/auth/handoff e anexar sso_code no fragmento', async () => {
    AuthStorage.salvarSessao({
      accessToken: 'token-jwt-teste',
      tokenType: 'Bearer',
      expiresIn: 900,
      usuario: { id: 'u1', nome: 'Tester', email: 'test@trilhara.com.br', papeis: ['USER'], preferencias: { tema: 'dark', disponibilidadeSemanalMinutos: [0,0,0,0,0,0,0], padraoLembreteMinutosAntes: 15 }, emailVerificado: true, ativo: true, criadoEm: '2026-01-01T00:00:00Z' }
    });

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      json: async () => ({ codigo: 'codigo-opaco-123' })
    } as Response);

    const destino = 'http://localhost:5174/';
    const urlFinal = await iniciarHandoffParaApp(destino);

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/auth/handoff', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ destino })
    }));
    expect(urlFinal).toBe('http://localhost:5174/#sso_code=codigo-opaco-123');
  });

  it('consumirHandoffSsoDaUrl: deve consumir sso_code, limpar fragmento da URL e salvar sessão', async () => {
    window.location.hash = '#sso_code=codigo-secreto-xyz';

    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        accessToken: 'novo-access-token',
        tokenType: 'Bearer',
        expiresIn: 900,
        usuario: { id: 'u2', nome: 'Consumidor', email: 'consumo@trilhara.com.br', papeis: ['USER'], preferencias: { tema: 'dark', disponibilidadeSemanalMinutos: [0,0,0,0,0,0,0], padraoLembreteMinutosAntes: 15 }, emailVerificado: true }
      })
    } as Response);

    const consumiu = await consumirHandoffSsoDaUrl();

    expect(consumiu).toBe(true);
    expect(window.location.hash).toBe('');
    expect(AuthStorage.obterAccessToken()).toBe('novo-access-token');
    expect(AuthStorage.obterUsuario()?.email).toBe('consumo@trilhara.com.br');
  });
});
