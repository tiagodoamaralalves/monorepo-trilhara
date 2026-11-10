const TRILHA_ATIVA_KEY = 'trilhara_estudos_ativa_id';

export const EstudosStorage = {
  obterTrilhaAtiva(): string | null {
    try {
      return window.localStorage.getItem(TRILHA_ATIVA_KEY);
    } catch {
      throw new Error('Não foi possível ler a trilha ativa do armazenamento local.');
    }
  },

  salvarTrilhaAtiva(id: string): void {
    try {
      window.localStorage.setItem(TRILHA_ATIVA_KEY, id);
    } catch {
      throw new Error('Não foi possível salvar a trilha ativa no armazenamento local.');
    }
  }
};
