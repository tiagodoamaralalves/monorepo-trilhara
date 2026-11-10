import React, { useState, useEffect } from 'react';
import { Bell, Smartphone, ShieldCheck, AlertCircle } from 'lucide-react';
import { Modal, Select, Button } from '@shared/design-system';
import {
  pushService,
  PushConfig,
  PushPermissionState,
  getPushPermissionState,
  isIOSNonStandalone
} from '@shared/services/pushService';

export interface ConfiguracaoLembreteModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSalvo?: (novoPadrao: number) => void;
}

export const ConfiguracaoLembreteModal: React.FC<ConfiguracaoLembreteModalProps> = ({
  isOpen,
  onClose,
  onSalvo
}) => {
  const [config, setConfig] = useState<PushConfig | null>(null);
  const [padrao, setPadrao] = useState<number>(15);
  const [permissionState, setPermissionState] = useState<PushPermissionState>('default');
  const [isIOS, setIsIOS] = useState(false);
  const [salvando, setSalvando] = useState(false);
  const [mensagemStatus, setMensagemStatus] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) return;

    setPermissionState(getPushPermissionState());
    setIsIOS(isIOSNonStandalone());
    setMensagemStatus(null);

    pushService.obterConfig()
      .then((cfg) => {
        setConfig(cfg);
        if (cfg.padraoMinutosAntes !== undefined) {
          setPadrao(cfg.padraoMinutosAntes);
        }
      })
      .catch(() => {
        setPadrao(15);
      });
  }, [isOpen]);

  const handleSalvar = async (e: React.FormEvent) => {
    e.preventDefault();
    setSalvando(true);
    try {
      await pushService.atualizarPreferenciaGlobal(padrao);
      if (onSalvo) onSalvo(padrao);
      onClose();
    } catch (err: any) {
      setMensagemStatus(err?.message || 'Erro ao salvar preferência.');
    } finally {
      setSalvando(false);
    }
  };

  const handleAtivarDispositivo = async () => {
    const res = await pushService.ativarNotificacoes(config?.publicKey);
    setPermissionState(getPushPermissionState());
    if (!res.sucesso && res.mensagem) {
      setMensagemStatus(res.mensagem);
    } else {
      setMensagemStatus('Notificações ativadas com sucesso neste dispositivo!');
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Preferências de Notificações Web Push"
      maxWidth="md"
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', width: '100%' }}>
          <Button variant="secondary" onClick={onClose} disabled={salvando}>
            Cancelar
          </Button>
          <Button
            variant="primary"
            onClick={handleSalvar}
            isLoading={salvando}
            data-testid="btn-salvar-preferencias-push"
          >
            Salvar Preferência
          </Button>
        </div>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <div>
          <Select
            label="Lembrete Padrão Global"
            value={String(padrao)}
            onChange={(val) => setPadrao(Number(val))}
            options={[
              { value: '0', label: 'No horário da tarefa (0 min)' },
              { value: '5', label: '5 minutos antes' },
              { value: '15', label: '15 minutos antes (recomendado)' },
              { value: '30', label: '30 minutos antes' },
              { value: '60', label: '60 minutos antes (1 hora)' }
            ]}
            disabled={salvando}
          />
          <p style={{ margin: '6px 0 0 0', fontSize: '0.8125rem', color: 'var(--text-secondary, #94a3b8)' }}>
            Novas tarefas com horário herdam esse padrão automaticamente, a menos que você sobrescreva manualmente.
          </p>
        </div>

        {/* Estado da permissão do navegador */}
        <div
          style={{
            padding: '12px',
            borderRadius: '8px',
            backgroundColor: 'var(--bg-subtle, rgba(255,255,255,0.04))',
            border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))'
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 600 }}>
              <Bell size={16} />
              <span>Status do Dispositivo:</span>
            </span>
            <span
              data-testid="status-permissao-push"
              style={{
                fontSize: '0.8125rem',
                fontWeight: 600,
                color:
                  permissionState === 'granted'
                    ? '#10b981'
                    : permissionState === 'denied'
                    ? '#ef4444'
                    : '#f59e0b'
              }}
            >
              {permissionState === 'granted'
                ? 'Concedida (Ativo)'
                : permissionState === 'denied'
                ? 'Bloqueada pelo Usuário'
                : permissionState === 'unsupported'
                ? 'Não Suportado'
                : 'Pendente de Permissão'}
            </span>
          </div>

          {permissionState !== 'granted' && permissionState !== 'unsupported' && (
            <div style={{ marginTop: '10px' }}>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleAtivarDispositivo}
                data-testid="btn-ativar-push"
              >
                Habilitar Notificações neste Navegador
              </Button>
            </div>
          )}
        </div>

        {/* Dica para iOS Safari */}
        {isIOS && (
          <div
            data-testid="dica-ios-standalone"
            style={{
              display: 'flex',
              gap: '10px',
              padding: '10px',
              borderRadius: '8px',
              backgroundColor: 'rgba(59, 130, 246, 0.1)',
              border: '1px solid rgba(59, 130, 246, 0.3)'
            }}
          >
            <Smartphone size={20} color="#3b82f6" style={{ flexShrink: 0 }} />
            <div style={{ fontSize: '0.8125rem', color: 'var(--text-secondary, #cbd5e1)' }}>
              <strong>Atenção no iPhone/iPad:</strong> No iOS Safari (16.4+), o Web Push só pode ser ativado se o Trilhara for instalado.
              Toque em <strong>Compartilhar</strong> e selecione <strong>"Adicionar à Tela de Início"</strong>.
            </div>
          </div>
        )}

        {mensagemStatus && (
          <div
            role="status"
            style={{
              padding: '8px 12px',
              borderRadius: '6px',
              fontSize: '0.8125rem',
              backgroundColor: 'rgba(59, 130, 246, 0.15)',
              color: 'var(--text-primary, #ffffff)'
            }}
          >
            {mensagemStatus}
          </div>
        )}
      </div>
    </Modal>
  );
};
