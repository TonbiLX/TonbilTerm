'use client';

import { useState } from 'react';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import type { SvgIconComponent } from '@mui/icons-material';

interface StatusCardToggle {
  /** Label shown on the primary toggle chip */
  primaryLabel: string;
  /** Label shown on the secondary toggle chip */
  secondaryLabel: string;
  /** Value displayed when secondary chip is active */
  secondaryValue: string;
  /** Optional subtitle for secondary view */
  secondarySubtitle?: string;
}

interface StatusCardProps {
  icon: SvgIconComponent;
  title: string;
  value: string;
  subtitle?: string;
  color?: string;
  trend?: 'up' | 'down' | 'neutral';
  /** When provided, renders a Gunluk/Haftalik toggle */
  toggle?: StatusCardToggle;
}

export default function StatusCard({
  icon: Icon,
  title,
  value,
  subtitle,
  color = '#FF6B35',
  toggle,
}: StatusCardProps) {
  const [showSecondary, setShowSecondary] = useState(false);

  const displayValue = toggle && showSecondary ? toggle.secondaryValue : value;
  const displaySubtitle = toggle && showSecondary ? toggle.secondarySubtitle : subtitle;

  return (
    <Card
      sx={{
        height: '100%',
        '&:hover': { transform: 'translateY(-1px)' },
        transition: 'transform 0.2s ease',
      }}
    >
      <CardContent sx={{ p: 2.5, '&:last-child': { pb: 2.5 } }}>
        <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2 }}>
          <Box
            sx={{
              p: 1.5,
              borderRadius: 3,
              bgcolor: `${color}18`,
              display: 'flex',
              flexShrink: 0,
            }}
          >
            <Icon sx={{ color, fontSize: 28 }} />
          </Box>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
              {title}
            </Typography>

            {/* Toggle chips — only rendered when toggle prop is provided */}
            {toggle && (
              <Box sx={{ display: 'flex', gap: 0.5, mb: 0.75 }}>
                <Box
                  component="button"
                  onClick={() => setShowSecondary(false)}
                  sx={{
                    px: 1,
                    py: 0.25,
                    borderRadius: 10,
                    border: 'none',
                    cursor: 'pointer',
                    typography: 'caption',
                    fontWeight: 600,
                    bgcolor: !showSecondary ? color : 'action.hover',
                    color: !showSecondary ? '#fff' : 'text.secondary',
                    transition: 'background 0.15s ease',
                    '&:hover': { opacity: 0.85 },
                  }}
                >
                  {toggle.primaryLabel}
                </Box>
                <Box
                  component="button"
                  onClick={() => setShowSecondary(true)}
                  sx={{
                    px: 1,
                    py: 0.25,
                    borderRadius: 10,
                    border: 'none',
                    cursor: 'pointer',
                    typography: 'caption',
                    fontWeight: 600,
                    bgcolor: showSecondary ? color : 'action.hover',
                    color: showSecondary ? '#fff' : 'text.secondary',
                    transition: 'background 0.15s ease',
                    '&:hover': { opacity: 0.85 },
                  }}
                >
                  {toggle.secondaryLabel}
                </Box>
              </Box>
            )}

            <Typography variant="h5" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
              {displayValue}
            </Typography>
            {displaySubtitle && (
              <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
                {displaySubtitle}
              </Typography>
            )}
          </Box>
        </Box>
      </CardContent>
    </Card>
  );
}
