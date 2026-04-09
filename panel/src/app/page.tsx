'use client';

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Typography from '@mui/material/Typography';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Skeleton from '@mui/material/Skeleton';
import IconButton from '@mui/material/IconButton';
import Collapse from '@mui/material/Collapse';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import LocalFireDepartmentIcon from '@mui/icons-material/LocalFireDepartment';
import CloudIcon from '@mui/icons-material/Cloud';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import DeviceThermostatIcon from '@mui/icons-material/DeviceThermostat';
import AirIcon from '@mui/icons-material/Air';
import CloseIcon from '@mui/icons-material/Close';
import WarningIcon from '@mui/icons-material/Warning';
import ErrorIcon from '@mui/icons-material/Error';
import InfoIcon from '@mui/icons-material/Info';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import BoltIcon from '@mui/icons-material/Bolt';
import ThermostatDial from '@/components/thermostat/ThermostatDial';
import ModeSelector from '@/components/thermostat/ModeSelector';
import RoomCard from '@/components/rooms/RoomCard';
import StatusCard from '@/components/ui/StatusCard';
import TemperatureChart from '@/components/charts/TemperatureChart';
import AppLayout from '@/components/layout/AppLayout';
import { useAuth } from '@/hooks/useAuth';
import { useWebSocket } from '@/hooks/useWebSocket';
import { useSensorStore } from '@/stores/sensorStore';
import { apiClient } from '@/lib/api';
import type { HistoryPoint, Alert, EnergyStats, EnergySummary, Room } from '@/types';

// Strateji bazlı mevcut sıcaklık hesabı
function calcCurrentTemp(rooms: Record<string, Room>, strategy: string): number | null {
  const roomList = Object.values(rooms);
  if (roomList.length === 0) return null;

  const temps: Array<{ temp: number; weight: number }> = [];
  for (const room of roomList) {
    const t = room.latestReading?.temp ?? room.currentTemp;
    if (t != null && t > 0) {
      temps.push({ temp: t, weight: room.weight ?? 1 });
    }
  }
  if (temps.length === 0) return null;

  switch (strategy) {
    case 'weighted_avg': {
      const totalWeight = temps.reduce((s, r) => s + r.weight, 0);
      if (totalWeight === 0) return null;
      return temps.reduce((s, r) => s + r.temp * r.weight, 0) / totalWeight;
    }
    case 'coldest_room':
      return Math.min(...temps.map((r) => r.temp));
    case 'hottest_room':
      return Math.max(...temps.map((r) => r.temp));
    case 'single_room': {
      // En yüksek ağırlıklı odanın sıcaklığını al
      const primary = temps.reduce((a, b) => (b.weight > a.weight ? b : a), temps[0]);
      return primary.temp;
    }
    default: {
      // Basit ortalama (fallback)
      return temps.reduce((s, r) => s + r.temp, 0) / temps.length;
    }
  }
}

const strategyLabel: Record<string, string> = {
  weighted_avg: 'Ağırlıklı Ort.',
  coldest_room: 'En Soğuk Oda',
  hottest_room: 'En Sıcak Oda',
  single_room: 'Birincil Oda',
};

const severityIcons = {
  critical: ErrorIcon,
  warning: WarningIcon,
  info: InfoIcon,
  success: CheckCircleIcon,
};

const severityColors = {
  critical: '#F44336',
  warning: '#FF9800',
  info: '#2196F3',
  success: '#4CAF50',
};

export default function DashboardPage() {
  const router = useRouter();
  const { isAuthenticated, isLoading: authLoading, isChecked } = useAuth();
  const { connected } = useWebSocket();
  const rooms = useSensorStore((s) => s.rooms);
  const boilerStatus = useSensorStore((s) => s.boilerStatus);
  const weather = useSensorStore((s) => s.outdoorWeather);
  const setRooms = useSensorStore((s) => s.setRooms);
  const setDevices = useSensorStore((s) => s.setDevices);
  const updateBoilerStatus = useSensorStore((s) => s.updateBoilerStatus);
  const updateWeather = useSensorStore((s) => s.updateWeather);

  const [historyData, setHistoryData] = useState<HistoryPoint[]>([]);
  const [historyRange, setHistoryRange] = useState<'24h' | '7d' | '30d'>('24h');
  const [selectedRoom, setSelectedRoom] = useState<number | 'all'>('all');
  const [initialLoading, setInitialLoading] = useState(true);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [dismissedAlerts, setDismissedAlerts] = useState<string[]>([]);
  const [thermalAnalysis, setThermalAnalysis] = useState<Record<string, unknown> | null>(null);
  const [energyStats, setEnergyStats] = useState<EnergyStats | null>(null);
  const [energySummary, setEnergySummary] = useState<EnergySummary | null>(null);
  const [strategy, setStrategy] = useState<string>('weighted_avg');

  // Boost state
  const [boostActive, setBoostActive] = useState(false);
  const [boostRemainingMinutes, setBoostRemainingMinutes] = useState(0);
  const [boostLoading, setBoostLoading] = useState(false);

  // Redirect to login if not authenticated — isChecked bekle (sayfa yenileme koruması)
  // isError durumunda da login'e yonlendir (ag hatasi vs.)
  useEffect(() => {
    if (!authLoading && !isAuthenticated) {
      router.replace('/login');
    }
  }, [authLoading, isAuthenticated, router]);

  // Initial data fetch
  useEffect(() => {
    if (!isAuthenticated) return;

    const loadInitialData = async () => {
      try {
        const [roomsData, configData, weatherData, devicesData, energyData, energyDailyData] =
          await Promise.allSettled([
            apiClient.getRooms(),
            apiClient.getHeatingConfig(),
            apiClient.getWeather(),
            apiClient.getDevices(),
            apiClient.getEnergyStats(),
            apiClient.getEnergyDaily(7),
          ]);

        // Devices must be loaded BEFORE rooms so WS can match device_id -> room_id
        if (devicesData.status === 'fulfilled') setDevices(devicesData.value);
        if (roomsData.status === 'fulfilled') setRooms(roomsData.value);
        if (configData.status === 'fulfilled') {
          const cfg = configData.value;
          updateBoilerStatus({
            relay: cfg.relayState || false,
            mode: cfg.mode,
            target: cfg.targetTemp,
          });
          if (cfg.strategy) setStrategy(cfg.strategy);
        }
        if (weatherData.status === 'fulfilled') updateWeather(weatherData.value);
        if (energyData.status === 'fulfilled') setEnergyStats(energyData.value);
        if (energyDailyData.status === 'fulfilled') setEnergySummary(energyDailyData.value.summary);
      } catch (err) {
        console.warn('[Dashboard] Baslangic verileri yuklenemedi, WebSocket ile yenilenecek:', err);
      } finally {
        setInitialLoading(false);
      }
    };

    loadInitialData();
  }, [isAuthenticated, setRooms, setDevices, updateBoilerStatus, updateWeather]);

  // Load chart data
  useEffect(() => {
    if (!isAuthenticated) return;

    const loadHistory = async () => {
      try {
        const deviceOrRoom = selectedRoom === 'all' ? 'all' : selectedRoom.toString();
        const data = await apiClient.getSensorHistory(deviceOrRoom, historyRange);
        setHistoryData(data);
      } catch (err) {
        console.warn('[Dashboard] Sensor gecmisi yuklenemedi:', err);
        setHistoryData([]);
      }
    };

    loadHistory();
  }, [isAuthenticated, historyRange, selectedRoom]);

  // Load alerts
  useEffect(() => {
    if (!isAuthenticated) return;

    const loadAlerts = async () => {
      try {
        const result = await apiClient.getAlerts();
        // Generate stable IDs for alerts
        const alertsWithIds = (result.alerts || []).map((a: Alert, i: number) => ({
          ...a,
          id: a.id || `${a.type}-${a.severity}-${i}`,
        }));
        setAlerts(alertsWithIds);
      } catch (err) {
        console.warn('[Dashboard] Uyarilar yuklenemedi:', err);
        setAlerts([]);
      }
    };

    setDismissedAlerts(apiClient.getDismissedAlerts());
    loadAlerts();

    // Load thermal analysis
    apiClient.getThermalAnalysis().then(setThermalAnalysis).catch((err) => {
      console.warn('[Dashboard] Termal analiz yuklenemedi:', err);
    });

    // Refresh alerts every 5 minutes
    const interval = setInterval(loadAlerts, 5 * 60 * 1000);
    return () => clearInterval(interval);
  }, [isAuthenticated]);

  const handleDismissAlert = useCallback((alertId: string) => {
    apiClient.dismissAlert(alertId);
    setDismissedAlerts((prev) => [...prev, alertId]);
  }, []);

  // Boost functions
  const fetchBoostStatus = useCallback(async () => {
    try {
      const status = await apiClient.getBoostStatus();
      const active = (status.active as boolean) || false;
      setBoostActive(active);
      if (active) {
        const remaining = (status.remaining_minutes as number) || 0;
        setBoostRemainingMinutes(Math.round(remaining));
      } else {
        setBoostRemainingMinutes(0);
      }
    } catch {
      // sessiz fail
    }
  }, []);

  const handleActivateBoost = useCallback(async (minutes: number) => {
    setBoostLoading(true);
    try {
      await apiClient.activateBoost(minutes);
      await fetchBoostStatus();
    } catch {
      // sessiz fail
    } finally {
      setBoostLoading(false);
    }
  }, [fetchBoostStatus]);

  const handleCancelBoost = useCallback(async () => {
    setBoostLoading(true);
    try {
      await apiClient.cancelBoost();
      setBoostActive(false);
      setBoostRemainingMinutes(0);
    } catch {
      // sessiz fail
    } finally {
      setBoostLoading(false);
    }
  }, []);

  // Boost status poll - her 30 saniyede
  useEffect(() => {
    if (!isAuthenticated) return;
    fetchBoostStatus();
    const interval = setInterval(fetchBoostStatus, 30_000);
    return () => clearInterval(interval);
  }, [isAuthenticated, fetchBoostStatus]);

  // Auth yukleniyor VEYA henuz giris yapilmamis → loading goster (redirect useEffect'te yapilir)
  if (authLoading || !isAuthenticated) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh' }}>
        <Skeleton variant="circular" width={300} height={300} />
      </Box>
    );
  }

  const roomList = Object.values(rooms);

  // Helper: dakika sayısını okunabilir stringe çevirir (ör: "1s 23dk" veya "45dk")
  function formatMinutes(min: number): string {
    const rounded = Math.round(min);
    if (rounded <= 0) return '0dk';
    const h = Math.floor(rounded / 60);
    const m = rounded % 60;
    return h > 0 ? `${h}s ${m}dk` : `${m}dk`;
  }

  // Günlük çalışma süresi: energy API'den gelir, boilerStatus.uptime'a bağımlı değil
  const todayMinutes = energyStats?.todayMinutes ?? 0;
  const weeklyMinutes = energySummary?.totalRuntimeMinutes ?? 0;
  const uptimeDisplay = formatMinutes(todayMinutes);

  // Strateji bazlı mevcut iç sıcaklık
  const currentIndoorTemp = calcCurrentTemp(rooms, strategy);
  const currentIndoorDisplay = currentIndoorTemp != null ? `${currentIndoorTemp.toFixed(1)}°C` : '--';
  const currentStrategyLabel = strategyLabel[strategy] ?? 'Ortalama';

  // Filter out dismissed alerts
  const visibleAlerts = alerts.filter((a) => !dismissedAlerts.includes(a.id));

  return (
    <AppLayout>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
        {/* Hero - Thermostat Dial */}
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
          {initialLoading ? (
            <Skeleton variant="circular" width={300} height={300} />
          ) : (
            <ThermostatDial
              disabled={
                boilerStatus.mode === 'manual' ||
                boilerStatus.mode === 'manual_on' ||
                boilerStatus.mode === 'manual_off'
              }
              currentTemp={currentIndoorTemp ?? 0}
            />
          )}
          <ModeSelector />

          {/* Strateji Secici */}
          <Box
            sx={{
              display: 'flex',
              gap: 1,
              flexWrap: 'wrap',
              justifyContent: 'center',
            }}
          >
            {Object.entries(strategyLabel).map(([value, label]) => (
              <Chip
                key={value}
                label={label}
                size="small"
                variant={strategy === value ? 'filled' : 'outlined'}
                color={strategy === value ? 'primary' : 'default'}
                onClick={() => {
                  setStrategy(value);
                  apiClient.updateHeatingConfig({ strategy: value }).catch(() => {});
                }}
                sx={{
                  fontWeight: strategy === value ? 700 : 400,
                }}
              />
            ))}
          </Box>

          {/* Boost Kontrolü */}
          <Card sx={{ width: '100%', maxWidth: 420 }}>
            <CardContent sx={{ py: 1.5, px: 2, '&:last-child': { pb: 1.5 } }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                <BoltIcon sx={{ color: '#FF9800', fontSize: 20 }} />
                <Typography variant="subtitle2" fontWeight={700}>
                  Boost Modu
                </Typography>
                {boostActive && (
                  <Chip
                    label={`${boostRemainingMinutes}dk kaldi`}
                    size="small"
                    color="warning"
                    variant="filled"
                    sx={{ ml: 'auto', fontWeight: 700 }}
                  />
                )}
              </Box>
              {boostActive ? (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Typography variant="body2" color="text.secondary" sx={{ flex: 1 }}>
                    Kombi maksimum isitmada calisiyor
                  </Typography>
                  <Button
                    size="small"
                    variant="outlined"
                    color="error"
                    onClick={handleCancelBoost}
                    disabled={boostLoading}
                  >
                    Iptal
                  </Button>
                </Box>
              ) : (
                <Box sx={{ display: 'flex', gap: 1 }}>
                  {[15, 30, 60].map((min) => (
                    <Button
                      key={min}
                      size="small"
                      variant="outlined"
                      color="warning"
                      onClick={() => handleActivateBoost(min)}
                      disabled={boostLoading}
                      sx={{ flex: 1, fontWeight: 600 }}
                    >
                      {min}dk
                    </Button>
                  ))}
                </Box>
              )}
            </CardContent>
          </Card>
        </Box>

        {/* Status cards row */}
        <Grid container spacing={2}>
          <Grid item xs={6} md={2.4}>
            <StatusCard
              icon={LocalFireDepartmentIcon}
              title="Kombi"
              value={boilerStatus.relay ? 'Calisiyor' : 'Kapali'}
              subtitle={boilerStatus.mode === 'auto' ? 'Otomatik' : boilerStatus.mode === 'schedule' ? 'Programli' : 'Manuel'}
              color={boilerStatus.relay ? '#FF6B35' : '#9E9E9E'}
            />
          </Grid>
          <Grid item xs={6} md={2.4}>
            <StatusCard
              icon={DeviceThermostatIcon}
              title="Ic Sicaklik"
              value={currentIndoorDisplay}
              subtitle={currentStrategyLabel}
              color="#4CAF50"
            />
          </Grid>
          <Grid item xs={6} md={2.4}>
            <StatusCard
              icon={AccessTimeIcon}
              title="Calisma Suresi"
              value={uptimeDisplay}
              subtitle={energyStats ? `${energyStats.efficiencyPct.toFixed(0)}% verimlilik` : 'Bugun'}
              color="#8B4513"
              toggle={{
                primaryLabel: 'Gunluk',
                secondaryLabel: 'Haftalik',
                secondaryValue: formatMinutes(weeklyMinutes),
                secondarySubtitle: 'Son 7 gun toplam',
              }}
            />
          </Grid>
          <Grid item xs={6} md={2.4}>
            <StatusCard
              icon={CloudIcon}
              title="Dis Hava"
              value={`${weather.temp.toFixed(1)}°C`}
              subtitle={weather.description}
              color="#2196F3"
            />
          </Grid>
          <Grid item xs={6} md={2.4}>
            <StatusCard
              icon={AirIcon}
              title="Ruzgar"
              value={`${weather.windSpeed.toFixed(1)} km/s`}
              subtitle={`Hissedilen: ${weather.feelsLike.toFixed(1)}°C`}
              color="#00BCD4"
            />
          </Grid>
        </Grid>

        {/* Rooms */}
        <Box>
          <Typography variant="h6" fontWeight={600} sx={{ mb: 2 }}>
            Odalar
          </Typography>
          {roomList.length > 0 ? (
            <Grid container spacing={2}>
              {roomList.map((room) => (
                <Grid item xs={12} sm={6} md={4} key={room.id}>
                  <RoomCard
                    room={room}
                    onClick={() => router.push(`/rooms?id=${room.id}`)}
                  />
                </Grid>
              ))}
            </Grid>
          ) : (
            <Card>
              <CardContent sx={{ textAlign: 'center', py: 4 }}>
                <DeviceThermostatIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 1 }} />
                <Typography color="text.secondary">
                  Henuz oda eklenmemis
                </Typography>
              </CardContent>
            </Card>
          )}
        </Box>

        {/* Thermal Analysis - Indoor vs Outdoor */}
        {thermalAnalysis && (() => {
          const indoor = thermalAnalysis.indoor as Record<string, unknown> | undefined;
          const outdoor = thermalAnalysis.outdoor as Record<string, unknown> | undefined;
          const balance = thermalAnalysis.thermal_balance as Record<string, unknown> | undefined;
          const recs = thermalAnalysis.recommendations as Array<Record<string, unknown>> | undefined;
          const humAnalysis = thermalAnalysis.humidity_analysis as Record<string, unknown> | undefined;

          return (
            <Card>
              <CardContent>
                <Typography variant="h6" fontWeight={600} sx={{ mb: 2 }}>
                  Termal Analiz
                </Typography>
                <Grid container spacing={2}>
                  <Grid item xs={12} sm={6}>
                    <Box sx={{ p: 2, bgcolor: 'action.hover', borderRadius: 2 }}>
                      <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                        Ic Ortam
                      </Typography>
                      <Typography variant="h4" fontWeight={700} color="primary">
                        {indoor?.avg_temp ? `${String(indoor.avg_temp)}°C` : '--'}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {'Nem: %' + String(indoor?.avg_humidity ?? '--')}
                      </Typography>
                    </Box>
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <Box sx={{ p: 2, bgcolor: 'action.hover', borderRadius: 2 }}>
                      <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                        Dis Ortam
                      </Typography>
                      <Typography variant="h4" fontWeight={700} color="info.main">
                        {outdoor?.temp != null ? `${String(outdoor.temp)}°C` : '--'}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {String(outdoor?.description ?? '--') + ' | Nem: %' + String(outdoor?.humidity ?? '--')}
                      </Typography>
                    </Box>
                  </Grid>

                  {balance && (
                    <Grid item xs={12}>
                      <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', mt: 1 }}>
                        <Box sx={{ flex: 1, minWidth: 140, p: 1.5, bgcolor: '#F4433618', borderRadius: 2, textAlign: 'center' }}>
                          <Typography variant="caption" color="text.secondary">Isi Kaybi</Typography>
                          <Typography variant="h6" fontWeight={600} color="error">
                            {String(balance.heat_loss_kcal_h)} kcal/s
                          </Typography>
                        </Box>
                        <Box sx={{ flex: 1, minWidth: 140, p: 1.5, bgcolor: '#FF6B3518', borderRadius: 2, textAlign: 'center' }}>
                          <Typography variant="caption" color="text.secondary">Kombi Cikti</Typography>
                          <Typography variant="h6" fontWeight={600} sx={{ color: '#FF6B35' }}>
                            {String(balance.boiler_output_kcal_h)} kcal/s
                          </Typography>
                        </Box>
                        <Box sx={{ flex: 1, minWidth: 140, p: 1.5, bgcolor: balance.relay_on ? '#4CAF5018' : '#2196F318', borderRadius: 2, textAlign: 'center' }}>
                          <Typography variant="caption" color="text.secondary">Net Denge</Typography>
                          <Typography variant="h6" fontWeight={600} color={Number(balance.net_heating_kcal_h) >= 0 ? 'success.main' : 'info.main'}>
                            {String(balance.net_heating_kcal_h)} kcal/s
                          </Typography>
                        </Box>
                      </Box>
                    </Grid>
                  )}

                  {recs && recs.length > 0 && (
                    <Grid item xs={12}>
                      <Typography variant="subtitle2" fontWeight={600} sx={{ mt: 1, mb: 1 }}>
                        Oneriler
                      </Typography>
                      {recs.map((rec, i) => (
                        <Box key={i} sx={{ p: 1.5, mb: 1, bgcolor: 'action.hover', borderRadius: 2, borderLeft: '3px solid', borderColor: rec.priority === 'high' ? 'error.main' : rec.priority === 'medium' ? 'warning.main' : 'info.main' }}>
                          <Typography variant="subtitle2" fontWeight={600}>
                            {String(rec.title)}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            {String(rec.message)}
                          </Typography>
                        </Box>
                      ))}
                    </Grid>
                  )}

                  {humAnalysis && (
                    <Grid item xs={12}>
                      <Box sx={{ p: 1.5, bgcolor: 'action.hover', borderRadius: 2 }}>
                        <Typography variant="subtitle2" fontWeight={600}>
                          Nem Durumu
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {String(humAnalysis.advice)}
                        </Typography>
                      </Box>
                    </Grid>
                  )}
                </Grid>
              </CardContent>
            </Card>
          );
        })()}

        {/* Temperature chart */}
        <Card>
          <CardContent>
            {/* Room selector chips */}
            <Box
              sx={{
                display: 'flex',
                gap: 1,
                mb: 2,
                overflowX: 'auto',
                pb: 0.5,
                '&::-webkit-scrollbar': { height: 4 },
                '&::-webkit-scrollbar-thumb': { bgcolor: 'divider', borderRadius: 2 },
              }}
            >
              <Chip
                label="Tumu"
                size="small"
                variant={selectedRoom === 'all' ? 'filled' : 'outlined'}
                color={selectedRoom === 'all' ? 'primary' : 'default'}
                onClick={() => setSelectedRoom('all')}
                sx={{
                  fontWeight: selectedRoom === 'all' ? 700 : 400,
                  flexShrink: 0,
                }}
              />
              {roomList
                .filter((r) => (r.deviceCount ?? 0) > 0)
                .map((room) => (
                  <Chip
                    key={room.id}
                    label={room.name}
                    size="small"
                    variant={selectedRoom === room.id ? 'filled' : 'outlined'}
                    color={selectedRoom === room.id ? 'primary' : 'default'}
                    onClick={() => setSelectedRoom(room.id)}
                    sx={{
                      fontWeight: selectedRoom === room.id ? 700 : 400,
                      flexShrink: 0,
                    }}
                  />
                ))}
            </Box>
            <TemperatureChart
              data={historyData}
              timeRange={historyRange}
              onTimeRangeChange={setHistoryRange}
              showPressure={true}
              title={
                selectedRoom === 'all'
                  ? 'Tum Sensorler'
                  : roomList.find((r) => r.id === selectedRoom)?.name ?? 'Sensorler'
              }
            />
          </CardContent>
        </Card>
      </Box>
    </AppLayout>
  );
}
