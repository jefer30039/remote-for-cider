import Shared
import SwiftUI
import UIKit

/// Color en sRGB que se puede mezclar (los `Color` de SwiftUI no se interpolan a mano).
struct RGB: Equatable {
    var r: Double, g: Double, b: Double

    var color: Color { Color(.sRGB, red: r, green: g, blue: b) }

    func mixed(with o: RGB, _ t: Double) -> RGB {
        RGB(r: r + (o.r - r) * t, g: g + (o.g - g) * t, b: b + (o.b - b) * t)
    }

    /// Mismo tono, brillo multiplicado (para la paleta de reserva hecha con `bgColor`).
    func scaled(_ k: Double) -> RGB {
        RGB(r: min(r * k, 1), g: min(g * k, 1), b: min(b * k, 1))
    }

    init(r: Double, g: Double, b: Double) { self.r = r; self.g = g; self.b = b }

    init?(hex: String?) {
        guard let hex, let v = UInt32(hex.trimmingCharacters(in: CharacterSet(charactersIn: "#")), radix: 16) else { return nil }
        self.init(r: Double((v >> 16) & 0xFF) / 255, g: Double((v >> 8) & 0xFF) / 255, b: Double(v & 0xFF) / 255)
    }
}

/// Cuatro colores dominantes de una carátula, del más al menos presente, ajustados al fondo de la
/// hoja como hace Apple Music (los mismos valores que la app de Android, medidos en la app oficial):
/// k-medias sobre 20 × 20 px, saturación × 1,3 y brillo × 0,63 con tope 0,5.
@MainActor
enum ArtworkPalette {
    private static var cache: [String: [RGB]] = [:]

    static func cached(_ url: String) -> [RGB]? { cache[url] }

    static func load(_ url: String) async -> [RGB]? {
        if let hit = cache[url] { return hit }
        guard let u = URL(string: url), let (data, _) = try? await URLSession.shared.data(from: u) else { return nil }
        let palette = await Task.detached(priority: .utility) { extract(data) }.value
        if let palette { cache[url] = palette }
        return palette
    }

    nonisolated private static func extract(_ data: Data) -> [RGB]? {
        guard let image = UIImage(data: data)?.cgImage else { return nil }
        let side = 20
        var px = [UInt8](repeating: 0, count: side * side * 4)
        let drawn: Bool = px.withUnsafeMutableBytes { buf in
            guard let ctx = CGContext(
                data: buf.baseAddress, width: side, height: side, bitsPerComponent: 8, bytesPerRow: side * 4,
                space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            ctx.interpolationQuality = .medium
            ctx.draw(image, in: CGRect(x: 0, y: 0, width: side, height: side))
            return true
        }
        guard drawn else { return nil }
        var points: [SIMD3<Double>] = []
        points.reserveCapacity(side * side)
        for i in 0..<(side * side) {
            let r = Double(px[i * 4]) / 255
            let g = Double(px[i * 4 + 1]) / 255
            let b = Double(px[i * 4 + 2]) / 255
            points.append(SIMD3<Double>(r, g, b))
        }
        // k-medias (k = 4) con semillas repartidas por brillo.
        func luma(_ p: SIMD3<Double>) -> Double { p.x * 0.2126 + p.y * 0.7152 + p.z * 0.0722 }
        let sorted = points.sorted { luma($0) < luma($1) }
        var centers = (0..<4).map { sorted[(sorted.count - 1) * (2 * $0 + 1) / 8] }
        var counts = [Int](repeating: 0, count: 4)
        for _ in 0..<10 {
            var sums = [SIMD3<Double>](repeating: .zero, count: 4)
            counts = [0, 0, 0, 0]
            for p in points {
                var best = 0
                var bestD = Double.greatestFiniteMagnitude
                for k in 0..<4 {
                    let d = p - centers[k]
                    let dd = (d * d).sum()
                    if dd < bestD { bestD = dd; best = k }
                }
                counts[best] += 1
                sums[best] += p
            }
            for k in 0..<4 where counts[k] > 0 { centers[k] = sums[k] / Double(counts[k]) }
        }
        return (0..<4).filter { counts[$0] > 0 }.sorted { counts[$0] > counts[$1] }.map { k in
            let c = centers[k]
            var h: CGFloat = 0, s: CGFloat = 0, v: CGFloat = 0, a: CGFloat = 0
            UIColor(red: c.x, green: c.y, blue: c.z, alpha: 1).getHue(&h, saturation: &s, brightness: &v, alpha: &a)
            let out = UIColor(hue: h, saturation: min(s * 1.3 + 0.02, 0.8), brightness: min(max(v * 0.63, 0.1), 0.5), alpha: 1)
            var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0
            out.getRed(&r, green: &g, blue: &b, alpha: &a)
            return RGB(r: r, g: g, b: b)
        }
    }
}

/// Fondo de la hoja de reproducción de Apple Music: cuatro manchas radiales enormes con la paleta
/// de la carátula que derivan despacio (ciclos de 29–43 s) sobre el color más presente, apenas más
/// oscuro abajo. Al cambiar de canción la paleta nueva se funde en 1,2 s. Se dibuja a 24 fps.
struct ColorField: View {
    let artwork: Shared.Artwork?

    @State private var shown: [RGB] = []
    @State private var previous: [RGB] = []
    @State private var switchedAt = Date.distantPast
    @State private var start = Date()

    private static let periods: [Double] = [31, 37, 43, 29]
    private static let phases: [Double] = [0, 1.9, 3.7, 5.1]

    var body: some View {
        let url = artwork?.sized(px: 64)
        TimelineView(.animation(minimumInterval: 1.0 / 24)) { timeline in
            let now = timeline.date
            let t = now.timeIntervalSince(start)
            let mix = min(max(now.timeIntervalSince(switchedAt) / 1.2, 0), 1)
            GeometryReader { geo in
                let w = Double(geo.size.width)
                let h = Double(geo.size.height)
                ZStack {
                    color(0, mix).color
                    ForEach(0..<4, id: \.self) { i in
                        Self.blob(i, t: t, w: w, h: h, color: color((i + 1) % 4, mix).color)
                    }
                    LinearGradient(stops: [.init(color: .clear, location: 0.5), .init(color: .black.opacity(0.12), location: 1)],
                                   startPoint: .top, endPoint: .bottom)
                }
                .frame(width: CGFloat(w), height: CGFloat(h))
                .clipped()
            }
            .drawingGroup()
        }
        .task(id: url) {
            let fallback = Self.fallback(artwork?.bgColor)
            if shown.isEmpty { shown = url.flatMap { ArtworkPalette.cached($0) } ?? fallback }
            guard let url else { change(to: fallback); return }
            if let palette = await ArtworkPalette.load(url) { change(to: palette) } else { change(to: fallback) }
        }
    }

    /// Una mancha: degradado radial de su color a transparente que gira despacio por la pantalla.
    private static func blob(_ i: Int, t: Double, w: Double, h: Double, color c: Color) -> some View {
        let th: Double = t * (2 * Double.pi / periods[i]) + phases[i]
        let r: Double = w * (0.95 + 0.15 * sin(th * 0.7))
        let x: Double = w * (0.5 + 0.5 * cos(th))
        let y: Double = h * (0.42 + 0.38 * sin(th * 1.3 + phases[i]))
        let gradient = RadialGradient(colors: [c.opacity(0.9), c.opacity(0)], center: .center, startRadius: 0, endRadius: CGFloat(r))
        return Circle()
            .fill(gradient)
            .frame(width: CGFloat(2 * r), height: CGFloat(2 * r))
            .position(x: CGFloat(x), y: CGFloat(y))
    }

    private func change(to palette: [RGB]) {
        guard palette != shown else { return }
        // Desde lo que se ve ahora mismo (si cambia a media mezcla, sin salto).
        let mix = min(max(Date().timeIntervalSince(switchedAt) / 1.2, 0), 1)
        previous = (0..<4).map { color($0, mix) }
        shown = palette
        switchedAt = Date()
    }

    private func color(_ i: Int, _ mix: Double) -> RGB {
        let b = shown.isEmpty ? RGB(r: 0.14, g: 0.14, b: 0.14) : shown[min(i, shown.count - 1)]
        guard !previous.isEmpty, mix < 1 else { return b }
        return previous[min(i, previous.count - 1)].mixed(with: b, mix)
    }

    /// Sin carátula descargada: variantes del `bgColor` de Apple, llevado al brillo del fondo.
    private static func fallback(_ hex: String?) -> [RGB] {
        guard let base = RGB(hex: hex) else { return [RGB(r: 0.14, g: 0.14, b: 0.14)] }
        let peak = max(base.r, base.g, base.b, 0.01)
        let c = base.scaled(min(0.4 / peak, 1))
        return [c, c.scaled(0.8), c.scaled(1.25), c.scaled(0.6)]
    }
}
