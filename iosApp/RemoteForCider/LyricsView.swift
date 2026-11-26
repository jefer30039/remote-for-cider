import Shared
import SwiftUI

// Letra de la hoja de reproducción, como la de Android (LyricsView.kt) y la de Cider: línea activa
// rellenándose de blanco sílaba a sílaba, la lista que sube con un muelle sin rebote, las líneas
// pasadas que se desvanecen desenfocándose y las siguientes más tenues cuanto más lejos. Efectos de
// Cider 4.0.9.1 (decisión del usuario: los de Cider, no los de AMLL): puntos en las pausas ≥ 5 s,
// sílabas que se alzan 0,05 em al cantarse y brillo letra a letra en las sostenidas (≥ 1 s).

// MARK: - Carga

enum LyricsLoad {
    case loading
    case missing
    case ready(Lyrics)
}

/// Letras ya pedidas (solo las encontradas: "sin letra" puede ser un fallo de red).
@MainActor
enum LyricsCache {
    private static var cache: [String: Lyrics] = [:]

    static func peek(_ id: String) -> Lyrics? { cache[id] }

    /// Por sílaba si Apple la tiene; si no, por línea.
    static func load(_ api: CiderApi, _ id: String) async -> Lyrics? {
        if let hit = cache[id] { return hit }
        var lyrics: Lyrics? = try? await api.lyrics(catalogId: id, syllable: true)
        if lyrics == nil { lyrics = try? await api.lyrics(catalogId: id, syllable: false) }
        if let lyrics { cache[id] = lyrics }
        return lyrics
    }
}

/// Medidas de la letra (Apple Music en teléfono: 34 pt negrita, renglón de 40, 34 pt entre líneas).
enum LyricsMetrics {
    static let fontSize: CGFloat = 34
    static let font = Font.system(size: fontSize, weight: .bold)
    static let side: CGFloat = 33
    static let lineGap: CGFloat = 34
    /// La lista sube un poco antes de que empiece la línea.
    static let lead = 0.3
    static let inactiveScale = 0.92
    /// Parte aún no cantada de la línea activa.
    static let unsung = 0.45
    static let wipeEdge: CGFloat = 10
    static let blurUnit: CGFloat = 1.4
    /// Pausa mínima con puntos (Cider: ≥ 5 s).
    static let interludeMin = 5.0
}

struct LyricsPanel: View {
    @Environment(RemoteStore.self) private var store
    let player: PlayerState?
    /// La parte de arriba de la línea activa queda a esta distancia del borde de la vista.
    let anchorTop: CGFloat
    /// Pausar el seguimiento (el usuario arrastra): lo lleva la vista de dentro.
    @State private var load: LyricsLoad = .loading

    var body: some View {
        let id = player?.track?.catalogId
        ZStack {
            switch load {
            case .loading:
                ProgressView().tint(.white.opacity(0.6))
            case .missing:
                Text("No lyrics available")
                    .font(.headline)
                    .foregroundStyle(.white.opacity(0.62))
            case .ready(let lyrics):
                if lyrics.synced, let player {
                    SyncedLyrics(lyrics: lyrics, player: player, anchorTop: anchorTop) { store.seek(to: $0) }
                } else {
                    PlainLyrics(lyrics: lyrics, anchorTop: anchorTop)
                }
            }
        }
        .task(id: id) {
            guard let id, let api = store.api else { load = .missing; return }
            if let hit = LyricsCache.peek(id) { load = .ready(hit); return }
            load = .loading
            if let lyrics = await LyricsCache.load(api, id) { load = .ready(lyrics) } else { load = .missing }
        }
    }
}

/// Letra sin tiempos: lista desplazable, todo en blanco, empezando donde va la línea activa de las sincronizadas.
private struct PlainLyrics: View {
    let lyrics: Lyrics
    let anchorTop: CGFloat

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ForEach(Array(lyrics.lines.enumerated()), id: \.offset) { _, line in
                    Text(line.text)
                        .font(LyricsMetrics.font)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(.horizontal, LyricsMetrics.side)
            .padding(.top, anchorTop)
            .padding(.bottom, 240)
        }
        .scrollIndicators(.hidden)
    }
}

// MARK: - Elementos (líneas y pausas)

private enum LyricItem {
    case line(LyricLine)
    /// Pausa de `start` (fin de lo cantado antes; 0 en la introducción) a `end` (cuando sube la línea siguiente).
    case gap(start: Double, end: Double)

    var activeFrom: Double {
        switch self {
        case .line(let l): l.startSec - LyricsMetrics.lead
        case .gap(let start, _): start
        }
    }

    var isGap: Bool {
        switch self {
        case .gap: true
        case .line: false
        }
    }
}

private func lyricItems(_ lyrics: Lyrics) -> [LyricItem] {
    var out: [LyricItem] = []
    var sungUntil = 0.0
    for (i, l) in lyrics.lines.enumerated() {
        // Desde el mayor final de todo lo anterior: las líneas se solapan.
        let from = i == 0 ? 0 : sungUntil
        let until = l.startSec - LyricsMetrics.lead
        if l.startSec - from >= LyricsMetrics.interludeMin, until > from { out.append(.gap(start: from, end: until)) }
        out.append(.line(l))
        sungUntil = max(sungUntil, l.endSec)
    }
    return out
}

// MARK: - Letra sincronizada

/// Todo sale de una posición de foco continua (`focus`, en "rango": las pausas plegadas no cuentan)
/// que se anima con un muelle sin rebote al cambiar de elemento: la colocación (`LyricsColumn`) y el
/// aspecto de cada línea (`LineFx`) la leen animada. El barrido de la línea activa se dibuja a cada
/// fotograma con un `TextRenderer`, solo en esa línea.
private struct SyncedLyrics: View {
    let lyrics: Lyrics
    let player: PlayerState
    let anchorTop: CGFloat
    let onSeek: (Double) -> Void

    @State private var items: [LyricItem] = []
    @State private var active = 0
    @State private var focus = 0.0
    /// Arrastrando: se deja de seguir la canción (el foco se queda quieto) y la lista sigue al dedo.
    @State private var userMode = false
    @State private var manual: CGFloat = 0
    @State private var dragStart: CGFloat = 0
    @State private var lastTouch = Date()

    private static let follow = Animation.spring(response: 0.6, dampingFraction: 1)

    var body: some View {
        let progress = player.progress
        let ranks = Self.ranks(items, active: active)
        LyricsColumn(focus: focus, manual: manual, anchorTop: anchorTop, ranks: ranks) {
            ForEach(Array(items.enumerated()), id: \.offset) { i, item in
                let d = (i < ranks.count ? ranks[i] : 0) - focus
                switch item {
                case .line(let line):
                    LyricLineView(line: line, phase: i == active ? 0 : (i < active ? -1 : 1), progress: progress)
                        .modifier(LineFx(distance: d, user: userMode))
                        .contentShape(Rectangle())
                        .onTapGesture { onSeek(line.startSec) }
                case .gap(let start, let end):
                    InterludeDots(start: start, end: end, progress: progress)
                        .frame(height: i == active ? 40 : 0)
                        .clipped()
                }
            }
        }
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 8)
                .onChanged { v in
                    if !userMode { userMode = true; dragStart = manual }
                    manual = dragStart + v.translation.height
                    lastTouch = Date()
                }
                .onEnded { _ in lastTouch = Date() }
        )
        // A los 3 s sin tocar vuelve a seguir la canción: el foco salta a la línea que suena y la lista
        // vuelve a su sitio con el mismo muelle.
        .task(id: lastTouch) {
            guard userMode else { return }
            try? await Task.sleep(for: .seconds(3))
            guard !Task.isCancelled else { return }
            let now = Self.index(items, at: player.progress.positionNow())
            withAnimation(Self.follow) {
                userMode = false
                manual = 0
                active = now
                focus = Self.ranks(items, active: now)[safe: now] ?? 0
            }
        }
        // El elemento activo: se comprueba 20 veces por segundo (el barrido va aparte, a cada fotograma).
        .task(id: ObjectIdentifier(lyrics)) {
            items = lyricItems(lyrics)
            active = Self.index(items, at: player.progress.positionNow())
            focus = Self.ranks(items, active: active)[safe: active] ?? 0
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(50))
                guard !userMode else { continue }
                let now = Self.index(items, at: player.progress.positionNow())
                if now != active {
                    withAnimation(Self.follow) {
                        active = now
                        focus = Self.ranks(items, active: now)[safe: now] ?? 0
                    }
                }
            }
        }
    }

    private static func index(_ items: [LyricItem], at t: Double) -> Int {
        var k = 0
        for (i, item) in items.enumerated() {
            if item.activeFrom <= t { k = i } else { break }
        }
        return k
    }

    /// Rango de cada elemento: las pausas solo cuentan si están desplegadas (la activa).
    private static func ranks(_ items: [LyricItem], active: Int) -> [Double] {
        var acc = 0.0
        return items.enumerated().map { i, item in
            let r = acc
            acc += item.isGap && i != active ? 0 : 1
            return r
        }
    }
}

extension Array {
    subscript(safe i: Int) -> Element? { indices.contains(i) ? self[i] : nil }
}

private func scaleFor(_ d: Double) -> Double { 1 - (1 - LyricsMetrics.inactiveScale) * min(abs(d), 1) }

/// Activa entera; las pasadas se desvanecen hasta desaparecer; las siguientes, más tenues con la distancia.
private func alphaFor(_ d: Double) -> Double {
    switch d {
    case ...(-1): 0
    case ..<0: 1 + d
    case ..<1: 1 - (1 - 0.36) * d
    case ..<2: 0.36 - 0.10 * (d - 1)
    case ..<3: 0.26 - 0.08 * (d - 2)
    default: 0.18
    }
}

/// Desenfoque (en unidades de 1,4 pt): fuerte en la que se va, creciente en las siguientes.
private func blurFor(_ d: Double) -> Double {
    d < 0 ? -d * 4 : min(max((d - 0.5) * 1.2, 0), 3.5)
}

/// Escala (desde arriba a la izquierda), opacidad y desenfoque de una línea según su distancia al foco.
private struct LineFx: ViewModifier, Animatable {
    var distance: Double
    let user: Bool

    var animatableData: Double {
        get { distance }
        set { distance = newValue }
    }

    func body(content: Content) -> some View {
        let d = distance
        content
            .scaleEffect(scaleFor(d), anchor: .topLeading)
            .opacity(user ? (abs(d) < 0.5 ? 1 : 0.5) : alphaFor(d))
            .blur(radius: user ? 0 : blurFor(d) * LyricsMetrics.blurUnit)
    }
}

/// Coloca las líneas una debajo de otra con su escala de ahora (la que crece empuja a las de abajo) y
/// deja la parte de arriba del foco a `anchorTop` del borde. Fuera de la vista no se coloca nada visible.
private struct LyricsColumn: Layout {
    var focus: Double
    var manual: CGFloat
    let anchorTop: CGFloat
    let ranks: [Double]

    var animatableData: AnimatablePair<Double, CGFloat> {
        get { AnimatablePair(focus, manual) }
        set { focus = newValue.first; manual = newValue.second }
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        proposal.replacingUnspecifiedDimensions(by: CGSize(width: 390, height: 800))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let width = max(bounds.width - 2 * LyricsMetrics.side, 0)
        let child = ProposedViewSize(width: width, height: nil)
        var tops: [CGFloat] = []
        var heights: [CGFloat] = []
        var y: CGFloat = 0
        for (i, sub) in subviews.enumerated() {
            let h = sub.sizeThatFits(child).height
            let s = CGFloat(scaleFor((i < ranks.count ? ranks[i] : 0) - focus))
            tops.append(y)
            heights.append(h)
            y += h > 0 ? (h + LyricsMetrics.lineGap) * s : 0
        }
        // Altura del foco (fraccionario): entre las dos líneas que lo rodean, interpolada por rango.
        var anchor: CGFloat = 0
        var prev: (rank: Double, top: CGFloat)?
        for i in tops.indices where heights[i] > 0 {
            let r = i < ranks.count ? ranks[i] : 0
            if r <= focus { prev = (r, tops[i]) } else {
                if let p = prev, r > p.rank {
                    anchor = p.top + (tops[i] - p.top) * CGFloat((focus - p.rank) / (r - p.rank))
                } else { anchor = tops[i] }
                prev = nil
                break
            }
        }
        if let p = prev { anchor = p.top }
        let offset = anchorTop - anchor + manual
        for (i, sub) in subviews.enumerated() {
            sub.place(at: CGPoint(x: bounds.minX + LyricsMetrics.side, y: bounds.minY + tops[i] + offset),
                      anchor: .topLeading, proposal: child)
        }
    }
}

// MARK: - Una línea

/// Atributo de cada sílaba en el `Text` de la línea: el `TextRenderer` sabe así a qué sílaba pertenece cada trozo.
private struct SyllableTag: TextAttribute {
    let index: Int
}

/// Una línea. Inactiva: texto blanco (la opacidad la pone `LineFx`); ya cantada, se queda alzada.
/// Activa: se redibuja a cada fotograma con `KaraokeRenderer`.
private struct LyricLineView: View {
    let line: LyricLine
    /// -1 pasada, 0 activa, 1 siguiente.
    let phase: Int
    let progress: Shared.Progress

    var body: some View {
        // Siempre la misma vista (si no, al cambiar de línea SwiftUI fundía la vieja en su sitio de antes
        // mientras la nueva ya se había movido: se veían dos líneas montadas). Solo la activa se redibuja.
        TimelineView(.animation(paused: phase != 0 || !progress.playing)) { _ in
            Self.text(line).textRenderer(KaraokeRenderer(line: line, time: progress.positionNow(), phase: phase))
        }
    }

    /// El texto armado con sus sílabas (mismas posiciones que en el analizador), cada una con su atributo.
    static func text(_ line: LyricLine) -> Text {
        let words = line.words
        let base: Text
        if words.isEmpty {
            base = Text(verbatim: line.text)
        } else {
            var t = Text(verbatim: "")
            for (k, w) in words.enumerated() {
                let piece = w.text + (w.spaceAfter && k < words.count - 1 ? " " : "")
                t = t + Text(verbatim: piece).customAttribute(SyllableTag(index: k))
            }
            base = t
        }
        return base
            .font(LyricsMetrics.font)
            .foregroundStyle(.white)
    }
}

/// Curvas de Cider: `ease-out` de CSS (alzado) y la del pico del brillo.
private func cubicBezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ x: Double) -> Double {
    // Bézier cúbica con extremos (0,0)–(1,1): se busca t tal que X(t) = x (Newton) y se devuelve Y(t).
    var t = x
    for _ in 0..<8 {
        let cx = 3 * x1, bx = 3 * (x2 - x1) - cx, ax = 1 - cx - bx
        let xt = ((ax * t + bx) * t + cx) * t - x
        let dx = (3 * ax * t + 2 * bx) * t + cx
        if abs(dx) < 1e-6 { break }
        t -= xt / dx
    }
    t = min(max(t, 0), 1)
    let cy = 3 * y1, by = 3 * (y2 - y1) - cy, ay = 1 - cy - by
    return ((ay * t + by) * t + cy) * t
}

private func liftEase(_ x: Double) -> Double { cubicBezier(0, 0, 0.58, 1, x) }
private func glowCurve(_ x: Double) -> Double {
    x < 0.5 ? cubicBezier(0.2, 0.4, 0.58, 1, x / 0.5) : 1 - cubicBezier(0.3, 0, 0.58, 1, (x - 0.5) / 0.5)
}

/// Barrido, alzado y brillo de la línea activa (Cider `fxSpecs`, con el brillo ajustado como en Android).
/// Cada sílaba es su propio trozo del texto (`SyllableTag`): se dibuja atenuada, alzada según lo que
/// lleva cantado, y encima en blanco recortada hasta donde va el canto con un borde suave. Las sílabas
/// de ≥ 1 s se dibujan letra a letra con la ola de escala y el resplandor.
private struct KaraokeRenderer: TextRenderer {
    let line: LyricLine
    let time: Double
    /// -1 pasada (alzada del todo), 0 activa, 1 siguiente (tal cual).
    let phase: Int

    var displayPadding: EdgeInsets {
        let p = LyricsMetrics.fontSize * 0.4
        return EdgeInsets(top: p, leading: p, bottom: p, trailing: p)
    }

    func draw(layout: Text.Layout, in ctx: inout GraphicsContext) {
        guard phase == 0 else {
            // Ya cantada: se queda alzada (Cider, `clp-cold-sung`) mientras se desvanece.
            var c = ctx
            if phase < 0, !line.words.isEmpty { c.translateBy(x: 0, y: -0.05 * LyricsMetrics.fontSize) }
            for row in layout { for run in row { c.draw(run) } }
            return
        }
        let t = time
        let words = line.words
        let started = t >= line.startSec && t <= line.endSec + 0.15
        let em = LyricsMetrics.fontSize
        let liftMax = 0.05 * em
        let lastText = words.last?.text ?? ""
        for row in layout {
            for run in row {
                // Línea sin sílabas: toda blanca mientras suena.
                guard let k = run[SyllableTag.self]?.index, k < words.count else {
                    var c = ctx
                    c.opacity = started ? 1 : LyricsMetrics.unsung
                    c.draw(run)
                    continue
                }
                let w = words[k]
                let dur = max(w.endSec - w.startSec, 0.01)
                let lift = liftMax * liftEase(min(max((t - w.startSec) / max(1, dur), 0), 1))
                let sung = started ? min(max((t - w.startSec) / dur, 0), 1) : 0
                let glow = Glow(word: w, last: !lastText.isEmpty && w.text.contains(lastText))
                var c = ctx
                c.translateBy(x: 0, y: -lift)
                // Atenuada.
                var dim = c
                dim.opacity = LyricsMetrics.unsung
                draw(run, in: &dim, glow: glow, time: t, em: em, halo: false)
                guard sung > 0 else { continue }
                // Cantada, hasta x con borde suave.
                let r = run.typographicBounds.rect
                var lit = c
                if sung < 1 {
                    let x = r.minX + r.width * sung
                    let e = LyricsMetrics.wipeEdge
                    let from = r.minX - em, to = x + e / 2
                    let solid = max(min((x - e / 2 - from) / (to - from), 1), 0)
                    lit.clipToLayer { layer in
                        layer.fill(
                            Path(CGRect(x: from, y: r.minY - em, width: to - from, height: r.height + 2 * em)),
                            with: .linearGradient(Gradient(stops: [.init(color: .white, location: 0),
                                                                   .init(color: .white, location: solid),
                                                                   .init(color: .clear, location: 1)]),
                                                  startPoint: CGPoint(x: from, y: 0), endPoint: CGPoint(x: to, y: 0))
                        )
                    }
                }
                draw(run, in: &lit, glow: glow, time: t, em: em, halo: true)
            }
        }
    }

    /// Una sílaba entera o, si su brillo está en marcha, letra a letra.
    private func draw(_ run: Text.Layout.Run, in ctx: inout GraphicsContext, glow: Glow?, time t: Double, em: CGFloat, halo: Bool) {
        guard let g = glow, g.animating(t) else { ctx.draw(run); return }
        let count = run.count
        for (i, slice) in run.enumerated() {
            let start = g.startMs + g.durMs / 2.5 / Double(count) * Double(i)
            let tMs = t * 1000
            let x = (tMs - start) / g.durMs
            let e = (0...1).contains(x) ? glowCurve(x) : 0
            let sx = (tMs - (start - 400)) / (g.durMs * 1.4)
            let float = (0...1).contains(sx) ? sin(sx * .pi) * 0.05 * em : 0
            let sc = 1 + 0.1 * g.amount * e
            let dx = -0.03 * g.amount * e * (Double(count) / 2 - Double(i)) * em
            let dy = -0.025 * g.amount * e * em
            let b = slice.typographicBounds.rect
            var c = ctx
            c.translateBy(x: dx + b.midX, y: dy - float + b.midY)
            c.scaleBy(x: sc, y: sc)
            c.translateBy(x: -b.midX, y: -b.midY)
            if halo, e > 0 {
                // Halo en dos capas: uno ancho (0,22 em, 70 %) y otro ceñido (0,07 em, 60 %).
                var wide = c
                wide.addFilter(.shadow(color: .white.opacity(min(g.glow * 0.7 * e, 1)), radius: 0.22 * em / 2))
                wide.draw(slice)
                var tight = c
                tight.addFilter(.shadow(color: .white.opacity(min(g.glow * 0.6 * e, 1)), radius: 0.07 * em / 2))
                tight.draw(slice)
            } else {
                c.draw(slice)
            }
        }
    }

    /// Brillo de una sílaba sostenida (≥ 1 s): intensidad de la ola y opacidad del halo.
    private struct Glow {
        let startMs: Double
        let durMs: Double
        let amount: Double
        let glow: Double

        init?(word w: LyricWord, last: Bool) {
            let d = (w.endSec - w.startSec) * 1000
            guard d >= 1000 else { return nil }
            var f = max(1000, d)
            var p = f / 2000
            p = p > 1 ? p.squareRoot() : p * p * p
            p *= 0.6
            if last { p *= 1.6; f *= 1.2 }
            startMs = w.startSec * 1000
            durMs = f
            amount = min(1.2, p)
            glow = min(0.6, (0.35 + 0.2 * min(max((d - 1000) / 1500, 0), 1)) * (last ? 1.1 : 1))
        }

        func animating(_ t: Double) -> Bool {
            let tMs = t * 1000
            return tMs > startMs - 400 && tMs < startMs + durMs * 1.4
        }
    }
}

// MARK: - Puntos de las pausas

/// Tres puntos durante una pausa (Cider `updateDots`): grupo a escala 0,7 que respira ±5 %, crece desde 0
/// en 2 s con salida exponencial y aparece entre 0,5 y 1 s; en los últimos 0,75 s encoge con un pequeño
/// rebote y en los últimos 0,375 s se desvanece. Cada punto pasa de 0,25 a 1 en su tercio de la pausa.
private struct InterludeDots: View {
    let start: Double
    let end: Double
    let progress: Shared.Progress

    var body: some View {
        TimelineView(.animation(paused: !progress.playing)) { _ in
            let n = progress.positionNow() - start
            Canvas { ctx, size in
                let total = max(end - start, 0.001)
                guard n > 0, n < total else { return }
                let r = total / ceil(total / 1.5)
                var scale = sin(1.5 * .pi - n / r * 2) / 20 + 1
                if n < 2 { scale *= expoOut(n / 2) }
                var alpha = n < 0.5 ? 0 : (n < 1 ? (n - 0.5) / 0.5 : 1)
                let left = total - n
                if left < 0.75 { scale *= 1 - easeInOutBack((0.75 - left) / 0.75 / 2) }
                if left < 0.375 { alpha *= min(max(left / 0.375, 0), 1) }
                scale = max(scale, 0) * 0.7
                guard scale > 0, alpha > 0 else { return }
                let em = LyricsMetrics.fontSize
                let dot = 0.34 * em, gap = 0.22 * em
                let groupW = 3 * dot + 2 * gap
                let x0 = -0.15 * em
                let cy = size.height / 2
                let s = max(total - 0.75, 0.001)
                var c = ctx
                c.translateBy(x: x0 + groupW / 2, y: cy)
                c.scaleBy(x: scale, y: scale)
                c.translateBy(x: -(x0 + groupW / 2), y: -cy)
                for k in 0..<3 {
                    let a = min(max((n - Double(k) * s / 3) * 3 / s * 0.75, 0.25), 1)
                    let cx = x0 + dot / 2 + Double(k) * (dot + gap)
                    c.fill(Path(ellipseIn: CGRect(x: cx - dot / 2, y: cy - dot / 2, width: dot, height: dot)),
                           with: .color(.white.opacity(alpha * a)))
                }
            }
        }
    }

    private func expoOut(_ x: Double) -> Double { x >= 1 ? 1 : 1 - pow(2, -10 * x) }

    private func easeInOutBack(_ x: Double) -> Double {
        let c = 1.70158 * 1.525
        return x < 0.5
            ? (2 * x) * (2 * x) * ((c + 1) * 2 * x - c) / 2
            : ((2 * x - 2) * (2 * x - 2) * ((c + 1) * (x * 2 - 2) + c) + 2) / 2
    }
}
