'use client';

import { useEffect, useState, useCallback, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Typography from '@mui/material/Typography';
import Fab from '@mui/material/Fab';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import MenuItem from '@mui/material/MenuItem';
import IconButton from '@mui/material/IconButton';
import Slider from '@mui/material/Slider';
import Drawer from '@mui/material/Drawer';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Skeleton from '@mui/material/Skeleton';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import ToggleButton from '@mui/material/ToggleButton';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import CloseIcon from '@mui/icons-material/Close';
import DeviceThermostatIcon from '@mui/icons-material/DeviceThermostat';
import WifiIcon from '@mui/icons-material/Wifi';
import WifiOffIcon from '@mui/icons-material/WifiOff';
import ThermostatIcon from '@mui/icons-material/Thermostat';
import WaterDropIcon from '@mui/icons-material/WaterDrop';
import CompressIcon from '@mui/icons-material/Compress';
import KitchenIcon from '@mui/icons-material/Kitchen';
import WeekendIcon from '@mui/icons-material/Weekend';
import HotelIcon from '@mui/icons-material/Hotel';
import BathroomIcon from '@mui/icons-material/Bathroom';
import ChildCareIcon from '@mui/icons-material/ChildCare';
import WorkIcon from '@mui/icons-material/Work';
import GarageIcon from '@mui/icons-material/Garage';
import MeetingRoomIcon from '@mui/icons-material/MeetingRoom';
import RoomCard from '@/components/rooms/RoomCard';
import TemperatureChart from '@/components/charts/TemperatureChart';
import AppLayout from '@/components/layout/AppLayout';
import { useAuth } from '@/hooks/useAuth';
import { useWebSocket } from '@/hooks/useWebSocket';
import { useSensorStore } from '@/stores/sensorStore';
import { apiClient } from '@/lib/api';
import type { Room, DeviceInfo, HistoryPoint } from '@/types';

const roomIconOptions = [
  { value: 'kitchen', label: 'Mutfak', Icon: KitchenIcon },
  { value: 'living', label: 'Salon', Icon: WeekendIcon },
  { value: 'bedroom', label: 'Yatak', Icon: HotelIcon },
  { value: 'child', label: 'Cocuk', Icon: ChildCareIcon },
  { value: 'bathroom', label: 'Banyo', Icon: BathroomIcon },
  { value: 'desk', label: 'Calisma', Icon: WorkIcon },
  { value: 'garage', label: 'Garaj', Icon: GarageIcon },
  { value: 'default', label: 'Diger', Icon: MeetingRoomIcon },
];

// --- Oda Detay Drawer ---

interface RoomDetailDrawerProps {
  room: Room | null;
  devices: DeviceInfo[];
  onClose: () => void;
}

function RoomDetailDrawer({ room, devices, onClose }: RoomDetailDrawerProps) {
  const [historyData, setHistoryData] = useState<HistoryPoint[]>([]);
  const [historyRange, setHistoryRange] = useState<'24h' | '7d' | '30d'>('24h');
  const [historyLoading, setHistoryLoading] = useState(false);

  const roomDevices = devices.filter((d) => d.roomId === room?.id);

  useEffect(() => {
    if (!room) return;
    setHistoryLoading(true);
    setHistoryData([]);

    apiClient
      .getSensorHistory(room.id.toString(), historyRange)
      .then(setHistoryData)
      .catch((err) => {
        console.warn('[RoomDetail] Grafik verisi yuklenemedi:', err);
        setHistoryData([]);
      })
      .finally(() => setHistoryLoading(false));
  }, [room, historyRange]);

  return (
    <Drawer
      anchor="right"
      open={!!room}
      onClose={onClose}
      PaperProps={{
        sx: { width: { xs: '100%', sm: 480 }, p: 0 },
      }}
    >
      {room && (
        <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
          {/* Header */}
          <Box
            sx={{
              px: 3,
              py: 2,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              borderBottom: '1px solid',
              borderColor: 'divider',
            }}
          >
            <Typography variant="h6" fontWeight={700}>
              {room.name}
            </Typography>
            <IconButton onClick={onClose} size="small" aria-label="Kapat">
              <CloseIcon />
            </IconButton>
          </Box>

          {/* İçerik */}
          <Box sx={{ flex: 1, overflow: 'auto', p: 2.5, display: 'flex', flexDirection: 'column', gap: 2.5 }}>
            {/* Anlık değerler */}
            <Grid container spacing={1.5}>
              <Grid item xs={4}>
                <Box
                  sx={{
                    p: 1.5,
                    borderRadius: 2,
                    bgcolor: 'action.hover',
                    textAlign: 'center',
                  }}
                >
                  <ThermostatIcon sx={{ color: '#FF6B35', fontSize: 22, mb: 0.5 }} />
                  <Typography variant="h6" fontWeight={700}>
                    {room.currentTemp != null ? `${room.currentTemp.toFixed(1)}°C` : '--'}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Sıcaklık
                  </Typography>
                </Box>
              </Grid>
              <Grid item xs={4}>
                <Box
                  sx={{
                    p: 1.5,
                    borderRadius: 2,
                    bgcolor: 'action.hover',
                    textAlign: 'center',
                  }}
                >
                  <WaterDropIcon sx={{ color: '#2196F3', fontSize: 22, mb: 0.5 }} />
                  <Typography variant="h6" fontWeight={700}>
                    {room.currentHumidity != null ? `%${room.currentHumidity.toFixed(0)}` : '--'}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Nem
                  </Typography>
                </Box>
              </Grid>
              <Grid item xs={4}>
                <Box
                  sx={{
                    p: 1.5,
                    borderRadius: 2,
                    bgcolor: 'action.hover',
                    textAlign: 'center',
                  }}
                >
                  <CompressIcon sx={{ color: '#9C27B0', fontSize: 22, mb: 0.5 }} />
                  <Typography variant="h6" fontWeight={700}>
                    {room.latestReading?.pres != null ? `${room.latestReading.pres.toFixed(0)}` : '--'}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    hPa
                  </Typography>
                </Box>
              </Grid>
            </Grid>

            {/* Oda ağırlığı */}
            <Box>
              <Typography variant="body2" color="text.secondary" gutterBottom>
                Isıtma Ağırlığı: <strong>%{Math.round((room.weight ?? 1) * 100)}</strong>
              </Typography>
              <Box
                sx={{
                  height: 6,
                  borderRadius: 3,
                  bgcolor: 'action.hover',
                  overflow: 'hidden',
                }}
              >
                <Box
                  sx={{
                    height: '100%',
                    width: `${Math.min((room.weight ?? 1) * 100, 100)}%`,
                    bgcolor: 'primary.main',
                    borderRadius: 3,
                    transition: 'width 0.3s ease',
                  }}
                />
              </Box>
            </Box>

            <Divider />

            {/* Bağlı cihazlar */}
            <Box>
              <Typography variant="subtitle2" fontWeight={700} gutterBottom>
                Bağlı Cihazlar ({roomDevices.length})
              </Typography>
              {roomDevices.length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  Bu odaya atanmış cihaz yok
                </Typography>
              ) : (
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                  {roomDevices.map((device) => (
                    <Box
                      key={device.id}
                      sx={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 1.5,
                        p: 1.5,
                        borderRadius: 2,
                        bgcolor: 'action.hover',
                      }}
                    >
                      <DeviceThermostatIcon sx={{ color: 'primary.main', fontSize: 20 }} />
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Typography variant="body2" fontWeight={600} noWrap>
                          {device.name || device.deviceId}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {device.deviceId}
                        </Typography>
                      </Box>
                      <Chip
                        icon={
                          device.online
                            ? <WifiIcon sx={{ fontSize: '12px !important' }} />
                            : <WifiOffIcon sx={{ fontSize: '12px !important' }} />
                        }
                        label={device.online ? 'Çevrimiçi' : 'Çevrimdışı'}
                        size="small"
                        color={device.online ? 'success' : 'default'}
                        variant="outlined"
                        sx={{ fontSize: '0.68rem', height: 20 }}
                      />
                    </Box>
                  ))}
                </Box>
              )}
            </Box>

            <Divider />

            {/* Grafik */}
            <Box>
              <Typography variant="subtitle2" fontWeight={700} gutterBottom>
                Geçmiş Veriler
              </Typography>

              {/* Zaman aralığı seçici */}
              <ToggleButtonGroup
                value={historyRange}
                exclusive
                onChange={(_, v) => { if (v) setHistoryRange(v as '24h' | '7d' | '30d'); }}
                size="small"
                sx={{ mb: 2 }}
              >
                <ToggleButton value="24h">24S</ToggleButton>
                <ToggleButton value="7d">7G</ToggleButton>
                <ToggleButton value="30d">30G</ToggleButton>
              </ToggleButtonGroup>

              {historyLoading ? (
                <Skeleton variant="rectangular" height={220} sx={{ borderRadius: 2 }} />
              ) : historyData.length === 0 ? (
                <Box sx={{ textAlign: 'center', py: 4, color: 'text.secondary' }}>
                  <Typography variant="body2">Bu aralık için veri bulunamadı</Typography>
                </Box>
              ) : (
                <Card variant="outlined">
                  <CardContent sx={{ p: 1, '&:last-child': { pb: 1 } }}>
                    <TemperatureChart
                      data={historyData}
                      timeRange={historyRange}
                      showPressure={false}
                      showHumidity={true}
                    />
                  </CardContent>
                </Card>
              )}
            </Box>
          </Box>
        </Box>
      )}
    </Drawer>
  );
}

// --- Ana Sayfa ---

function RoomsPageInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { isAuthenticated, isLoading } = useAuth();
  useWebSocket();
  const rooms = useSensorStore((s) => s.rooms);
  const devices = useSensorStore((s) => s.devices);
  const setRooms = useSensorStore((s) => s.setRooms);
  const setDevices = useSensorStore((s) => s.setDevices);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editRoom, setEditRoom] = useState<Room | null>(null);
  const [name, setName] = useState('');
  const [icon, setIcon] = useState('default');
  const [weight, setWeight] = useState(0.5);
  const [minTemp, setMinTemp] = useState(15);
  const [saving, setSaving] = useState(false);
  const [deleteConfirm, setDeleteConfirm] = useState<Room | null>(null);
  const [detailRoom, setDetailRoom] = useState<Room | null>(null);

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.push('/login');
    }
  }, [isLoading, isAuthenticated, router]);

  useEffect(() => {
    if (!isAuthenticated) return;
    Promise.all([
      apiClient.getRooms(),
      apiClient.getDevices(),
    ])
      .then(([roomsData, devicesData]) => {
        setRooms(roomsData);
        setDevices(devicesData);
      })
      .catch(() => {});
  }, [isAuthenticated, setRooms, setDevices]);

  // URL param ?id=X ile drawer'ı otomatik aç
  useEffect(() => {
    const idParam = searchParams.get('id');
    if (idParam && Object.keys(rooms).length > 0) {
      const found = rooms[idParam];
      if (found) setDetailRoom(found);
    }
  }, [searchParams, rooms]);

  const refreshRooms = useCallback(async () => {
    const updated = await apiClient.getRooms();
    setRooms(updated);
  }, [setRooms]);

  const openAdd = () => {
    setEditRoom(null);
    setName('');
    setIcon('default');
    setWeight(1.0);
    setMinTemp(15);
    setDialogOpen(true);
  };

  const openEdit = (room: Room) => {
    setEditRoom(room);
    setName(room.name);
    setIcon(room.icon || 'default');
    setWeight(room.weight ?? 0.5);
    setMinTemp(room.minTemp || 15);
    setDialogOpen(true);
  };

  const handleSave = async () => {
    if (!name.trim()) return;
    setSaving(true);
    try {
      if (editRoom) {
        await apiClient.updateRoom(editRoom.id, {
          name: name.trim(),
          icon,
          weight,
          min_temp: minTemp,
        });
      } else {
        await apiClient.createRoom({
          name: name.trim(),
          icon,
          weight,
          min_temp: minTemp,
        });
      }
      await refreshRooms();
      setDialogOpen(false);
    } catch {
      // Handle error
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteConfirm) return;
    try {
      await apiClient.deleteRoom(deleteConfirm.id);
      await refreshRooms();
      setDeleteConfirm(null);
    } catch {
      // Handle error
    }
  };

  const handleRoomClick = (room: Room) => {
    setDetailRoom(room);
  };

  const handleDetailClose = () => {
    setDetailRoom(null);
  };

  if (isLoading || !isAuthenticated) return null;

  const roomList = Object.values(rooms);

  return (
    <AppLayout>
      <Box>
        <Typography variant="h5" fontWeight={700} sx={{ mb: 3 }}>
          Odalar
        </Typography>

        <Grid container spacing={2}>
          {roomList.map((room) => (
            <Grid item xs={12} sm={6} md={4} key={room.id}>
              <Box sx={{ position: 'relative' }}>
                <RoomCard
                  room={room}
                  onClick={() => handleRoomClick(room)}
                />
                <Box sx={{ position: 'absolute', top: 8, right: 8, display: 'flex', gap: 0.5 }}>
                  <IconButton
                    size="small"
                    onClick={(e) => { e.stopPropagation(); openEdit(room); }}
                    sx={{ bgcolor: 'background.paper', boxShadow: 1, '&:hover': { bgcolor: 'action.hover' } }}
                    aria-label="Oda düzenle"
                  >
                    <EditIcon fontSize="small" />
                  </IconButton>
                  <IconButton
                    size="small"
                    onClick={(e) => { e.stopPropagation(); setDeleteConfirm(room); }}
                    sx={{ bgcolor: 'background.paper', boxShadow: 1, '&:hover': { bgcolor: 'error.light', color: 'white' } }}
                    aria-label="Oda sil"
                  >
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Box>
              </Box>
            </Grid>
          ))}
        </Grid>

        {roomList.length === 0 && (
          <Box sx={{ textAlign: 'center', py: 8 }}>
            <Typography color="text.secondary" variant="h6">
              Henuz oda eklenmemis
            </Typography>
            <Typography color="text.secondary" variant="body2" sx={{ mt: 1 }}>
              Asagidaki butona tiklayarak yeni oda ekleyin
            </Typography>
          </Box>
        )}

        <Fab
          color="primary"
          onClick={openAdd}
          sx={{
            position: 'fixed',
            bottom: { xs: 80, md: 24 },
            right: 24,
          }}
          aria-label="Oda Ekle"
        >
          <AddIcon />
        </Fab>

        {/* Add/Edit Dialog */}
        <Dialog
          open={dialogOpen}
          onClose={() => setDialogOpen(false)}
          maxWidth="xs"
          fullWidth
        >
          <DialogTitle>{editRoom ? 'Oda Duzenle' : 'Yeni Oda Ekle'}</DialogTitle>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '16px !important' }}>
            <TextField
              fullWidth
              label="Oda Adi"
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoFocus
            />

            {/* Ikon Seçici Grid */}
            <Box>
              <Typography variant="body2" color="text.secondary" gutterBottom>
                Oda Tipi
              </Typography>
              <Grid container spacing={1}>
                {roomIconOptions.map((opt) => {
                  const selected = icon === opt.value;
                  return (
                    <Grid item xs={3} key={opt.value}>
                      <Box
                        onClick={() => setIcon(opt.value)}
                        sx={{
                          display: 'flex',
                          flexDirection: 'column',
                          alignItems: 'center',
                          gap: 0.5,
                          p: 1,
                          borderRadius: 2,
                          cursor: 'pointer',
                          border: '2px solid',
                          borderColor: selected ? 'primary.main' : 'divider',
                          bgcolor: selected ? 'primary.main' + '18' : 'action.hover',
                          transition: 'all 0.15s ease',
                          '&:hover': { borderColor: 'primary.main', bgcolor: 'primary.main' + '10' },
                        }}
                      >
                        <opt.Icon
                          sx={{
                            fontSize: 26,
                            color: selected ? 'primary.main' : 'text.secondary',
                          }}
                        />
                        <Typography
                          variant="caption"
                          sx={{
                            fontSize: '0.65rem',
                            color: selected ? 'primary.main' : 'text.secondary',
                            fontWeight: selected ? 700 : 400,
                            textAlign: 'center',
                            lineHeight: 1.2,
                          }}
                        >
                          {opt.label}
                        </Typography>
                      </Box>
                    </Grid>
                  );
                })}
              </Grid>
            </Box>

            <Box>
              {(() => {
                const pct = Math.round(weight * 100);
                return (
                  <>
                    <Typography variant="body2" color="text.secondary" gutterBottom>
                      Isitma Agirligi: <strong>%{pct}</strong>
                    </Typography>
                    <Slider
                      value={weight}
                      onChange={(_, v) => setWeight(v as number)}
                      min={0}
                      max={1.0}
                      step={0.01}
                      valueLabelDisplay="auto"
                      valueLabelFormat={(v) => `%${Math.round(v * 100)}`}
                      marks={[
                        { value: 0, label: '%0' },
                        { value: 0.25, label: '%25' },
                        { value: 0.5, label: '%50' },
                        { value: 0.75, label: '%75' },
                        { value: 1.0, label: '%100' },
                      ]}
                    />
                  </>
                );
              })()}
            </Box>
            <TextField
              fullWidth
              type="number"
              label="Minimum Sicaklik (°C)"
              value={minTemp}
              onChange={(e) => setMinTemp(parseFloat(e.target.value) || 15)}
              inputProps={{ min: 5, max: 25, step: 0.5 }}
            />
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={() => setDialogOpen(false)} color="inherit">
              Iptal
            </Button>
            <Button
              onClick={handleSave}
              variant="contained"
              disabled={!name.trim() || saving}
            >
              {editRoom ? 'Kaydet' : 'Ekle'}
            </Button>
          </DialogActions>
        </Dialog>

        {/* Delete Confirmation Dialog */}
        <Dialog
          open={!!deleteConfirm}
          onClose={() => setDeleteConfirm(null)}
          maxWidth="xs"
        >
          <DialogTitle>Oda Sil</DialogTitle>
          <DialogContent>
            <Typography>
              &quot;{deleteConfirm?.name}&quot; odasini silmek istediginize emin misiniz?
              Bu odaya atanmis cihazlar odadan cikarilacaktir.
            </Typography>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDeleteConfirm(null)}>Iptal</Button>
            <Button onClick={handleDelete} color="error" variant="contained">
              Sil
            </Button>
          </DialogActions>
        </Dialog>

        {/* Oda Detay Drawer */}
        <RoomDetailDrawer
          room={detailRoom}
          devices={devices}
          onClose={handleDetailClose}
        />
      </Box>
    </AppLayout>
  );
}

export default function RoomsPage() {
  return (
    <Suspense>
      <RoomsPageInner />
    </Suspense>
  );
}