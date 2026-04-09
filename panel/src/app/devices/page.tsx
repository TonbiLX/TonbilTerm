'use client';

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Typography from '@mui/material/Typography';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import MenuItem from '@mui/material/MenuItem';
import Skeleton from '@mui/material/Skeleton';
import Tooltip from '@mui/material/Tooltip';
import DeviceThermostatIcon from '@mui/icons-material/DeviceThermostat';
import SensorsIcon from '@mui/icons-material/Sensors';
import PowerIcon from '@mui/icons-material/Power';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import SearchIcon from '@mui/icons-material/Search';
import RefreshIcon from '@mui/icons-material/Refresh';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import WifiIcon from '@mui/icons-material/Wifi';
import WifiOffIcon from '@mui/icons-material/WifiOff';
import RouterIcon from '@mui/icons-material/Router';
import MeetingRoomIcon from '@mui/icons-material/MeetingRoom';
import AppLayout from '@/components/layout/AppLayout';
import { useAuth } from '@/hooks/useAuth';
import { useWebSocket } from '@/hooks/useWebSocket';
import { useSensorStore } from '@/stores/sensorStore';
import { apiClient } from '@/lib/api';
import type { DeviceInfo, Room } from '@/types';

// Cihaz tipi ikonlari ve aciklamalari
const deviceTypeConfig: Record<string, { label: string; icon: typeof DeviceThermostatIcon; color: string; description: string }> = {
  combo: {
    label: 'Kombo',
    icon: DeviceThermostatIcon,
    color: '#FF6B35',
    description: 'Sensör + Röle (kombiyi kontrol eder)',
  },
  sensor: {
    label: 'Sensör',
    icon: SensorsIcon,
    color: '#2196F3',
    description: 'Sadece sıcaklık/nem/basınç ölçer',
  },
  relay: {
    label: 'Röle',
    icon: PowerIcon,
    color: '#9C27B0',
    description: 'Sadece röle (kombi kontrolü, sensörsüz)',
  },
};

function getDeviceTypeConfig(type: string) {
  return deviceTypeConfig[type] ?? deviceTypeConfig.sensor;
}

function formatLastSeen(lastSeen: string | null): string {
  if (!lastSeen) return 'Bilinmiyor';
  const date = new Date(lastSeen);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  if (diffMin < 1) return 'Az önce';
  if (diffMin < 60) return `${diffMin} dk önce`;
  const diffH = Math.floor(diffMin / 60);
  if (diffH < 24) return `${diffH} saat önce`;
  return date.toLocaleDateString('tr-TR');
}

interface EditDialogProps {
  device: DeviceInfo | null;
  rooms: Room[];
  onClose: () => void;
  onSave: (deviceId: string, name: string, roomId: number | null) => Promise<void>;
}

function EditDeviceDialog({ device, rooms, onClose, onSave }: EditDialogProps) {
  const [name, setName] = useState(device?.name ?? '');
  const [roomId, setRoomId] = useState<number | null>(device?.roomId ?? null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (device) {
      setName(device.name);
      setRoomId(device.roomId);
    }
  }, [device]);

  const handleSave = async () => {
    if (!device) return;
    setSaving(true);
    try {
      await onSave(device.deviceId, name.trim(), roomId);
      onClose();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={!!device} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Cihaz Düzenle</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '16px !important' }}>
        <TextField
          fullWidth
          label="Cihaz Adı"
          value={name}
          onChange={(e) => setName(e.target.value)}
          autoFocus
        />
        <TextField
          fullWidth
          select
          label="Oda Ataması"
          value={roomId ?? ''}
          onChange={(e) => setRoomId(e.target.value === '' ? null : Number(e.target.value))}
        >
          <MenuItem value="">Oda Atanmamış</MenuItem>
          {rooms.map((room) => (
            <MenuItem key={room.id} value={room.id}>
              {room.name}
            </MenuItem>
          ))}
        </TextField>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} color="inherit">İptal</Button>
        <Button
          onClick={handleSave}
          variant="contained"
          disabled={!name.trim() || saving}
        >
          Kaydet
        </Button>
      </DialogActions>
    </Dialog>
  );
}

interface DeviceCardProps {
  device: DeviceInfo;
  isAdmin: boolean;
  onEdit: (device: DeviceInfo) => void;
  onDelete: (device: DeviceInfo) => void;
}

function DeviceCard({ device, isAdmin, onEdit, onDelete }: DeviceCardProps) {
  const typeConfig = getDeviceTypeConfig(device.type);
  const TypeIcon = typeConfig.icon;

  return (
    <Card
      sx={{
        height: '100%',
        borderLeft: `4px solid ${typeConfig.color}`,
        transition: 'box-shadow 0.2s',
        '&:hover': { boxShadow: 4 },
      }}
    >
      <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
        {/* Üst satır: ikon + isim + online durumu */}
        <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5, mb: 1.5 }}>
          <Box
            sx={{
              p: 1,
              borderRadius: 2,
              bgcolor: `${typeConfig.color}18`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}
          >
            <TypeIcon sx={{ color: typeConfig.color, fontSize: 22 }} />
          </Box>

          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography variant="subtitle1" fontWeight={700} noWrap>
              {device.name || device.deviceId}
            </Typography>
            <Typography variant="caption" color="text.secondary" noWrap>
              {device.deviceId}
            </Typography>
          </Box>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, flexShrink: 0 }}>
            <Chip
              icon={device.online ? <WifiIcon sx={{ fontSize: '14px !important' }} /> : <WifiOffIcon sx={{ fontSize: '14px !important' }} />}
              label={device.online ? 'Çevrimiçi' : 'Çevrimdışı'}
              size="small"
              color={device.online ? 'success' : 'default'}
              variant="outlined"
              sx={{ fontSize: '0.7rem', height: 22 }}
            />
            {isAdmin && (
              <>
                <IconButton
                  size="small"
                  onClick={() => onEdit(device)}
                  sx={{ ml: 0.5 }}
                  aria-label="Cihazı düzenle"
                >
                  <EditIcon fontSize="small" />
                </IconButton>
                <IconButton
                  size="small"
                  color="error"
                  onClick={() => onDelete(device)}
                  aria-label="Cihazı sil"
                >
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </>
            )}
          </Box>
        </Box>

        {/* Cihaz tipi chip */}
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mb: 1.5 }}>
          <Tooltip title={typeConfig.description} arrow>
            <Chip
              label={typeConfig.label}
              size="small"
              sx={{
                bgcolor: `${typeConfig.color}18`,
                color: typeConfig.color,
                fontWeight: 600,
                fontSize: '0.72rem',
                cursor: 'help',
              }}
            />
          </Tooltip>
          {device.firmware && (
            <Chip
              label={`v${device.firmware}`}
              size="small"
              variant="outlined"
              sx={{ fontSize: '0.72rem', height: 22 }}
            />
          )}
        </Box>

        {/* Oda ve IP bilgisi */}
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
            <MeetingRoomIcon sx={{ fontSize: 14, color: 'text.disabled' }} />
            <Typography variant="caption" color="text.secondary">
              {device.roomName ?? 'Oda atanmamış'}
            </Typography>
          </Box>

          {device.ipAddress && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
              <RouterIcon sx={{ fontSize: 14, color: 'text.disabled' }} />
              <Typography variant="caption" color="text.secondary">
                {device.ipAddress}
              </Typography>
            </Box>
          )}

          <Typography variant="caption" color="text.disabled" sx={{ mt: 0.5 }}>
            Son görülme: {formatLastSeen(device.lastSeen)}
          </Typography>
        </Box>
      </CardContent>
    </Card>
  );
}

export default function DevicesPage() {
  const router = useRouter();
  const { isAuthenticated, isLoading: authLoading, isChecked, user } = useAuth();

  const storeDevices = useSensorStore((s) => s.devices);
  const setDevices = useSensorStore((s) => s.setDevices);

  const [rooms, setRooms] = useState<Room[]>([]);
  const [loading, setLoading] = useState(true);
  const [editTarget, setEditTarget] = useState<DeviceInfo | null>(null);

  const isAdmin = user?.role === 'admin';

  useEffect(() => {
    if (isChecked && !isAuthenticated) {
      router.push('/login');
    }
  }, [isChecked, isAuthenticated, router]);

  const loadData = useCallback(async () => {
    if (!isAuthenticated) return;
    try {
      const [devicesData, roomsData] = await Promise.all([
        apiClient.getDevices(),
        apiClient.getRooms(),
      ]);
      setDevices(devicesData);
      setRooms(roomsData);
    } catch (err) {
      console.warn('[Devices] Veriler yüklenemedi:', err);
    } finally {
      setLoading(false);
    }
  }, [isAuthenticated, setDevices]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleSaveDevice = useCallback(
    async (deviceId: string, name: string, roomId: number | null) => {
      await apiClient.updateDevice(deviceId, {
        name,
        room_id: roomId,
      });
      await loadData();
    },
    [loadData],
  );

  const handleDeleteDevice = useCallback(
    async (device: DeviceInfo) => {
      if (!window.confirm(`"${device.name || device.deviceId}" cihazini silmek istediginize emin misiniz?\n\nCihaz tekrar baglangdiginda otomatik kaydedilecektir.`)) return;
      try {
        await apiClient.deleteDevice(device.deviceId);
        await loadData();
      } catch (err) {
        alert(`Cihaz silinemedi: ${err instanceof Error ? err.message : 'Bilinmeyen hata'}`);
      }
    },
    [loadData],
  );

  const [scanning, setScanning] = useState(false);
  const [scanMessage, setScanMessage] = useState('');

  const handleScanDevices = useCallback(async () => {
    setScanning(true);
    setScanMessage('');
    try {
      // Mevcut cihaz sayısını kaydet
      const beforeCount = storeDevices.length;
      // 5 saniye bekle — MQTT telemetri ile yeni cihazlar otomatik kaydedilir
      setScanMessage('MQTT uzerinden cihaz araniyor... (10sn)');
      await new Promise((r) => setTimeout(r, 10000));
      // Tekrar yükle
      const devicesData = await apiClient.getDevices();
      setDevices(devicesData);
      const newCount = devicesData.length - beforeCount;
      if (newCount > 0) {
        setScanMessage(`${newCount} yeni cihaz bulundu!`);
      } else {
        setScanMessage('Yeni cihaz bulunamadi. Cihazin acik ve WiFi\'ye bagli oldugundan emin olun.');
      }
    } catch (err) {
      setScanMessage(`Tarama hatasi: ${err instanceof Error ? err.message : 'Bilinmeyen hata'}`);
    } finally {
      setScanning(false);
      setTimeout(() => setScanMessage(''), 8000);
    }
  }, [storeDevices.length, setDevices]);

  if (!isChecked || !isAuthenticated) return null;

  // Cihazları online > offline sırala
  const deviceList = [...storeDevices].sort((a, b) => {
    if (a.online === b.online) return (a.name || a.deviceId).localeCompare(b.name || b.deviceId);
    return a.online ? -1 : 1;
  });

  const onlineCount = deviceList.filter((d) => d.online).length;

  return (
    <AppLayout>
      <Box>
        {/* Başlık */}
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 3 }}>
          <Box>
            <Typography variant="h5" fontWeight={700}>
              Cihazlar
            </Typography>
            {!loading && (
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                {onlineCount} çevrimiçi / {deviceList.length} toplam
              </Typography>
            )}
          </Box>
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Button
              variant="outlined"
              size="small"
              startIcon={scanning ? <CircularProgress size={16} /> : <SearchIcon />}
              onClick={handleScanDevices}
              disabled={scanning}
            >
              {scanning ? 'Taraniyor...' : 'Cihaz Tara'}
            </Button>
            <IconButton onClick={loadData} size="small" aria-label="Yenile">
              <RefreshIcon />
            </IconButton>
          </Box>
        </Box>

        {scanMessage && (
          <Alert severity={scanMessage.includes('bulundu') ? 'success' : 'info'} sx={{ mb: 2, borderRadius: 2 }}>
            {scanMessage}
          </Alert>
        )}

        {/* Tip açıklamaları */}
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mb: 3 }}>
          {Object.entries(deviceTypeConfig).map(([key, cfg]) => {
            const Icon = cfg.icon;
            return (
              <Chip
                key={key}
                icon={<Icon sx={{ fontSize: '16px !important', color: `${cfg.color} !important` }} />}
                label={`${cfg.label}: ${cfg.description}`}
                size="small"
                variant="outlined"
                sx={{ fontSize: '0.72rem', borderColor: cfg.color, color: 'text.secondary' }}
              />
            );
          })}
        </Box>

        {/* Cihaz kartları */}
        {loading ? (
          <Grid container spacing={2}>
            {[1, 2, 3, 4].map((i) => (
              <Grid item xs={12} sm={6} md={4} key={i}>
                <Skeleton variant="rectangular" height={160} sx={{ borderRadius: 2 }} />
              </Grid>
            ))}
          </Grid>
        ) : deviceList.length === 0 ? (
          <Card>
            <CardContent sx={{ textAlign: 'center', py: 6 }}>
              <DeviceThermostatIcon sx={{ fontSize: 56, color: 'text.disabled', mb: 1.5 }} />
              <Typography variant="h6" color="text.secondary">
                Henüz cihaz kayıtlı değil
              </Typography>
              <Typography variant="body2" color="text.disabled" sx={{ mt: 0.5 }}>
                ESP8266 cihazınız sunucuya bağlandığında burada görünecek
              </Typography>
            </CardContent>
          </Card>
        ) : (
          <Grid container spacing={2}>
            {deviceList.map((device) => (
              <Grid item xs={12} sm={6} md={4} key={device.id}>
                <DeviceCard
                  device={device}
                  isAdmin={isAdmin}
                  onEdit={setEditTarget}
                  onDelete={handleDeleteDevice}
                />
              </Grid>
            ))}
          </Grid>
        )}

        {/* Düzenleme dialog */}
        {isAdmin && (
          <EditDeviceDialog
            device={editTarget}
            rooms={rooms}
            onClose={() => setEditTarget(null)}
            onSave={handleSaveDevice}
          />
        )}
      </Box>
    </AppLayout>
  );
}
