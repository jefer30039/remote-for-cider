#!/usr/bin/env python3
"""Graba respuestas reales de Cider para el modo demostración (solo GET: no cambia lo que suena).

    python3 iosApp/Demo/record.py            # graba las rutas de ROUTES en iosApp/Demo/fixtures/
    python3 iosApp/Demo/record.py RUTA...    # añade rutas sueltas (p. ej. las que fake_cider.py dio como faltantes)
    python3 iosApp/Demo/record.py --pack     # cifra fixtures/ en fixtures.tar.gz.enc (lo que va al repositorio)

El token se lee de ~/.config/cider-remote/token-dev y la clave de cifrado de
~/.config/cider-remote/demo-fixtures-key (se crea si no existe). Ninguno de los dos va al repositorio:
la clave se copia al secreto DEMO_FIXTURES_KEY de GitHub para que el flujo de iOS descifre las fixtures.
Las fixtures en claro (biblioteca del usuario, letras con derechos) están en .gitignore.
"""
import base64
import json
import os
import secrets
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
FIXTURES = os.path.join(HERE, "fixtures")
PACKED = os.path.join(HERE, "fixtures.tar.gz.enc")
CONFIG = os.path.expanduser("~/.config/cider-remote")
CIDER = os.environ.get("CIDER_URL", "http://127.0.0.1:10767")

# Canción de demostración: letra por sílaba y portada animada ("Broken Clocks", SZA, Ctrl (Deluxe)).
SONG = os.environ.get("DEMO_SONG", "1628388227")
SF = "$STOREFRONT"

ROUTES = [
    "/api/v2/client/info",
    "/api/v2/playback",
    "/api/v2/playback/now-playing",
    "/api/v2/playback/audio-quality",
    "/api/v2/playback/autoplay",
    "/api/v2/playback/repeat",
    "/api/v2/playback/shuffle",
    "/api/v2/library/now-playing/status",
    "/api/v2/queue/position",
    # La cola entera: fake_cider.py la recorta según offset/limit.
    "/api/v2/queue?offset=0&limit=200",
    "/api/v2/audio/volume",
    "/api/v2/audio/crossfade",
    "/api/v2/audio/atmos",
    "/api/v2/audio/automix",
    "/api/v2/audio/listening-mode",
    # Biblioteca: páginas grandes; el servidor de demostración también las recorta.
    "/api/v2/library/playlists?offset=0&limit=100",
    "/api/v2/library/albums?offset=0&limit=100",
    "/api/v2/library/artists?offset=0&limit=100",
    "/api/v2/library/songs?offset=0&limit=100",
    # Letras y portada animada de la canción de demostración.
    f"/api/v2/amapi/raw/v1/catalog/{SF}/songs/{SONG}/syllable-lyrics",
    f"/api/v2/amapi/raw/v1/catalog/{SF}/songs/{SONG}/lyrics",
    f"/api/v2/amapi/raw/v1/catalog/{SF}/songs/{SONG}/albums?extend=editorialVideo",
    f"/api/v2/amapi/raw/v1/catalog/{SF}/songs/{SONG}?include=albums,artists",
    # Inicio, Nuevo, Radio, recientes y fijados.
    "/api/v2/amapi/raw/v1/me/recommendations?name=listen-now&timezone=+00:00&with=friendsMix,library,social"
    "&extend=editorialArtwork,plainEditorialNotes,editorialVideo&art%5Burl%5D=c,f",
    f"/api/v2/amapi/raw/v1/editorial/{SF}/groupings?name=music&platform=web&tabs=subscriber"
    "&extend=editorialArtwork,artistUrl,plainEditorialNotes&art%5Burl%5D=f",
    f"/api/v2/amapi/raw/v1/editorial/{SF}/groupings?name=radio&platform=web&tabs=subscriber"
    "&extend=editorialArtwork,artistUrl,plainEditorialNotes&art%5Burl%5D=f",
    "/api/v2/amapi/raw/v1/me/library/recently-added?limit=10&offset=0",
    "/api/v2/amapi/raw/v1/me/library/pins?include=catalog",
    f"/api/v2/amapi/raw/v1/recommendations/{SF}?extend=editorialArtwork"
    "&types=editorial-items,apple-curators,activities&name=search-landing",
]


def token() -> str:
    with open(os.path.join(CONFIG, "token-dev")) as f:
        return f.read().strip()


def key() -> str:
    path = os.path.join(CONFIG, "demo-fixtures-key")
    if not os.path.exists(path):
        os.makedirs(CONFIG, exist_ok=True)
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w") as f:
            f.write(base64.b64encode(secrets.token_bytes(32)).decode())
        print(f"Clave nueva en {path}: cópiala al secreto DEMO_FIXTURES_KEY")
    with open(path) as f:
        return f.read().strip()


def slug(route: str) -> str:
    """Nombre de archivo estable y legible para una ruta."""
    path, _, query = route.partition("?")
    name = path.removeprefix("/api/v2/").replace("$STOREFRONT", "sf").strip("/").replace("/", "_")
    if query:
        name += "__" + "".join(c if c.isalnum() else "-" for c in query)[:60]
        name += "-" + format(abs(hash_stable(query)) % 0xFFFFFF, "06x")
    return name + ".json"


def hash_stable(s: str) -> int:
    h = 0
    for c in s.encode():
        h = (h * 131 + c) & 0xFFFFFFFF
    return h


def record(routes: list[str]) -> None:
    os.makedirs(FIXTURES, exist_ok=True)
    index_path = os.path.join(FIXTURES, "routes.json")
    index = json.load(open(index_path)) if os.path.exists(index_path) else {}
    tok = token()
    for route in routes:
        # `$` y las comas van tal cual (como los manda Ktor); urllib no toca la ruta.
        req = urllib.request.Request(CIDER + route, headers={"apptoken": tok})
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                status, body = r.status, r.read()
        except urllib.error.HTTPError as e:
            status, body = e.code, e.read()
        name = slug(route)
        with open(os.path.join(FIXTURES, name), "wb") as f:
            f.write(body)
        index[route] = {"file": name, "status": status}
        print(f"{status}  {len(body):>8}  {route}")
    with open(index_path, "w") as f:
        json.dump(index, f, indent=1, sort_keys=True)


def pack() -> None:
    tar = subprocess.run(["tar", "-czf", "-", "-C", HERE, "fixtures"], check=True, capture_output=True).stdout
    subprocess.run(
        ["openssl", "enc", "-aes-256-cbc", "-pbkdf2", "-iter", "200000", "-salt", "-out", PACKED, "-pass", "env:DEMO_KEY"],
        input=tar, check=True, env={**os.environ, "DEMO_KEY": key()},
    )
    print(f"{PACKED}: {os.path.getsize(PACKED)} bytes")


if __name__ == "__main__":
    args = sys.argv[1:]
    if args == ["--pack"]:
        pack()
    else:
        record(args or ROUTES)
