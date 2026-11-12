import { EditalResumo, TrilhaDetalheDTO } from '../types/trilhara';
import { EstudosService } from '../services/estudosService';

export const ESFERAS_DISPONIVEIS = [
  { value: '', label: 'Todas as esferas' },
  { value: 'FEDERAL', label: 'Federal' },
  { value: 'ESTADUAL', label: 'Estadual' }
];

export const ORDENACAO_OPCOES = [
  { value: 'prova_proxima', label: 'Prova mais próxima' },
  { value: 'alfabetica', label: 'Ordem alfabética (A–Z)' }
];

export const EDITAIS_VITRINE_FALLBACK: EditalResumo[] = [
  {
    id: 'edital-tcu-auditor-2026',
    nome: 'TCU — Auditor Federal de Controle Externo',
    area: 'Controle & Gestão',
    cargo: 'Auditor Federal (AUFC)',
    orgao: 'Tribunal de Contas da União',
    banca: 'FGV',
    esfera: 'FEDERAL',
    uf: 'DF',
    dataProva: '2027-02-14',
    disciplinas: [
      {
        id: 'd-ctrl-ext',
        nome: 'Controle Externo',
        cor: '#0ea5e9',
        peso: 5,
        topicos: [
          { id: 'tcu-1', nome: 'Competências Constitucionais do TCU (Art. 70 a 75 da CF)' },
          { id: 'tcu-2', nome: 'Lei Orgânica do TCU (Lei nº 8.443/1992)' },
          { id: 'tcu-3', nome: 'Tomada de Contas Especial e Julgamento de Contas' },
          { id: 'tcu-4', nome: 'Normas de Auditoria do TCU (NAT) e Fiscalização' }
        ]
      },
      {
        id: 'd-adm-pub',
        nome: 'Direito Administrativo',
        cor: '#10b981',
        peso: 5,
        topicos: [
          { id: 'tcu-5', nome: 'Nova Lei de Licitações e Contratos (Lei nº 14.133/2021)' },
          { id: 'tcu-6', nome: 'Agentes Públicos e Regime Jurídico (Lei nº 8.112/1990)' },
          { id: 'tcu-7', nome: 'Improbidade Administrativa e Controle da Administração' }
        ]
      },
      {
        id: 'd-afo',
        nome: 'Administração Financeira e Orçamentária (AFO)',
        cor: '#a855f7',
        peso: 4,
        topicos: [
          { id: 'tcu-8', nome: 'Plano Plurianual (PPA), LDO e LOA na Constituição' },
          { id: 'tcu-9', nome: 'Lei de Responsabilidade Fiscal (LC nº 101/2000)' },
          { id: 'tcu-10', nome: 'Estágios da Receita, Despesa Pública e Restos a Pagar' }
        ]
      },
      {
        id: 'd-dados',
        nome: 'Análise de Dados e Estatística',
        cor: '#f59e0b',
        peso: 4,
        topicos: [
          { id: 'tcu-11', nome: 'Inferência Estatística, Testes de Hipóteses e Regressão' },
          { id: 'tcu-12', nome: 'SQL, Modelagem Dimensional e Aprendizado Supervisionado' }
        ]
      }
    ]
  },
  {
    id: 'edital-rfb-auditor-2026',
    nome: 'Receita Federal — Auditor-Fiscal (RFB)',
    area: 'Fiscal',
    cargo: 'Auditor-Fiscal da Receita Federal',
    orgao: 'Ministério da Fazenda',
    banca: 'FGV',
    esfera: 'FEDERAL',
    uf: 'BR',
    dataProva: '2027-03-21',
    disciplinas: [
      {
        id: 'd-trib',
        nome: 'Direito Tributário',
        cor: '#0ea5e9',
        peso: 5,
        topicos: [
          { id: 'rfb-1', nome: 'Sistema Tributário Nacional e Limitações ao Poder de Tributar' },
          { id: 'rfb-2', nome: 'Obrigação, Crédito Tributário, Lançamento e Suspensão' },
          { id: 'rfb-3', nome: 'Reforma Tributária (IBS, CBS e Imposto Seletivo)' }
        ]
      },
      {
        id: 'd-aud',
        nome: 'Auditoria Fiscal e Contabilidade Avançada',
        cor: '#10b981',
        peso: 5,
        topicos: [
          { id: 'rfb-4', nome: 'NBC TA — Normas Brasileiras de Contabilidade de Auditoria' },
          { id: 'rfb-5', nome: 'Demonstrações Contábeis, CPCs e Análise de Balanços' }
        ]
      },
      {
        id: 'd-aduana',
        nome: 'Legislação Aduaneira e Comércio Internacional',
        cor: '#f59e0b',
        peso: 4,
        topicos: [
          { id: 'rfb-6', nome: 'Regulamento Aduaneiro e Regimes Aduaneiros Especiais' },
          { id: 'rfb-7', nome: 'Acordos da OMC, Valoração Aduaneira e Mercosul' }
        ]
      }
    ]
  },
  {
    id: 'edital-pf-agente-2026',
    nome: 'Polícia Federal — Agente de Polícia Federal',
    area: 'Policial',
    cargo: 'Agente de Polícia Federal',
    orgao: 'Departamento de Polícia Federal',
    banca: 'Cebraspe',
    esfera: 'FEDERAL',
    uf: 'BR',
    dataProva: '2027-01-24',
    disciplinas: [
      {
        id: 'pf-ti',
        nome: 'Tecnologia da Informação e Redes',
        cor: '#0ea5e9',
        peso: 5,
        topicos: [
          { id: 'pf-1', nome: 'Arquitetura TCP/IP, DNS, HTTP/HTTPS e Segurança de Redes' },
          { id: 'pf-2', nome: 'Banco de Dados Relacionais, Python e Ciência de Dados' },
          { id: 'pf-3', nome: 'Segurança da Informação, Criptografia e Forense Computacional' }
        ]
      },
      {
        id: 'pf-contab',
        nome: 'Contabilidade Geral',
        cor: '#10b981',
        peso: 4,
        topicos: [
          { id: 'pf-4', nome: 'Patrimônio, Escrituração, Balancete e DRE' },
          { id: 'pf-5', nome: 'Ativo Imobilizado, Intangível, Provisões e Depreciação' }
        ]
      },
      {
        id: 'pf-penal',
        nome: 'Direito Penal e Processual Penal',
        cor: '#f43f5e',
        peso: 4,
        topicos: [
          { id: 'pf-6', nome: 'Inquérito Policial, Prisões, Provas e Interceptação Telefônica' },
          { id: 'pf-7', nome: 'Crimes contra a Administração Pública e Lavagem de Dinheiro' }
        ]
      }
    ]
  }
];

const OBJECT_ID_REGEX = /^[a-f0-9]{24}$/i;

/**
 * Importa um edital do catálogo para a conta do usuário.
 * - Editais da API (ObjectId): usa o endpoint de importação; erros são propagados.
 * - Itens da vitrine local (fallback offline): cria a trilha a partir do snapshot local.
 */
export async function importarOuClonarEdital(edital: EditalResumo): Promise<TrilhaDetalheDTO> {
  if (OBJECT_ID_REGEX.test(edital.id)) {
    return await EstudosService.importarEdital(edital.id);
  }

  return await EstudosService.criarTrilha({
    nome: edital.nome,
    cargo: [edital.area, edital.cargo].filter(Boolean).join(' - ') || edital.cargo,
    banca: edital.banca,
    dataProva: edital.dataProva,
    disciplinasIniciais: (edital.disciplinas ?? []).map((disciplina) => ({
      nome: disciplina.nome,
      cor: disciplina.cor,
      peso: disciplina.peso,
      topicos: (disciplina.topicos ?? []).map((topico) => ({ nome: topico.nome }))
    }))
  });
}
