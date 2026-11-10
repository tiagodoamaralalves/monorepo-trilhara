import React, { createContext, useContext, useEffect, useState, useCallback, useMemo, useRef } from 'react';
import { AuthStorage, AUTH_CHANGE_EVENT, AUTH_CONTA_EXCLUIDA_EVENT, limparDadosLocaisDoUsuario } from './authStorage';
import { AuthService } from './authService';
import { consumirHandoffSsoDaUrl } from './sso';
import { AUTH_REQUIRED_EVENT } from './api';
import { AuthModal } from './AuthModal';
import { ContaModal } from './ContaModal';
import { AuthResponse, CadastroPayload, LoginPayload, UsuarioAutenticado } from './types';

export interface AuthContextValue {
  usuario: UsuarioAutenticado | null;
  isAuthenticated: boolean;
  carregando: boolean;
  isAuthModalOpen: boolean;
  abrirAuthModal: () => void;
  fecharAuthModal: () => void;
  isContaModalOpen: boolean;
  abrirContaModal: () => void;
  fecharContaModal: () => void;
  login: (payload: LoginPayload) => Promise<AuthResponse>;
  cadastrar: (payload: CadastroPayload) => Promise<AuthResponse>;
  logout: () => Promise<void>;
  excluirConta: (senha: string) => Promise<{ operationId: string; estado: string }>;
  verificarSessao: () => Promise<UsuarioAutenticado | null>;
  setUsuario: (usuario: UsuarioAutenticado | null) => void;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export interface AuthProviderProps {
  children: React.ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [usuario, setUsuario] = useState<UsuarioAutenticado | null>(() => AuthStorage.obterUsuario());
  const [carregando, setCarregando] = useState<boolean>(true);
  const [isAuthModalOpen, setIsAuthModalOpen] = useState<boolean>(false);
  const [isContaModalOpen, setIsContaModalOpen] = useState<boolean>(false);
  const ultimaVerificacaoRef = useRef<number>(0);

  const abrirAuthModal = useCallback(() => setIsAuthModalOpen(true), []);
  const fecharAuthModal = useCallback(() => setIsAuthModalOpen(false), []);
  const abrirContaModal = useCallback(() => setIsContaModalOpen(true), []);
  const fecharContaModal = useCallback(() => setIsContaModalOpen(false), []);

  const verificarSessao = useCallback(async (): Promise<UsuarioAutenticado | null> => {
    // 1. Verifica se recebeu código de handoff SSO via URL de outro módulo do ecossistema
    try {
      const consumiu = await consumirHandoffSsoDaUrl();
      if (consumiu) {
        const u = AuthStorage.obterUsuario();
        setUsuario(u);
        setCarregando(false);
        return u;
      }
    } catch {
      // Ignora erro no handoff e tenta sessão silenciosa
    }

    try {
      const u = await AuthService.verificarSessaoSilenciosa();
      setUsuario(u);
      return u;
    } catch {
      setUsuario(null);
      return null;
    } finally {
      setCarregando(false);
    }
  }, []);

  useEffect(() => {
    let ativo = true;
    void verificarSessao().then((u) => {
      if (ativo) setUsuario(u);
    });
    return () => {
      ativo = false;
    };
  }, [verificarSessao]);

  // Sincroniza com alterações de auth (logout automático, renovação de token, ou mudanças em outra aba)
  useEffect(() => {
    const handleAuthChange = () => {
      setUsuario(AuthStorage.obterUsuario());
    };

    window.addEventListener(AUTH_CHANGE_EVENT, handleAuthChange);
    window.addEventListener('storage', handleAuthChange);

    return () => {
      window.removeEventListener(AUTH_CHANGE_EVENT, handleAuthChange);
      window.removeEventListener('storage', handleAuthChange);
    };
  }, []);

  // Reação ao evento 401 de reautenticação sem desmontar a árvore de componentes
  useEffect(() => {
    const handleAuthRequired = () => {
      setIsAuthModalOpen(true);
    };

    window.addEventListener(AUTH_REQUIRED_EVENT, handleAuthRequired);
    return () => {
      window.removeEventListener(AUTH_REQUIRED_EVENT, handleAuthRequired);
    };
  }, []);

  // Sincronização ao focar a aba com throttle de 5s e single-flight por aba
  useEffect(() => {
    let verificacaoEmAndamento = false;

    const tentarSincronizarAba = async () => {
      if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return;
      const agora = Date.now();
      if (agora - ultimaVerificacaoRef.current < 5000) return;
      if (verificacaoEmAndamento) return;

      ultimaVerificacaoRef.current = agora;
      verificacaoEmAndamento = true;
      try {
        const u = await AuthService.verificarSessaoSilenciosa();
        setUsuario(u);
      } catch {
        setUsuario(null);
      } finally {
        verificacaoEmAndamento = false;
      }
    };

    window.addEventListener('focus', tentarSincronizarAba);
    document.addEventListener('visibilitychange', tentarSincronizarAba);

    return () => {
      window.removeEventListener('focus', tentarSincronizarAba);
      document.removeEventListener('visibilitychange', tentarSincronizarAba);
    };
  }, []);

  const login = useCallback(async (payload: LoginPayload): Promise<AuthResponse> => {
    const res = await AuthService.login(payload);
    setUsuario(res.usuario);
    setIsAuthModalOpen(false);
    return res;
  }, []);

  const cadastrar = useCallback(async (payload: CadastroPayload): Promise<AuthResponse> => {
    const res = await AuthService.cadastrar(payload);
    setUsuario(res.usuario);
    setIsAuthModalOpen(false);
    return res;
  }, []);

  const logout = useCallback(async (): Promise<void> => {
    try {
      await AuthService.logout();
    } finally {
      setUsuario(null);
    }
  }, []);

  const excluirConta = useCallback(async (senha: string): Promise<{ operationId: string; estado: string }> => {
    const u = usuario || AuthStorage.obterUsuario();
    const res = await AuthService.excluirConta({ senha, confirmacao: 'EXCLUIR MINHA CONTA' });
    if (u?.id) {
      limparDadosLocaisDoUsuario(u.id);
    }
    setUsuario(null);
    setIsContaModalOpen(false);
    return res;
  }, [usuario]);

  useEffect(() => {
    const handleContaExcluida = () => {
      setUsuario(null);
      setIsContaModalOpen(false);
    };
    window.addEventListener(AUTH_CONTA_EXCLUIDA_EVENT, handleContaExcluida);
    return () => {
      window.removeEventListener(AUTH_CONTA_EXCLUIDA_EVENT, handleContaExcluida);
    };
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      usuario,
      isAuthenticated: Boolean(usuario),
      carregando,
      isAuthModalOpen,
      abrirAuthModal,
      fecharAuthModal,
      isContaModalOpen,
      abrirContaModal,
      fecharContaModal,
      login,
      cadastrar,
      logout,
      excluirConta,
      verificarSessao,
      setUsuario
    }),
    [usuario, carregando, isAuthModalOpen, abrirAuthModal, fecharAuthModal, isContaModalOpen, abrirContaModal, fecharContaModal, login, cadastrar, logout, excluirConta, verificarSessao]
  );

  return (
    <AuthContext.Provider value={value}>
      {children}
      <AuthModal
        isOpen={isAuthModalOpen}
        onClose={fecharAuthModal}
        onAutenticado={() => fecharAuthModal()}
      />
      {isContaModalOpen && (
        <ContaModal
          isOpen={isContaModalOpen}
          onClose={fecharContaModal}
          usuario={usuario}
          onExcluirConta={excluirConta}
        />
      )}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextValue => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth deve ser utilizado dentro de um AuthProvider');
  }
  return context;
};
