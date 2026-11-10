import React, { useState } from 'react';
import { Plus } from 'lucide-react';
import styles from './QuickAddTask.module.css';

export interface QuickAddTaskProps {
  onAdicionar: (titulo: string) => Promise<boolean> | boolean;
  disabled?: boolean;
}

export const QuickAddTask: React.FC<QuickAddTaskProps> = ({ onAdicionar, disabled }) => {
  const [titulo, setTitulo] = useState('');
  const [salvando, setSalvando] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const texto = titulo.trim();
    if (!texto || salvando || disabled) return;

    setSalvando(true);
    try {
      const ok = await onAdicionar(texto);
      if (ok) {
        setTitulo('');
      }
    } finally {
      setSalvando(false);
    }
  };

  return (
    <form className={styles.container} onSubmit={handleSubmit}>
      <button
        type="submit"
        className={styles.iconButton}
        disabled={!titulo.trim() || salvando || disabled}
        title="Adicionar tarefa (Enter)"
        aria-label="Adicionar tarefa"
      >
        <Plus size={18} />
      </button>
      <input
        type="text"
        className={styles.input}
        placeholder="Adicionar uma tarefa... (Pressione Enter para salvar)"
        value={titulo}
        onChange={(e) => setTitulo(e.target.value)}
        disabled={salvando || disabled}
      />
    </form>
  );
};
