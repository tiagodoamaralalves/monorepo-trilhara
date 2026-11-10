import React, { useState, useEffect } from 'react';
import { FileText } from 'lucide-react';
import { FormModal, Input } from '@shared/design-system';
import { Nota } from '../../types/nota';

export interface NotaModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSalvar: (titulo: string) => Promise<void> | void;
  notaParaEditar?: Nota | null;
}

export const NotaModal: React.FC<NotaModalProps> = ({
  isOpen,
  onClose,
  onSalvar,
  notaParaEditar
}) => {
  const [titulo, setTitulo] = useState('');
  const [erroTitulo, setErroTitulo] = useState('');
  const [salvando, setSalvando] = useState(false);

  useEffect(() => {
    if (!isOpen) return;

    if (notaParaEditar) {
      setTitulo(notaParaEditar.titulo);
    } else {
      setTitulo('');
    }
    setErroTitulo('');
  }, [notaParaEditar, isOpen]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!titulo.trim()) {
      setErroTitulo('O título da nota é obrigatório.');
      return;
    }

    try {
      setSalvando(true);
      await onSalvar(titulo.trim());
      onClose();
    } finally {
      setSalvando(false);
    }
  };

  return (
    <FormModal
      isOpen={isOpen}
      onClose={onClose}
      onSubmit={handleSubmit}
      title={notaParaEditar ? 'Renomear Nota' : 'Nova Nota'}
      icon={<FileText size={20} />}
      submitText={notaParaEditar ? 'Salvar Alterações' : 'Criar Nota'}
      isLoading={salvando}
    >
      <Input
        label="Título da Nota"
        placeholder="Ex: Introdução ao Controle de Constitucionalidade..."
        value={titulo}
        onChange={(e) => {
          setTitulo(e.target.value);
          if (erroTitulo) setErroTitulo('');
        }}
        error={erroTitulo}
        required
        autoFocus
      />
    </FormModal>
  );
};
