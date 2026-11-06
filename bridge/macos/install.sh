#!/usr/bin/env bash
# Remote for Cider — network bridge for macOS (LaunchAgent + socat from Homebrew).
#
# Cider's API only listens on 127.0.0.1:10767. This forwards port 10768 from the
# network to it so the Android app can reach Cider on your LAN or hotspot.
# Port 10767 can't be reused: Cider checks it at startup and moves to a random
# port if anything holds it.
set -euo pipefail

SOCAT="$(command -v socat || true)"
[ -n "$SOCAT" ] || { echo "socat is required: brew install socat" >&2; exit 1; }

LABEL="io.github.remoteforcider.bridge"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
mkdir -p "$HOME/Library/LaunchAgents"
cat > "$PLIST" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>$LABEL</string>
  <key>ProgramArguments</key>
  <array>
    <string>$SOCAT</string>
    <string>TCP-LISTEN:10768,reuseaddr,fork,keepalive</string>
    <string>TCP:127.0.0.1:10767,keepalive</string>
  </array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
</dict>
</plist>
PLIST
launchctl bootout "gui/$(id -u)/$LABEL" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$PLIST"
echo "Done. Listening on port 10768. macOS may ask to allow incoming connections for socat."
