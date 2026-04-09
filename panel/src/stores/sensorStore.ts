import { create } from 'zustand';
import type { Room, BoilerStatus, WeatherData, SensorReading, DeviceInfo } from '@/types';

interface SensorState {
  rooms: Record<string, Room>;
  devices: DeviceInfo[];
  boilerStatus: BoilerStatus;
  outdoorWeather: WeatherData;
  connectionStatus: 'connected' | 'disconnected' | 'reconnecting';

  updateSensorData: (deviceId: string, reading: SensorReading) => void;
  updateBoilerStatus: (status: Partial<BoilerStatus>) => void;
  updateWeather: (weather: WeatherData) => void;
  setRooms: (rooms: Room[]) => void;
  setDevices: (devices: DeviceInfo[]) => void;
  setConnectionStatus: (status: 'connected' | 'disconnected' | 'reconnecting') => void;
}

const defaultBoilerStatus: BoilerStatus = {
  relay: false,
  mode: 'auto',
  target: 22.0,
  uptime: 0,
  localFallback: false,
};

const defaultWeather: WeatherData = {
  temp: 0,
  humidity: 0,
  windSpeed: 0,
  description: '--',
  icon: 'clear-day',
  feelsLike: 0,
};

export const useSensorStore = create<SensorState>((set, get) => ({
  rooms: {},
  devices: [],
  boilerStatus: defaultBoilerStatus,
  outdoorWeather: defaultWeather,
  connectionStatus: 'disconnected',

  updateSensorData: (deviceId, reading) =>
    set((state) => {
      const updatedRooms = { ...state.rooms };

      // 1. Find which room this device belongs to via device list
      const device = state.devices.find((d) => d.deviceId === deviceId);
      const roomId = device?.roomId;

      let matched = false;

      // 2. Match by device's room_id (most reliable)
      if (roomId && updatedRooms[roomId.toString()]) {
        const key = roomId.toString();
        updatedRooms[key] = {
          ...updatedRooms[key],
          latestReading: reading,
          currentTemp: reading.temp ??updatedRooms[key].currentTemp,
          currentHumidity: reading.hum ??updatedRooms[key].currentHumidity,
        };
        matched = true;
      }

      // 3. Fallback: match by room id, device id, or room name
      if (!matched) {
        for (const key of Object.keys(updatedRooms)) {
          const room = updatedRooms[key];
          if (
            room.id.toString() === deviceId ||
            room.name === deviceId ||
            key === deviceId
          ) {
            updatedRooms[key] = {
              ...room,
              latestReading: reading,
              currentTemp: reading.temp ??room.currentTemp,
              currentHumidity: reading.hum ??room.currentHumidity,
            };
            break;
          }
        }
      }

      return { rooms: updatedRooms };
    }),

  updateBoilerStatus: (status) =>
    set((state) => {
      const merged = { ...state.boilerStatus };
      for (const [key, val] of Object.entries(status)) {
        if (val !== undefined) {
          (merged as Record<string, unknown>)[key] = val;
        }
      }
      return { boilerStatus: merged };
    }),

  updateWeather: (weather) => set({ outdoorWeather: weather }),

  setRooms: (rooms) =>
    set({
      rooms: rooms.reduce(
        (acc, room) => {
          acc[room.id.toString()] = room;
          return acc;
        },
        {} as Record<string, Room>,
      ),
    }),

  setDevices: (devices) => set({ devices }),

  setConnectionStatus: (status) => set({ connectionStatus: status }),
}));
