import { readFileSync } from 'fs';
import { resolve } from 'path';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { Trash2, Pencil } from 'lucide-react';
import { Button } from '@shared/design-system';
import { TarefaAgendaItem } from '@shared/agenda/TarefaAgendaItem';
import { TaskModal } from '../modules/tarefas/components/TaskModal/TaskModal';
import { SecaoTabs } from '../modules/notas/components/Navbar/SecaoTabs';
import { ListaPaginas } from '../modules/notas/components/ListaPaginas/ListaPaginas';

const buttonCss = readFileSync(
  resolve(__dirname, '../shared/design-system/Button/Button.module.css'),
  'utf-8'
);

describe('Padronização de botões de exclusão e ícones de ação', () => {
  it('botão de texto danger tem realce vermelho em repouso e vermelho sólido no hover', () => {
    expect(buttonCss).toMatch(
      /\.danger\s*\{\s*background:\s*var\(--red-bg\);\s*color:\s*var\(--red-text\);\s*border-color:\s*var\(--red-border\);/
    );
    expect(buttonCss).toMatch(
      /\.danger:hover:not\(:disabled\)\s*\{\s*background:\s*var\(--red\);\s*border-color:\s*var\(--red\);\s*color:\s*var\(--primary-contrast\);/
    );
  });

  it('botão iconOnly danger é cinza em repouso e usa realce vermelho suave no hover', () => {
    expect(buttonCss).toMatch(
      /\.btn\.danger\.iconOnly:not\(\.selected\)\s*\{\s*border-color:\s*transparent;\s*background:\s*transparent;\s*color:\s*var\(--text-mid\);/
    );
    expect(buttonCss).toMatch(
      /\.btn\.danger\.iconOnly:not\(\.selected\):hover:not\(:disabled\)\s*\{\s*border-color:\s*var\(--red-border\);\s*background:\s*var\(--red-bg\);\s*color:\s*var\(--red-text\);/
    );
  });

  it('botões iconOnly sm padronizam tamanho do SVG via .icon e .label', () => {
    expect(buttonCss).toMatch(
      /\.sm\.iconOnly \.icon svg,\s*\.sm\.iconOnly \.label svg\s*\{\s*width:\s*16px;\s*height:\s*16px;/
    );
  });

  it('Button iconOnly renderiza classes padronizadas para lápis e lixeira', () => {
    render(
      <div>
        <Button type="button" variant="ghost" size="sm" iconOnly aria-label="Editar">
          <Pencil size={15} />
        </Button>
        <Button type="button" variant="danger" size="sm" iconOnly aria-label="Excluir">
          <Trash2 size={15} />
        </Button>
      </div>
    );

    const editBtn = screen.getByRole('button', { name: 'Editar' });
    const deleteBtn = screen.getByRole('button', { name: 'Excluir' });

    expect(editBtn.className).toMatch(/ghost/);
    expect(editBtn.className).toMatch(/sm/);
    expect(editBtn.className).toMatch(/iconOnly/);

    expect(deleteBtn.className).toMatch(/danger/);
    expect(deleteBtn.className).toMatch(/sm/);
    expect(deleteBtn.className).toMatch(/iconOnly/);
  });

  it('lixeira da tarefa na agenda usa variant danger sm iconOnly', () => {
    render(
      <TarefaAgendaItem
        tarefa={{
          id: 't-1',
          titulo: 'Revisar Direito Constitucional',
          concluida: false,
          prioridade: 'MEDIA'
        }}
        onAlternar={vi.fn()}
        onExcluir={vi.fn()}
      />
    );

    const deleteBtn = screen.getByRole('button', {
      name: 'Excluir tarefa Revisar Direito Constitucional'
    });
    expect(deleteBtn.className).toMatch(/danger/);
    expect(deleteBtn.className).toMatch(/sm/);
    expect(deleteBtn.className).toMatch(/iconOnly/);
  });

  it('TaskModal usa botão danger md com texto e lixeira danger sm iconOnly nas subtarefas', () => {
    render(
      <TaskModal
        isOpen
        onClose={vi.fn()}
        onSalvar={vi.fn().mockResolvedValue(true)}
        onExcluir={vi.fn()}
        tarefaParaEditar={{
          id: 't-2',
          titulo: 'Tarefa de teste',
          concluida: false,
          prioridade: 'MEDIA',
          subtarefas: [{ id: 's-1', titulo: 'Subtarefa 1', concluida: false }]
        }}
      />
    );

    const excluirTarefaBtn = screen.getByRole('button', { name: 'Excluir tarefa' });
    expect(excluirTarefaBtn.className).toMatch(/danger/);
    expect(excluirTarefaBtn.className).toMatch(/md/);

    const editarSubBtn = screen.getByRole('button', { name: 'Editar subtarefa Subtarefa 1' });
    expect(editarSubBtn.className).toMatch(/ghost/);
    expect(editarSubBtn.className).toMatch(/sm/);
    expect(editarSubBtn.className).toMatch(/iconOnly/);

    const removerSubBtn = screen.getByRole('button', { name: 'Remover subtarefa Subtarefa 1' });
    expect(removerSubBtn.className).toMatch(/danger/);
    expect(removerSubBtn.className).toMatch(/sm/);
    expect(removerSubBtn.className).toMatch(/iconOnly/);
  });

  it('módulo Notas usa Button padronizado para editar/excluir seção e excluir página', () => {
    render(
      <div>
        <SecaoTabs
          secoes={[
            {
              id: 'sec-1',
              cadernoId: 'cad-1',
              titulo: 'Resumo',
              ordem: 1,
              totalPaginas: 1,
              criadoEm: '2026-11-01T10:00:00Z',
              atualizadoEm: '2026-11-01T10:00:00Z'
            }
          ]}
          secaoAtivaId="sec-1"
          onSelecionarSecao={vi.fn()}
          onAbrirNovaSecao={vi.fn()}
          onAbrirEditarSecao={vi.fn()}
          onExcluirSecao={vi.fn()}
        />
        <ListaPaginas
          secaoNome="Resumo"
          notas={[
            {
              id: 'n-1',
              secaoId: 'sec-1',
              cadernoId: 'cad-1',
              titulo: 'Aula 01',
              ordem: 1,
              criadoEm: '2026-11-01T10:00:00Z',
              atualizadoEm: '2026-11-01T10:00:00Z'
            }
          ]}
          onSelecionarNota={vi.fn()}
          onNovaPagina={vi.fn()}
          onExcluirNota={vi.fn()}
        />
      </div>
    );

    const editarSecaoBtn = screen.getByRole('button', { name: 'Editar seção' });
    expect(editarSecaoBtn.className).toMatch(/ghost/);
    expect(editarSecaoBtn.className).toMatch(/sm/);
    expect(editarSecaoBtn.className).toMatch(/iconOnly/);

    const excluirSecaoBtn = screen.getByRole('button', { name: 'Excluir seção' });
    expect(excluirSecaoBtn.className).toMatch(/danger/);
    expect(excluirSecaoBtn.className).toMatch(/sm/);
    expect(excluirSecaoBtn.className).toMatch(/iconOnly/);

    const excluirPaginaBtn = screen.getByRole('button', { name: 'Excluir página Aula 01' });
    expect(excluirPaginaBtn.className).toMatch(/danger/);
    expect(excluirPaginaBtn.className).toMatch(/sm/);
    expect(excluirPaginaBtn.className).toMatch(/iconOnly/);
  });
});
