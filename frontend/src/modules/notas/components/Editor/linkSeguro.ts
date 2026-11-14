import { Mark } from '@tiptap/core';

/** Protocolos aceitos em links de nota. Qualquer outro descarta a marcação. */
const PROTOCOLOS_PERMITIDOS = ['http:', 'https:', 'mailto:'] as const;

/**
 * Extrai o protocolo de um `href` aceitando apenas o que é link navegável.
 *
 * Usa `URL` com base fictícia para normalizar `HTTPS://`, `//host` e caminhos relativos
 * (que herdam `https:` e são permitidos). `javascript:`, `data:`, `vbscript:` e variantes com
 * caracteres de controle no meio do esquema são rejeitados.
 */
export function protocoloDeLinkPermitido(href: unknown): boolean {
  if (typeof href !== 'string') return false;
  const valor = href.trim();
  if (!valor) return false;

  // Qualquer caractere de controle/quebra no href impede a navegação segura.
  // eslint-disable-next-line no-control-regex
  if (/[\u0000-\u001F\u007F]/.test(valor)) return false;

  let url: URL;
  try {
    url = new URL(valor, 'https://trilhara.invalid');
  } catch {
    return false;
  }

  // Link relativo/ancoragem: `https:` foi herdado da base fictícia e é seguro.
  return (PROTOCOLOS_PERMITIDOS as readonly string[]).includes(url.protocol);
}

/**
 * Mark `link` restrito do editor de Notas.
 *
 * Por que existe: o `tiptap-markdown` só registra os utilitários de serialização de link —
 * o mark em si não entra no schema, então `[texto](url)` seria descartado na leitura. Este mark
 * usa o mesmo nome canônico (`link`) para herdar esse serializador e adiciona a política que
 * falta:
 *
 * **allowlist de protocolos** (`http:`, `https:`, `mailto:`); qualquer outro `href` perde a
 * marcação e o texto permanece legível — cobre `javascript:`, `data:` e `vbscript:` vindos de
 * Markdown colado ou de `setContent`;
 * `rel="noopener noreferrer nofollow"` e `target="_blank"` **sempre** aplicados na
 * renderização, nunca herdados do documento de entrada;
 * colagem passa pelo schema do ProseMirror (`html: false` no `Markdown`), então nós e marcações
 * desconhecidas são descartados.
 *
 * Não declara `addStorage.markdown`: herdar o serializador do `tiptap-markdown` é o que
 * mantém `[texto](url)` intacto no Markdown salvo.
 */
export const LinkSeguro = Mark.create({
  name: 'link',

  inclusive: false,

  addAttributes() {
    return {
      href: {
        default: null,
        parseHTML: (element) => element.getAttribute('href'),
        renderHTML: (attributes) =>
          protocoloDeLinkPermitido(attributes.href) ? { href: attributes.href } : {}
      },
      target: {
        default: '_blank',
        parseHTML: () => '_blank',
        renderHTML: () => ({ target: '_blank' })
      },
      rel: {
        default: 'noopener noreferrer nofollow',
        parseHTML: () => 'noopener noreferrer nofollow',
        renderHTML: () => ({ rel: 'noopener noreferrer nofollow' })
      }
    };
  },

  parseHTML() {
    return [
      {
        tag: 'a[href]',
        getAttrs: (element) => {
          const href = (element as HTMLElement).getAttribute('href');
          return protocoloDeLinkPermitido(href) ? null : false;
        }
      }
    ];
  },

  renderHTML({ HTMLAttributes }) {
    const permitido = protocoloDeLinkPermitido(HTMLAttributes.href);
    if (!permitido) {
      // Sem `href` navegável: emite só o contêiner neutro, mantendo o texto.
      return ['a', {}, 0];
    }

    // `rel` e `target` são reescritos aqui, não herdados: um `rel="opener"` vindo do documento
    // de entrada nunca chega ao DOM.
    return [
      'a',
      {
        href: HTMLAttributes.href,
        target: '_blank',
        rel: 'noopener noreferrer nofollow'
      },
      0
    ];
  }
});
