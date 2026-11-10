# Trilhara — Plataforma Integrada de Estudos para Concursos Públicos

O **Trilhara** é um ecossistema completo para estudantes e concurseiros de alta performance, estruturado em quatro módulos integrados:
- **Estudos** (`/estudos`): Gestão de trilhas, editais, disciplinas ponderadas, cronogramas adaptativos de estudo e registro granular de sessões.
- **Tarefas** (`/tarefas`): Listas de afazeres, recorrências automáticas com janelas operacionais, subtarefas e notificações Web Push.
- **Pomodoro** (`/pomodoro`): Cronômetro de foco, descansos curtos/longos, ciclos progressivos e espelhamento em tempo real com blocos e tarefas diárias.
- **Notas** (`/notas`): Cadernos digitais estruturados em seções e páginas em Markdown, busca em texto pleno (`$text`) e autosave resiliente.

---

## 1. Arquitetura e Tecnologias

### Backend
- **Java 21 / 25** com **Quarkus 3.40.1** (REST Reactive, Hibernate Validator, SmallRye OpenAPI).
- **MongoDB 7**: 19 coleções normalizadas com índices compostos, texto pleno e unicidade parcial.
- **Segurança**: Autenticação stateless JWT (HS256) com tolerância de clock-skew (30s), Refresh Tokens com rotação e detecção de reuso, hash PBKDF2-SHA512 com pepper, isolamento estrito multi-inquilino (`usuarioId`), autorização anti-IDOR e exclusão de conta em lote com reconciliação assíncrona.
- **Web Push**: RFC 8030 e VAPID (RFC 8292) com janela útil de entrega de 15 minutos e descarte inteligente de lembretes expirados fora do prazo.

### Frontend
- **React 18** e **TypeScript** com **Vite 6**.
- **Design System Trilhara**: Alto contraste e ergonomia ocular (Modo Escuro Obsidian e Modo Claro Daylight Slate), Acessibilidade WCAG 2.2 AA (Focus trap, alvos mínimos 24x24px desktop e 40x40px touch, `prefers-reduced-motion` e `role="timer"` / `aria-live="off"`).
- **PWA & Service Worker**: Servido com cabeçalhos de revalidação imediata (`Cache-Control: no-cache, no-store, must-revalidate`), permitindo notificações Web Push inclusive no iOS 16.4+ (modo standalone).

### Infraestrutura
- **Caddy 2**: Proxy reverso de alta performance, terminação TLS / Auto-HTTPS, roteamento por subdomínios e cabeçalhos de segurança HTTP.
- **Docker & Docker Compose**: Orquestração integrada para ambiente local e produção.

---

## 2. Pré-requisitos de Ambiente

- **Java JDK 21+** (recomendado Java 21 LTS ou OpenJDK 25)
- **Node.js 20+** e **npm 10+**
- **Docker** e **Docker Compose** (com MongoDB 7)
- **Caddy 2** (opcional para proxy local com HTTPS)

---

## 3. Configuração de Variáveis de Ambiente

Copie o modelo de ambiente e preencha as variáveis:
```bash
cp .env.example .env
```

Gere chaves criptográficas seguras:
```bash
# JWT Secret e Password Pepper
openssl rand -base64 48

# Chaves VAPID para Web Push (Node.js)
npx web-push generate-vapid-keys
```

Preencha no `.env`:
```env
AUTH_JWT_SECRET=<seu-jwt-secret-gerado>
AUTH_PASSWORD_PEPPER=<seu-pepper-gerado>
VAPID_PUBLIC_KEY=<sua-vapid-public-key>
VAPID_PRIVATE_KEY=<sua-vapid-private-key>
VAPID_SUBJECT=mailto:suporte@trilhara.com.br
MONGODB_URL=mongodb://localhost:27017/?minPoolSize=5&maxPoolSize=50
```

---

## 4. Execução em Desenvolvimento Local

### 1. Iniciar Banco de Dados (MongoDB 7)
```bash
docker compose up -d mongodb
```

### 2. Executar Backend (Quarkus Dev Mode com Hot-Reload)
```bash
cd backend
./mvnw quarkus:dev
```
- A API REST iniciará em `http://localhost:8080`.
- Documentação Swagger UI: `http://localhost:8080/q/swagger-ui`.
- Dev UI do Quarkus: `http://localhost:8080/q/dev-ui`.

### 3. Executar Frontend (Vite Dev Server)
```bash
cd frontend
npm install
npm run dev
```
- Aplicação disponível em `http://localhost:5173`.

---

## 5. Testes Automatizados e Qualidade de Código

### Backend (JUnit 5 + REST Assured + Testcontainers/DevServices)
Para rodar a suíte completa de testes unitários e de integração:
```bash
cd backend
./mvnw test
```
*Garante validações de autorização por proprietário (anti-IDOR), exclusão atômica de contas em 18 coleções filhas, engine de recorrência, cronogramas, sessões e push.*

### Frontend (Vitest + React Testing Library + jsdom)
Para rodar os testes de componentes, hooks e serviços:
```bash
cd frontend
npm test -- --run
```

Para validar a tipagem estrita do TypeScript e compilação do bundle de produção:
```bash
cd frontend
npm run build
```

---

## 6. Benchmark de Performance e Índices (`explain("executionStats")`)

A FASE 11 inclui um teste sintético opt-in que injeta carga representativa (2.190 sessões/ano, trilha com 2.000 tópicos, cronograma de 8.784 blocos e 300 páginas de notas em Markdown) e audita o plano de execução das consultas críticas no MongoDB 7:

```bash
cd backend
./mvnw test -Dtest=MongoExplainBenchmarkTest -Dbenchmark=true
```

Resultados avaliados pelo benchmark:
- **Blocos de Cronograma**: Confirma estágio `IXSCAN` no índice composto `idx_blocos_usuario_data_concluido` com alta seletividade (`docsExamined <= 24`).
- **Paginação de Sessões**: Confirma estágio `IXSCAN` no índice composto `idx_sessoes_usuario_trilha_data_criado_id` com exame restrito ao limite de página (`docsExamined <= 20`).
- **Disciplinas Ordenadas**: Confirma estágio `IXSCAN` utilizando `idx_disciplinas_usuario_trilha_ordem`.
- **Busca Textual em Notas**: Confirma utilização do índice de texto (`$text`) do MongoDB 7 com prefixo estrito `(usuarioId, deletedAt)`.
- **Tarefas de Hoje**: Confirma utilização de `idx_tarefas_usuario_deleted_concluida_vencimento`.

---

## 7. Diretrizes de Acessibilidade (WCAG 2.2 AA)

- **Focus Trap & Restauração de Foco**: Todos os modais (`Modal`, `FormModal`, `ConfirmDialog`, `GlobalSessionModal`) prendem o foco via tecla Tab, fecham com tecla Escape (bloqueado quando salvando) e devolvem o foco ao disparador.
- **Cronômetros**: Dígitos numéricos utilizam `role="timer"` e `aria-live="off"` para não sobrecarregar leitores de tela a cada segundo; transições de ciclo e badges utilizam `aria-live="polite"`.
- **Alvos de Toque (SC 2.5.8)**: Alvo mínimo de 24x24px no desktop e 40x40px em telas sensíveis ao toque (`@media (pointer: coarse)`).
- **Sensibilidade a Movimento**: Desativação e redução de animações e transições sob `@media (prefers-reduced-motion: reduce)`.
