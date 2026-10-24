import { ThemeProvider, CssBaseline } from '@mui/material';
import { appTheme } from './theme/theme';
import { AuthProvider } from './providers/AuthProvider';
import { AppRouter } from '../routes/index';

export default function App() {
  return (
    <ThemeProvider theme={appTheme}>
      <CssBaseline />
      <AuthProvider>
        <AppRouter />
      </AuthProvider>
    </ThemeProvider>
  );
}
