export interface SensorReading {
  temp: number;
  hum: number;
  pres: number;
  ts: number;
  deviceId: string;
}

export interface Room {
  id: number;
  name: string;
  icon: string;
  weight: number;
  minTemp: number;
  sortOrder?: number;
  currentTemp?: number | null;
  currentHumidity?: number | null;
  deviceCount?: number;
  latestReading?: SensorReading;
}

export interface BoilerStatus {
  relay: boolean;
  mode: string;
  target: number;
  uptime: number;
  localFallback: boolean;
}

export interface WeatherData {
  temp: number;
  humidity: number;
  windSpeed: number;
  description: string;
  icon: string;
  feelsLike: number;
  city?: string;
}

export interface HeatingConfig {
  targetTemp: number;
  hysteresis: number;
  minCycleMin: number;
  mode: string;
  strategy: string;
  gasPricePerM3: number;
  floorAreaM2: number;
  boilerPowerKw: number;
  flowTemp?: number;
  boilerBrand?: string;
  boilerModel?: string;
  relayState?: boolean;
}

export interface ScheduleEntry {
  id: number;
  dayOfWeek: number;
  hour: number;
  minute: number;
  targetTemp: number;
  enabled: boolean;
}

export interface User {
  id: number;
  email: string;
  displayName: string;
  role: string;
}

export interface UserInfo {
  id: number;
  email: string;
  displayName: string;
  isActive: boolean;
  createdAt: string;
  role: string;
}

export interface DeviceInfo {
  id: number;
  deviceId: string;
  name: string;
  roomId: number | null;
  roomName: string | null;
  type: string;
  online: boolean;
  lastSeen: string | null;
  firmware: string;
  ipAddress?: string | null;
}

export interface HistoryPoint {
  ts: number;
  temp: number;
  hum: number;
  pres?: number;
}

// Energy types - matches /api/energy/current response
export interface EnergyStats {
  todayMinutes: number;
  todayCost: number;
  todayGasM3: number;
  todayKwh: number;
  todayKcal: number;
  relayState: boolean;
  relayOnSince: string | null;
  flowTemp: number;
  efficiencyPct: number;
  isCondensing: boolean;
  gasPricePerM3: number;
}

// Energy daily - matches /api/energy/daily response items
export interface EnergyDaily {
  date: string;
  runtimeMinutes: number;
  gasM3: number;
  thermalKwh: number;
  costTl: number;
  efficiencyPct: number;
  dutyCyclePct: number;
}

export interface EnergySummary {
  totalGasM3: number;
  totalCostTl: number;
  totalRuntimeMinutes: number;
  avgDailyCostTl: number;
}

// Alert type - matches /api/energy/alerts response
export interface Alert {
  id: string;
  type: 'temperature' | 'humidity' | 'energy' | 'boiler' | 'forecast';
  severity: 'critical' | 'warning' | 'info' | 'success';
  title: string;
  message: string;
  icon: string;
  action?: string;
}

export interface Profile {
  id: number;
  name: string;
  icon: string;
  targetTemp: number;
  hysteresis: number;
  isDefault: boolean;
  sortOrder: number;
}

export type ThemeMode = 'light' | 'dark' | 'system';

// WebSocket message types from backend
export interface WsMessage {
  type: 'telemetry' | 'boiler' | 'relay_state' | 'device_status' | 'config_update' | 'boost_update' | 'initial_state' | 'sensor' | 'weather' | 'config' | 'command' | 'ping' | 'pong' | 'heartbeat';
  data?: Record<string, unknown>;
  payload?: unknown;
}
