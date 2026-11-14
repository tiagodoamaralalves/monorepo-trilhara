import React, { useContext } from 'react';
import { UserRound } from 'lucide-react';
import { AuthContext } from '../../auth/AuthContext';
import styles from './ContaMenuButton.module.css';

export interface ContaMenuButtonProps {
  /** `sidebar` desenha as iniciais no rodapé; `header` desenha só o ícone. */
  variant?: 'sidebar' | 'header';
  /** Nome do usuário (iniciais no modo `sidebar`). */
  nome?: string;
  /** Rótulo acessível; default `Conta`. */
  label?: string;
  collapsed?: boolean;
  /** Exibido à direita do rótulo apenas no modo `sidebar` expandido. */
  textoVisivel?: boolean;
  className?: string;
  /**
 * Fallback opcional quando não há `AuthProvider` acima. Em produção o botão sempre lê o
 * contexto; o fallback existe para telas públicas montadas isoladamente em teste.
 */
  onAbrirConta?: () => void;
}

/**
 * Entrada de conta única do produto.
 *
 * <p>Centraliza o acesso ao gerenciamento de conta em <strong>um</strong> componente, consumido
 * pela `AppSidebar` (Estudos, Tarefas, Notas) e pelo header próprio do Pomodoro. Não recebe a
 * ação por prop de cada app: lê `AuthContext.abrirContaModal` diretamente, evitando a
 * propagação manual que deixava o Pomodoro sem entrada.</p>
 */
export const ContaMenuButton: React.FC<ContaMenuButtonProps> = ({
  variant = 'sidebar',
  nome,
  label = 'Conta',
  collapsed = false,
  textoVisivel = true,
  className = '',
  onAbrirConta
}) => {
  const auth = useContext(AuthContext);
  const abrir = onAbrirConta ?? auth?.abrirContaModal;

  const iniciais = (nome ?? '')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((parte) => parte.charAt(0).toUpperCase())
    .join('');

  const classe = [
    styles.botao,
    variant === 'header' ? styles.header : styles.sidebar,
    collapsed && variant === 'sidebar' ? styles.collapsed : '',
    className
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <button
      type="button"
      className={classe}
      onClick={() => abrir?.()}
      disabled={!abrir}
      title="Gerenciar minha conta"
      aria-label={label}
      data-variant={variant}
    >
      {variant === 'sidebar' ? (
        <span className={styles.iniciais} aria-hidden="true">
          {iniciais || nome?.trim().charAt(0).toUpperCase() || '?'}
        </span>
      ) : (
        <UserRound size={16} aria-hidden="true" />
      )}
      {variant === 'sidebar' && !collapsed && textoVisivel && (
        <span className={styles.rotulo}>{label}</span>
      )}
    </button>
  );
};
