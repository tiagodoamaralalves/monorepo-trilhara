import React, { useState, useEffect, useMemo } from 'react';
import {
  FormModal,
  Input,
  ColorPickerPopover,
  obterCorDisponivel,
  obterCoresEmUso
} from '@shared/design-system';
import { ListaTarefas } from '../../types/tarefa';
import styles from './ListaModal.module.css';

export interface ListaModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSalvar: (dados: { titulo: string; cor: string; icone?: string }) => Promise<boolean | void> | boolean | void;
  listaParaEditar?: ListaTarefas | null;
  listasExistentes?: ListaTarefas[];
}

export const ListaModal: React.FC<ListaModalProps> = ({
  isOpen,
  onClose,
  onSalvar,
  listaParaEditar,
  listasExistentes = []
}) => {
  const [titulo, setTitulo] = useState('');
  const [cor, setCor] = useState('');
  const [erroTitulo, setErroTitulo] = useState('');
  const [salvando, setSalvando] = useState(false);
  const [popoverAberto, setPopoverAberto] = useState(false);

  // 1. Mapeia cores em uso pelas outras listas (para os tooltips informativos do picker)
  const coresEmUso = useMemo(
    () => obterCoresEmUso(listasExistentes, listaParaEditar?.id),
    [listasExistentes, listaParaEditar]
  );

  // 2. Inicialização / Reset do Rascunho
  useEffect(() => {
    if (!isOpen) {
      setPopoverAberto(false);
      return;
    }
    if (listaParaEditar) {
      setTitulo(listaParaEditar.titulo);
      setCor(listaParaEditar.cor || obterCorDisponivel(listasExistentes, listaParaEditar.id));
    } else {
      setTitulo('');
      // Atribuição automática inteligente de cor ainda não utilizada
      setCor(obterCorDisponivel(listasExistentes));
    }
    setErroTitulo('');
    setPopoverAberto(false);
  }, [listaParaEditar, isOpen, listasExistentes]);

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!titulo.trim()) {
      setErroTitulo('O nome da lista é obrigatório.');
      return;
    }

    try {
      setSalvando(true);
      await onSalvar({ titulo: titulo.trim(), cor });
      onClose();
    } finally {
      setSalvando(false);
    }
  };

  // 3. Botão circular da cor que abre o popover com grade 9x3 das 27 cores
  const headerColorBadge = (
    <ColorPickerPopover
      color={cor}
      onChangeColor={setCor}
      usedColors={coresEmUso}
      title="Cor da Lista"
      eyebrow="Aparência"
      description="Escolha uma cor para identificar esta lista com facilidade."
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
      title={listaParaEditar ? 'Editar Lista' : 'Nova Lista'}
      icon={headerColorBadge}
      iconBadgeStyle={{
        borderRadius: '50%',
        backgroundColor: 'transparent',
        borderColor: 'transparent'
      }}
      submitText={listaParaEditar ? 'Salvar Alterações' : 'Criar Lista'}
      loading={salvando}
      overflowVisible={true}
    >
      <div className={`${styles.formBody} ${popoverAberto ? styles.formBodyOpen : ''}`}>
        <Input
          label="Nome da Lista *"
          placeholder="Ex: Pessoal, Trabalho, Concursos, Metas..."
          value={titulo}
          onChange={(e) => {
            setTitulo(e.target.value);
            if (erroTitulo) setErroTitulo('');
          }}
          error={erroTitulo}
          required
          disabled={salvando}
        />
      </div>
    </FormModal>
  );
};
