"""WebSocket baglanti yoneticisi - real-time panel guncellemeleri."""

import asyncio
import json
import logging
from datetime import datetime, timezone

from fastapi import WebSocket

logger = logging.getLogger("tonbil.websocket")


class WebSocketManager:
    """Aktif WebSocket baglantilari yonetir ve broadcast yapar."""

    def __init__(self):
        self._connections: list[WebSocket] = []
        self._lock = asyncio.Lock()
        # Son bilinen durum (yeni baglantilara gonderilir)
        self._cached_state: dict | None = None

    async def connect(self, websocket: WebSocket):
        """Yeni WebSocket baglantisini kabul et."""
        await websocket.accept()
        async with self._lock:
            self._connections.append(websocket)
        logger.info(
            "WS baglanti kuruldu. Aktif baglanti: %d", len(self._connections)
        )

        # Yeni baglantiya cached state gonder — aninda guncel veri gorsun
        if self._cached_state:
            try:
                safe_data = json.loads(json.dumps(self._cached_state, default=str))
                await websocket.send_json(safe_data)
            except Exception:
                pass

    async def disconnect(self, websocket: WebSocket):
        """WebSocket baglantisini kaldir."""
        async with self._lock:
            if websocket in self._connections:
                self._connections.remove(websocket)
                logger.info(
                    "WS baglanti kapandi. Aktif baglanti: %d", len(self._connections)
                )

    async def broadcast(self, data: dict):
        """Tum bagli client'lara mesaj gonder."""
        # Snapshot al — lock altinda iterate etme (send_json await eder)
        async with self._lock:
            connections = list(self._connections)

        if not connections:
            return

        # Cached state guncelle
        self._update_cached_state(data)

        # datetime objelerini string'e cevir
        safe_data = json.loads(json.dumps(data, default=str))

        # Kopan baglantilari temizlemek icin
        disconnected: list[WebSocket] = []

        for ws in connections:
            try:
                await ws.send_json(safe_data)
            except Exception:
                disconnected.append(ws)

        # Kopan baglantilari temizle
        if disconnected:
            async with self._lock:
                for ws in disconnected:
                    if ws in self._connections:
                        self._connections.remove(ws)
            logger.debug(
                "Kopan %d baglanti temizlendi", len(disconnected)
            )

    async def send_to(self, websocket: WebSocket, data: dict):
        """Tek bir client'a mesaj gonder."""
        try:
            await websocket.send_json(data)
        except Exception as e:
            logger.warning("WS tekil gonderim hatasi: %s", e)
            await self.disconnect(websocket)

    def get_cached_state(self) -> dict | None:
        """Son bilinen durumu dondur (yeni baglantilara gonderilir)."""
        return self._cached_state

    def _update_cached_state(self, data: dict):
        """Gelen mesajla cached state'i guncelle."""
        msg_type = data.get("type")
        if not msg_type:
            return

        if self._cached_state is None:
            self._cached_state = {
                "type": "initial_state",
                "data": {
                    "telemetry": {},
                    "relay_state": False,
                    "devices": {},
                    "config": None,
                    "timestamp": datetime.now(timezone.utc).isoformat(),
                },
            }

        state_data = self._cached_state["data"]

        match msg_type:
            case "sensor" | "telemetry":
                device_id = data["data"].get("device_id", "") or data["data"].get("deviceId", "")
                if device_id:
                    state_data["telemetry"][device_id] = data["data"]
            case "relay_state":
                state_data["relay_state"] = data["data"].get("state", False)
            case "device_status":
                device_id = data["data"].get("device_id", "")
                if device_id:
                    state_data["devices"][device_id] = data["data"]
            case "config_update":
                state_data["config"] = data["data"]
            case "boiler":
                state_data["relay_state"] = data["data"].get("relay", data["data"].get("active", False))

        state_data["timestamp"] = datetime.now(timezone.utc).isoformat()

    @property
    def active_count(self) -> int:
        """Aktif baglanti sayisi."""
        return len(self._connections)


# Singleton instance
ws_manager = WebSocketManager()
