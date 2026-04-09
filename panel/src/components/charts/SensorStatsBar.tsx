'use client';

import { useMemo } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import ThermostatIcon from '@mui/icons-material/Thermostat';
import WaterDropIcon from '@mui/icons-material/WaterDrop';
import type { HistoryPoint } from '@/types';

interface Props {
  data: HistoryPoint[];
}

interface StatGroup {
  min: number;
  avg: number;
  max: number;
}

function computeStats(values: number[]): StatGroup | null {
  if (values.length === 0) return null;
  const min = Math.min(...values);
  const max = Math.max(...values);
  const avg = values.reduce((a, b) => a + b, 0) / values.length;
  return { min, avg, max };
}

function StatChip({ label, value, unit, color }: { label: string; value: string; unit: string; color: string }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.5 }}>
      <Typography variant="caption" color="text.secondary" sx={{ fontSize: 11 }}>
        {label}:
      </Typography>
      <Typography variant="body2" fontWeight={600} sx={{ color, fontSize: 13 }}>
        {value}{unit}
      </Typography>
    </Box>
  );
}

export default function SensorStatsBar({ data }: Props) {
  const { tempStats, humStats } = useMemo(() => {
    const temps = data.map((p) => p.temp).filter((v) => v != null && !isNaN(v));
    const hums = data.map((p) => p.hum).filter((v) => v != null && !isNaN(v));
    return {
      tempStats: computeStats(temps),
      humStats: computeStats(hums),
    };
  }, [data]);

  if (!tempStats) return null;

  return (
    <Box
      sx={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: { xs: 1.5, sm: 3 },
        mb: 1.5,
        px: 1,
        py: 0.75,
        bgcolor: 'action.hover',
        borderRadius: 1.5,
      }}
    >
      {/* Temperature stats */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
        <ThermostatIcon sx={{ fontSize: 16, color: '#FF6B35' }} />
        <StatChip label="En Dusuk" value={tempStats.min.toFixed(1)} unit="°" color="#FF6B35" />
        <Typography variant="caption" color="text.disabled">|</Typography>
        <StatChip label="Ort" value={tempStats.avg.toFixed(1)} unit="°" color="#FF6B35" />
        <Typography variant="caption" color="text.disabled">|</Typography>
        <StatChip label="En Yuksek" value={tempStats.max.toFixed(1)} unit="°" color="#FF6B35" />
      </Box>

      {/* Humidity stats */}
      {humStats && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <WaterDropIcon sx={{ fontSize: 16, color: '#2196F3' }} />
          <StatChip label="En Dusuk" value={humStats.min.toFixed(0)} unit="%" color="#2196F3" />
          <Typography variant="caption" color="text.disabled">|</Typography>
          <StatChip label="Ort" value={humStats.avg.toFixed(0)} unit="%" color="#2196F3" />
          <Typography variant="caption" color="text.disabled">|</Typography>
          <StatChip label="En Yuksek" value={humStats.max.toFixed(0)} unit="%" color="#2196F3" />
        </Box>
      )}
    </Box>
  );
}
