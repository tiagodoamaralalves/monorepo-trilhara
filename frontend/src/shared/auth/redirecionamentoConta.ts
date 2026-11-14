/** Mensagem normativa exibida após o aceite da exclusão. */
export const MENSAGEM_CONTA_EXCLUIDA =
  'Sua conta está sendo excluída. Isso pode levar alguns minutos.';

/** Parâmetro de URL usado para sinalizar a tela pública que a conta foi excluída. */
const PARAMETRO_CONTA_EXCLUIDA = 'conta';
const VALOR_CONTA_EXCLUIDA = 'excluida';

/** Redireciona para a tela pública do módulo atual sinalizando a exclusão em andamento. */
export function redirecionarAposExclusaoDeConta(): void {
  if (typeof window === 'undefined') return;
  const { pathname, search } = window.location;
  const url = new URL(window.location.href);
  url.pathname = pathname;
  url.search = search;
  url.hash = '';
  url.searchParams.set(PARAMETRO_CONTA_EXCLUIDA, VALOR_CONTA_EXCLUIDA);
  window.location.assign(`${url.pathname}${url.search}`);
}

/** Lê a mensagem de conta excluída da URL; `null` quando o parâmetro não está presente. */
export function lerMensagemContaExcluida(): string | null {
  if (typeof window === 'undefined') return null;
  const params = new URLSearchParams(window.location.search);
  return params.get(PARAMETRO_CONTA_EXCLUIDA) === VALOR_CONTA_EXCLUIDA
    ? MENSAGEM_CONTA_EXCLUIDA
    : null;
}

/** Remove o parâmetro da URL para que a mensagem não reapareça ao recarregar. */
export function limparParametroContaExcluida(): void {
  if (typeof window === 'undefined') return;
  const url = new URL(window.location.href);
  if (!url.searchParams.has(PARAMETRO_CONTA_EXCLUIDA)) return;
  url.searchParams.delete(PARAMETRO_CONTA_EXCLUIDA);
  const novo = `${url.pathname}${url.searchParams.toString() ? `?${url.searchParams}` : ''}`;
  window.history.replaceState(null, '', novo);
}
