#!/usr/bin/env python3
"""
Servicio auxiliar de Cider Remote en el PC: lo que Cider no expone.

Hoy: modo bocina de EasyEffects (cargar el preset "Bocina" o "Normal").
  GET  /easyeffects            -> {"bass": bool, "preset": str, "available": bool}
  POST /easyeffects {"bass":b} -> carga el preset y devuelve el estado

Seguridad: solo atiende peticiones con la cabecera `apptoken` de un token que
Cider acepte (se valida contra 127.0.0.1:10767 y se recuerda 5 minutos).
Uso: companion.py <ip-en-la-que-escuchar> [puerto] [interfaz]
Con [interfaz] (p. ej. wlp3s0) solo atiende por esa interfaz, sea cual sea su IP.
"""
import json
import re
import subprocess
import sys
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = sys.argv[1] if len(sys.argv) > 1 else "127.0.0.1"
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 10769
IFACE = sys.argv[3] if len(sys.argv) > 3 else None
CIDER = "http://127.0.0.1:10767"
PRESET_ON, PRESET_OFF = "Bocina", "Normal"
EE = ["flatpak", "run", "--command=easyeffects", "com.github.wwmm.easyeffects"]

_valid_tokens: dict[str, float] = {}
_ee_lock = threading.Lock()  # una orden de EasyEffects a la vez


def authorized(token: str | None) -> bool:
    if not token:
        return False
    if _valid_tokens.get(token, 0) > time.time():
        return True
    try:
        req = urllib.request.Request(f"{CIDER}/api/v2/client/info", headers={"apptoken": token})
        with urllib.request.urlopen(req, timeout=5) as r:
            ok = r.status == 200
    except Exception:
        ok = False
    if ok:
        _valid_tokens[token] = time.time() + 300
    return ok


class EasyEffectsNotRunning(Exception):
    pass


def ee_running() -> bool:
    # Si el servicio no corre, la CLI arrancaría una instancia nueva (y colgada): no hacerlo.
    out = subprocess.run(["pgrep", "-f", "easyeffects --service-mode"], capture_output=True)
    return out.returncode == 0


def ee(*args: str) -> str:
    if not ee_running():
        raise EasyEffectsNotRunning()
    with _ee_lock:
        out = subprocess.run(EE + list(args), capture_output=True, text=True, timeout=20)
    return out.stdout


def state() -> dict:
    current = re.search(r"^output:\s*(.*)$", ee("-s"), re.MULTILINE)
    preset = current.group(1).strip() if current else ""
    presets = ee("-p")
    return {
        "bass": preset == PRESET_ON,
        "preset": preset,
        "available": PRESET_ON in presets and PRESET_OFF in presets,
    }


class Handler(BaseHTTPRequestHandler):
    server_version = "CiderRemoteCompanion/1"

    def _send(self, code: int, body: dict) -> None:
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _guard(self) -> bool:
        if self.path.split("?")[0] != "/easyeffects":
            self._send(404, {"error": "NOT_FOUND"})
            return False
        if not authorized(self.headers.get("apptoken")):
            self._send(403, {"error": "UNAUTHORIZED_APP_TOKEN"})
            return False
        return True

    def do_GET(self) -> None:
        if self._guard():
            try:
                self._send(200, state())
            except EasyEffectsNotRunning:
                self._send(503, {"error": "EASYEFFECTS_NOT_RUNNING"})

    def do_POST(self) -> None:
        if not self._guard():
            return
        try:
            length = int(self.headers.get("Content-Length") or 0)
            body = json.loads(self.rfile.read(length) or b"{}")
            bass = bool(body["bass"])
        except Exception:
            self._send(400, {"error": "INVALID_REQUEST", "message": 'body: {"bass": true|false}'})
            return
        try:
            ee("-l", PRESET_ON if bass else PRESET_OFF)
            self._send(200, state())
        except EasyEffectsNotRunning:
            self._send(503, {"error": "EASYEFFECTS_NOT_RUNNING"})

    def log_message(self, fmt: str, *args) -> None:  # registro breve en el journal
        sys.stderr.write("%s %s\n" % (self.command, self.path))


class Server(ThreadingHTTPServer):
    def server_bind(self) -> None:
        if IFACE:
            # Solo esta interfaz (SO_BINDTODEVICE): funciona con cualquier IP de esa red.
            import socket
            self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_BINDTODEVICE, IFACE.encode())
        super().server_bind()


if __name__ == "__main__":
    Server((HOST, PORT), Handler).serve_forever()
