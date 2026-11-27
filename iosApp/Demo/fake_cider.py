#!/usr/bin/env python3
"""Cider de mentira para el modo demostración: el simulador de iOS en GitHub Actions no llega al
Cider de casa, así que este servidor hace de Cider con respuestas reales grabadas (record.py).

    pip install aiohttp
    python3 iosApp/Demo/fake_cider.py [--port 10767] [--fixtures iosApp/Demo/fixtures] [--paused]

- Las rutas GET se sirven desde fixtures/routes.json (consulta en cualquier orden; si una ruta solo
  tiene una grabación, vale para cualquier consulta, p. ej. la zona horaria de Inicio).
- Reproducción, cola, volumen, repetir y aleatorio se simulan en memoria y emiten los mismos eventos
  de Socket.IO que Cider (canal API:Playback; Engine.IO v4 solo por WebSocket, sin token).
- Lo que no está grabado responde 404 y se anota como "FALTA" en la salida, para grabarlo después
  con record.py.
- Acepta cualquier token; /api/v2/auth/request aprueba al momento con el token "demo".
- POST /demo/reset vuelve a la canción y el segundo del arranque (demo.sh lo llama antes de cada captura).
"""
import argparse
import asyncio
import copy
import json
import os
import sys
import time
import urllib.parse

from aiohttp import WSMsgType, web

HERE = os.path.dirname(os.path.abspath(__file__))
PUBLIC = {"/api/v2/client/info", "/api/v2/events/catalog", "/api/v2/auth/request"}


def log(*a):
    print(*a, flush=True)


def norm(path: str, query: str) -> str:
    """Clave de una petición: ruta y pares de la consulta decodificados (sin tocar '+') y ordenados."""
    path = urllib.parse.unquote(path)
    pairs = sorted(urllib.parse.unquote(p) for p in query.split("&") if p) if query else []
    return path + ("?" + "&".join(pairs) if pairs else "")


class Fixtures:
    def __init__(self, folder: str):
        self.folder = folder
        index = json.load(open(os.path.join(folder, "routes.json")))
        self.exact: dict[str, dict] = {}
        self.by_path: dict[str, list[dict]] = {}
        for route, entry in index.items():
            path, _, query = route.partition("?")
            self.exact[norm(path, query)] = entry
            self.by_path.setdefault(urllib.parse.unquote(path), []).append(entry)

    def load(self, entry: dict):
        with open(os.path.join(self.folder, entry["file"]), "rb") as f:
            return entry["status"], f.read()

    def find(self, path: str, query: str):
        entry = self.exact.get(norm(path, query))
        if entry is None:
            options = self.by_path.get(urllib.parse.unquote(path), [])
            entry = options[0] if len(options) == 1 else None
        return self.load(entry) if entry else None

    def json(self, route: str):
        path, _, query = route.partition("?")
        found = self.find(path, query)
        return json.loads(found[1]) if found else None


class Player:
    """Estado simulado de Cider."""

    def __init__(self, fx: Fixtures, playing: bool, position: float):
        snap = fx.json("/api/v2/playback")["data"]
        self.snapshot = snap
        self.track = snap.get("nowPlaying") or fx.json("/api/v2/playback/now-playing")["data"]
        queue = fx.json("/api/v2/queue?offset=0&limit=200")["data"]
        self.queue = queue["items"]
        self.index = queue.get("position", 0)
        self.volume = snap.get("volume", 0.5)
        self.repeat = {"none": 0, "one": 1, "all": 2}.get(snap.get("repeatMode", "none"), 0)
        self.shuffle = 1 if snap.get("shuffleMode") else 0
        self.autoplay = snap.get("autoplay", True)
        self.playing = playing
        self._pos = position
        self._at = time.monotonic()
        self.sockets: set[web.WebSocketResponse] = set()
        self._initial = (copy.deepcopy(self.track), copy.deepcopy(self.queue), self.index, playing, position, self.volume)

    async def reset(self):
        """Vuelve al estado del arranque (lo llama demo.sh antes de cada captura: si no, la canción de
        demostración acaba durante los minutos que tarda el simulador y suena otra)."""
        track, queue, self.index, self.playing, position, self.volume = copy.deepcopy(self._initial)
        self.track, self.queue = track, queue
        self.set_position(position)
        await self.emit("playbackStatus.nowPlayingItemDidChange", self.track)
        await self.state_changed()

    # --- Tiempo ---
    @property
    def duration(self) -> float:
        return (self.track.get("durationInMillis") or 0) / 1000

    @property
    def position(self) -> float:
        p = self._pos + (time.monotonic() - self._at if self.playing else 0)
        return min(p, self.duration) if self.duration else p

    def set_position(self, p: float):
        self._pos, self._at = max(0.0, p), time.monotonic()

    def anchor(self) -> dict:
        now = time.time() * 1000
        start = now - self.position * 1000
        return {"startEpochMs": start, "endEpochMs": start + self.duration * 1000, "currentTime": self.position,
                "duration": self.duration, "timestamp": now, "rate": 1}

    def time_payload(self) -> dict:
        return {"currentPlaybackTime": self.position, "currentPlaybackDuration": self.duration,
                "currentPlaybackTimeRemaining": max(0.0, self.duration - self.position),
                "isPlaying": self.playing, "anchor": self.anchor()}

    def playback(self) -> dict:
        s = copy.deepcopy(self.snapshot)
        s.update(state="playing" if self.playing else "paused", nowPlaying=self.track,
                 time={"currentTime": self.position, "duration": self.duration,
                       "remaining": max(0.0, self.duration - self.position)},
                 volume=self.volume, repeatMode=["none", "one", "all"][self.repeat],
                 shuffleMode=bool(self.shuffle), autoplay=self.autoplay)
        return s

    # --- Eventos ---
    async def emit(self, type_: str, data):
        frame = "42" + json.dumps(["API:Playback", {"type": type_, "data": data}])
        for ws in list(self.sockets):
            try:
                await ws.send_str(frame)
            except Exception:
                self.sockets.discard(ws)

    async def state_changed(self):
        await self.emit("playbackStatus.playbackStateDidChange",
                        {"attributes": self.track, "state": "playing" if self.playing else "paused",
                         "anchor": self.anchor()})

    async def jump(self, index: int):
        if not 0 <= index < len(self.queue):
            return
        self.index = index
        attrs = self.queue[index]["track"].get("attributes")
        if attrs:
            self.track = attrs
        self.set_position(0)
        await self.emit("playbackStatus.nowPlayingItemDidChange", self.track)
        await self.emit("queueStatus.queueChanged", {"position": self.index, "total": len(self.queue)})

    async def ticker(self):
        while True:
            await asyncio.sleep(1)
            if self.playing:
                if self.duration and self.position >= self.duration - 0.05:
                    await self.jump(self.index + 1 if self.index + 1 < len(self.queue) else 0)
                await self.emit("playbackStatus.playbackTimeDidChange", self.time_payload())


def ok(data=None):
    return web.json_response({"data": data if data is not None else {}})


def page(items: list, offset: int, limit: int, extra: dict | None = None):
    body = {"data": {"items": items[offset:offset + limit], **(extra or {})},
            "meta": {"offset": offset, "limit": limit, "total": len(items)}}
    return web.json_response(body)


def make_app(fx: Fixtures, player: Player) -> web.Application:
    async def body(request) -> dict:
        try:
            return await request.json()
        except Exception:
            return {}

    async def socket(request):
        ws = web.WebSocketResponse(autoping=True)
        await ws.prepare(request)
        await ws.send_str('0' + json.dumps({"sid": "demo", "upgrades": [], "pingInterval": 25000,
                                            "pingTimeout": 20000, "maxPayload": 1000000}))
        player.sockets.add(ws)

        async def pinger():
            while not ws.closed:
                await asyncio.sleep(25)
                await ws.send_str("2")

        ping = asyncio.create_task(pinger())
        try:
            async for msg in ws:
                if msg.type != WSMsgType.TEXT:
                    continue
                if msg.data.startswith("40"):
                    await ws.send_str('40{"sid":"demo-ns"}')
                    await ws.send_str('42' + json.dumps(["API:Playback", {"type": "message", "data": "Hello from server"}]))
        finally:
            ping.cancel()
            player.sockets.discard(ws)
        return ws

    @web.middleware
    async def auth(request, handler):
        path = request.path
        if path.startswith("/api/") and path not in PUBLIC and not request.headers.get("apptoken"):
            return web.json_response({"error": "UNAUTHORIZED_APP_TOKEN"}, status=401)
        response = await handler(request)
        log(f"{request.method:6} {response.status} {request.path_qs}")
        return response

    async def handle(request):
        method, path = request.method, request.path
        q = request.rel_url.query
        offset, limit = int(q.get("offset", 0)), int(q.get("limit", 50))
        p = player
        b = await body(request) if method != "GET" else {}

        # --- Rutas con estado ---
        if method == "GET":
            if path == "/api/v2/playback":
                return ok(p.playback())
            if path == "/api/v2/playback/now-playing":
                return ok(p.track)
            if path == "/api/v2/playback/time":
                return ok({"currentTime": p.position, "duration": p.duration, "remaining": p.duration - p.position})
            if path == "/api/v2/queue/position":
                return ok({"position": p.index, "total": len(p.queue)})
            if path == "/api/v2/queue":
                return page(p.queue, offset, limit, {"position": p.index})
            if path == "/api/v2/audio/volume":
                return ok({"volume": p.volume})
            if path == "/api/v2/playback/audio-quality":
                # La grabación es AAC; en la demostración suena "en Atmos" para ver la insignia de la hoja.
                return ok({"flavor": "bin_256", "flavorLabel": "Lossless (256kbps variant)"})
            if path.startswith("/api/v2/library/") and path.count("/") == 4:
                # Páginas de la biblioteca: se recorta la grabación más grande.
                found = fx.find(path, "offset=0&limit=100")
                if found:
                    items = json.loads(found[1])["data"]["items"]
                    return page(items, offset, limit)
        else:
            if path == "/demo/reset":
                await p.reset()
                return ok()
            if path == "/api/v2/auth/request":
                return ok({"token": "demo", "scopes": b.get("scopes", [])})
            if path in ("/api/v2/playback/play", "/api/v2/playback/pause", "/api/v2/playback/toggle",
                        "/api/v2/playback/stop"):
                pos = p.position
                p.playing = {"play": True, "pause": False, "stop": False}.get(path.rsplit("/", 1)[1], not p.playing)
                p.set_position(pos)
                await p.state_changed()
                return ok()
            if path == "/api/v2/playback/next":
                await p.jump(p.index + 1)
                return ok()
            if path == "/api/v2/playback/previous":
                if p.position > 3 and not b.get("force"):
                    p.set_position(0)
                    await p.emit("playbackStatus.playbackTimeDidChange", p.time_payload())
                else:
                    await p.jump(p.index - 1)
                return ok()
            if path == "/api/v2/playback/seek":
                p.set_position(float(b.get("position", b.get("seconds", 0))))
                await p.emit("playbackStatus.playbackTimeDidChange", p.time_payload())
                return ok()
            if path == "/api/v2/audio/volume":
                p.volume = float(b.get("volume", p.volume))
                await p.emit("playerStatus.volumeDidChange", p.volume)
                return ok({"volume": p.volume})
            if path == "/api/v2/playback/repeat":
                mode = b.get("mode", "none")
                p.repeat = {"none": 0, "one": 1, "all": 2}.get(mode, 0)
                await p.emit("playerStatus.repeatModeDidChange", p.repeat)
                return ok()
            if path == "/api/v2/playback/shuffle":
                p.shuffle = 1 if b.get("enabled") else 0
                await p.emit("playerStatus.shuffleModeDidChange", p.shuffle)
                return ok()
            if path == "/api/v2/queue/jump":
                await p.jump(int(b.get("index", 0)))
                return ok()
            if path.startswith("/api/v2/queue/items/") and method == "DELETE":
                index = int(path.rsplit("/", 1)[1])
                if 0 <= index < len(p.queue) and index != p.index:
                    del p.queue[index]
                    if index < p.index:
                        p.index -= 1
                await p.emit("queueStatus.queueChanged", {"position": p.index, "total": len(p.queue)})
                return ok()
            if path == "/api/v2/queue/move":
                src, dst = int(b.get("from", 0)), int(b.get("to", 0))
                if 0 <= src < len(p.queue) and 0 <= dst < len(p.queue):
                    p.queue.insert(dst, p.queue.pop(src))
                await p.emit("queueStatus.queueChanged", {"position": p.index, "total": len(p.queue)})
                return ok()
            # Lo demás (añadir a la cola, biblioteca, ajustes…) se acepta sin cambiar nada.
            log(f"(sin efecto) {method} {path} {json.dumps(b)[:200]}")
            return ok()

        found = fx.find(path, request.rel_url.raw_query_string)
        if found:
            status, raw = found
            return web.Response(body=raw, status=status, content_type="application/json")
        log(f"FALTA {method} {request.path_qs}")
        if path.startswith("/api/v2/amapi/"):
            return web.json_response({"error": {"code": "AMAPI_ERROR", "message": "demo: no grabado"}}, status=404)
        return web.json_response({"error": {"code": "NOT_FOUND", "message": "demo: no grabado"}}, status=404)

    app = web.Application(middlewares=[auth])
    app.router.add_get("/socket.io/", socket)
    app.router.add_route("*", "/{tail:.*}", handle)

    async def start_ticker(app):
        app["ticker"] = asyncio.create_task(player.ticker())

    app.on_startup.append(start_ticker)
    return app


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=10767)
    ap.add_argument("--fixtures", default=os.path.join(HERE, "fixtures"))
    ap.add_argument("--paused", action="store_true")
    ap.add_argument("--position", type=float, default=float(os.environ.get("DEMO_POSITION", 47)))
    args = ap.parse_args()
    fx = Fixtures(args.fixtures)
    player = Player(fx, playing=not args.paused, position=args.position)
    log(f"Cider de demostración en http://{args.host}:{args.port} · {player.track.get('name')} · "
        f"cola {player.index + 1}/{len(player.queue)}")
    web.run_app(make_app(fx, player), host=args.host, port=args.port, print=None)


if __name__ == "__main__":
    sys.exit(main())
