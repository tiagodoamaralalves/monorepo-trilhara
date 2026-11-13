import { ApiError, request, tentarRenovarToken } from './api';
import { registrarFalha } from '../diagnostico';
import { pushService } from '../services/pushService';
import { AuthStorage } from './authStorage';
import {
  AuthResponse,
  CadastroPayload,
  LoginPayload,
  MensagemResponse,
  PreferenciasUsuario,
  RecuperarSenhaPayload,
  ResetarSenhaPayload,
  TemaPreferencia,
  UsuarioAutenticado
} from './types';

/**
 * Persiste o tema no servidor (D-10) com rollback em caso de falha.
 *
 * O `localStorage` continua sendo apenas cache de primeiro paint; a fonte da verdade é
 * `PUT /api/usuarios/me/preferencias/tema`.
 */
export async function salvarTemaPreferencia(
  tema: TemaPreferencia,
  usuario: UsuarioAutenticado,
  setUsuario: (usuario: UsuarioAutenticado | null) => void
): Promise<void> {
  try {
    const preferencias = await request<PreferenciasUsuario>('/usuarios/me/preferencias/tema', {
      method: 'PUT',
      body: JSON.stringify({ tema })
    });
    const atualizado: UsuarioAutenticado = { ...usuario, preferencias };
    AuthStorage.atualizarUsuario(atualizado);
    setUsuario(atualizado);
  } catch (err) {
    registrarFalha('tema.persistir', err);
    // Rollback: restaura a preferência conhecida do servidor.
    setUsuario(usuario);
    throw err;
  }
}

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
      // SEC-12 / D-36e: a inscrição push deste dispositivo pertence à sessão que está saindo.
      // Best-effort com timeout interno: falha ou lentidão nunca bloqueiam o logout.
      await pushService.removerInscricaoDoDispositivo();
      await request('/auth/logout', { method: 'POST', body: '{}' });
    } catch (err) {
      // Falha de rede/servidor: limpa localmente, mas registra para diagnóstico
      registrarFalha('auth.revogarSessao', err);
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
   * Restaura a sessão no boot **sem** token persistido (D-09).
   *
   * 1. Se já houver access token em memória (outra aba do mesmo bundle), valida com `/auth/me`.
   * 2. Caso contrário, `POST /api/auth/refresh` (cookie HttpOnly, single-flight em `api.ts`)
   *    e então confirma o perfil com `GET /api/auth/me`.
   */
  async verificarSessaoSilenciosa(): Promise<UsuarioAutenticado | null> {
    const tokenEmMemoria = AuthStorage.obterAccessToken();

    if (tokenEmMemoria) {
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

    try {
      const sessaoRenovada = await tentarRenovarToken();
      if (!sessaoRenovada?.accessToken) {
        return null;
      }
      const perfil = await AuthService.obterMeuPerfil();
      return perfil ?? sessaoRenovada.usuario ?? null;
    } catch {
      // Sem cookie válido ou já expirado: segue deslogado normalmente.
      return null;
    }
  },

  /**
   * Solicita a exclusão definitiva da conta (D-35).
   *
   * <p>A chave de idempotência vai <strong>somente</strong> no cabeçalho `Idempotency-Key`.
   * `semRefresh` impede que o 401 pós-202 seja tratado como "sessão expirada": depois do aceite
   * as credenciais já foram revogadas, então o 401 significa "exclusão em andamento".</p>
   */
  async excluirConta(payload: {
    senha: string;
    confirmacao?: string;
    idempotencyKey?: string;
  }): Promise<{ operationId: string; estado: string }> {
    const idempotencyKey =
      payload.idempotencyKey ||
      (typeof crypto !== 'undefined' && crypto.randomUUID
        ? crypto.randomUUID()
        : `idemp-${Date.now()}`);
    return request<{ operationId: string; estado: string }>('/auth/conta', {
      method: 'DELETE',
      semRefresh: true,
      headers: {
        'Idempotency-Key': idempotencyKey
      },
      body: JSON.stringify({
        senha: payload.senha,
        confirmacao: payload.confirmacao || 'EXCLUIR MINHA CONTA'
      })
    });
  }
};

