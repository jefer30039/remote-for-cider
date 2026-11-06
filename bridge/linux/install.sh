#!/usr/bin/env bash
# Remote for Cider — network bridge for Linux (systemd user service).
#
# Cider's API only listens on 127.0.0.1:10767. This forwards port 10768 from the
# network to it, so the Android app can reach Cider on your LAN or hotspot.
# Port 10767 can't be reused: Cider checks it on every interface at startup and
# moves to a random port if anything holds it.
#
# Usage: ./install.sh [--interface IFACE] [--with-easyeffects]
#   --interface IFACE    only listen on that network interface (e.g. wlp3s0)
#                        instead of all of them. Recommended on laptops that join
#                        public Wi-Fi.
#   --with-easyeffects   also install the optional helper that lets the app switch
#                        EasyEffects presets "Bocina" (bass on) / "Normal".
set -euo pipefail

IFACE=""
WITH_EE=0
while [ $# -gt 0 ]; do
  case "$1" in
    --interface) IFACE="${2:?missing interface name}"; shift 2 ;;
    --with-easyeffects) WITH_EE=1; shift ;;
    -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

command -v socat >/dev/null || { echo "socat is required (e.g. sudo apt install socat)." >&2; exit 1; }
UNIT_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
mkdir -p "$UNIT_DIR"

LISTEN="TCP-LISTEN:10768,reuseaddr,fork,keepalive"
[ -n "$IFACE" ] && LISTEN="$LISTEN,so-bindtodevice=$IFACE"

cat > "$UNIT_DIR/remote-for-cider-bridge.service" <<UNIT
[Unit]
Description=Remote for Cider: network bridge to Cider's API (port 10768)
After=network-online.target

[Service]
ExecStart=$(command -v socat) $LISTEN TCP:127.0.0.1:10767,keepalive
Restart=always
RestartSec=5

[Install]
WantedBy=default.target
UNIT

SERVICES="remote-for-cider-bridge.service"

if [ "$WITH_EE" = 1 ]; then
  DATA="${XDG_DATA_HOME:-$HOME/.local/share}/remote-for-cider"
  mkdir -p "$DATA"
  cp "$(dirname "$0")/companion.py" "$DATA/companion.py"
  cat > "$UNIT_DIR/remote-for-cider-companion.service" <<UNIT
[Unit]
Description=Remote for Cider: optional helper (EasyEffects presets, port 10769)
After=network-online.target

[Service]
ExecStart=/usr/bin/python3 $DATA/companion.py 0.0.0.0 10769 $IFACE
Restart=always
RestartSec=5

[Install]
WantedBy=default.target
UNIT
  SERVICES="$SERVICES remote-for-cider-companion.service"
fi

systemctl --user daemon-reload
# shellcheck disable=SC2086
systemctl --user enable --now $SERVICES
echo "Done. Listening on port 10768${IFACE:+ (interface $IFACE)}."
PORTS="10768"; [ "$WITH_EE" = 1 ] && PORTS="10768 and 10769"
echo "If you use a firewall (e.g. ufw), allow TCP $PORTS from your local network."
