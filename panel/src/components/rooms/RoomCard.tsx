'use client';

import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import CardContent from '@mui/material/CardContent';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import { useTheme } from '@mui/material/styles';
import ThermostatIcon from '@mui/icons-material/Thermostat';
import WaterDropIcon from '@mui/icons-material/WaterDrop';
import CompressIcon from '@mui/icons-material/Compress';
import WifiIcon from '@mui/icons-material/Wifi';
import WifiOffIcon from '@mui/icons-material/WifiOff';
import MeetingRoomIcon from '@mui/icons-material/MeetingRoom';
import BedIcon from '@mui/icons-material/Bed';
import WeekendIcon from '@mui/icons-material/Weekend';
import KitchenIcon from '@mui/icons-material/Kitchen';
import BathroomIcon from '@mui/icons-material/Bathroom';
import ChildCareIcon from '@mui/icons-material/ChildCare';
import type { Room } from '@/types';
import { useSensorStore } from '@/stores/sensorStore';

const iconMap: Record<string, typeof MeetingRoomIcon> = {
  bedroom: BedIcon,
  living: WeekendIcon,
  kitchen: KitchenIcon,
  bathroom: BathroomIcon,
  child: ChildCareIcon,
  default: MeetingRoomIcon,
};

function getTempStatus(current: number | undefined, target: number) {
  if (current === undefined) return { color: 'grey', label: 'Veri Yok' };
  const diff = current - target;
  if (Math.abs(diff) < 0.5) return { color: '#4CAF50', label: 'Ideal' };
  if (diff < -1) return { color: '#2196F3', label: 'Soguk' };
  if (diff < 0) return { color: '#FF9800', label: 'Isiniyor' };
  return { color: '#F44336', label: 'Sicak' };
}

interface Props {
  room: Room;
  onClick?: () => void;
}

export default function RoomCard({ room, onClick }: Props) {
  const theme = useTheme();
  const target = useSensorStore((s) => s.boilerStatus.target);
  const reading = room.latestReading;
  // Use latestReading from WS, or fallback to currentTemp from API
  const currentTemp = reading?.temp ?? room.currentTemp ?? undefined;
  const currentHum = reading?.hum ?? room.currentHumidity ?? undefined;
  const currentPres = reading?.pres;
  const isOnline = reading ? (Date.now() / 1000 - reading.ts < 300) : (currentTemp !== undefined);
  const status = getTempStatus(currentTemp, target);
  const RoomIcon = iconMap[room.icon] ?? iconMap.default;

  return (
    <Card
      sx={{
        height: '100%',
        position: 'relative',
        overflow: 'visible',
        '&:hover': {
          boxShadow: theme.shadows[6],
          transform: 'translateY(-2px)',
        },
        transition: 'all 0.2s ease',
      }}
    >
      <CardActionArea onClick={onClick} sx={{ height: '100%' }}>
        <CardContent sx={{ p: 2.5 }}>
          {/* Header */}
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Box
                sx={{
                  p: 1,
                  borderRadius: 2,
                  bgcolor: `${status.color}18`,
                  display: 'flex',
                }}
              >
                <RoomIcon sx={{ color: status.color, fontSize: 24 }} />
              </Box>
              <Typography variant="subtitle1" fontWeight={600}>
                {room.name}
              </Typography>
            </Box>
            <Chip
              icon={isOnline ? <WifiIcon /> : <WifiOffIcon />}
              label={isOnline ? 'Cevrimici' : 'Cevrimdisi'}
              size="small"
              color={isOnline ? 'success' : 'default'}
              variant="outlined"
              sx={{ height: 24, '& .MuiChip-label': { px: 0.5, fontSize: '0.7rem' } }}
            />
          </Box>

          {/* Temperature - prominent */}
          <Box sx={{ display: 'flex', alignItems: 'baseline', mb: 1.5 }}>
            <ThermostatIcon sx={{ color: status.color, mr: 0.5, fontSize: 28 }} />
            <Typography
              variant="h3"
              component="span"
              sx={{ fontWeight: 700, color: status.color, lineHeight: 1 }}
            >
              {currentTemp !== undefined ? currentTemp.toFixed(1) : '--.-'}
            </Typography>
            <Typography variant="body1" color="text.secondary" sx={{ ml: 0.5 }}>
              °C
            </Typography>
          </Box>

          {/* Secondary readings */}
          <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
              <WaterDropIcon sx={{ fontSize: 16, color: 'info.main' }} />
              <Typography variant="body2" color="text.secondary">
                {currentHum !== undefined ? `%${currentHum.toFixed(0)}` : '--%'}
              </Typography>
            </Box>
            {currentPres !== undefined && currentPres > 0 && (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                <CompressIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                <Typography variant="body2" color="text.secondary">
                  {currentPres.toFixed(0)} hPa
                </Typography>
              </Box>
            )}
          </Box>

          {/* Status indicator bar */}
          <Box
            sx={{
              mt: 2,
              height: 4,
              borderRadius: 2,
              bgcolor: `${status.color}30`,
              overflow: 'hidden',
            }}
          >
            <Box
              sx={{
                height: '100%',
                width: currentTemp !== undefined
                  ? `${Math.min(100, Math.max(10, ((currentTemp - MIN_DISPLAY) / (MAX_DISPLAY - MIN_DISPLAY)) * 100))}%`
                  : '0%',
                bgcolor: status.color,
                borderRadius: 2,
                transition: 'width 0.5s ease',
              }}
            />
          </Box>
        </CardContent>
      </CardActionArea>
    </Card>
  );
}

const MIN_DISPLAY = 10;
const MAX_DISPLAY = 35;
