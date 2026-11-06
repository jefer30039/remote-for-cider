#!/usr/bin/env bash
# Removes the Remote for Cider bridge (and the optional helper) user services.
set -euo pipefail
UNIT_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
for s in remote-for-cider-bridge remote-for-cider-companion; do
  systemctl --user disable --now "$s.service" 2>/dev/null || true
  rm -f "$UNIT_DIR/$s.service"
done
rm -rf "${XDG_DATA_HOME:-$HOME/.local/share}/remote-for-cider"
systemctl --user daemon-reload
echo "Removed."
