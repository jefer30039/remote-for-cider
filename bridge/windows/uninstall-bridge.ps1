# Removes the Remote for Cider bridge. Run in an elevated PowerShell.
#Requires -RunAsAdministrator
netsh interface portproxy delete v4tov4 listenport=10768 listenaddress=0.0.0.0
Remove-NetFirewallRule -DisplayName 'Remote for Cider bridge' -ErrorAction SilentlyContinue
Write-Host 'Removed.'
