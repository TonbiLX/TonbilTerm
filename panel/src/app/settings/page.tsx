'use client';

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Accordion from '@mui/material/Accordion';
import AccordionSummary from '@mui/material/AccordionSummary';
import AccordionDetails from '@mui/material/AccordionDetails';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import MenuItem from '@mui/material/MenuItem';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import ListItemSecondaryAction from '@mui/material/ListItemSecondaryAction';
import Switch from '@mui/material/Switch';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Alert from '@mui/material/Alert';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import IconButton from '@mui/material/IconButton';
import CircularProgress from '@mui/material/CircularProgress';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import GroupIcon from '@mui/icons-material/Group';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import DevicesIcon from '@mui/icons-material/Devices';
import HomeIcon from '@mui/icons-material/Home';
import PersonIcon from '@mui/icons-material/Person';
import InfoIcon from '@mui/icons-material/Info';
import SensorsIcon from '@mui/icons-material/Sensors';
import WifiIcon from '@mui/icons-material/Wifi';
import WifiOffIcon from '@mui/icons-material/WifiOff';
import DownloadIcon from '@mui/icons-material/Download';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import NotificationsIcon from '@mui/icons-material/Notifications';
import PaletteIcon from '@mui/icons-material/Palette';
import LockIcon from '@mui/icons-material/Lock';
import LocationOnIcon from '@mui/icons-material/LocationOn';
import SearchIcon from '@mui/icons-material/Search';
import AppLayout from '@/components/layout/AppLayout';
import { useAuth } from '@/hooks/useAuth';
import { apiClient } from '@/lib/api';
import { useUiStore } from '@/stores/uiStore';
import type { HeatingConfig, DeviceInfo, Room, ThemeMode, UserInfo } from '@/types';

const themeOptions: Array<{ value: ThemeMode; label: string }> = [
  { value: 'light', label: 'Aydinlik' },
  { value: 'dark', label: 'Karanlik' },
  { value: 'system', label: 'Sistem' },
];

export default function SettingsPage() {
  const router = useRouter();
  const { isAuthenticated, isLoading: authLoading, isChecked, user } = useAuth();
  const isAdmin = user?.role === 'admin';
  const themeMode = useUiStore((s) => s.theme);
  const setTheme = useUiStore((s) => s.setTheme);

  const [devices, setDevices] = useState<DeviceInfo[]>([]);
  const [rooms, setRoomsList] = useState<Room[]>([]);
  const [config, setConfig] = useState<HeatingConfig | null>(null);
  const [notifications, setNotifications] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [editingDevice, setEditingDevice] = useState<DeviceInfo | null>(null);
  const [editDeviceName, setEditDeviceName] = useState('');
  const [editDeviceRoom, setEditDeviceRoom] = useState<number | null>(null);

  // Konum ayarı state
  const [locationQuery, setLocationQuery] = useState('');
  const [locationResults, setLocationResults] = useState<Array<{ name: string; district: string; city: string; country: string; latitude: number; longitude: number; full_address: string }>>([]);
  const [locationSearching, setLocationSearching] = useState(false);
  const [currentLocation, setCurrentLocation] = useState<{ city: string; latitude: number; longitude: number } | null>(null);
  const [locationSaved, setLocationSaved] = useState(false);

  // Şifre değiştirme state
  const [pwOld, setPwOld] = useState('');
  const [pwNew, setPwNew] = useState('');
  const [pwConfirm, setPwConfirm] = useState('');
  const [pwSaving, setPwSaving] = useState(false);
  const [pwError, setPwError] = useState<string | null>(null);
  const [pwSuccess, setPwSuccess] = useState(false);

  // Kullanici Yonetimi state
  const [usersList, setUsersList] = useState<UserInfo[]>([]);
  const [usersLoading, setUsersLoading] = useState(false);
  const [userDialogOpen, setUserDialogOpen] = useState(false);
  const [userDialogMode, setUserDialogMode] = useState<'create' | 'edit'>('create');
  const [editingUser, setEditingUser] = useState<UserInfo | null>(null);
  const [userFormEmail, setUserFormEmail] = useState('');
  const [userFormPassword, setUserFormPassword] = useState('');
  const [userFormDisplayName, setUserFormDisplayName] = useState('');
  const [userFormRole, setUserFormRole] = useState('user');
  const [userFormActive, setUserFormActive] = useState(true);
  const [userFormSaving, setUserFormSaving] = useState(false);
  const [userError, setUserError] = useState<string | null>(null);

  useEffect(() => {
    if (isChecked && !isAuthenticated) {
      router.push('/login');
    }
  }, [isChecked, isAuthenticated, router]);

  useEffect(() => {
    if (!isAuthenticated) return;
    apiClient.getDevices().then(setDevices).catch(() => {});
    apiClient.getRooms().then(setRoomsList).catch(() => {});
    apiClient.getHeatingConfig().then(setConfig).catch(() => {});
    // Mevcut konum bilgisini yükle
    fetch('/api/weather/location', { credentials: 'include' })
      .then((r) => r.json())
      .then((j) => { if (j.success && j.data) setCurrentLocation(j.data); })
      .catch(() => {});
  }, [isAuthenticated]);

  const handleEditDevice = useCallback((device: DeviceInfo) => {
    setEditingDevice(device);
    setEditDeviceName(device.name || '');
    setEditDeviceRoom(device.roomId);
  }, []);

  const handleSaveDevice = useCallback(async () => {
    if (!editingDevice) return;
    setSaving(true);
    try {
      await apiClient.updateDevice(editingDevice.deviceId, {
        name: editDeviceName || undefined,
        room_id: editDeviceRoom,
      });
      // Refresh device list
      const updated = await apiClient.getDevices();
      setDevices(updated);
      setEditingDevice(null);
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    } catch {
      // Handle error
    } finally {
      setSaving(false);
    }
  }, [editingDevice, editDeviceName, editDeviceRoom]);

  const handleRelayCommand = useCallback(async (deviceId: string, command: string) => {
    try {
      await apiClient.sendDeviceCommand(deviceId, command);
    } catch {
      // Handle error
    }
  }, []);

  const handleConfigSave = useCallback(async () => {
    if (!config) return;
    setSaving(true);
    try {
      // Convert camelCase to snake_case for backend
      const result = await apiClient.updateHeatingConfig({
        target_temp: config.targetTemp,
        hysteresis: config.hysteresis,
        min_cycle_min: config.minCycleMin,
        mode: config.mode,
        strategy: config.strategy,
        gas_price_per_m3: config.gasPricePerM3,
        floor_area_m2: config.floorAreaM2,
        boiler_power_kw: config.boilerPowerKw,
        flow_temp: config.flowTemp,
      });
      setConfig(result);
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    } catch {
      // Handle error
    } finally {
      setSaving(false);
    }
  }, [config]);

  const loadUsers = useCallback(async () => {
    setUsersLoading(true);
    try {
      const list = await apiClient.getUsers();
      setUsersList(list);
    } catch {
      // sessiz fail
    } finally {
      setUsersLoading(false);
    }
  }, []);

  const openCreateUserDialog = useCallback(() => {
    setUserDialogMode('create');
    setEditingUser(null);
    setUserFormEmail('');
    setUserFormPassword('');
    setUserFormDisplayName('');
    setUserError(null);
    setUserDialogOpen(true);
  }, []);

  const openEditUserDialog = useCallback((u: UserInfo) => {
    setUserDialogMode('edit');
    setEditingUser(u);
    setUserFormEmail(u.email);
    setUserFormPassword('');
    setUserFormDisplayName(u.displayName);
    setUserFormRole(u.role || 'user');
    setUserFormActive(u.isActive !== false);
    setUserError(null);
    setUserDialogOpen(true);
  }, []);

  const closeUserDialog = useCallback(() => {
    setUserDialogOpen(false);
    setEditingUser(null);
    setUserError(null);
  }, []);

  const handleUserFormSubmit = useCallback(async () => {
    setUserError(null);
    if (userDialogMode === 'create') {
      if (!userFormEmail.trim() || !userFormPassword.trim()) {
        setUserError('E-posta ve sifre zorunludur.');
        return;
      }
    }
    setUserFormSaving(true);
    try {
      if (userDialogMode === 'create') {
        await apiClient.createUser(userFormEmail.trim(), userFormPassword, userFormDisplayName.trim());
      } else if (editingUser) {
        await apiClient.updateUser(editingUser.id, {
          displayName: userFormDisplayName.trim(),
          email: userFormEmail.trim() !== editingUser.email ? userFormEmail.trim() : undefined,
          role: userFormRole !== editingUser.role ? userFormRole : undefined,
          isActive: userFormActive !== editingUser.isActive ? userFormActive : undefined,
        });
      }
      await loadUsers();
      closeUserDialog();
    } catch (err) {
      setUserError(err instanceof Error ? err.message : 'Bir hata olustu.');
    } finally {
      setUserFormSaving(false);
    }
  }, [userDialogMode, userFormEmail, userFormPassword, userFormDisplayName, editingUser, loadUsers, closeUserDialog]);

  const handleDeleteUser = useCallback(async (u: UserInfo) => {
    if (!window.confirm(`"${u.email}" kullanicisini silmek istediginize emin misiniz?`)) return;
    try {
      await apiClient.deleteUser(u.id);
      await loadUsers();
    } catch {
      // sessiz fail
    }
  }, [loadUsers]);

  const handleChangePassword = useCallback(async () => {
    setPwError(null);
    if (!pwOld.trim()) { setPwError('Mevcut sifre gerekli.'); return; }
    if (pwNew.length < 6) { setPwError('Yeni sifre en az 6 karakter olmali.'); return; }
    if (pwNew !== pwConfirm) { setPwError('Yeni sifreler uyusmuyor.'); return; }
    setPwSaving(true);
    try {
      await apiClient.changePassword(pwOld, pwNew);
      setPwSuccess(true);
      setPwOld('');
      setPwNew('');
      setPwConfirm('');
      setTimeout(() => setPwSuccess(false), 3000);
    } catch (err) {
      setPwError(err instanceof Error ? err.message : 'Sifre degistirilemedi.');
    } finally {
      setPwSaving(false);
    }
  }, [pwOld, pwNew, pwConfirm]);

  const handleLocationSearch = useCallback(async () => {
    if (locationQuery.trim().length < 2) return;
    setLocationSearching(true);
    setLocationResults([]);
    try {
      const results = await apiClient.searchLocation(locationQuery.trim());
      setLocationResults(results as typeof locationResults);
    } catch {
      // sessiz fail
    } finally {
      setLocationSearching(false);
    }
  }, [locationQuery]);

  const handleLocationSelect = useCallback(async (loc: typeof locationResults[0]) => {
    try {
      await apiClient.setWeatherLocation({
        latitude: loc.latitude,
        longitude: loc.longitude,
        city: loc.city || loc.name,
        district: loc.district || loc.name,
      });
      setCurrentLocation({ city: loc.full_address || loc.name, latitude: loc.latitude, longitude: loc.longitude });
      setLocationResults([]);
      setLocationQuery('');
      setLocationSaved(true);
      setTimeout(() => setLocationSaved(false), 3000);
    } catch {
      // sessiz fail
    }
  }, []);

  const handleExport = useCallback(async () => {
    try {
      const data = {
        config,
        devices,
        schedule: await apiClient.getSchedule(),
        rooms: await apiClient.getRooms(),
        exportedAt: new Date().toISOString(),
      };
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `tonbilterm-backup-${new Date().toISOString().slice(0, 10)}.json`;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      // Handle error
    }
  }, [config, devices]);

  if (!isChecked || !isAuthenticated) return null;

  return (
    <AppLayout>
      <Box>
        <Typography variant="h5" fontWeight={700} sx={{ mb: 3 }}>
          Ayarlar
        </Typography>

        {saved && (
          <Alert severity="success" sx={{ mb: 2, borderRadius: 3 }}>
            Ayarlar basariyla kaydedildi
          </Alert>
        )}

        {/* Cihaz Yonetimi */}
        <Accordion defaultExpanded sx={{ mb: 1 }}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <DevicesIcon sx={{ mr: 2, color: 'primary.main' }} />
            <Typography fontWeight={600}>Cihaz Yonetimi</Typography>
          </AccordionSummary>
          <AccordionDetails>
            <List disablePadding>
              {devices.length === 0 && (
                <ListItem>
                  <ListItemText primary="Kayitli cihaz bulunamadi" />
                </ListItem>
              )}
              {devices.map((device) => {
                const isRelay = device.type === 'relay' || device.type === 'combo';
                const roomName = rooms.find((r) => r.id === device.roomId)?.name;
                return (
                  <Box key={device.id}>
                    <ListItem sx={{ px: 0, flexWrap: 'wrap', gap: 1 }}>
                      <ListItemIcon>
                        <SensorsIcon color={device.online ? 'success' : 'disabled'} />
                      </ListItemIcon>
                      <ListItemText
                        primary={
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <Typography fontWeight={600}>
                              {device.name || device.deviceId}
                            </Typography>
                            <Chip label={device.type} size="small" variant="outlined" />
                            {device.online ? (
                              <Chip icon={<WifiIcon />} label="Cevrimici" size="small" color="success" variant="outlined" />
                            ) : (
                              <Chip icon={<WifiOffIcon />} label="Cevrimdisi" size="small" variant="outlined" />
                            )}
                          </Box>
                        }
                        secondary={
                          <Box component="span">
                            {`Oda: ${roomName || 'Atanmamis'} | ID: ${device.deviceId} | FW: ${device.firmware || '-'}`}
                            {device.lastSeen && ` | Son: ${new Date(device.lastSeen).toLocaleString('tr-TR')}`}
                          </Box>
                        }
                      />
                      <Box sx={{ display: 'flex', gap: 0.5, ml: 'auto' }}>
                        {isRelay && (
                          <>
                            <Button
                              size="small"
                              variant="outlined"
                              color="success"
                              onClick={() => handleRelayCommand(device.deviceId, 'relay_on')}
                              disabled={!device.online}
                            >
                              AC
                            </Button>
                            <Button
                              size="small"
                              variant="outlined"
                              color="error"
                              onClick={() => handleRelayCommand(device.deviceId, 'relay_off')}
                              disabled={!device.online}
                            >
                              KAPA
                            </Button>
                          </>
                        )}
                        {isAdmin && (
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={() => handleEditDevice(device)}
                          >
                            Duzenle
                          </Button>
                        )}
                      </Box>
                    </ListItem>
                    <Divider sx={{ my: 0.5 }} />
                  </Box>
                );
              })}
            </List>

            {/* Device Edit Dialog */}
            {editingDevice && (
              <Box sx={{ mt: 2, p: 2, bgcolor: 'action.hover', borderRadius: 2 }}>
                <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 2 }}>
                  Cihaz Duzenle: {editingDevice.deviceId}
                </Typography>
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  <TextField
                    label="Cihaz Adi"
                    value={editDeviceName}
                    onChange={(e) => setEditDeviceName(e.target.value)}
                    fullWidth
                    size="small"
                  />
                  <TextField
                    select
                    label="Oda Atamasi"
                    value={editDeviceRoom ?? ''}
                    onChange={(e) => setEditDeviceRoom(e.target.value ? Number(e.target.value) : null)}
                    fullWidth
                    size="small"
                  >
                    <MenuItem value="">Oda Yok</MenuItem>
                    {rooms.map((room) => (
                      <MenuItem key={room.id} value={room.id}>
                        {room.name}
                      </MenuItem>
                    ))}
                  </TextField>
                  <Box sx={{ display: 'flex', gap: 1 }}>
                    <Button
                      variant="contained"
                      onClick={handleSaveDevice}
                      disabled={saving}
                      size="small"
                    >
                      Kaydet
                    </Button>
                    <Button
                      variant="outlined"
                      onClick={() => setEditingDevice(null)}
                      size="small"
                    >
                      Iptal
                    </Button>
                  </Box>
                </Box>
              </Box>
            )}
          </AccordionDetails>
        </Accordion>

        {/* Ev Ayarlari */}
        <Accordion sx={{ mb: 1 }}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <HomeIcon sx={{ mr: 2, color: 'primary.main' }} />
            <Typography fontWeight={600}>Ev Ayarlari</Typography>
          </AccordionSummary>
          <AccordionDetails>
            {config && (
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                <TextField
                  select
                  label="Sicaklik Stratejisi"
                  value={config.strategy || 'weighted_avg'}
                  onChange={(e) =>
                    setConfig({ ...config, strategy: e.target.value })
                  }
                  fullWidth
                  size="small"
                  helperText="Mevcut sicaklik hesaplanirken hangi yontem kullanilsin"
                >
                  <MenuItem value="weighted_avg">Agirlikli Ortalama</MenuItem>
                  <MenuItem value="coldest_room">En Soguk Oda</MenuItem>
                  <MenuItem value="hottest_room">En Sicak Oda</MenuItem>
                  <MenuItem value="single_room">Birincil Oda</MenuItem>
                </TextField>
                <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
                  <TextField
                    label="Taban Alani (m2)"
                    type="number"
                    value={config.floorAreaM2}
                    onChange={(e) =>
                      setConfig({ ...config, floorAreaM2: parseFloat(e.target.value) || 0 })
                    }
                    sx={{ flex: 1, minWidth: 150 }}
                  />
                  <TextField
                    label="Kombi Gucu (kW)"
                    type="number"
                    value={config.boilerPowerKw}
                    onChange={(e) =>
                      setConfig({ ...config, boilerPowerKw: parseFloat(e.target.value) || 0 })
                    }
                    sx={{ flex: 1, minWidth: 150 }}
                  />
                </Box>
                <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
                  <TextField
                    label="Gaz Fiyati (TL/m3)"
                    type="number"
                    value={config.gasPricePerM3}
                    onChange={(e) =>
                      setConfig({ ...config, gasPricePerM3: parseFloat(e.target.value) || 0 })
                    }
                    sx={{ flex: 1, minWidth: 150 }}
                  />
                  <TextField
                    label="Hysteresis (°C)"
                    type="number"
                    value={config.hysteresis}
                    onChange={(e) =>
                      setConfig({ ...config, hysteresis: parseFloat(e.target.value) || 0 })
                    }
                    inputProps={{ step: 0.1, min: 0.1, max: 2 }}
                    sx={{ flex: 1, minWidth: 150 }}
                  />
                </Box>
                <Button
                  variant="contained"
                  onClick={handleConfigSave}
                  disabled={saving}
                  sx={{ alignSelf: 'flex-start' }}
                >
                  {saving ? 'Kaydediliyor...' : 'Kaydet'}
                </Button>
              </Box>
            )}
          </AccordionDetails>
        </Accordion>

        {/* Konum Ayarlari */}
        <Accordion sx={{ mb: 1 }}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <LocationOnIcon sx={{ mr: 2, color: 'primary.main' }} />
            <Typography fontWeight={600}>Konum Ayarlari</Typography>
          </AccordionSummary>
          <AccordionDetails>
            {currentLocation && (
              <Alert severity="info" sx={{ mb: 2, borderRadius: 2 }}>
                Mevcut konum: <strong>{currentLocation.city}</strong> ({currentLocation.latitude?.toFixed(4)}, {currentLocation.longitude?.toFixed(4)})
              </Alert>
            )}
            {locationSaved && (
              <Alert severity="success" sx={{ mb: 2, borderRadius: 2 }}>
                Konum basariyla kaydedildi
              </Alert>
            )}
            <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
              <TextField
                label="Sehir veya ilce ara"
                value={locationQuery}
                onChange={(e) => setLocationQuery(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') handleLocationSearch(); }}
                fullWidth
                size="small"
                placeholder="Ornek: Kadikoy, Istanbul"
              />
              <Button
                variant="contained"
                onClick={handleLocationSearch}
                disabled={locationSearching || locationQuery.trim().length < 2}
                sx={{ minWidth: 100 }}
              >
                {locationSearching ? <CircularProgress size={20} color="inherit" /> : <SearchIcon />}
              </Button>
            </Box>
            {locationResults.length > 0 && (
              <List disablePadding sx={{ bgcolor: 'action.hover', borderRadius: 2, mb: 1 }}>
                {locationResults.map((loc, i) => (
                  <Box key={i}>
                    <ListItem
                      component="div"
                      sx={{ cursor: 'pointer', '&:hover': { bgcolor: 'action.selected' } }}
                      onClick={() => handleLocationSelect(loc)}
                    >
                      <ListItemIcon>
                        <LocationOnIcon color="primary" />
                      </ListItemIcon>
                      <ListItemText
                        primary={loc.full_address || loc.name}
                        secondary={`${loc.latitude.toFixed(4)}, ${loc.longitude.toFixed(4)}`}
                      />
                    </ListItem>
                    {i < locationResults.length - 1 && <Divider />}
                  </Box>
                ))}
              </List>
            )}
            <Typography variant="caption" color="text.secondary">
              Hava durumu verisi icin konumunuzu secin. Konum bilgisi Open-Meteo API ile kullanilir.
            </Typography>
          </AccordionDetails>
        </Accordion>

        {/* Kullanici Yonetimi — sadece admin gorebilir */}
        {isAdmin && <Accordion
          sx={{ mb: 1 }}
          onChange={(_e, expanded) => { if (expanded && usersList.length === 0) loadUsers(); }}
        >
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <GroupIcon sx={{ mr: 2, color: 'primary.main' }} />
            <Typography fontWeight={600}>Kullanici Yonetimi</Typography>
          </AccordionSummary>
          <AccordionDetails>
            <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
              <Button
                variant="contained"
                startIcon={<PersonAddIcon />}
                size="small"
                onClick={openCreateUserDialog}
              >
                Yeni Kullanici
              </Button>
            </Box>

            {usersLoading ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
                <CircularProgress size={28} />
              </Box>
            ) : (
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell><Typography variant="caption" fontWeight={700}>E-posta</Typography></TableCell>
                      <TableCell><Typography variant="caption" fontWeight={700}>Ad Soyad</Typography></TableCell>
                      <TableCell><Typography variant="caption" fontWeight={700}>Durum</Typography></TableCell>
                      <TableCell align="right"><Typography variant="caption" fontWeight={700}>Islemler</Typography></TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {usersList.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={4}>
                          <Typography variant="body2" color="text.secondary" sx={{ py: 1 }}>
                            Kullanici bulunamadi
                          </Typography>
                        </TableCell>
                      </TableRow>
                    )}
                    {usersList.map((u) => {
                      const isSelf = user?.id === u.id;
                      return (
                        <TableRow key={u.id} hover>
                          <TableCell>
                            <Typography variant="body2">{u.email}</Typography>
                          </TableCell>
                          <TableCell>
                            <Typography variant="body2">{u.displayName || '—'}</Typography>
                          </TableCell>
                          <TableCell>
                            <Chip
                              label={u.isActive ? 'Aktif' : 'Pasif'}
                              size="small"
                              color={u.isActive ? 'success' : 'default'}
                              variant="outlined"
                            />
                            {isSelf && (
                              <Chip label="Ben" size="small" color="primary" variant="outlined" sx={{ ml: 0.5 }} />
                            )}
                          </TableCell>
                          <TableCell align="right">
                            <IconButton
                              size="small"
                              onClick={() => openEditUserDialog(u)}
                              title="Duzenle"
                            >
                              <EditIcon fontSize="small" />
                            </IconButton>
                            <IconButton
                              size="small"
                              color="error"
                              onClick={() => handleDeleteUser(u)}
                              disabled={isSelf}
                              title={isSelf ? 'Kendinizi silemezsiniz' : 'Sil'}
                            >
                              <DeleteIcon fontSize="small" />
                            </IconButton>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </AccordionDetails>
        </Accordion>}

        {/* Kullanici Yonetimi Dialog — sadece admin acabilir */}
        {isAdmin &&
        <Dialog open={userDialogOpen} onClose={closeUserDialog} maxWidth="xs" fullWidth>
          <DialogTitle>
            {userDialogMode === 'create' ? 'Yeni Kullanici Ekle' : 'Kullanici Duzenle'}
          </DialogTitle>
          <DialogContent>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
              {userError && (
                <Alert severity="error" sx={{ borderRadius: 2 }}>
                  {userError}
                </Alert>
              )}
              <TextField
                label="Kullanici Adi / E-posta"
                value={userFormEmail}
                onChange={(e) => setUserFormEmail(e.target.value)}
                fullWidth
                size="small"
                autoComplete="off"
                helperText="E-posta formati zorunlu degil"
              />
              {userDialogMode === 'create' && (
                <TextField
                  label="Sifre"
                  type="password"
                  value={userFormPassword}
                  onChange={(e) => setUserFormPassword(e.target.value)}
                  fullWidth
                  size="small"
                  autoComplete="new-password"
                />
              )}
              <TextField
                label="Ad Soyad"
                value={userFormDisplayName}
                onChange={(e) => setUserFormDisplayName(e.target.value)}
                fullWidth
                size="small"
              />
              <TextField
                select
                label="Rol"
                value={userFormRole}
                onChange={(e) => setUserFormRole(e.target.value)}
                fullWidth
                size="small"
              >
                <MenuItem value="admin">Admin</MenuItem>
                <MenuItem value="user">Kullanici</MenuItem>
              </TextField>
              {userDialogMode === 'edit' && (
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <Typography variant="body2">Hesap Aktif</Typography>
                  <Switch
                    checked={userFormActive}
                    onChange={(e) => setUserFormActive(e.target.checked)}
                    color="primary"
                  />
                </Box>
              )}
            </Box>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={closeUserDialog} variant="outlined" size="small">
              Iptal
            </Button>
            <Button
              onClick={handleUserFormSubmit}
              variant="contained"
              size="small"
              disabled={userFormSaving}
            >
              {userFormSaving ? 'Kaydediliyor...' : (userDialogMode === 'create' ? 'Ekle' : 'Kaydet')}
            </Button>
          </DialogActions>
        </Dialog>}

        {/* Kullanici */}
        <Accordion sx={{ mb: 1 }}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <PersonIcon sx={{ mr: 2, color: 'primary.main' }} />
            <Typography fontWeight={600}>Kullanici</Typography>
          </AccordionSummary>
          <AccordionDetails>
            <List disablePadding>
              {user && (
                <>
                  <ListItem sx={{ px: 0 }}>
                    <ListItemText primary="E-posta" secondary={user.email} />
                  </ListItem>
                  <Divider />
                </>
              )}
              <ListItem sx={{ px: 0 }}>
                <ListItemIcon>
                  <PaletteIcon />
                </ListItemIcon>
                <ListItemText primary="Tema" />
                <ListItemSecondaryAction>
                  <Box sx={{ display: 'flex', gap: 0.5 }}>
                    {themeOptions.map((opt) => (
                      <Chip
                        key={opt.value}
                        label={opt.label}
                        size="small"
                        variant={themeMode === opt.value ? 'filled' : 'outlined'}
                        color={themeMode === opt.value ? 'primary' : 'default'}
                        onClick={() => setTheme(opt.value)}
                      />
                    ))}
                  </Box>
                </ListItemSecondaryAction>
              </ListItem>
              <Divider />
              <ListItem sx={{ px: 0 }}>
                <ListItemIcon>
                  <NotificationsIcon />
                </ListItemIcon>
                <ListItemText primary="Bildirimler" />
                <ListItemSecondaryAction>
                  <Switch
                    checked={notifications}
                    onChange={(e) => setNotifications(e.target.checked)}
                  />
                </ListItemSecondaryAction>
              </ListItem>
            </List>
          </AccordionDetails>
        </Accordion>

        {/* Sifre Degistir */}
        <Accordion sx={{ mb: 1 }}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <LockIcon sx={{ mr: 2, color: 'primary.main' }} />
            <Typography fontWeight={600}>Sifre Degistir</Typography>
          </AccordionSummary>
          <AccordionDetails>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, maxWidth: 400 }}>
              {pwError && (
                <Alert severity="error" sx={{ borderRadius: 2 }}>
                  {pwError}
                </Alert>
              )}
              {pwSuccess && (
                <Alert severity="success" sx={{ borderRadius: 2 }}>
                  Sifre basariyla guncellendi
                </Alert>
              )}
              <TextField
                label="Mevcut Sifre"
                type="password"
                value={pwOld}
                onChange={(e) => setPwOld(e.target.value)}
                fullWidth
                size="small"
                autoComplete="current-password"
              />
              <TextField
                label="Yeni Sifre"
                type="password"
                value={pwNew}
                onChange={(e) => setPwNew(e.target.value)}
                fullWidth
                size="small"
                autoComplete="new-password"
                helperText="En az 6 karakter"
              />
              <TextField
                label="Yeni Sifre (Tekrar)"
                type="password"
                value={pwConfirm}
                onChange={(e) => setPwConfirm(e.target.value)}
                fullWidth
                size="small"
                autoComplete="new-password"
                error={pwConfirm.length > 0 && pwNew !== pwConfirm}
                helperText={pwConfirm.length > 0 && pwNew !== pwConfirm ? 'Sifreler uyusmuyor' : ''}
              />
              <Button
                variant="contained"
                onClick={handleChangePassword}
                disabled={pwSaving}
                sx={{ alignSelf: 'flex-start' }}
              >
                {pwSaving ? 'Kaydediliyor...' : 'Sifre Degistir'}
              </Button>
            </Box>
          </AccordionDetails>
        </Accordion>

        {/* Sistem */}
        <Accordion sx={{ mb: 1 }}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <InfoIcon sx={{ mr: 2, color: 'primary.main' }} />
            <Typography fontWeight={600}>Sistem</Typography>
          </AccordionSummary>
          <AccordionDetails>
            <List disablePadding>
              <ListItem sx={{ px: 0 }}>
                <Button
                  startIcon={<DownloadIcon />}
                  onClick={handleExport}
                  variant="outlined"
                  fullWidth
                >
                  Veriyi Disa Aktar
                </Button>
              </ListItem>
              <ListItem sx={{ px: 0 }}>
                <Button
                  startIcon={<RestartAltIcon />}
                  color="error"
                  variant="outlined"
                  fullWidth
                  onClick={() => {
                    if (window.confirm('Fabrika ayarlarina sifirlamak istediginize emin misiniz?')) {
                      // Factory reset through first relay device
                      const relayDevice = devices.find((d) => d.type === 'relay' || d.type === 'combo');
                      if (relayDevice) {
                        apiClient.sendDeviceCommand(relayDevice.deviceId, 'reboot').catch(() => {});
                      }
                    }
                  }}
                >
                  Fabrika Sifirlama
                </Button>
              </ListItem>
              <Divider sx={{ my: 1 }} />
              <ListItem sx={{ px: 0 }}>
                <ListItemText
                  primary="TonbilTerm v1.0.0"
                  secondary="Akilli Termostat Kontrol Paneli"
                />
              </ListItem>
            </List>
          </AccordionDetails>
        </Accordion>
      </Box>
    </AppLayout>
  );
}
