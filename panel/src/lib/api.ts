import type {
  User,
  UserInfo,
  Room,
  DeviceInfo,
  HistoryPoint,
  WeatherData,
  HeatingConfig,
  ScheduleEntry,
  BoilerStatus,
  EnergyStats,
  EnergyDaily,
  Alert,
  Profile,
} from '@/types';

class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/**
 * Backend returns ApiResponse: { success: bool, data: T, error: string | null }
 * This function unwraps .data automatically.
 */
async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
    ...options,
  });

  if (!res.ok) {
    const body = await res.text();
    throw new ApiError(res.status, body || res.statusText);
  }

  if (res.status === 204) return undefined as T;

  const json = await res.json();

  // Unwrap ApiResponse format: { success, data, error }
  if (json && typeof json === 'object' && 'success' in json && 'data' in json) {
    if (!json.success && json.error) {
      throw new ApiError(400, json.error);
    }
    return json.data as T;
  }

  return json as T;
}

// ---- Helper: snake_case → camelCase mapping ----

function mapRoom(raw: Record<string, unknown>): Room {
  return {
    id: raw.id as number,
    name: raw.name as string,
    icon: (raw.icon as string) || 'room',
    weight: (raw.weight as number) || 1.0,
    minTemp: (raw.min_temp as number) || 5.0,
    sortOrder: (raw.sort_order as number) || 0,
    currentTemp: (raw.current_temp as number) ?? null,
    currentHumidity: (raw.current_humidity as number) ?? null,
    deviceCount: (raw.device_count as number) || 0,
  };
}

function mapDevice(raw: Record<string, unknown>): DeviceInfo {
  return {
    id: raw.id as number,
    deviceId: raw.device_id as string,
    name: (raw.name as string) || '',
    roomId: raw.room_id as number | null,
    roomName: (raw.room_name as string) || null,
    type: raw.type as string,
    online: (raw.is_online as boolean) || false,
    lastSeen: raw.last_seen as string | null,
    firmware: (raw.firmware_version as string) || '',
    ipAddress: (raw.ip_address as string) || null,
  };
}

function mapHistoryPoint(raw: Record<string, unknown>): HistoryPoint {
  const timeStr = raw.time as string;
  const ts = timeStr ? new Date(timeStr).getTime() / 1000 : 0;
  return {
    ts,
    temp: (raw.temperature as number) ?? 0,
    hum: (raw.humidity as number) ?? 0,
    pres: (raw.pressure as number) ?? undefined,
  };
}

function mapWeather(raw: Record<string, unknown>): WeatherData {
  return {
    temp: (raw.temperature as number) ?? 0,
    humidity: (raw.humidity as number) ?? 0,
    windSpeed: (raw.wind_speed as number) ?? 0,
    description: (raw.description as string) || '--',
    icon: (raw.icon as string) || 'clear-day',
    feelsLike: (raw.feels_like as number) ?? (raw.temperature as number) ?? 0,
    city: (raw.city as string) || '',
  };
}

function mapHeatingConfig(raw: Record<string, unknown>): HeatingConfig {
  return {
    targetTemp: raw.target_temp as number,
    hysteresis: raw.hysteresis as number,
    minCycleMin: raw.min_cycle_min as number,
    mode: raw.mode as string,
    strategy: raw.strategy as string,
    gasPricePerM3: raw.gas_price_per_m3 as number,
    floorAreaM2: raw.floor_area_m2 as number,
    boilerPowerKw: raw.boiler_power_kw as number,
    flowTemp: (raw.flow_temp as number) || 60,
    boilerBrand: (raw.boiler_brand as string) || '',
    boilerModel: (raw.boiler_model as string) || '',
    relayState: (raw.relay_state as boolean) || false,
  };
}

function mapBoilerStatus(raw: Record<string, unknown>): BoilerStatus {
  return {
    relay: (raw.relay_state as boolean) ?? false,
    mode: (raw.mode as string) ?? 'auto',
    target: (raw.target_temp as number) ?? 22,
    uptime: (raw.uptime as number) ?? (raw.runtime_today as number) ?? 0,
    localFallback: (raw.local_fallback as boolean) ?? false,
  };
}

function mapScheduleEntry(raw: Record<string, unknown>): ScheduleEntry {
  return {
    id: raw.id as number,
    dayOfWeek: raw.day_of_week as number,
    hour: raw.hour as number,
    minute: (raw.minute as number) || 0,
    targetTemp: raw.target_temp as number,
    enabled: raw.enabled !== false,
  };
}

function mapUser(raw: Record<string, unknown>): UserInfo {
  return {
    id: raw.id as number,
    email: raw.email as string,
    displayName: (raw.display_name as string) || '',
    isActive: (raw.is_active as boolean) ?? true,
    createdAt: (raw.created_at as string) || '',
    role: (raw.role as string) || 'user',
  };
}

function mapProfile(raw: Record<string, unknown>): Profile {
  return {
    id: raw.id as number,
    name: raw.name as string,
    icon: (raw.icon as string) || 'thermostat',
    targetTemp: raw.target_temp as number,
    hysteresis: raw.hysteresis as number,
    isDefault: (raw.is_default as boolean) || false,
    sortOrder: (raw.sort_order as number) || 0,
  };
}

function mapEnergyStats(raw: Record<string, unknown>): EnergyStats {
  // From /api/energy/current
  const today = raw.today as Record<string, unknown> | undefined;
  const relay = raw.relay as Record<string, unknown> | undefined;
  const efficiency = raw.efficiency as Record<string, unknown> | undefined;
  return {
    todayMinutes: (today?.runtime_minutes as number) || 0,
    todayCost: (today?.cost_tl as number) || 0,
    todayGasM3: (today?.gas_m3 as number) || 0,
    todayKwh: (today?.thermal_kwh as number) || 0,
    todayKcal: (today?.thermal_kcal as number) || 0,
    relayState: (relay?.state as boolean) || false,
    relayOnSince: (relay?.on_since as string) || null,
    flowTemp: (raw.flow_temp as number) || 60,
    efficiencyPct: (efficiency?.current_pct as number) || 0,
    isCondensing: (efficiency?.is_condensing as boolean) || false,
    gasPricePerM3: (raw.gas_price_per_m3 as number) || 0,
  };
}

function mapEnergyDaily(raw: Record<string, unknown>): EnergyDaily {
  return {
    date: raw.date as string,
    runtimeMinutes: (raw.runtime_minutes as number) || 0,
    gasM3: (raw.gas_m3 as number) || 0,
    thermalKwh: (raw.thermal_kwh as number) || 0,
    costTl: (raw.cost_tl as number) || 0,
    efficiencyPct: (raw.efficiency_pct as number) || 0,
    dutyCyclePct: (raw.duty_cycle_pct as number) || 0,
  };
}

// ---- API Client ----

export const apiClient = {
  // Auth
  login: (email: string, password: string) =>
    request<Record<string, unknown>>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),

  logout: () =>
    request<void>('/api/auth/logout', { method: 'POST' }),

  getMe: () =>
    request<Record<string, unknown>>('/api/auth/me').then((raw) => {
      if (!raw || typeof raw !== 'object') throw new Error('Invalid user data');
      return {
        id: (raw.id as number) ?? 0,
        email: (raw.email as string) ?? '',
        displayName: (raw.display_name as string) || '',
        role: (raw.role as string) || 'user',
      };
    }),

  // User management
  getUsers: () =>
    request<Record<string, unknown>[]>('/api/auth/users').then((list) =>
      list.map(mapUser),
    ),

  createUser: (email: string, password: string, displayName: string) =>
    request<Record<string, unknown>>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, password, display_name: displayName }),
    }).then(mapUser),

  updateUser: (userId: number, data: { displayName?: string; isActive?: boolean; email?: string }) =>
    request<Record<string, unknown>>(`/api/auth/users/${userId}`, {
      method: 'PUT',
      body: JSON.stringify({
        ...(data.displayName !== undefined && { display_name: data.displayName }),
        ...(data.isActive !== undefined && { is_active: data.isActive }),
        ...(data.email !== undefined && { email: data.email }),
      }),
    }).then(mapUser),

  deleteUser: (userId: number) =>
    request<void>(`/api/auth/users/${userId}`, { method: 'DELETE' }),

  changePassword: (oldPassword: string, newPassword: string) =>
    request<void>('/api/auth/change-password', {
      method: 'POST',
      body: JSON.stringify({ old_password: oldPassword, new_password: newPassword }),
    }),

  // Devices
  getDevices: () =>
    request<Record<string, unknown>[]>('/api/devices').then((list) =>
      list.map(mapDevice),
    ),

  updateDevice: (deviceId: string, data: { name?: string; room_id?: number | null }) =>
    request<Record<string, unknown>>(`/api/devices/${deviceId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }).then(mapDevice),

  sendDeviceCommand: (deviceId: string, command: string, payload?: Record<string, unknown>) =>
    request<{ success: boolean }>(`/api/devices/${deviceId}/command`, {
      method: 'POST',
      body: JSON.stringify({ command, payload }),
    }),

  // Rooms
  getRooms: () =>
    request<Record<string, unknown>[]>('/api/rooms').then((list) =>
      list.map(mapRoom),
    ),

  createRoom: (data: { name: string; weight?: number; min_temp?: number; icon?: string }) =>
    request<Record<string, unknown>>('/api/rooms', {
      method: 'POST',
      body: JSON.stringify(data),
    }).then(mapRoom),

  updateRoom: (id: number, data: { name?: string; weight?: number; min_temp?: number; icon?: string }) =>
    request<Record<string, unknown>>(`/api/rooms/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }).then(mapRoom),

  deleteRoom: (id: number) =>
    request<void>(`/api/rooms/${id}`, { method: 'DELETE' }),

  // Sensor history
  getSensorHistory: (deviceOrRoom: string, range: '1h' | '6h' | '24h' | '7d' | '30d') => {
    // 'all' means no filter, otherwise pass as device= or room= param
    let params = `range=${range}`;
    if (deviceOrRoom && deviceOrRoom !== 'all') {
      // If numeric, treat as room ID; otherwise device ID
      if (/^\d+$/.test(deviceOrRoom)) {
        params += `&room=${deviceOrRoom}`;
      } else {
        params += `&device=${deviceOrRoom}`;
      }
    }
    return request<Record<string, unknown>[]>(`/api/sensors/history?${params}`).then(
      (list) => list.map(mapHistoryPoint),
    );
  },

  // Current sensor readings
  getCurrentSensors: () =>
    request<Record<string, unknown>[]>('/api/sensors/current'),

  // Weather
  getWeather: () =>
    request<Record<string, unknown>>('/api/weather/current').then(mapWeather),

  getWeatherForecast: () =>
    request<Record<string, unknown>>('/api/weather/forecast'),

  searchLocation: (query: string) =>
    request<Record<string, unknown>[]>(`/api/weather/search-location?q=${encodeURIComponent(query)}`),

  setWeatherLocation: (data: { latitude: number; longitude: number; city: string; district?: string }) =>
    request<Record<string, unknown>>('/api/weather/location', {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  // Heating config
  getHeatingConfig: () =>
    request<Record<string, unknown>>('/api/config/heating').then(mapHeatingConfig),

  updateHeatingConfig: (config: Partial<{
    target_temp: number;
    hysteresis: number;
    min_cycle_min: number;
    mode: string;
    strategy: string;
    gas_price_per_m3: number;
    floor_area_m2: number;
    boiler_power_kw: number;
    flow_temp: number;
  }>) =>
    request<Record<string, unknown>>('/api/config/heating', {
      method: 'PUT',
      body: JSON.stringify(config),
    }).then(mapHeatingConfig),

  // Boiler status (derived from heating config endpoint)
  getBoilerStatus: () =>
    request<Record<string, unknown>>('/api/config/heating').then(mapBoilerStatus),

  // Schedule
  getSchedule: () =>
    request<Record<string, unknown>[]>('/api/config/schedules').then((list) =>
      list.map(mapScheduleEntry),
    ),

  updateSchedule: (entries: Array<{
    day_of_week: number;
    hour: number;
    minute: number;
    target_temp: number;
    enabled: boolean;
  }>) =>
    request<Record<string, unknown>[]>('/api/config/schedules', {
      method: 'PUT',
      body: JSON.stringify({ entries }),
    }).then((list) => list.map(mapScheduleEntry)),

  // Boost
  getBoostStatus: () =>
    request<Record<string, unknown>>('/api/config/boost'),

  activateBoost: (minutes: number) =>
    request<Record<string, unknown>>('/api/config/boost', {
      method: 'POST',
      body: JSON.stringify({ minutes }),
    }),

  cancelBoost: () =>
    request<void>('/api/config/boost', { method: 'DELETE' }),

  // Profiles
  getProfiles: () =>
    request<Record<string, unknown>[]>('/api/config/profiles').then((list) =>
      list.map(mapProfile),
    ),

  createProfile: (data: { name: string; icon: string; target_temp: number; hysteresis: number }) =>
    request<Record<string, unknown>>('/api/config/profiles', {
      method: 'POST',
      body: JSON.stringify(data),
    }).then(mapProfile),

  updateProfile: (id: number, data: Partial<{ name: string; icon: string; target_temp: number; hysteresis: number; sort_order: number }>) =>
    request<Record<string, unknown>>(`/api/config/profiles/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }).then(mapProfile),

  deleteProfile: (id: number) =>
    request<void>(`/api/config/profiles/${id}`, { method: 'DELETE' }),

  // Convenience: set target temp (sends to heating config)
  setTargetTemp: (temp: number) =>
    request<Record<string, unknown>>('/api/config/heating', {
      method: 'PUT',
      body: JSON.stringify({ target_temp: temp }),
    }).then(mapHeatingConfig),

  setMode: (mode: string) =>
    request<Record<string, unknown>>('/api/config/heating', {
      method: 'PUT',
      body: JSON.stringify({ mode }),
    }).then(mapHeatingConfig),

  // Energy stats
  getEnergyStats: () =>
    request<Record<string, unknown>>('/api/energy/current').then(mapEnergyStats),

  getEnergyDaily: (days: number = 7) =>
    request<{ days: Record<string, unknown>[]; summary: Record<string, unknown> }>(
      `/api/energy/daily?days=${days}`,
    ).then((raw) => ({
      days: raw.days.map(mapEnergyDaily),
      summary: {
        totalGasM3: (raw.summary.total_gas_m3 as number) || 0,
        totalCostTl: (raw.summary.total_cost_tl as number) || 0,
        totalRuntimeMinutes: (raw.summary.total_runtime_minutes as number) || 0,
        avgDailyCostTl: (raw.summary.avg_daily_cost_tl as number) || 0,
      },
    })),

  getEnergyMonthly: () =>
    request<Record<string, unknown>>('/api/energy/monthly'),

  getEnergyEfficiency: () =>
    request<Record<string, unknown>>('/api/energy/efficiency'),

  getEnergyEstimate: (currentTemp: number, targetTemp: number, outdoorTemp: number) =>
    request<Record<string, unknown>>(
      `/api/energy/estimate?current_temp=${currentTemp}&target_temp=${targetTemp}&outdoor_temp=${outdoorTemp}`,
    ),

  getEnergyPredict: (hours: number = 4) =>
    request<Record<string, unknown>>(`/api/energy/predict?hours=${hours}`),

  getThermalAnalysis: () =>
    request<Record<string, unknown>>('/api/energy/thermal-analysis'),

  // Alerts
  getAlerts: () =>
    request<{ alerts: Alert[]; count: number; critical_count: number; warning_count: number }>(
      '/api/energy/alerts',
    ),

  // Alert dismiss (stored client-side in localStorage)
  dismissAlert: (alertId: string) => {
    const dismissed = JSON.parse(localStorage.getItem('dismissed_alerts') || '[]');
    if (!dismissed.includes(alertId)) {
      dismissed.push(alertId);
      localStorage.setItem('dismissed_alerts', JSON.stringify(dismissed));
    }
  },

  getDismissedAlerts: (): string[] => {
    return JSON.parse(localStorage.getItem('dismissed_alerts') || '[]');
  },

  clearDismissedAlerts: () => {
    localStorage.removeItem('dismissed_alerts');
  },

  // Notifications
  registerFcmToken: (token: string, deviceName?: string) =>
    request<void>('/api/notifications/register', {
      method: 'POST',
      body: JSON.stringify({ token, device_name: deviceName }),
    }),

  getNotificationPreferences: () =>
    request<Record<string, unknown>>('/api/notifications/preferences'),

  updateNotificationPreferences: (prefs: Record<string, unknown>) =>
    request<Record<string, unknown>>('/api/notifications/preferences', {
      method: 'PUT',
      body: JSON.stringify(prefs),
    }),
};
                                                                                                                                                                                        