import React, { useContext, useState } from 'react';
import { AlertTriangle, ShieldAlert, Trash2 } from 'lucide-react';
import { Button, Input, Modal, ConfirmContext } from '../design-system';
import { UsuarioAutenticado } from './types';
import styles from './ContaModal.module.css';

export interface ContaModalProps {
  isOpen: boolean;
  onClose: () => void;
  usuario?: UsuarioAutenticado | null;
  onExcluirConta?: (senha: string) => Promise<unknown>;
}

export const ContaModal: React.FC<ContaModalProps> = ({
  isOpen,
  onClose,
  usuario,
  onExcluirConta
}) => {
  const confirmContext = useContext(ConfirmContext);
  const confirm = confirmContext?.confirm ?? (async (opts) => {
    return typeof window !== 'undefined' ? window.confirm(opts.description) : true;
  });
  const [etapaConfirmacao, setEtapaConfirmacao] = useState<boolean>(false);
  const [senha, setSenha] = useState<string>('');
  const [confirmacaoTexto, setConfirmacaoTexto] = useState<string>('');
  const [carregando, setCarregando] = useState<boolean>(false);
  const [erro, setErro] = useState<string | null>(null);

  const resetarEstado = () => {
    setEtapaConfirmacao(false);
    setSenha('');
    setConfirmacaoTexto('');
    setErro(null);
    setCarregando(false);
  };

  const handleClose = () => {
    resetarEstado();
    onClose();
  };

  const handleIniciarExclusao = async () => {
    setErro(null);
    const confirmou = await confirm({
      title: 'Excluir minha conta permanentemente',
      description:
        'Tem certeza de que deseja excluir sua conta? Todos os seus estudos, cronogramas, sessões, tarefas, notas e lixeiras serão permanentemente apagados. Esta ação é irreversível.',
      confirmText: 'Prosseguir para exclusão',
      cancelText: 'Cancelar',
      variant: 'danger'
    });

    if (confirmou) {
      setEtapaConfirmacao(true);
    }
  };

  const handleConfirmarExclusaoDefinitiva = async (e: React.FormEvent) => {
    e.preventDefault();
    if (confirmacaoTexto.trim() !== 'EXCLUIR MINHA CONTA') {
      setErro('Digite exatamente EXCLUIR MINHA CONTA para confirmar.');
      return;
    }
    if (!senha) {
      setErro('Informe sua senha atual para autorizar a exclusão.');
      return;
    }

    setCarregando(true);
    setErro(null);

    try {
      if (onExcluirConta) {
        await onExcluirConta(senha);
      }
      handleClose();
    } catch (err: unknown) {
      if (err instanceof Error) {
        setErro(err.message);
      } else {
        setErro('Ocorreu um erro ao processar a exclusão da conta. Verifique sua senha.');
      }
    } finally {
      setCarregando(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="Minha Conta"
      icon={<ShieldAlert size={20} />}
      maxWidth="md"
    >
      <div className={styles.container}>
        {usuario && (
          <div className={styles.profileSection}>
            <div className={styles.profileRow}>
              <span className={styles.label}>Nome:</span>
              <span className={styles.value}>{usuario.nome}</span>
            </div>
            <div className={styles.profileRow}>
              <span className={styles.label}>E-mail:</span>
              <span className={styles.value}>{usuario.email}</span>
            </div>
          </div>
        )}

        <div className={styles.dangerZone}>
          <div className={styles.dangerHeader}>
            <AlertTriangle size={18} />
            <span>Zona de Perigo</span>
          </div>

          <p className={styles.dangerDescription}>
            A exclusão da sua conta remove permanentemente todos os seus dados nas 18 coleções privadas
            do sistema (incluindo estudos, cronogramas, sessões, tarefas, notas e lixeiras).
            Essa operação é definitiva e não pode ser desfeita.
          </p>

          {!etapaConfirmacao ? (
            <div>
              <Button
                type="button"
                variant="danger"
                onClick={handleIniciarExclusao}
                leftIcon={<Trash2 size={16} />}
              >
                Excluir minha conta
              </Button>
            </div>
          ) : (
            <form onSubmit={handleConfirmarExclusaoDefinitiva} className={styles.confirmationForm}>
              <p className={styles.confirmationInstructions}>
                Para autorizar a exclusão permanente, digite sua senha atual e a frase{' '}
                <strong className={styles.highlightText}>EXCLUIR MINHA CONTA</strong>:
              </p>

              <Input
                label="Senha atual"
                type="password"
                value={senha}
                onChange={(e) => setSenha(e.target.value)}
                placeholder="Sua senha atual"
                required
              />

              <Input
                label="Confirmação textual"
                type="text"
                value={confirmacaoTexto}
                onChange={(e) => setConfirmacaoTexto(e.target.value)}
                placeholder="EXCLUIR MINHA CONTA"
                required
              />

              {erro && <div className={styles.errorMessage}>{erro}</div>}

              <div className={styles.formActions}>
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => setEtapaConfirmacao(false)}
                  disabled={carregando}
                >
                  Cancelar
                </Button>
                <Button
                  type="submit"
                  variant="danger"
                  disabled={carregando || confirmacaoTexto.trim() !== 'EXCLUIR MINHA CONTA' || !senha}
                >
                  {carregando ? 'Excluindo conta...' : 'Confirmar Exclusão Definitiva'}
                </Button>
              </div>
            </form>
          )}
        </div>
      </div>
    </Modal>
  );
};
