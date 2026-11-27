# Botones de vidrio de la barra superior en iOS 26/27: cómo aparecen y desaparecen

Investigación del 2026-11-26 para copiar en Android (Remote for Cider) cómo anima iOS los botones circulares de vidrio de la barra de navegación (atrás "‹", "…", acciones de `toolbar`). No se ha grabado ningún iPhone ni simulador. Todo sale de documentación de Apple, sesiones de la WWDC y análisis de terceros.

Cada dato lleva su grado de certeza:
- **[Apple]**: documentación, HIG o transcripción de una sesión de la WWDC.
- **[Medido]**: un tercero lo midió contra el sistema real (grabación o comparación lado a lado).
- **[Tercero]**: lo afirma un tercero sin decir que lo midiera, o es un valor que eligió él al recrearlo.
- **[Deducido]**: lo deduzco yo de lo anterior.

## Resumen

1. **Los botones de la barra no "se funden".** Apple lo dice explícitamente: el vidrio líquido *"en vez de fundirse, se materializa y desmaterializa modulando poco a poco la refracción y el efecto lente"* [Apple, Meet Liquid Glass]. En UIKit, para quitar o poner vidrio hay que animar `effect` (a `nil` o a un `UIGlassEffect`) y **no** `alpha`, porque `alpha` no da la animación correcta [Apple, sesión 284]. En SwiftUI, `.materialize` *"funde el contenido y anima la entrada o salida del material"*, y *"el sistema aplica más que cambios de opacidad"* [Apple, docs].
2. **Los botones pertenecen a la barra, no a la página.** Con la página que entra o sale no se deslizan. Si los dos niveles tienen el mismo botón, *"queda quieto durante la transición"* [Apple, `setRightBarButtonItems`]. Si cambian, UIKit *"anima automáticamente la transición entre los dos conjuntos"* y empareja botones por posición y contenido, o por `identifier` (iOS 26) [Apple]. MacStories lo resume así: los controles *"no aparecen de la nada"*, *"casi siempre están en el mismo sitio y cambian de forma"* según la pantalla. En Apple Music, *"la foto de perfil se convierte en una barra de herramientas al abrir una playlist o un álbum"* [Tercero, MacStories].
3. **Materializar, según una medición a 120 fps de la barra de navegación nativa** (librería Flutter `liquid_glass_widgets`, MIT):
   - **Entrada (~250 ms):** el vidrio aparece a ritmo casi constante y se encoge desde ~1,15× hasta su tamaño. El icono empieza tarde (35 % del tramo), borroso (σ ≈ 8 px), y es lo último en enfocarse.
   - **Salida (~350 ms, más larga):** el icono se emborrona y desaparece primero (~55 % del tramo). El vidrio vacío se disuelve después.
   - En la barra, la escala converge hacia el borde al que está anclado el botón [Medido/Tercero].
4. **Al navegar, la entrada empieza hacia la mitad del deslizamiento.** Un informe de Titanium SDK midió en iOS 27.1/27.2 (simulador, 60 fps) un deslizamiento de ~400 ms y botones que aparecen ~170 ms después de empezar [Medido]. La librería Flutter reparte la entrada en el 45–100 % del progreso y la salida en el 0–55 % [Tercero].
5. **Fusión y separación:** solo dentro de un mismo contenedor (`GlassEffectContainer` / `UIGlassContainerEffect`). Cuando dos formas quedan a menos de `spacing`, se unen *"como gotas de agua"*. Lo que aparece dentro de ese radio sale de la geometría de la forma vecina (`matchedGeometry`, por defecto). Lo que aparece más lejos se materializa por su cuenta (`materialize`) [Apple].
6. **Gesto interactivo de volver: sin confirmar.** Ninguna fuente fiable dice si los botones siguen al dedo. La librería Flutter los deja quietos hasta que el gesto se confirma y entonces hace un fundido corto. Afirma que *"es lo que hace iOS 26 tras deslizar"*, pero no dice haberlo medido.
7. **Muelles:** Apple no publica los de la barra. Sí publica los valores por defecto de las API que usa en sus ejemplos:
   - `withAnimation` por defecto: `spring(response 0,55, damping 1,0)`.
   - `UIView.animate { }`: `springDuration 0,5, bounce 0`.
   - Medidos por terceros en iOS 27, para un botón que se abre en menú: apertura ω₀ = 17 rad/s, ζ ≈ 0,76; cierre ω₀ = 18 rad/s, ζ ≈ 0,86.

## Lo que hay hoy en la app

- `GlassCircleButton` (`ui/components/Glass.kt`) es un círculo de 40 dp con `shadow(8.dp)` y vidrio propio (`glass(backdrop, GlassStyle.button())`: refracción −1,6, bisel 8 dp, desenfoque 12 dp). No tiene ningún parámetro de aparición.
- En `MainScreen.kt`, el botón de volver, el ⋮ de álbum/playlist y el ⋮ de las raíces (`ConnectionMenuButton`, que también es un `GlassCircleButton` con `MoreVert`) llevan `graphicsLayer { alpha = shownFor{…}() ; ModulateAlpha }` y `skipWhenClear`.
  - `shownFor` es `lerp(debajo, encima, cover())`: un fundido lineal de opacidad con el progreso de la página, que también sigue al dedo en el atrás predictivo.
  - En el zoom no se cruzan: el de abajo se va en la primera mitad y el de arriba llega en la segunda.
- Consecuencias:
  - Entre dos páginas anidadas el botón de volver ya queda quieto (vale 1 en las dos), como en iOS.
  - Raíz → álbum: el ⋮ de la raíz (a `top + 16 dp`) se funde mientras el del álbum (a `top + 13 dp`) aparece en el mismo rincón. A mitad hay dos vidrios al 50 % uno encima del otro, cuando en iOS sería el mismo botón quieto o transformándose.

## Tablas por caso

### 1. Aparecer al empujar una pantalla (push): botón que no existía en la de abajo

Ejemplo: raíz → álbum, aparece "‹".

| Propiedad | Qué hace iOS | Fuente |
|---|---|---|
| Opacidad del vidrio | Sube desde 0 a ritmo casi constante (lineal); llega al 100 % al 92 % de su tramo | [Medido] Flutter, `GlassMaterializeChoreography.entranceGlass` |
| Refracción / lente | Se "materializa" modulando poco a poco la refracción y la lente, no solo la opacidad | [Apple] Meet Liquid Glass; docs de `materialize` |
| Escala | De ~1,15× a 1× (en la barra la librería usa 1,1×), sin curva propia: sigue a la opacidad del vidrio | [Medido] ~1,15 "de la captura nativa"; 1,1 en barra [Tercero] |
| Origen de la escala | Hacia el borde al que está anclado el grupo (izquierda para "‹", derecha para las acciones), no desde el centro | [Tercero] Flutter (`centerLeft`/`centerRight`); dice que en el borde "una escala grande parece un deslizamiento" |
| Desplazamiento | Ninguno propio: no se desliza con la página. Los botones están fijos en la barra | [Apple] `setRightBarButtonItems`, `UIBarButtonItem.identifier`; [Tercero] MacStories, Flutter |
| Icono | Va aparte del vidrio: empieza al 35 % del tramo, borroso (σ ≈ 8 px) y con opacidad que sube; se enfoca al final | [Medido] Flutter (`entranceContent` 0,35–1,0; `contentSigma` 8) |
| Sombra | Sin datos propios; [Deducido] acompaña al vidrio | — |
| Recorte / gota | No sale de otro botón salvo que haya un vecino del mismo contenedor a menos de `spacing` (ver caso 5) | [Apple] docs de `matchedGeometry` |
| Momento | Hacia la mitad del deslizamiento: ~170 ms de ~400 ms. La librería Flutter usa el 45–100 % del progreso | [Medido] Titanium #14649 (iOS 27.1/27.2, 60 fps); [Tercero] Flutter `materializeStart = 0,45` |
| Duración aislada | ~250 ms (aparición fuera de una navegación) | [Medido] Flutter `materializeDuration` |

### 2. Desaparecer al quitar una pantalla (pop): botón que no existe en la de abajo

Ejemplo: álbum → raíz, se va "‹".

| Propiedad | Qué hace iOS | Fuente |
|---|---|---|
| Orden | Al revés que la entrada: primero se va el icono, después el vidrio | [Medido] Flutter |
| Icono | Se emborrona desde el primer fotograma y desaparece hacia el 55 % del tramo; mientras tanto se ve un vidrio vacío | [Medido] Flutter (`exitContent`: "el armazón visiblemente vacío que enseña la barra nativa") |
| Vidrio | Se disuelve de forma uniforme en todo el tramo | [Medido] Flutter (`exitGlass` 0–1) |
| Escala | Crece de 1× hacia ~1,1–1,15× mientras se disuelve | [Medido/Tercero] Flutter |
| Duración aislada | ~350 ms, "notablemente más larga que la entrada" | [Medido] Flutter `dematerializeDuration` |
| Momento al navegar | En la primera mitad: la librería usa el 0–55 % del progreso del pop | [Tercero] Flutter `dematerializeEnd = 0,55` |
| Push y pop | Ventanas simétricas en el progreso (mismo recorrido al revés); la asimetría está dentro de las curvas de entrada y salida | [Tercero] Flutter |

### 3. Gesto interactivo de volver (desde el borde o desde cualquier punto en iOS 26)

| Propiedad | Qué hace iOS | Fuente |
|---|---|---|
| Página y título | Siguen al dedo | [Apple] la transición de navegación es interactiva e interrumpible (sesión 284) |
| Botones que cambian | **Sin confirmar.** La librería Flutter los deja quietos mientras el dedo arrastra; si el gesto se confirma, hace la transición entera en lo que queda (o un fundido corto). Si se cancela, no hay nada que deshacer | [Tercero] Flutter; su enum `GlassSwipeCommitTransition.crossFade` dice "lo que hace iOS 26 tras deslizar", sin decir que lo midiera |
| Botones iguales en los dos niveles | Quietos | [Apple] `setRightBarButtonItems` |
| Interrupción | Se puede volver a deslizar o tocar atrás varias veces durante la transición | [Apple] sesión 284 |
| Antes de iOS 26 | [Deducido, memoria de UIKit clásico] los elementos de la barra se fundían siguiendo al dedo. Ninguna fuente lo confirma para iOS 26 | — |

### 4. Cambio de elementos en la misma pantalla (sin navegar)

Ejemplo: aparece o se va una acción con `withAnimation` / `UIView.animate`.

| Propiedad | Qué hace iOS | Fuente |
|---|---|---|
| Aparecer lejos de otro vidrio | `materialize`: como en el caso 1, pero con su propio reloj (~250 ms al entrar, ~350 ms al salir) | [Apple] docs; [Medido] Flutter |
| Aparecer cerca de otro vidrio del mismo contenedor | `matchedGeometry` (por defecto): la forma nueva sale de la geometría de la vecina | [Apple] docs de `matchedGeometry` y "Applying Liquid Glass to custom views" |
| Mismo botón con otro contenido | Con la animación por defecto, `matchedGeometry` añade al contenido efectos de escala y desplazamiento; se quitan pasando un muelle explícito | [Apple] docs de `matchedGeometry` |
| Muelle | El que pase la app. Los ejemplos de Apple usan `withAnimation` sin argumentos = `spring(response 0,55, dampingFraction 1,0)`. En UIKit, `UIView.animate { }` = `springDuration 0,5, bounce 0` | [Apple] `Animation.default`, `UIView.animate(springDuration:…)` |
| Mismo botón con otro icono (navegando) | El armazón se queda y solo cambia el contenido: el icono saliente se emborrona (σ hasta ~6 px) y se funde entre el 25 % y el 60 % del recorrido; el entrante llega algo borroso (σ ≈ 3,5 px) y se enfoca entre el 48 % y el 90 % | [Tercero] Flutter (`morphBlurSigma`, `crossFade*`, `glyphSharpen*`); en los comentarios: "el morph nativo nunca enseña un icono que solo se funde" |
| Grupo que cambia de ancho | El armazón se hincha entero (alto, radio e iconos) mientras viaja al ancho nuevo y luego se relaja, con un rebote que acaba cuando aterriza la página | [Tercero] Flutter; comportamiento "observado en nativo", números elegidos por ellos (hinchazón máx. 22 %, muelle "bouncy" 0,5 s con rebote 0,4) |
| Barra que se esconde al desplazar (iOS 27) | `toolbarMinimizeBehavior(.onScrollDown, for: .navigationBar)` / `navigationBarMinimization`: la barra "se aparta" de forma interactiva al desplazar | [Apple] WWDC26 269 y 278 (sin detalles de la animación) |
| Movimiento reducido | Baja la intensidad y quita la elasticidad del material | [Apple] Meet Liquid Glass |

### 5. Fusión y separación (morphing entre cápsulas)

| Propiedad | Qué hace iOS | Fuente |
|---|---|---|
| Requisito | Las piezas tienen que estar en el mismo `GlassEffectContainer` / `UIGlassContainerEffect`: *"el vidrio no puede muestrear otro vidrio"* | [Apple] sesión 323, docs |
| `spacing` | Distancia a la que dos formas empiezan a mezclarse. Cuanto mayor, antes se unen. Si es mayor que la separación del `HStack`, ya están unidas en reposo | [Apple] docs de `GlassEffectContainer` y `UIGlassContainerEffect.spacing` |
| Unirse | Al animar dos vistas hacia el mismo marco, se combinan en una sola forma; al acercarse, *"se juntan como gotas de agua"* | [Apple] sesión 284 |
| Separarse | Se colocan sin animación en la misma posición y luego se animan a sus marcos: salen de una sola gota | [Apple] sesión 284 (código) |
| Unidas siempre | `glassEffectUnion(id:namespace:)` fusiona en una forma todas las que tengan el mismo id, forma y variante | [Apple] docs |
| Transiciones | `matchedGeometry` (por defecto, dentro de `spacing`), `materialize` (más lejos; funde el contenido y anima el material sin buscar geometría) e `identity` (sin cambios) | [Apple] docs de `GlassEffectTransition` |
| Ids | `glassEffectID(_:in:)` con un `@Namespace`: SwiftUI usa el `spacing` y la geometría para decidir qué forma sale de cuál | [Apple] docs |
| Barras (UIKit) | Los botones de imagen comparten fondo; los de texto, Done/Close y los `prominent` van aparte. `fixedSpace` / `ToolbarSpacer(.fixed)` separan grupos. `sharesBackground = false` / `hidesSharedBackground = true` / `.sharedBackgroundVisibility(.hidden)` sacan un botón a su propio grupo (o sin vidrio) | [Apple] sesión 284, docs |
| Grupo nuevo junto a otro al navegar (iOS 27) | Sale de su vecino unido por un "puente líquido" hasta que se separan; el que se quita vuelve a fundirse en él | [Tercero] Flutter ("como iOS 27 hace crecer un armazón de otro"; mezcla de 20 px que sube y baja con sin(π·t)) |
| Botón → menú | El botón se convierte en el menú y vuelve a ser botón al cerrarlo. El material se vuelve "más grueso": más sombra y más refracción | [Apple] Meet Liquid Glass; [Tercero] MacStories |
| Muelles del menú (iOS 27) | Apertura ω₀ = 17 rad/s, ζ ≈ 0,76; cierre ω₀ = 18 rad/s, ζ ≈ 0,86; retroceso del botón ~183 ms | [Medido] Flutter, CHANGELOG PR #494 (capturas a 60 fps de iOS 27) |

## Tiempos y muelles en un solo sitio

| Qué | Valor | Equivalente en Compose (`spring(dampingRatio, stiffness)`) | Fuente |
|---|---|---|---|
| `withAnimation` por defecto (iOS 17+) | response 0,55 s, damping 1,0 | `spring(1f, 130f)` (ω = 2π/0,55) | [Apple] |
| `UIView.animate { }` (iOS 17+) | springDuration 0,5 s, bounce 0 | `spring(1f, 158f)` | [Apple] firma del método |
| `Animation.smooth` | duration 0,5 s, extraBounce 0 | `spring(1f, 158f)` | [Apple] |
| Materializar (aislado) | 250 ms casi lineal | `tween(250, LinearEasing)` con subtramos | [Medido] Flutter |
| Desmaterializar (aislado) | 350 ms | `tween(350, LinearEasing)` con subtramos | [Medido] Flutter |
| Deslizamiento de push nativo | ~400 ms; botones a ~170 ms | — | [Medido] Titanium #14649 |
| Menú desde botón (abrir / cerrar) | ω₀ 17 / 18 rad/s; ζ 0,76 / 0,86 | `spring(0.76f, 289f)` / `spring(0.86f, 324f)` | [Medido] Flutter |
| Push de la app hoy | `PageStack.Spring` = `spring(1f, 220f)` ≈ 0,45 s | — | CLAUDE.md |

## Contradicciones y huecos

- **¿Se deslizan o no los botones con la página?** Apple y la librería Flutter dicen que los que coinciden quedan quietos o se transforman en su sitio. El informe de Titanium dice que en una app UIKit nativa los botones *"se deslizan y aparecen con la ventana"*. Puede que ese informe se refiera al título, que sí se desliza; no lo aclara.
- **Interactivo:** solo hay una fuente (la librería Flutter), y no dice haberlo medido.
- **Escala al materializar:** la librería dice "~1,15 de la captura nativa" para el efecto aislado y usa 1,1 en la barra por gusto propio. Ninguna fuente de Apple da la escala.
- **Hinchazón del morph (22 %) y muelle con rebote:** son números elegidos por la librería, no medidos. Que el armazón se hinche entero sí lo presenta como observado.
- **Errores de iOS 26 que pueden confundir al grabar:** título y botón de volver que se van a la izquierda y vuelven de golpe (foro 804550, mejorado en 26.1 beta 4); fondo blanco o negro que parpadea en los botones durante el push o pop (797116, 819974); `.glassProminent` que parpadea y desaparece (815095); "un rectángulo raro que se convierte en el botón circular de volver" al hacer push (Fatbobman). Son fallos, no el diseño buscado.
- **Zoom (como la apertura de álbumes de la app):** no encontré qué hacen los botones de la barra en una transición `.zoom` de navegación.

## Qué no se puede confirmar sin grabar

Esto lo podría grabar la sesión de iOS en el simulador de iOS 27 en GitHub Actions. El simulador de CI dibuja por software: los tiempos valen, pero hay que fijarse en la marca de tiempo de cada fotograma y no en la fluidez.

1. **Push raíz → detalle con `NavigationStack`** (SwiftUI) y con `UINavigationController` (UIKit). Raíz con un `ToolbarItem` "ellipsis" arriba a la derecha y sin botón de volver; detalle con "‹" y el mismo "ellipsis". Medir fotograma a fotograma:
   - cuándo empieza y acaba la aparición de "‹" respecto al deslizamiento;
   - su escala, su opacidad y hacia qué borde converge;
   - si el icono va aparte (desenfoque);
   - si el "ellipsis" se queda totalmente quieto.
2. **Igual, pero con iconos distintos** a la derecha (por ejemplo "person.circle" en la raíz y "plus" + "ellipsis" en el detalle, como Apple Music). Mirar el morph: hinchazón, desenfoque del icono y puente al separarse.
3. **Gesto de volver:** `XCUICoordinate.press(forDuration:thenDragTo:withVelocity:thenHoldForDuration:)` desde el borde izquierdo, con paradas al 25 %, 50 % y 75 % del ancho. Responde a dos preguntas: ¿el "‹" se disuelve siguiendo al dedo o espera a soltar? Y al cancelar, ¿vuelve?
4. **Pop con el botón** (no gesto), para comparar con el 1 al revés (orden icono → vidrio y duración).
5. **Misma pantalla:** un `ToolbarItem` que aparece o desaparece con `withAnimation` (con y sin un vecino en el mismo grupo).
6. **`GlassEffectContainer` de prueba** con dos círculos, `glassEffectID` y `spacing` 40. Primero un círculo que aparece con `.materialize` (escala, opacidad y desenfoque del icono por fotograma) y después con `.matchedGeometry`.
7. Si da tiempo, **una transición `.zoom` de navegación** con botones en la barra del destino.
8. Apple Music no existe en el simulador. Para las pantallas reales de Apple Music hay que esperar al iPhone 18 Pro Max del usuario. Ahí también se medirá el rendimiento.

## Fuentes

**Apple: sesiones**
- WWDC25 219, *Meet Liquid Glass*: <https://developer.apple.com/videos/play/wwdc2025/219/>. Materializar en vez de fundir, morph entre estados, botón → menú, movimiento reducido.
- WWDC25 356, *Get to know the new design system*: <https://developer.apple.com/videos/play/wwdc2025/356/>. Agrupación de botones de barra.
- WWDC25 323, *Build a SwiftUI app with the new design*: <https://developer.apple.com/videos/play/wwdc2025/323/>. `ToolbarSpacer`, `sharedBackgroundVisibility`, `GlassEffectContainer`, `glassEffectID`.
- WWDC25 284, *Build a UIKit app with the new design*: <https://developer.apple.com/videos/play/wwdc2025/284/>. Grupos de botones, `hidesSharedBackground`, materializar con `effect` (no `alpha`), `UIGlassContainerEffect.spacing`, unir y separar, transición interrumpible.
- WWDC25 256, *What's new in SwiftUI*: <https://developer.apple.com/videos/play/wwdc2025/256/>. *"Durante las transiciones de navegación, estos elementos incluso pueden transformarse."*
- WWDC26 269, *What's new in SwiftUI*: <https://developer.apple.com/videos/play/wwdc2026/269/>. `visibilityPriority`, `ToolbarOverflowMenu`, `toolbarMinimizeBehavior(.onScrollDown, for: .navigationBar)`.
- WWDC26 278, *Modernize your UIKit app*: <https://developer.apple.com/videos/play/wwdc2026/278/>. Barra de navegación que se minimiza al desplazar.

**Apple: documentación** (leída por la API JSON de developer.apple.com)
- Applying Liquid Glass to custom views: <https://developer.apple.com/documentation/swiftui/applying-liquid-glass-to-custom-views>
- `GlassEffectContainer`: <https://developer.apple.com/documentation/swiftui/glasseffectcontainer>
- `GlassEffectTransition` (`matchedGeometry`, `materialize`, `identity`): <https://developer.apple.com/documentation/swiftui/glasseffecttransition>
- `glassEffectTransition(_:)`: <https://developer.apple.com/documentation/swiftui/view/glasseffecttransition(_:)>
- `glassEffectUnion(id:namespace:)`: <https://developer.apple.com/documentation/swiftui/view/glasseffectunion(id:namespace:)>
- `UIGlassEffect`: <https://developer.apple.com/documentation/uikit/uiglasseffect>; `UIGlassContainerEffect`: <https://developer.apple.com/documentation/uikit/uiglasscontainereffect>
- `UIBarButtonItem.identifier`: <https://developer.apple.com/documentation/uikit/uibarbuttonitem/identifier>; `sharesBackground`: <https://developer.apple.com/documentation/uikit/uibarbuttonitem/sharesbackground>; `hidesSharedBackground`: <https://developer.apple.com/documentation/uikit/uibarbuttonitem/hidessharedbackground>; `visibilityPriority` (iOS 27): <https://developer.apple.com/documentation/uikit/uibarbuttonitem/visibilitypriority>
- `UINavigationItem.setRightBarButtonItems(_:animated:)`: <https://developer.apple.com/documentation/uikit/uinavigationitem/setrightbarbuttonitems(_:animated:)>
- `sharedBackgroundVisibility(_:)`: <https://developer.apple.com/documentation/swiftui/toolbarcontent/sharedbackgroundvisibility(_:)>
- `Animation.default`: <https://developer.apple.com/documentation/swiftui/animation/default>; `Animation.smooth(duration:extraBounce:)`: <https://developer.apple.com/documentation/swiftui/animation/smooth(duration:extrabounce:)>
- `UIView.animate(springDuration:bounce:…)`: <https://developer.apple.com/documentation/uikit/uiview/animate(springduration:bounce:initialspringvelocity:delay:options:animations:completion:)>
- HIG Toolbars: <https://developer.apple.com/design/human-interface-guidelines/toolbars>; Materials: <https://developer.apple.com/design/human-interface-guidelines/materials>; Motion: <https://developer.apple.com/design/human-interface-guidelines/motion>

**Terceros**
- `liquid_glass_widgets` (Flutter, **MIT**): <https://github.com/sdegenaar/liquid_glass_widgets>. Archivos leídos: `docs/GLASS_NAVIGATION_TRANSITION.md`, `lib/widgets/effects/shared/glass_materialize_effect.dart` ("ajustado contra una captura a 120 fps de `glassEffectTransition(.materialize)` en la barra de navegación nativa"), `lib/widgets/effects/glass_materialize.dart`, `lib/constants/glass_defaults.dart`, `lib/widgets/surfaces/shared/glass_nav_pinned_host.dart` y `CHANGELOG.md`. Solo se han tomado comportamiento y números.
- MacStories, *iOS and iPadOS 26: The MacStories Review*, p. 6: <https://www.macstories.net/stories/ios-and-ipados-26-the-macstories-review/6/>
- Titanium SDK, issue #14649 (tiempos del push nativo en iOS 27): <https://github.com/tidev/titanium-sdk/issues/14649>
- Fatbobman, *Grow on iOS 26*: <https://fatbobman.com/en/posts/grow-on-ios26/>
- Foros de Apple: <https://developer.apple.com/forums/thread/804550>, <https://developer.apple.com/forums/thread/797116>, <https://developer.apple.com/forums/thread/815095>, <https://developer.apple.com/forums/thread/794154>

## Cómo trasladarlo a la app Android

Son propuestas; no se ha tocado el código. Siguen las reglas de rendimiento de CLAUDE.md: lo que cambia por fotograma se lee al dibujar, nada de recomponer en cada fotograma, y la colocación va con `offset {}` y no con una capa trasladada, para que el vidrio siga leyendo lo de detrás.

### En `GlassCircleButton`

1. **Nuevo parámetro de fase**: `phase: () -> Float = { 1f }` (0 = sin materializar, 1 = en reposo), `exiting: () -> Boolean` (para elegir la curva de entrada o de salida) y `anchor: TransformOrigin` (izquierda-centro para volver, derecha-centro para los ⋮). Sustituye al `graphicsLayer { alpha }` + `skipWhenClear` que hoy pone MainScreen. `skipWhenClear` pasa dentro del botón: con fase 0, no dibuja.
2. **Dos canales, como en la medición nativa**, calculados al dibujar a partir de `t = phase()`:
   - Entrada: vidrio `g = clamp(t / 0,92)` y contenido `c = clamp((t − 0,35) / 0,65)`.
   - Salida: vidrio `g = t` y contenido `c = clamp((t − 0,45) / 0,55)`. Así, al bajar `t` de 1 a 0, el icono se va primero.
3. **Capa exterior (vidrio + sombra)**: `graphicsLayer { scaleX = scaleY = 1,1 − 0,1·g; transformOrigin = anchor; alpha = g; compositingStrategy = ModulateAlpha }`. La escala es solo de capa y no afecta a la colocación. Ojo: el vidrio, escalado, lee el fondo con la escala aplicada; comprobar que no se descuadra (en `dissolveOnScroll` ya hubo que dar margen al desenfoque).
4. **Lente que se "enciende"**: lo que pide Apple es modular la refracción, no solo la opacidad. Hoy `GlassStyle` es clave de `remember` en `glass()`, y cambiarlo por fotograma rehace el `RenderEffect`. Hay dos opciones, y hay que medir la segunda antes de quedársela:
   - Dejarlo en opacidad + escala.
   - Añadir a `glassPiece` un `strength: () -> Float` que multiplique refracción, bisel y dispersión en los uniformes del shader al dibujar, sin recrear el efecto.
5. **Icono aparte**: `Icon` dentro de su propia capa con `alpha = c` y desenfoque `σ = 8 dp × (1 − c)`, redondeado a pasos de 2 px y en caché, como en `dissolveOnScroll`. Es una capa de 40 dp; el coste debería ser pequeño, pero hay que medirlo. En modo rendimiento (`Appearance.lite`), sin desenfoque ni escala: solo opacidad, como hace iOS con Movimiento reducido.
6. **Cambio de icono en el mismo botón** (opcional, para el futuro): `icon` y `previousIcon` con un `swap: () -> Float`. El saliente se emborrona (σ hasta 6 dp) y se funde entre el 25 % y el 60 %; el entrante llega con σ 3,5 dp y se enfoca entre el 48 % y el 90 %. Hoy no hace falta, porque todos los botones de cabecera llevan iconos fijos.

### En `MainScreen`

1. **Ventanas en vez de `lerp`**: `shownFor` pasa a devolver la fase con ventanas sobre `c = t.cover()` cuando el botón está solo en una de las dos páginas:
   - si lo tiene la de arriba (aparece al empujar): `fase = clamp((c − 0,45) / 0,55)`;
   - si lo tiene la de abajo (se va al empujar): `fase = 1 − clamp(c / 0,55)`.

   Como en un pop `c` baja de 1 a 0, las mismas fórmulas dan el pop al revés, que es lo que hace la referencia. `exiting` se decide por cuál de las dos páginas tiene el botón, no por el sentido del gesto, para que no salte si el gesto se invierte. Esto también sustituye a la regla actual del zoom ("no se cruzan en mitades"), que ya iba en esa dirección.
2. **Un solo ⋮ arriba a la derecha**: la raíz (`ConnectionMenuButton`) y el álbum/playlist llevan el mismo icono en el mismo rincón. En iOS, un botón igual en los dos niveles *queda quieto* [Apple]; hoy se funden dos vidrios uno sobre otro. Propuesta:
   - dibujar un único botón de vidrio que no cambia de fase;
   - interpolar su `top` entre 16 dp (raíz) y 13 dp (álbum) con `Modifier.offset { }` leyendo `cover()`; o mejor, igualar las dos alturas, decisión de diseño del usuario;
   - cambiar a mitad (`cover` = 0,5) a qué menú abre (`ConnectionMenu` o `PlayMenu`).

   El tono (`topLabel`) ya se muestrea por zona. En tablet, el ⋮ de las raíces y el de álbum están ambos a `top + 13 dp`, así que ahí basta con no fundirlos. Con Buscar (sin ⋮ de raíz) sigue habiendo aparición y desaparición normal.
3. **Botón de volver**: entre dos páginas anidadas ya está quieto (correcto). Solo cambia raíz ↔ anidada, con las ventanas de arriba y `anchor` a la izquierda.
4. **Gesto de atrás predictivo**: hoy la fase sigue al dedo. Hay dos opciones; conviene que el usuario las vea, porque iOS no está confirmado:
   - **(a)** Seguir al dedo con las ventanas nuevas: el "‹" se disuelve en el primer 55 % del arrastre.
   - **(b)** Como la referencia Flutter: congelar la fase mientras el dedo arrastra, en el valor de antes del gesto. Al soltar y confirmar, reproducir la salida con su reloj (~350 ms, como `dematerializeDuration`); al cancelar, no hay nada que deshacer.

   La (b) necesita saber en MainScreen si el `PageTransition` está en gesto. Hoy `predictiveBack` lo sabe dentro de `PageStack`; habría que exponerlo como estado leído al dibujar.
5. **Sin cambios en** el oscurecido, el borde superior (`edgeAlpha`) ni la barra de pestañas: no son botones de barra.

### Orden sugerido y cómo comprobarlo

1. Unificar el ⋮ (es el error visible más claro: dos vidrios al 50 %).
2. Ventanas y escala del botón de volver.
3. Icono aparte con desenfoque.
4. Modulación de la lente, si la medición lo permite.

Comprobar grabando el teléfono en claro y oscuro (push, pop con botón, gesto sujeto a medias y cancelado, zoom de álbum) y midiendo con `bench` + `gfxinfo framestats` como en "Rendimiento" de CLAUDE.md. La regla de "nada que lea el progreso en la capa de la página" se cumple porque los botones ya se dibujan fuera de la página.
