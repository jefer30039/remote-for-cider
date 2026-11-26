#!/bin/bash
# Escribe el UDID del simulador del CI (SIM_DEVICE con el runtime SIM_RUNTIME más nuevo), creándolo si
# la imagen no lo trae. Lo usan el paso que lo arranca al principio del trabajo y demo.sh.
set -euo pipefail
SIM_DEVICE=${SIM_DEVICE:-iPhone 18 Pro Max}
SIM_RUNTIME=${SIM_RUNTIME:-iOS-27}
UDID=$(xcrun simctl list devices available -j | SIM_DEVICE="$SIM_DEVICE" SIM_RUNTIME="$SIM_RUNTIME" python3 -c '
import json, os, re, sys
best = None
for runtime, devices in json.load(sys.stdin)["devices"].items():
    if os.environ["SIM_RUNTIME"] not in runtime:
        continue
    # "…SimRuntime.iOS-27-2" (y en Xcode 27 también "…iOS-27-1-iPhone19…" para modelos concretos).
    m = re.search(r"iOS-(\d+)-(\d+)", runtime)
    if not m:
        continue
    version = (int(m.group(1)), int(m.group(2)))
    for d in devices:
        if d["name"] == os.environ["SIM_DEVICE"] and (best is None or version > best[0]):
            best = (version, d["udid"])
print(best[1] if best else "")')
if [ -z "$UDID" ]; then
  RUNTIME_ID=$(xcrun simctl list runtimes available -j | SIM_RUNTIME="$SIM_RUNTIME" python3 -c '
import json, os, sys
ids = [r["identifier"] for r in json.load(sys.stdin)["runtimes"] if os.environ["SIM_RUNTIME"] in r["identifier"]]
print(sorted(ids)[-1] if ids else "")')
  echo "Creando $SIM_DEVICE con $RUNTIME_ID" >&2
  UDID=$(xcrun simctl create "$SIM_DEVICE" "$SIM_DEVICE" "$RUNTIME_ID")
fi
echo "$UDID"
