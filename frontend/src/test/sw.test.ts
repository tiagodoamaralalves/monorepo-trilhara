import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import fs from 'fs';
import path from 'path';

/**
 * R11 — teste do próprio `public/sw.js` em ambiente simulado (D-36d).
 *
 * O arquivo é lido do disco e avaliado em um contexto com `self` mockado, exatamente como o
 * navegador faria: assim testamos dedupe e o clique sem depender de um SW real.
 */
type Listener = (event: unknown) => void;

function carregarServiceWorker() {
  const caminho = path.resolve(__dirname, '../../public/sw.js');
  const codigo = fs.readFileSync(caminho, 'utf8');

  const listeners = new Map<string, Listener>();
  const selfMock = {
    location: { origin: 'https://tarefas.trilhara.app' },
    addEventListener: (tipo: string, cb: Listener) => listeners.set(tipo, cb),
    skipWaiting: vi.fn(),
    clients: {
      claim: vi.fn(),
      matchAll: vi.fn().mockResolvedValue([]),
      openWindow: vi.fn().mockResolvedValue(undefined)
    },
    registration: { showNotification: vi.fn().mockResolvedValue(undefined) }
  };

  new Function('self', codigo)(selfMock);
  return { listeners, selfMock, codigo };
}

function eventoPush(payload: unknown) {
  let aguardado: Promise<unknown> = Promise.resolve();
  const event = {
    data: { json: () => payload, text: () => JSON.stringify(payload) },
    waitUntil: (p: Promise<unknown>) => {
      aguardado = p;
    }
  };
  return { event, concluido: () => aguardado };
}

describe('sw.js — dedupe e clique (R11)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('exibe a notificação uma única vez por lembreteId', async () => {
    const { listeners, selfMock } = carregarServiceWorker();
    const push = listeners.get('push')!;

    const primeiro = eventoPush({ lembreteId: 'lembrete-1', tarefaId: 't1', titulo: 'Estudar' });
    push(primeiro.event);
    await primeiro.concluido();

    const segundo = eventoPush({ lembreteId: 'lembrete-1', tarefaId: 't1', titulo: 'Estudar' });
    push(segundo.event);
    await segundo.concluido();

    expect(selfMock.registration.showNotification).toHaveBeenCalledTimes(1);
    expect(selfMock.registration.showNotification).toHaveBeenCalledWith(
      'Estudar',
      expect.objectContaining({ tag: 'lembrete-1' })
    );
  });

  it('exibe lembretes distintos normalmente', async () => {
    const { listeners, selfMock } = carregarServiceWorker();
    const push = listeners.get('push')!;

    for (const id of ['a', 'b', 'c']) {
      const e = eventoPush({ lembreteId: id, tarefaId: 't1', titulo: 'Tarefa' });
      push(e.event);
      await e.concluido();
    }

    expect(selfMock.registration.showNotification).toHaveBeenCalledTimes(3);
  });

  it('notificationclick usa a URL do payload e nunca chama API de conclusão', async () => {
    const { listeners, selfMock, codigo } = carregarServiceWorker();

    let aguardado: Promise<unknown> = Promise.resolve();
    const event = {
      notification: {
        close: vi.fn(),
        data: {
          tarefaId: 't1',
          lembreteId: 'l1',
          url: 'https://tarefas.trilhara.app/?tarefaId=t1'
        }
      },
      waitUntil: (p: Promise<unknown>) => {
        aguardado = p;
      }
    };
    listeners.get('notificationclick')!(event);
    await aguardado;

    expect(event.notification.close).toHaveBeenCalled();
    expect(selfMock.clients.openWindow).toHaveBeenCalledWith('https://tarefas.trilhara.app/?tarefaId=t1');

    // O SW nunca deve chamar fetch: o clique é navegação, não conclusão da tarefa.
    expect(codigo).not.toMatch(/\bfetch\s*\(/);
  });

  it('foca aba existente da mesma origem em vez de abrir outra', async () => {
    const { listeners, selfMock } = carregarServiceWorker();
    const cliente = { url: 'https://tarefas.trilhara.app/', focus: vi.fn(), navigate: vi.fn() };
    selfMock.clients.matchAll.mockResolvedValue([cliente]);

    let aguardado: Promise<unknown> = Promise.resolve();
    listeners.get('notificationclick')!({
      notification: { close: vi.fn(), data: { url: 'https://tarefas.trilhara.app/?tarefaId=t9' } },
      waitUntil: (p: Promise<unknown>) => {
        aguardado = p;
      }
    });
    await aguardado;

    expect(cliente.focus).toHaveBeenCalled();
    expect(selfMock.clients.openWindow).not.toHaveBeenCalled();
  });
});

