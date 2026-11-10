import React from 'react';
import { Modal } from '../design-system';
import { AuthForm } from './AuthForm';
import { AuthMode, UsuarioAutenticado } from './types';

export interface AuthModalProps {
  isOpen: boolean;
  onClose: () => void;
  initialMode?: AuthMode;
  onAutenticado?: (usuario: UsuarioAutenticado, modo: AuthMode) => void;
}

export const AuthModal: React.FC<AuthModalProps> = ({
  isOpen,
  onClose,
  initialMode = 'login',
  onAutenticado
}) => {
  const handleAutenticado = (usuario: UsuarioAutenticado, modo: AuthMode) => {
    onAutenticado?.(usuario, modo);
    onClose();
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Acesse sua conta"
      maxWidth="md"
    >
      {isOpen && (
        <AuthForm
          key={initialMode}
          initialMode={initialMode}
          onAutenticado={handleAutenticado}
          showBrandHeader={false}
        />
      )}
    </Modal>
  );
};
