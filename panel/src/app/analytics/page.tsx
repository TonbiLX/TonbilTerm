'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Typography from '@mui/material/Typography';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import ToggleButton from '@mui/material/ToggleButton';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import { useTheme } from '@mui/material/styles';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  LineChart,
  Line,
  Legend,
  Area,
  AreaChart,
  ComposedChart,
} from 'recharts';
import LocalFireDepartmentIcon from '@mui/icons-material/LocalFireDepartment';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import EnergySavingsLeafIcon from '@mui/icons-material/EnergySavingsLeaf';
import GasMeterIcon from '@mui/icons-material/GasMeter';
import BoltIcon from '@mui/icons-material/Bolt';
import ThermostatIcon from '@mui/icons-material/Thermostat';
import StatusCard from '@/components/ui/StatusCard';
import AppLayout from '@/components/layout/AppLayout';
import { useAuth } from '@/hooks/useAuth';
import { apiClient } from '@/lib/api';
import type { EnergyStats, EnergyDaily, EnergySummary } from '@/types';

export default function AnalyticsPage() {
  const router = useRouter();
  const theme = useTheme();
  const { isAuthenticated, isChecked } = useAuth();
  const [stats, setStats] = useState<EnergyStats | null>(null);
  const [dailyData, setDailyData] = useState<EnergyDaily[]>([]);
  const [summary, setSummary] = useState<EnergySummary | null>(null);
  const [runtimeRange, setRuntimeRange] = useState<7 | 30>(7);
  const [efficiency, setEfficiency] = useState<Record<string, unknown> | null>(null);
  const [prediction, setPrediction] = useState<Record<string, unknown> | null>(null);

  useEffect(() => {
    if (isChecked && !isAuthenticated) {
      router.push('/login');
    }
  }, [isChecked, isAuthenticated, router]);

  // Load current energy stats
  useEffect(() => {
    if (!isAuthenticated) return;
    apiClient.getEnergyStats().then(setStats).catch(() => {});
    apiClient.getEnergyEfficiency().then(setEfficiency).catch(() => {});
    apiClient.getEnergyPredict(8).then(setPrediction).catch(() => {});
  }, [isAuthenticated]);

  // Load daily data
  useEffect(() => {
    if (!isAuthenticated) return;
    apiClient
      .getEnergyDaily(runtimeRange)
      .then((result) => {
        setDailyData(result.days);
        setSummary(result.summary);
      })
      .catch(() => {
        setDailyData([]);
        setSummary(null);
      });
  }, [isAuthenticated, runtimeRange]);

  if (!isChecked || !isAuthenticated) return null;

  const todayHours = stats ? (stats.todayMinutes / 60).toFixed(1) : '0';
  const periodHours = summary ? (summary.totalRuntimeMinutes / 60).toFixed(1) : '0';

  // Chart data (reversed to show oldest→newest)
  const chartData = [...dailyData].reverse().map((d) => ({
    ...d,
    hours: +(d.runtimeMinutes / 60).toFixed(1),
    cost: +d.costTl.toFixed(2),
    gas: +d.gasM3.toFixed(3),
    kwh: +d.thermalKwh.toFixed(1),
    duty: +d.dutyCyclePct.toFixed(1),
    label: new Date(d.date).toLocaleDateString('tr-TR', {
      day: 'numeric',
      month: 'short',
    }),
  }));

  // Prediction curve data
  const predictionCurve = prediction
    ? ((prediction as Record<string, unknown>).prediction_curve as Array<Record<string, unknown>> || []).map((p) => ({
        hours: p.hours as number,
        heatingOn: +((p.heating_on as number) ?? 0).toFixed(1),
        heatingOff: +((p.heating_off as number) ?? 0).toFixed(1),
        label: `${p.hours}s`,
      }))
    : [];

  // Efficiency table
  const efficiencyTable = efficiency
    ? ((efficiency as Record<string, unknown>).efficiency_table as Array<Record<string, unknown>> || [])
    : [];

  const effCurrent = efficiency
    ? (efficiency as Record<string, unknown>).current as Record<string, unknown> | undefined
    : null;

  return (
    <AppLayout>
      <Box>
        <Typography variant="h5" fontWeight={700} sx={{ mb: 3 }}>
          Enerji Analizi
        </Typography>

        {/* ---- Summary Cards ---- */}
        <Grid container spacing={2} sx={{ mb: 3 }}>
          <Grid item xs={6} md={3}>
            <StatusCard
              icon={LocalFireDepartmentIcon}
              title="Bugun"
              value={`${todayHours} saat`}
              subtitle={stats ? `${stats.todayCost.toFixed(2)} TL` : ''}
              color="#FF6B35"
            />
          </Grid>
          <Grid item xs={6} md={3}>
            <StatusCard
              icon={GasMeterIcon}
              title="Gaz Tuketimi"
              value={stats ? `${stats.todayGasM3.toFixed(2)} m3` : '-- m3'}
              subtitle={stats ? `${stats.todayKwh.toFixed(1)} kWh` : ''}
              color="#8B4513"
            />
          </Grid>
          <Grid item xs={6} md={3}>
            <StatusCard
              icon={AttachMoneyIcon}
              title={`${runtimeRange} Gun Toplam`}
              value={summary ? `${summary.totalCostTl.toFixed(2)} TL` : '-- TL'}
              subtitle={summary ? `${summary.totalGasM3.toFixed(2)} m3 gaz` : ''}
              color="#2E7D32"
            />
          </Grid>
          <Grid item xs={6} md={3}>
            <StatusCard
              icon={EnergySavingsLeafIcon}
              title="Verimlilik"
              value={stats ? `%${stats.efficiencyPct.toFixed(0)}` : '--%'}
              subtitle={stats?.isCondensing ? 'Yogusma aktif' : 'Konvansiyonel mod'}
              color={stats?.isCondensing ? '#0288D1' : '#FF9800'}
            />
          </Grid>
        </Grid>

        {/* ---- Daily Runtime + Cost Combined Chart ---- */}
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
              <Typography variant="h6" fontWeight={600}>
                Gunluk Calisma & Maliyet
              </Typography>
              <ToggleButtonGroup
                value={runtimeRange}
                exclusive
                onChange={(_, val) => val && setRuntimeRange(val)}
                size="small"
              >
                <ToggleButton value={7}>7 Gun</ToggleButton>
                <ToggleButton value={30}>30 Gun</ToggleButton>
              </ToggleButtonGroup>
            </Box>
            <ResponsiveContainer width="100%" height={300}>
              <ComposedChart data={chartData} margin={{ top: 5, right: 20, left: -10, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={theme.palette.divider} />
                <XAxis
                  dataKey="label"
                  tick={{ fontSize: 11, fill: theme.palette.text.secondary }}
                  tickLine={false}
                />
                <YAxis
                  yAxisId="hours"
                  tick={{ fontSize: 11, fill: theme.palette.text.secondary }}
                  tickLine={false}
                  axisLine={false}
                  unit="s"
                />
                <YAxis
                  yAxisId="cost"
                  orientation="right"
                  tick={{ fontSize: 11, fill: '#8B4513' }}
                  tickLine={false}
                  axisLine={false}
                  unit=" TL"
                />
                <Tooltip
                  contentStyle={{
                    backgroundColor: theme.palette.background.paper,
                    border: `1px solid ${theme.palette.divider}`,
                    borderRadius: 12,
                  }}
                  formatter={(value: number, name: string) => {
                    if (name === 'hours') return [`${value} saat`, 'Calisma'];
                    if (name === 'cost') return [`${value.toFixed(2)} TL`, 'Maliyet'];
                    if (name === 'gas') return [`${value.toFixed(3)} m3`, 'Gaz'];
                    return [value, name];
                  }}
                />
                <Legend
                  formatter={(value: string) => {
                    if (value === 'hours') return 'Saat';
                    if (value === 'cost') return 'Maliyet (TL)';
                    return value;
                  }}
                />
                <Bar
                  yAxisId="hours"
                  dataKey="hours"
                  fill="#FF6B35"
                  radius={[6, 6, 0, 0]}
                  maxBarSize={40}
                  opacity={0.8}
                />
                <Line
                  yAxisId="cost"
                  type="monotone"
                  dataKey="cost"
                  stroke="#8B4513"
                  strokeWidth={2.5}
                  dot={false}
                  activeDot={{ r: 5 }}
                />
              </ComposedChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        {/* ---- Gas Consumption & Thermal Output ---- */}
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Typography variant="h6" fontWeight={600} sx={{ mb: 2 }}>
              Gaz Tuketimi & Termal Cikti
            </Typography>
            <ResponsiveContainer width="100%" height={250}>
              <AreaChart data={chartData} margin={{ top: 5, right: 20, left: -10, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={theme.palette.divider} />
                <XAxis
                  dataKey="label"
                  tick={{ fontSize: 11, fill: theme.palette.text.secondary }}
                  tickLine={false}
                />
                <YAxis
                  yAxisId="gas"
                  tick={{ fontSize: 11, fill: theme.palette.text.secondary }}
                  tickLine={false}
                  axisLine={false}
                  unit=" m3"
                />
                <YAxis
                  yAxisId="kwh"
                  orientation="right"
                  tick={{ fontSize: 11, fill: '#FF6B35' }}
                  tickLine={false}
                  axisLine={false}
                  unit=" kWh"
                />
                <Tooltip
                  contentStyle={{
                    backgroundColor: theme.palette.background.paper,
                    border: `1px solid ${theme.palette.divider}`,
                    borderRadius: 12,
                  }}
                  formatter={(value: number, name: string) => {
                    if (name === 'gas') return [`${value.toFixed(3)} m3`, 'Gaz'];
                    if (name === 'kwh') return [`${value.toFixed(1)} kWh`, 'Termal'];
                    return [value, name];
                  }}
                />
                <Legend
                  formatter={(value: string) => {
                    if (value === 'gas') return 'Gaz (m3)';
                    if (value === 'kwh') return 'Termal (kWh)';
                    return value;
                  }}
                />
                <Area
                  yAxisId="gas"
                  type="monotone"
                  dataKey="gas"
                  stroke="#2E7D32"
                  fill="#2E7D32"
                  fillOpacity={0.15}
                  strokeWidth={2}
                />
                <Area
                  yAxisId="kwh"
                  type="monotone"
                  dataKey="kwh"
                  stroke="#FF6B35"
                  fill="#FF6B35"
                  fillOpacity={0.1}
                  strokeWidth={2}
                />
              </AreaChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        {/* ---- Temperature Prediction Curve ---- */}
        {predictionCurve.length > 0 && (
          <Card sx={{ mb: 3 }}>
            <CardContent>
              <Typography variant="h6" fontWeight={600} sx={{ mb: 1 }}>
                Sicaklik Tahmini (8 Saat)
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Kombi acik/kapali senaryolari
              </Typography>
              <ResponsiveContainer width="100%" height={220}>
                <LineChart data={predictionCurve} margin={{ top: 5, right: 10, left: -10, bottom: 5 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke={theme.palette.divider} />
                  <XAxis
                    dataKey="label"
                    tick={{ fontSize: 11, fill: theme.palette.text.secondary }}
                    tickLine={false}
                  />
                  <YAxis
                    tick={{ fontSize: 11, fill: theme.palette.text.secondary }}
                    tickLine={false}
                    axisLine={false}
                    unit="°C"
                    domain={['auto', 'auto']}
                  />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: theme.palette.background.paper,
                      border: `1px solid ${theme.palette.divider}`,
                      borderRadius: 12,
                    }}
                    formatter={(value: number, name: string) => {
                      if (name === 'heatingOn') return [`${value}°C`, 'Kombi Acik'];
                      if (name === 'heatingOff') return [`${value}°C`, 'Kombi Kapali'];
                      return [value, name];
                    }}
                  />
                  <Legend
                    formatter={(value: string) => {
                      if (value === 'heatingOn') return 'Kombi Acik';
                      if (value === 'heatingOff') return 'Kombi Kapali';
                      return value;
                    }}
                  />
                  <Line
                    type="monotone"
                    dataKey="heatingOn"
                    stroke="#FF6B35"
                    strokeWidth={2.5}
                    dot={false}
                  />
                  <Line
                    type="monotone"
                    dataKey="heatingOff"
                    stroke="#2196F3"
                    strokeWidth={2}
                    dot={false}
                    strokeDasharray="5 3"
                  />
                </LineChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        )}

        {/* ---- Duty Cycle Chart ---- */}
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Typography variant="h6" fontWeight={600} sx={{ mb: 2 }}>
              Gunluk Gorev Dongusu (Duty Cycle)
            </Typography>
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={chartData} margin={{ top: 5, right: 10, left: -10, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={theme.palette.divider} />
                <XAxis
                  dataKey="label"
                  tick={{ fontSize: 11, fill: theme.palette.text.secondary }}
                  tickLine={false}
                />
                <YAxis
                  tick={{ fontSize: 11, fill: theme.palette.text.secondary }}
                  tickLine={false}
                  axisLine={false}
                  unit="%"
                  domain={[0, 100]}
                />
                <Tooltip
                  contentStyle={{
                    backgroundColor: theme.palette.background.paper,
                    border: `1px solid ${theme.palette.divider}`,
                    borderRadius: 12,
                  }}
                  formatter={(value: number) => [`%${value.toFixed(1)}`, 'Duty Cycle']}
                />
                <Bar
                  dataKey="duty"
                  fill="#0288D1"
                  radius={[4, 4, 0, 0]}
                  maxBarSize={30}
                  opacity={0.7}
                />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        {/* ---- Efficiency Table ---- */}
        {efficiencyTable.length > 0 && (
          <Card sx={{ mb: 3 }}>
            <CardContent>
              <Typography variant="h6" fontWeight={600} sx={{ mb: 2 }}>
                Verimlilik Tablosu (ECA Proteus Premix 30kW)
              </Typography>
              {effCurrent && (
                <Box sx={{ mb: 2, display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                  <Chip
                    icon={<ThermostatIcon />}
                    label={`Mevcut: ${(effCurrent.flow_temp as number) || 60}°C`}
                    color="primary"
                    variant="outlined"
                  />
                  <Chip
                    icon={<BoltIcon />}
                    label={`Verim: %${(effCurrent.efficiency_pct as number)?.toFixed(1) || '--'}`}
                    color={(effCurrent.is_condensing as boolean) ? 'success' : 'warning'}
                    variant="filled"
                  />
                  {(effCurrent.is_condensing as boolean) && (
                    <Chip label="Yogusma Modu" color="info" size="small" />
                  )}
                </Box>
              )}
              <Box sx={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                  <thead>
                    <tr style={{ borderBottom: `2px solid ${theme.palette.divider}` }}>
                      <th style={{ textAlign: 'left', padding: '8px' }}>Akis Sicakligi</th>
                      <th style={{ textAlign: 'center', padding: '8px' }}>Verim %</th>
                      <th style={{ textAlign: 'center', padding: '8px' }}>Gaz (m3/s)</th>
                      <th style={{ textAlign: 'center', padding: '8px' }}>Mod</th>
                    </tr>
                  </thead>
                  <tbody>
                    {efficiencyTable.map((row, i) => {
                      const isCurrent = row.is_current as boolean;
                      const isCondensing = row.is_condensing as boolean;
                      return (
                        <tr
                          key={i}
                          style={{
                            borderBottom: `1px solid ${theme.palette.divider}`,
                            backgroundColor: isCurrent
                              ? theme.palette.action.selected
                              : 'transparent',
                            fontWeight: isCurrent ? 600 : 400,
                          }}
                        >
                          <td style={{ padding: '6px 8px' }}>
                            {(row.flow_temp as number)}°C
                            {isCurrent && ' (mevcut)'}
                          </td>
                          <td style={{ textAlign: 'center', padding: '6px 8px' }}>
                            %{(row.efficiency_pct as number)?.toFixed(1)}
                          </td>
                          <td style={{ textAlign: 'center', padding: '6px 8px' }}>
                            {(row.gas_rate_m3h as number)?.toFixed(2)}
                          </td>
                          <td style={{ textAlign: 'center', padding: '6px 8px' }}>
                            <Chip
                              label={isCondensing ? 'Yogusma' : 'Konv.'}
                              size="small"
                              color={isCondensing ? 'success' : 'default'}
                              variant="outlined"
                            />
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </Box>
              {efficiency && (efficiency as Record<string, unknown>).recommendation ? (
                <Box sx={{ mt: 2, p: 1.5, bgcolor: 'action.hover', borderRadius: 2 }}>
                  <Typography variant="body2" fontWeight={500}>
                    Oneri: {String((efficiency as Record<string, unknown>).recommendation)}
                  </Typography>
                </Box>
              ) : null}
            </CardContent>
          </Card>
        )}

        {/* ---- Period Summary ---- */}
        {summary && (
          <Card>
            <CardContent>
              <Typography variant="h6" fontWeight={600} sx={{ mb: 2 }}>
                {runtimeRange} Gunluk Ozet
              </Typography>
              <Grid container spacing={2}>
                <Grid item xs={6} sm={3}>
                  <Typography variant="body2" color="text.secondary">Toplam Calisma</Typography>
                  <Typography variant="h6" fontWeight={600}>{periodHours} saat</Typography>
                </Grid>
                <Grid item xs={6} sm={3}>
                  <Typography variant="body2" color="text.secondary">Toplam Gaz</Typography>
                  <Typography variant="h6" fontWeight={600}>{summary.totalGasM3.toFixed(2)} m3</Typography>
                </Grid>
                <Grid item xs={6} sm={3}>
                  <Typography variant="body2" color="text.secondary">Toplam Maliyet</Typography>
                  <Typography variant="h6" fontWeight={600}>{summary.totalCostTl.toFixed(2)} TL</Typography>
                </Grid>
                <Grid item xs={6} sm={3}>
                  <Typography variant="body2" color="text.secondary">Gunluk Ortalama</Typography>
                  <Typography variant="h6" fontWeight={600}>{summary.avgDailyCostTl.toFixed(2)} TL/gun</Typography>
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        )}
      </Box>
    </AppLayout>
  );
}
