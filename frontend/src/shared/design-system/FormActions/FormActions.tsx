import React from 'react';
import { Button } from '../Button/Button';
import styles from './FormActions.module.css';

export interface FormActionsProps {
  onCancel: () => void;
  submitText: string;
  cancelText?: string;
  onSubmit?: () => void;
  form?: string;
  loading?: boolean;
  submitDisabled?: boolean;
  leadingAction?: React.ReactNode;
}

export const FormActions: React.FC<FormActionsProps> = ({
  onCancel,
  submitText,
  cancelText = 'Cancelar',
  onSubmit,
  form,
  loading = false,
  submitDisabled = false,
  leadingAction
}) => (
  <>
    {leadingAction && <div className={styles.leadingAction}>{leadingAction}</div>}
    <Button
      type="button"
      variant="secondary"
      onClick={onCancel}
      disabled={loading}
    >
      {cancelText}
    </Button>
    <Button
      type={form ? 'submit' : 'button'}
      form={form}
      onClick={form ? undefined : onSubmit}
      loading={loading}
      disabled={submitDisabled}
    >
      {submitText}
    </Button>
  </>
);
