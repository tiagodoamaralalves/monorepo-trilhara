import {
  ToastProvider,
  ConfirmProvider,
  GlobalSessionModalProvider,
  GlobalSessionModal
} from '@shared/design-system';
import { AuthProvider } from '@shared/auth';
import { StudyTimerProvider } from '@shared/timers/estudoTimer';
import { AppRouter } from './AppRouter';

export function App() {
  return (
    <ToastProvider>
      <ConfirmProvider>
        <AuthProvider>
          <StudyTimerProvider>
            <GlobalSessionModalProvider>
              <AppRouter />
              <GlobalSessionModal />
            </GlobalSessionModalProvider>
          </StudyTimerProvider>
        </AuthProvider>
      </ConfirmProvider>
    </ToastProvider>
  );
}


