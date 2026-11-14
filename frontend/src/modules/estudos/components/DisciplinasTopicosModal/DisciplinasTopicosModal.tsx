import React, { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { ArrowLeft, BookOpen, Check, Pencil, Trash2, Undo2, X } from 'lucide-react';
import {
  Button,
  Badge,
  Checkbox,
  ColorPicker,
  DisciplineRow,
  Input,
  Modal,
  QuickAddInput,
  Textarea,
  TopicRow,
  useConfirm
} from '@shared/design-system';
import { CriarDisciplinaRequest, Disciplina, ProgressoTrilha, Topico } from '../../types/trilhara';
import { obterCorDisponivel, obterCoresEmUso } from '../../utils/coresDisciplina';
import { DisciplinaPesoSelector } from '../DisciplinaPesoSelector/DisciplinaPesoSelector';
import { DisciplinaPesoButton } from '../DisciplinaPesoButton/DisciplinaPesoButton';
import styles from './DisciplinasTopicosModal.module.css';

const ID_NOVA_DISCIPLINA = '__nova_disciplina__';
const PREFIXO_TOPICO_TEMPORARIO = '__novo_topico_';
const MAX_CARACTERES_DISCIPLINA = 120;
const MAX_CARACTERES_TOPICO = 300;
const MAX_TOPICOS_POR_DISCIPLINA = 500;

let contadorIdLocal = 0;

function gerarIdTemporario(): string {
  contadorIdLocal += 1;
  return `${PREFIXO_TOPICO_TEMPORARIO}${Date.now()}_${contadorIdLocal.toString(36)}`;
}

function isTopicoTemporario(id: string): boolean {
  return !id || id.startsWith(PREFIXO_TOPICO_TEMPORARIO);
}

interface InlineTopicTextareaProps {
  value: string;
  maxLength: number;
  onChange: (value: string) => void;
  onCommit: () => void;
  onCancel: () => void;
}

const InlineTopicTextarea: React.FC<InlineTopicTextareaProps> = ({
  value,
  maxLength,
  onChange,
  onCommit,
  onCancel
}) => {
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.focus();
    const len = el.value.length;
    el.setSelectionRange(len, len);
  }, []);

  useLayoutEffect(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${el.scrollHeight}px`;
  }, [value]);

  return (
    <textarea
      ref={textareaRef}
      rows={1}
      className={styles.inlineEditTextarea}
      value={value}
      aria-label="Nome do assunto"
      maxLength={maxLength}
      onChange={(event) => onChange(event.target.value)}
      onBlur={onCommit}
      onKeyDown={(event) => {
        if (event.key === 'Enter' && !event.shiftKey) {
          event.preventDefault();
          onCommit();
        } else if (event.key === 'Escape') {
          event.preventDefault();
          onCancel();
        }
      }}
      onClick={(event) => event.stopPropagation()}
    />
  );
};

export interface DisciplinasTopicosModalProps {
  isOpen: boolean;
  mode?: 'create' | 'edit';
  title: string;
  subtitle?: string;
  icon?: React.ReactNode;
  disciplinas: Disciplina[];
  initialDisciplinaId?: string | null;
  /** Conjunto de IDs de tópicos concluídos (derivado de `topicosConcluidosIds`). */
  progresso?: ProgressoTrilha;
  /** Se true e callback de edição/criação fornecido, permite gerenciar a disciplina e seus assuntos */
  editable?: boolean;
  onCreateDiscipline?: (payload: CriarDisciplinaRequest) => void | Promise<void>;
  onSaveDiscipline?: (disciplina: Disciplina) => void;
  savingDiscipline?: boolean;
  onDeleteDiscipline?: (disciplina: Disciplina) => void;
  deletingDiscipline?: boolean;
  onToggleTopico?: (topicoId: string, concluido: boolean) => void;
  onToggleConclusaoDisciplina?: (disciplinaId: string, concluida: boolean) => void;
  onClose: () => void;
  /** Ações extras no rodapé (ex: botão "Usar essa trilha") */
  extraFooterAction?: React.ReactNode;
  /** Se deve mostrar o peso da disciplina no modo visualização (padrão: true se readonly) */
  mostrarPeso?: boolean;
  maxWidth?: 'sm' | 'md' | 'lg' | 'xl';
}

export const DisciplinasTopicosModal: React.FC<DisciplinasTopicosModalProps> = ({
  isOpen,
  mode = 'edit',
  title,
  subtitle = 'Disciplinas cadastradas',
  icon = <BookOpen size={22} />,
  disciplinas,
  initialDisciplinaId = null,
  progresso,
  editable = false,
  onCreateDiscipline,
  onSaveDiscipline,
  savingDiscipline = false,
  onDeleteDiscipline,
  deletingDiscipline = false,
  onToggleTopico,
  onToggleConclusaoDisciplina,
  onClose,
  extraFooterAction,
  mostrarPeso,
  maxWidth = 'lg'
}) => {
  const { confirm } = useConfirm();
  const [disciplinasLocais, setDisciplinasLocais] = useState<Disciplina[]>(disciplinas);
  const [disciplinaAbertaId, setDisciplinaAbertaId] = useState<string | null>(null);

  // Estados de edição
  const [novosTopicosTexto, setNovosTopicosTexto] = useState('');
  const [novoTopicoRapido, setNovoTopicoRapido] = useState('');
  const [erroTopicos, setErroTopicos] = useState('');
  const [editandoDisciplinaId, setEditandoDisciplinaId] = useState<string | null>(null);
  const [nomeDisciplinaEditado, setNomeDisciplinaEditado] = useState('');
  const [editandoTopicoId, setEditandoTopicoId] = useState<string | null>(null);
  const [nomeTopicoEditado, setNomeTopicoEditado] = useState('');
  const [mostrarCores, setMostrarCores] = useState(false);
  const colorPickerContainerRef = useRef<HTMLDivElement>(null);
  const titleInputRef = useRef<HTMLInputElement>(null);

  const prevIsOpenRef = useRef(isOpen);
  const prevInitialIdRef = useRef(initialDisciplinaId);
  const prevModeRef = useRef(mode);

  const isCreateMode = mode === 'create';
  const isEditable = Boolean(
    editable && (onSaveDiscipline || (isCreateMode && onCreateDiscipline))
  );

  // Sincroniza rascunho local ao abrir, trocar de modo ou trocar de disciplina
  useEffect(() => {
    const justOpened = isOpen && !prevIsOpenRef.current;
    const switchedDisciplina = isOpen && initialDisciplinaId !== prevInitialIdRef.current;
    const switchedMode = isOpen && mode !== prevModeRef.current;

    if (justOpened || switchedDisciplina || switchedMode) {
      if (isCreateMode) {
        const corInicial = obterCorDisponivel(disciplinas);
        const rascunhoNova: Disciplina = {
          id: ID_NOVA_DISCIPLINA,
          nome: '',
          cor: corInicial,
          peso: 1,
          ordem: disciplinas.length,
          topicos: [],
          concluida: false
        };
        setDisciplinasLocais([rascunhoNova]);
        setDisciplinaAbertaId(ID_NOVA_DISCIPLINA);
        setEditandoDisciplinaId(ID_NOVA_DISCIPLINA);
        setNomeDisciplinaEditado('');
      } else {
        const targetId =
          initialDisciplinaId ?? (isEditable && disciplinas.length === 1 ? disciplinas[0].id : null);
        setDisciplinasLocais(disciplinas);
        setDisciplinaAbertaId(targetId);
        setEditandoDisciplinaId(null);
        const disciplinaInicial = disciplinas.find((item) => item.id === targetId);
        setNomeDisciplinaEditado(disciplinaInicial?.nome ?? '');
      }
      setNovosTopicosTexto('');
      setNovoTopicoRapido('');
      setErroTopicos('');
      setEditandoTopicoId(null);
      setNomeTopicoEditado('');
      setMostrarCores(false);
    } else if (!isOpen && prevIsOpenRef.current) {
      setDisciplinaAbertaId(null);
      setNovosTopicosTexto('');
      setNovoTopicoRapido('');
      setErroTopicos('');
      setEditandoDisciplinaId(null);
      setEditandoTopicoId(null);
      setNomeTopicoEditado('');
      setMostrarCores(false);
    }

    prevIsOpenRef.current = isOpen;
    prevInitialIdRef.current = initialDisciplinaId;
    prevModeRef.current = mode;
  }, [isOpen, initialDisciplinaId, mode, isCreateMode, isEditable, disciplinas]);

  // Mantém `concluida` sincronizado com atualizações otimistas/rollback vindas do pai
  useEffect(() => {
    if (!isOpen || isCreateMode) return;
    setDisciplinasLocais((prev) =>
      prev.map((local) => {
        const atualizada = disciplinas.find((item) => item.id === local.id);
        if (!atualizada || atualizada.concluida === local.concluida) {
          return local;
        }
        return { ...local, concluida: atualizada.concluida };
      })
    );
  }, [isOpen, isCreateMode, disciplinas]);

  // Fecha o popover de cores ao clicar fora ou pressionar Escape
  useEffect(() => {
    if (!mostrarCores) return;

    function handleClickOutside(event: MouseEvent) {
      if (
        colorPickerContainerRef.current &&
        !colorPickerContainerRef.current.contains(event.target as Node)
      ) {
        setMostrarCores(false);
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setMostrarCores(false);
      }
    }

    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [mostrarCores]);

  const disciplinaSelecionada = useMemo(
    () => disciplinasLocais.find((item) => item.id === disciplinaAbertaId) || null,
    [disciplinasLocais, disciplinaAbertaId]
  );

  const coresEmUso = useMemo(
    () =>
      isCreateMode
        ? obterCoresEmUso(disciplinas)
        : obterCoresEmUso(disciplinasLocais, disciplinaSelecionada?.id),
    [isCreateMode, disciplinas, disciplinasLocais, disciplinaSelecionada?.id]
  );

  const exibePeso = mostrarPeso ?? !isEditable;

  const handleRenomearDisciplina = () => {
    const valor = nomeDisciplinaEditado.trim();
    if (!editandoDisciplinaId) return;
    if (!valor) {
      if (isCreateMode) {
        setDisciplinasLocais((prev) =>
          prev.map((item) => (item.id === editandoDisciplinaId ? { ...item, nome: '' } : item))
        );
        return;
      }
      setEditandoDisciplinaId(null);
      return;
    }
    setDisciplinasLocais((prev) =>
      prev.map((item) => (item.id === editandoDisciplinaId ? { ...item, nome: valor } : item))
    );
    setEditandoDisciplinaId(null);
  };

  const handleAlterarDisciplina = (campo: 'cor' | 'peso', valor: string | number) => {
    if (disciplinaAbertaId === null) return;
    setDisciplinasLocais((prev) =>
      prev.map((disciplina) =>
        disciplina.id === disciplinaAbertaId
          ? { ...disciplina, [campo]: valor }
          : disciplina
      )
    );
  };

  const handleExcluirDisciplina = async (disciplina: Disciplina) => {
    const ok = await confirm({
      title: 'Excluir disciplina?',
      description: `A disciplina "${disciplina.nome}" e todo o seu histórico (sessões de estudo, horas e questões) serão excluídos permanentemente.`,
      confirmText: 'Excluir definitivamente',
      variant: 'danger'
    });
    if (!ok) return;

    if (onDeleteDiscipline) {
      onDeleteDiscipline(disciplina);
    }
  };

  const validarNovosTopicos = (nomes: string[], quantidadeAtual: number): string | null => {
    if (nomes.some((nome) => nome.length > MAX_CARACTERES_TOPICO)) {
      return `Cada assunto deve ter no máximo ${MAX_CARACTERES_TOPICO} caracteres.`;
    }
    if (quantidadeAtual + nomes.length > MAX_TOPICOS_POR_DISCIPLINA) {
      return `Cada disciplina pode ter no máximo ${MAX_TOPICOS_POR_DISCIPLINA} assuntos.`;
    }
    return null;
  };

  const anexarTopicosLocais = (nomes: string[]): boolean => {
    if (disciplinaAbertaId === null || !disciplinaSelecionada || nomes.length === 0) return false;

    const erroValidacao = validarNovosTopicos(nomes, disciplinaSelecionada.topicos.length);
    if (erroValidacao) {
      setErroTopicos(erroValidacao);
      return false;
    }

    setDisciplinasLocais((prev) =>
      prev.map((disciplina) =>
        disciplina.id === disciplinaAbertaId
          ? {
              ...disciplina,
              topicos: [
                ...disciplina.topicos,
                ...nomes.map((nome) => ({ id: gerarIdTemporario(), nome }))
              ]
            }
          : disciplina
      )
    );
    setErroTopicos('');
    return true;
  };

  const handleAdicionarTopicos = () => {
    const nomes = novosTopicosTexto.split(/\r?\n/).map((item) => item.trim()).filter(Boolean);
    if (anexarTopicosLocais(nomes)) {
      setNovosTopicosTexto('');
    }
  };

  const handleAdicionarTopicoRapido = (nome: string): boolean => {
    return anexarTopicosLocais([nome]);
  };

  const handleRenomearTopico = () => {
    if (disciplinaAbertaId === null) return;
    const valor = nomeTopicoEditado.trim();
    if (!editandoTopicoId || !valor) {
      setEditandoTopicoId(null);
      setNomeTopicoEditado('');
      return;
    }
    setDisciplinasLocais((prev) =>
      prev.map((disciplina) =>
        disciplina.id === disciplinaAbertaId
          ? {
              ...disciplina,
              topicos: disciplina.topicos.map((topico) =>
                topico.id === editandoTopicoId ? { ...topico, nome: valor } : topico
              )
            }
          : disciplina
      )
    );
    setEditandoTopicoId(null);
    setNomeTopicoEditado('');
  };

  const handleExcluirTopico = async (topico: Topico) => {
    if (disciplinaAbertaId === null || !disciplinaSelecionada) return;
    const ok = await confirm({
      title: 'Excluir assunto?',
      description: `O assunto "${topico.nome}" será removido.`,
      confirmText: 'Excluir',
      variant: 'danger'
    });
    if (!ok) return;

    if (editandoTopicoId === topico.id) {
      setEditandoTopicoId(null);
      setNomeTopicoEditado('');
    }

    setDisciplinasLocais((prev) =>
      prev.map((item) =>
        item.id === disciplinaAbertaId
          ? { ...item, topicos: item.topicos.filter((actual) => actual.id !== topico.id) }
          : item
      )
    );
  };

  const isEditingSelectedTitle =
    isEditable &&
    Boolean(disciplinaSelecionada) &&
    editandoDisciplinaId === disciplinaSelecionada?.id;

  useEffect(() => {
    if (isOpen && isEditingSelectedTitle) {
      titleInputRef.current?.focus();
    }
  }, [isOpen, isEditingSelectedTitle]);

  const startEditingDiscipline = (disciplina: Disciplina) => {
    if (!isEditable) return;
    setEditandoDisciplinaId(disciplina.id);
    setNomeDisciplinaEditado(disciplina.nome);
  };

  const nomeFinalAtual = (
    isEditingSelectedTitle ? nomeDisciplinaEditado : disciplinaSelecionada?.nome || ''
  ).trim();
  const nomeFinalValido = Boolean(nomeFinalAtual);

  const isSaving = Boolean(savingDiscipline);
  const isDeleting = Boolean(deletingDiscipline);

  const handleSalvar = () => {
    if (!disciplinaSelecionada || !nomeFinalValido) return;

    let topicosAtuais = disciplinaSelecionada.topicos;
    if (editandoTopicoId && nomeTopicoEditado.trim()) {
      topicosAtuais = topicosAtuais.map((t) =>
        t.id === editandoTopicoId ? { ...t, nome: nomeTopicoEditado.trim() } : t
      );
    }

    const pendentesNomes = [
      ...novosTopicosTexto.split(/\r?\n/).map((item) => item.trim()).filter(Boolean),
      ...(novoTopicoRapido.trim() ? [novoTopicoRapido.trim()] : [])
    ];

    const erroValidacao = validarNovosTopicos(pendentesNomes, topicosAtuais.length);
    if (erroValidacao) {
      setErroTopicos(erroValidacao);
      return;
    }

    const topicosNormalizados = [
      ...topicosAtuais.map((t) => ({
        ...t,
        id: isTopicoTemporario(t.id) ? '' : t.id
      })),
      ...pendentesNomes.map((nome) => ({ id: '', nome }))
    ];

    setNovosTopicosTexto('');
    setNovoTopicoRapido('');
    setErroTopicos('');
    setEditandoTopicoId(null);
    setNomeTopicoEditado('');

    if (isCreateMode && onCreateDiscipline) {
      void onCreateDiscipline({
        nome: nomeFinalAtual,
        cor: disciplinaSelecionada.cor,
        peso: disciplinaSelecionada.peso ?? 1,
        topicos: topicosNormalizados.map((t, idx) => ({
          nome: t.nome,
          ordem: idx + 1
        }))
      });
      return;
    }

    const disciplinaFinal: Disciplina = {
      ...disciplinaSelecionada,
      nome: nomeFinalAtual,
      topicos: topicosNormalizados
    };

    onSaveDiscipline?.(disciplinaFinal);
  };

  const modalTitle = disciplinaSelecionada ? (
    isEditingSelectedTitle ? (
      <Input
        ref={titleInputRef}
        className={styles.titleEditInput}
        value={nomeDisciplinaEditado}
        maxLength={MAX_CARACTERES_DISCIPLINA}
        placeholder={isCreateMode ? 'Nome da disciplina' : undefined}
        aria-label="Nome da disciplina"
        onChange={(event) => setNomeDisciplinaEditado(event.target.value)}
        onBlur={handleRenomearDisciplina}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.preventDefault();
            handleRenomearDisciplina();
          } else if (event.key === 'Escape') {
            event.preventDefault();
            if (isCreateMode && !disciplinaSelecionada.nome.trim()) {
              return;
            }
            setNomeDisciplinaEditado(disciplinaSelecionada.nome);
            setEditandoDisciplinaId(null);
          }
        }}
      />
    ) : isEditable ? (
      <button
        type="button"
        className={styles.editableTitle}
        onClick={() => startEditingDiscipline(disciplinaSelecionada)}
        aria-label={`Editar ${disciplinaSelecionada.nome}`}
      >
        <Pencil size={15} aria-hidden="true" />
        <span>{disciplinaSelecionada.nome}</span>
      </button>
    ) : (
      disciplinaSelecionada.nome
    )
  ) : (
    title
  );

  const totalTopicosSelecionados = disciplinaSelecionada?.topicos.length ?? 0;
  const topicosPersistidos = disciplinaSelecionada
    ? disciplinaSelecionada.topicos.filter((topico) => !isTopicoTemporario(topico.id))
    : [];
  const topicosConcluidosSelecionados = topicosPersistidos.filter((topico) =>
    progresso?.has(topico.id)
  ).length;
  const percentualSelecionado = totalTopicosSelecionados > 0
    ? Math.round((topicosConcluidosSelecionados / totalTopicosSelecionados) * 100)
    : 0;
  const todosTopicosConcluidos =
    topicosPersistidos.length > 0 &&
    topicosConcluidosSelecionados === topicosPersistidos.length;
  const disciplinaConcluida = Boolean(disciplinaSelecionada?.concluida) || todosTopicosConcluidos;

  const voltarParaDisciplinas = () => {
    setDisciplinaAbertaId(null);
    setEditandoDisciplinaId(null);
    setEditandoTopicoId(null);
    setMostrarCores(false);
  };

  const handleModalClose = () => {
    if (isSaving || isDeleting) return;
    if (disciplinaSelecionada && !isEditable) {
      voltarParaDisciplinas();
      return;
    }
    onClose();
  };

  const modalSubtitle = disciplinaSelecionada ? (
    <div className={styles.modalSubtitleMeta}>
      {!isEditable && (
        <Button
          type="button"
          variant="ghost"
          size="sm"
          leftIcon={<ArrowLeft size={15} />}
          onClick={voltarParaDisciplinas}
        >
          Voltar para disciplinas
        </Button>
      )}
      {isEditable && !isCreateMode && onToggleConclusaoDisciplina && (
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className={`${styles.topicCompletionButton} ${
            disciplinaConcluida
              ? styles.topicCompletionButtonUndo
              : styles.topicCompletionButtonComplete
          }`}
          aria-pressed={disciplinaConcluida}
          aria-label={disciplinaConcluida ? 'Reabrir disciplina' : 'Concluir disciplina'}
          onClick={() =>
            onToggleConclusaoDisciplina(disciplinaSelecionada.id, !disciplinaConcluida)
          }
          leftIcon={disciplinaConcluida ? <Undo2 size={15} /> : <Check size={15} />}
        >
          {disciplinaConcluida ? 'Reabrir disciplina' : 'Concluir disciplina'}
        </Button>
      )}
      <Badge
        variant={disciplinaConcluida || percentualSelecionado >= 60 ? 'success' : 'secondary'}
        size="md"
        className={styles.discCount}
      >
        {isEditable
          ? totalTopicosSelecionados === 0 && disciplinaConcluida
            ? 'Concluída'
            : `${topicosConcluidosSelecionados} / ${totalTopicosSelecionados} assuntos (${percentualSelecionado}%)`
          : `${totalTopicosSelecionados} ${totalTopicosSelecionados === 1 ? 'assunto' : 'assuntos'}`}
      </Badge>
      {isEditable ? (
        <DisciplinaPesoSelector
          peso={disciplinaSelecionada.peso ?? 1}
          onChange={(peso) => handleAlterarDisciplina('peso', peso)}
          className={styles.modalWeightSelector}
          compact
        />
      ) : (
        <DisciplinaPesoButton
          peso={disciplinaSelecionada.peso ?? 1}
          className={styles.modalWeightBadge}
          title={`Peso atual: ${disciplinaSelecionada.peso ?? 1}x`}
        />
      )}
    </div>
  ) : (
    subtitle
  );

  const corDisciplinaSelecionada =
    disciplinaSelecionada?.cor ||
    obterCorDisponivel(disciplinasLocais, disciplinaSelecionada?.id);

  const disciplinaIcon = disciplinaSelecionada ? (
    <div className={styles.headerColorWrapper} ref={colorPickerContainerRef}>
      <button
        type="button"
        className={`${styles.colorBadgeButton} ${mostrarCores ? styles.colorBadgeButtonActive : ''}`}
        aria-label={isEditable ? 'Alterar cor da disciplina' : `Cor da disciplina: ${corDisciplinaSelecionada}`}
        title={isEditable ? 'Clique para alterar a cor da disciplina' : undefined}
        onClick={() => isEditable && setMostrarCores((prev) => !prev)}
        disabled={!isEditable}
      >
        <span
          className={styles.colorBadge}
          style={{ '--discipline-color': corDisciplinaSelecionada } as React.CSSProperties}
        />
      </button>

      {isEditable && mostrarCores && (
        <div
          className={styles.colorPopover}
          role="dialog"
          aria-labelledby="color-popover-title"
          aria-describedby="color-popover-description"
        >
          <div className={styles.colorPopoverHeader}>
            <div>
              <span className={styles.colorPopoverEyebrow}>Aparência</span>
              <span className={styles.colorPopoverTitle} id="color-popover-title">
                Cor da disciplina
              </span>
            </div>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              iconOnly
              onClick={() => setMostrarCores(false)}
              aria-label="Fechar"
            >
              <X size={20} />
            </Button>
          </div>
          <p className={styles.colorPopoverDescription} id="color-popover-description">
            Escolha uma cor para identificar esta disciplina com facilidade.
          </p>
          <div className={styles.selectedColorPreview}>
            <span
              className={styles.selectedColorSwatch}
              style={{ '--discipline-color': corDisciplinaSelecionada } as React.CSSProperties}
              aria-hidden="true"
            />
            <span>
              Cor atual <strong>{corDisciplinaSelecionada.toUpperCase()}</strong>
            </span>
          </div>
          <ColorPicker
            value={corDisciplinaSelecionada}
            onChange={(cor) => {
              handleAlterarDisciplina('cor', cor);
            }}
            usedColors={coresEmUso}
            compact
            label="Escolha uma cor"
          />
        </div>
      )}
    </div>
  ) : (
    icon
  );

  const iconBadgeStyle: React.CSSProperties | undefined = disciplinaSelecionada
    ? {
        width: '30px',
        height: '30px',
        marginTop: '0',
        borderRadius: '50%',
        backgroundColor: 'transparent',
        borderColor: 'transparent'
      }
    : undefined;

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleModalClose}
      title={modalTitle}
      icon={disciplinaIcon}
      iconBadgeStyle={iconBadgeStyle}
      maxWidth={maxWidth}
      isSaving={isSaving || isDeleting}
      footer={
        isEditable && disciplinaSelecionada ? (
          <div className={styles.modalFooter}>
            {!isCreateMode && (
              <div className={styles.modalFooterLeft}>
                <Button
                  type="button"
                  variant="danger"
                  size="md"
                  leftIcon={<Trash2 size={15} />}
                  loading={isDeleting}
                  disabled={isSaving || isDeleting}
                  onClick={() => void handleExcluirDisciplina(disciplinaSelecionada)}
                >
                  Excluir disciplina
                </Button>
              </div>
            )}
            <div className={styles.modalFooterRight}>
              <Button
                type="button"
                variant="secondary"
                size="md"
                disabled={isSaving || isDeleting}
                onClick={handleModalClose}
              >
                Cancelar
              </Button>
              <Button
                type="button"
                variant="primary"
                size="md"
                loading={isSaving}
                disabled={!nomeFinalValido || isSaving || isDeleting}
                onClick={handleSalvar}
              >
                Salvar
              </Button>
            </div>
          </div>
        ) : (
          extraFooterAction
        )
      }
    >
      <div className={styles.modalContentMeta}>
        {disciplinaSelecionada ? modalSubtitle : subtitle}
      </div>
      {disciplinaSelecionada ? (
        <div className={styles.topicosView}>
          {isEditable && (
            <div className={styles.formCadastro}>
              <div className={styles.topicField}>
                <div className={styles.topicFieldHeader}>
                  <label htmlFor="modal-novos-topicos">Cadastrar novos assuntos</label>
                  {novosTopicosTexto.trim() && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      iconOnly
                      className={styles.confirmTopicsButton}
                      onClick={handleAdicionarTopicos}
                      aria-label="Adicionar assuntos"
                      title="Adicionar assuntos"
                    >
                      <Check strokeWidth={2.5} />
                    </Button>
                  )}
                </div>
                <Textarea
                  id="modal-novos-topicos"
                  rows={4}
                  placeholder={'Digite um assunto por linha e todos serão cadastrados simultaneamente:\nPrincípios fundamentais\nDireitos e garantias\nOrganização do Estado'}
                  value={novosTopicosTexto}
                  error={erroTopicos || undefined}
                  onChange={(event) => {
                    setNovosTopicosTexto(event.target.value);
                    if (erroTopicos) setErroTopicos('');
                  }}
                />
              </div>

              <QuickAddInput
                className={styles.quickAddTopic}
                value={novoTopicoRapido}
                onValueChange={(value) => {
                  setNovoTopicoRapido(value);
                  if (erroTopicos) setErroTopicos('');
                }}
                onAdd={handleAdicionarTopicoRapido}
                placeholder="Adicionar um assunto... (Pressione Enter para salvar)"
                buttonTitle="Adicionar assunto (Enter)"
                buttonAriaLabel="Adicionar assunto"
                inputAriaLabel="Adicionar um assunto"
                maxLength={MAX_CARACTERES_TOPICO}
                disabled={isSaving || isDeleting}
              />
            </div>
          )}

          {disciplinaSelecionada.topicos.length === 0 ? (
            <p className={styles.emptyList}>Nenhum assunto cadastrado.</p>
          ) : (
            <ul className={styles.topicList}>
              {disciplinaSelecionada.topicos.map((topico, index) => {
                const isEditing = isEditable && editandoTopicoId === topico.id;
                const topicoNovo = isTopicoTemporario(topico.id);
                return (
                  <TopicRow
                    key={topico.id}
                    index={index}
                    name={topico.nome}
                    leading={
                      onToggleTopico ? (
                        <Checkbox
                          checked={Boolean(progresso?.has(topico.id))}
                          disabled={topicoNovo}
                          title={
                            topicoNovo
                              ? 'Salve a disciplina para marcar este assunto como concluído'
                              : undefined
                          }
                          aria-label={`Marcar assunto ${topico.nome} como concluído`}
                          onChange={(event) => {
                            if (topicoNovo) return;
                            onToggleTopico(topico.id, event.target.checked);
                          }}
                        />
                      ) : undefined
                    }
                    content={
                      isEditing ? (
                        <InlineTopicTextarea
                          value={nomeTopicoEditado}
                          maxLength={MAX_CARACTERES_TOPICO}
                          onChange={setNomeTopicoEditado}
                          onCommit={handleRenomearTopico}
                          onCancel={() => {
                            setNomeTopicoEditado('');
                            setEditandoTopicoId(null);
                          }}
                        />
                      ) : undefined
                    }
                    actions={
                      isEditable ? (
                        isEditing ? (
                          <Button
                            type="button"
                            variant="danger"
                            size="sm"
                            iconOnly
                            title="Excluir assunto"
                            aria-label="Excluir assunto"
                            onMouseDown={(event) => event.preventDefault()}
                            onClick={() => void handleExcluirTopico(topico)}
                            leftIcon={<Trash2 size={15} />}
                          />
                        ) : (
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            iconOnly
                            title="Editar assunto"
                            aria-label={`Editar assunto ${topico.nome}`}
                            onMouseDown={(event) => event.preventDefault()}
                            onClick={() => {
                              if (editandoTopicoId) {
                                handleRenomearTopico();
                              }
                              setEditandoTopicoId(topico.id);
                              setNomeTopicoEditado(topico.nome);
                            }}
                            leftIcon={<Pencil size={15} />}
                          />
                        )
                      ) : undefined
                    }
                  />
                );
              })}
            </ul>
          )}
        </div>
      ) : !isEditable ? (
        <div className={styles.disciplinasView}>
          {disciplinasLocais.length === 0 ? (
            <p className={styles.emptyList}>Nenhuma disciplina cadastrada.</p>
          ) : (
            <ul className={styles.disciplineList}>
              {disciplinasLocais.map((disciplina) => (
                <DisciplineRow
                  key={disciplina.id}
                  name={disciplina.nome}
                  color={disciplina.cor}
                  topicCount={disciplina.topicos.length}
                  topicButtonClassName={styles.modalTopicPill}
                  onClick={() => {
                    setDisciplinaAbertaId(disciplina.id);
                    setEditandoDisciplinaId(null);
                    setEditandoTopicoId(null);
                    setMostrarCores(false);
                  }}
                  peso={exibePeso ? disciplina.peso : undefined}
                  pesoContent={
                    exibePeso ? (
                      <DisciplinaPesoButton
                        peso={disciplina.peso}
                        className={styles.modalListWeightPill}
                        title={`Peso atual: ${disciplina.peso}x`}
                      />
                    ) : undefined
                  }
                />
              ))}
            </ul>
          )}
        </div>
      ) : null}
    </Modal>
  );
};
