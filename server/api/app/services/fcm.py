"""FCM push notification servisi - Firebase Cloud Messaging HTTP v1 API."""

import logging
from typing import Any

import httpx
from sqlalchemy import select

from app.config import settings
from app.models.database import async_session
from app.models.fcm import FCMToken
from app.models.notification_prefs import NotificationPreferences

logger = logging.getLogger("tonbil.fcm")

# FCM legacy HTTP API endpoint
FCM_SEND_URL = "https://fcm.googleapis.com/fcm/send"


class FCMService:
    """Firebase Cloud Messaging push notification servisi.

    FCM_ENABLED=false ise tum metotlar sessizce atlanir (no-op).
    Basarisiz token'lar otomatik temizlenir (stale token cleanup).
    """

    def __init__(self):
        self._enabled = settings.fcm_enabled
        self._server_key = settings.fcm_server_key

        if self._enabled and not self._server_key:
            logger.warning("FCM etkin ama FCM_SERVER_KEY ayarlanmamis, bildirimler gonderilemeyecek")
            self._enabled = False

    @property
    def enabled(self) -> bool:
        return self._enabled

    async def send_notification(
        self,
        tokens: list[str],
        title: str,
        body: str,
        data: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        """Birden fazla token'a FCM notification gonder.

        Returns:
            {"success": int, "failure": int, "invalid_tokens": list[str]}
        """
        if not self._enabled or not tokens:
            return {"success": 0, "failure": 0, "invalid_tokens": []}

        result = {"success": 0, "failure": 0, "invalid_tokens": []}

        # FCM legacy API: tek seferde 1000 token gonderilebilir
        # Batch'lere bol
        batch_size = 1000
        for i in range(0, len(tokens), batch_size):
            batch = tokens[i : i + batch_size]
            batch_result = await self._send_batch(batch, title, body, data)
            result["success"] += batch_result["success"]
            result["failure"] += batch_result["failure"]
            result["invalid_tokens"].extend(batch_result["invalid_tokens"])

        # Gecersiz token'lari DB'den temizle
        if result["invalid_tokens"]:
            await self._cleanup_invalid_tokens(result["invalid_tokens"])

        return result

    async def _send_batch(
        self,
        tokens: list[str],
        title: str,
        body: str,
        data: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        """Tek bir FCM batch gonder."""
        result = {"success": 0, "failure": 0, "invalid_tokens": []}

        payload: dict[str, Any] = {
            "registration_ids": tokens,
            "notification": {
                "title": title,
                "body": body,
                "sound": "default",
                "click_action": "OPEN_APP",
            },
        }

        if data:
            # FCM data payload'da tum degerler string olmali
            payload["data"] = {k: str(v) for k, v in data.items()}

        headers = {
            "Authorization": f"key={self._server_key}",
            "Content-Type": "application/json",
        }

        try:
            async with httpx.AsyncClient(timeout=10) as client:
                resp = await client.post(FCM_SEND_URL, json=payload, headers=headers)

                if resp.status_code == 200:
                    resp_data = resp.json()
                    result["success"] = resp_data.get("success", 0)
                    result["failure"] = resp_data.get("failure", 0)

                    # Basarisiz token'lari tespit et
                    results_list = resp_data.get("results", [])
                    for idx, r in enumerate(results_list):
                        error = r.get("error")
                        if error in (
                            "NotRegistered",
                            "InvalidRegistration",
                            "MismatchSenderId",
                        ):
                            if idx < len(tokens):
                                result["invalid_tokens"].append(tokens[idx])

                    if result["failure"] > 0:
                        logger.warning(
                            "FCM gonderim: %d basarili, %d basarisiz",
                            result["success"],
                            result["failure"],
                        )
                elif resp.status_code == 401:
                    logger.error("FCM yetkilendirme hatasi: server key gecersiz")
                else:
                    logger.error("FCM hatasi: HTTP %d - %s", resp.status_code, resp.text[:200])

        except httpx.TimeoutException:
            logger.error("FCM timeout: bildirim gonderilemedi")
            result["failure"] = len(tokens)
        except Exception as e:
            logger.error("FCM gonderim hatasi: %s", e)
            result["failure"] = len(tokens)

        return result

    async def _cleanup_invalid_tokens(self, invalid_tokens: list[str]) -> None:
        """Gecersiz FCM token'lari DB'den sil."""
        try:
            from sqlalchemy import delete

            async with async_session() as session:
                await session.execute(
                    delete(FCMToken).where(FCMToken.token.in_(invalid_tokens))
                )
                await session.commit()
                logger.info("Gecersiz FCM token'lar temizlendi: %d adet", len(invalid_tokens))
        except Exception as e:
            logger.error("FCM token temizleme hatasi: %s", e)

    async def _get_tokens_for_alert(self, alert_type: str) -> list[str]:
        """Belirli bir alert tipi icin bildirim almak isteyen kullanicilarin token'larini getir.

        alert_type: "temp_low", "temp_high", "relay_change", "sensor_offline"
        """
        pref_column_map = {
            "temp_low": NotificationPreferences.temp_low_alert,
            "temp_high": NotificationPreferences.temp_high_alert,
            "relay_change": NotificationPreferences.relay_change_alert,
            "sensor_offline": NotificationPreferences.sensor_offline_alert,
        }

        pref_filter = pref_column_map.get(alert_type)

        try:
            async with async_session() as session:
                if pref_filter is not None:
                    # Tercihi acik olan kullanicilarin token'larini getir
                    # Tercihi OLMAYAN kullanicilar da bildirim alir (default: acik)
                    query = (
                        select(FCMToken.token)
                        .outerjoin(
                            NotificationPreferences,
                            FCMToken.user_id == NotificationPreferences.user_id,
                        )
                        .where(
                            (pref_filter == True) | (pref_filter.is_(None))  # noqa: E712
                        )
                    )
                else:
                    query = select(FCMToken.token)

                result = await session.execute(query)
                return [row[0] for row in result.fetchall()]
        except Exception as e:
            logger.error("FCM token sorgulama hatasi: %s", e)
            return []

    async def notify_temp_alert(
        self,
        device_id: str,
        temp: float,
        threshold: float,
        alert_type: str,
    ) -> None:
        """Sicaklik esik uyarisi gonder.

        alert_type: "temp_low" veya "temp_high"
        """
        if not self._enabled:
            return

        if alert_type == "temp_low":
            title = "Dusuk Sicaklik Uyarisi"
            body = f"Sicaklik {temp:.1f}C'ye dustu (esik: {threshold:.1f}C)"
        else:
            title = "Yuksek Sicaklik Uyarisi"
            body = f"Sicaklik {temp:.1f}C'ye yukseldi (esik: {threshold:.1f}C)"

        tokens = await self._get_tokens_for_alert(alert_type)
        if tokens:
            await self.send_notification(
                tokens=tokens,
                title=title,
                body=body,
                data={
                    "type": "temp_alert",
                    "alert_type": alert_type,
                    "device_id": device_id,
                    "temperature": temp,
                    "threshold": threshold,
                },
            )
            logger.info("Sicaklik uyarisi gonderildi: %s, %.1fC", alert_type, temp)

    async def notify_relay_change(self, state: bool) -> None:
        """Relay durumu degisikligi bildirimi gonder."""
        if not self._enabled:
            return

        title = "Kombi Durumu"
        body = "Kombi ACILDI" if state else "Kombi KAPANDI"

        tokens = await self._get_tokens_for_alert("relay_change")
        if tokens:
            await self.send_notification(
                tokens=tokens,
                title=title,
                body=body,
                data={
                    "type": "relay_change",
                    "state": state,
                },
            )

    async def notify_sensor_offline(self, device_id: str) -> None:
        """Sensor cevrimdisi bildirimi gonder."""
        if not self._enabled:
            return

        title = "Sensor Cevrimdisi"
        body = f"Sensor {device_id} baglanti kaybetti"

        tokens = await self._get_tokens_for_alert("sensor_offline")
        if tokens:
            await self.send_notification(
                tokens=tokens,
                title=title,
                body=body,
                data={
                    "type": "sensor_offline",
                    "device_id": device_id,
                },
            )
            logger.info("Sensor offline bildirimi gonderildi: %s", device_id)


# Singleton instance
fcm_service = FCMService()
