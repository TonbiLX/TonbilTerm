'use client';

import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import LightModeIcon from '@mui/icons-material/LightMode';
import SettingsBrightnessIcon from '@mui/icons-material/SettingsBrightness';
import { useUiStore } from '@/stores/uiStore';
import type { ThemeMode } from '@/types';

const modeConfig: Record<ThemeMode, { icon: typeof DarkModeIcon; label: string; next: ThemeMode }> = {
  light: { icon: LightModeIcon, label: 'Aydınlık Tema', next: 'dark' },
  dark: { icon: DarkModeIcon, label: 'Karanlık Tema', next: 'system' },
  system: { icon: SettingsBrightnessIcon, label: 'Sistem Teması', next: 'light' },
};

export default function ThemeToggle() {
  const themeMode = useUiStore((s) => s.theme);
  const setTheme = useUiStore((s) => s.setTheme);
  const config = modeConfig[themeMode];
  const Icon = config.icon;

  return (
    <Tooltip title={config.label} arrow>
      <IconButton
        onClick={() => setTheme(config.next)}
        color="inherit"
        aria-label={config.label}
        sx={{
          transition: 'transform 0.3s ease',
          '&:hover': { transform: 'rotate(30deg)' },
        }}
      >
        <Icon />
      </IconButton>
    </Tooltip>
  );
}
