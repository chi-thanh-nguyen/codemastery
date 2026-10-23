import React, { useState } from 'react';
import { ROUTE_PATHS, type AppRoutePath } from './routePaths';
import { LoginPage } from '../features/auth/pages/LoginPage';
import { RegisterPage } from '../features/auth/pages/RegisterPage';
import { ProfilePage } from '../features/auth/pages/ProfilePage';
import { ProtectedRoute } from './guards/ProtectedRoute';
import { DashboardLayout } from '../layouts/DashboardLayout';
import { PageHeader } from '../shared/components/PageHeader';
import { ConfirmDialog } from '../shared/components/ConfirmDialog';
import { LoadingView, EmptyView, ErrorView } from '../shared/components/StatusViews';
import { useDebounce } from '../shared/hooks/useDebounce';
import { formatDate, formatNumber, formatDurationSeconds } from '../shared/utils/format';
import { useAuth } from '../features/auth/hooks/useAuth';
import { Box, Button, TextField, Paper, Typography } from '@mui/material';
import type { NavItem } from '../shared/components/SidebarNav';

export const AppRouter: React.FC = () => {
    const [currentPath, setCurrentPath] = useState<AppRoutePath>(ROUTE_PATHS.HOME);
    const { user, isAuthenticated, logout } = useAuth();

    // Helper navigate function to satisfy (path: string) => void
    const handleNavigate = (path: string) => {
        setCurrentPath(path as AppRoutePath);
    };

    // FE01 Showcase state
    const [dialogOpen, setDialogOpen] = useState(false);
    const [dialogResult, setDialogResult] = useState<string | null>(null);
    const [inputValue, setInputValue] = useState('');
    const debouncedInput = useDebounce(inputValue, 400);

    const currentDateFormatted = formatDate(new Date(), {
        locale: 'en-US',
        options: { dateStyle: 'full' },
    });

    const formattedScore = formatNumber(0.885, {
        locale: 'en-US',
        options: { style: 'percent', minimumFractionDigits: 1 },
    });

    const duration = formatDurationSeconds(3725);

    const navItems: NavItem[] = [
        { id: ROUTE_PATHS.HOME, label: 'Platform Showcase' },
        ...(isAuthenticated
            ? [{ id: ROUTE_PATHS.PROFILE, label: 'My Profile' }]
            : [
                { id: ROUTE_PATHS.LOGIN, label: 'Sign In' },
                { id: ROUTE_PATHS.REGISTER, label: 'Register' },
            ]),
    ];

    const handleNavSelect = (id: string) => {
        handleNavigate(id);
    };

    // Render individual auth routes
    if (currentPath === ROUTE_PATHS.LOGIN) {
        return <LoginPage onNavigate={handleNavigate} />;
    }

    if (currentPath === ROUTE_PATHS.REGISTER) {
        return <RegisterPage onNavigate={handleNavigate} />;
    }

    return (
        <DashboardLayout
            headerTitle="CodeMastery"
            userDisplayName={user ? `${user.displayName} (${user.role})` : undefined}
            navItems={navItems}
            selectedNavId={currentPath}
            onNavSelect={handleNavSelect}
            headerActions={
                isAuthenticated ? (
                    <Button color="inherit" size="small" onClick={logout}>
                        Sign Out
                    </Button>
                ) : (
                    <Button
                        variant="contained"
                        size="small"
                        onClick={() => handleNavigate(ROUTE_PATHS.LOGIN)}
                    >
                        Sign In
                    </Button>
                )
            }
        >
            {currentPath === ROUTE_PATHS.PROFILE ? (
                <ProtectedRoute onNavigate={handleNavigate}>
                    <ProfilePage onNavigate={handleNavigate} />
                </ProtectedRoute>
            ) : (
                <Box>
                    {/* Auth Session Status Banner */}
                    <Paper sx={{ p: 2.5, mb: 3, borderLeft: '4px solid #1976d2' }}>
                        <Typography variant="h6" sx={{ fontWeight: 'bold' }}>
                            {isAuthenticated
                                ? `Welcome back, ${user?.displayName}!`
                                : 'Authentication Session Status: Guest (Public Mode)'}
                        </Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                            {isAuthenticated
                                ? `Active role: ${user?.role}. Bearer token is safely held in memory.`
                                : 'You are currently browsing publicly. Sign in or register to test the core Auth integration.'}
                        </Typography>
                    </Paper>

                    {/* Preserved FE01 Presentation Work */}
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
                </Box>
            )}
        </DashboardLayout>
    );
};