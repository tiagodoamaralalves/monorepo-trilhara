import { ApiError, request, tentarRenovarToken } from './api';
import { AuthStorage, limparDadosLocaisDoUsuario } from './authStorage';
import {
  AuthResponse,
  CadastroPayload,
  LoginPayload,
  MensagemResponse,
  RecuperarSenhaPayload,
  ResetarSenhaPayload,
  UsuarioAutenticado
} from './types';

export const AuthService = {
  async cadastrar(payload: CadastroPayload): Promise<AuthResponse> {
    const resposta = await request<AuthResponse>('/auth/cadastro', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    AuthStorage.salvarSessao(resposta);
    return resposta;
  },

  async login(payload: LoginPayload): Promise<AuthResponse> {
    const resposta = await request<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    AuthStorage.salvarSessao(resposta);
    return resposta;
  },

  async refresh(): Promise<AuthResponse> {
    return await tentarRenovarToken();
  },

  async obterMeuPerfil(): Promise<UsuarioAutenticado> {
    const usuario = await request<UsuarioAutenticado>('/auth/me');
    AuthStorage.atualizarUsuario(usuario);
    return usuario;
  },

  async logout(): Promise<void> {
    try {
      await request('/auth/logout', { method: 'POST', body: '{}' });
    } catch (err) {
      // Falha de rede/servidor: limpa localmente, mas registra para diagnóstico
      console.warn('Falha ao revogar sessão no servidor.', err);
    } finally {
      AuthStorage.limparSessao();
    }
  },

  async recuperarSenha(payload: RecuperarSenhaPayload): Promise<MensagemResponse> {
    return request<MensagemResponse>('/auth/recuperar-senha', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },

  async solicitarRecuperacaoSenha(email: string): Promise<MensagemResponse> {
    return this.recuperarSenha({ email });
  },

  async resetarSenha(payloadOrToken: ResetarSenhaPayload | string, novaSenha?: string): Promise<void> {
    const payload: ResetarSenhaPayload = typeof payloadOrToken === 'string'
      ? { token: payloadOrToken, novaSenha: novaSenha ?? '' }
      : payloadOrToken;
    await request('/auth/resetar-senha', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },

  async confirmarEmail(token: string): Promise<UsuarioAutenticado> {
    const usuario = await request<UsuarioAutenticado>('/auth/confirmar-email', {
      method: 'POST',
      body: JSON.stringify({ token })
    });
    // Atualiza storage local se for o usuário atual
    const usuarioSalvo = AuthStorage.obterUsuario();
    if (usuarioSalvo && usuarioSalvo.id === usuario.id) {
      AuthStorage.atualizarUsuario({ ...usuarioSalvo, emailVerificado: true });
    }
    return usuario;
  },

  async reenviarConfirmacaoEmail(email?: string): Promise<MensagemResponse> {
    return request<MensagemResponse>('/auth/reenviar-confirmacao', {
      method: 'POST',
      body: JSON.stringify({ email })
    });
  },

  /**
   * Verifica a sessão existente no boot ou executa o Silent SSO via Cookie HttpOnly.
   * Se o usuário estiver logado em outro app do mesmo domínio, o cookie é aproveitado
   * e a sessão é restaurada automaticamente sem intervenção manual.
   */
  async verificarSessaoSilenciosa(): Promise<UsuarioAutenticado | null> {
    // 1. Caso haja token no storage local, valida o perfil
    const tokenSalvo = AuthStorage.obterAccessToken();

    if (tokenSalvo) {
      try {
        return await AuthService.obterMeuPerfil();
      } catch (err) {
        if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
          AuthStorage.limparSessao();
        } else if (err instanceof ApiError && (err.status === 0 || err.status >= 500)) {
          return AuthStorage.obterUsuario();
        } else if (!(err instanceof ApiError)) {
          return AuthStorage.obterUsuario();
        }
      }
    }

    // 2. Caso não haja token local (ex: app vizinho em outra porta/aba),
    // tenta restaurar a sessão via cookie HttpOnly enviando chamada silenciosa
    try {
      const sessaoRenovada = await tentarRenovarToken();
      if (sessaoRenovada?.usuario) {
        return sessaoRenovada.usuario;
      }
    } catch {
      // Não há cookie ou já expirou; segue deslogado normalmente
    }

    return null;
  },

  async excluirConta(payload: { senha: string; confirmacao?: string; idempotencyKey?: string }): Promise<{ operationId: string; estado: string }> {
    const usuarioAtual = AuthStorage.obterUsuario();
    const idempotencyKey = payload.idempotencyKey || (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `idemp-${Date.now()}`);
    const resposta = await request<{ operationId: string; estado: string }>('/auth/conta', {
      method: 'DELETE',
      headers: {
        'Idempotency-Key': idempotencyKey
      },
      body: JSON.stringify({
        senha: payload.senha,
        confirmacao: payload.confirmacao || 'EXCLUIR MINHA CONTA',
        idempotencyKey
      })
    });
    if (usuarioAtual?.id) {
      limparDadosLocaisDoUsuario(usuarioAtual.id);
    } else {
      AuthStorage.limparSessao();
    }
    return resposta;
  }
};

