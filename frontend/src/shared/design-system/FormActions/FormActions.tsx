import React from 'react';
import { Button } from '../Button/Button';
import styles from './FormActions.module.css';

export interface FormActionsProps {
  onCancel: () => void;
  submitText: string;
  cancelText?: string;
  onSubmit?: () => void;
  form?: string;
  isLoading?: boolean;
  submitDisabled?: boolean;
  leadingAction?: React.ReactNode;
}

export const FormActions: React.FC<FormActionsProps> = ({
  onCancel,
  submitText,
  cancelText = 'Cancelar',
  onSubmit,
  form,
  isLoading = false,
  submitDisabled = false,
  leadingAction
}) => (
  <>
    {leadingAction && <div className={styles.leadingAction}>{leadingAction}</div>}
    <Button
      type="button"
      variant="secondary"
      onClick={onCancel}
      disabled={isLoading}
    >
      {cancelText}
    </Button>
    <Button
      type={form ? 'submit' : 'button'}
      form={form}
      onClick={form ? undefined : onSubmit}
      isLoading={isLoading}
      disabled={submitDisabled}
    >
      {submitText}
    </Button>
  </>
);
