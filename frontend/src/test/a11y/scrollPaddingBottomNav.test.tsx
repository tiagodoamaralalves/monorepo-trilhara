import { readFileSync } from 'fs';
import { resolve } from 'path';
import { render } from '@testing-library/react';
import { Clock } from 'lucide-react';
import { describe, it, expect, afterEach } from 'vitest';
import { AppSidebar } from '../../shared/design-system/AppSidebar/AppSidebar';
import { AuthContext, AuthContextValue } from '../../shared/auth/AuthContext';
import { UsuarioAutenticado } from '../../shared/auth/types';

/**
 * `scroll-padding-bottom` ligado à presença real da barra inferior (WCAG 2.2 SC 2.4.11) — .
 *
 * A regra anterior aplicava padding a `html`, `body`, `main` e a qualquer classe
 * com "list"/"container" abaixo de 900px, mesmo sem barra inferior, num
 * breakpoint diferente do usado pela barra (960px). Agora o atributo
 * `body[data-bottom-nav]` — definido pela `AppSidebar` — é quem liga a regra.
 */

const css = readFileSync(resolve(__dirname, '../../shared/styles/index.css'), 'utf-8');

const USUARIO_FIXTURE: UsuarioAutenticado = {
  id: '6af20000000000000000000a',
  nome: 'Aluno Teste',
  email: 'aluno@trilhara.com.br',
  emailVerificado: true,
  ativo: true,
  criadoEm: '2026-11-09T00:00:00Z',
  papeis: ['USER'],
  preferencias: {
    tema: 'dark',
    disponibilidadeSemanalMinutos: [0, 0, 0, 0, 0, 0, 0],
    padraoLembreteMinutosAntes: 15
  }
};

const authAnonimo: AuthContextValue = {
  usuario: null,
  isAuthenticated: false,
  carregando: false,
  isAuthModalOpen: false,
  abrirAuthModal: () => {},
  fecharAuthModal: () => {},
  isContaModalOpen: false,
  abrirContaModal: () => {},
  fecharContaModal: () => {},
  excluirConta: async () => ({ operationId: 'op-teste', estado: 'PENDENTE' }),
  login: async () => ({
    accessToken: 'token',
    tokenType: 'Bearer',
    expiresIn: 900,
    usuario: USUARIO_FIXTURE
  }),
  cadastrar: async () => ({
    accessToken: 'token',
    tokenType: 'Bearer',
    expiresIn: 900,
    usuario: USUARIO_FIXTURE
  }),
  logout: async () => {},
  verificarSessao: async () => null,
  setUsuario: () => {}
};

function montarSidebar() {
  return render(
    <AuthContext.Provider value={authAnonimo}>
      <AppSidebar
        brand={{ title: 'Trilhara', logoSrc: '/logo.svg', onClick: () => {} }}
        items={[{ id: 'hoje', label: 'Hoje', title: 'Hoje', icon: Clock }]}
        activeId="hoje"
        onNavigate={() => {}}
        showAppSwitcher={false}
      />
    </AuthContext.Provider>
  );
}

describe('scroll-padding-bottom condicionado à barra inferior', () => {
  afterEach(() => {
    delete document.body.dataset.bottomNav;
  });

  it('o CSS só aplica scroll-padding via body[data-bottom-nav="true"]', () => {
    expect(css).toContain("body[data-bottom-nav='true']");
    expect(css).toContain('scroll-padding-bottom: calc(var(--bottom-nav-height)');
    expect(css).toContain('--bottom-nav-height: 88px');
  });

  it('AppSidebar marca data-bottom-nav=false em viewport desktop', () => {
    montarSidebar();
    expect(document.body.dataset.bottomNav).toBe('false');
  });

  it('AppSidebar marca data-bottom-nav=true em viewport mobile (<= 960px)', () => {
    const original = window.matchMedia;
    window.matchMedia = ((query: string) => ({
      matches: query.includes('960px'),
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false
    })) as unknown as typeof window.matchMedia;

    try {
      montarSidebar();
      expect(document.body.dataset.bottomNav).toBe('true');
    } finally {
      window.matchMedia = original;
    }
  });

  it('remove o atributo ao desmontar (sem padding fantasma)', () => {
    const { unmount } = montarSidebar();
    expect(document.body.dataset.bottomNav).toBeDefined();
    unmount();
    expect(document.body.dataset.bottomNav).toBeUndefined();
  });
});
