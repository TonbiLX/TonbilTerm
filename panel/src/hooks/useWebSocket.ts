'use client';

import { useEffect, useRef, useCallback, useState } from 'react';
import { useSensorStore } from '@/stores/sensorStore';
import type { WsMessage, SensorReading, BoilerStatus, WeatherData } from '@/types';

const MAX_RECONNECT_DELAY = 30_000;
const BASE_DELAY = 1_000;

/**
 * Map backend telemetry message to frontend SensorReading format.
 * Backend sends: { device_id, temperature, humidity, pressure, ... }
 * Frontend expects: { deviceId, temp, hum, pres, ts }
 */
function mapTelemetry(data: Record<string, unknown>): SensorReading {
  return {
    deviceId: (data.device_id as string) || '',
    temp: (data.temperature as number) ?? 0,
    hum: (data.humidity as number) ?? 0,
    pres: (data.pressure as number) ?? 0,
    ts: data.timestamp
      ? new Date(data.timestamp as string).getTime() / 1000
      : Date.now() / 1000,
  };
}

/**
 * Map backend boiler message to frontend BoilerStatus.
 * relay her zaman alinir; mode ve target varsa (backend config kaynakli)
 * onlar da store'a yazilir.
 */
/**
 * boiler/telemetry mesajından SADECE relay + fallback + uptime alınır.
 * mode ve target ALINMAZ — ESP cihazı kendi lokal modunu gönderir,
 * sunucu modu ile uyumsuz olabilir. Mode/target sadece config_update'ten gelir.
 */
function mapBoilerStatus(data: Record<string, unknown>): Partial<BoilerStatus> {
  return {
    relay: (data.relay as boolean) ?? (data.active as boolean) ?? false,
    localFallback: (data.localFallback as boolean) || false,
    uptime: (data.runtimeToday as number) || undefined,
  };
}

/**
 * config_update mesajından mode + target + relay alınır.
 * Bu sunucu tarafından yayınlanır — güvenilir kaynak.
 */
function mapConfigUpdate(data: Record<string, unknown>): Partial<BoilerStatus> {
  const result: Partial<BoilerStatus> = {};
  if (data.relay_state !== undefined) result.relay = data.relay_state as boolean;
  if (data.mode !== undefined) result.mode = data.mode as string;
  if (data.target_temp !== undefined) result.target = data.target_temp as number;
  return result;
}

export function useWebSocket() {
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectAttemptRef = useRef(0);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout>>();
  const connectRef = useRef<() => void>();
  const [connected, setConnected] = useState(false);
  const [lastMessage, setLastMessage] = useState<WsMessage | null>(null);

  const store = useSensorStore;

  const getWsUrl = useCallback(() => {
    if (typeof window === 'undefined') return '';
    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${proto}//${window.location.host}/ws`;
  }, []);

  const handleMessage = useCallback(
    (event: MessageEvent) => {
      try {
        const msg = JSON.parse(event.data) as WsMessage;
        setLastMessage(msg);

        const { updateSensorData, updateBoilerStatus, updateWeather, setConnectionStatus } =
          store.getState();

        // Backend message format: { type: "telemetry"|"boiler"|"relay_state"|..., data: {...} }
        const data = (msg.data || msg.payload || {}) as Record<string, unknown>;

        switch (msg.type) {
          // Backend sends "telemetry" for sensor data
          case 'telemetry':
          case 'sensor': {
            const reading = mapTelemetry(data);
            if (reading.deviceId) {
              updateSensorData(reading.deviceId, reading);
            }
            break;
          }

          // Backend sends "boiler" for relay/combo device status
          case 'boiler': {
            const status = mapBoilerStatus(data);
            updateBoilerStatus(status);
            break;
          }

          // Backend sends "relay_state" on relay transitions
          case 'relay_state': {
            const relayOn = (data.state as boolean) ?? false;
            updateBoilerStatus({ relay: relayOn });
            break;
          }

          // Backend sends "config_update" when heating config changes
          case 'config_update':
          case 'config': {
            // Sunucu tarafından yayınlanan güvenilir config değişikliği
            const configUpdate = mapConfigUpdate(data);
            if (Object.keys(configUpdate).length > 0) {
              updateBoilerStatus(configUpdate);
            }
            break;
          }

          // Backend sends "boost_update" for boost mode changes
          case 'boost_update': {
            // Could update a boost store, for now update boiler relay
            if (data.active !== undefined) {
              updateBoilerStatus({ relay: data.active as boolean });
            }
            break;
          }

          // Backend sends "initial_state" with cached state on new connection
          case 'initial_state': {
            const stateData = data as Record<string, unknown>;
            // Apply telemetry for all cached devices
            const telemetry = stateData.telemetry as Record<string, Record<string, unknown>> | undefined;
            if (telemetry) {
              for (const [, deviceData] of Object.entries(telemetry)) {
                const reading = mapTelemetry(deviceData);
                if (reading.deviceId) {
                  updateSensorData(reading.deviceId, reading);
                }
              }
            }
            // Apply relay state
            if (stateData.relay_state !== undefined) {
              updateBoilerStatus({ relay: stateData.relay_state as boolean });
            }
            break;
          }

          // Legacy frontend format support
          case 'weather': {
            const weather: WeatherData = {
              temp: (data.temperature as number) ?? (data.temp as number) ?? 0,
              humidity: (data.humidity as number) ?? 0,
              windSpeed: (data.wind_speed as number) ?? (data.windSpeed as number) ?? 0,
              description: (data.description as string) || '--',
              icon: (data.icon as string) || 'clear-day',
              feelsLike: (data.feels_like as number) ?? (data.feelsLike as number) ?? 0,
            };
            updateWeather(weather);
            break;
          }

          case 'device_status': {
            // Device online/offline — update device list in store
            const statusDeviceId = (data.device_id as string) || '';
            const isOnline = (data.online as boolean) ?? (data.status === 'online');
            if (statusDeviceId) {
              const currentDevices = store.getState().devices;
              const updatedDevices = currentDevices.map((d) =>
                d.deviceId === statusDeviceId ? { ...d, online: isOnline } : d,
              );
              store.getState().setDevices(updatedDevices);
            }
            break;
          }

          default:
            break;
        }
      } catch (err) {
        console.warn('[WS] Mesaj parse hatasi:', err);
      }
    },
    [store],
  );

  const scheduleReconnect = useCallback(() => {
    const attempt = reconnectAttemptRef.current;
    const delay = Math.min(BASE_DELAY * Math.pow(2, attempt), MAX_RECONNECT_DELAY);
    reconnectAttemptRef.current = attempt + 1;

    reconnectTimerRef.current = setTimeout(() => {
      connectRef.current?.();
    }, delay);
  }, []);

  const connect = useCallback(() => {
    const url = getWsUrl();
    if (!url) return;

    try {
      const ws = new WebSocket(url);
      wsRef.current = ws;

      ws.onopen = () => {
        reconnectAttemptRef.current = 0;
        setConnected(true);
        useSensorStore.getState().setConnectionStatus('connected');
      };

      ws.onmessage = handleMessage;

      ws.onclose = (event: CloseEvent) => {
        console.warn(`[WS] Baglanti kapandi: code=${event.code} reason=${event.reason}`);
        setConnected(false);
        useSensorStore.getState().setConnectionStatus('reconnecting');
        scheduleReconnect();
      };

      ws.onerror = () => {
        ws.close();
      };
    } catch (err) {
      console.warn('[WS] Baglanti olusturulamadi:', err);
      scheduleReconnect();
    }
  }, [getWsUrl, handleMessage, scheduleReconnect]);

  useEffect(() => {
    connectRef.current = connect;
  }, [connect]);

  useEffect(() => {
    connect();
    return () => {
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
      wsRef.current?.close();
    };
  }, [connect]);

  return { connected, send: (data: string) => wsRef.current?.send(data) };
}

export function useWebSocketInit() {
  useWebSocket();
}