import { lazy, Suspense } from 'react';
import { BrowserRouter } from 'react-router';
import { ThemeProvider, CssBaseline, Box } from '@mui/material';
import { AuthProvider } from './providers/AuthProvider';
import { AppRouter } from '../routes/index';
import { appTheme, color } from './theme/theme';
import { LoadingView } from '../shared/components/StatusViews';

// The design preview is a separate chunk: production journeys never load its
// fixtures, preview-only screens or the React Flow graph code.
const PreviewApp = lazy(() => import('./preview/PreviewApp'));

function PreviewFallback() {
    return <Box sx={{ minHeight: '100dvh', bgcolor: color.canvas100, p: 5 }}><LoadingView message="Loading design preview" variant="tiles" /></Box>;
}

export default function App() {
    return <ThemeProvider theme={appTheme}><CssBaseline />
        <BrowserRouter><AuthProvider>
            <AppRouter preview={<Suspense fallback={<PreviewFallback />}><PreviewApp /></Suspense>} />
        </AuthProvider></BrowserRouter>
    </ThemeProvider>;
}
