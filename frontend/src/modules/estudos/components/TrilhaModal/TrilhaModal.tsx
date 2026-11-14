import React, { useEffect, useState } from 'react';
import { Route, Trash2 } from 'lucide-react';
import {
  Button,
  DatePicker,
  FormModal,
  Input,
  Modal,
  Textarea,
  useToast
} from '@shared/design-system';
import { AtualizacaoMetadadosTrilha, TrilhaDetalheDTO, TrilhaImpactoExclusaoDTO } from '../../types/trilhara';
import { EstudosService } from '../../services/estudosService';
import styles from './TrilhaModal.module.css';

const LIMITES = { nome: 120, cargo: 120, banca: 80, informacoesComplementares: 2000 } as const;

interface FormTrilha {
  nome: string;
  cargo: string;
  banca: string;
  dataProva: string;
  informacoesComplementares: string;
}

/** Trilha exibida no modal: o resumo do Hub ou o detalhe carregado. */
type TrilhaDoModal = Pick<TrilhaDetalheDTO, 'id' | 'nome' | 'cargo' | 'banca' | 'dataProva'> & {
  informacoesComplementares?: string;
};

const paraFormulario = (trilha?: TrilhaDoModal | null): FormTrilha => ({
  nome: trilha?.nome ?? '',
  cargo: trilha?.cargo ?? '',
  banca: trilha?.banca ?? '',
  dataProva: trilha?.dataProva ?? '',
  informacoesComplementares: trilha?.informacoesComplementares ?? ''
});

export interface TrilhaModalProps {
  isOpen: boolean;
  trilha?: TrilhaDoModal | null;
  onClose: () => void;
  onTrilhaSalva: (trilha: TrilhaDetalheDTO, modo: 'criada' | 'editada') => void;
  onExcluirTrilha?: (id: string) => Promise<boolean>;
}

export const TrilhaModal: React.FC<TrilhaModalProps> = ({
  isOpen,
  trilha,
  onClose,
  onTrilhaSalva,
  onExcluirTrilha
}) => {
  const toast = useToast();
  const isEdicao = Boolean(trilha);

  const [form, setForm] = useState<FormTrilha>(() => paraFormulario(trilha));
  const atualizarCampo = <K extends keyof FormTrilha>(campo: K, valor: FormTrilha[K]) =>
    setForm((atual) => ({ ...atual, [campo]: valor }));

  const [salvando, setSalvando] = useState(false);
  const [erroNome, setErroNome] = useState('');
  const [confirmarExclusao, setConfirmarExclusao] = useState(false);
  const [textoConfirmacao, setTextoConfirmacao] = useState('');
  const [impacto, setImpacto] = useState<TrilhaImpactoExclusaoDTO | null>(null);
  const [carregandoImpacto, setCarregandoImpacto] = useState(false);
  const [excluindo, setExcluindo] = useState(false);

  useEffect(() => {
    if (!isOpen) return;
    setForm(paraFormulario(trilha));
    setErroNome('');
    setConfirmarExclusao(false);
    setTextoConfirmacao('');
    setImpacto(null);
  }, [trilha, isOpen]);

  // exclusão de trilha exige o impacto em números e o nome exato.
  useEffect(() => {
    if (!confirmarExclusao || !trilha) return;
    let ativo = true;
    setCarregandoImpacto(true);
    EstudosService.obterImpactoExclusaoTrilha(trilha.id)
      .then((dados) => {
        if (ativo) setImpacto(dados);
      })
      .catch(() => {
        if (ativo) setImpacto(null);
      })
      .finally(() => {
        if (ativo) setCarregandoImpacto(false);
      });
    return () => {
      ativo = false;
    };
  }, [confirmarExclusao, trilha]);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (salvando || excluindo) return;

    const nomeLimpo = form.nome.trim();
    if (!nomeLimpo) {
      setErroNome('Informe o nome da trilha de estudos.');
      return;
    }

    const dados: AtualizacaoMetadadosTrilha = {
      nome: nomeLimpo,
      cargo: form.cargo.trim() || undefined,
      banca: form.banca.trim() || undefined,
      dataProva: form.dataProva || undefined,
      informacoesComplementares: form.informacoesComplementares.trim() || undefined
    };

    setSalvando(true);
    try {
      if (trilha) {
        const atualizada = await EstudosService.atualizarMetadadosTrilha(trilha.id, dados);
        toast.success('Trilha atualizada com sucesso.');
        onTrilhaSalva(atualizada, 'editada');
      } else {
        const nova = await EstudosService.criarTrilha({
          nome: nomeLimpo,
          cargo: dados.cargo,
          banca: dados.banca,
          dataProva: dados.dataProva,
          disciplinasIniciais: []
        });
        toast.success(`Trilha "${nova.nome}" criada com sucesso!`);
        onTrilhaSalva(nova, 'criada');
      }
    } catch (err) {
      toast.error(
        err instanceof Error
          ? err
          : trilha
            ? 'Não foi possível atualizar a trilha.'
            : 'Não foi possível criar a trilha.'
      );
    } finally {
      setSalvando(false);
    }
  };

  // recurso grande confirma digitando o nome exato da trilha (padrão GitHub).
  const confirmacaoValida = Boolean(trilha)
    && textoConfirmacao.trim() === (trilha?.nome ?? '').trim();

  const handleExcluir = async () => {
    if (!trilha || !onExcluirTrilha || !confirmacaoValida || excluindo) return;
    setExcluindo(true);
    try {
      if (await onExcluirTrilha(trilha.id)) {
        setConfirmarExclusao(false);
        onClose();
      }
    } catch (err) {
      toast.error(err instanceof Error ? err : 'Não foi possível excluir a trilha.');
    } finally {
      setExcluindo(false);
    }
  };

  const fecharSeOcioso = () => {
    if (!salvando && !excluindo) onClose();
  };

  const descricaoImpacto = impacto
    ? `${impacto.disciplinas} disciplina(s), ${impacto.cronogramas} cronograma(s), ${impacto.sessoes} sessão(ões) e ${impacto.cadernosVinculados} caderno(s) vinculado(s).`
    : carregandoImpacto
      ? 'Calculando o impacto da exclusão…'
      : 'O impacto da exclusão não pôde ser calculado.';

  return (
    <>
      <FormModal
        isOpen={isOpen}
        onClose={fecharSeOcioso}
        title={isEdicao ? 'Editar Trilha de Estudos' : 'Criar Trilha de Estudos'}
        subtitle={isEdicao ? 'Atualize os detalhes da sua trilha.' : 'Defina os detalhes da sua trilha.'}
        icon={<Route size={22} />}
        onSubmit={handleSubmit}
        submitText="Salvar"
        loading={salvando || excluindo}
        deleteAction={
          isEdicao && onExcluirTrilha ? (
            <Button
              type="button"
              variant="danger"
              size="md"
              leftIcon={<Trash2 size={15} />}
              disabled={salvando || excluindo}
              onClick={() => setConfirmarExclusao(true)}
            >
              Excluir
            </Button>
          ) : undefined
        }
        maxWidth="md"
      >
        <Input
          label="Nome da trilha"
          placeholder="Ex: TCU - Auditor Federal, Pós IA, Certificação AWS..."
          value={form.nome}
          maxLength={LIMITES.nome}
          onChange={(event) => {
            atualizarCampo('nome', event.target.value);
            if (erroNome) setErroNome('');
          }}
          error={erroNome}
          required
        />

        <div className={styles.formGrid}>
          <Input
            label="Área/Cargo"
            placeholder="Ex: Controle Externo e Gestão Pública, Auditor Federal..."
            value={form.cargo}
            maxLength={LIMITES.cargo}
            onChange={(event) => atualizarCampo('cargo', event.target.value)}
          />
          <Input
            label="Banca ou instituição"
            placeholder="Ex: FGV, Cebraspe, FCC..."
            value={form.banca}
            maxLength={LIMITES.banca}
            onChange={(event) => atualizarCampo('banca', event.target.value)}
          />
        </div>

        <div className={styles.formGrid}>
          <DatePicker
            label="Data da prova ou meta (opcional)"
            value={form.dataProva}
            onChange={(valor) => atualizarCampo('dataProva', valor)}
            allowClear
          />
        </div>

        <hr className={styles.divider} />

        <Textarea
          label="Informações complementares (opcional)"
          rows={6}
          maxLength={LIMITES.informacoesComplementares}
          helperText={`${form.informacoesComplementares.length}/${LIMITES.informacoesComplementares} caracteres`}
          placeholder={'Ex:\nSite do concurso: https://...\nTaxa de inscrição: R$ 180,00\nPeríodo de inscrições: 10/01 a 15/02\nLocal da prova: Brasília/DF'}
          value={form.informacoesComplementares}
          onChange={(event) => atualizarCampo('informacoesComplementares', event.target.value)}
        />
      </FormModal>

      {isEdicao && (
        <Modal
          isOpen={confirmarExclusao}
          onClose={() => !excluindo && setConfirmarExclusao(false)}
          title="Excluir trilha"
          subtitle="Esta ação não pode ser desfeita."
          icon={<Trash2 size={22} />}
          footer={
            <>
              <Button
                type="button"
                variant="ghost"
                disabled={excluindo}
                onClick={() => setConfirmarExclusao(false)}
              >
                Cancelar
              </Button>
              <Button
                type="button"
                variant="danger"
                size="md"
                leftIcon={<Trash2 size={15} />}
                loading={excluindo}
                disabled={!confirmacaoValida || excluindo}
                onClick={() => void handleExcluir()}
              >
                Excluir trilha
              </Button>
            </>
          }
        >
          <form
            onSubmit={(event) => {
              event.preventDefault();
              void handleExcluir();
            }}
          >
            <p>
              Serão excluídos: <strong>{descricaoImpacto}</strong>
            </p>
            <p>
              Digite o nome exato <strong>{trilha?.nome}</strong> para confirmar.
            </p>
            <Input
              label="Confirme o nome da trilha"
              value={textoConfirmacao}
              onChange={(event) => setTextoConfirmacao(event.target.value)}
              placeholder={trilha?.nome}
              maxLength={120}
              autoComplete="off"
            />
          </form>
        </Modal>
      )}
    </>
  );
};
