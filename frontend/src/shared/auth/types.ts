export type RoleUsuario = 'USER' | 'ADMIN';

export interface UsuarioAutenticado {
  id: string;
  nome: string;
  email: string;
  roles: RoleUsuario[];
  ativo: boolean;
  emailVerificado: boolean;
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

