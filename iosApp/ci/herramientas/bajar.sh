#!/bin/bash
# Baja del artefacto simulator-demo de una ejecución solo lo que no es vídeo (el artefacto pesa ~900 MB
# por los vídeos y `gh run download` iba a <1 MB/min). bajar.sh RUN [sufijos-a-saltar] [solo-los-que-contengan]
set -e
RUN=$1; D=~/cider-remote-ios-capturas/$(date +%F)-run$RUN; mkdir -p "$D"
ID=$(gh api repos/jefer30039/remote-for-cider/actions/runs/$RUN/artifacts --jq '.artifacts[] | select(.name=="simulator-demo") | .id')
URLF=$(mktemp)
curl -s -o /dev/null -w '%{redirect_url}' -H "Authorization: Bearer $(gh auth token)" \
  https://api.github.com/repos/jefer30039/remote-for-cider/actions/artifacts/$ID/zip > "$URLF"
cd /tmp && python3 -I "$(dirname "$0")/rangezip.py" "$URLF" "$D" "${2:-}" ${3:+"$3"}
rm -f "$URLF"; echo "$D"
