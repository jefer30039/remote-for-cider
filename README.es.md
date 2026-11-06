# Remote for Cider

[English](README.md)

Control remoto nativo para Android de [Cider](https://cider.sh), el cliente de Apple Music para escritorio. Controla la reproducción, explora tu biblioteca, lee letras sincronizadas y maneja la cola desde el teléfono o la tablet, con una interfaz inspirada en Apple Music.

> Proyecto no oficial, sin relación con Cider Collective ni con Apple. Apple Music es una marca de Apple Inc.

## Funciones

- **Reproducción**: carátula (incluidas las portadas animadas de Apple Music), progreso, reproducir/pausa, siguiente/anterior, aleatorio, repetición y volumen de Cider.
- **Pantalla de bloqueo y notificación** mediante una sesión de medios; las teclas de volumen del teléfono cambian el volumen de Cider.
- **A continuación**: ver la cola, reordenar arrastrando y quitar deslizando.
- **Biblioteca**: pins, playlists, artistas, álbumes, canciones y agregados recientemente; desliza una canción a la derecha para reproducirla a continuación o al final.
- **Inicio y búsqueda**: tus recomendaciones de Apple Music, búsqueda en el catálogo y categorías.
- **Letras sincronizadas**.
- **Diseño para tablet** con barra de pestañas arriba, barra lateral y páginas de álbum a dos columnas.
- **Encuentra la computadora sola** en la red local, incluido el hotspot del teléfono, así que sigue funcionando aunque cambie la IP.
- En inglés y español.

## Requisitos

- Cider 4 con su API activada (WebSockets/API en los ajustes de conectividad de Cider). Remote for Cider se empareja con el sistema de tokens de Cider: Cider te pide que apruebes la app.
- Android 8.0 o posterior, en la misma red que la computadora (Wi-Fi, Ethernet o el hotspot del teléfono).
- Un pequeño **puente de red** en la computadora (ver abajo).

## Instalación

### 1. Instalar el puente en la computadora

La API de Cider solo escucha en `127.0.0.1:10767`, así que otros dispositivos no llegan. El puente reenvía el puerto **10768** de tu red hacia ella. (No se puede usar el 10767: Cider lo comprueba al arrancar y, si está ocupado, se va a un puerto aleatorio.)

**Windows** (proxy de puertos integrado, no hay que instalar nada). En PowerShell como administrador:

```powershell
powershell -ExecutionPolicy Bypass -File bridge\windows\install-bridge.ps1
```

Solo acepta conexiones desde redes marcadas como *Privadas*.

**Linux** (servicio de usuario de systemd; necesita `socat`):

```sh
bridge/linux/install.sh                      # todas las interfaces
bridge/linux/install.sh --interface wlp3s0   # solo esa interfaz (recomendado en laptops)
```

**macOS** (LaunchAgent; necesita `brew install socat`):

```sh
bridge/macos/install.sh
```

Cada carpeta tiene su script para desinstalar.

### 2. Instalar la app

Descarga el APK desde [Releases](../../releases) y ábrelo en el teléfono (permite instalar desde el navegador o el gestor de archivos cuando lo pida).

### 3. Emparejar

Abre la app. Busca Cider en tu red y rellena la dirección. Toca **Conectar** y pulsa **Permitir** en el aviso que muestra Cider en la computadora.

## Privacidad y seguridad

- Para controlar Cider hace falta el token que Cider le da a la app cuando la apruebas.
- Los eventos en vivo de Cider (qué está sonando) no piden token, así que con el puente activo cualquiera en la misma red puede ver qué escuchas. En laptops que se conectan a Wi-Fi públicas, ata el puente a la interfaz de casa o desinstálalo.
- El tráfico entre la app y el puente es HTTP sin cifrar dentro de tu red local.
- La app solo habla con tu computadora. Las carátulas y los vídeos se cargan de los servidores de Apple.

## Opcional: graves con EasyEffects (Linux)

`bridge/linux/install.sh --with-easyeffects` instala un pequeño servicio en el puerto 10769 que permite a la app cambiar EasyEffects entre dos presets de salida que crees tú: **Bocina** (graves activados) y **Normal**. El interruptor aparece en la app en el botón del dispositivo de reproducción. El servicio solo acepta peticiones con un token que Cider acepte.

## Problemas frecuentes

- **La app no encuentra la computadora**: comprueba que ambas están en la misma red, que el puente está activo y que el firewall permite TCP 10768. También puedes usar ⋮ → *Cambiar dirección…*.
- **Xiaomi/HyperOS pausa Cider una y otra vez**: HyperOS pausa las demás sesiones de medios cuando una empieza a sonar. Si otra app (por ejemplo, el control multimedia de KDE Connect o Zorin Connect) también refleja Cider en el teléfono, desactiva ese complemento.
- **La notificación desaparece al actualizar la app**: abre la app una vez.

## Compilar desde el código

Requiere JDK 21 y el SDK de Android (compile SDK 37).

```sh
./gradlew :androidApp:assembleDebug
# androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Estructura:

- `shared/` – cliente Kotlin Multiplatform de la API de Cider: REST, un cliente Socket.IO mínimo, emparejamiento, estado en vivo y un parser de letras TTML. Incluye una pequeña CLI para desarrollo.
- `androidApp/` – la app en Jetpack Compose.
- `bridge/` – instaladores del puente de red.
- `docs/api-v2.md` – notas sobre la API v2 de Cider.

## Licencia

[MIT](LICENSE). La fuente Inter incluida está bajo la [SIL Open Font License](docs/licencias/Inter-OFL.txt). La barra de pestañas de vidrio líquido usa [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) de Kyant y adapta su componente de ejemplo `LiquidBottomTabs` ([Apache 2.0](docs/licencias/AndroidLiquidGlass-Apache-2.0.txt)).
