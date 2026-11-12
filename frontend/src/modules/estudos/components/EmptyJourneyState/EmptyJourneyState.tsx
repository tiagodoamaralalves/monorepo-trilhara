import React from 'react';
import { Plus, Route } from 'lucide-react';
import { Button, EmptyState } from '@shared/design-system';

interface EmptyJourneyStateProps {
  hasTrilha: boolean;
  onPrimaryAction: () => void;
  onViewCatalog?: () => void;
}

export const EmptyJourneyState: React.FC<EmptyJourneyStateProps> = ({
  hasTrilha,
  onPrimaryAction,
  onViewCatalog
}) => (
  <EmptyState
    icone={<Route size={28} />}
    titulo={hasTrilha ? 'Sua trilha ainda não tem disciplinas' : 'Vamos iniciar sua primeira jornada?'}
    descricao={hasTrilha
      ? 'Cadastre as disciplinas e os assuntos do edital para começar a organizar seus estudos.'
      : 'Crie uma trilha de estudos personalizada ou escolha uma trilha pronta no catálogo abaixo para dar o pontapé inicial com matérias e assuntos já estruturados.'}
    acaoPrimaria={
      <>
        <Button variant="secondary" leftIcon={<Plus size={16} />} onClick={onPrimaryAction}>
          {hasTrilha ? 'Cadastrar Disciplinas' : 'Criar Minha Trilha'}
        </Button>
        {!hasTrilha && onViewCatalog && (
          <Button variant="ghost" onClick={onViewCatalog}>
            Catálogo de Trilhas
          </Button>
        )}
      </>
    }
  />
);
