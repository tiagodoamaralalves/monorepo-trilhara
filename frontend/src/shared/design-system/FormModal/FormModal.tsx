import React, { useId } from 'react';
import { FormActions } from '../FormActions/FormActions';
import { Modal, ModalProps } from '../Modal/Modal';
import styles from './FormModal.module.css';

export interface FormModalProps extends Omit<ModalProps, 'children' | 'footer'> {
  children: React.ReactNode;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
  submitText: string;
  cancelText?: string;
  isLoading?: boolean;
  submitDisabled?: boolean;
  error?: string;
  deleteAction?: React.ReactNode;
}

export const FormModal: React.FC<FormModalProps> = ({
  children,
  onSubmit,
  submitText,
  cancelText = 'Cancelar',
  isLoading = false,
  submitDisabled = false,
  error,
  deleteAction,
  onClose,
  ...modalProps
}) => {
  const formId = useId();

  const handleClose = isLoading ? () => {} : onClose;

  return (
    <Modal
      {...modalProps}
      isSaving={isLoading}
      onClose={handleClose}
      footer={
        <FormActions
          onCancel={handleClose}
          submitText={submitText}
          cancelText={cancelText}
          form={formId}
          isLoading={isLoading}
          submitDisabled={submitDisabled}
          leadingAction={deleteAction}
        />
      }
    >
      <form id={formId} className={styles.form} onSubmit={onSubmit} aria-busy={isLoading}>
        {children}
        {error && <p className={styles.error} role="alert">{error}</p>}
      </form>
    </Modal>
  );
};
