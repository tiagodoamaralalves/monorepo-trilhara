import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { useState } from 'react';
import { ToastProvider } from '@shared/design-system';
import { ConfirmProvider } from '../shared/design-system/ConfirmDialog/ConfirmDialog';
import { DisciplinasView } from '../modules/estudos/views/DisciplinasView';
import { EstudosService } from '../modules/estudos/services/estudosService';
import type { TrilhaDetalheDTO } from '../modules/estudos/types/trilhara';

function montarTrilha(overrides: Partial<TrilhaDetalheDTO> = {}): TrilhaDetalheDTO {
  return {
    id: 'trilha-1',
    nome: 'Concurso TRF',
    topicosConcluidosIds: [],
    disciplinas: [
      {
        id: 'disc-1',
        trilhaId: 'trilha-1',
        nome: 'Direito Constitucional',
        cor: '#2563eb',
        peso: 2,
        ordem: 0,
        concluida: false,
        topicos: [
          { id: 'top-1', nome: 'Princípios Fundamentais', ordem: 1, concluido: false }
        ]
      },
      {
        id: 'disc-2',
        trilhaId: 'trilha-1',
        nome: 'Língua Portuguesa',
        cor: '#16a34a',
        peso: 3,
        ordem: 1,
        concluida: false,
        topicos: []
      }
    ],
    ...overrides
  };
}

async function renderDisciplinasView(
  trilhaInicial: TrilhaDetalheDTO,
  onTrilhaAtualizada?: (t: TrilhaDetalheDTO) => void
) {
  function Wrapper() {
    const [trilha, setTrilha] = useState(trilhaInicial);
    return (
      <DisciplinasView
        trilha={trilha}
        onTrilhaAtualizada={(atualizada) => {
          setTrilha(atualizada);
          onTrilhaAtualizada?.(atualizada);
        }}
      />
    );
  }

  const resultado = render(
    <ToastProvider>
      <ConfirmProvider>
        <Wrapper />
      </ConfirmProvider>
    </ToastProvider>
  );
  await waitFor(() => {
    expect(EstudosService.obterEstatisticas).toHaveBeenCalledWith(trilhaInicial.id);
  });
  return resultado;
}

describe('DisciplinasView — modal unificado, edição inline, quick add e conclusão', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(EstudosService, 'obterEstatisticas').mockResolvedValue({
      totalSegundos: 0,
      totalSessoes: 0,
      totalQuestoes: 0,
      totalAcertos: 0,
      totalErros: 0,
      percentualAcertoGlobal: 0,
      porDisciplina: [],
      serieTemporal: [],
      streakDias: 0,
      granularidade: 'DIA'
    });
  });

  it('abre o modal unificado em modo criação com nome em input, sem Excluir disciplina e sem Concluir disciplina', async () => {
    const trilha = montarTrilha();
    const criarSpy = vi.spyOn(EstudosService, 'criarDisciplina').mockResolvedValue({
      id: 'disc-3',
      trilhaId: 'trilha-1',
      nome: 'Direito Administrativo',
      cor: '#dc2626',
      peso: 1,
      ordem: 2,
      concluida: false,
      topicos: [
        { id: 'top-novo-1', nome: 'Atos Administrativos', ordem: 1, concluido: false },
        { id: 'top-novo-2', nome: 'Licitações', ordem: 2, concluido: false },
        { id: 'top-novo-3', nome: 'Poderes Administrativos', ordem: 3, concluido: false }
      ]
    });
    const buscarSpy = vi.spyOn(EstudosService, 'buscarTrilha').mockResolvedValue(trilha);
    const onAtualizada = vi.fn();

    await renderDisciplinasView(trilha, onAtualizada);

    fireEvent.click(screen.getByRole('button', { name: /Adicionar Disciplina/i }));

    const dialog = screen.getByRole('dialog');
    const nomeInput = within(dialog).getByLabelText('Nome da disciplina');
    expect(nomeInput).toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: 'Excluir disciplina' })).not.toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: 'Concluir disciplina' })).not.toBeInTheDocument();

    const salvarBtn = within(dialog).getByRole('button', { name: 'Salvar' });
    expect(salvarBtn).toBeDisabled();

    fireEvent.change(nomeInput, { target: { value: 'Direito Administrativo' } });
    expect(salvarBtn).not.toBeDisabled();

    fireEvent.change(within(dialog).getByLabelText('Cadastrar novos assuntos'), {
      target: { value: 'Atos Administrativos\nLicitações' }
    });

    const quickAddInput = within(dialog).getByLabelText('Adicionar um assunto');
    fireEvent.change(quickAddInput, { target: { value: 'Poderes Administrativos' } });
    fireEvent.submit(quickAddInput.closest('form')!);
    expect(within(dialog).getByText('Poderes Administrativos')).toBeInTheDocument();

    fireEvent.click(salvarBtn);

    await waitFor(() => {
      expect(criarSpy).toHaveBeenCalledWith(
        'trilha-1',
        expect.objectContaining({
          nome: 'Direito Administrativo',
          peso: 1,
          topicos: [
            { nome: 'Poderes Administrativos', ordem: 1 },
            { nome: 'Atos Administrativos', ordem: 2 },
            { nome: 'Licitações', ordem: 3 }
          ]
        })
      );
      expect(buscarSpy).toHaveBeenCalledWith('trilha-1');
      expect(onAtualizada).toHaveBeenCalledWith(trilha);
    });
  });

  it('abre o modal unificado em modo criação também no estado vazio da trilha', async () => {
    const trilhaVazia = montarTrilha({ disciplinas: [] });
    await renderDisciplinasView(trilhaVazia);

    fireEvent.click(screen.getByRole('button', { name: /Cadastrar Disciplinas/i }));

    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByLabelText('Nome da disciplina')).toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: 'Excluir disciplina' })).not.toBeInTheDocument();
  });

  it('exibe lápis e lixeira lado a lado na linha do assunto e só edita ao clicar no lápis', async () => {
    const trilha = montarTrilha();
    await renderDisciplinasView(trilha);

    fireEvent.click(screen.getByRole('button', { name: 'Editar disciplina Direito Constitucional' }));
    const dialog = screen.getByRole('dialog');

    // Clicar no texto do assunto NÃO abre edição
    fireEvent.click(within(dialog).getByText('Princípios Fundamentais'));
    expect(within(dialog).queryByLabelText('Nome do assunto')).not.toBeInTheDocument();

    // Lápis e lixeira ficam visíveis lado a lado antes de editar
    const editBtn = within(dialog).getByRole('button', { name: 'Editar assunto Princípios Fundamentais' });
    expect(editBtn).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Excluir assunto Princípios Fundamentais' })).toBeInTheDocument();

    fireEvent.click(editBtn);

    // Ao clicar no lápis, abre o textarea de edição, oculta o lápis e mantém a lixeira visível
    const editTextarea = within(dialog).getByLabelText('Nome do assunto') as HTMLTextAreaElement;
    expect(editTextarea.tagName).toBe('TEXTAREA');
    expect(within(dialog).getByRole('button', { name: 'Excluir assunto Princípios Fundamentais' })).toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: 'Editar assunto Princípios Fundamentais' })).not.toBeInTheDocument();

    fireEvent.change(editTextarea, { target: { value: 'Princípios Constitucionais' } });
    fireEvent.keyDown(editTextarea, { key: 'Enter' });

    // Ao concluir com Enter, salva e volta o botão de lápis ao lado da lixeira
    expect(within(dialog).queryByLabelText('Nome do assunto')).not.toBeInTheDocument();
    expect(within(dialog).getByText('Princípios Constitucionais')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Editar assunto Princípios Constitucionais' })).toBeInTheDocument();

    // Clica diretamente na lixeira da linha para excluir o assunto sem precisar abrir edição
    fireEvent.click(within(dialog).getByRole('button', { name: 'Excluir assunto Princípios Constitucionais' }));

    const confirmBtn = await screen.findByRole('button', { name: 'Excluir' });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(within(dialog).queryByText('Princípios Constitucionais')).not.toBeInTheDocument();
    });
    expect(within(dialog).getByText('Nenhum assunto cadastrado.')).toBeInTheDocument();
  });

  it('ao adicionar novos tópicos via textarea e quick add na edição, omite ids sintéticos e preserva ids persistidos', async () => {
    const trilha = montarTrilha();
    const atualizarSpy = vi.spyOn(EstudosService, 'atualizarDisciplina').mockResolvedValue({
      ...trilha.disciplinas[0],
      topicos: [
        { id: 'top-1', nome: 'Princípios Fundamentais', ordem: 1, concluido: false },
        { id: 'top-2', nome: 'Direitos Sociais', ordem: 2, concluido: false },
        { id: 'top-3', nome: 'Controle de Constitucionalidade', ordem: 3, concluido: false }
      ]
    });
    vi.spyOn(EstudosService, 'buscarTrilha').mockResolvedValue(trilha);

    await renderDisciplinasView(trilha);

    fireEvent.click(screen.getByRole('button', { name: 'Editar disciplina Direito Constitucional' }));
    const dialog = screen.getByRole('dialog');

    const quickAddInput = within(dialog).getByLabelText('Adicionar um assunto');
    fireEvent.change(quickAddInput, { target: { value: 'Direitos Sociais' } });
    fireEvent.submit(quickAddInput.closest('form')!);

    // Assunto recém-adicionado localmente ainda não foi salvo, então seu checkbox fica desabilitado.
    const checkboxNovo = within(dialog).getByRole('checkbox', {
      name: 'Marcar assunto Direitos Sociais como concluído'
    });
    expect(checkboxNovo).toBeDisabled();

    // Também suporta adicionar diretamente pelo textarea ao clicar em Salvar.
    const textarea = within(dialog).getByLabelText('Cadastrar novos assuntos');
    fireEvent.change(textarea, { target: { value: 'Controle de Constitucionalidade' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() => {
      expect(atualizarSpy).toHaveBeenCalledWith('trilha-1', 'disc-1', {
        nome: 'Direito Constitucional',
        cor: '#2563eb',
        peso: 2,
        topicos: [
          { id: 'top-1', nome: 'Princípios Fundamentais', ordem: 1 },
          { nome: 'Direitos Sociais', ordem: 2 },
          { nome: 'Controle de Constitucionalidade', ordem: 3 }
        ]
      });
    });

    const payloadEnviado = atualizarSpy.mock.calls[0][2];
    expect(payloadEnviado.topicos?.[1]).not.toHaveProperty('id');
    expect(payloadEnviado.topicos?.[2]).not.toHaveProperty('id');
  });

  it('permite concluir disciplina sem assuntos e reflete nos filtros Pendentes e Concluídos', async () => {
    const trilha = montarTrilha();
    const concluirSpy = vi.spyOn(EstudosService, 'atualizarConclusaoDisciplina').mockResolvedValue({
      ...trilha.disciplinas[1],
      concluida: true
    });

    await renderDisciplinasView(trilha);

    // Disciplina sem assuntos deve aparecer no filtro Pendentes
    fireEvent.click(screen.getByRole('button', { name: 'Pendentes' }));
    expect(screen.getByText('Língua Portuguesa')).toBeInTheDocument();
    expect(screen.getByText('Direito Constitucional')).toBeInTheDocument();

    // Concluir disciplina sem assuntos pelo modal
    fireEvent.click(screen.getByRole('button', { name: 'Editar disciplina Língua Portuguesa' }));
    const dialog = screen.getByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Concluir disciplina' }));

    await waitFor(() => {
      expect(concluirSpy).toHaveBeenCalledWith('trilha-1', 'disc-2', true);
    });
    expect(within(dialog).getByRole('button', { name: 'Reabrir disciplina' })).toBeInTheDocument();
    expect(within(dialog).getByText('Concluída')).toBeInTheDocument();

    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));

    // Toolbar reflete progresso ponderado (disc-2 peso 3 de 2+3 = 60%) e contadores mistos
    expect(
      screen.getByText(
        (_content, element) =>
          element?.tagName === 'SPAN' &&
          element.textContent?.replace(/\s+/g, ' ').trim() === '1/2 disciplinas · 0/1 assuntos (60%)'
      )
    ).toBeInTheDocument();

    // Em Pendentes só resta Direito Constitucional
    expect(screen.queryByText('Língua Portuguesa')).not.toBeInTheDocument();
    expect(screen.getByText('Direito Constitucional')).toBeInTheDocument();

    // Em Concluídos aparece Língua Portuguesa com badge Concluída
    fireEvent.click(screen.getByRole('button', { name: 'Concluídos' }));
    expect(screen.getByText('Língua Portuguesa')).toBeInTheDocument();
    expect(screen.queryByText('Direito Constitucional')).not.toBeInTheDocument();
  });

  it('concluir disciplina com assuntos marca todos os tópicos e desmarcar 1 tópico reabre para pendente', async () => {
    const trilha = montarTrilha();
    vi.spyOn(EstudosService, 'atualizarConclusaoDisciplina').mockResolvedValue({
      ...trilha.disciplinas[0],
      concluida: true
    });
    const desmarcarSpy = vi.spyOn(EstudosService, 'desmarcarTopicoConcluido').mockResolvedValue();

    await renderDisciplinasView(trilha);

    fireEvent.click(screen.getByRole('button', { name: 'Editar disciplina Direito Constitucional' }));
    const dialog = screen.getByRole('dialog');

    fireEvent.click(within(dialog).getByRole('button', { name: 'Concluir disciplina' }));

    const checkbox = within(dialog).getByRole('checkbox', {
      name: 'Marcar assunto Princípios Fundamentais como concluído'
    }) as HTMLInputElement;
    await waitFor(() => {
      expect(checkbox.checked).toBe(true);
    });
    expect(within(dialog).getByRole('button', { name: 'Reabrir disciplina' })).toBeInTheDocument();

    // Desmarcar o assunto reabre a disciplina
    fireEvent.click(checkbox);
    await waitFor(() => {
      expect(desmarcarSpy).toHaveBeenCalledWith('trilha-1', 'top-1');
    });
    expect(checkbox.checked).toBe(false);
    expect(within(dialog).getByRole('button', { name: 'Concluir disciplina' })).toBeInTheDocument();
  });

  it('valida localmente assunto com mais de 300 caracteres sem enviar payload inválido', async () => {
    const trilha = montarTrilha();
    const atualizarSpy = vi.spyOn(EstudosService, 'atualizarDisciplina');

    await renderDisciplinasView(trilha);

    fireEvent.click(screen.getByRole('button', { name: 'Editar disciplina Direito Constitucional' }));
    const dialog = screen.getByRole('dialog');

    fireEvent.change(within(dialog).getByLabelText('Cadastrar novos assuntos'), {
      target: { value: 'a'.repeat(301) }
    });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Adicionar assuntos' }));

    expect(within(dialog).getByText('Cada assunto deve ter no máximo 300 caracteres.')).toBeInTheDocument();
    expect(atualizarSpy).not.toHaveBeenCalled();
  });

  it('exibe cabeçalho fixo ao criar e editar disciplina com cor e nome editáveis no corpo do modal', async () => {
    const trilha = montarTrilha();
    const atualizarSpy = vi.spyOn(EstudosService, 'atualizarDisciplina').mockResolvedValue({
      ...trilha.disciplinas[0],
      nome: 'Direito Administrativo',
      cor: '#16a34a'
    });
    vi.spyOn(EstudosService, 'buscarTrilha').mockResolvedValue(trilha);

    await renderDisciplinasView(trilha);

    // Modo criação: cabeçalho "Nova disciplina" + campo de nome e cor no corpo
    fireEvent.click(screen.getByRole('button', { name: /Adicionar Disciplina/i }));
    const createDialog = screen.getByRole('dialog');
    expect(within(createDialog).getByRole('heading', { name: 'Nova disciplina' })).toBeInTheDocument();
    expect(within(createDialog).getByLabelText('Nome da disciplina')).toHaveValue('');
    expect(within(createDialog).getByRole('button', { name: 'Alterar cor da disciplina' })).toBeInTheDocument();
    fireEvent.click(within(createDialog).getByRole('button', { name: 'Cancelar' }));

    // Modo edição: cabeçalho "Editar disciplina" + input de nome já aberto e editável diretamente
    fireEvent.click(screen.getByRole('button', { name: 'Editar disciplina Direito Constitucional' }));
    const editDialog = screen.getByRole('dialog');

    expect(within(editDialog).getByRole('heading', { name: 'Editar disciplina' })).toBeInTheDocument();
    const nomeInput = within(editDialog).getByLabelText('Nome da disciplina');
    expect(nomeInput).toHaveValue('Direito Constitucional');

    fireEvent.change(nomeInput, { target: { value: 'Direito Administrativo' } });

    const colorTrigger = within(editDialog).getByRole('button', { name: 'Alterar cor da disciplina' });
    fireEvent.click(colorTrigger);
    expect(within(editDialog).getByRole('dialog', { name: 'Cor da disciplina' })).toBeInTheDocument();

    fireEvent.click(within(editDialog).getByRole('button', { name: 'Salvar' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Manter histórico' }));

    await waitFor(() => {
      expect(atualizarSpy).toHaveBeenCalledWith('trilha-1', 'disc-1', {
        nome: 'Direito Administrativo',
        cor: '#2563eb',
        peso: 2,
        topicos: [{ id: 'top-1', nome: 'Princípios Fundamentais', ordem: 1 }]
      });
    });
  });
});
