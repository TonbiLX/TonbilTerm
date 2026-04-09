'use client';

import { useMemo } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import ToggleButton from '@mui/material/ToggleButton';
import { useTheme } from '@mui/material/styles';
import {
  ResponsiveContainer,
  ComposedChart,
  Line,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
} from 'recharts';
import type { HistoryPoint } from '@/types';
import SensorStatsBar from './SensorStatsBar';

interface Props {
  data: HistoryPoint[];
  timeRange: '24h' | '7d' | '30d';
  onTimeRangeChange?: (range: '24h' | '7d' | '30d') => void;
  title?: string;
  showHumidity?: boolean;
  showPressure?: boolean;
}

function formatTime(ts: number, range: '24h' | '7d' | '30d'): string {
  const date = new Date(ts * 1000);
  if (range === '24h') {
    return date.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
  }
  if (range === '7d') {
    return date.toLocaleDateString('tr-TR', { weekday: 'short', hour: '2-digit' });
  }
  return date.toLocaleDateString('tr-TR', { day: 'numeric', month: 'short' });
}

export default function TemperatureChart({
  data,
  timeRange,
  onTimeRangeChange,
  title = 'Sicaklik Gecmisi',
  showHumidity = true,
  showPressure = true,
}: Props) {
  const theme = useTheme();

  const hasPressure = useMemo(
    () => showPressure && data.some((p) => p.pres !== undefined && p.pres !== null && p.pres > 0),
    [data, showPressure],
  );

  const chartData = useMemo(
    () => data.map((point) => ({
      ...point,
      label: formatTime(point.ts, timeRange),
    })),
    [data, timeRange],
  );

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <Typography variant="h6" fontWeight={600}>
          {title}
        </Typography>
        {onTimeRangeChange && (
          <ToggleButtonGroup
            value={timeRange}
            exclusive
            onChange={(_, val) => val && onTimeRangeChange(val)}
            size="small"
          >
            <ToggleButton value="24h">24S</ToggleButton>
            <ToggleButton value="7d">7G</ToggleButton>
            <ToggleButton value="30d">30G</ToggleButton>
          </ToggleButtonGroup>
        )}
      </Box>

      <SensorStatsBar data={data} />

      {/* Temperature + Humidity chart */}
      <ResponsiveContainer width="100%" height={220}>
        <ComposedChart data={chartData} margin={{ top: 5, right: 10, left: -10, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" stroke={theme.palette.divider} />
          <XAxis
            dataKey="label"
            tick={{ fontSize: 10, fill: theme.palette.text.secondary }}
            tickLine={false}
            axisLine={{ stroke: theme.palette.divider }}
            interval="preserveStartEnd"
          />
          <YAxis
            yAxisId="temp"
            tick={{ fontSize: 11, fill: theme.palette.text.secondary }}
            tickLine={false}
            axisLine={false}
            domain={['auto', 'auto']}
            unit="°"
            width={40}
          />
          {showHumidity && (
            <YAxis
              yAxisId="hum"
              orientation="right"
              tick={{ fontSize: 11, fill: '#2196F3' }}
              tickLine={false}
              axisLine={false}
              domain={[0, 100]}
              unit="%"
              width={40}
            />
          )}
          <Tooltip
            contentStyle={{
              backgroundColor: theme.palette.background.paper,
              border: `1px solid ${theme.palette.divider}`,
              borderRadius: 8,
              fontSize: 12,
            }}
            formatter={(value: number, name: string) => {
              if (name === 'temp') return [`${value.toFixed(1)}°C`, 'Sicaklik'];
              if (name === 'hum') return [`%${value.toFixed(0)}`, 'Nem'];
              return [value, name];
            }}
          />
          <Legend
            iconSize={10}
            wrapperStyle={{ fontSize: 12 }}
            formatter={(value: string) => {
              if (value === 'temp') return 'Sicaklik (°C)';
              if (value === 'hum') return 'Nem (%)';
              return value;
            }}
          />
          <Area
            yAxisId="temp"
            type="monotone"
            dataKey="temp"
            stroke="#FF6B35"
            fill="#FF6B35"
            fillOpacity={0.1}
            strokeWidth={2}
            dot={false}
            activeDot={{ r: 4, strokeWidth: 2 }}
          />
          {showHumidity && (
            <Line
              yAxisId="hum"
              type="monotone"
              dataKey="hum"
              stroke="#2196F3"
              strokeWidth={1.5}
              dot={false}
              activeDot={{ r: 3 }}
              strokeDasharray="4 2"
              opacity={0.7}
            />
          )}
        </ComposedChart>
      </ResponsiveContainer>

      {/* Pressure chart (separate, below) */}
      {hasPressure && (
        <Box sx={{ mt: 2 }}>
          <Typography variant="body2" color="text.secondary" fontWeight={500} sx={{ mb: 1 }}>
            Atmosfer Basinci (hPa)
          </Typography>
          <ResponsiveContainer width="100%" height={120}>
            <ComposedChart data={chartData} margin={{ top: 5, right: 10, left: -10, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke={theme.palette.divider} />
              <XAxis
                dataKey="label"
                tick={{ fontSize: 9, fill: theme.palette.text.secondary }}
                tickLine={false}
                interval="preserveStartEnd"
              />
              <YAxis
                tick={{ fontSize: 10, fill: '#9C27B0' }}
                tickLine={false}
                axisLine={false}
                domain={['auto', 'auto']}
                unit=""
                width={50}
              />
              <Tooltip
                contentStyle={{
                  backgroundColor: theme.palette.background.paper,
                  border: `1px solid ${theme.palette.divider}`,
                  borderRadius: 8,
                  fontSize: 12,
                }}
                formatter={(value: number) => [`${value.toFixed(1)} hPa`, 'Basinc']}
              />
              <Area
                type="monotone"
                dataKey="pres"
                stroke="#9C27B0"
                fill="#9C27B0"
                fillOpacity={0.08}
                strokeWidth={1.5}
                dot={false}
              />
            </ComposedChart>
          </ResponsiveContainer>
        </Box>
      )}
    </Box>
  );
}
