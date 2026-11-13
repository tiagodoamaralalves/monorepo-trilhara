import React, { useState, useEffect } from 'react';
import { FormModal, Input } from '@shared/design-system';
import { Secao } from '../../types/nota';
import styles from './SecaoModal.module.css';

export interface SecaoModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSalvar: (dados: { titulo: string }) => Promise<void> | void;
  secaoParaEditar?: Secao | null;
}

/**
 * Criação/renomeação de seção.
 *
 * O contrato de seção não tem cor (a cor é atributo do caderno), então o formulário expõe apenas
 * o título — o `ColorPickerPopover` legado saiu junto com o modelo antigo.
 */
export const SecaoModal: React.FC<SecaoModalProps> = ({
  isOpen,
  onClose,
  onSalvar,
  secaoParaEditar
}) => {
  const [titulo, setTitulo] = useState('');
  const [erroTitulo, setErroTitulo] = useState('');
  const [salvando, setSalvando] = useState(false);

  useEffect(() => {
    if (!isOpen) return;
    setTitulo(secaoParaEditar?.titulo ?? '');
    setErroTitulo('');
  }, [isOpen, secaoParaEditar]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!titulo.trim()) {
      setErroTitulo('O título da seção é obrigatório.');
      return;
    }

    try {
      setSalvando(true);
      await onSalvar({ titulo: titulo.trim() });
      onClose();
    } finally {
      setSalvando(false);
    }
  };

  return (
    <FormModal
      isOpen={isOpen}
      onClose={onClose}
      title={secaoParaEditar ? 'Renomear seção' : 'Nova seção'}
      onSubmit={handleSubmit}
      loading={salvando}
      submitText={secaoParaEditar ? 'Salvar' : 'Criar seção'}
    >
      <div className={styles.formBody}>
        <Input
          label="Título da seção"
          value={titulo}
          onChange={(e) => {
            setTitulo(e.target.value);
            setErroTitulo('');
          }}
          error={erroTitulo || undefined}
          maxLength={120}
        />
      </div>
    </FormModal>
  );
};
