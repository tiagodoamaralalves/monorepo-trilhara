import React, { useState, useEffect, useMemo } from 'react';
import {
  FormModal,
  Input,
  ColorPickerPopover,
  obterCorDisponivel,
  obterCoresEmUso
} from '@shared/design-system';
import { Caderno } from '../../types/nota';
import styles from './CadernoModal.module.css';

export interface CadernoModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSalvar: (dados: {
    titulo: string;
    cor?: string;
    icone?: string;
  }) => Promise<void> | void;
  cadernoParaEditar?: Caderno | null;
  cadernos?: Caderno[];
}

export const CadernoModal: React.FC<CadernoModalProps> = ({
  isOpen,
  onClose,
  onSalvar,
  cadernoParaEditar,
  cadernos = []
}) => {
  const [titulo, setTitulo] = useState('');
  const [cor, setCor] = useState('');
  const [erroTitulo, setErroTitulo] = useState('');
  const [salvando, setSalvando] = useState(false);
  const [popoverAberto, setPopoverAberto] = useState(false);

  // Mapeia as cores em uso pelos outros cadernos para exibir tooltips informativos
  const coresEmUso = useMemo(
    () => obterCoresEmUso(cadernos, cadernoParaEditar?.id),
    [cadernos, cadernoParaEditar]
  );

  useEffect(() => {
    if (!isOpen) {
      setPopoverAberto(false);
      return;
    }

    if (cadernoParaEditar) {
      setTitulo(cadernoParaEditar.titulo);
      setCor(cadernoParaEditar.cor || obterCorDisponivel(cadernos, cadernoParaEditar.id));
    } else {
      setTitulo('');
      setCor(obterCorDisponivel(cadernos));
    }
    setErroTitulo('');
    setPopoverAberto(false);
  }, [cadernoParaEditar, isOpen, cadernos]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!titulo.trim()) {
      setErroTitulo('O título do caderno é obrigatório.');
      return;
    }

    try {
      setSalvando(true);
      await onSalvar({
        titulo: titulo.trim(),
        cor,
        icone: 'book-open'
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
      usedColors={coresEmUso}
      title="Cor do Caderno"
      eyebrow="Aparência"
      description="Escolha uma cor para identificar este caderno com facilidade."
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
      title={cadernoParaEditar ? 'Editar Caderno' : 'Novo Caderno'}
      icon={headerColorBadge}
      iconBadgeStyle={{
        borderRadius: '50%',
        backgroundColor: 'transparent',
        borderColor: 'transparent'
      }}
      submitText={cadernoParaEditar ? 'Salvar Alterações' : 'Criar Caderno'}
      isLoading={salvando}
      overflowVisible={true}
    >
      <div className={`${styles.formBody} ${popoverAberto ? styles.formBodyOpen : ''}`}>
        <Input
          label="Título do Caderno"
          placeholder="Ex: Caderno Geral, Projetos, Rascunhos..."
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
