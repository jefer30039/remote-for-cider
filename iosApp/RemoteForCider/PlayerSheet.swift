import OSLog
import SwiftUI

/// Registro de la transición (`log show --predicate 'category == "hoja"'`).
let sheetLog = Logger(subsystem: "io.github.jefer30039.remoteforcider", category: "hoja")

/// Estado de la hoja de reproducción y de su transición desde el miniplayer (como Apple Music).
///
/// La hoja es un `fullScreenCover` que aparece y desaparece **sin animación del sistema**: la
/// transición la hace la propia hoja con `progress` (0 = miniplayer, 1 = abierta). La carátula
/// vuela desde la miniatura del miniplayer (`miniArt`, en coordenadas de la ventana) y el resto
/// sube desde la altura del miniplayer fundiéndose. Deslizar hacia abajo baja la hoja entera con el
/// dedo (`drag`, como una hoja de iOS: nada se funde ni encoge) y, al soltar, se recoge en el
/// miniplayer desde donde quedó. El zoom del sistema (`navigationTransition(.zoom)`) no sirve aquí: desde el accesorio
/// de la barra de pestañas salía descentrado y al cerrar no volvía al miniplayer.
@MainActor
@Observable
final class PlayerSheet {
    /// El `fullScreenCover` está puesto (también mientras dura la transición).
    var presented: Bool
    var progress: Double
    /// Miniatura del miniplayer en coordenadas de la ventana.
    var miniArt: CGRect = .zero
    /// Cuánto ha bajado la hoja con el dedo (pt).
    var drag: CGFloat = 0
    /// La hoja ya está dibujada y su carátula tapa la miniatura: el miniplayer puede esconder la suya.
    /// Antes se escondía al pedir la hoja y, mientras el sistema la ponía (un par de fotogramas), la
    /// miniatura se veía en negro.
    var coversMini: Bool

    static let curve = Animation.smooth(duration: 0.45)

    init(open: Bool = false) {
        presented = open
        progress = open ? 1 : 0
        coversMini = open
    }

    /// Se está abriendo: la hoja aún no ha aparecido y, cuando lo haga, anima de 0 a 1.
    private var opening = false

    func open() {
        guard !presented else { return }
        sheetLog.notice("abrir: miniatura \(self.miniArt.debugDescription, privacy: .public)")
        progress = 0
        opening = true
        withoutAnimation { presented = true }
    }

    /// La hoja ya está en pantalla (dibujada en 0, la carátula sobre la miniatura): ahora se anima.
    /// Antes se animaba a los 34 ms de pedirla y la presentación tardaba más: aparecía ya abierta.
    func appeared() {
        coversMini = true
        guard opening else { return }
        opening = false
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(17))
            sheetLog.notice("abrir: animación")
            withAnimation(Self.curve) { self.progress = 1 } completion: { sheetLog.notice("abrir: fin") }
        }
    }

    func close(animation: Animation = PlayerSheet.curve) {
        guard presented else { return }
        sheetLog.notice("cerrar desde \(self.progress, privacy: .public)")
        withAnimation(animation) {
            progress = 0
            drag = 0
        } completion: {
            sheetLog.notice("cerrar: fin")
            if self.progress == 0 {
                self.withoutAnimation {
                    self.coversMini = false
                    self.presented = false
                }
            }
        }
    }

    /// Vuelve a abrirse del todo (gesto cancelado).
    func settle() {
        withAnimation(.spring(response: 0.4, dampingFraction: 0.86)) {
            progress = 1
            drag = 0
        }
    }

    /// Sigue al dedo (sin animación); hacia arriba apenas se mueve.
    func follow(_ translation: CGFloat) {
        withoutAnimation { drag = translation >= 0 ? translation : translation / 5 }
    }

    private func withoutAnimation(_ body: () -> Void) {
        var t = Transaction()
        t.disablesAnimations = true
        withTransaction(t, body)
    }
}

/// La carátula en vuelo: de `from` (miniatura del miniplayer) a `to` (su sitio en la hoja, encogida
/// a `scale` en pausa). Borde superior y tamaño van lineales con el progreso y el centro horizontal se
/// adelanta (1 − (1 − p)²), así la trayectoria es curva. Se dibuja a tamaño final y se escala (barato).
struct ArtFlight: ViewModifier, Animatable {
    var progress: Double
    var scale: Double
    /// La hoja bajada con el dedo: la carátula baja con ella.
    var drag: Double = 0
    let from: CGRect
    let to: CGRect
    let fromCorner: CGFloat
    let toCorner: CGFloat

    var animatableData: AnimatablePair<AnimatablePair<Double, Double>, Double> {
        get { AnimatablePair(AnimatablePair(progress, scale), drag) }
        set {
            progress = newValue.first.first
            scale = newValue.first.second
            drag = newValue.second
        }
    }

    /// `-demoLogFrames YES`: anota cada fotograma de la transición (para ver en CI si va fluida).
    private static let logFrames = UserDefaults.standard.bool(forKey: "demoLogFrames")

    private static func logFrame(_ p: Double) {
        if logFrames, p > 0, p < 1 { sheetLog.notice("fotograma \(p, privacy: .public)") }
    }

    func body(content: Content) -> some View {
        let p = min(max(progress, 0), 1)
        let _ = Self.logFrame(p)
        let side = to.width * scale
        let big = CGRect(x: to.midX - side / 2, y: to.midY - side / 2 + drag, width: side, height: side)
        let start = from == .zero ? CGRect(x: 24, y: to.maxY + 300, width: 30, height: 30) : from
        let w = start.width + (big.width - start.width) * p
        let top = start.minY + (big.minY - start.minY) * p
        let ahead = 1 - (1 - p) * (1 - p)
        let cx = start.midX + (big.midX - start.midX) * ahead
        let k = max(w / to.width, 0.001)
        let corner = fromCorner + (toCorner - fromCorner) * p
        content
            .frame(width: to.width, height: to.width)
            .clipShape(RoundedRectangle(cornerRadius: corner / k, style: .continuous))
            .shadow(color: .black.opacity(0.3 * p), radius: 22 / k, y: 10 / k)
            .scaleEffect(k)
            .position(x: cx, y: top + w / 2)
    }
}

/// El resto de la hoja: sube desde la altura del miniplayer y aparece fundiéndose (nada hasta el
/// 20 % del recorrido, del todo al 80 %).
struct SheetFade: ViewModifier, Animatable {
    var progress: Double
    var drag: Double
    let distance: CGFloat
    var from: Double = 0.2
    var to: Double = 0.8

    var animatableData: AnimatablePair<Double, Double> {
        get { AnimatablePair(progress, drag) }
        set { progress = newValue.first; drag = newValue.second }
    }

    func body(content: Content) -> some View {
        let p = min(max(progress, 0), 1)
        let t = min(max((p - from) / (to - from), 0), 1)
        content
            .opacity(t * t * (3 - 2 * t))
            .offset(y: (1 - p) * distance + drag)
    }
}
