import React, { useState, useMemo } from 'react';
import {
  Mail,
  Lock,
  User,
  ArrowRight,
  Eye,
  EyeOff,
  CheckCircle2,
  Circle,
  AlertCircle
} from 'lucide-react';
import { Button, Input, useToast } from '../design-system';
import logoMark from '../assets/logo-mark.svg';
import { AuthService } from './authService';
import { AuthContext } from './AuthContext';
import { AuthMode, UsuarioAutenticado } from './types';
import styles from './AuthForm.module.css';

export interface AuthFormProps {
  initialMode?: AuthMode;
  onAutenticado?: (usuario: UsuarioAutenticado, modo: AuthMode) => void;
  onAlternarModo?: (modo: AuthMode) => void;
  onEsqueciSenha?: () => void;
  showBrandHeader?: boolean;
  className?: string;
}

interface RequisitoSenha {
  id: string;
  label: string;
  cumprido: boolean;
}

function avaliarRequisitosSenha(senha: string, email: string): RequisitoSenha[] {
  const temTamanho = senha.length >= 8 && senha.length <= 128;
  const temMaiuscula = /\p{Lu}/u.test(senha);
  const temMinuscula = /\p{Ll}/u.test(senha);
  const temNumero = /\p{Nd}/u.test(senha);
  const temEspecial = /[^\p{Lu}\p{Ll}\p{Nd}\s]/u.test(senha);

  const arroba = email.indexOf('@');
  const parteLocal = (arroba > 0 ? email.slice(0, arroba) : email).trim().toLowerCase();
  const senhaLower = senha.toLowerCase();
  const segmentosEmail = parteLocal
    .split(/[^a-z0-9]+/)
    .filter((seg) => seg.length >= 4);
  const contemEmail =
    senha.length > 0 &&
    ((parteLocal.length >= 4 && senhaLower.includes(parteLocal)) ||
      segmentosEmail.some((seg) => senhaLower.includes(seg)));

  const lista: RequisitoSenha[] = [
    { id: 'len', label: 'Mínimo de 8 caracteres', cumprido: temTamanho },
    { id: 'upper', label: 'Letra maiúscula (A–Z)', cumprido: temMaiuscula },
    { id: 'lower', label: 'Letra minúscula (a–z)', cumprido: temMinuscula },
    { id: 'num', label: 'Pelo menos 1 número (0–9)', cumprido: temNumero },
    { id: 'spec', label: 'Caractere especial (!@#$%&*)', cumprido: temEspecial }
  ];

  if (parteLocal.length >= 4) {
    lista.push({
      id: 'no-email',
      label: 'Não conter parte do seu e-mail',
      cumprido: senha.length > 0 && !contemEmail
    });
  }

  return lista;
}

export const AuthForm: React.FC<AuthFormProps> = ({
  initialMode = 'login',
  onAutenticado,
  onAlternarModo,
  onEsqueciSenha,
  showBrandHeader = true,
  className = ''
}) => {
  const toast = useToast();
  const authContext = React.useContext(AuthContext);
  const [mode, setMode] = useState<AuthMode>(initialMode);
  const isCadastro = mode === 'cadastro';
  const isRecuperar = mode === 'recuperar';

  const [form, setForm] = useState({ nome: '', email: '', senha: '', confirmarSenha: '' });
  const { nome, email, senha, confirmarSenha } = form;
  const atualizarCampo =
    (campo: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
      setForm((prev) => ({ ...prev, [campo]: e.target.value }));

  const [mostrarSenha, setMostrarSenha] = useState(false);
  const [mostrarConfirmarSenha, setMostrarConfirmarSenha] = useState(false);
  const [submetendo, setSubmetendo] = useState(false);
  const [erroGeral, setErroGeral] = useState<string | null>(null);
  const [sucessoRecuperacao, setSucessoRecuperacao] = useState(false);
  const [confirmandoEmail, setConfirmandoEmail] = useState(false);
  const [emailConfirmadoSucesso, setEmailConfirmadoSucesso] = useState(false);

  React.useEffect(() => {
    if (typeof window === 'undefined') return;
    const params = new URLSearchParams(window.location.search);
    const pagina = params.get('pagina');
    const token = params.get('token');
    if (pagina === 'confirmar-email' && token) {
      setConfirmandoEmail(true);
      AuthService.confirmarEmail(token)
        .then((user) => {
          setEmailConfirmadoSucesso(true);
          authContext?.setUsuario(user);
          toast.success('E-mail confirmado com sucesso!');
        })
        .catch((err) => {
          setErroGeral(err instanceof Error ? err.message : 'Falha ao confirmar e-mail.');
        })
        .finally(() => {
          setConfirmandoEmail(false);
        });
    }
  }, []);

  const requisitosSenha = useMemo(
    () => avaliarRequisitosSenha(senha, email),
    [senha, email]
  );

  const senhaForteValida = useMemo(
    () => requisitosSenha.every((req) => req.cumprido),
    [requisitosSenha]
  );

  const trocarModo = (novoModo: AuthMode) => {
    setErroGeral(null);
    setSucessoRecuperacao(false);
    setForm((prev) => ({ ...prev, senha: '', confirmarSenha: '' }));
    setMode(novoModo);
    onAlternarModo?.(novoModo);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (submetendo) return;
    setErroGeral(null);

    const emailLimpo = email.trim();
    if (!emailLimpo) {
      setErroGeral('Informe seu endereço de e-mail.');
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(emailLimpo)) {
      setErroGeral('Informe um e-mail válido.');
      return;
    }

    if (isRecuperar) {
      setSubmetendo(true);
      try {
        await AuthService.solicitarRecuperacaoSenha(emailLimpo);
        setSucessoRecuperacao(true);
        toast.info('Instruções de recuperação enviadas.');
      } catch (err) {
        const mensagem =
          err instanceof Error
            ? err.message
            : 'Não foi possível solicitar a recuperação. Tente novamente.';
        setErroGeral(mensagem);
      } finally {
        setSubmetendo(false);
      }
      return;
    }

    if (isCadastro) {
      const nomeLimpo = nome.trim();
      if (nomeLimpo.length < 2) {
        setErroGeral('Informe seu nome completo (mínimo de 2 caracteres).');
        return;
      }
      if (!senhaForteValida) {
        setErroGeral(
          'Sua senha ainda não atende a todos os requisitos de segurança listados abaixo.'
        );
        return;
      }
      if (senha !== confirmarSenha) {
        setErroGeral('A confirmação de senha não confere com a senha digitada.');
        return;
      }
    } else if (!senha) {
      setErroGeral('Informe sua senha para entrar.');
      return;
    }

    setSubmetendo(true);
    let autenticado: { usuario: UsuarioAutenticado; modo: AuthMode } | null = null;
    try {
      if (isCadastro) {
        const resposta = await AuthService.cadastrar({
          nome: nome.trim(),
          email: emailLimpo,
          senha
        });
        authContext?.setUsuario(resposta.usuario);
        toast.success(`Bem-vindo(a), ${resposta.usuario.nome.split(' ')[0]}! Enviamos um link de confirmação para ${emailLimpo}.`);
        autenticado = { usuario: resposta.usuario, modo: 'cadastro' };
      } else {
        const resposta = await AuthService.login({
          email: emailLimpo,
          senha
        });
        authContext?.setUsuario(resposta.usuario);
        toast.success(`Bem-vindo(a) de volta, ${resposta.usuario.nome.split(' ')[0]}!`);
        autenticado = { usuario: resposta.usuario, modo: 'login' };
      }
    } catch (err) {
      const mensagem =
        err instanceof Error
          ? err.message
          : 'Não foi possível concluir a autenticação. Tente novamente.';
      setErroGeral(mensagem);
    } finally {
      setSubmetendo(false);
    }
    if (autenticado) {
      onAutenticado?.(autenticado.usuario, autenticado.modo);
    }
  };

  return (
    <section className={`${styles.formCard} ${className}`.trim()} aria-labelledby="auth-form-title">
      {showBrandHeader && (
        <div className={styles.formBrand}>
          <img className={styles.brandLogo} src={logoMark} alt="Trilhara" />
          <span className={styles.brandName}>Trilhara</span>
        </div>
      )}

      {/* Seletor de Abas (Login / Cadastro) */}
      {!isRecuperar && (
        <div className={styles.modeTabs} role="tablist" aria-label="Modo de autenticação">
          <button
            type="button"
            role="tab"
            aria-selected={!isCadastro}
            className={`${styles.modeTab} ${!isCadastro ? styles.modeTabActive : ''}`}
            onClick={() => trocarModo('login')}
          >
            Entrar na conta
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={isCadastro}
            className={`${styles.modeTab} ${isCadastro ? styles.modeTabActive : ''}`}
            onClick={() => trocarModo('cadastro')}
          >
            Criar conta gratuita
          </button>
        </div>
      )}

      <div className={styles.formHeader}>
        <h2 id="auth-form-title" className={styles.formTitle}>
          {isRecuperar
            ? 'Recuperação de Senha'
            : isCadastro
              ? 'Crie sua conta no Trilhara'
              : 'Bem-vindo(a) de volta'}
        </h2>
        <p className={styles.formSubtitle}>
          {isRecuperar
            ? 'Informe seu e-mail cadastrado para receber um link de redefinição de senha.'
            : isCadastro
              ? 'Preencha seus dados abaixo para iniciar sua jornada de estudos e tarefas.'
              : 'Informe seu e-mail e senha cadastrados para acessar seu painel.'}
        </p>
      </div>

      {erroGeral && (
        <div id="auth-erro" className={styles.errorBanner} role="alert">
          <AlertCircle size={18} className={styles.errorIcon} aria-hidden="true" />
          <span>{erroGeral}</span>
        </div>
      )}

      {confirmandoEmail ? (
        <div className={styles.form} style={{ textAlign: 'center', padding: '32px 0' }}>
          <p style={{ fontSize: 16, color: 'var(--text-mid)' }}>
            Confirmando seu endereço de e-mail, aguarde...
          </p>
        </div>
      ) : emailConfirmadoSucesso ? (
        <div className={styles.form} style={{ textAlign: 'center', padding: '16px 0' }}>
          <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 16 }}>
            <CheckCircle2 size={46} style={{ color: 'var(--success, #10b981)' }} />
          </div>
          <h3 style={{ fontSize: 18, color: 'var(--text)', marginBottom: 8 }}>E-mail confirmado com sucesso!</h3>
          <p style={{ fontSize: 15, lineHeight: 1.6, color: 'var(--text-mid)', marginBottom: 24 }}>
            Sua conta está totalmente ativada. Você já pode desfrutar de todos os recursos do Trilhara.
          </p>
          <Button
            type="button"
            variant="primary"
            size="lg"
            fullWidth
            onClick={() => trocarModo('login')}
          >
            Acessar minha conta
          </Button>
        </div>
      ) : isRecuperar && sucessoRecuperacao ? (
        <div className={styles.form} style={{ textAlign: 'center', padding: '16px 0' }}>
          <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 16 }}>
            <CheckCircle2 size={42} style={{ color: 'var(--success, #10b981)' }} />
          </div>
          <p style={{ fontSize: 15, lineHeight: 1.6, color: 'var(--text-mid)', marginBottom: 24 }}>
            Se o e-mail informado estiver cadastrado, você receberá um link com instruções válidas por 15 minutos para redefinir sua senha. Verifique também sua caixa de spam.
          </p>
          <Button
            type="button"
            variant="primary"
            size="lg"
            fullWidth
            onClick={() => trocarModo('login')}
          >
            Voltar para o login
          </Button>
        </div>
      ) : (
        <form key={mode} className={styles.form} onSubmit={handleSubmit} noValidate>
          {isCadastro && (
            <Input
              id="auth-nome"
              label="Nome completo"
              type="text"
              autoComplete="name"
              placeholder="Ex.: Ana Beatriz Silva"
              value={nome}
              onChange={atualizarCampo('nome')}
              disabled={submetendo}
              maxLength={120}
              autoFocus={isCadastro}
              leftIcon={<User size={16} />}
              aria-describedby={erroGeral ? 'auth-erro' : undefined}
              required
            />
          )}

          <Input
            id="auth-email"
            label="E-mail"
            type="email"
            autoComplete="email"
            inputMode="email"
            autoCapitalize="none"
            spellCheck={false}
            placeholder="voce@exemplo.com.br"
            value={email}
            onChange={atualizarCampo('email')}
            disabled={submetendo}
            maxLength={254}
            autoFocus={!isCadastro}
            leftIcon={<Mail size={16} />}
            aria-describedby={erroGeral ? 'auth-erro' : undefined}
            required
          />

          {!isRecuperar && (
            <>
              <Input
                id="auth-senha"
                label="Senha"
                type={mostrarSenha ? 'text' : 'password'}
                autoComplete={isCadastro ? 'new-password' : 'current-password'}
                placeholder={isCadastro ? 'Crie uma senha forte (ex: Estudo@2026!)' : 'Digite sua senha'}
                value={senha}
                onChange={atualizarCampo('senha')}
                disabled={submetendo}
                maxLength={128}
                leftIcon={<Lock size={16} />}
                aria-describedby={erroGeral ? 'auth-erro' : undefined}
                rightIcon={
                  <button
                    type="button"
                    className={styles.visibilityBtn}
                    onClick={() => setMostrarSenha((v) => !v)}
                    aria-label={mostrarSenha ? 'Ocultar senha' : 'Mostrar senha'}
                    aria-pressed={mostrarSenha}
                  >
                    {mostrarSenha ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                }
                required
              />

              {!isCadastro && (
                <div className={styles.forgotPasswordRow}>
                  <button
                    type="button"
                    className={styles.switchLink}
                    onClick={() => {
                      if (onEsqueciSenha) {
                        onEsqueciSenha();
                      } else {
                        trocarModo('recuperar');
                      }
                    }}
                  >
                    Esqueci minha senha
                  </button>
                </div>
              )}

              {isCadastro && (
                <>
                  <Input
                    id="auth-confirmar-senha"
                    label="Confirmar senha"
                    type={mostrarConfirmarSenha ? 'text' : 'password'}
                    autoComplete="new-password"
                    placeholder="Repita a senha criada"
                    value={confirmarSenha}
                    onChange={atualizarCampo('confirmarSenha')}
                    disabled={submetendo}
                    maxLength={128}
                    leftIcon={<Lock size={16} />}
                    aria-invalid={confirmarSenha.length > 0 && confirmarSenha !== senha}
                    aria-describedby={erroGeral ? 'auth-erro' : undefined}
                    rightIcon={
                      <button
                        type="button"
                        className={styles.visibilityBtn}
                        onClick={() => setMostrarConfirmarSenha((v) => !v)}
                        aria-label={
                          mostrarConfirmarSenha
                            ? 'Ocultar confirmação de senha'
                            : 'Mostrar confirmação de senha'
                        }
                        aria-pressed={mostrarConfirmarSenha}
                      >
                        {mostrarConfirmarSenha ? <EyeOff size={16} /> : <Eye size={16} />}
                      </button>
                    }
                    required
                  />

                  {/* Checklist interativo de política de senha forte */}
                  <div className={styles.passwordChecklist} aria-label="Requisitos de segurança da senha">
                    <span className={styles.checklistTitle}>Requisitos de segurança da senha:</span>
                    <ul className={styles.checklistGrid}>
                      {requisitosSenha.map((req) => (
                        <li
                          key={req.id}
                          className={`${styles.checklistItem} ${
                            req.cumprido ? styles.checklistItemDone : ''
                          }`}
                        >
                          {req.cumprido ? (
                            <CheckCircle2 size={13} className={styles.checkIconDone} aria-hidden="true" />
                          ) : (
                            <Circle size={13} className={styles.checkIconPending} aria-hidden="true" />
                          )}
                          <span>{req.label}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                </>
              )}
            </>
          )}

          <Button
            type="submit"
            variant="primary"
            size="lg"
            fullWidth
            isLoading={submetendo}
            rightIcon={<ArrowRight size={17} />}
            className={styles.submitBtn}
          >
            {isRecuperar
              ? 'Enviar link de recuperação'
              : isCadastro
                ? 'Criar minha conta e acessar painel'
                : 'Entrar no meu painel'}
          </Button>
        </form>
      )}

      <div className={styles.formFooter}>
        {isRecuperar ? (
          <p>
            Lembrou da sua senha?{' '}
            <button
              type="button"
              className={styles.switchLink}
              onClick={() => trocarModo('login')}
            >
              Fazer login agora
            </button>
          </p>
        ) : isCadastro ? (
          <p>
            Já possui uma conta no Trilhara?{' '}
            <button
              type="button"
              className={styles.switchLink}
              onClick={() => trocarModo('login')}
            >
              Fazer login agora
            </button>
          </p>
        ) : (
          <p>
            Ainda não tem conta?{' '}
            <button
              type="button"
              className={styles.switchLink}
              onClick={() => trocarModo('cadastro')}
            >
              Cadastre-se gratuitamente
            </button>
          </p>
        )}
      </div>
    </section>
  );
};
