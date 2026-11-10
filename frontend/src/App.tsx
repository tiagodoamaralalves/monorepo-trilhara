import {
  ToastProvider,
  ConfirmProvider,
  GlobalSessionModalProvider,
  GlobalSessionModal
} from '@shared/design-system';
import { AuthProvider } from '@shared/auth';
import { AppRouter } from './AppRouter';

export function App() {
  return (
    <ToastProvider>
      <ConfirmProvider>
        <AuthProvider>
          <GlobalSessionModalProvider>
            <AppRouter />
            <GlobalSessionModal />
          </GlobalSessionModalProvider>
        </AuthProvider>
      </ConfirmProvider>
    </ToastProvider>
  );
}

