'use client';

import { useState, useCallback, useEffect } from 'react';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Snackbar from '@mui/material/Snackbar';
import CircularProgress from '@mui/material/CircularProgress';
import Typography from '@mui/material/Typography';
import AddIcon from '@mui/icons-material/Add';
import AutoModeIcon from '@mui/icons-material/AutoMode';
import TuneIcon from '@mui/icons-material/Tune';
import LocalFireDepartmentIcon from '@mui/icons-material/LocalFireDepartment';
import AcUnitIcon from '@mui/icons-material/AcUnit';
import type { Profile } from '@/types';
import { apiClient } from '@/lib/api';
import { useSensorStore } from '@/stores/sensorStore';
import ProfileDialog from './ProfileDialog';
import ProfileChip from './ProfileChip';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Backend'den gelen mode → UI'da hangi ana sekme aktif */
function resolveMainMode(backendMode: string): 'auto' | 'manual' {
  if (
    backendMode === 'manual' ||
    backendMode === 'manual_on' ||
    backendMode === 'manual_off'
  ) {
    return 'manual';
  }
  return 'auto'; // 'auto' | 'schedule' | her bilinmeyen
}

const ACTIVE_PROFILE_KEY = 'tonbil_active_profile_id';

function getSavedProfileId(): number | null {
  if (typeof window === 'undefined') return null;
  const raw = localStorage.getItem(ACTIVE_PROFILE_KEY);
  if (!raw) return null;
  const parsed = parseInt(raw, 10);
  return Number.isNaN(parsed) ? null : parsed;
}

function resolveActiveProfile(profiles: Profile[]): number | null {
  const savedId = getSavedProfileId();
  if (savedId !== null && profiles.some((p) => p.id === savedId)) return savedId;
  const def = profiles.find((p) => p.isDefault) ?? profiles[0];
  return def?.id ?? null;
}

// ---------------------------------------------------------------------------
// ModeSelector
// ---------------------------------------------------------------------------

export default function ModeSelector() {
  const boilerMode = useSensorStore((s) => s.boilerStatus.mode);
  const boilerRelay = useSensorStore((s) => s.boilerStatus.relay);

  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [fetchingProfiles, setFetchingProfiles] = useState(true);
  const [loading, setLoading] = useState(false);
  const [feedback, setFeedback] = useState('');

  // Which main tab is selected
  const [mainMode, setMainMode] = useState<'auto' | 'manual'>(() =>
    resolveMainMode(boilerMode),
  );

  // Which profile chip is active (only meaningful in auto mode)
  const [activeProfileId, setActiveProfileId] = useState<number | null>(null);

  // Dialog state
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editProfile, setEditProfile] = useState<Profile | null>(null);

  // Fetch profiles on mount
  useEffect(() => {
    fetchProfiles();
  }, []);

  const fetchProfiles = async () => {
    setFetchingProfiles(true);
    try {
      const data = await apiClient.getProfiles();
      const sorted = [...data].sort((a, b) => a.sortOrder - b.sortOrder);
      setProfiles(sorted);
    } catch {
      // silent — will retry on next interaction
    } finally {
      setFetchingProfiles(false);
    }
  };

  // Sync main tab from backend mode (when not loading an action)
  useEffect(() => {
    if (!loading) {
      setMainMode(resolveMainMode(boilerMode));
    }
  }, [boilerMode, loading]);

  // Sync active profile when profiles load or backend mode changes to auto
  useEffect(() => {
    if (profiles.length > 0 && !loading) {
      setActiveProfileId(resolveActiveProfile(profiles));
    }
  }, [profiles, loading]);

  // ---------------------------------------------------------------------------
  // Main mode toggle: Otomatik ↔ Manuel
  // ---------------------------------------------------------------------------

  const handleMainModeChange = useCallback(
    async (_: React.MouseEvent, value: 'auto' | 'manual' | null) => {
      if (value === null || value === mainMode || loading) return;

      const prev = mainMode;
      setMainMode(value);
      setLoading(true);

      try {
        const config = await apiClient.updateHeatingConfig({ mode: value });
        useSensorStore.getState().updateBoilerStatus({
          mode: config.mode,
          target: config.targetTemp,
          relay: config.relayState ?? undefined,
        });
        setFeedback(value === 'auto' ? 'Otomatik mod aktif' : 'Manuel mod aktif');
      } catch {
        setMainMode(prev);
        setFeedback('Hata! Mod degistirilemedi');
      } finally {
        setLoading(false);
      }
    },
    [mainMode, loading],
  );

  // ---------------------------------------------------------------------------
  // Manual relay toggle: Kombi AÇ / KAPAT
  // ---------------------------------------------------------------------------

  const handleRelayToggle = useCallback(async () => {
    if (loading) return;

    const targetRelay = !boilerRelay;
    const newMode = targetRelay ? 'manual_on' : 'manual_off';

    setLoading(true);
    // Optimistic update
    useSensorStore.getState().updateBoilerStatus({ relay: targetRelay, mode: newMode });

    try {
      const config = await apiClient.updateHeatingConfig({ mode: newMode });
      useSensorStore.getState().updateBoilerStatus({
        mode: config.mode,
        relay: config.relayState ?? targetRelay,
        target: config.targetTemp,
      });
      setFeedback(targetRelay ? 'Kombi acildi' : 'Kombi kapatildi');
    } catch {
      // Rollback
      useSensorStore.getState().updateBoilerStatus({
        relay: boilerRelay,
        mode: boilerRelay ? 'manual_on' : 'manual_off',
      });
      setFeedback('Hata! Kombi komutu gonderilemedi');
    } finally {
      setLoading(false);
    }
  }, [loading, boilerRelay]);

  // ---------------------------------------------------------------------------
  // Profile chip selection (auto mode only)
  // ---------------------------------------------------------------------------

  const handleProfileSelect = useCallback(
    async (profile: Profile) => {
      if (loading || activeProfileId === profile.id) return;

      const prev = activeProfileId;
      setActiveProfileId(profile.id);
      localStorage.setItem(ACTIVE_PROFILE_KEY, String(profile.id));
      setLoading(true);

      try {
        const config = await apiClient.updateHeatingConfig({
          mode: 'auto',
          target_temp: profile.targetTemp,
          hysteresis: profile.hysteresis,
        });
        useSensorStore.getState().updateBoilerStatus({
          mode: config.mode,
          target: config.targetTemp,
          relay: config.relayState ?? undefined,
        });
        setFeedback(`${profile.name} (${profile.targetTemp}°C)`);
      } catch {
        setActiveProfileId(prev);
        setFeedback('Hata!');
      } finally {
        setLoading(false);
      }
    },
    [loading, activeProfileId],
  );

  // ---------------------------------------------------------------------------
  // Profile CRUD
  // ---------------------------------------------------------------------------

  const handleSaveProfile = useCallback(
    async (
      data: { name: string; icon: string; targetTemp: number; hysteresis: number },
      id?: number,
    ) => {
      const payload = {
        name: data.name,
        icon: data.icon,
        target_temp: data.targetTemp,
        hysteresis: data.hysteresis,
      };
      if (id !== undefined) {
        await apiClient.updateProfile(id, payload);
      } else {
        await apiClient.createProfile(payload);
      }
      await fetchProfiles();
    },
    [],
  );

  const handleDeleteProfile = useCallback(async (id: number) => {
    await apiClient.deleteProfile(id);
    if (getSavedProfileId() === id) {
      localStorage.removeItem(ACTIVE_PROFILE_KEY);
    }
    await fetchProfiles();
  }, []);

  const openNewDialog = useCallback(() => {
    setEditProfile(null);
    setDialogOpen(true);
  }, []);

  const openEditDialog = useCallback((profile: Profile) => {
    setEditProfile(profile);
    setDialogOpen(true);
  }, []);

  const closeDialog = useCallback(() => {
    setDialogOpen(false);
    setEditProfile(null);
  }, []);

  // ---------------------------------------------------------------------------
  // Render
  // ---------------------------------------------------------------------------

  const isRelayOn = boilerRelay;

  return (
    <>
      {/* ── Ana mod toggle ── */}
      <ToggleButtonGroup
        value={mainMode}
        exclusive
        onChange={handleMainModeChange}
        disabled={loading}
        sx={{
          width: '100%',
          maxWidth: 360,
          '& .MuiToggleButton-root': {
            flex: 1,
            py: 1.25,
            fontSize: '0.95rem',
            fontWeight: 600,
            textTransform: 'none',
            gap: 1,
            transition: 'all 0.2s ease-in-out',
          },
          '& .MuiToggleButton-root.Mui-selected': {
            fontWeight: 700,
          },
        }}
      >
        <ToggleButton value="auto" aria-label="Otomatik mod">
          <AutoModeIcon fontSize="small" />
          Otomatik
        </ToggleButton>
        <ToggleButton value="manual" aria-label="Manuel mod">
          <TuneIcon fontSize="small" />
          Manuel
        </ToggleButton>
      </ToggleButtonGroup>

      {/* ── Manuel modda: Kombi AÇ / KAPAT butonu ── */}
      {mainMode === 'manual' && (
        <Box sx={{ width: '100%', maxWidth: 360, mt: 1.5 }}>
          <Button
            fullWidth
            variant={isRelayOn ? 'contained' : 'outlined'}
            color={isRelayOn ? 'error' : 'primary'}
            size="large"
            disabled={loading}
            onClick={handleRelayToggle}
            startIcon={
              loading ? (
                <CircularProgress size={18} color="inherit" />
              ) : isRelayOn ? (
                <LocalFireDepartmentIcon />
              ) : (
                <AcUnitIcon />
              )
            }
            sx={{
              py: 1.5,
              fontSize: '1rem',
              fontWeight: 700,
              textTransform: 'none',
              borderRadius: 2,
              transition: 'all 0.25s ease-in-out',
              ...(isRelayOn && {
                background: 'linear-gradient(135deg, #FF6B35 0%, #F44336 100%)',
                boxShadow: '0 4px 16px rgba(244, 67, 54, 0.35)',
                '&:hover': {
                  background: 'linear-gradient(135deg, #e55a24 0%, #d32f2f 100%)',
                },
              }),
            }}
          >
            {isRelayOn ? 'Kombi Kapali' : 'Kombi Ac'}
          </Button>
          <Typography
            variant="caption"
            color="text.disabled"
            sx={{ display: 'block', textAlign: 'center', mt: 0.75 }}
          >
            Manuel modda kombi otomatik kontrol edilmez
          </Typography>
        </Box>
      )}

      {/* ── Isitma profilleri (her iki modda da gorünur) ── */}
      <Box sx={{ width: '100%', maxWidth: 480, mt: mainMode === 'manual' ? 1.5 : 1 }}>
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{ display: 'block', mb: 0.75, fontWeight: 500 }}
        >
          Isitma Profili
          {mainMode === 'manual' && (
            <Typography component="span" variant="caption" color="text.disabled">
              {' '}(otomatik modda etkin olur)
            </Typography>
          )}
        </Typography>

        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          {/* Loading skeleton */}
          {fetchingProfiles && profiles.length === 0 && (
            <CircularProgress size={24} />
          )}

          {/* Profile chips */}
          {profiles.map((p) => (
            <ProfileChip
              key={p.id}
              profile={p}
              isActive={activeProfileId === p.id && mainMode === 'auto'}
              disabled={loading || mainMode === 'manual'}
              onSelect={() => handleProfileSelect(p)}
              onEdit={() => openEditDialog(p)}
            />
          ))}

          {/* Yeni profil chip */}
          {!fetchingProfiles && (
            <Chip
              icon={<AddIcon />}
              label="Yeni"
              variant="outlined"
              onClick={openNewDialog}
              disabled={loading}
              sx={{
                px: 0.5,
                height: 36,
                fontSize: '0.8rem',
                fontWeight: 500,
                borderStyle: 'dashed',
              }}
            />
          )}
        </Box>
      </Box>

      {/* Profile edit/create dialog */}
      <ProfileDialog
        open={dialogOpen}
        profile={editProfile}
        onClose={closeDialog}
        onSave={handleSaveProfile}
        onDelete={handleDeleteProfile}
      />

      <Snackbar
        open={!!feedback}
        autoHideDuration={2500}
        onClose={() => setFeedback('')}
        message={feedback}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      />
    </>
  );
}
