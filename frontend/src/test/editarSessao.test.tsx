import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  ConfirmProvider,
  GlobalSessionModalProvider,
  ToastProvider
} from '../shared/design-system';
import { DashboardView } from '../modules/estudos/views/DashboardView';
import type { TrilhaDetalheDTO } from '../modules/estudos/types/trilhara';
import { EstudosService } from '../modules/estudos/services/estudosService';
import { ApiError } from '../shared/auth/api';

const trilha: TrilhaDetalheDTO = {
  id: 't-1',
  nome: 'Receita Federal',
  disciplinas: [{ id: 'd-1', nome: 'Português', cor: '#4A6B82', peso: 1, ordem: 0, topicos: [] }],
  topicosConcluidosIds: []
};

const estatisticas = {
  totalSegundos: 1800,
  totalSessoes: 1,
  totalQuestoes: 5,
  totalAcertos: 4,
  totalErros: 1,
  percentualAcertoGlobal: 80,
  porDisciplina: [
    {
      disciplinaId: 'd-1',
      nome: 'Português',
      cor: '#4A6B82',
      disciplinaRemovida: false,
      totalSegundos: 1800,
      totalSessoes: 1,
      totalQuestoes: 5,
      totalAcertos: 4,
      totalErros: 1,
      percentualAcerto: 80
    }
  ],
  serieTemporal: [{ data: '2026-11-12', totalSegundos: 1800, totalQuestoes: 5, totalAcertos: 4 }],
  streakDias: 1,
  granularidade: 'DIA'
};

function pagina(editavel: boolean) {
  return {
    itens: [
      {
        id: 's-1',
        trilhaId: 't-1',
        disciplinaId: 'd-1',
        disciplinaNome: 'Português',
        disciplinaCor: '#4A6B82',
        disciplinaRemovida: false,
        dataSessao: '2026-11-12',
        duracaoSegundos: 1800,
        questoes: 5,
        acertos: 4,
        erros: 1,
        percentualAcerto: 80,
        editavel
      }
    ],
    pagina: 1,
    tamanho: 20,
    total: 1,
    totalPaginas: 1
  };
}

function renderizar() {
  return render(
    <ToastProvider>
      <ConfirmProvider>
        <GlobalSessionModalProvider>
          <DashboardView trilha={trilha} onIrParaDisciplinas={vi.fn()} />
        </GlobalSessionModalProvider>
      </ConfirmProvider>
    </ToastProvider>
  );
}

describe('Edição da sessão mais recente', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('só oferece edição na sessão marcada como editavel', async () => {
    vi.spyOn(EstudosService, 'obterEstatisticas').mockResolvedValue(estatisticas as never);
    vi.spyOn(EstudosService, 'listarSessoesPaginado').mockResolvedValue(pagina(false) as never);

    renderizar();

    await screen.findByRole('button', { name: /Excluir/i });
    expect(screen.queryByRole('button', { name: /^Editar/i })).not.toBeInTheDocument();
  });

  it('PATCH 409 LATEST_SESSION_ONLY avisa o usuário e recarrega o histórico', async () => {
    const statsSpy = vi.spyOn(EstudosService, 'obterEstatisticas').mockResolvedValue(estatisticas as never);
    const listaSpy = vi.spyOn(EstudosService, 'listarSessoesPaginado').mockResolvedValue(pagina(true) as never);
    vi.spyOn(EstudosService, 'atualizarSessao').mockRejectedValue(
      new ApiError({
        status: 409,
        code: 'LATEST_SESSION_ONLY',
        detail: 'Apenas a sessão mais recente da trilha pode ser editada.'
      })
    );

    renderizar();

    const chamadasStats = statsSpy.mock.calls.length;
    const chamadasLista = listaSpy.mock.calls.length;

    await userEvent.click(await screen.findByRole('button', { name: /^Editar/i }));

    const horas = await screen.findByRole('spinbutton', { name: /Horas de estudo/i });
    await userEvent.clear(horas);
    await userEvent.type(horas, '2');

    await userEvent.click(screen.getByRole('button', { name: /Salvar alterações/i }));

    await waitFor(() => {
      expect(screen.getByText(/Apenas a sessão mais recente pode ser editada/i)).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(statsSpy.mock.calls.length).toBeGreaterThan(chamadasStats);
      expect(listaSpy.mock.calls.length).toBeGreaterThan(chamadasLista);
    });
  });
});
