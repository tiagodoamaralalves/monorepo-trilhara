import React, { useEffect, useState } from 'react';
import { Download, ListChecks } from 'lucide-react';
import { Button } from '@shared/design-system';
import { EditalResumo } from '../../types/trilhara';
import { DisciplinasTopicosModal } from '../DisciplinasTopicosModal/DisciplinasTopicosModal';

export interface VerTrilhaModalProps {
  edital: EditalResumo | null;
  importando: boolean;
  onClose: () => void;
  onUsar: (edital: EditalResumo) => void;
}

export const VerTrilhaModal: React.FC<VerTrilhaModalProps> = ({
  edital,
  importando,
  onClose,
  onUsar
}) => {
  // Mantém o último edital durante a animação de fechamento
  const [ultimo, setUltimo] = useState<EditalResumo | null>(edital);
  useEffect(() => {
    if (edital) setUltimo(edital);
  }, [edital]);

  const exibido = edital ?? ultimo;

  const subtitle = exibido
    ? [exibido.area ? `${exibido.area} - ${exibido.cargo}` : exibido.cargo, exibido.banca]
        .filter(Boolean)
        .join(' - ')
    : undefined;

  return (
    <DisciplinasTopicosModal
      isOpen={Boolean(edital)}
      onClose={onClose}
      title={exibido?.nome ?? 'Trilha'}
      subtitle={subtitle}
      icon={<ListChecks size={22} />}
      disciplinas={exibido?.disciplinas ?? []}
      editable={false}
      mostrarPeso
      extraFooterAction={
        <Button
          type="button"
          variant="primary"
          leftIcon={<Download size={15} />}
          isLoading={importando}
          disabled={!exibido}
          onClick={() => exibido && onUsar(exibido)}
        >
          Usar essa trilha
        </Button>
      }
    />
  );
};
