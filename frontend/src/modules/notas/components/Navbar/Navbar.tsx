import React from 'react';
import { AppSidebar, DisciplineLabel } from '@shared/design-system';
import { UsuarioAutenticado } from '@shared/auth';
import { Caderno, Secao } from '../../types/nota';
import { CadernoContextPod } from './CadernoContextPod';
import { SecaoTabs } from './SecaoTabs';

export interface NavbarProps {
  cadernos: Caderno[];
  cadernoAtivoId?: string;
  secoes: Secao[];
  secaoAtivaId?: string;
  usuario?: UsuarioAutenticado | null;
  onSelecionarCaderno: (id: string) => void;
  onSelecionarSecao: (id: string) => void;
  onAbrirNovoCaderno: () => void;
  onAbrirEditarCaderno: () => void;
  onExcluirCaderno: () => void;
  onAbrirNovaSecao: () => void;
  onAbrirEditarSecao: (secao: Secao) => void;
  onExcluirSecao: (secao: Secao) => void;
  onAbrirNovaNota?: () => void;
  onAbrirImportarTrilha?: () => void;
  onSincronizarTrilha?: () => void;
  onAbrirBusca?: () => void;
  onAbrirLixeira?: () => void;
  onAbrirLogin?: () => void;
  onLogout?: () => void;
  onAbrirConta?: () => void;
  onCollapsedChange?: (collapsed: boolean) => void;
}

export const Navbar: React.FC<NavbarProps> = ({
  cadernos,
  cadernoAtivoId,
  secoes,
  secaoAtivaId,
  usuario,
  onSelecionarCaderno,
  onSelecionarSecao,
  onAbrirNovoCaderno,
  onAbrirEditarCaderno,
  onExcluirCaderno,
  onAbrirNovaSecao,
  onAbrirEditarSecao,
  onExcluirSecao,
  onAbrirNovaNota,
  onAbrirImportarTrilha,
  onSincronizarTrilha,
  onAbrirBusca,
  onAbrirLixeira,
  onAbrirLogin,
  onLogout,
  onAbrirConta,
  onCollapsedChange
}) => {
  const cadernoAtivo = cadernos.find((c) => c.id === cadernoAtivoId);

  return (
    <AppSidebar<string>
      brand={{
        title: 'Trilhara',
        subtitle: cadernoAtivo ? (
          <DisciplineLabel name={cadernoAtivo.titulo} color={cadernoAtivo.cor} />
        ) : (
          'Cadernos'
        ),
        logoSrc: '/favicon.svg',
        titleAttr: 'Cadernos Trilhara'
      }}
      user={
        usuario
          ? {
              nome: usuario.nome,
              email: usuario.email
            }
          : null
      }
      onLogout={onLogout}
      onLogin={onAbrirLogin}
      onOpenAccount={onAbrirConta}
      contextSlot={
        <div>
          <CadernoContextPod
            cadernos={cadernos}
            cadernoAtivoId={cadernoAtivoId}
            onSelecionarCaderno={onSelecionarCaderno}
            onAbrirNovoCaderno={onAbrirNovoCaderno}
            onAbrirEditarCaderno={onAbrirEditarCaderno}
            onExcluirCaderno={onExcluirCaderno}
            onAbrirNovaNota={onAbrirNovaNota}
            onAbrirImportarTrilha={onAbrirImportarTrilha}
            onSincronizarTrilha={onSincronizarTrilha}
            onAbrirBusca={onAbrirBusca}
            onAbrirLixeira={onAbrirLixeira}
          />

          {cadernoAtivo && secoes.length > 0 && (
            <SecaoTabs
              secoes={secoes}
              secaoAtivaId={secaoAtivaId}
              onSelecionarSecao={onSelecionarSecao}
              onAbrirNovaSecao={onAbrirNovaSecao}
              onAbrirEditarSecao={onAbrirEditarSecao}
              onExcluirSecao={onExcluirSecao}
            />
          )}
        </div>
      }
      items={[]}
      onCollapsedChange={onCollapsedChange}
    />
  );
};
