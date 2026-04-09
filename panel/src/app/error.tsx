'use client';

import { useEffect } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';

const isDev = process.env.NODE_ENV === 'development';

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error('PAGE ERROR:', error.message, error.stack);
  }, [error]);

  return (
    <Box sx={{ p: 4, textAlign: 'center', mt: 8, maxWidth: 500, mx: 'auto' }}>
      <ErrorOutlineIcon sx={{ fontSize: 64, color: 'error.main', mb: 2 }} />
      <Typography variant="h5" color="error" gutterBottom>
        Bir hata olustu
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
        Beklenmeyen bir sorun meydana geldi. Lutfen tekrar deneyin.
      </Typography>

      {/* Stack trace sadece development'ta gosterilir */}
      {isDev && (
        <Typography
          variant="body2"
          sx={{ mb: 2, fontFamily: 'monospace', bgcolor: '#1a1a1a', color: '#ff6b6b', p: 2, borderRadius: 1, textAlign: 'left', whiteSpace: 'pre-wrap', maxHeight: 300, overflow: 'auto' }}
        >
          {error.message}
          {'\n\n'}
          {error.stack}
        </Typography>
      )}

      <Box sx={{ display: 'flex', gap: 1, justifyContent: 'center' }}>
        <Button variant="contained" onClick={reset}>
          Tekrar Dene
        </Button>
        <Button variant="outlined" onClick={() => window.location.href = '/'}>
          Ana Sayfaya Don
        </Button>
      </Box>
    </Box>
  );
}
