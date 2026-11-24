# Letras de Apple Music: puntos de pausa, palabras que se alzan y brillo en notas sostenidas

Documento técnico para reimplementar en `LyricsView.kt` (Compose) tres efectos de la vista de letras sincronizadas de Apple Music que aún no tenemos. Escrito el 2026-11-23. No se ha tocado código del proyecto.

## 0. De dónde salen los números (léase primero)

**Apple no documenta nada de esto** y en este proyecto todavía **no se ha medido ninguno de estos tres efectos en Apple Music**. Todos los valores salen de dos reimplementaciones que imitan Apple Music a ojo:

| Fuente | Qué es | Versión leída | Cómo se leyó |
|---|---|---|---|
| **Cider** | Cliente de escritorio. Su vista de letras (clases `clp-*`) sigue la arquitectura y las constantes de AMLL antiguo casi al pie de la letra. | Cider 4.0.9.1 instalado (`/usr/lib/cider/resources/spa/assets/smart-playlist-store-UtwKIkTc.js` y `…-DYXanacL.css` para el reproductor de letras; `LyricsComposable-Cqj5hsu-.js` para el análisis del TTML). Ojo: el frontend **no** está en `app.asar` (ahí solo está el proceso principal), está en `resources/spa/`. | Código minificado, pasado por prettier. |
| **AMLL** (Apple Music-like Lyrics) | Reimplementación de código abierto, muy detallada. Repositorio `Steve-xmh/applemusic-like-lyrics`, que redirige a `amll-dev/applemusic-like-lyrics`. | `main` en 9db9236 (2026-11-09), `packages/core/src/lyric-player/…`. También la versión anterior de los puntos (commit 5c09596, antes del rediseño 6c07d1d8 «重构间奏点实现并改善视觉效果», 2026-09-13; el PR #610 no tiene descripción). | Código fuente TypeScript/CSS. |

Por eso cada valor lleva una etiqueta:

- **[código]**: copiado de una constante o fórmula de Cider o AMLL. Es exacto *para esa reimplementación*, pero **no está confirmado que Apple use ese valor**.
- **[coinciden]**: Cider y AMLL dan el mismo valor (tienen origen común, así que no son dos medidas independientes).
- **[estimado]**: deducción o propuesta mía, sin respaldo de ninguna de las dos.
- **[sin verificar en Apple]**: nadie lo ha contrastado con una grabación de Apple Music. Vale para todo el documento mientras no se haga lo de la sección 6.

**iOS 16 → iOS 26:** no hay ninguna diferencia documentada en estos tres efectos. Las novedades de las letras en iOS 26 son la traducción y la pronunciación ([9to5Mac](https://9to5mac.com/2025/09/24/heres-everything-new-for-apple-music-in-ios-26/), [AppleInsider](https://appleinsider.com/inside/ios-26/tips/how-to-view-translated-lyrics-in-apple-music)). El brillo existe al menos desde 2023: hay usuarios preguntando por él en el [foro de Apple](https://discussions-jp-prz.apple.com/thread/254995570), sin respuesta oficial. **[sin verificar en Apple]**

## 1. Datos del TTML: qué hay y qué falta

TTML real de Apple ("Sexy Magic", `syllable-lyrics`, descargado por el proxy de Cider):

```xml
<body dur="3:15.840">
<div begin="1.832" end="17.392" itunes:songPart="Chorus">
<p begin="5.760" end="9.880" itunes:key="L3" ttm:agent="v2">
  <span ttm:role="x-bg"><span begin="5.760" end="6.381">(Hey,</span> <span begin="6.869" end="8.101">hey)</span></span>
  <span begin="7.838" end="8.286">It's</span> <span begin="8.286" end="8.739">in</span> …
```

Lo que ya da `TtmlParser` (`shared/src/commonMain/kotlin/cider/remote/Lyrics.kt`):

| Dato | ¿Lo tenemos? | Para qué efecto |
|---|---|---|
| `LyricLine.startSec/endSec` (= `begin`/`end` del `<p>`) | Sí, también en letras por línea | Huecos (puntos) |
| `LyricWord.startSec/endSec/text` por sílaba | Sí, solo con `itunes:timing="Word"` | Alzado y brillo |
| Final de palabra (`LyricWord.spaceAfter`) | Sí | Brillo: se decide **por palabra** (sílabas agrupadas hasta `spaceAfter`) |
| Última palabra de la línea | Se deduce (última sílaba de `words`) | Brillo: la última palabra brilla más |
| Coros `x-bg` | **Se descartan** al analizar | Opcional: en AMLL/Cider los coros suben el doble (0,1 em). No hace falta para los tres efectos. |
| `<body dur>` y `itunes:songPart` | No | No hacen falta (ni Cider ni AMLL ponen puntos tras la última línea) |

Detalles a tener en cuenta:

- **El `begin`/`end` del `<p>` incluye los coros `x-bg`.** En el ejemplo, la línea "It's in your eyes" empieza en 5,760 (el "(Hey,") aunque la voz principal entra en 7,838. Para detectar huecos hay que usar el `<p>`, que es lo que ya guardamos: el coro también se canta. Para el alzado y el brillo se usan los tiempos absolutos de cada sílaba, así que no importa.
- **Las líneas se solapan** (L3 acaba en 9,880 y L4 empieza en 9,480). El hueco se mide desde **el mayor `end` de todas las líneas anteriores**, no desde el `end` de la línea anterior. AMLL lo hace así; Cider usa el de la anterior, que en la práctica casi siempre coincide.
- Huecos reales de esa canción (mayor `end` anterior → `begin` siguiente): 24,56 → 35,62 (11,1 s), 101,94 → 111,58 (9,6 s), 174,78 → 181,11 (6,3 s) y 36,64 → 40,20 (3,6 s). Con el umbral de 5 s de Cider salen tres pausas; con el de 7 s de AMLL, dos.
- Con letras por línea (`timing="Line"`) solo se pueden hacer los puntos. Cider los pone igual en ese caso.
- **Conclusión: no hace falta tocar `TtmlParser`.** Todo se calcula a partir de `Lyrics` en un `remember(lyrics)`.

---

## 2. Efecto 1: puntos de pausa ("…")

### 2.1 Cuándo aparecen

| | Cider 4.0.9.1 | AMLL antiguo (≤ 2026-09) | AMLL actual |
|---|---|---|---|
| Hueco mínimo | **≥ 5 s** (`t.start - r >= 5`, en el analizador del TTML) | ≥ 4 s | **≥ 7 s** (`MIN_INTERLUDE_GAP`) |
| Intervalo | de `end` anterior (0 en la introducción) a `begin` siguiente − 0,01 s | mayor `end` anterior → `begin` siguiente | igual |
| Introducción (antes de la 1.ª línea) | Sí, si la 1.ª línea empieza a ≥ 5 s | Sí | Sí (`anchorLineIndex = -1`, sin la espera de entrada) |
| Tras la última línea | No | No | No |
| Otras condiciones | — | — | Además, el hueco útil (hueco − 500 ms de espera − 1000 ms de salida) tiene que ser ≥ 910 ms; si no, no se pintan puntos y el foco pasa a la línea siguiente. Con un hueco ≥ 7 s esto siempre se cumple. |

Todo **[código]**. Para Apple no hay dato. **Propuesta:** umbral de **5 s** (el de Cider, que es lo que el usuario ve en el PC) en una constante fácil de cambiar, y confirmarlo midiendo (sección 6). **[estimado]**

### 2.2 Geometría

| | Cider | AMLL actual |
|---|---|---|
| Diámetro del punto | 0,34 em × escala base 0,7 ≈ **0,24 em** | **0,30 em** |
| Separación entre puntos | 0,22 em × 0,7 ≈ 0,15 em | **0,18 em** (el código dice "separación = 0,6 × diámetro") |
| Color | El del texto (blanco) | Blanco (`--amll-lp-color`) |
| Posición horizontal | Pegados al margen izquierdo del texto (`margin-left: -0.15em` para compensar) | El primer punto empieza en el margen del texto. Con un dueto, a la derecha. |
| Origen de la escala | Centro del grupo de puntos | Centro del grupo |
| Hueco que ocupa en la lista | Es una línea más: 0,35 em + renglón (1,2 em) + 0,35 em ≈ **1,9 em**. Fuera de la pausa se pliega a **10 px**. | Se inserta tras la línea anterior **solo mientras dura la pausa**: puntos (0,5 em + 2 × 0,4 em de relleno) + 2 × 0,4 em de margen ≈ **2,1 em**. Las líneas de debajo bajan con su muelle. |

(em = tamaño de letra de la línea; con nuestros 34 sp, 1 em = 34 sp.) **[código]**

**Traducido a nuestra vista [estimado]:** a 34 sp, AMLL da puntos de **10 dp** separados **6 dp**; Cider, de 8 dp separados 5 dp. El hueco de AMLL (2,1 em ≈ 71 dp) es casi lo mismo que una línea de texto más su separación (40 dp de renglón + 34 dp de `LineGap` = 74 dp). Propuesta: que la pausa sea **un elemento más de la lista con la altura de un renglón (40 dp) y los puntos centrados en vertical**, con el `LineGap` normal debajo. En tablet (38 sp), puntos de 11 dp.

### 2.3 Animación: AMLL actual (la recomendada)

Es la más elaborada y la más reciente, y la única con una entrada y una salida claras. Los tiempos son relativos al inicio de la pausa `S`; la pausa acaba en `E` (= `begin` de la línea siguiente). **[código]** (`base/interlude-dots.ts`)

**Fases:**

1. **Espera** de **500 ms** (`ENTER_HOLD_MS`): el sitio ya está reservado, pero los puntos son invisibles mientras la lista sube. En la introducción no hay espera (0 ms). Si se salta a mitad de una pausa, el reloj arranca en el punto del salto con 500 ms de espera y todo se recalcula con el tiempo que queda.
2. **Cuerpo**: dura `body = (E − S) − espera − 1000 ms`.
   - **Opacidad del grupo:** de 0 a 1 en **180 ms** con `cubic-bezier(0.59, 0.02, 0.07, 1)`.
   - **Entrada de cada punto:** fundido escalonado, el punto *i* empieza a los *i* × **80 ms** y tarda **750 ms** con curva cuadrática (t²). Los tres están del todo a los 910 ms.
   - **Llenado de los puntos (cómo se reparte el tiempo):** `segment = round((body + 750) / 3)`.
     - El punto 1 se llena de 0 a `segment`.
     - El punto 2, de `segment` a 2 × `segment`.
     - El punto 3 empieza en 2 × `segment` y dura `body − 2 × segment` (unos 750 ms × 2/3 menos que los otros). Solo llega a una fracción `(body − 2·segment)/segment` y **acaba de llenarse durante los primeros 750 ms de la salida**. Así el último punto termina justo cuando la pausa encoge.
     - Cada llenado usa `cubic-bezier(0.56, 0.01, 0.45, 1)`.
     - Opacidad de cada punto = `0.20 + 0.70 × llenado` (apagado **0,2**, encendido **0,9**), multiplicada por su fundido de entrada.
   - **Respiración (escala del grupo):** ciclos de **≥ 4 s**: `ciclos = max(1, floor(body / 4000))` y `periodo = body / ciclos`, de modo que caben ciclos enteros y el último acaba justo al final del cuerpo. En cada ciclo la escala va **1 → 1,25 → 1** con una curva de respiración (inspirar y espirar con pausa arriba y abajo):
     `p(t) = t − 0.084·sin 4πt + 0.008·(1 − cos 4πt) + 0.0046·sin 4πt·(cos 4πt − sin 4πt)`
     `escala = p ≤ 0.5 ? 1 + 0.25·p/0.5 : 1.25 − 0.25·(p − 0.5)/0.5`

     Tabla útil (t = fracción del ciclo): 0 → 1,000; 0,1 → 1,011; 0,2 → 1,081; 0,3 → 1,182; 0,4 → 1,240; 0,5 → 1,250; 0,6 → 1,239; 0,7 → 1,169; 0,8 → 1,068; 0,9 → 1,010; 1 → 1,000.
   - **Cuerpo corto (< 3 s):** no respira (escala 1) y los puntos se encienden enseguida, a 0,9.
3. **Salida**: **1000 ms**, y termina exactamente en `E`.
   - 0–750 ms: la escala **crece 1 → 1,25** con `cubic-bezier(0.14, 0.06, 0.25, 1)` (toma impulso).
   - 750–1000 ms: **encoge 1,25 → 0,4** con `cubic-bezier(0.29, 0.03, 1, 0.38)`.
   - Últimos **250 ms**: opacidad 1 → 0 con `cubic-bezier(0.43, 0.08, 0.83, 0.31)`.
   - Mientras tanto, el punto 3 completa su llenado (lineal en esos 750 ms).
4. **Cancelación** (saltar fuera de la pausa, etc.): fundido de **150 ms** congelando escala y opacidades. En pausa (reproducción detenida) se congela todo.

Mientras dura la pausa, **el foco de la lista apunta a los puntos** (quedan donde estaría la línea activa). La línea anterior pasa a "ya cantada".

### 2.4 Animación: Cider y AMLL antiguo (alternativa)

Lo que el usuario ve hoy en Cider. **[código]** Tiempos en s desde el inicio de la pausa (`n`); `t` = duración total.

- **Escala base 0,7**, con respiración de **±5 %**: `escala = (sin(1.5π − 2·n/r)/20 + 1)`, con `r = t / ceil(t / 1.5)`. En Cider el ciclo dura π·r ≈ **4,3–4,7 s**; AMLL antiguo escribía `2π·n/r` con r ≈ 4,5 s, lo que da casi el mismo periodo. Arranca en el mínimo (sin(1,5π) = −1).
- **Entrada:** la escala se multiplica por `easeOutExpo(n/2)` (crece desde 0 en 2 s). Opacidad 0 hasta 0,5 s y lineal hasta 1 entre 0,5 y 1 s.
- **Salida (últimos 750 ms):** la escala × `1 − easeInOutBack(…/2)`, un pequeño rebote antes de encoger. Opacidad a 0 en los últimos **375 ms**.
- **Puntos:** con `s = t − 0,75`, el punto *k* (0, 1, 2) tiene `clamp(0.25, ((n − k·s/3)·3/s)·0.75, 1)`, es decir, rampas lineales de 0,25 a 1, cada una empieza un tercio después. El tercero solo llega a ~0,75.
- **Hueco en la lista (Cider):** la línea de puntos se despliega de 10 px a su alto con `easeOutExpo` en **0,8 s** y se pliega linealmente en los últimos **0,6 s**.

**Diferencias entre las dos versiones:** la actual respira más (+25 % frente a ±5 %), entra antes y en menos tiempo (180 ms frente a 1 s), sale con un crecimiento y un encogimiento claros en lugar de un rebote pequeño, y los puntos van de 0,2 a 0,9 en vez de 0,25 a 1. Ninguna de las dos se ha comparado con Apple (sección 6). **Propuesta [estimado]:** implementar la actual con sus constantes agrupadas, para poder ajustarla tras medir.

### 2.5 Encaje en `LyricsView.kt`

- **Modelo.** `remember(lyrics)` crea una lista de elementos: `Line(i)` y `Gap(start, end)`. Los huecos se calculan con el mayor `end` acumulado y el umbral de 2.1. `indexAt` y `active` se calculan sobre esa lista, de modo que durante la pausa el elemento activo es el `Gap` y el `focus` (el `Animatable` actual) lo lleva al ancla como a cualquier línea.
- **Adelanto de la lista (`Lead` = 0,3 s).** La lista ya se mueve 0,3 s antes de cada línea. Para que los puntos desaparezcan cuando la lista sube y no después, conviene calcular la animación con `E' = begin siguiente − Lead`. **[estimado]**
- **Altura plegada y desplegada.** Un `Animatable` `gapReveal` (0 → 1) que se anima al entrar en la pausa (por ejemplo, `tween(800)` con salida exponencial, como Cider) y vuelve a 0 al empezar la línea siguiente. El bloque de colocación del `Layout` ya lee `focus.value`; si también lee `gapReveal.value` para la altura del `Gap` (`alto × reveal`), solo se recoloca mientras dura esa animación. **No leer el reloj en la colocación**: cambia en cada fotograma y obligaría a recolocar toda la lista en cada fotograma.
- **Dibujo.** El `Gap` es un `Spacer`/`Box` de 40 dp con `Modifier.drawBehind { val t = clock.value; … }`. Ahí se calculan la escala, la opacidad del grupo y la de cada punto con las fórmulas de 2.3, y se pintan tres `drawCircle` dentro de `scale(s, pivot = centro del grupo)`. Al leer el reloj solo al dibujar, solo se repinta ese nodo, igual que la línea activa del barrido. Las opacidades, la escala y el desenfoque por distancia que ya pone la capa de cada elemento (`alphaFor`/`scaleFor`/`blurFor`) se aplican igual.
- **Arrastre manual.** Con el modo de usuario conviene dejar los puntos quietos (escala 1, opacidad 0,5) o esconderlos; AMLL los mantiene mientras haya pausa. **[estimado]**
- **Tocar el `Gap`.** Que salte a `start` o no haga nada; Cider no le pone marca de tiempo.

---

## 3. Efecto 2: palabras que se alzan

### 3.1 Valores

| | Valor | Fuente |
|---|---|---|
| Unidad | **Cada sílaba** (cada `<span>` con tiempo), no la palabra entera | **[coinciden]** (AMLL: un `createFloatAnimation` por `LyricWord`; Cider: un `lift` por elemento `clp-word`, que es la sílaba) |
| Altura | **0,05 em** (5 % del tamaño de letra). Coros `x-bg`: 0,10 em. A 34 sp son **1,7 dp** (≈ 4–5 px en el POCO); en tablet (38 sp), 1,9 dp. | **[coinciden]** |
| Inicio | En el `begin` de la sílaba (cuando empieza su barrido) | **[coinciden]** |
| Duración | **max(1000 ms, duración de la sílaba)** | **[coinciden]** |
| Curva | `ease-out` de CSS = `cubic-bezier(0, 0, 0.58, 1)`. Con 1 s, ~60 % de la subida ocurre en los primeros 300 ms. | **[coinciden]** |
| Se queda arriba | Sí, mientras la línea está activa | **[coinciden]** |
| Vuelve a bajar | **AMLL:** al desactivarse la línea, la misma animación se reproduce al revés (≈ 1 s, la curva queda como un *ease-in*). **Cider:** las líneas ya cantadas se quedan arriba (`.clp-cold-sung` con `translateY(-0.05em)`) mientras se desvanecen. | **[código]**, difieren |
| Accesibilidad | Ambos lo quitan con "reducir movimiento" | **[código]** |

Las sílabas largas (> 1 s) suben durante toda la sílaba; las cortas, en 1 s, de modo que una palabra corta sigue subiendo después de cantada. **[código]**

Con 1,7 dp es un efecto muy sutil, y no consta que Apple suba más. **Hay que medirlo (sección 6).** **[sin verificar en Apple]**

### 3.2 Encaje en `LyricsView.kt`

Ahora `LyricText` dibuja la línea activa en dos pasadas: el texto al 45 % y, en una capa, el blanco recortado con `DstIn`/`DstOut`. Para el alzado:

- En cada pasada, en lugar de un `drawText(l, …)`, se dibuja **por tramos de sílabas**: para cada tramo, `clipRect(x0, filaTop − margen, x1, filaBottom)` + `translate(0f, −alzado(t))` + `drawText(l, …)`. `x0`/`x1` salen de `getBoundingBox` del primer y último carácter (ya se calculan en `ranges`). `alzado_k(t) = 0.05 × fontSizePx × easeOut(clamp((t − start_k) / max(1, dur_k)))`, leído al dibujar (el `clock` que ya se lee ahí).
- **Agrupar tramos:** las sílabas ya subidas del todo (alzado = máximo) y las que no han empezado (alzado 0) forman bloques contiguos con el mismo desplazamiento. En cada fotograma solo hay 1–2 sílabas "en vuelo", así que bastan unas **3–4 llamadas a `drawText` por fila y pasada**, no una por sílaba.
- **Rectángulos de la máscara del barrido:** el degradado es horizontal y ocupa toda la fila, así que no le afecta el desplazamiento vertical. Basta con ampliar cada rectángulo hacia arriba unos 0,1 em para que no corte las sílabas alzadas.
- **Recorte entre sílabas:** con Inter Bold casi ningún glifo se sale de su caja de avance. Si alguno se sale ('f', 'j'), puede verse un corte de 1–2 px entre dos sílabas a distinta altura durante unos fotogramas. Es aceptable. **[estimado]**
- **Líneas no activas:** si se sigue el comportamiento de Cider (la línea cantada se queda arriba), la línea anterior se dibuja entera con `translate(0, −0.05 em)` mientras se desvanece. El de AMLL (bajar en ~1 s) exige seguir dibujando por sílaba la línea anterior. Como la nuestra se funde en el muelle del foco, recomiendo el de Cider. **[estimado]**
- Sin recomponer: todo ocurre en `drawWithContent`, igual que el barrido actual.

---

## 4. Efecto 3: brillo en notas sostenidas

### 4.1 Cuándo se aplica

| | AMLL actual | Cider 4.0.9.1 |
|---|---|---|
| Unidad que se evalúa | **La palabra** (sílabas agrupadas hasta el espacio) **o** cualquiera de sus sílabas | **La sílaba** sola |
| Umbral de duración | **≥ 1000 ms** (de la palabra entera o de una de sus sílabas) | **≥ 1000 ms** (`YE = 1` s) |
| Longitud | Fuera de CJK: **de 2 a 7 caracteres** (sin espacios). CJK: cualquiera. | Sin límite |
| Sin tiempos por sílaba | No | No |
| Reducir movimiento | — | Desactivado |

**[código]** Las dos coinciden en **1 s**. Me quedo con el criterio de AMLL (palabra de 2–7 letras), que evita que brillen palabras largas como "automatic" (9 letras, 1,15 s en "Sexy Magic"). **[estimado]**

**Última palabra de la línea:** si la palabra contiene la última sílaba de la línea (AMLL compara con `includes` de texto, un atajo; mejor "el grupo que contiene la última sílaba"), el efecto se refuerza: intensidad × 1,6, brillo × 1,5 y duración × 1,2. **[coinciden]**

### 4.2 Intensidad según la duración

Con `du = max(1000, duración de la palabra en ms)`. **[coinciden]**

```
amount = du/2000 → (>1 ? sqrt : ^3) → × 0.6     (× 1.6 si es la última; tope 1.2)
glow   = du/3000 → (>1 ? sqrt : ^3) → × 0.5     (× 1.5 si es la última; tope 0.8)
radio  = min(0.3, glow × 0.3) em                (desenfoque de la sombra de texto)
```

Valores en el pico (a mitad de la animación de cada letra):

| Duración | Última | Escala de la letra | Subida extra | Opacidad del brillo | Radio del brillo | Separación por letra |
|---|---|---|---|---|---|---|
| 1,0 s | no | 1,008 | 0,002 em | 0,02 | 0,006 em | 0,002 em |
| 1,5 s | no | 1,025 | 0,006 em | 0,06 | 0,019 em | 0,008 em |
| 2,0 s | no | 1,060 | 0,015 em | 0,15 | 0,044 em | 0,018 em |
| 2,0 s | sí | 1,096 | 0,024 em | 0,22 | 0,067 em | 0,029 em |
| 3,0 s | no | 1,073 | 0,018 em | 0,50 | 0,150 em | 0,022 em |
| 3,0 s | sí | 1,118 | 0,029 em | 0,75 | 0,225 em | 0,035 em |
| 4,0 s | no | 1,085 | 0,021 em | 0,58 | 0,173 em | 0,026 em |
| ≥ 6 s | — | → 1,12 | → 0,03 em | → 0,8 | → 0,24 em | → 0,036 em |

Por debajo de ~1,5 s el efecto casi no se ve; se nota de verdad a partir de ~2 s. Es la curva cúbica a propósito. **[código]**

### 4.3 Animación letra a letra (la ola)

**[coinciden]**: AMLL `dom/animation/emphasize/index.ts` y Cider `fxSpecs`/`buildFx`. Cada letra (grafema) de la palabra tiene dos animaciones:

1. **Brillo y escala.**
   - Empieza en `inicio de la palabra + (du / 2.5 / n) × i`, con *i* = índice de la letra y *n* = número de letras: la ola recorre la palabra en el **40 % de su duración**. Dura **du** (× 1,2 si es la última).
   - Curva de intensidad `e(x)`: sube en la primera mitad con `cubic-bezier(0.2, 0.4, 0.58, 1)`, **pico a la mitad** y vuelve a 0 en la segunda mitad con `1 − cubic-bezier(0.3, 0, 0.58, 1)`. AMLL la muestrea en 32 fotogramas clave.
   - En cada instante:
     - escala `1 + 0.1·amount·e` respecto al **centro de la letra**;
     - desplazamiento `x = −0.03·amount·e·(n/2 − i)` em: las letras se separan del centro de la palabra, las de la izquierda hacia la izquierda y las de la derecha hacia la derecha;
     - `y = −0.025·amount·e` em (hacia arriba);
     - sombra de texto `0 0 radio rgba(255,255,255, glow·e)`.
2. **Flotación extra.** `translateY(−0.05·sin(πx)) em` (el doble en coros). Empieza **400 ms antes** que el brillo de esa letra, dura **1,4 × du** y se **suma** al alzado de la sílaba (sección 3). Es una joroba: sube y vuelve.

Entrada y salida: no hay más que esto. Todo vale 0 al principio y al final de su animación, así que la palabra acaba idéntica a una normal; solo le queda el alzado de 0,05 em de la sílaba. El brillo va por debajo de la máscara del barrido (en AMLL la sombra está en la letra, dentro del elemento enmascarado), así que solo se ve en la parte ya cantada. **[código]**

### 4.4 Encaje en `LyricsView.kt`

- **Precálculo** (`remember(line, style)`): las palabras que brillan, sus letras con el índice en el texto y, para cada letra, un `TextLayoutResult` propio medido con `rememberTextMeasurer()` y el mismo estilo. Su posición sale de `getBoundingBox(offset)` en el diseño de la línea, alineando la línea base. Son pocas palabras por canción. Partir en letras pierde el *kerning* entre ellas, igual que en AMLL y Cider, que usan un `<span>` por letra.
- **Dibujo** (solo mientras alguna letra está animándose, es decir, `t` entre `inicio − 400 ms` y `inicio + du·1,4`):
  1. En las dos pasadas (atenuada y blanca), dibujar la línea **sin** esas palabras: `clipRect(…, ClipOp.Difference)` con su rectángulo, combinado con los tramos del alzado.
  2. Para cada letra: `withTransform({ translate(dx, dy − alzado − flotación); scale(s, pivot = centro de la letra) }) { drawText(letra, color, shadow = Shadow(Color.White.copy(alpha = glow·e), Offset.Zero, blurRadius = r)) }`. `DrawScope.drawText(TextLayoutResult, …, shadow = …)` acepta la sombra **al dibujar**, sin tocar el `TextStyle`, así que no recompone ni vuelve a medir.
  3. La máscara del barrido (`DstIn`/`DstOut`) se aplica igual. Hay que ampliar los rectángulos verticalmente al menos el radio del brillo (hasta 0,24 em) para que no lo corten en recto.
- **Conversión del radio:** en CSS, el desenfoque de `text-shadow` es `r` con σ = r/2. En Android, `setShadowLayer(radius)`, que es lo que usa `Shadow.blurRadius`, convierte σ ≈ 0,577·radius + 0,5. Para igualarlos: `blurRadius ≈ max(0, (r/2 − 0.5 px) / 0.577)`. **[estimado]** La conversión de Skia es conocida; conviene comprobarla a ojo.
- **Coste:** sombras desenfocadas de unos pocos glifos durante 1–4 s y en una sola línea. Debería ser barato, pero **medirlo** con `bench` + `gfxinfo` como el resto (CLAUDE.md, "Rendimiento"). En modo rendimiento (`Appearance.lite`) se puede quitar la sombra y dejar solo la escala, como ya se hace con el desenfoque de las líneas.

---

## 5. Reglas comunes de rendimiento en Compose

- Todo lo que cambia en cada fotograma (puntos, alzado, ola, brillo) **se lee al dibujar** desde `clock` (`drawWithContent`/`drawBehind`). Así solo se repinta el nodo que lo lee, como el barrido de hoy.
- En la colocación del `Layout` solo se leen estados que cambian durante una transición: `focus`, `manual` y el nuevo `gapReveal`. **Nunca `clock`.**
- La composición solo cambia al pasar de elemento (`active` sigue siendo un `derivedStateOf`).
- Los cálculos por línea (rangos de sílabas, palabras que brillan, medidas de letras sueltas, huecos) van en `remember`.
- Solo la línea activa (y la pausa activa) hace trabajo por fotograma. Las demás siguen con un `drawText` normal y su capa.

---

## 6. Qué falta confirmar en Apple Music y cómo

Ningún número de este documento se ha comprobado en Apple Music. El proyecto ya sabe medir así (portadas animadas, hoja de reproducción). Propuesta **[estimado]**:

1. Grabar Apple Music (para Android, que ya sirve de referencia en el proyecto, o un iPhone) a 60 fps con canciones que tengan:
   - una pausa larga conocida: "Sexy Magic" tiene huecos de 11,1 s (24,56 → 35,62) y 6,3 s (174,78 → 181,11), útiles para saber si el umbral es 5 o 7 s;
   - notas sostenidas de 2–4 s.
2. **Puntos:** en qué momento aparecen respecto al final de la línea anterior (¿espera de 500 ms?), cuándo desaparecen respecto a la siguiente, el diámetro en px frente a la altura de mayúsculas del texto, la escala del grupo fotograma a fotograma (periodo y amplitud: ¿±5 % o 1 → 1,25?) y la opacidad de cada punto.
3. **Alzado:** desplazamiento vertical de una sílaba entre antes y después de cantarse (se ve comparando la línea base de sílabas vecinas). Con 0,05 em a 34 sp salen ~4–5 px en el POCO.
4. **Brillo:** escala de las letras en el pico, extensión del halo y desfase entre la primera y la última letra.
5. Para las capturas de iPhone, convertir antes de Display P3 a sRGB (lección de CLAUDE.md, 2026-11-18).

---

## 7. Licencias de las fuentes consultadas

- **AMLL** (`Steve-xmh/applemusic-like-lyrics` → `amll-dev/applemusic-like-lyrics`): **GNU AGPL-3.0** (`LICENSE` del repositorio; `packages/core/package.json`: `"AGPL-3.0-only"`). Copiar o traducir su código obligaría a publicar nuestra app bajo AGPL, incompatible con la MIT de Remote for Cider. Aquí solo se describen el comportamiento y los parámetros numéricos (hechos, no expresión). **No portar funciones línea a línea**: escribir la implementación propia a partir de este documento. Usa `bezier-easing` (MIT) para las curvas; en Compose, `CubicBezierEasing` hace lo mismo.
- **Cider** 4.0.9.1: **propietario**, "All Rights Reserved" (`/usr/lib/cider/resources/LICENSE`, Cider Collective, LLC). Se leyó el frontend instalado para estudiar el comportamiento. No copiar código ni recursos, solo los valores. Su reproductor de letras comparte estructura y constantes con AMLL (que es AGPL) y no lo atribuye en `THIRD_PARTY.txt`. Lo apunto solo para explicar por qué las dos fuentes no son independientes.
- **Apple Music**: el TTML de ejemplo se obtuvo por la API de Cider (`/api/v2/amapi/raw/…/syllable-lyrics`), como hace la app. No se reproduce aquí más que un fragmento mínimo de su estructura.
- **Artículos citados:** [9to5Mac, novedades de Apple Music en iOS 26](https://9to5mac.com/2025/09/24/heres-everything-new-for-apple-music-in-ios-26/), [AppleInsider, letras traducidas en iOS 26](https://appleinsider.com/inside/ios-26/tips/how-to-view-translated-lyrics-in-apple-music), [Apple Community, "Why do some lyrics glow in Apple Music?"](https://discussions-jp-prz.apple.com/thread/254995570). Solo se usan como referencia; no se copia contenido.

## Apéndice: dónde mirar el código original (para volver a comprobarlo)

- Cider: `/usr/lib/cider/resources/spa/assets/smart-playlist-store-UtwKIkTc.js`, clase de línea con `buildDots`/`updateDots`/`fxSpecs`/`buildFx`/`buildMasks` (constantes `JE`, `YE = 1`, `gD = 1.5`, `_D = 10`). CSS en `smart-playlist-store-DYXanacL.css` (`.clp-dots`, `.clp-dot`, `.clp-interlude`, `.clp-cold-sung`). Detección de huecos (`empty: true`, umbral 5 s) en `LyricsComposable-Cqj5hsu-.js`. Los nombres con hash cambian con cada versión de Cider: buscar `clp-dot` y `isInterlude`.
- AMLL: `packages/core/src/lyric-player/base/interlude-dots.ts` (puntos), `base/timeline.ts` (`MIN_INTERLUDE_GAP`), `base/index.ts` (`calcLayout`: `dotMargin = fontSize × 0.4`), `base/line.ts` (`shouldEmphasize`), `dom/lyric-line.ts` (agrupación por palabra, última palabra, alzado al revés al desactivar), `dom/animation/float/` (alzado), `dom/animation/emphasize/` (brillo), `styles/lyric-player.module.css` (`.interludeDots`).
