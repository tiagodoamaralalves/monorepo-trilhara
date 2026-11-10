import React from 'react';
import { Plus } from 'lucide-react';
import { Button } from '@shared/design-system';
import styles from './TarefaContextPod.module.css';

export interface MobileTarefaControlsProps {
  onAbrirNovaTarefa: () => void;
}

export const MobileTarefaControls: React.FC<MobileTarefaControlsProps> = ({
  onAbrirNovaTarefa
}) => {
  return (
    <div className={styles.mobileControlsWrap}>
      <Button
        variant="primary"
        size="sm"
        iconOnly
        className={styles.mobileAddBtn}
        onClick={onAbrirNovaTarefa}
        title="Nova tarefa"
        aria-label="Nova tarefa"
      >
        <Plus size={16} />
      </Button>
    </div>
  );
};
