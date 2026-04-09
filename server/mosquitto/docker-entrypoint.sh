#!/bin/sh
# Initialize dynamic passwd/acl files from static config if they don't exist.
# These files are on a shared volume so the API container can write to them.

DYNAMIC_DIR="/mosquitto/dynamic"

# Create dynamic directory if missing
mkdir -p "$DYNAMIC_DIR"

# Copy base passwd file if dynamic one doesn't exist
if [ ! -f "$DYNAMIC_DIR/passwd" ]; then
    if [ -f "/mosquitto/config/passwd.seed" ]; then
        cp /mosquitto/config/passwd.seed "$DYNAMIC_DIR/passwd"
        echo "Initialized dynamic passwd from seed"
    else
        # Create minimal passwd with api_server
        echo "api_server:tonbil_api_2024" > "$DYNAMIC_DIR/passwd"
        echo "web_client:tonbil_web_2024" >> "$DYNAMIC_DIR/passwd"
        echo "device_default:tonbil_device_2024" >> "$DYNAMIC_DIR/passwd"
        echo "Created default dynamic passwd"
    fi
fi

# Copy base ACL file if dynamic one doesn't exist
if [ ! -f "$DYNAMIC_DIR/acl" ]; then
    if [ -f "/mosquitto/config/acl.seed" ]; then
        cp /mosquitto/config/acl.seed "$DYNAMIC_DIR/acl"
        echo "Initialized dynamic ACL from seed"
    else
        cat > "$DYNAMIC_DIR/acl" << 'ACLEOF'
# Tonbil Termostat - Mosquitto ACL
user api_server
topic readwrite tonbil/#

user web_client
topic read tonbil/+/telemetry
topic read tonbil/+/status
topic read tonbil/system/#

# Pattern rules for provisioned devices
pattern write tonbil/devices/%u/telemetry
pattern write tonbil/devices/%u/status
pattern read tonbil/devices/%u/command
pattern read tonbil/devices/%u/config
pattern read $SYS/#
ACLEOF
        echo "Created default dynamic ACL"
    fi
fi

# Ensure mosquitto can read AND api container can write
chmod 777 "$DYNAMIC_DIR"
chmod 666 "$DYNAMIC_DIR/passwd" "$DYNAMIC_DIR/acl"

# Start mosquitto
exec /usr/sbin/mosquitto -c /mosquitto/config/mosquitto.conf
