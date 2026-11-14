import React from 'react';
import { Repeat } from 'lucide-react';
import { Button, Modal } from '../design-system';
import type { EscopoRecorrencia } from '../contracts/domainContracts';
import styles from './ExcluirRecorrenteModal.module.css';

export interface ExcluirRecorrenteModalProps {
  isOpen: boolean;
  escopo: EscopoRecorrencia;
  onChangeEscopo: (escopo: EscopoRecorrencia) => void;
  onCancel: () => void;
  onConfirm: () => void;
}

export const ExcluirRecorrenteModal: React.FC<ExcluirRecorrenteModalProps> = ({
  isOpen,
  escopo,
  onChangeEscopo,
  onCancel,
  onConfirm
}) => {
  return (
    <Modal
      isOpen={isOpen}
      onClose={onCancel}
      title="Excluir Tarefa Recorrente"
      icon={<Repeat size={20} />}
      maxWidth="md"
      footer={
        <div className={styles.footer}>
          <Button type="button" variant="ghost" onClick={onCancel}>
            Cancelar
          </Button>
          <Button type="button" variant="danger" onClick={onConfirm}>
            Confirmar Exclusão
          </Button>
        </div>
      }
    >
      <div className={styles.body}>
        <p className={styles.description}>
          Esta tarefa faz parte de uma série recorrente. Como deseja proceder com a exclusão?
        </p>

        <div className={styles.options}>
          <label className={styles.option}>
            <input
              type="radio"
              name="escopoExclusao"
              value="ESTA"
              aria-label="Apenas esta ocorrência"
              checked={escopo === 'ESTA'}
              onChange={() => onChangeEscopo('ESTA')}
            />
            <div className={styles.optionText}>
              <strong>Apenas esta ocorrência</strong>
              <span className={styles.optionHint}>
                Move somente a tarefa selecionada para a lixeira. A série continua ativa.
              </span>
            </div>
          </label>

          <label className={styles.option}>
            <input
              type="radio"
              name="escopoExclusao"
              value="ESTA_E_FUTURAS"
              aria-label="Esta e todas as futuras"
              checked={escopo === 'ESTA_E_FUTURAS'}
              onChange={() => onChangeEscopo('ESTA_E_FUTURAS')}
            />
            <div className={styles.optionText}>
              <strong>Esta e todas as futuras</strong>
              <span className={styles.optionHint}>
                Encerra a série nesta data e remove ocorrências futuras não concluídas.
              </span>
            </div>
          </label>

          <label className={styles.option}>
            <input
              type="radio"
              name="escopoExclusao"
              value="TODA_A_SERIE"
              aria-label="Toda a série de tarefas"
              checked={escopo === 'TODA_A_SERIE'}
              onChange={() => onChangeEscopo('TODA_A_SERIE')}
            />
            <div className={styles.optionText}>
              <strong>Toda a série de tarefas</strong>
              <span className={styles.optionHint}>
                Envia a regra e todas as ocorrências pendentes para a lixeira. Ocorrências já
                concluídas são preservadas.
              </span>
            </div>
          </label>
        </div>
      </div>
    </Modal>
  );
};
