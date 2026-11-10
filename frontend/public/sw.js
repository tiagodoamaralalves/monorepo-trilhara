/* Service Worker do Trilhara para Web Push (PWA Standalone & Desktop) */

const recentNotificationIds = new Set();

self.addEventListener('install', (event) => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('push', (event) => {
  let data = {};
  if (event.data) {
    try {
      data = event.data.json();
    } catch (e) {
      data = { body: event.data.text() };
    }
  }

  const lembreteId = data.lembreteId || data.id;
  if (lembreteId && recentNotificationIds.has(lembreteId)) {
    // Deduplicação local contra disparos repetidos
    return;
  }
  if (lembreteId) {
    recentNotificationIds.add(lembreteId);
    setTimeout(() => recentNotificationIds.delete(lembreteId), 60000);
  }

  const title = data.title || data.titulo || 'Trilhara — Lembrete de Tarefa';
  const tarefaId = data.tarefaId;
  const targetUrl = data.url || (tarefaId ? `/tarefas?tarefaId=${encodeURIComponent(tarefaId)}` : '/tarefas');

  const options = {
    body: data.body || data.mensagem || 'Você tem uma tarefa agendada.',
    icon: '/favicon.svg',
    badge: '/favicon.svg',
    tag: lembreteId || (tarefaId ? `tarefa-${tarefaId}` : 'trilhara-push'),
    data: {
      tarefaId,
      lembreteId,
      url: targetUrl
    },
    requireInteraction: false
  };

  event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();

  const data = event.notification.data || {};
  const targetUrl = data.url || (data.tarefaId ? `/tarefas?tarefaId=${encodeURIComponent(data.tarefaId)}` : '/tarefas');

  // Ação de clique foca aba existente ou abre URL da tarefa (sem efetuar finalização ou marcação de status)
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if ('focus' in client) {
          if (client.url && client.url.includes('/tarefas')) {
            client.focus();
            if ('navigate' in client && targetUrl) {
              return client.navigate(targetUrl);
            }
            return client;
          }
        }
      }
      if (self.clients.openWindow) {
        return self.clients.openWindow(targetUrl);
      }
    })
  );
});
