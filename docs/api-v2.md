# API v2 de Cider 4.0.9.1 — referencia para el cliente

Leída de `docs/referencia/rutas-v2.min.js` y del bundle (`main-DsuNUYUa.js`).

## Convenciones
- Éxito: `{data: ...}` o `{data: ..., meta: {offset, limit, total}}`.
- Error: `{error: {code, message}}`. Códigos: `INVALID_REQUEST` (400), `INSUFFICIENT_SCOPE` (403), `RPC_TIMEOUT` (504, el frontend de Cider no respondió).
- Error de token (distinto formato): 403 `{"error":"UNAUTHORIZED_APP_TOKEN"}`, sin objeto.
- Los comandos (`POST` sin respuesta útil) devuelven `{data:{}}` **en cuanto se envían**: no confirman que el reproductor los haya ejecutado. El estado real llega por Socket.IO.
- Paginación: query `offset` (≥0, por defecto 0) y `limit` (por defecto 50). La validación del servidor admite hasta 200, pero **las rutas de biblioteca devuelven 400 si `limit` > 100** ("Apple Music library caps pages at 100"). Usar 100 como máximo.
- `type`/`id`/`url`/`href` no admiten comillas (`'` ni `"`).
- Token sin campo `scopes` = acceso a todo (tokens antiguos).

## Reproducción (scope `playback`)
| Método | Ruta | Cuerpo | `data` |
|---|---|---|---|
| GET | `/playback` | | snapshot completo (`playbackSnapshot`) |
| GET | `/playback/now-playing` | | atributos de la canción actual |
| GET | `/playback/state` | | `{state: "playing"\|"paused"}` |
| GET | `/playback/time` | | tiempo actual |
| GET | `/playback/audio-quality` | | info de calidad |
| POST | `/playback/play`, `/pause`, `/stop`, `/toggle`, `/next` | | `{}` |
| POST | `/playback/previous` | `{force?: bool}` | `{}` (sin `force`, reinicia si va avanzada) |
| POST | `/playback/seek` | `{position: number≥0}` (segundos) | `{}` |
| POST | `/playback/play-item` (= `/play-id`) | `{type, id}` | `{}` |
| POST | `/playback/play-url` | `{url}` | `{}` |
| POST | `/playback/play-href` | `{href}` | `{}` |
| POST | `/playback/play-collection` | `{type, id, shuffle?}` | `{}`; 422 `NO_PLAYABLE_TRACKS` |
| GET/PATCH | `/playback/rate` | `{rate: 0.25–4}` | `{rate}` |
| GET/PUT | `/playback/repeat` | `{mode: "none"\|"one"\|"all"}` | `{mode}` |
| POST | `/playback/repeat/toggle` | | `{}` |
| GET/PUT | `/playback/shuffle` | `{enabled: bool}` | `{enabled}` |
| POST | `/playback/shuffle/toggle` | | `{}` |
| GET/PUT | `/playback/autoplay` | `{enabled: bool}` | `{enabled}` |
| POST | `/playback/autoplay/toggle` | | `{}` |

## Cola (scope `queue`)
| Método | Ruta | Cuerpo | `data` |
|---|---|---|---|
| GET | `/queue?offset&limit` | | `{items, position}` + `meta.total` |
| GET | `/queue/position` | | `{position, total}` |
| POST | `/queue/jump` | `{index}` | `{}` |
| POST | `/queue/add-next`, `/queue/add-later` | `{type, id}` | `{}` |
| POST | `/queue/move` | `{from, to}` | `{}` |
| DELETE | `/queue/items/:index` | | `{}` |
| DELETE | `/queue` | | `{}` (vacía la cola) |
| POST | `/queue/shuffle` | | `{}` (= toggle shuffle) |
| GET | `/queue/smart-optimizer` | | estado |
| POST | `/queue/smart-optimizer/toggle` | | estado nuevo |

## Audio (scope `audio`)
| Método | Ruta | Cuerpo |
|---|---|---|
| GET/PATCH | `/audio/volume` | `{volume: 0–1}` → `{volume}` |
| GET/PATCH | `/audio/crossfade` | `{enabled?, durationSec? 0–30, skipEnabled?, tempoSensitivity? 0–1, loudnessSensitivity? 0–1, exclusions?}` |
| GET/PATCH | `/audio/automix` | `{enabled?, energyBoost?, normalizeDurationMs? 0–30000, exclusions?}` |
| GET/PATCH | `/audio/atmos` | `{enabled?, binaural?, devicePreferences?}` |
| GET/PATCH | `/audio/listening-mode` | `{mode: "off"\|"game"\|"antifatigue"}` |

`exclusions = {albums?, playlists?, djMixes?, liveAlbums?}` (booleanos). Los PATCH de audio necesitan al menos un campo y devuelven el estado nuevo.

## Biblioteca (scope `library`)
| Método | Ruta | `data` |
|---|---|---|
| GET | `/library/now-playing/status` | `{inLibrary, rating}` |
| POST | `/library/now-playing/add` | `{}` |
| DELETE | `/library/now-playing` | `{}` |
| POST | `/library/now-playing/love`, `/dislike` | `{}` |
| PUT | `/library/now-playing/rating` `{rating: -1\|0\|1}` | `{rating}` |
| DELETE | `/library/now-playing/rating` | `{}` |
| GET | `/library/playlists`, `/albums`, `/artists`, `/songs` | `{items}` + meta |
| GET | `/library/playlists/:id/songs`, `/albums/:id/songs`, `/artists/:id/albums` | `{items}` + meta |

## Cuenta (scope `account`)
- `GET /client/tokens` → `{developerToken, userToken, storefront}` (tokens de MusicKit). No se usa: las letras van por el proxy.

## Letras
- `/api/v1/lyrics/:id` **no sirve desde fuera**: la protege `verifyBearerAuth`, con una clave interna (`capi`) que Cider genera y no expone.
- **Usar el proxy de Apple Music** (scope `library`), que añade los tokens en el servidor:
  - `GET /amapi/raw/v1/catalog/$STOREFRONT/songs/{id}/lyrics` → `data.data[0].attributes.ttml`, con `itunes:timing="Line"`.
  - `GET /amapi/raw/v1/catalog/$STOREFRONT/songs/{id}/syllable-lyrics` → igual, con `itunes:timing="Word"` (karaoke).
  - `$STOREFRONT` va literal: Cider lo sustituye. `{id}` es `playParams.id` del now-playing (catálogo).
  - La respuesta de Apple va envuelta dos veces: `{data: {data: [...]}}`.
  - Nunca hace falta el scope `account`.

## Proxy de Apple Music (scope `library`, solo lectura)
- `GET /amapi/search?term&types&limit(1-25)&offset`: búsqueda en el catálogo.
- `GET /amapi/catalog/:type/:id?include&views`
- `GET /amapi/raw/v1/...`: cualquier ruta `v1/` relativa. Errores: `AMAPI_ERROR` con el estado HTTP de Apple.
  - `v1/me/library/pins?include=catalog`: elementos fijados de la biblioteca (artistas, playlists, álbumes, canciones). Los `library-artists` no traen foto; la del catálogo viene en `relationships.catalog.data[0].attributes.artwork`.
  - `v1/me/library/recently-added?limit=1..10`: añadidos recientemente.
  - Las carátulas de este proxy son plantillas: `{w}x{h}bb.{f}`.

## Playlists (escritura, scope `library`)
`POST /playlists`, `PATCH /playlists/:id`, `POST|DELETE|PUT /playlists/:id/tracks`, historial `/playlists/:id/history/restore`. Límite: 10 escrituras/min por token. Detalle completo en `referencia/catalogo-eventos.json` (`data.playlists`).

## Eventos (Socket.IO, canal `API:Playback`, **sin token**)
Payloads reales (catálogo completo en `referencia/catalogo-eventos.json`, `data.websocket`):
- `playbackStatus.nowPlayingItemDidChange`: atributos de la pista (como `/playback/now-playing`).
- `playbackStatus.playbackStateDidChange`: `{attributes, state: playing|paused|stopped, anchor}`.
- `playbackStatus.playbackTimeDidChange` (~1 s): `{currentPlaybackTime, currentPlaybackDuration, currentPlaybackTimeRemaining, isPlaying, anchor}`.
- `anchor = {startEpochMs, endEpochMs, currentTime, duration, timestamp, rate}`: permite interpolar el progreso localmente. **`endEpochMs` puede faltar** (visto en el teléfono el 2026-11-07, seguramente con duración desconocida: emisoras en vivo); tratar todos los campos como opcionales.
- `playbackStatus.nowPlayingStatusDidChange`: `{inLibrary, inFavorites}`.
- `playbackStatus.transitionStarted|Completed`: `{mode, from, to}`.
- `playerStatus.volumeDidChange`: número 0–1. `repeatModeDidChange`: 0/1/2. `shuffleModeDidChange`: 0/1.
- `queueStatus.queueChanged`: `{position, total}`. `smartQueueChanged`: `{enabled, busy, pending, message}`.
- `audioStatus.*` y `libraryStatus.playlistChanged`: ver catálogo.
- El servidor también envía `message: "Hello from server"` al conectar (ignorar).
- **Seguridad:** cualquiera en la red puede escuchar los eventos (qué suena), aunque no pueda controlar nada.

## Otros
- `UA` / `ValidateClientUserAgent`: las rutas `/api*` y `/socket.io*` no exigen user-agent `Cider`.
- CORS abierto (`origin: *`) y cabeceras permitidas `Content-Type, Authorization, apptoken`.
- El cooldown de `/auth/request` es por IP. Detrás del puente todas las peticiones llegan desde 127.0.0.1, así que el cooldown es compartido.
- API v1 (`/api/v1/playback/*`): versión antigua con el mismo `apptoken`. No se usará.
