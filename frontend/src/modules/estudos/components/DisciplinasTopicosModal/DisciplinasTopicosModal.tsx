import React, { useEffect, useMemo, useRef, useState } from 'react';
import { ArrowLeft, BookOpen, Check, Pencil, Trash2, Undo2, X } from 'lucide-react';
import {
  Button,
  Badge,
  Checkbox,
  ColorPicker,
  DisciplineRow,
  Input,
  Modal,
  Textarea,
  TopicRow,
  useConfirm
} from '@shared/design-system';
import { Disciplina, ItemProgresso, Topico } from '../../types/trilhara';
import { obterCorDisponivel, obterCoresEmUso } from '../../utils/coresDisciplina';
import { DisciplinaPesoSelector } from '../DisciplinaPesoSelector/DisciplinaPesoSelector';
import { DisciplinaPesoButton } from '../DisciplinaPesoButton/DisciplinaPesoButton';
import styles from './DisciplinasTopicosModal.module.css';

function gerarId(prefixo: string): string {
  return `${prefixo}_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
}

export interface DisciplinasTopicosModalProps {
  isOpen: boolean;
  title: string;
  subtitle?: string;
  icon?: React.ReactNode;
  disciplinas: Disciplina[];
  initialDisciplinaId?: string | null;
  initialEditing?: boolean;
  creatingDiscipline?: boolean;
  progresso?: Record<string, ItemProgresso>;
  /** Se true ou se onChange for fornecido, permite adicionar, renomear e excluir disciplinas/assuntos */
  editable?: boolean;
  onChange?: (disciplinas: Disciplina[]) => void;
  onSaveNewDiscipline?: (disciplina: Disciplina) => void;
  savingNewDiscipline?: boolean;
  onSaveDiscipline?: (disciplina: Disciplina) => void;
  savingDiscipline?: boolean;
  onDeleteDiscipline?: (disciplina: Disciplina) => void;
  deletingDiscipline?: boolean;
  onToggleTopico?: (topicoId: string, concluido: boolean) => void;
  onToggleTopicos?: (disciplinaId: string, concluido: boolean) => void;
  onClose: () => void;
  /** Ações extras no rodapé (ex: botão "Usar essa trilha") */
  extraFooterAction?: React.ReactNode;
  /** Se deve mostrar o peso da disciplina no modo visualização (padrão: true se readonly) */
  mostrarPeso?: boolean;
  maxWidth?: 'sm' | 'md' | 'lg' | 'xl';
}

export const DisciplinasTopicosModal: React.FC<DisciplinasTopicosModalProps> = ({
  isOpen,
  title,
  subtitle = 'Disciplinas cadastradas',
  icon = <BookOpen size={22} />,
  disciplinas,
  initialDisciplinaId = null,
  initialEditing = false,
  creatingDiscipline = false,
  progresso,
  editable = false,
  onChange,
  onSaveNewDiscipline,
  savingNewDiscipline = false,
  onSaveDiscipline,
  savingDiscipline = false,
  onDeleteDiscipline,
  deletingDiscipline = false,
  onToggleTopico,
  onToggleTopicos,
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
  const [editandoDisciplinaId, setEditandoDisciplinaId] = useState<string | null>(null);
  const [nomeDisciplinaEditado, setNomeDisciplinaEditado] = useState('');
  const [editandoTopicoId, setEditandoTopicoId] = useState<string | null>(null);
  const [nomeTopicoEditado, setNomeTopicoEditado] = useState('');
  const [mostrarCores, setMostrarCores] = useState(false);
  const colorPickerContainerRef = useRef<HTMLDivElement>(null);

  const prevIsOpenRef = useRef(isOpen);
  const prevInitialIdRef = useRef(initialDisciplinaId);

  // Sincroniza rascunho local ao abrir ou trocar de disciplina
  useEffect(() => {
    const justOpened = isOpen && !prevIsOpenRef.current;
    const switchedDisciplina = isOpen && initialDisciplinaId !== prevInitialIdRef.current;

    if (justOpened || switchedDisciplina) {
      setDisciplinasLocais(disciplinas);
      setDisciplinaAbertaId(initialDisciplinaId);
      setEditandoDisciplinaId(
        creatingDiscipline
          ? initialDisciplinaId
          : initialEditing && initialDisciplinaId
            ? initialDisciplinaId
            : null
      );
      const disciplinaInicial = disciplinas.find((item) => item.id === initialDisciplinaId);
      setNomeDisciplinaEditado(disciplinaInicial?.nome ?? '');
      setNovosTopicosTexto('');
      setEditandoTopicoId(null);
      setNomeTopicoEditado('');
      setMostrarCores(false);
    } else if (!isOpen && prevIsOpenRef.current) {
      setDisciplinaAbertaId(null);
      setNovosTopicosTexto('');
      setEditandoDisciplinaId(null);
      setEditandoTopicoId(null);
      setNomeTopicoEditado('');
      setMostrarCores(false);
    }

    prevIsOpenRef.current = isOpen;
    prevInitialIdRef.current = initialDisciplinaId;
  }, [isOpen, initialDisciplinaId, initialEditing, creatingDiscipline, disciplinas]);

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
    () => obterCoresEmUso(disciplinasLocais, disciplinaSelecionada?.id),
    [disciplinasLocais, disciplinaSelecionada?.id]
  );

  const isEditable = Boolean(editable && (onChange || onSaveDiscipline || onSaveNewDiscipline));
  const exibePeso = mostrarPeso ?? !isEditable;

  const handleRenomearDisciplina = () => {
    const valor = nomeDisciplinaEditado.trim();
    if (!editandoDisciplinaId || !valor) {
      if (creatingDiscipline) return;
      setEditandoDisciplinaId(null);
      return;
    }
    setDisciplinasLocais((prev) =>
      prev.map((item) => (item.id === editandoDisciplinaId ? { ...item, nome: valor } : item))
    );
    if (!creatingDiscipline) setEditandoDisciplinaId(null);
  };

  const handleAlterarDisciplina = (campo: 'cor' | 'peso', valor: string | number) => {
    if (!disciplinaAbertaId) return;
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
    } else if (onChange) {
      onChange(disciplinasLocais.filter((item) => item.id !== disciplina.id));
      if (disciplinaAbertaId === disciplina.id) {
        setDisciplinaAbertaId(null);
      }
    }
  };

  const handleAdicionarTopicos = () => {
    if (!disciplinaAbertaId) return;
    const nomes = novosTopicosTexto.split(/\r?\n/).map((item) => item.trim()).filter(Boolean);
    if (nomes.length === 0) return;

    setDisciplinasLocais((prev) =>
      prev.map((disciplina) =>
        disciplina.id === disciplinaAbertaId
          ? {
              ...disciplina,
              topicos: [
                ...disciplina.topicos,
                ...nomes.map((nome) => ({ id: gerarId('topico'), nome }))
              ]
            }
          : disciplina
      )
    );
    setNovosTopicosTexto('');
  };

  const handleRenomearTopico = () => {
    if (!disciplinaAbertaId) return;
    const valor = nomeTopicoEditado.trim();
    if (!editandoTopicoId || !valor) {
      setEditandoTopicoId(null);
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
  };

  const handleExcluirTopico = async (topico: Topico) => {
    if (!disciplinaAbertaId || !disciplinaSelecionada) return;
    const ok = await confirm({
      title: 'Excluir assunto?',
      description: `O assunto "${topico.nome}" será removido.`,
      confirmText: 'Excluir',
      variant: 'danger'
    });
    if (!ok) return;

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
    (creatingDiscipline || editandoDisciplinaId === disciplinaSelecionada?.id);

  const startEditingDiscipline = (disciplina: Disciplina) => {
    if (!isEditable) return;
    setEditandoDisciplinaId(disciplina.id);
    setNomeDisciplinaEditado(disciplina.nome);
  };

  const nomeFinalAtual = (
    isEditingSelectedTitle ? nomeDisciplinaEditado : disciplinaSelecionada?.nome || ''
  ).trim();
  const nomeFinalValido = Boolean(nomeFinalAtual);

  const isSaving = creatingDiscipline ? Boolean(savingNewDiscipline) : Boolean(savingDiscipline);
  const isDeleting = Boolean(deletingDiscipline);

  const handleSalvar = () => {
    if (!disciplinaSelecionada || !nomeFinalValido) return;

    let topicosAtuais = disciplinaSelecionada.topicos;
    if (editandoTopicoId && nomeTopicoEditado.trim()) {
      topicosAtuais = topicosAtuais.map((t) =>
        t.id === editandoTopicoId ? { ...t, nome: nomeTopicoEditado.trim() } : t
      );
    }

    const pendentes = novosTopicosTexto
      .split(/\r?\n/)
      .map((item) => item.trim())
      .filter(Boolean)
      .map((nome) => ({ id: gerarId('topico'), nome }));

    const disciplinaFinal: Disciplina = {
      ...disciplinaSelecionada,
      nome: nomeFinalAtual,
      topicos: [...topicosAtuais, ...pendentes]
    };

    setNovosTopicosTexto('');
    setEditandoTopicoId(null);

    if (creatingDiscipline) {
      onSaveNewDiscipline?.(disciplinaFinal);
    } else {
      if (onSaveDiscipline) {
        onSaveDiscipline(disciplinaFinal);
      } else if (onChange) {
        const disciplinasFinais = disciplinasLocais.map((d) =>
          d.id === disciplinaFinal.id ? disciplinaFinal : d
        );
        onChange(disciplinasFinais);
      }
    }
  };

  const modalTitle = disciplinaSelecionada ? (
    isEditingSelectedTitle ? (
      <Input
        className={styles.titleEditInput}
        value={nomeDisciplinaEditado}
        placeholder={creatingDiscipline ? 'Digite o nome da disciplina' : undefined}
        maxLength={100}
        aria-label="Nome da disciplina"
        onChange={(event) => setNomeDisciplinaEditado(event.target.value)}
        onBlur={handleRenomearDisciplina}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.preventDefault();
            event.currentTarget.blur();
          } else if (event.key === 'Escape') {
            event.preventDefault();
            if (!creatingDiscipline) {
              setNomeDisciplinaEditado(disciplinaSelecionada.nome);
              setEditandoDisciplinaId(null);
            }
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
  const topicosConcluidosSelecionados = disciplinaSelecionada
    ? disciplinaSelecionada.topicos.filter((topico) => progresso?.[topico.id]?.done).length
    : 0;
  const percentualSelecionado = totalTopicosSelecionados > 0
    ? Math.round((topicosConcluidosSelecionados / totalTopicosSelecionados) * 100)
    : 0;
  const todosTopicosConcluidos =
    totalTopicosSelecionados > 0 &&
    topicosConcluidosSelecionados === totalTopicosSelecionados;
  const voltarParaDisciplinas = () => {
    setDisciplinaAbertaId(null);
    setEditandoDisciplinaId(null);
    setEditandoTopicoId(null);
    setMostrarCores(false);
  };
  const handleModalClose = () => {
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
      {isEditable && totalTopicosSelecionados > 0 && (
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className={`${styles.topicCompletionButton} ${
            todosTopicosConcluidos
              ? styles.topicCompletionButtonUndo
              : styles.topicCompletionButtonComplete
          }`}
          aria-pressed={todosTopicosConcluidos}
          aria-label={todosTopicosConcluidos ? 'Desmarcar todos' : 'Marcar todos'}
          onClick={() =>
            onToggleTopicos?.(disciplinaSelecionada.id, !todosTopicosConcluidos)
          }
          leftIcon={todosTopicosConcluidos ? <Undo2 size={15} /> : <Check size={15} />}
        >
          {todosTopicosConcluidos ? 'Desmarcar todos' : 'Marcar todos'}
        </Button>
      )}
      <Badge
        variant={percentualSelecionado >= 60 ? 'success' : 'secondary'}
        size="md"
        className={styles.discCount}
      >
        {isEditable
          ? `${topicosConcluidosSelecionados} / ${totalTopicosSelecionados} assuntos (${percentualSelecionado}%)`
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
      footer={
        isEditable && disciplinaSelecionada ? (
          <div className={styles.modalFooter}>
            <div className={styles.modalFooterLeft}>
              {!creatingDiscipline && (
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
              )}
            </div>
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
                  onChange={(event) => setNovosTopicosTexto(event.target.value)}
                />
              </div>
            </div>
          )}

          {disciplinaSelecionada.topicos.length === 0 ? (
            <p className={styles.emptyList}>Nenhum assunto cadastrado.</p>
          ) : (
            <>
              <ul className={styles.topicList}>
              {disciplinaSelecionada.topicos.map((topico, index) => {
                const isEditing = isEditable && editandoTopicoId === topico.id;
                return (
                  <TopicRow
                    key={topico.id}
                    index={index}
                    name={topico.nome}
                    leading={
                      onToggleTopico ? (
                        <Checkbox
                          checked={Boolean(progresso?.[topico.id]?.done)}
                          aria-label={`Marcar assunto ${topico.nome} como concluído`}
                          onChange={(event) => onToggleTopico(topico.id, event.target.checked)}
                        />
                      ) : undefined
                    }
                    content={
                      isEditing ? (
                        <Input
                          className={styles.inlineEditInput}
                          value={nomeTopicoEditado}
                          aria-label="Nome do assunto"
                          maxLength={500}
                          onChange={(event) => setNomeTopicoEditado(event.target.value)}
                          onBlur={handleRenomearTopico}
                          onKeyDown={(event) => {
                            if (event.key === 'Enter') {
                              event.preventDefault();
                              event.currentTarget.blur();
                            } else if (event.key === 'Escape') {
                              event.preventDefault();
                              setNomeTopicoEditado('');
                              setEditandoTopicoId(null);
                            }
                          }}
                          onClick={(event) => event.stopPropagation()}
                        />
                      ) : isEditable ? (
                        <button
                          type="button"
                          className={styles.topicNameButton}
                          onClick={() => {
                            setEditandoTopicoId(topico.id);
                            setNomeTopicoEditado(topico.nome);
                          }}
                        >
                          {topico.nome}
                        </button>
                      ) : undefined
                    }
                    actions={
                      isEditable ? (
                        <Button
                          type="button"
                          variant="danger"
                          size="sm"
                          iconOnly
                          title="Excluir assunto"
                          aria-label="Excluir assunto"
                          onClick={() => void handleExcluirTopico(topico)}
                          leftIcon={<Trash2 size={15} />}
                        />
                      ) : undefined
                    }
                  />
                );
              })}
              </ul>
            </>
          )}

        </div>
      ) : (
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
      )}
    </Modal>
  );
};
