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
# Con reintentos: un corte de red del runner (DNS) tumbaba la ejecución entera.
for i in 1 2 3 4 5; do
  "$WORK/venv/bin/pip" install -q aiohttp && break
  echo "pip falló (intento $i), reintento en 15 s"; sleep 15
done
"$WORK/venv/bin/python" -I "$DEMO/fake_cider.py" --fixtures "$WORK/fixtures" > "$OUT/fake-cider.log" 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null || true' EXIT
for _ in $(seq 1 30); do curl -sf http://127.0.0.1:10767/api/v2/client/info >/dev/null && break; sleep 0.5; done
curl -sf http://127.0.0.1:10767/api/v2/client/info
echo "::endgroup::"

echo "::group::Simulador"
# Simulador del runner: el flujo de CI ya lo arrancó al principio (SIM_UDID, en paralelo con la
# compilación); si no, se elige aquí. `bootstatus -b` espera a que acabe de arrancar.
SIM_DEVICE=${SIM_DEVICE:-iPhone 18 Pro Max}
SIM_RUNTIME=${SIM_RUNTIME:-iOS-27}
UDID=${SIM_UDID:-$("$HERE/sim.sh")}
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
shot 08-cola-es 12 "${CONNECTED[@]}" "${SPANISH[@]}" -demoOpenPlayer YES -demoQueue YES
shot 09-buscar-es 8 "${CONNECTED[@]}" "${SPANISH[@]}" -demoTab search
shot 05-conectar-en 5
xcrun simctl ui "$UDID" appearance dark
shot 06-biblioteca-albumes-oscuro-es 10 "${CONNECTED[@]}" "${SPANISH[@]}" -demoTab library
xcrun simctl ui "$UDID" appearance light
video 10-arranque-hasta-reproduccion 10 "${CONNECTED[@]}" "${SPANISH[@]}" -demoOpenPlayer YES
video 11-arranque-biblioteca 8 "${CONNECTED[@]}" "${SPANISH[@]}" -demoTab library

# Recorridos de DemoFlows, todos en una sola tanda de xcodebuild (cada lanzamiento gastaba ~2 min en
# preparar las pruebas). Cada prueba pide su vídeo con archivos en $OUT/.flow (ver DemoFlows.record):
# `start` con el nombre → se reinicia el Cider de demostración, se empieza a grabar y se crea
# `recording`; `stop` → se para la grabación y se borra `recording`. Van en orden alfabético:
# testTintedGlass, que deja Liquid Glass teñido, es la última.
if [ -n "$XCTESTRUN" ]; then
  FLOW="$OUT/.flow"
  rm -rf "$FLOW"; mkdir -p "$FLOW"
  (
    rec=""
    while [ ! -f "$FLOW/done" ]; do
      if [ -f "$FLOW/start" ]; then
        name=$(cat "$FLOW/start"); rm -f "$FLOW/start"
        reset_demo
        xcrun simctl io "$UDID" recordVideo --codec=h264 --force "$OUT/$name.mp4" 2>/dev/null &
        rec=$!
        sleep 1.5
        touch "$FLOW/recording"
        echo "vídeo: $name.mp4"
      fi
      if [ -f "$FLOW/stop" ]; then
        rm -f "$FLOW/stop"
        if [ -n "$rec" ]; then kill -INT "$rec" 2>/dev/null || true; wait "$rec" 2>/dev/null || true; rec=""; fi
        rm -f "$FLOW/recording"
      fi
      sleep 0.2
    done
    if [ -n "$rec" ]; then kill -INT "$rec" 2>/dev/null || true; wait "$rec" 2>/dev/null || true; fi
  ) &
  RECORDER=$!
  xcrun simctl terminate "$UDID" "$BID" 2>/dev/null || true
  TEST_RUNNER_DEMO_OUT="$(cd "$OUT" && pwd)" xcodebuild test-without-building -xctestrun "$XCTESTRUN" -destination "id=$UDID" \
    -only-testing:RemoteForCiderUITests/DemoFlows > "$OUT/recorridos.xcodebuild.log" 2>&1 \
    || echo "Algún recorrido falló (ver recorridos.xcodebuild.log)"
  grep -E "Test Case .*(passed|failed)" "$OUT/recorridos.xcodebuild.log" || true
  touch "$FLOW/done"
  wait $RECORDER || true
  rm -rf "$FLOW"
fi

# Registro de la app (println de shared incluido) para depurar lo que no se ve.
xcrun simctl spawn "$UDID" log show --last 20m --style compact \
  --predicate "process == \"RemoteForCider\"" > "$OUT/app.log" 2>&1 || true
# Informes de cierre de la app (el simulador los deja en los del Mac).
mkdir -p "$OUT/cierres"
cp ~/Library/Logs/DiagnosticReports/RemoteForCider* "$OUT/cierres/" 2>/dev/null || true
echo "Cierres de la app: $(ls "$OUT/cierres" | wc -l)"
grep FALTA "$OUT/fake-cider.log" | sort -u > "$OUT/rutas-sin-grabar.txt" || true
echo "Rutas sin grabar: $(wc -l < "$OUT/rutas-sin-grabar.txt")"
