'use client';

import { useState, useEffect } from 'react';
import Box from '@mui/material/Box';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import CircularProgress from '@mui/material/CircularProgress';
import DeleteIcon from '@mui/icons-material/Delete';
import EnergySavingsLeafIcon from '@mui/icons-material/EnergySavingsLeaf';
import WhatshotIcon from '@mui/icons-material/Whatshot';
import NightsStayIcon from '@mui/icons-material/NightsStay';
import WbSunnyIcon from '@mui/icons-material/WbSunny';
import ThermostatIcon from '@mui/icons-material/Thermostat';
import AcUnitIcon from '@mui/icons-material/AcUnit';
import type { SvgIconProps } from '@mui/material/SvgIcon';
import type { Profile } from '@/types';

// ---------------------------------------------------------------------------
// Icon mapping (shared)
// ---------------------------------------------------------------------------

type IconComponent = React.ComponentType<SvgIconProps>;

export const ICON_MAP: Record<string, IconComponent> = {
  eco: EnergySavingsLeafIcon,
  whatshot: WhatshotIcon,
  nightlight: NightsStayIcon,
  wb_sunny: WbSunnyIcon,
  thermostat: ThermostatIcon,
  ac_unit: AcUnitIcon,
};

const ICON_OPTIONS: { key: string; label: string }[] = [
  { key: 'eco', label: 'Eko' },
  { key: 'whatshot', label: 'Sicak' },
  { key: 'nightlight', label: 'Gece' },
  { key: 'wb_sunny', label: 'Gunduz' },
  { key: 'thermostat', label: 'Termostat' },
  { key: 'ac_unit', label: 'Soguk' },
];

export function IconByName({ name, ...props }: { name: string } & SvgIconProps) {
  const Icon = ICON_MAP[name] ?? ThermostatIcon;
  return <Icon {...props} />;
}

// ---------------------------------------------------------------------------
// ProfileDialog
// ---------------------------------------------------------------------------

export interface ProfileDialogProps {
  open: boolean;
  profile: Profile | null; // null = new profile
  onClose: () => void;
  onSave: (data: { name: string; icon: string; targetTemp: number; hysteresis: number }, id?: number) => Promise<void>;
  onDelete?: (id: number) => Promise<void>;
}

export default function ProfileDialog({ open, profile, onClose, onSave, onDelete }: ProfileDialogProps) {
  const [name, setName] = useState('');
  const [icon, setIcon] = useState('thermostat');
  const [targetTemp, setTargetTemp] = useState(22);
  const [hysteresis, setHysteresis] = useState(0.5);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  // Sync form when dialog opens
  useEffect(() => {
    if (open) {
      setName(profile?.name ?? '');
      setIcon(profile?.icon ?? 'thermostat');
      setTargetTemp(profile?.targetTemp ?? 22);
      setHysteresis(profile?.hysteresis ?? 0.5);
      setError('');
    }
  }, [open, profile]);

  const isEdit = profile !== null;
  const canDelete = isEdit && !profile.isDefault;

  const handleSave = async () => {
    const trimmed = name.trim();
    if (!trimmed) {
      setError('Profil ismi gerekli');
      return;
    }
    if (targetTemp < 5 || targetTemp > 35) {
      setError('Sicaklik 5-35 arasi olmali');
      return;
    }
    if (hysteresis < 0.1 || hysteresis > 3) {
      setError('Hysteresis 0.1-3 arasi olmali');
      return;
    }

    setSaving(true);
    setError('');
    try {
      await onSave(
        { name: trimmed, icon, targetTemp, hysteresis },
        isEdit ? profile.id : undefined,
      );
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Kaydetme hatasi');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!canDelete || !onDelete) return;
    setSaving(true);
    try {
      await onDelete(profile.id);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Silme hatasi');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{isEdit ? 'Profil Duzenle' : 'Yeni Profil'}</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '8px !important' }}>
        <TextField
          label="Isim"
          value={name}
          onChange={(e) => setName(e.target.value)}
          fullWidth
          size="small"
          autoFocus
          slotProps={{ htmlInput: { maxLength: 20 } }}
        />

        {/* Icon selector */}
        <Box>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
            Ikon
          </Typography>
          <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
            {ICON_OPTIONS.map((opt) => (
              <IconButton
                key={opt.key}
                size="small"
                onClick={() => setIcon(opt.key)}
                sx={{
                  border: 1,
                  borderColor: icon === opt.key ? 'primary.main' : 'divider',
                  bgcolor: icon === opt.key ? 'primary.main' : 'transparent',
                  color: icon === opt.key ? 'primary.contrastText' : 'text.secondary',
                  '&:hover': {
                    bgcolor: icon === opt.key ? 'primary.dark' : 'action.hover',
                  },
                }}
                aria-label={opt.label}
              >
                <IconByName name={opt.key} fontSize="small" />
              </IconButton>
            ))}
          </Box>
        </Box>

        <TextField
          label="Sicaklik (°C)"
          type="number"
          value={targetTemp}
          onChange={(e) => setTargetTemp(parseFloat(e.target.value) || 0)}
          fullWidth
          size="small"
          slotProps={{ htmlInput: { min: 5, max: 35, step: 0.5 } }}
        />

        <TextField
          label="Hysteresis (°C)"
          type="number"
          value={hysteresis}
          onChange={(e) => setHysteresis(parseFloat(e.target.value) || 0)}
          fullWidth
          size="small"
          slotProps={{ htmlInput: { min: 0.1, max: 3, step: 0.1 } }}
        />

        {error && (
          <Typography variant="body2" color="error">
            {error}
          </Typography>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2, justifyContent: canDelete ? 'space-between' : 'flex-end' }}>
        {canDelete && (
          <Button
            color="error"
            startIcon={<DeleteIcon />}
            onClick={handleDelete}
            disabled={saving}
          >
            Sil
          </Button>
        )}
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button onClick={onClose} disabled={saving}>
            Iptal
          </Button>
          <Button
            variant="contained"
            onClick={handleSave}
            disabled={saving}
            startIcon={saving ? <CircularProgress size={16} /> : undefined}
          >
            Kaydet
          </Button>
        </Box>
      </DialogActions>
    </Dialog>
  );
}
