import AVFoundation
import Shared
import SwiftUI
import UIKit

/// Portada animada de la canción que suena (`editorialVideo.motionTallVideo3x4`, HLS público de Apple)
/// y su fotograma fijo (`previewFrame`: coincide con el primer fotograma, así que sirve de espera sin
/// salto). Se consulta al cambiar de canción, no al abrir la hoja, para que al abrirla ya esté.
@MainActor
@Observable
final class AnimatedCovers {
    struct Hero: Equatable {
        let video: URL
        let preview: URL?
        /// Fotograma fijo descargado (mientras no está, la hoja usa la tarjeta de siempre).
        var image: UIImage?
        /// Altura (fracción del ancho, desde arriba) del cuadrado del vídeo que coincide con la carátula fija.
        var squareTop: Double?
        /// La franja de abajo del fotograma es clara (luminancia > 0,5): la hoja lleva un velo extra.
        var lightBottom = false
    }

    /// Lo que hay para la canción actual (`nil`: no tiene o aún no se sabe).
    private(set) var current: Hero?
    private var currentId: String?
    @ObservationIgnored private var known: [String: Hero?] = [:]
    @ObservationIgnored private var task: Task<Void, Never>?

    /// Ancho con que se pide el fotograma fijo (3:4).
    private static let previewPx: Int32 = 1200

    func track(_ catalogId: String?, artwork: Shared.Artwork?, api: CiderApi?) {
        guard catalogId != currentId else { return }
        currentId = catalogId
        task?.cancel()
        guard let catalogId, let api else { current = nil; return }
        if let hit = known[catalogId] { current = hit; return }
        current = nil
        task = Task { [weak self] in
            // Un fallo de red no se recuerda: se reintenta la próxima vez.
            let cover: AnimatedCover?
            do { cover = try await api.animatedCoverForSong(catalogSongId: catalogId) } catch { return }
            guard let tall = cover?.tall, let video = URL(string: tall) else {
                self?.remember(catalogId, nil)
                return
            }
            var hero = Hero(video: video, preview: cover?.tallPreview?.sized(w: Self.previewPx, h: Self.previewPx * 4 / 3).flatMap { URL(string: $0) })
            if let url = hero.preview, let (data, _) = try? await URLSession.shared.data(from: url), let image = UIImage(data: data) {
                hero.image = image
                var artData: Data?
                if let small = artwork?.sized(px: 48).flatMap({ URL(string: $0) }) {
                    artData = try? await URLSession.shared.data(from: small).0
                }
                let art = artData.flatMap { UIImage(data: $0) }
                let measured = await Task.detached(priority: .utility) {
                    (Self.squareTop(preview: image, artwork: art), Self.bottomLuminance(image))
                }.value
                hero.squareTop = measured.0
                hero.lightBottom = measured.1 > 0.5
            }
            self?.remember(catalogId, hero)
        }
    }

    private func remember(_ id: String, _ hero: Hero?) {
        known[id] = .some(hero)
        if id == currentId { current = hero }
    }

    // MARK: - Medidas sobre el fotograma fijo

    /// Píxeles RGB de una imagen reducida a `w × h`.
    nonisolated private static func pixels(_ image: UIImage, _ w: Int, _ h: Int) -> [Double]? {
        guard let cg = image.cgImage else { return nil }
        var px = [UInt8](repeating: 0, count: w * h * 4)
        let ok: Bool = px.withUnsafeMutableBytes { buf in
            guard let ctx = CGContext(data: buf.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                                      space: CGColorSpace(name: CGColorSpace.sRGB)!,
                                      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            ctx.interpolationQuality = .medium
            ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
            return true
        }
        guard ok else { return nil }
        var out = [Double]()
        out.reserveCapacity(w * h * 3)
        for i in 0..<(w * h) {
            out.append(Double(px[i * 4]))
            out.append(Double(px[i * 4 + 1]))
            out.append(Double(px[i * 4 + 2]))
        }
        return out
    }

    /// Dónde cae la carátula fija dentro del vídeo vertical: arriba del todo en unos álbumes ("Un Verano
    /// Sin Ti"), centrada en otros ("Short n' Sweet"). Se compara la carátula a 48 × 48 con cada
    /// desplazamiento vertical del fotograma a 48 × 64 y se queda el de menor error (como en Android).
    nonisolated private static func squareTop(preview: UIImage, artwork: UIImage?) -> Double? {
        let n = 48
        guard let artwork, let a = pixels(artwork, n, n), let v = pixels(preview, n, n * 4 / 3) else { return nil }
        var best = (offset: (n * 4 / 3 - n) / 2, error: Double.greatestFiniteMagnitude)
        for offset in 0...(n * 4 / 3 - n) {
            var e = 0.0
            for i in 0..<(n * n * 3) {
                let d = a[i] - v[offset * n * 3 + i]
                e += d * d
            }
            if e < best.error { best = (offset, e) }
        }
        return Double(best.offset) / Double(n)
    }

    /// Luminancia (0–1) del 30 % inferior del fotograma.
    nonisolated private static func bottomLuminance(_ image: UIImage) -> Double {
        let w = 16, h = 20
        guard let p = pixels(image, w, h) else { return 0 }
        var sum = 0.0, count = 0.0
        for y in 14..<h {
            for x in 0..<w {
                let i = (y * w + x) * 3
                sum += (0.2126 * p[i] + 0.7152 * p[i + 1] + 0.0722 * p[i + 2]) / 255
                count += 1
            }
        }
        return sum / count
    }
}

/// Reproductor de la portada animada: sin sonido, en bucle. Un solo `AVPlayer` alimenta varias capas
/// (el vídeo nítido y sus dos reflejos desenfocados).
@MainActor
final class CoverVideo {
    let player = AVPlayer()
    private var loop: NSObjectProtocol?

    init(url: URL) {
        player.isMuted = true
        player.preventsDisplaySleepDuringVideoPlayback = false
        player.actionAtItemEnd = .none
        let item = AVPlayerItem(url: url)
        // Basta con la resolución de la pantalla; el reflejo va aún más pequeño.
        item.preferredMaximumResolution = CGSize(width: 1440, height: 1920)
        player.replaceCurrentItem(with: item)
        loop = NotificationCenter.default.addObserver(forName: AVPlayerItem.didPlayToEndTimeNotification, object: item, queue: .main) { [player] _ in
            player.seek(to: .zero)
            player.play()
        }
        player.play()
    }

    deinit {
        if let loop { NotificationCenter.default.removeObserver(loop) }
    }
}

/// Una capa de vídeo (`AVPlayerLayer`, relleno) que aparece fundiéndose cuando tiene el primer fotograma.
struct VideoLayer: UIViewRepresentable {
    let player: AVPlayer

    final class View: UIView {
        override class var layerClass: AnyClass { AVPlayerLayer.self }
        var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
        private var ready: NSKeyValueObservation?

        func attach(_ player: AVPlayer) {
            guard playerLayer.player !== player else { return }
            playerLayer.player = player
            playerLayer.videoGravity = .resizeAspectFill
            alpha = playerLayer.isReadyForDisplay ? 1 : 0
            ready = playerLayer.observe(\.isReadyForDisplay, options: [.new]) { [weak self] layer, _ in
                DispatchQueue.main.async {
                    guard let self, layer.isReadyForDisplay, self.alpha == 0 else { return }
                    UIView.animate(withDuration: 0.3) { self.alpha = 1 }
                }
            }
        }
    }

    func makeUIView(context: Context) -> View {
        let v = View()
        v.isUserInteractionEnabled = false
        v.attach(player)
        return v
    }

    func updateUIView(_ uiView: View, context: Context) { uiView.attach(player) }
}

/// Portada animada a pantalla completa, como Apple Music en iOS 26: el vídeo 3:4 arriba a todo el ancho
/// y, debajo, **su parte baja reflejada en el borde inferior, estirada × 1,8, con desenfoque
/// progresivo**: una capa suave (σ = 0,035 × ancho) que entre el 85 % y el 120 % del alto del vídeo se
/// funde con otra muy ancha (σ = 0,22 × ancho). El vídeo nítido se funde del 76 % al 92 % de su alto en
/// arco (degradados radiales centrados en el eje al 20 % del alto), y el velo negro va solo por debajo
/// del vídeo (0 en su borde → 0,37 abajo). Valores medidos en la app de Android contra una captura real
/// de iPhone con "Mercury" de Steve Lacy (ver CLAUDE.md, "Réplica de iOS 26 en la hoja").
struct HeroArtwork: View {
    let hero: AnimatedCovers.Hero
    let size: CGSize

    @State private var video: CoverVideo?

    /// Los reflejos se dibujan a 1/4 y se amplían: son muy borrosos (no se nota) y cuesta 16 veces menos.
    private static let reduce: CGFloat = 4

    var body: some View {
        let w = size.width, h = size.height
        let vh = w * 4 / 3
        ZStack(alignment: .topLeading) {
            Color.black
            reflection(blur: 0.22 * w)
            reflection(blur: 0.035 * w)
                .mask(alignment: .topLeading) { arc(center: 0.2 * vh, from: 0.65 * vh, to: 1.0 * vh) }
            frame
                .frame(width: w, height: vh)
                .mask(alignment: .topLeading) { arc(center: 0.2 * vh, from: 0.56 * vh, to: 0.72 * vh) }
            // Velo solo por debajo del vídeo: Apple no oscurece el vídeo.
            LinearGradient(colors: [.black.opacity(0), .black.opacity(0.37)], startPoint: .top, endPoint: .bottom)
                .frame(width: w, height: max(h - vh, 1))
                .offset(y: vh)
            if hero.lightBottom {
                // Portadas claras: velo extra desde el 60 % del vídeo (0,45 a la altura del título, 0,6 abajo).
                LinearGradient(stops: [.init(color: .black.opacity(0), location: 0),
                                       .init(color: .black.opacity(0.45), location: 0.6),
                                       .init(color: .black.opacity(0.6), location: 1)],
                               startPoint: .top, endPoint: .bottom)
                    .frame(width: w, height: max(h - 0.6 * vh, 1))
                    .offset(y: 0.6 * vh)
            }
        }
        .frame(width: w, height: h, alignment: .topLeading)
        .clipped()
        .task(id: hero.video) { video = CoverVideo(url: hero.video) }
    }

    /// El fotograma fijo y, encima, el vídeo en cuanto tiene imagen.
    private var frame: some View {
        ZStack {
            if let image = hero.image {
                Image(uiImage: image).resizable().scaledToFill()
            }
            if let video {
                VideoLayer(player: video.player)
            }
        }
        .clipped()
    }

    /// Vídeo y su reflejo, a 1/4 de tamaño, desenfocados y ampliados a pantalla completa. El reflejo
    /// también lleva el color de Apple: saturación × 1,3 y brillo × 0,82.
    private func reflection(blur sigma: CGFloat) -> some View {
        let k = Self.reduce
        let w = size.width / k, h = size.height / k
        let vh = w * 4 / 3
        return ZStack(alignment: .topLeading) {
            frame.frame(width: w, height: vh)
            frame.frame(width: w, height: vh)
                .scaleEffect(x: 1, y: -1)
                .scaleEffect(x: 1, y: 1.8, anchor: .top)
                .offset(y: vh)
            Color.black.opacity(0.18)
                .frame(width: w, height: max(h - vh, 1))
                .offset(y: vh)
        }
        .frame(width: w, height: h, alignment: .topLeading)
        .clipped()
        .saturation(1.3)
        .blur(radius: sigma / k, opaque: true)
        .scaleEffect(k, anchor: .topLeading)
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    /// Máscara opaca hasta `from` y transparente desde `to`, en círculos con centro en el eje a la
    /// altura `center`: la nitidez cae antes en los bordes que en el centro, como en Apple Music.
    private func arc(center: CGFloat, from: CGFloat, to: CGFloat) -> some View {
        RadialGradient(colors: [.white, .white.opacity(0)],
                       center: UnitPoint(x: 0.5, y: center / size.height),
                       startRadius: from, endRadius: to)
            .frame(width: size.width, height: size.height)
    }
}

/// La portada animada en vuelo (abrir/cerrar la hoja). Sale **cuadrada** del cuadrado del vídeo que
/// coincide con la carátula (`squareTop`), sobre la miniatura del miniplayer; en la primera mitad del
/// recorrido el recorte crece hasta la pantalla entera (arriba con smoothstep, abajo a ritmo constante)
/// mientras todo se amplía hasta su tamaño. Encima, la carátula fija se funde con el vídeo en el
/// primer 30 % (`cover`), así el primer fotograma coincide con la miniatura sea cual sea el álbum.
struct HeroFlight: ViewModifier, Animatable {
    var progress: Double
    var drag: Double
    let from: CGRect
    let size: CGSize
    /// Borde superior del cuadrado de la carátula dentro del vídeo, en pt de la hoja abierta.
    let squareTop: CGFloat
    let fromCorner: CGFloat

    var animatableData: AnimatablePair<Double, Double> {
        get { AnimatablePair(progress, drag) }
        set { progress = newValue.first; drag = newValue.second }
    }

    private static let formEnd = 0.5

    func body(content: Content) -> some View {
        let p = min(max(progress, 0), 1)
        let w = size.width, h = size.height
        let start = from == .zero ? CGRect(x: 24, y: h - 120, width: 30, height: 30) : from
        // Escala: de la miniatura (que es el cuadrado de ancho w) a 1.
        let k0 = start.width / w
        let k = k0 + (1 - k0) * p
        // Recorte en coordenadas de la hoja: del cuadrado a la pantalla en la primera mitad.
        let f = min(p / Self.formEnd, 1)
        let s = f * f * (3 - 2 * f)
        let cropTop = squareTop * (1 - s)
        let cropBottom = (squareTop + w) + (h - squareTop - w) * f
        // Posición: el borde superior del recorte va lineal de la miniatura a 0; el centro horizontal se
        // adelanta (1 − (1 − p)²) como la carátula fija, así la trayectoria es curva.
        let ahead = 1 - (1 - p) * (1 - p)
        let top = start.minY + (0 - start.minY) * p
        let cx = start.midX + (w / 2 - start.midX) * ahead
        let x = cx - k * w / 2
        let y = top - k * cropTop + drag
        // Esquinas: las de la miniatura → las de la pantalla mientras vuela → ninguna abierta.
        let flying = 6 + (50 - 6) * min(p / 0.3, 1)
        let screen = p < 1 ? flying * (1 - max(0, (p - 0.85) / 0.15)) : 0
        let corner = max(p == 0 ? fromCorner : screen, min(drag * 0.8, 50))
        content
            .frame(width: w, height: h)
            .clipShape(CropShape(top: cropTop, bottom: cropBottom, radius: corner / k))
            .scaleEffect(k, anchor: .topLeading)
            .offset(x: x, y: y)
    }
}

/// Rectángulo redondeado de `top` a `bottom` a todo el ancho.
struct CropShape: Shape {
    var top: CGFloat
    var bottom: CGFloat
    var radius: CGFloat

    func path(in rect: CGRect) -> Path {
        Path(roundedRect: CGRect(x: rect.minX, y: top, width: rect.width, height: max(bottom - top, 0)),
             cornerRadius: radius, style: .continuous)
    }
}

/// La carátula fija encima del vídeo durante el primer 30 % del vuelo: misma caja que el cuadrado del
/// vídeo, así se transforma con él.
struct HeroCoverFade: ViewModifier, Animatable {
    var progress: Double
    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    func body(content: Content) -> some View {
        let t = min(max(progress / 0.3, 0), 1)
        content.opacity(1 - t * t * (3 - 2 * t))
    }
}
