type RoleUsuario = 'USER' | 'ADMIN';

export type TemaPreferencia = 'dark' | 'light' | 'system';

/** Preferências persistidas no servidor. */
export interface PreferenciasUsuario {
  tema: TemaPreferencia;
  disponibilidadeSemanalMinutos: number[];
  padraoLembreteMinutosAntes: number;
}

/**
 * Perfil autenticado — contrato único de login, cadastro, refresh, handoff e `/auth/me`
 *. Vive somente em memória.
 */
export interface UsuarioAutenticado {
  id: string;
  nome: string;
  email: string;
  emailVerificado: boolean;
  papeis: RoleUsuario[];
  preferencias: PreferenciasUsuario;
  ativo: boolean;
  criadoEm: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken?: string;
  tokenType: string;
  expiresIn: number;
  usuario: UsuarioAutenticado;
}

export interface CadastroPayload {
  nome: string;
  email: string;
  senha: string;
}

export interface LoginPayload {
  email: string;
  senha: string;
}

export type AuthMode = 'login' | 'cadastro' | 'recuperar';

export interface RecuperarSenhaPayload {
  email: string;
}

export interface ResetarSenhaPayload {
  token: string;
  novaSenha: string;
}

export interface MensagemResponse {
  detail: string;
}

