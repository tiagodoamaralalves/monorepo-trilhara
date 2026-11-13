/* Service Worker do Trilhara para Web Push (PWA Standalone & Desktop) */

/**
 * Dedupe por `lembreteId`: a entrega externa pode repetir (Web Push não garante
 * exactly-once), então o SW guarda os últimos 100 ids já exibidos.
 */
const MAX_LEMBRETES_RECENTES = 100;
const recentNotificationIds = [];

self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

function jaExibido(lembreteId) {
  return recentNotificationIds.includes(lembreteId);
}

function registrarExibicao(lembreteId) {
  recentNotificationIds.push(lembreteId);
  while (recentNotificationIds.length > MAX_LEMBRETES_RECENTES) {
    recentNotificationIds.shift();
  }
}

self.addEventListener('push', (event) => {
  let data = {};
  if (event.data) {
    try {
      data = event.data.json();
    } catch (e) {
      data = { body: event.data.text() };
    }
  }

  const lembreteId = data.lembreteId;
  if (lembreteId && jaExibido(lembreteId)) {
    return;
  }
  if (lembreteId) {
    registrarExibicao(lembreteId);
  }

  const tarefaId = data.tarefaId;
  const targetUrl = data.url || (tarefaId ? `/tarefas/?tarefaId=${encodeURIComponent(tarefaId)}` : '/tarefas/');

  const options = {
    body: data.body || 'Você tem uma tarefa agendada.',
    icon: '/favicon.svg',
    badge: '/favicon.svg',
    tag: lembreteId || (tarefaId ? `tarefa-${tarefaId}` : 'trilhara-push'),
    data: { tarefaId, lembreteId, url: targetUrl },
    requireInteraction: false
  };

  event.waitUntil(
    self.registration.showNotification(data.titulo || data.title || 'Trilhara — Lembrete de Tarefa', options)
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();

  const data = event.notification.data || {};
  const targetUrl = data.url || (data.tarefaId ? `/tarefas/?tarefaId=${encodeURIComponent(data.tarefaId)}` : '/tarefas/');
  const mesmaOrigem = new URL(targetUrl, self.location.origin).origin === self.location.origin;

  // Foca uma aba existente da MESMA origem ou abre a URL. Nunca conclui a tarefa: o clique é
  // apenas navegação (D-36d).
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if ('focus' in client && client.url && new URL(client.url).origin === self.location.origin) {
          if (mesmaOrigem && 'navigate' in client) {
            // `navigate` pode devolver undefined em alguns navegadores: nunca deixar estourar.
            Promise.resolve(client.navigate(targetUrl)).catch(() => undefined);
          }
          return client.focus();
        }
      }
      if (self.clients.openWindow) {
        return self.clients.openWindow(targetUrl);
      }
      return undefined;
    })
  );
});
