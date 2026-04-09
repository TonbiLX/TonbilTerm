'use client';

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import ListItemSecondaryAction from '@mui/material/ListItemSecondaryAction';
import IconButton from '@mui/material/IconButton';
import Switch from '@mui/material/Switch';
import Fab from '@mui/material/Fab';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import Chip from '@mui/material/Chip';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import AppLayout from '@/components/layout/AppLayout';
import { useAuth } from '@/hooks/useAuth';
import { apiClient } from '@/lib/api';
import type { ScheduleEntry } from '@/types';

const dayNames = ['Pzt', 'Sal', 'Car', 'Per', 'Cum', 'Cmt', 'Paz'];
const dayFullNames = ['Pazartesi', 'Sali', 'Carsamba', 'Persembe', 'Cuma', 'Cumartesi', 'Pazar'];

export default function SchedulePage() {
  const router = useRouter();
  const { isAuthenticated, isChecked } = useAuth();
  const [selectedDay, setSelectedDay] = useState(0);
  const [entries, setEntries] = useState<ScheduleEntry[]>([]);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editEntry, setEditEntry] = useState<ScheduleEntry | null>(null);
  const [formHour, setFormHour] = useState('8');
  const [formMinute, setFormMinute] = useState('0');
  const [formTemp, setFormTemp] = useState('22');

  useEffect(() => {
    if (isChecked && !isAuthenticated) {
      router.push('/login');
    }
  }, [isChecked, isAuthenticated, router]);

  const loadSchedule = useCallback(async () => {
    try {
      const data = await apiClient.getSchedule();
      setEntries(data);
    } catch {
      setEntries([]);
    }
  }, []);

  useEffect(() => {
    if (isAuthenticated) loadSchedule();
  }, [isAuthenticated, loadSchedule]);

  const dayEntries = entries
    .filter((e) => e.dayOfWeek === selectedDay)
    .sort((a, b) => a.hour * 60 + a.minute - (b.hour * 60 + b.minute));

  const handleSave = async () => {
    const hour = parseInt(formHour, 10);
    const minute = parseInt(formMinute, 10);
    const temp = parseFloat(formTemp);

    if (isNaN(hour) || isNaN(minute) || isNaN(temp)) return;

    try {
      let updated: ScheduleEntry[];
      if (editEntry) {
        updated = entries.map((e) =>
          e.id === editEntry.id ? { ...e, hour, minute, targetTemp: temp } : e,
        );
      } else {
        updated = [
          ...entries,
          {
            id: 0, // Server will assign
            dayOfWeek: selectedDay,
            hour,
            minute,
            targetTemp: temp,
            enabled: true,
          },
        ];
      }
      // Backend only supports bulk replace (PUT /api/config/schedules)
      await apiClient.updateSchedule(
        updated.map((e) => ({
          day_of_week: e.dayOfWeek,
          hour: e.hour,
          minute: e.minute,
          target_temp: e.targetTemp,
          enabled: e.enabled,
        })),
      );
      await loadSchedule();
      closeDialog();
    } catch {
      // Handle error
    }
  };

  const handleDelete = async (id: number) => {
    try {
      // Remove entry and bulk update
      const updated = entries.filter((e) => e.id !== id);
      await apiClient.updateSchedule(
        updated.map((e) => ({
          day_of_week: e.dayOfWeek,
          hour: e.hour,
          minute: e.minute,
          target_temp: e.targetTemp,
          enabled: e.enabled,
        })),
      );
      await loadSchedule();
    } catch {
      // Handle error
    }
  };

  const handleToggle = async (entry: ScheduleEntry) => {
    const updated = entries.map((e) =>
      e.id === entry.id ? { ...e, enabled: !e.enabled } : e,
    );
    setEntries(updated);
    try {
      await apiClient.updateSchedule(
        updated.map((e) => ({
          day_of_week: e.dayOfWeek,
          hour: e.hour,
          minute: e.minute,
          target_temp: e.targetTemp,
          enabled: e.enabled,
        })),
      );
    } catch {
      loadSchedule();
    }
  };

  const handleCopyToAll = async () => {
    const currentDayEntries = entries.filter((e) => e.dayOfWeek === selectedDay);

    const newEntries: ScheduleEntry[] = [];
    for (let day = 0; day < 7; day++) {
      currentDayEntries.forEach((e, idx) => {
        newEntries.push({
          ...e,
          id: -(day * 100 + idx),
          dayOfWeek: day,
        });
      });
    }

    try {
      await apiClient.updateSchedule(
        newEntries.map((e) => ({
          day_of_week: e.dayOfWeek,
          hour: e.hour,
          minute: e.minute,
          target_temp: e.targetTemp,
          enabled: e.enabled,
        })),
      );
      await loadSchedule();
    } catch {
      // Handle error
    }
  };

  const openAdd = () => {
    setEditEntry(null);
    setFormHour('8');
    setFormMinute('0');
    setFormTemp('22');
    setDialogOpen(true);
  };

  const openEdit = (entry: ScheduleEntry) => {
    setEditEntry(entry);
    setFormHour(entry.hour.toString());
    setFormMinute(entry.minute.toString());
    setFormTemp(entry.targetTemp.toString());
    setDialogOpen(true);
  };

  const closeDialog = () => {
    setDialogOpen(false);
    setEditEntry(null);
  };

  if (!isChecked || !isAuthenticated) return null;

  // Build 24h timeline bar
  const timelineBlocks = dayEntries.map((entry, idx) => {
    const nextEntry = dayEntries[idx + 1];
    const startMinute = entry.hour * 60 + entry.minute;
    const endMinute = nextEntry ? nextEntry.hour * 60 + nextEntry.minute : 24 * 60;
    const widthPercent = ((endMinute - startMinute) / (24 * 60)) * 100;
    const leftPercent = (startMinute / (24 * 60)) * 100;

    const tempRatio = (entry.targetTemp - 15) / 15;
    const hue = 30 + (1 - tempRatio) * 180;

    return (
      <Box
        key={entry.id}
        sx={{
          position: 'absolute',
          left: `${leftPercent}%`,
          width: `${widthPercent}%`,
          height: '100%',
          bgcolor: `hsl(${hue}, 70%, ${entry.enabled ? '50%' : '80%'})`,
          opacity: entry.enabled ? 0.8 : 0.3,
          borderRadius: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          transition: 'all 0.3s ease',
        }}
      >
        {widthPercent > 8 && (
          <Typography variant="caption" sx={{ color: 'white', fontWeight: 600, fontSize: '0.65rem' }}>
            {entry.targetTemp}°
          </Typography>
        )}
      </Box>
    );
  });

  return (
    <AppLayout>
      <Box>
        <Typography variant="h5" fontWeight={700} sx={{ mb: 3 }}>
          Haftalik Program
        </Typography>

        {/* Day tabs */}
        <Tabs
          value={selectedDay}
          onChange={(_, val) => setSelectedDay(val)}
          variant="scrollable"
          scrollButtons="auto"
          sx={{
            mb: 3,
            '& .MuiTab-root': { minWidth: 60, fontWeight: 600 },
          }}
        >
          {dayNames.map((day, idx) => (
            <Tab key={idx} label={day} />
          ))}
        </Tabs>

        {/* 24h timeline bar */}
        <Card sx={{ mb: 3 }}>
          <CardContent sx={{ py: 2 }}>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              {dayFullNames[selectedDay]} - Gunluk Gorunum
            </Typography>
            <Box
              sx={{
                position: 'relative',
                height: 36,
                bgcolor: 'action.hover',
                borderRadius: 2,
                overflow: 'hidden',
              }}
            >
              {timelineBlocks}
            </Box>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 0.5 }}>
              {[0, 6, 12, 18, 24].map((h) => (
                <Typography key={h} variant="caption" color="text.secondary">
                  {h.toString().padStart(2, '0')}:00
                </Typography>
              ))}
            </Box>
          </CardContent>
        </Card>

        {/* Entry list */}
        <Card>
          <List disablePadding>
            {dayEntries.length === 0 && (
              <ListItem>
                <ListItemText
                  primary="Bu gun icin program yok"
                  secondary="Asagidaki butona tiklayarak yeni program ekleyin"
                />
              </ListItem>
            )}
            {dayEntries.map((entry, idx) => (
              <Box key={entry.id}>
                {idx > 0 && <Divider />}
                <ListItem sx={{ py: 1.5 }}>
                  <AccessTimeIcon sx={{ mr: 2, color: 'text.secondary' }} />
                  <ListItemText
                    primary={
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Typography variant="body1" fontWeight={600}>
                          {entry.hour.toString().padStart(2, '0')}:
                          {entry.minute.toString().padStart(2, '0')}
                        </Typography>
                        <Chip
                          label={`${entry.targetTemp}°C`}
                          size="small"
                          color="primary"
                          variant="outlined"
                        />
                      </Box>
                    }
                  />
                  <ListItemSecondaryAction>
                    <Switch
                      checked={entry.enabled}
                      onChange={() => handleToggle(entry)}
                      size="small"
                    />
                    <IconButton size="small" onClick={() => openEdit(entry)} aria-label="Duzenle">
                      <EditIcon fontSize="small" />
                    </IconButton>
                    <IconButton size="small" onClick={() => handleDelete(entry.id)} aria-label="Sil">
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </ListItemSecondaryAction>
                </ListItem>
              </Box>
            ))}
          </List>
        </Card>

        {/* Copy to all days */}
        {dayEntries.length > 0 && (
          <Button
            startIcon={<ContentCopyIcon />}
            onClick={handleCopyToAll}
            sx={{ mt: 2 }}
            variant="outlined"
          >
            Tum Gunlere Kopyala
          </Button>
        )}

        {/* FAB */}
        <Fab
          color="primary"
          onClick={openAdd}
          sx={{
            position: 'fixed',
            bottom: { xs: 80, md: 24 },
            right: 24,
          }}
          aria-label="Program Ekle"
        >
          <AddIcon />
        </Fab>

        {/* Add/Edit dialog */}
        <Dialog open={dialogOpen} onClose={closeDialog} maxWidth="xs" fullWidth>
          <DialogTitle>{editEntry ? 'Programi Duzenle' : 'Yeni Program Ekle'}</DialogTitle>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '16px !important' }}>
            <Box sx={{ display: 'flex', gap: 2 }}>
              <TextField
                label="Saat"
                type="number"
                value={formHour}
                onChange={(e) => setFormHour(e.target.value)}
                inputProps={{ min: 0, max: 23 }}
                fullWidth
              />
              <TextField
                label="Dakika"
                type="number"
                value={formMinute}
                onChange={(e) => setFormMinute(e.target.value)}
                inputProps={{ min: 0, max: 59 }}
                fullWidth
              />
            </Box>
            <TextField
              label="Hedef Sicaklik (°C)"
              type="number"
              value={formTemp}
              onChange={(e) => setFormTemp(e.target.value)}
              inputProps={{ min: 15, max: 30, step: 0.5 }}
              fullWidth
            />
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={closeDialog} color="inherit">
              Iptal
            </Button>
            <Button onClick={handleSave} variant="contained">
              Kaydet
            </Button>
          </DialogActions>
        </Dialog>
      </Box>
    </AppLayout>
  );
}
