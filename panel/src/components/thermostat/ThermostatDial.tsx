'use client';

import { useState, useRef, useCallback, useEffect } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Snackbar from '@mui/material/Snackbar';
import { useTheme } from '@mui/material/styles';
import AddIcon from '@mui/icons-material/Add';
import RemoveIcon from '@mui/icons-material/Remove';
import LocalFireDepartmentIcon from '@mui/icons-material/LocalFireDepartment';
import AcUnitIcon from '@mui/icons-material/AcUnit';
import { apiClient } from '@/lib/api';
import { useSensorStore } from '@/stores/sensorStore';

const MIN_TEMP = 15;
const MAX_TEMP = 30;
const TEMP_STEP = 0.5;
// Arc: 270° sweep from bottom-left to bottom-right (90° gap at bottom)

function tempToAngle(temp: number): number {
  const clamped = Math.max(MIN_TEMP, Math.min(MAX_TEMP, temp));
  const ratio = (clamped - MIN_TEMP) / (MAX_TEMP - MIN_TEMP);
  // Go clockwise from START through 360/0 to END
  // Total arc = 360 - (START - END) = 360 - (210-330) but we go the long way
  // Arc span = 360 - START_ANGLE + END_ANGLE = 360 - 210 + 330 = 480? No.
  // Actually: from 210° clockwise to 330° = going through 270, 360/0, 90, 180, 270, 330
  // That's the WRONG way. We want the SHORT arc: 210 -> 180 -> 90 -> 0 -> 330.
  // Let me reconsider. Let's use a simpler approach:
  // Start at bottom-left, sweep clockwise through top to bottom-right
  // Start: 135° (from 12 o'clock), End: 405° (= 45° past full circle)
  // This gives a 270° sweep with a 90° gap at the bottom
  const startDeg = 135;
  const endDeg = 405; // 135 + 270
  return startDeg + ratio * (endDeg - startDeg);
}

function angleToTemp(angleDeg: number): number {
  const startDeg = 135;
  const endDeg = 405;
  // Normalize angle to the arc range
  let a = angleDeg;
  if (a < startDeg) a += 360;
  if (a > endDeg) a = a > (endDeg + startDeg) / 2 + 180 ? startDeg : endDeg;
  const ratio = Math.max(0, Math.min(1, (a - startDeg) / (endDeg - startDeg)));
  const raw = MIN_TEMP + ratio * (MAX_TEMP - MIN_TEMP);
  return Math.round(raw / TEMP_STEP) * TEMP_STEP;
}

function polarToCartesian(cx: number, cy: number, r: number, angleDeg: number) {
  const rad = ((angleDeg - 90) * Math.PI) / 180;
  return { x: cx + r * Math.cos(rad), y: cy + r * Math.sin(rad) };
}

function describeArc(cx: number, cy: number, r: number, startAngle: number, endAngle: number) {
  if (endAngle - startAngle < 0.5) return '';
  const start = polarToCartesian(cx, cy, r, endAngle);
  const end = polarToCartesian(cx, cy, r, startAngle);
  const largeArcFlag = endAngle - startAngle <= 180 ? 0 : 1;
  return `M ${start.x} ${start.y} A ${r} ${r} 0 ${largeArcFlag} 0 ${end.x} ${end.y}`;
}

function getTempColor(temp: number): string {
  const ratio = (temp - MIN_TEMP) / (MAX_TEMP - MIN_TEMP);
  if (ratio < 0.2) return '#2196F3';
  if (ratio < 0.4) return '#26A69A';
  if (ratio < 0.6) return '#4CAF50';
  if (ratio < 0.8) return '#FF9800';
  return '#F44336';
}

interface ThermostatDialProps {
  disabled?: boolean;
  currentTemp?: number;
}

export default function ThermostatDial({ disabled = false, currentTemp = 0 }: ThermostatDialProps) {
  const theme = useTheme();
  const boilerStatus = useSensorStore((s) => s.boilerStatus);
  // currentTemp is now received as a prop (strategy-based calculation from parent)

  const [targetTemp, setTargetTemp] = useState(boilerStatus.target);
  const [isDragging, setIsDragging] = useState(false);
  const [isCommitting, setIsCommitting] = useState(false);
  const [feedback, setFeedback] = useState('');
  const svgRef = useRef<SVGSVGElement>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout>>();
  const commitLockRef = useRef<ReturnType<typeof setTimeout>>();

  // Sync from store when idle
  useEffect(() => {
    if (!isDragging && !isCommitting) {
      setTargetTemp(boilerStatus.target);
    }
  }, [boilerStatus.target, isDragging, isCommitting]);

  const commitTemp = useCallback((temp: number) => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (commitLockRef.current) clearTimeout(commitLockRef.current);
    setIsCommitting(true);

    debounceRef.current = setTimeout(() => {
      useSensorStore.getState().updateBoilerStatus({ target: temp });
      setFeedback(`Hedef: ${temp.toFixed(1)}°C ayarlandi`);
      apiClient.setTargetTemp(temp).then((cfg) => {
        useSensorStore.getState().updateBoilerStatus({ target: cfg.targetTemp });
      }).catch(() => {
        setFeedback('Hata! Ayar gonderilemedi');
        apiClient.getHeatingConfig().then((cfg) => {
          useSensorStore.getState().updateBoilerStatus({ target: cfg.targetTemp });
        }).catch(() => {});
      }).finally(() => {
        commitLockRef.current = setTimeout(() => setIsCommitting(false), 2000);
      });
    }, 600);
  }, []);

  const adjustTemp = useCallback(
    (delta: number) => {
      setTargetTemp((prev) => {
        const next = Math.max(MIN_TEMP, Math.min(MAX_TEMP, +(prev + delta).toFixed(1)));
        commitTemp(next);
        return next;
      });
    },
    [commitTemp],
  );

  const getAngleFromEvent = useCallback(
    (clientX: number, clientY: number) => {
      if (!svgRef.current) return null;
      const rect = svgRef.current.getBoundingClientRect();
      const centerX = rect.left + rect.width / 2;
      const centerY = rect.top + rect.height / 2;
      const dx = clientX - centerX;
      const dy = clientY - centerY;
      // atan2 returns angle from positive X axis, counterclockwise
      // We need angle from top (12 o'clock), clockwise
      let angle = (Math.atan2(dy, dx) * 180) / Math.PI + 90;
      if (angle < 0) angle += 360;
      return angle;
    },
    [],
  );

  const isAngleInArc = useCallback((angle: number) => {
    // Arc from 135° to 405° (=45°). Gap is 45°-135° (bottom area)
    // If angle is in 45-135 range, it's in the gap
    if (angle > 45 && angle < 135) return false;
    return true;
  }, []);

  const handlePointerDown = useCallback(
    (e: React.PointerEvent) => {
      if (disabled) return;
      e.preventDefault();
      (e.target as Element).setPointerCapture?.(e.pointerId);
      const angle = getAngleFromEvent(e.clientX, e.clientY);
      if (angle !== null && isAngleInArc(angle)) {
        setIsDragging(true);
        const temp = angleToTemp(angle);
        setTargetTemp(Math.max(MIN_TEMP, Math.min(MAX_TEMP, temp)));
      }
    },
    [disabled, getAngleFromEvent, isAngleInArc],
  );

  const handlePointerMove = useCallback(
    (e: React.PointerEvent) => {
      if (disabled || !isDragging) return;
      e.preventDefault();
      const angle = getAngleFromEvent(e.clientX, e.clientY);
      if (angle !== null) {
        const temp = angleToTemp(angle);
        setTargetTemp(Math.max(MIN_TEMP, Math.min(MAX_TEMP, temp)));
      }
    },
    [disabled, isDragging, getAngleFromEvent],
  );

  const handlePointerUp = useCallback(() => {
    if (isDragging) {
      setIsDragging(false);
      commitTemp(targetTemp);
    }
  }, [isDragging, targetTemp, commitTemp]);

  const isHeating = boilerStatus.relay;
  const size = 340;
  const cx = size / 2;
  const cy = size / 2;
  const outerR = 145;
  const arcR = 135;
  const innerR = 105;
  const tickR = outerR - 5;

  const startDeg = 135;
  const endDeg = 405;
  const handleAngle = tempToAngle(targetTemp);
  const handlePos = polarToCartesian(cx, cy, arcR, handleAngle);
  const targetColor = getTempColor(targetTemp);

  // Generate tick marks — every 1°C, labels every 5°C
  const ticks = [];
  for (let t = MIN_TEMP; t <= MAX_TEMP; t += 1) {
    const angle = tempToAngle(t);
    const isMajor = t % 5 === 0;
    const len = isMajor ? 12 : 6;
    const outerTick = polarToCartesian(cx, cy, tickR, angle);
    const innerTick = polarToCartesian(cx, cy, tickR - len, angle);
    ticks.push(
      <line
        key={t}
        x1={outerTick.x}
        y1={outerTick.y}
        x2={innerTick.x}
        y2={innerTick.y}
        stroke={theme.palette.text.secondary}
        strokeWidth={isMajor ? 2 : 1}
        opacity={isMajor ? 0.8 : 0.3}
      />,
    );
    if (isMajor) {
      const labelPos = polarToCartesian(cx, cy, tickR + 14, angle);
      ticks.push(
        <text
          key={`l${t}`}
          x={labelPos.x}
          y={labelPos.y}
          textAnchor="middle"
          dominantBaseline="middle"
          fill={theme.palette.text.secondary}
          fontSize="12"
          fontWeight="600"
        >
          {t}°
        </text>,
      );
    }
  }

  const activeArcEnd = tempToAngle(targetTemp);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1.5 }}>
      <Box
        sx={{
          position: 'relative',
          width: { xs: 300, sm: 340, md: 380 },
          height: { xs: 300, sm: 340, md: 380 },
        }}
      >
        <svg
          ref={svgRef}
          viewBox={`0 0 ${size} ${size}`}
          width="100%"
          height="100%"
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          onPointerLeave={handlePointerUp}
          style={{
            touchAction: 'none',
            cursor: disabled ? 'default' : isDragging ? 'grabbing' : 'pointer',
            userSelect: 'none',
            opacity: disabled ? 0.45 : 1,
            transition: 'opacity 0.3s ease',
          }}
        >
          <defs>
            <linearGradient id="arcGrad" gradientUnits="userSpaceOnUse" x1="40" y1="280" x2="300" y2="60">
              <stop offset="0%" stopColor="#2196F3" />
              <stop offset="30%" stopColor="#4CAF50" />
              <stop offset="60%" stopColor="#FF9800" />
              <stop offset="100%" stopColor="#F44336" />
            </linearGradient>
            <filter id="knobGlow">
              <feGaussianBlur stdDeviation="3" result="blur" />
              <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
            </filter>
            <filter id="innerShadow">
              <feGaussianBlur stdDeviation="2" result="blur" />
              <feComposite in="SourceGraphic" in2="blur" operator="over" />
            </filter>
          </defs>

          {/* Background arc track */}
          <path
            d={describeArc(cx, cy, arcR, startDeg, endDeg)}
            fill="none"
            stroke={theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.08)'}
            strokeWidth="14"
            strokeLinecap="round"
          />

          {/* Active colored arc */}
          {activeArcEnd > startDeg && (
            <path
              d={describeArc(cx, cy, arcR, startDeg, activeArcEnd)}
              fill="none"
              stroke="url(#arcGrad)"
              strokeWidth="14"
              strokeLinecap="round"
              opacity={isHeating ? 1 : 0.85}
            />
          )}

          {/* Tick marks (outside arc) */}
          {ticks}

          {/* Draggable handle (hidden in manual/disabled mode) */}
          {!disabled && (
            <>
              <circle
                cx={handlePos.x}
                cy={handlePos.y}
                r="16"
                fill={targetColor}
                stroke="white"
                strokeWidth="3"
                filter="url(#knobGlow)"
                style={{ cursor: 'grab' }}
              />
              <circle
                cx={handlePos.x}
                cy={handlePos.y}
                r="7"
                fill="white"
                opacity={0.9}
                style={{ pointerEvents: 'none' }}
              />
            </>
          )}

          {/* Inner circle */}
          <circle
            cx={cx}
            cy={cy}
            r={innerR}
            fill={theme.palette.background.paper}
            stroke={theme.palette.divider}
            strokeWidth="1"
          />

          {disabled ? (
            <>
              {/* Disabled/Manual mode center display */}
              <text x={cx} y={cy - 28} textAnchor="middle" dominantBaseline="middle"
                fill={theme.palette.text.secondary} fontSize="12" fontWeight="500">
                Manuel Mod
              </text>
              <text x={cx} y={cy + 4} textAnchor="middle" dominantBaseline="middle"
                fill={theme.palette.text.primary} fontSize="48" fontWeight="700">
                {currentTemp > 0 ? `${currentTemp.toFixed(1)}°` : '--.-°'}
              </text>
              <text x={cx} y={cy + 34} textAnchor="middle" dominantBaseline="middle"
                fill={theme.palette.text.secondary} fontSize="11">
                Oda Sicakligi
              </text>
              <text x={cx} y={cy + 56} textAnchor="middle" dominantBaseline="middle"
                fill={isHeating ? '#FF6B35' : '#2196F3'} fontSize="12" fontWeight="500">
                {isHeating ? 'Kombi Acik' : 'Kombi Kapali'}
              </text>
            </>
          ) : (
            <>
              {/* Normal/Auto mode center display */}
              <text x={cx} y={cy - 28} textAnchor="middle" dominantBaseline="middle"
                fill={theme.palette.text.secondary} fontSize="12" fontWeight="400">
                Hedef
              </text>
              <text x={cx} y={cy + 4} textAnchor="middle" dominantBaseline="middle"
                fill={targetColor} fontSize="48" fontWeight="700">
                {targetTemp.toFixed(1)}°
              </text>
              <text x={cx} y={cy + 34} textAnchor="middle" dominantBaseline="middle"
                fill={theme.palette.text.secondary} fontSize="11">
                Oda
              </text>
              <text x={cx} y={cy + 50} textAnchor="middle" dominantBaseline="middle"
                fill={theme.palette.text.primary} fontSize="17" fontWeight="500">
                {currentTemp > 0 ? `${currentTemp.toFixed(1)}°C` : '--.-°C'}
              </text>
              <text x={cx} y={cy + 72} textAnchor="middle" dominantBaseline="middle"
                fill={isHeating ? '#FF6B35' : '#2196F3'} fontSize="12" fontWeight="500">
                {isHeating ? 'Isitiliyor' : 'Bekleniyor'}
              </text>
            </>
          )}
        </svg>

        {/* Pulse ring when heating */}
        {isHeating && (
          <Box
            sx={{
              position: 'absolute', top: '50%', left: '50%',
              transform: 'translate(-50%, -50%)',
              width: innerR * 2, height: innerR * 2,
              borderRadius: '50%', pointerEvents: 'none',
              animation: 'pulse 2s ease-in-out infinite',
              border: '2px solid', borderColor: 'secondary.main', opacity: 0.3,
              '@keyframes pulse': {
                '0%, 100%': { transform: 'translate(-50%, -50%) scale(1)', opacity: 0.3 },
                '50%': { transform: 'translate(-50%, -50%) scale(1.06)', opacity: 0.1 },
              },
            }}
          />
        )}
      </Box>

      {/* Quick adjust: - / status / + (hidden in manual/disabled mode) */}
      {!disabled && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 3, mt: -1 }}>
          <IconButton
            onClick={() => adjustTemp(-TEMP_STEP)}
            disabled={targetTemp <= MIN_TEMP}
            sx={{ bgcolor: 'action.hover', width: 48, height: 48, '&:hover': { bgcolor: 'action.selected' } }}
          >
            <RemoveIcon />
          </IconButton>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            {isHeating ? (
              <LocalFireDepartmentIcon sx={{ color: '#FF6B35', fontSize: 20 }} />
            ) : (
              <AcUnitIcon sx={{ color: '#2196F3', fontSize: 20 }} />
            )}
            <Typography variant="body2" color="text.secondary">
              {isHeating ? 'Isitiliyor' : 'Bekleniyor'}
            </Typography>
          </Box>

          <IconButton
            onClick={() => adjustTemp(TEMP_STEP)}
            disabled={targetTemp >= MAX_TEMP}
            sx={{ bgcolor: 'action.hover', width: 48, height: 48, '&:hover': { bgcolor: 'action.selected' } }}
          >
            <AddIcon />
          </IconButton>
        </Box>
      )}

      {/* Feedback snackbar */}
      <Snackbar
        open={!!feedback}
        autoHideDuration={2000}
        onClose={() => setFeedback('')}
        message={feedback}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      />
    </Box>
  );
}
