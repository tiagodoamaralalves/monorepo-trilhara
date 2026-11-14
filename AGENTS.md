# Regras do Projeto — Monorepo Trilhara

Este documento define as diretrizes obrigatórias para qualquer agente atuando neste repositório (`backend/` e `frontend/`), com foco em qualidade estrutural, reaproveitamento de componentes, higiene de código e prevenção de refatorações futuras.

---

## 1. Arquitetura e Visão Geral
- **Frontend (`frontend/`)**: React 18 + TypeScript + Vite 6 + CSS Modules + Vitest.
  - Design System compartilhado: `frontend/src/shared/design-system/` (importado via `@shared/design-system`).
  - Contratos de domínio: `frontend/src/shared/contracts/domainContracts.ts`.
  - Módulos de negócio: `frontend/src/modules/` (`estudos`, `tarefas`, `pomodoro`, `notas`).
- **Backend (`backend/`)**: Java 25 + Quarkus 3.40.1 + MongoDB 7 (repositórios BSON diretos).
  - Isolamento obrigatório por `usuarioId` em todas as queries e mutações.
  - Endpoints protegidos declarados em `backend/src/test/java/br/com/trilhara/security/RotasPrivadas.java`.

---

## 2. Design System e Reaproveitamento de UI (Frontend)
1. **Consultar o Design System antes de criar qualquer UI**:
   - Verifique `frontend/src/shared/design-system/index.ts` antes de criar componentes novos (`Button`, `Badge`, `Checkbox`, `IconButton`, `Input`, `QuickAddInput`, `Select`, `Modal`, `FormModal`, `FormActions`, `DisciplineLabel`, `DisciplineRow`, `TopicRow`, `CalendarView`, `ConfirmDialog`, `Toast`, `Tabs`, etc.).
   - Nunca recrie manualmente botões, caixas de seleção, inputs, badges, modais ou linhas de item quando houver equivalente no Design System.
2. **Generalizar em vez de duplicar**:
   - Se um mesmo padrão visual ou interativo aparecer em dois ou mais contextos (ex.: `QuickAddInput`, linhas de edição inline, seletores, cabeçalhos), extraia ou generalize um componente no Design System (`frontend/src/shared/design-system/`) mantendo API limpa e neutra ao domínio.
   - Prefira parametrizar um componente existente (ex.: `mode="create" | "edit"`) em vez de manter dois modais/formulários paralelos com estrutura quase idêntica.
3. **Tokens de Tema e CSS Modules**:
   - Use sempre CSS Modules (`*.module.css`) e tokens CSS do tema (`var(--surface-*)`, `var(--border*)`, `var(--text*)`, `var(--primary*)`, `var(--green*)`, `var(--red*)`, `var(--yellow*)`, `var(--radius-*)`, etc.).
   - Evite estilos inline extensos e não use cores hexadecimais hardcoded na UI (exceto definições de paleta/cor de disciplina).
4. **Layout, Overflow e Responsividade**:
   - Em containers flex com texto + ações à direita (ex.: `DisciplineRow`, `TopicRow`, cards), garanta `min-width: 0` no elemento textual e `flex: 0 0 auto` nas ações para evitar que textos longos quebrem o alinhamento ou empurrem botões para fora da linha.
   - Campos de edição inline de textos potencialmente longos devem expandir/quebrar linha sem truncamento horizontal injustificado.
5. **Acessibilidade e Semântica**:
   - Use `type="button"` em todo `<button>` que não envia formulário.
   - Defina `aria-label` claro em botões apenas com ícone, `aria-pressed` em toggles de estado e navegação completa por teclado (`Enter`, `Escape`, foco visível com `:focus-visible`).

---

## 3. Higiene de Código e Prevenção de Refators Futuros
1. **Zero Código Morto ao Refatorar ou Substituir**:
   - Ao substituir, unificar ou remover uma funcionalidade, exclua na mesma tarefa:
     - Arquivos/diretórios obsoletos;
     - Props, callbacks, estados, handlers e tipos que ficaram sem uso;
     - Imports não utilizados em `.ts`, `.tsx` e `.java`;
     - Classes CSS órfãs nos `*.module.css` alterados;
     - Endpoints, DTOs ou métodos de serviço sem consumidores.
2. **Sem Duplicação de Lógica**:
   - Extraia helpers puros e coesos para regras repetidas (ex.: cálculo de conclusão de disciplina/tópicos, validações, mapeamento de payload) em vez de repetir a mesma condição em filtro, card e modal.
   - Evite `eslint-disable` para contornar dependências de hooks: estabilize valores derivados com `useMemo`/`useCallback`.
3. **Padrões e Regras Estáticas no Backend (Java / SonarQube)**:
   - **Constantes para literais repetidos (`java:S1192`)**: nunca duplique strings literais (como nomes de campos BSON, chaves ou prefixos de rota) 3 ou mais vezes; defina constantes `private static final` seguindo o padrão `CAMPO_*` do repositório/classe.
   - Use Java `record` para DTOs e entidades imutáveis com Bean Validation (`@NotNull`, `@NotBlank`, `@Size`, `@Valid`) na borda REST.
   - Mantenha consistência de estado no backend: mutações relacionadas (ex.: concluir/reabrir disciplina, adicionar/desmarcar tópicos) devem manter as flags e coleções derivadas sincronizadas no servidor, não apenas no cliente.
4. **Sincronia entre Camadas**:
   - Alterou um DTO no backend? Atualize `frontend/src/shared/contracts/domainContracts.ts` e o serviço correspondente na mesma entrega.
   - Criou, removeu ou alterou rota REST privada no backend? Atualize `backend/src/test/java/br/com/trilhara/security/RotasPrivadas.java` (`TODAS_AS_ROTAS` e `casosDeclarados()`).

---

## 4. Higiene de Testes
- Ao mudar um fluxo ou substituir um componente, atualize ou remova testes que validavam o comportamento antigo — nunca deixe testes/mocks obsoletos.
- Escreva testes de UI focando comportamento observável pelo usuário (papéis ARIA, labels, textos e payloads enviados aos serviços) via Vitest + Testing Library.
- No frontend, garanta cobertura para serviços novos/alterados em `frontend/src/test/` (ex.: `estudosNormalizado.test.ts`).
- No backend, cubra novos endpoints e regras de negócio em testes de integração e garanta conformidade nos testes de contrato/segurança (`RotasPublicasContratoTest`, `RotasPrivadasClassificacaoTest`).

---

## 5. Checklist Obrigatório Antes de Encerrar a Tarefa
Execute e verifique **após a última edição**:

- **Frontend (`cd frontend`)**:
  - `npm run lint` (0 erros e 0 avisos)
  - `npm run typecheck` (0 erros)
  - `npm run knip` (verificar que nenhum arquivo, exportação ou dependência morta foi introduzida nos arquivos alterados)
  - `npm run build`
  - `npm test`
- **Backend (`cd backend`, com Java 25)**:
  - `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw test` (ou `RotasPublicasContratoTest,RotasPrivadasClassificacaoTest` + suíte completa quando MongoDB/Docker estiver ativo)
  - Conferir visualmente `git status` e `git diff` para garantir que não sobraram arquivos temporários, código morto ou literais repetidos.
