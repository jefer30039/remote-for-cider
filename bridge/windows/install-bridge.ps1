# Remote for Cider — network bridge for Windows.
#
# Cider's API only listens on 127.0.0.1:10767. This forwards port 10768 from the
# network to it with Windows' built-in port proxy, so the Android app can reach
# Cider on your LAN or hotspot. Nothing to install; survives reboots.
# Port 10767 can't be reused: Cider checks it at startup and moves to a random
# port if anything holds it.
#
# Run in an elevated PowerShell (Run as administrator):
#   powershell -ExecutionPolicy Bypass -File install-bridge.ps1

#Requires -RunAsAdministrator
$ErrorActionPreference = 'Stop'

netsh interface portproxy delete v4tov4 listenport=10768 listenaddress=0.0.0.0 2>$null | Out-Null
netsh interface portproxy add v4tov4 listenport=10768 listenaddress=0.0.0.0 connectport=10767 connectaddress=127.0.0.1

# Only private networks (home, your phone's hotspot if marked private), never public Wi-Fi.
Remove-NetFirewallRule -DisplayName 'Remote for Cider bridge' -ErrorAction SilentlyContinue
New-NetFirewallRule -DisplayName 'Remote for Cider bridge' -Direction Inbound -Protocol TCP `
  -LocalPort 10768 -Action Allow -Profile Private | Out-Null

# The port proxy needs the IP Helper service.
Set-Service -Name iphlpsvc -StartupType Automatic
Start-Service -Name iphlpsvc

Write-Host 'Done. Cider is reachable on port 10768 from private networks.'
Write-Host 'If your Wi-Fi or hotspot shows as "Public", set it to "Private" in Windows settings.'
