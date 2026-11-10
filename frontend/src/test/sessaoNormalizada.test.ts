import { describe, it, expect, vi, beforeEach } from 'vitest';
import { EstudosService } from '../modules/estudos/services/estudosService';
import * as apiModule from '../modules/estudos/services/api';

describe('FASE 3 - Frontend: Sessões Normalizadas e Estatísticas', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('deve registrar sessão enviando duracaoSegundos, dataSessao e Idempotency-Key', async () => {
    const requestSpy = vi.spyOn(apiModule, 'request').mockResolvedValueOnce({
      id: 'sessao-1',
      trilhaId: 'trilha-123',
      disciplinaId: 'disc-456',
      disciplinaNome: 'Português',
      disciplinaCor: '#4A6B82',
      disciplinaRemovida: false,
      dataSessao: '2026-11-09',
      duracaoSegundos: 95,
      questoes: 10,
      acertos: 8,
      erros: 2,
      percentualAcerto: 80,
      editavel: true,
      chaveIdempotencia: 'idemp-key-1',
      criadoEm: '2026-11-09T12:00:00Z',
      atualizadoEm: '2026-11-09T12:00:00Z'
    });

    const resposta = await EstudosService.registrarSessao(
      'trilha-123',
      {
        disciplinaId: 'disc-456',
        dataSessao: '2026-11-09',
        duracaoSegundos: 95,
        questoes: 10,
        acertos: 8
      },
      'idemp-key-1'
    );

    expect(requestSpy).toHaveBeenCalledWith('/trilhas/trilha-123/sessoes', {
      method: 'POST',
      headers: {
        'Idempotency-Key': 'idemp-key-1'
      },
      body: JSON.stringify({
        disciplinaId: 'disc-456',
        dataSessao: '2026-11-09',
        duracaoSegundos: 95,
        questoes: 10,
        acertos: 8,
        chaveIdempotencia: 'idemp-key-1'
      })
    });

    expect(resposta.id).toBe('sessao-1');
    expect(resposta.duracaoSegundos).toBe(95);
    expect(resposta.percentualAcerto).toBe(80);
    expect(resposta.editavel).toBe(true);
  });

  it('deve chamar PATCH para atualizar a sessão mais recente', async () => {
    const requestSpy = vi.spyOn(apiModule, 'request').mockResolvedValueOnce({
      id: 'sessao-1',
      trilhaId: 'trilha-123',
      disciplinaId: 'disc-456',
      duracaoSegundos: 180,
      questoes: 15,
      acertos: 14,
      editavel: true
    });

    const resposta = await EstudosService.atualizarSessao('trilha-123', 'sessao-1', {
      duracaoSegundos: 180,
      questoes: 15,
      acertos: 14
    });

    expect(requestSpy).toHaveBeenCalledWith('/trilhas/trilha-123/sessoes/sessao-1', {
      method: 'PATCH',
      body: JSON.stringify({
        duracaoSegundos: 180,
        questoes: 15,
        acertos: 14
      })
    });
    expect(resposta.duracaoSegundos).toBe(180);
  });

  it('deve chamar DELETE para excluir uma sessão individual', async () => {
    const requestSpy = vi.spyOn(apiModule, 'request').mockResolvedValueOnce(undefined);

    await EstudosService.excluirSessao('trilha-123', 'sessao-1');

    expect(requestSpy).toHaveBeenCalledWith('/trilhas/trilha-123/sessoes/sessao-1', {
      method: 'DELETE'
    });
  });

  it('deve chamar DELETE para apagar o histórico de uma disciplina removida', async () => {
    const requestSpy = vi.spyOn(apiModule, 'request').mockResolvedValueOnce({
      sessoesRemovidas: 3,
      totalSegundosRemovidos: 600
    });

    const resposta = await EstudosService.excluirSessoesDisciplina('trilha-123', 'disc-456');

    expect(requestSpy).toHaveBeenCalledWith('/trilhas/trilha-123/disciplinas/disc-456/sessoes', {
      method: 'DELETE'
    });
    expect(resposta.sessoesRemovidas).toBe(3);
    expect(resposta.totalSegundosRemovidos).toBe(600);
  });

  it('deve listar sessões paginado com metadados e conversão para tipo Sessao', async () => {
    const mockResposta = {
      itens: [
        {
          id: 'sessao-1',
          trilhaId: 'trilha-123',
          disciplinaId: 'disc-456',
          disciplinaNome: 'Português',
          disciplinaCor: '#4A6B82',
          disciplinaRemovida: false,
          dataSessao: '2026-11-09',
          duracaoSegundos: 120,
          questoes: 5,
          acertos: 4,
          erros: 1,
          percentualAcerto: 80,
          editavel: true,
          chaveIdempotencia: 'idemp-1',
          criadoEm: '2026-11-09T10:00:00Z',
          atualizadoEm: '2026-11-09T10:00:00Z'
        }
      ],
      pagina: 1,
      tamanho: 20,
      totalItens: 1,
      totalPaginas: 1
    };

    vi.spyOn(apiModule, 'request').mockResolvedValueOnce(mockResposta);

    const pag = await EstudosService.listarSessoesPaginado('trilha-123', {
      pagina: 1,
      tamanho: 20
    });

    expect(pag.pagina).toBe(1);
    expect(pag.total).toBe(1);
    expect(pag.itens[0].id).toBe('sessao-1');
    expect(pag.itens[0].duracaoSegundos).toBe(120);
    expect(pag.itens[0].minutos).toBe(2);
    expect(pag.itens[0].editavel).toBe(true);
  });

  it('deve consultar estatísticas com período e retornar totais agregados e streak', async () => {
    const mockStats = {
      totalSegundos: 3600,
      totalSessoes: 4,
      totalQuestoes: 50,
      totalAcertos: 40,
      totalErros: 10,
      percentualAcertoGlobal: 80,
      porDisciplina: [
        {
          disciplinaId: 'd1',
          nome: 'Português',
          cor: '#4A6B82',
          disciplinaRemovida: false,
          totalSegundos: 3600,
          totalSessoes: 4,
          totalQuestoes: 50,
          totalAcertos: 40,
          totalErros: 10,
          percentualAcerto: 80
        }
      ],
      serieTemporal: [
        {
          data: '2026-11-09',
          totalSegundos: 3600,
          totalQuestoes: 50,
          totalAcertos: 40
        }
      ],
      streakDias: 5
    };

    const requestSpy = vi.spyOn(apiModule, 'request').mockResolvedValueOnce(mockStats);

    const stats = await EstudosService.obterEstatisticas('trilha-123', { periodo: '7d' });

    expect(requestSpy).toHaveBeenCalledWith('/trilhas/trilha-123/estatisticas?periodo=7d');
    expect(stats.totalSegundos).toBe(3600);
    expect(stats.streakDias).toBe(5);
    expect(stats.percentualAcertoGlobal).toBe(80);
    expect(stats.porDisciplina[0].disciplinaRemovida).toBe(false);
  });
});
