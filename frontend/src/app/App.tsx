import React, { useState } from 'react';
import {
  ThemeProvider,
  CssBaseline,
  Box,
  Button,
  TextField,
  Paper,
  Typography,
} from '@mui/material';
import { appTheme } from './theme/theme';
import { DashboardLayout } from '../layouts/DashboardLayout';
import { AuthLayout } from '../layouts/AuthLayout';
import { PageHeader } from '../shared/components/PageHeader';
import { ConfirmDialog } from '../shared/components/ConfirmDialog';
import { LoadingView, EmptyView, ErrorView } from '../shared/components/StatusViews';
import { useDebounce } from '../shared/hooks/useDebounce';
import { formatDate, formatNumber, formatDurationSeconds } from '../shared/utils/format';
import type { NavItem } from '../shared/components/SidebarNav';

const DEMO_NAV_ITEMS: NavItem[] = [
  { id: 'dashboard', label: 'Dashboard Showcase' },
  { id: 'auth-preview', label: 'Auth Layout Preview' },
];

export default function App() {
  const [selectedNav, setSelectedNav] = useState('dashboard');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogResult, setDialogResult] = useState<string | null>(null);

  // useDebounce demonstration
  const [inputValue, setInputValue] = useState('');
  const debouncedInput = useDebounce(inputValue, 400);

  // Formatting utilities demonstration with caller-supplied configs
  const currentDateFormatted = formatDate(new Date(), {
    locale: 'en-US',
    options: { dateStyle: 'full' },
  });

  const formattedScore = formatNumber(0.885, {
    locale: 'en-US',
    options: { style: 'percent', minimumFractionDigits: 1 },
  });

  const duration = formatDurationSeconds(3725); // 1h 2m 5s

  if (selectedNav === 'auth-preview') {
    return (
      <ThemeProvider theme={appTheme}>
        <CssBaseline />
        <AuthLayout title="CodeMastery Auth" subtitle="Presentation Showcase Mode">
          <Typography variant="body2" sx={{ mb: 2 }}>
            This is an isolated presentation view of <strong>AuthLayout</strong>.
          </Typography>
          <Button
            variant="contained"
            fullWidth
            onClick={() => setSelectedNav('dashboard')}
          >
            Back to Dashboard Showcase
          </Button>
        </AuthLayout>
      </ThemeProvider>
    );
  }

  return (
    <ThemeProvider theme={appTheme}>
      <CssBaseline />
      <DashboardLayout
        headerTitle="CodeMastery — Presentation Showcase"
        userDisplayName="Student (Demo Mode)"
        navItems={DEMO_NAV_ITEMS}
        selectedNavId={selectedNav}
        onNavSelect={setSelectedNav}
      >
        <PageHeader
          title="Component & Utility Showcase"
          subtitle="Independent presentation components and parameterized utilities (FE01)"
          actions={
            <Button
              variant="contained"
              color="primary"
              onClick={() => setDialogOpen(true)}
            >
              Open Confirm Dialog
            </Button>
          }
        />

        {/* Utilities Demo Section */}
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 3, mb: 4 }}>
          <Paper sx={{ p: 2.5 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 'bold', mb: 1 }}>
              useDebounce Hook Demo
            </Typography>
            <TextField
              size="small"
              fullWidth
              label="Type to test debounce..."
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              sx={{ mb: 2 }}
            />
            <Typography variant="body2" color="text.secondary">
              Real-time value: <strong>{inputValue || '(empty)'}</strong>
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Debounced value (400ms): <strong>{debouncedInput || '(empty)'}</strong>
            </Typography>
          </Paper>

          <Paper sx={{ p: 2.5 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 'bold', mb: 1 }}>
              Parameterized Format Utilities Demo
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Formatted Date (en-US): <strong>{currentDateFormatted}</strong>
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Formatted Score: <strong>{formattedScore}</strong>
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Parsed Duration (3725s):{' '}
              <strong>
                {duration.hours}h {duration.minutes}m {duration.seconds}s
              </strong>
            </Typography>
          </Paper>
        </Box>

        {/* Status Views Demo Section */}
        <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 2 }}>
          Status Views Showcase
        </Typography>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr 1fr' }, gap: 3, mb: 4 }}>
          <Paper sx={{ p: 2 }}>
            <LoadingView message="Loading skill assessment..." />
          </Paper>
          <EmptyView
            title="No courses enrolled"
            message="Please browse the course catalog to get started."
          />
          <ErrorView
            title="Failed to load diagnostic"
            message="Could not reach the server endpoint."
            onRetry={() => alert('Retry callback triggered')}
          />
        </Box>

        {dialogResult && (
          <Typography variant="body2" color="success.main" sx={{ mt: 2, fontWeight: 'bold' }}>
            {dialogResult}
          </Typography>
        )}

        {/* Modal Confirm Dialog */}
        <ConfirmDialog
          open={dialogOpen}
          title="Submit Assessment?"
          message="Are you sure you want to finalize this attempt? This action cannot be reversed."
          confirmLabel="Submit"
          cancelLabel="Review Answers"
          isDestructive={false}
          onConfirm={() => {
            setDialogOpen(false);
            setDialogResult('Confirmed action at: ' + new Date().toLocaleTimeString());
          }}
          onCancel={() => setDialogOpen(false)}
        />
      </DashboardLayout>
    </ThemeProvider>
  );
}