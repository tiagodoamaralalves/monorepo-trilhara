import React, { useEffect, useState } from 'react';
import { BookOpen } from 'lucide-react';
import { Modal, Button, Select, Checkbox } from '@shared/design-system';
import { Caderno } from '../../types/nota';

export interface TrilhaItem {
  id: string;
  nome: string;
  cor?: string;
}

/** Preferência local que dispensa a sugestão automática (REQUISITOS §10.1). */
export const CHAVE_OCULTAR_MODAL_IMPORTACAO = (usuarioId: string) =>
  `trilhara:u:${usuarioId}:notas_ocultar_modal_importacao`;

export interface ImportarTrilhaModalProps {
  isOpen: boolean;
  onClose: () => void;
  trilhas: TrilhaItem[];
  cadernos: Caderno[];
  usuarioId: string;
  onImportar: (trilhaId: string, reimportar: boolean) => Promise<void>;
}

/**
 * Importação (e reimportação confirmada) de trilha como caderno (D-29).
 *
 * Quando a trilha escolhida já tem caderno ativo vinculado, o import normal responde
 * `409 TRAIL_ALREADY_IMPORTED`; aqui o usuário confirma explicitamente e a requisição segue com
 * `?reimportar=true`, mostrando que o caderno atual será **mantido e desvinculado**.
 */
export const ImportarTrilhaModal: React.FC<ImportarTrilhaModalProps> = ({
  isOpen,
  onClose,
  trilhas,
  cadernos,
  usuarioId,
  onImportar
}) => {
  const [trilhaSelecionadaId, setTrilhaSelecionadaId] = useState<string>(() => trilhas[0]?.id ?? '');
  const [naoMostrarNovamente, setNaoMostrarNovamente] = useState(false);
  const [importando, setImportando] = useState(false);
  const [trilhaConflitanteId, setTrilhaConflitanteId] = useState<string | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) return;
    setTrilhaSelecionadaId(trilhas[0]?.id ?? '');
    setTrilhaConflitanteId(null);
    setErro(null);
  }, [isOpen, trilhas]);

  const cadernoDaTrilha = (trilhaId: string) =>
    cadernos.find((c) => c.origemTrilhaId === trilhaId) ?? null;

  const opcoes = trilhas.map((trilha) => ({
    value: trilha.id,
    label: cadernoDaTrilha(trilha.id) ? `${trilha.nome} (já vinculada)` : trilha.nome,
    dotColor: trilha.cor
  }));

  const persistirPreferencia = (ocultar: boolean) => {
    if (!usuarioId) return;
    try {
      if (ocultar) {
        window.localStorage.setItem(CHAVE_OCULTAR_MODAL_IMPORTACAO(usuarioId), 'true');
      } else {
        window.localStorage.removeItem(CHAVE_OCULTAR_MODAL_IMPORTACAO(usuarioId));
      }
    } catch {
      // Preferência local é best-effort
    }
  };

  const executar = async (trilhaId: string, reimportar: boolean) => {
    try {
      setImportando(true);
      setErro(null);
      await onImportar(trilhaId, reimportar);
      onClose();
    } catch (falha) {
      setErro(
        falha instanceof Error ? falha.message : 'Não foi possível importar a trilha selecionada.'
      );
    } finally {
      setImportando(false);
    }
  };

  const handleImportar = async () => {
    if (!trilhaSelecionadaId) return;
    if (naoMostrarNovamente) {
      persistirPreferencia(true);
    }

    if (cadernoDaTrilha(trilhaSelecionadaId)) {
      setTrilhaConflitanteId(trilhaSelecionadaId);
      return;
    }

    await executar(trilhaSelecionadaId, false);
  };

  const confirmarReimportacao = async () => {
    if (!trilhaConflitanteId) return;
    const alvo = trilhaConflitanteId;
    setTrilhaConflitanteId(null);
    await executar(alvo, true);
  };

  return (
    <>
      <Modal
        isOpen={isOpen}
        onClose={onClose}
        title="Importar Trilha de Estudos"
        subtitle="Crie um novo caderno estruturado a partir de suas disciplinas e tópicos."
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
              loading={importando}
              disabled={!trilhaSelecionadaId}
            >
              Importar trilha
            </Button>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <p style={{ fontSize: '0.9rem', color: 'var(--text-muted)', margin: 0 }}>
            A importação cria uma seção para cada disciplina e uma página para cada tópico. O
            conteúdo que você escrever depois nunca é sobrescrito.
          </p>

          <div>
            <label
              htmlFor="importar-trilha-select"
              style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '6px' }}
            >
              Selecione a trilha a ser importada
            </label>
            <Select
              id="importar-trilha-select"
              options={opcoes}
              value={trilhaSelecionadaId || (opcoes[0]?.value ?? '')}
              onChange={(valor) => setTrilhaSelecionadaId(valor)}
            />
          </div>

          {erro && (
            <p role="alert" style={{ margin: 0, fontSize: '0.85rem', color: '#dc2626' }}>
              {erro}
            </p>
          )}

          <div>
            <Checkbox
              label="Não mostrar esta sugestão automaticamente ao abrir Notas"
              checked={naoMostrarNovamente}
              onChange={(evento) => {
                const marcado = evento.target.checked;
                setNaoMostrarNovamente(marcado);
                persistirPreferencia(marcado);
              }}
            />
          </div>
        </div>
      </Modal>

      <Modal
        isOpen={trilhaConflitanteId !== null}
        onClose={() => setTrilhaConflitanteId(null)}
        title="Importar novamente esta trilha?"
        icon={<BookOpen size={20} />}
        maxWidth="md"
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', width: '100%' }}>
            <Button variant="ghost" onClick={() => setTrilhaConflitanteId(null)} disabled={importando}>
              Cancelar
            </Button>
            <Button variant="primary" onClick={confirmarReimportacao} loading={importando}>
              Importar e desvincular o atual
            </Button>
          </div>
        }
      >
        <p style={{ margin: 0, fontSize: '0.9rem' }}>
          O caderno atual será mantido e desvinculado, e um novo caderno será criado com o nome da
          trilha. Nenhuma anotação é apagada.
        </p>
      </Modal>
    </>
  );
};
