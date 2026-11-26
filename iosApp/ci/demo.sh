#!/bin/bash
# Capturas y vídeos de la app en el simulador contra el Cider de demostración (fake_cider.py).
#   DEMO_FIXTURES_KEY=… iosApp/ci/demo.sh <RemoteForCider.app> <carpeta de salida> [<.xctestrun>]
# Con el .xctestrun (xcodebuild build-for-testing) graba además los recorridos de UITests/DemoFlows.swift.
# En el simulador, 127.0.0.1 es el propio Mac: ahí escucha el Cider de mentira.
set -euo pipefail
APP=$1
OUT=$2
XCTESTRUN=${3:-}
HERE=$(cd "$(dirname "$0")" && pwd)
DEMO=$HERE/../Demo
WORK=$(mktemp -d)
BID=io.github.jefer30039.remoteforcider
mkdir -p "$OUT"

echo "::group::Cider de demostración"
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in "$DEMO/fixtures.tar.gz.enc" -pass env:DEMO_FIXTURES_KEY \
  | tar -xz -C "$WORK"
python3 -m venv "$WORK/venv"
"$WORK/venv/bin/pip" install -q aiohttp
"$WORK/venv/bin/python" -I "$DEMO/fake_cider.py" --fixtures "$WORK/fixtures" > "$OUT/fake-cider.log" 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null || true' EXIT
for _ in $(seq 1 30); do curl -sf http://127.0.0.1:10767/api/v2/client/info >/dev/null && break; sleep 0.5; done
curl -sf http://127.0.0.1:10767/api/v2/client/info
echo "::endgroup::"

echo "::group::Simulador"
# Simulador del runner (cambia con la imagen): SIM_DEVICE y SIM_RUNTIME (prefijo, p. ej. "iOS-27").
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
  # La imagen no trae ese modelo creado: se crea con el runtime más nuevo que encaje.
  RUNTIME_ID=$(xcrun simctl list runtimes available -j | SIM_RUNTIME="$SIM_RUNTIME" python3 -c '
import json, os, sys
ids = [r["identifier"] for r in json.load(sys.stdin)["runtimes"] if os.environ["SIM_RUNTIME"] in r["identifier"]]
print(sorted(ids)[-1] if ids else "")')
  echo "Creando $SIM_DEVICE con $RUNTIME_ID"
  UDID=$(xcrun simctl create "$SIM_DEVICE" "$SIM_DEVICE" "$RUNTIME_ID")
fi
echo "$SIM_DEVICE ($SIM_RUNTIME): $UDID"
xcrun simctl boot "$UDID" || true
xcrun simctl bootstatus "$UDID" -b
xcrun simctl status_bar "$UDID" override --time 9:41 --dataNetwork wifi --wifiMode active --wifiBars 3 \
  --cellularMode active --cellularBars 4 --batteryState charged --batteryLevel 100
xcrun simctl install "$UDID" "$APP"
echo "::endgroup::"

CONNECTED=(-ciderHost 127.0.0.1 -ciderPort 10767 -ciderToken demo)
SPANISH=(-AppleLanguages "(es)" -AppleLocale es_ES)

reset_demo() {
  curl -sf -X POST http://127.0.0.1:10767/demo/reset >/dev/null || echo "No se pudo reiniciar el Cider de demostración"
}

launch() {
  xcrun simctl terminate "$UDID" "$BID" 2>/dev/null || true
  reset_demo
  xcrun simctl launch "$UDID" "$BID" "$@" >/dev/null
}

# captura <nombre> <espera> <argumentos de lanzamiento…>
shot() {
  local name=$1 wait=$2
  shift 2
  launch "$@"
  sleep "$wait"
  xcrun simctl io "$UDID" screenshot "$OUT/$name.png" >/dev/null
  echo "captura: $name.png"
}

# video <nombre> <segundos> <argumentos de lanzamiento…>
video() {
  local name=$1 seconds=$2
  shift 2
  xcrun simctl terminate "$UDID" "$BID" 2>/dev/null || true
  xcrun simctl io "$UDID" recordVideo --codec=h264 --force "$OUT/$name.mp4" 2>/dev/null &
  local rec=$!
  sleep 1.5
  launch "$@"
  sleep "$seconds"
  kill -INT $rec
  wait $rec || true
  echo "vídeo: $name.mp4"
}

xcrun simctl ui "$UDID" appearance light
shot 01-conectar-es 5 "${SPANISH[@]}"
shot 02-inicio-miniplayer-es 8 "${CONNECTED[@]}" "${SPANISH[@]}" -demoTab home
shot 03-biblioteca-albumes-es 10 "${CONNECTED[@]}" "${SPANISH[@]}" -demoTab library
shot 04-reproduccion-es 14 "${CONNECTED[@]}" "${SPANISH[@]}" -demoOpenPlayer YES
shot 07-letra-es 16 "${CONNECTED[@]}" "${SPANISH[@]}" -demoOpenPlayer YES -demoLyrics YES
shot 05-conectar-en 5
xcrun simctl ui "$UDID" appearance dark
shot 06-biblioteca-albumes-oscuro-es 10 "${CONNECTED[@]}" "${SPANISH[@]}" -demoTab library
xcrun simctl ui "$UDID" appearance light
video 10-arranque-hasta-reproduccion 10 "${CONNECTED[@]}" "${SPANISH[@]}" -demoOpenPlayer YES
video 11-arranque-biblioteca 8 "${CONNECTED[@]}" "${SPANISH[@]}" -demoTab library

# recorrido <nombre> <prueba de DemoFlows>: graba la pantalla mientras XCUITest mueve la app.
flow() {
  local name=$1 test=$2
  xcrun simctl terminate "$UDID" "$BID" 2>/dev/null || true
  reset_demo
  xcrun simctl io "$UDID" recordVideo --codec=h264 --force "$OUT/$name.mp4" 2>/dev/null &
  local rec=$!
  sleep 1.5
  TEST_RUNNER_DEMO_OUT="$(cd "$OUT" && pwd)" xcodebuild test-without-building -xctestrun "$XCTESTRUN" -destination "id=$UDID" \
    -only-testing:"RemoteForCiderUITests/DemoFlows/$test" > "$OUT/$name.xcodebuild.log" 2>&1 \
    || echo "El recorrido $test falló (ver $name.xcodebuild.log)"
  kill -INT $rec
  wait $rec || true
  echo "vídeo: $name.mp4"
}

if [ -n "$XCTESTRUN" ]; then
  flow 20-recorrido-desplazar-minimiza-barra testScrollMinimizesTabBar
  flow 21-recorrido-abrir-y-cerrar-hoja testOpenAndClosePlayer
  flow 23-recorrido-letra testLyrics
  flow 22-recorrido-pantalla-bloqueo testLockScreen
  # El último: deja Liquid Glass en teñido.
  flow 24-recorrido-vidrio-tenido testTintedGlass
fi

# Registro de la app (println de shared incluido) para depurar lo que no se ve.
xcrun simctl spawn "$UDID" log show --last 3m --style compact \
  --predicate "process == \"RemoteForCider\"" > "$OUT/app.log" 2>&1 || true
grep FALTA "$OUT/fake-cider.log" | sort -u > "$OUT/rutas-sin-grabar.txt" || true
echo "Rutas sin grabar: $(wc -l < "$OUT/rutas-sin-grabar.txt")"
