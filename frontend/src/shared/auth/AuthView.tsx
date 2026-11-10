import React, { useState, useEffect } from 'react';
import { AuthForm } from './AuthForm';
import { AuthMode, UsuarioAutenticado } from './types';
import styles from './AuthView.module.css';

export interface AuthViewProps {
  mode?: AuthMode;
  onAlternarModo?: (novoModo: AuthMode) => void;
  onAutenticado?: (usuario: UsuarioAutenticado, modo: AuthMode) => void;
}

export const AuthView: React.FC<AuthViewProps> = ({
  mode = 'login',
  onAlternarModo,
  onAutenticado
}) => {
  const [modoAtual, setModoAtual] = useState<AuthMode>(mode);

  useEffect(() => {
    setModoAtual(mode);
  }, [mode]);

  const alternar = (novoModo: AuthMode) => {
    setModoAtual(novoModo);
    onAlternarModo?.(novoModo);
  };

  return (
    <div className={styles.page}>
      <div className={styles.ambientGlow} aria-hidden="true" />
      <main className={styles.main}>
        <div className={styles.gridSingle}>
          <AuthForm
            key={modoAtual}
            initialMode={modoAtual}
            onAlternarModo={alternar}
            onEsqueciSenha={() => alternar('recuperar')}
            onAutenticado={onAutenticado}
            showBrandHeader={true}
          />
        </div>
      </main>
    </div>
  );
};
