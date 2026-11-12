import React, { useId } from 'react';
import { FormActions } from '../FormActions/FormActions';
import { Modal, ModalProps } from '../Modal/Modal';
import styles from './FormModal.module.css';

export interface FormModalProps extends Omit<ModalProps, 'children' | 'footer'> {
  children: React.ReactNode;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
  submitText: string;
  cancelText?: string;
  loading?: boolean;
  submitDisabled?: boolean;
  error?: string;
  deleteAction?: React.ReactNode;
}

export const FormModal: React.FC<FormModalProps> = ({
  children,
  onSubmit,
  submitText,
  cancelText = 'Cancelar',
  loading = false,
  submitDisabled = false,
  error,
  deleteAction,
  onClose,
  ...modalProps
}) => {
  const formId = useId();

  const handleClose = loading ? () => {} : onClose;

  return (
    <Modal
      {...modalProps}
      isSaving={loading}
      onClose={handleClose}
      footer={
        <FormActions
          onCancel={handleClose}
          submitText={submitText}
          cancelText={cancelText}
          form={formId}
          loading={loading}
          submitDisabled={submitDisabled}
          leadingAction={deleteAction}
        />
      }
    >
      <form id={formId} className={styles.form} onSubmit={onSubmit} aria-busy={loading}>
        {children}
        {error && <p className={styles.error} role="alert">{error}</p>}
      </form>
    </Modal>
  );
};
