import { createTheme, type ThemeOptions } from '@mui/material/styles';

const sharedTypography = {
  fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
  h1: { fontSize: '2.5rem', fontWeight: 700, letterSpacing: '-0.02em' },
  h2: { fontSize: '2rem', fontWeight: 600, letterSpacing: '-0.01em' },
  h3: { fontSize: '1.75rem', fontWeight: 600 },
  h4: { fontSize: '1.5rem', fontWeight: 500 },
  h5: { fontSize: '1.25rem', fontWeight: 500 },
  h6: { fontSize: '1.1rem', fontWeight: 500 },
  body1: { fontSize: '1rem', lineHeight: 1.6 },
  body2: { fontSize: '0.875rem', lineHeight: 1.5 },
  button: { textTransform: 'none' as const, fontWeight: 600 },
};

const sharedComponents: ThemeOptions['components'] = {
  MuiButton: {
    styleOverrides: {
      root: {
        borderRadius: 12,
        padding: '10px 24px',
        transition: 'all 0.2s ease-in-out',
      },
      contained: {
        boxShadow: 'none',
        '&:hover': { boxShadow: '0 2px 8px rgba(0,0,0,0.15)' },
      },
    },
  },
  MuiCard: {
    styleOverrides: {
      root: {
        borderRadius: 16,
        transition: 'box-shadow 0.3s ease, transform 0.2s ease',
      },
    },
  },
  MuiChip: {
    styleOverrides: {
      root: {
        borderRadius: 12,
        fontWeight: 500,
        transition: 'all 0.2s ease-in-out',
      },
    },
  },
  MuiTextField: {
    styleOverrides: {
      root: {
        '& .MuiOutlinedInput-root': {
          borderRadius: 12,
        },
      },
    },
  },
  MuiDialog: {
    styleOverrides: {
      paper: { borderRadius: 20 },
    },
  },
  MuiDrawer: {
    styleOverrides: {
      paper: { borderRadius: '0 16px 16px 0' },
    },
  },
  MuiFab: {
    styleOverrides: {
      root: { borderRadius: 16 },
    },
  },
  MuiPaper: {
    styleOverrides: {
      root: { borderRadius: 16 },
    },
  },
};

export const lightTheme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: '#8B4513',
      light: '#B5713A',
      dark: '#5D2E0D',
      contrastText: '#FFFFFF',
    },
    secondary: {
      main: '#FF6B35',
      light: '#FF9A6C',
      dark: '#C44B1A',
    },
    background: {
      default: '#FFF8F5',
      paper: '#FFFFFF',
    },
    error: { main: '#BA1A1A' },
    warning: { main: '#F4A100' },
    success: { main: '#2E7D32' },
    info: { main: '#0288D1' },
    text: {
      primary: '#1C1B1F',
      secondary: '#49454F',
    },
    divider: 'rgba(0,0,0,0.08)',
  },
  typography: sharedTypography,
  components: sharedComponents,
  shape: { borderRadius: 12 },
});

export const darkTheme = createTheme({
  palette: {
    mode: 'dark',
    primary: {
      main: '#FFB68C',
      light: '#FFDCC8',
      dark: '#8B4513',
      contrastText: '#4A2800',
    },
    secondary: {
      main: '#FF6B35',
      light: '#FF9A6C',
      dark: '#C44B1A',
    },
    background: {
      default: '#1C1B1F',
      paper: '#2B2930',
    },
    error: { main: '#FFB4AB' },
    warning: { main: '#FFD54F' },
    success: { main: '#81C784' },
    info: { main: '#4FC3F7' },
    text: {
      primary: '#E6E1E5',
      secondary: '#CAC4D0',
    },
    divider: 'rgba(255,255,255,0.08)',
  },
  typography: sharedTypography,
  components: {
    ...sharedComponents,
    MuiCard: {
      styleOverrides: {
        root: {
          borderRadius: 16,
          backgroundImage: 'none',
          transition: 'box-shadow 0.3s ease, transform 0.2s ease',
        },
      },
    },
  },
  shape: { borderRadius: 12 },
});
