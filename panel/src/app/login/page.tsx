'use client';

import { useState, useEffect, type FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import FormControlLabel from '@mui/material/FormControlLabel';
import Checkbox from '@mui/material/Checkbox';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import ThermostatIcon from '@mui/icons-material/Thermostat';
import { useTheme } from '@mui/material/styles';
import { z } from 'zod';
import { useAuth } from '@/hooks/useAuth';

const loginSchema = z.object({
  // Kullanici adi veya email kabul et
  email: z.string().min(1, 'Kullanici adi veya email gerekli'),
  password: z.string().min(1, 'Sifre gerekli'),
});

const REMEMBERED_EMAIL_KEY = 'tonbil_remembered_email';

export default function LoginPage() {
  const theme = useTheme();
  const router = useRouter();
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<{ email?: string; password?: string }>({});
  const [loading, setLoading] = useState(false);
  const [shake, setShake] = useState(false);

  // Restore remembered email on mount
  useEffect(() => {
    const saved = localStorage.getItem(REMEMBERED_EMAIL_KEY);
    if (saved) {
      setEmail(saved);
      setRemember(true);
    }
  }, []);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});

    const result = loginSchema.safeParse({ email, password });
    if (!result.success) {
      const errors: { email?: string; password?: string } = {};
      for (const issue of result.error.issues) {
        const field = issue.path[0] as 'email' | 'password';
        if (!errors[field]) errors[field] = issue.message;
      }
      setFieldErrors(errors);
      setShake(true);
      setTimeout(() => setShake(false), 600);
      return;
    }

    setLoading(true);

    try {
      await login(result.data.email, result.data.password);
      // Save or clear remembered email based on checkbox
      if (remember) {
        localStorage.setItem(REMEMBERED_EMAIL_KEY, email);
      } else {
        localStorage.removeItem(REMEMBERED_EMAIL_KEY);
      }
      router.push('/');
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Giris basarisiz. Lutfen bilgilerinizi kontrol edin.';
      setError(message);
      setShake(true);
      setTimeout(() => setShake(false), 600);
    } finally {
      setLoading(false);
    }
  };

  const isDark = theme.palette.mode === 'dark';

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        p: 2,
        background: isDark
          ? 'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)'
          : 'linear-gradient(135deg, #FFF8F5 0%, #FFE8D6 50%, #FFD4B2 100%)',
      }}
    >
      <Card
        sx={{
          width: '100%',
          maxWidth: 420,
          animation: shake ? 'shake 0.5s ease-in-out' : 'fadeIn 0.6s ease-out',
          '@keyframes shake': {
            '0%, 100%': { transform: 'translateX(0)' },
            '10%, 30%, 50%, 70%, 90%': { transform: 'translateX(-4px)' },
            '20%, 40%, 60%, 80%': { transform: 'translateX(4px)' },
          },
          '@keyframes fadeIn': {
            from: { opacity: 0, transform: 'translateY(20px)' },
            to: { opacity: 1, transform: 'translateY(0)' },
          },
        }}
      >
        <CardContent sx={{ p: { xs: 3, sm: 4 } }}>
          {/* Logo */}
          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', mb: 4 }}>
            <Box
              sx={{
                p: 2,
                borderRadius: '50%',
                bgcolor: 'primary.main',
                display: 'flex',
                mb: 2,
                boxShadow: '0 4px 20px rgba(139, 69, 19, 0.3)',
              }}
            >
              <ThermostatIcon sx={{ fontSize: 40, color: 'white' }} />
            </Box>
            <Typography variant="h4" fontWeight={700} color="primary">
              TonbilTerm
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              Akilli Termostat Kontrol Paneli
            </Typography>
          </Box>

          {/* Error */}
          {error && (
            <Alert severity="error" sx={{ mb: 2, borderRadius: 3 }}>
              {error}
            </Alert>
          )}

          {/* Form */}
          <Box component="form" onSubmit={handleSubmit} noValidate>
            <TextField
              fullWidth
              label="Kullanici Adi / E-posta"
              type="text"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                if (fieldErrors.email) setFieldErrors((prev) => ({ ...prev, email: undefined }));
              }}
              required
              autoComplete="email"
              autoFocus
              error={!!fieldErrors.email}
              helperText={fieldErrors.email}
              sx={{ mb: 2 }}
            />
            <TextField
              fullWidth
              label="Sifre"
              type="password"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                if (fieldErrors.password) setFieldErrors((prev) => ({ ...prev, password: undefined }));
              }}
              required
              autoComplete="current-password"
              error={!!fieldErrors.password}
              helperText={fieldErrors.password}
              sx={{ mb: 2 }}
            />

            <FormControlLabel
              control={
                <Checkbox
                  checked={remember}
                  onChange={(e) => setRemember(e.target.checked)}
                  color="primary"
                  size="small"
                />
              }
              label={
                <Typography variant="body2" color="text.secondary">
                  Beni Hatirla
                </Typography>
              }
              sx={{ mb: 3 }}
            />

            <Button
              type="submit"
              fullWidth
              variant="contained"
              size="large"
              disabled={loading || !email || !password}
              sx={{
                py: 1.5,
                fontSize: '1rem',
                position: 'relative',
              }}
            >
              {loading ? (
                <CircularProgress size={24} color="inherit" />
              ) : (
                'Giris Yap'
              )}
            </Button>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
}
