import React, { useState, useEffect } from 'react';
import {
  FormModal,
  Input,
  ColorPickerPopover
} from '@shared/design-system';
import { Secao } from '../../types/nota';
import styles from './SecaoModal.module.css';

export interface SecaoModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSalvar: (dados: { titulo: string; cor?: string }) => Promise<void> | void;
  secaoParaEditar?: Secao | null;
  corSugerida?: string;
}

export const SecaoModal: React.FC<SecaoModalProps> = ({
  isOpen,
  onClose,
  onSalvar,
  secaoParaEditar,
  corSugerida = '#0ea5e9'
}) => {
  const [titulo, setTitulo] = useState('');
  const [cor, setCor] = useState(corSugerida);
  const [erroTitulo, setErroTitulo] = useState('');
  const [salvando, setSalvando] = useState(false);
  const [popoverAberto, setPopoverAberto] = useState(false);

  useEffect(() => {
    if (!isOpen) {
      setPopoverAberto(false);
      return;
    }

    if (secaoParaEditar) {
      setTitulo(secaoParaEditar.titulo);
      setCor(secaoParaEditar.cor || corSugerida);
    } else {
      setTitulo('');
      setCor(corSugerida);
    }
    setErroTitulo('');
    setPopoverAberto(false);
  }, [secaoParaEditar, isOpen, corSugerida]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!titulo.trim()) {
      setErroTitulo('O título da seção é obrigatório.');
      return;
    }

    try {
      setSalvando(true);
      await onSalvar({
        titulo: titulo.trim(),
        cor
      });
      onClose();
    } finally {
      setSalvando(false);
    }
  };

  const headerColorBadge = (
    <ColorPickerPopover
      color={cor}
      onChangeColor={setCor}
      title="Cor da Seção"
      eyebrow="Divisória"
      description="Escolha uma cor para esta seção divisória do caderno."
      buttonSize={32}
      swatchSize={20}
      onOpenChange={setPopoverAberto}
    />
  );

  return (
    <FormModal
      isOpen={isOpen}
      onClose={onClose}
      onSubmit={handleSubmit}
      title={secaoParaEditar ? 'Editar Seção' : 'Nova Seção'}
      icon={headerColorBadge}
      iconBadgeStyle={{
        borderRadius: '50%',
        backgroundColor: 'transparent',
        borderColor: 'transparent'
      }}
      submitText={secaoParaEditar ? 'Salvar Alterações' : 'Criar Seção'}
      isLoading={salvando}
      overflowVisible={true}
    >
      <div className={`${styles.formBody} ${popoverAberto ? styles.formBodyOpen : ''}`}>
        <Input
          label="Título da Seção"
          placeholder="Ex: Introdução, Conceitos, Fichamentos..."
          value={titulo}
          onChange={(e) => {
            setTitulo(e.target.value);
            if (erroTitulo) setErroTitulo('');
          }}
          error={erroTitulo}
          required
          autoFocus
        />
      </div>
    </FormModal>
  );
};
