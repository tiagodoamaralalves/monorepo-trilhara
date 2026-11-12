import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AuthStorage, limparDadosLocaisDoUsuario, AUTH_CONTA_EXCLUIDA_EVENT } from '../shared/auth/authStorage';
import { AuthService } from '../shared/auth/authService';
import { AuthProvider, useAuth } from '../shared/auth/AuthContext';
import { ContaModal } from '../shared/auth/ContaModal';
import { ConfirmProvider } from '../shared/design-system/ConfirmDialog/ConfirmDialog';
import { ToastProvider } from '../shared/design-system/Toast/Toast';
import * as apiModule from '../shared/auth/api';

describe('FASE 9 — Frontend: Exclusão de Conta, Limpeza Local e Modal de Confirmação', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  describe('1. Helper limparDadosLocaisDoUsuario', () => {
    it('deve remover todas as chaves do usuario no localStorage e sessionStorage preservando outros usuários', () => {
      const userA = '6af20000000000000000000a';
      const userB = '6af20000000000000000000b';

      // Chaves do usuário A
      localStorage.setItem(`trilhara:u:${userA}:study_timer`, JSON.stringify({ running: false }));
      localStorage.setItem(`trilhara:u:${userA}:nota_draft:1`, '# Minha nota');
      localStorage.setItem(`trilhara:u:${userA}:preferencias`, JSON.stringify({ pomodoro: 25 }));
      sessionStorage.setItem(`trilhara:u:${userA}:active_session`, 'sess-123');

      // Chaves de outro usuário B
      localStorage.setItem(`trilhara:u:${userB}:study_timer`, JSON.stringify({ running: true }));
      localStorage.setItem(`trilhara:u:${userB}:nota_draft:2`, '# Nota B');
      sessionStorage.setItem(`trilhara:u:${userB}:active_session`, 'sess-999');

      // Resíduo legado de versões anteriores (deve ser tolerado/limpo junto à sessão)
      localStorage.setItem('trilhara_auth_' + 'access_token', 'jwt-legado');

      const eventoDisparadoSpy = vi.fn();
      window.addEventListener(AUTH_CONTA_EXCLUIDA_EVENT, eventoDisparadoSpy);

      // Executa a limpeza
      limparDadosLocaisDoUsuario(userA);

      // Verifica que as chaves de A foram completamente removidas
      expect(localStorage.getItem(`trilhara:u:${userA}:study_timer`)).toBeNull();
      expect(localStorage.getItem(`trilhara:u:${userA}:nota_draft:1`)).toBeNull();
      expect(localStorage.getItem(`trilhara:u:${userA}:preferencias`)).toBeNull();
      expect(sessionStorage.getItem(`trilhara:u:${userA}:active_session`)).toBeNull();

      // Sessão foi limpa em memória (D-09: nada de token/usuário em storage)
      expect(AuthStorage.obterAccessToken()).toBeNull();
      expect(AuthStorage.obterUsuario()).toBeNull();
      expect(localStorage.getItem('trilhara_auth_' + 'access_token')).toBeNull();

      // Dados do usuário B permanecem 100% intactos
      expect(localStorage.getItem(`trilhara:u:${userB}:study_timer`)).not.toBeNull();
      expect(localStorage.getItem(`trilhara:u:${userB}:nota_draft:2`)).toBe('# Nota B');
      expect(sessionStorage.getItem(`trilhara:u:${userB}:active_session`)).toBe('sess-999');

      // Disparou evento no window
      expect(eventoDisparadoSpy).toHaveBeenCalledTimes(1);
      expect(eventoDisparadoSpy.mock.calls[0][0].detail).toEqual({ usuarioId: userA });

      window.removeEventListener(AUTH_CONTA_EXCLUIDA_EVENT, eventoDisparadoSpy);
    });
  });

  describe('2. AuthService.excluirConta', () => {
    it('deve chamar DELETE /api/auth/conta com Idempotency-Key e limpar dados locais após 202', async () => {
      const userA = '6af20000000000000000000a';
      AuthStorage.salvarSessao({
        accessToken: 'fake-jwt',
        tokenType: 'Bearer',
        expiresIn: 900,
        usuario: {
          id: userA,
          nome: 'Tiago Amaral',
          email: 'tiago@trilhara.com.br',
          papeis: ['USER'], preferencias: { tema: 'dark', disponibilidadeSemanalMinutos: [0,0,0,0,0,0,0], padraoLembreteMinutosAntes: 15 },
          emailVerificado: true,
          ativo: true,
          criadoEm: '2026-11-09T00:00:00Z'
        }
      });

      localStorage.setItem(`trilhara:u:${userA}:timer`, 'dados');

      const requestSpy = vi.spyOn(apiModule, 'request').mockResolvedValue({
        operationId: 'op-12345',
        estado: 'PENDENTE'
      });

      const resposta = await AuthService.excluirConta({
        senha: 'MinhaSenhaSegura123!',
        confirmacao: 'EXCLUIR MINHA CONTA',
        idempotencyKey: 'idem-test-key'
      });

      expect(resposta).toEqual({ operationId: 'op-12345', estado: 'PENDENTE' });
      expect(requestSpy).toHaveBeenCalledTimes(1);
      expect(requestSpy).toHaveBeenCalledWith(
        '/auth/conta',
        expect.objectContaining({
          method: 'DELETE',
          headers: expect.objectContaining({
            'Idempotency-Key': 'idem-test-key'
          }),
          body: JSON.stringify({
            senha: 'MinhaSenhaSegura123!',
            confirmacao: 'EXCLUIR MINHA CONTA',
            idempotencyKey: 'idem-test-key'
          })
        })
      );

      // Limpeza imediata no storage
      expect(localStorage.getItem(`trilhara:u:${userA}:timer`)).toBeNull();
      expect(AuthStorage.obterUsuario()).toBeNull();
      expect(AuthStorage.obterAccessToken()).toBeNull();
    });
  });

  describe('3. Componente ContaModal e ConfirmDialog', () => {
    it('deve renderizar dados da conta, exigir ConfirmDialog destrutivo e depois solicitar senha e texto de confirmacao', async () => {
      const user = userEvent.setup();
      const mockExcluir = vi.fn().mockResolvedValue(undefined);
      const mockClose = vi.fn();

      const usuarioMock = {
        id: '6af21234567890abcdef1234',
        nome: 'Carlos Drummond',
        email: 'carlos@trilhara.com.br',
        papeis: ['USER'] as ('USER' | 'ADMIN')[],
        preferencias: { tema: 'dark' as const, disponibilidadeSemanalMinutos: [0, 0, 0, 0, 0, 0, 0], padraoLembreteMinutosAntes: 15 },
        emailVerificado: true,
        ativo: true,
        criadoEm: '2026-11-09T00:00:00Z'
      };

      render(
        <ToastProvider>
          <ConfirmProvider>
            <ContaModal
              isOpen={true}
              onClose={mockClose}
              usuario={usuarioMock}
              onExcluirConta={mockExcluir}
            />
          </ConfirmProvider>
        </ToastProvider>
      );

      // 1. Dados do perfil e Zona de Perigo visíveis
      expect(screen.getByText('Carlos Drummond')).toBeInTheDocument();
      expect(screen.getByText('carlos@trilhara.com.br')).toBeInTheDocument();
      expect(screen.getByText('Zona de Perigo')).toBeInTheDocument();

      const botaoExcluir = screen.getByRole('button', { name: /Excluir minha conta/i });
      expect(botaoExcluir).toBeInTheDocument();

      // 2. Clica no botão "Excluir minha conta" -> deve abrir o ConfirmDialog destrutivo
      await user.click(botaoExcluir);

      // ConfirmDialog destrutivo na tela
      expect(screen.getByText('Excluir minha conta permanentemente')).toBeInTheDocument();
      expect(screen.getByText(/Todos os seus estudos, cronogramas, sessões, tarefas, notas e lixeiras serão permanentemente apagados/i)).toBeInTheDocument();

      const botaoProsseguir = screen.getByRole('button', { name: /Prosseguir para exclusão/i });
      await user.click(botaoProsseguir);

      // 3. Etapa de autorização com formulário de senha e confirmação textual
      expect(screen.getByLabelText(/Senha atual/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Confirmação textual/i)).toBeInTheDocument();

      const botaoConfirmarDefinitivo = screen.getByRole('button', { name: /Confirmar Exclusão Definitiva/i });
      // Deve estar desabilitado enquanto texto e senha não forem preenchidos
      expect(botaoConfirmarDefinitivo).toBeDisabled();

      // Preenche senha
      await user.type(screen.getByLabelText(/Senha atual/i), 'Senha123!');
      expect(botaoConfirmarDefinitivo).toBeDisabled();

      // Preenche confirmação textual incorreta
      await user.type(screen.getByLabelText(/Confirmação textual/i), 'excluir');
      expect(botaoConfirmarDefinitivo).toBeDisabled();

      // Preenche confirmação textual exata
      await user.clear(screen.getByLabelText(/Confirmação textual/i));
      await user.type(screen.getByLabelText(/Confirmação textual/i), 'EXCLUIR MINHA CONTA');

      // Agora deve estar habilitado
      expect(botaoConfirmarDefinitivo).toBeEnabled();

      // Submete o formulário
      await user.click(botaoConfirmarDefinitivo);

      expect(mockExcluir).toHaveBeenCalledWith('Senha123!');
      await waitFor(() => {
        expect(mockClose).toHaveBeenCalled();
      });
    });

    it('deve exibir mensagem de erro na tela quando onExcluirConta rejeitar', async () => {
      const user = userEvent.setup();
      const mockExcluir = vi.fn().mockRejectedValue(new Error('Senha atual incorreta.'));
      const mockClose = vi.fn();

      render(
        <ToastProvider>
          <ConfirmProvider>
            <ContaModal
              isOpen={true}
              onClose={mockClose}
              usuario={{ id: '1', nome: 'Ana', email: 'ana@teste.com', papeis: ['USER'], preferencias: { tema: 'dark', disponibilidadeSemanalMinutos: [0,0,0,0,0,0,0], padraoLembreteMinutosAntes: 15 }, emailVerificado: true, ativo: true, criadoEm: '2026-11-09T00:00:00Z' }}
              onExcluirConta={mockExcluir}
            />
          </ConfirmProvider>
        </ToastProvider>
      );

      // Inicia e confirma no ConfirmDialog
      await user.click(screen.getByRole('button', { name: /Excluir minha conta/i }));
      await user.click(screen.getByRole('button', { name: /Prosseguir para exclusão/i }));

      // Preenche credenciais
      await user.type(screen.getByLabelText(/Senha atual/i), 'SenhaErrada!');
      await user.type(screen.getByLabelText(/Confirmação textual/i), 'EXCLUIR MINHA CONTA');

      await user.click(screen.getByRole('button', { name: /Confirmar Exclusão Definitiva/i }));

      // Exibe erro na tela
      expect(await screen.findByText('Senha atual incorreta.')).toBeInTheDocument();
      expect(mockClose).not.toHaveBeenCalled();
    });
  });

  describe('4. AuthContext - Integração abrirContaModal e excluirConta', () => {
    it('deve abrir e fechar o modal de conta através do useAuth', async () => {
      const TestConsumer: React.FC = () => {
        const { isContaModalOpen, abrirContaModal, fecharContaModal } = useAuth();
        return (
          <div>
            <button onClick={abrirContaModal}>Abrir Conta</button>
            <button onClick={fecharContaModal}>Fechar Conta</button>
            <div data-testid="status">{isContaModalOpen ? 'ABERTO' : 'FECHADO'}</div>
          </div>
        );
      };

      const user = userEvent.setup();
      render(
        <ToastProvider>
          <ConfirmProvider>
            <AuthProvider>
              <TestConsumer />
            </AuthProvider>
          </ConfirmProvider>
        </ToastProvider>
      );

      expect(screen.getByTestId('status')).toHaveTextContent('FECHADO');
      await user.click(screen.getByRole('button', { name: 'Abrir Conta' }));
      expect(screen.getByTestId('status')).toHaveTextContent('ABERTO');
      await user.click(screen.getByRole('button', { name: 'Fechar Conta' }));
      expect(screen.getByTestId('status')).toHaveTextContent('FECHADO');
    });
  });
});
