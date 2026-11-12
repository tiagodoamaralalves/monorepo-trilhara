import React, { useState } from 'react';
import { BookOpen } from 'lucide-react';
import { Modal, Button, Select, Checkbox } from '@shared/design-system';
import { Caderno } from '../../types/nota';

export interface TrilhaItem {
  id: string;
  nome: string;
  cor?: string;
}

export interface ImportarTrilhaModalProps {
  isOpen: boolean;
  onClose: () => void;
  trilhas: TrilhaItem[];
  cadernos: Caderno[];
  usuarioId: string;
  onImportar: (trilhaId: string) => Promise<void>;
}

export const ImportarTrilhaModal: React.FC<ImportarTrilhaModalProps> = ({
  isOpen,
  onClose,
  trilhas,
  cadernos,
  usuarioId,
  onImportar
}) => {
  const [trilhaSelecionadaId, setTrilhaSelecionadaId] = useState<string>(() => {
    return trilhas[0]?.id || '';
  });
  const [naoMostrarNovamente, setNaoMostrarNovamente] = useState(false);
  const [importando, setImportando] = useState(false);

  const opcoes = trilhas.map((t) => {
    const jaImportada = cadernos.some((c) => c.origemTrilhaId === t.id && !c.deletedAt);
    return {
      value: t.id,
      label: jaImportada ? `${t.nome} (Já vinculada - criará nova versão)` : t.nome,
      dotColor: t.cor
    };
  });

  const handleImportar = async () => {
    if (!trilhaSelecionadaId) return;

    if (naoMostrarNovamente && usuarioId) {
      try {
        window.localStorage.setItem(`trilhara:u:${usuarioId}:notas_hide_auto_import_modal`, 'true');
      } catch {
        // Ignora erro de storage
      }
    }

    try {
      setImportando(true);
      await onImportar(trilhaSelecionadaId);
      onClose();
    } finally {
      setImportando(false);
    }
  };

  const handleNaoMostrarChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const checked = e.target.checked;
    setNaoMostrarNovamente(checked);
    if (checked && usuarioId) {
      try {
        window.localStorage.setItem(`trilhara:u:${usuarioId}:notas_hide_auto_import_modal`, 'true');
      } catch {
        // Ignora
      }
    } else if (usuarioId) {
      try {
        window.localStorage.removeItem(`trilhara:u:${usuarioId}:notas_hide_auto_import_modal`);
      } catch {
        // Ignora
      }
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Importar Trilha de Estudos"
      subtitle="Crie um novo caderno estruturado a partir de suas disciplinas e tópicos cadastrados."
      icon={<BookOpen size={20} />}
      maxWidth="md"
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', width: '100%' }}>
          <Button variant="ghost" onClick={onClose} disabled={importando}>
            Cancelar
          </Button>
          <Button
            variant="primary"
            onClick={handleImportar}
            isLoading={importando}
            disabled={!trilhaSelecionadaId}
          >
            Importar Trilha
          </Button>
        </div>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <p style={{ fontSize: '0.9rem', color: 'var(--text-muted)', margin: 0 }}>
          Ao importar uma trilha, o sistema cria automaticamente um novo caderno contendo uma seção para cada disciplina e uma página para cada tópico.
        </p>

        <div>
          <label htmlFor="importar-trilha-select" style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '6px' }}>
            Selecione a trilha a ser importada
          </label>
          <Select
            id="importar-trilha-select"
            options={opcoes}
            value={trilhaSelecionadaId || (opcoes[0]?.value ?? '')}
            onChange={(val) => setTrilhaSelecionadaId(val)}
          />
        </div>

        <div style={{ marginTop: '8px' }}>
          <Checkbox
            label="Não mostrar esta sugestão automaticamente ao abrir Notas"
            checked={naoMostrarNovamente}
            onChange={handleNaoMostrarChange}
          />
        </div>
      </div>
    </Modal>
  );
};
