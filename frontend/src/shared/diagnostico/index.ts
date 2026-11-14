/**
 * Diagnóstico de falhas não fatais do cliente.
 *
 * O produto proíbe `console.*` em código de aplicação: falhas de integração devem ser
 * visíveis na interface e registradas de forma inspecionável. Este módulo mantém um
 * anel limitado de eventos e notifica ouvintes interessados.
 */

interface FalhaDiagnostico {
  origem: string;
  detalhe: string;
  instante: string;
}

const LIMITE = 50;
const eventos: FalhaDiagnostico[] = [];
const ouvintes = new Set<(falha: FalhaDiagnostico) => void>();

function descrever(erro: unknown): string {
  if (erro instanceof Error) return erro.message;
  if (typeof erro === 'string') return erro;
  try {
    return JSON.stringify(erro);
  } catch {
    return String(erro);
  }
}

/**
 * Registra uma falha best-effort (ex.: falha ao gravar rascunho local, som do alarme).
 *
 * Use apenas para erros que já são tratados na UI ou que não impedem o fluxo; erros que
 * mudam o estado da seção devem ir para `SectionErrorCard`/`Toast`.
 */
export function registrarFalha(origem: string, erro: unknown): void {
  const falha: FalhaDiagnostico = {
    origem,
    detalhe: descrever(erro),
    instante: new Date().toISOString()
  };

  eventos.push(falha);
  if (eventos.length > LIMITE) eventos.shift();

  ouvintes.forEach((ouvinte) => {
    try {
      ouvinte(falha);
    } catch {
      // Um ouvinte com defeito não pode derrubar o fluxo que originou a falha.
    }
  });
}
