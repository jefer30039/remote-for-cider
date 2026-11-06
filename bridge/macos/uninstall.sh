#!/usr/bin/env bash
# Removes the Remote for Cider bridge LaunchAgent.
LABEL="io.github.remoteforcider.bridge"
launchctl bootout "gui/$(id -u)/$LABEL" 2>/dev/null || true
rm -f "$HOME/Library/LaunchAgents/$LABEL.plist"
echo "Removed."
