"""MQTT credential management for device provisioning.

Manages Mosquitto password file and ACL entries for dynamically
provisioned ESP32 devices. The passwd file is on a shared Docker
volume between the API and Mosquitto containers.
"""

import base64
import hashlib
import logging
import os
import secrets
import string
import subprocess
from pathlib import Path

logger = logging.getLogger("tonbil.mqtt_credentials")

# Shared volume path — mounted in both api and mosquitto containers
MQTT_PASSWD_FILE = os.getenv("MQTT_PASSWD_FILE", "/mosquitto/config/passwd")
MQTT_ACL_FILE = os.getenv("MQTT_ACL_FILE", "/mosquitto/config/acl")

# Password generation config
PASSWORD_LENGTH = 32
PASSWORD_ALPHABET = string.ascii_letters + string.digits + "!@#$%^&*"

# Mosquitto PBKDF2-SHA512 hash parameters
MOSQUITTO_HASH_PREFIX = "$7$"
MOSQUITTO_HASH_ITERATIONS = 101
MOSQUITTO_SALT_LENGTH = 12
MOSQUITTO_KEY_LENGTH = 64


def generate_password() -> str:
    """Generate a cryptographically secure random password.

    32 chars, alphanumeric + special characters.
    Uses secrets module for CSPRNG.
    """
    return "".join(secrets.choice(PASSWORD_ALPHABET) for _ in range(PASSWORD_LENGTH))


def generate_mqtt_username(device_id: str) -> str:
    """Generate MQTT username from device_id.

    Format: dev_{device_id}
    """
    return f"dev_{device_id}"


def add_to_mosquitto(username: str, password: str) -> bool:
    """Add or update a user in the Mosquitto passwd file.

    Generates a Mosquitto-compatible PBKDF2-SHA512 hash in pure Python
    and writes it to the shared passwd file on the Docker volume.

    Args:
        username: MQTT username (e.g., "dev_sensor-A1B2")
        password: Plain-text password to hash

    Returns:
        True if successful, False otherwise
    """
    passwd_path = Path(MQTT_PASSWD_FILE)

    # Generate Mosquitto-compatible PBKDF2-SHA512 hash in Python
    # (API container does not have mosquitto_passwd binary)
    try:
        _write_passwd_entry(passwd_path, username, password)
        _signal_mosquitto_reload()
        return True
    except Exception as e:
        logger.error("Failed to write passwd file: %s", e)
        return False


def remove_from_mosquitto(username: str) -> bool:
    """Remove a user from the Mosquitto passwd file.

    Args:
        username: MQTT username to remove

    Returns:
        True if successful, False otherwise
    """
    passwd_path = Path(MQTT_PASSWD_FILE)

    # Remove line from passwd file directly (no mosquitto_passwd binary in API container)
    try:
        if not passwd_path.exists():
            logger.warning("Passwd file not found: %s", passwd_path)
            return False

        lines = passwd_path.read_text().splitlines()
        filtered = [
            line
            for line in lines
            if not line.startswith(f"{username}:")
        ]

        if len(filtered) == len(lines):
            logger.warning("User not found in passwd file: %s", username)
            return False

        passwd_path.write_text("\n".join(filtered) + "\n")
        logger.info("MQTT credential removed from passwd file: %s", username)

        _remove_acl_entry(username)
        _signal_mosquitto_reload()
        return True
    except Exception as e:
        logger.error("Failed to remove from passwd file: %s", e)
        return False


def add_acl_entry(username: str, device_id: str) -> bool:
    """Add device-specific ACL entry to Mosquitto ACL file.

    Each device gets write access to its own telemetry/status topics
    and read access to its command/config topics.

    Args:
        username: MQTT username (e.g., "dev_sensor-A1B2")
        device_id: Device identifier (e.g., "sensor-A1B2")

    Returns:
        True if successful
    """
    acl_path = Path(MQTT_ACL_FILE)

    acl_block = f"""
# --- Device: {device_id} (auto-provisioned) ---
user {username}
topic write tonbil/devices/{device_id}/telemetry
topic write tonbil/devices/{device_id}/status
topic read tonbil/devices/{device_id}/command
topic read tonbil/devices/{device_id}/config
topic read tonbil/system/time
"""

    try:
        # Check if entry already exists
        if acl_path.exists():
            existing = acl_path.read_text()
            if f"user {username}" in existing:
                logger.info("ACL entry already exists for: %s", username)
                return True

        with open(acl_path, "a") as f:
            f.write(acl_block)

        logger.info("ACL entry added for: %s (%s)", username, device_id)
        return True
    except Exception as e:
        logger.error("Failed to add ACL entry: %s", e)
        return False


def generate_credentials(device_id: str) -> dict:
    """Generate complete MQTT credentials for a device.

    Args:
        device_id: Device identifier (e.g., "sensor-A1B2")

    Returns:
        Dict with username, password, and topic info
    """
    username = generate_mqtt_username(device_id)
    password = generate_password()

    topics = {
        "telemetry": f"tonbil/devices/{device_id}/telemetry",
        "status": f"tonbil/devices/{device_id}/status",
        "command": f"tonbil/devices/{device_id}/command",
        "config": f"tonbil/devices/{device_id}/config",
    }

    return {
        "username": username,
        "password": password,
        "topics": topics,
    }


def _mosquitto_hash_password(password: str) -> str:
    """Generate a Mosquitto-compatible PBKDF2-SHA512 password hash.

    Mosquitto password file format (v7):
        $7$<iterations>$<base64_salt>$<base64_hash>

    Where:
        - $7$ identifies PBKDF2-SHA512 algorithm
        - iterations is the PBKDF2 iteration count (decimal, default 101)
        - salt is 12 random bytes, base64-encoded
        - hash is PBKDF2-SHA512 output (64 bytes), base64-encoded

    Args:
        password: Plain-text password to hash

    Returns:
        Mosquitto-compatible hash string (e.g., "$7$101$base64salt$base64hash")
    """
    salt = secrets.token_bytes(MOSQUITTO_SALT_LENGTH)
    dk = hashlib.pbkdf2_hmac(
        "sha512",
        password.encode("utf-8"),
        salt,
        MOSQUITTO_HASH_ITERATIONS,
        dklen=MOSQUITTO_KEY_LENGTH,
    )
    salt_b64 = base64.b64encode(salt).decode("ascii")
    hash_b64 = base64.b64encode(dk).decode("ascii")
    return f"{MOSQUITTO_HASH_PREFIX}{MOSQUITTO_HASH_ITERATIONS}${salt_b64}${hash_b64}"


def _write_passwd_entry(passwd_path: Path, username: str, password: str) -> None:
    """Write or update a hashed entry in the passwd file.

    Generates a Mosquitto-compatible PBKDF2-SHA512 hash for the password.
    Removes existing entry for the username if present, then appends new one.
    """
    lines = []
    if passwd_path.exists():
        lines = [
            line
            for line in passwd_path.read_text().splitlines()
            if line.strip() and not line.startswith(f"{username}:")
        ]

    hashed = _mosquitto_hash_password(password)
    lines.append(f"{username}:{hashed}")

    passwd_path.write_text("\n".join(lines) + "\n")
    logger.info("Wrote hashed passwd entry for: %s", username)


def _remove_acl_entry(username: str) -> None:
    """Remove ACL block for a specific user from the ACL file."""
    acl_path = Path(MQTT_ACL_FILE)

    if not acl_path.exists():
        return

    try:
        lines = acl_path.read_text().splitlines()
        filtered = []
        skip_block = False

        for line in lines:
            # Detect start of auto-provisioned device block
            if line.strip().startswith(f"# --- Device:") and "(auto-provisioned)" in line:
                skip_block = True
                continue

            if skip_block:
                if line.strip().startswith(f"user {username}"):
                    continue
                if line.strip().startswith("topic "):
                    continue
                if line.strip() == "":
                    skip_block = False
                    continue
                # Non-empty, non-topic line means new block
                skip_block = False

            filtered.append(line)

        acl_path.write_text("\n".join(filtered) + "\n")
        logger.info("ACL entry removed for: %s", username)
    except Exception as e:
        logger.error("Failed to remove ACL entry: %s", e)


def _signal_mosquitto_reload() -> None:
    """Signal Mosquitto to reload its configuration.

    Sends SIGHUP to the mosquitto process if we can find it,
    otherwise logs a warning that manual reload is needed.
    """
    try:
        # Send SIGHUP via pkill (works when API shares PID namespace or network)
        result = subprocess.run(
            ["pkill", "-HUP", "mosquitto"],
            capture_output=True,
            text=True,
            timeout=5,
        )
        if result.returncode == 0:
            logger.info("Sent SIGHUP to mosquitto for config reload")
            return
    except FileNotFoundError:
        pass
    except Exception:
        pass

    logger.warning(
        "Could not signal mosquitto to reload. "
        "Changes will take effect on next mosquitto restart."
    )
