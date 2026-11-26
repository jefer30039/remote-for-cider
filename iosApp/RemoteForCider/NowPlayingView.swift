import Shared
import SwiftUI

/// Hoja de reproducción de Apple Music en iOS 26 (iPhone). Se abre y se cierra con su propia
/// transición desde el miniplayer (ver `PlayerSheet`).
///
/// Las piezas van a distancias fijas del borde inferior, medidas en una captura de iPhone (centros
/// desde abajo, en pt): título 355, barra de progreso 289, controles 199, volumen 112 y la fila de
/// abajo 64. La carátula ocupa lo que queda arriba, a 24 pt de los lados y 44 pt sobre el título; así
/// nada se mueve con o sin portada animada, ni al pasar a la letra o la cola.
///
/// Texto, iconos y barras son blanco translúcido **sumado** a lo que hay detrás (`.plusLighter`),
/// como la vibrancia de iOS 26: conservan el tono del fondo. Medido con "Mercury" de Steve Lacy,
/// igual en los tres canales: título +192, artista +148, iconos +120, relleno de las barras +113,
/// tiempos +70, pista +46.
struct NowPlayingView: View {
    @Environment(RemoteStore.self) private var store
    @Environment(PlayerSheet.self) private var sheet

    var body: some View {
        let player = store.player
        let track = player?.track
        let playing = player?.isPlaying ?? false
        // Portada animada, en cuanto está su fotograma fijo (antes, la tarjeta de siempre).
        let hero = store.covers.current.flatMap { $0.image == nil ? nil : $0 }
        // El lector respeta el área segura (así da sus márgenes) y el contenido la cubre entera: se
        // coloca en coordenadas de pantalla, las mismas de la miniatura del miniplayer.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let w = geo.size.width + insets.leading + insets.trailing
            let h = geo.size.height + insets.top + insets.bottom
            let edge = M.edge
            let titleY = h - M.title
            // Fila del título: 44 pt de alto (lo que miden los botones).
            let artBottom = titleY - 22 - M.artGap
            let artTop = insets.top + 14
            let side = max(120, min(w - 2 * M.artSide, artBottom - artTop))
            let art = CGRect(x: (w - side) / 2, y: artBottom - side, width: side, height: side)
            // La hoja sube desde el borde superior del miniplayer.
            let distance = sheet.miniArt == .zero ? h * 0.85 : max(sheet.miniArt.minY - 9, 0)
            ZStack(alignment: .topLeading) {
                // La página de debajo se oscurece mientras la hoja sube.
                // Al bajarla con el dedo se aclara: se ve la página a la que se vuelve.
                Color.black.opacity(0.9 * min(max(sheet.progress, 0), 1) * (1 - 0.6 * min(max(sheet.drag / h, 0), 1)))
                    .allowsHitTesting(false)

                // Esquinas de la pantalla mientras sube o baja con el dedo; abierta, sin esquinas.
                let corner = max(M.screenCorner * (1 - min(max(sheet.progress, 0), 1)), min(sheet.drag * 0.8, M.screenCorner))
                if hero == nil {
                    ColorField(artwork: track?.artwork)
                        .frame(width: w, height: h)
                        .clipShape(RoundedRectangle(cornerRadius: corner, style: .continuous))
                        .modifier(SheetFade(progress: sheet.progress, drag: sheet.drag, distance: distance, from: 0, to: 0.5))
                        .transition(.opacity)
                }
                if let hero {
                    // Con portada animada, el vídeo es la carátula: vuela entero desde la miniatura.
                    let squareTop = CGFloat(hero.squareTop ?? 1.0 / 6) * w
                    ZStack(alignment: .topLeading) {
                        HeroArtwork(hero: hero, size: CGSize(width: w, height: h))
                        ZStack {
                            CoverImage(artwork: track?.artwork, pixels: 120, corner: 0)
                            CoverImage(artwork: track?.artwork, pixels: 1200, corner: 0, placeholder: false)
                        }
                        .frame(width: w, height: w)
                        .offset(y: squareTop)
                        .modifier(HeroCoverFade(progress: sheet.progress))
                    }
                    .modifier(HeroFlight(
                        progress: sheet.progress, drag: sheet.drag, from: sheet.miniArt,
                        size: CGSize(width: w, height: h), squareTop: squareTop, fromCorner: MiniPlayer.artCorner
                    ))
                    .allowsHitTesting(false)
                    .transition(.opacity)
                }

                ZStack(alignment: .topLeading) {
                    Capsule()
                        .fill(.white.opacity(0.25))
                        .frame(width: 36, height: 5)
                        .blendMode(.plusLighter)
                        .position(x: w / 2, y: max(insets.top - 8, 10))

                    // El "…" acaba en el margen: la fila va 7,5 pt más allá (el círculo de 31 pt va centrado en 44).
                    TitleRow(player: player)
                        .frame(width: w - 2 * edge + 7.5, height: 44)
                        .position(x: edge + (w - 2 * edge + 7.5) / 2, y: titleY)

                    // La barra va centrada en los 44 pt de arriba y los tiempos debajo: la caja mide 50 pt
                    // y el centro de la barra queda 3 pt por encima del de la caja.
                    TimelineView(.periodic(from: .now, by: 0.25)) { _ in
                        ScrubBar(
                            position: player?.progress.positionNow() ?? 0,
                            duration: player?.progress.durationSec ?? 0
                        ) { store.seek(to: $0) }
                    }
                    .frame(width: w - 2 * edge, height: 50)
                    .position(x: w / 2, y: h - M.progress + 3)

                    transport(playing: playing, width: w)
                        .position(x: w / 2, y: h - M.controls)

                    VolumeRow(volume: player?.volume ?? 0) { store.setVolume($0) }
                        .frame(width: w - 2 * edge, height: 44)
                        .position(x: w / 2, y: h - M.volume)

                    bottomRow(width: w)
                        .position(x: w / 2, y: h - M.bottom)
                }
                .frame(width: w, height: h)
                .modifier(SheetFade(progress: sheet.progress, drag: sheet.drag, distance: distance))

                // La carátula vuela desde la miniatura del miniplayer; en pausa se encoge a 0,78 con
                // un muelle (iOS). Debajo, la de 120 px del miniplayer mientras carga la grande.
                if hero == nil { ZStack {
                    CoverImage(artwork: track?.artwork, pixels: 120, corner: 0)
                    CoverImage(artwork: track?.artwork, pixels: 1200, corner: 0, placeholder: false)
                }
                .modifier(ArtFlight(
                    progress: sheet.progress, scale: playing ? 1 : 0.78, drag: sheet.drag,
                    from: sheet.miniArt, to: art, fromCorner: MiniPlayer.artCorner, toCorner: 10
                ))
                .animation(.spring(response: 0.5, dampingFraction: 0.72), value: playing)
                .allowsHitTesting(false)
                .transition(.opacity) }
            }
            .animation(.easeInOut(duration: 0.4), value: hero?.video)
            .frame(width: w, height: h)
            .contentShape(Rectangle())
            // Deslizar hacia abajo cierra: la hoja entera baja con el dedo y, al soltar, decide por la
            // velocidad proyectada (como iOS) si se recoge en el miniplayer o vuelve arriba.
            .gesture(
                DragGesture(minimumDistance: 12, coordinateSpace: .global)
                    .onChanged { v in
                        guard sheet.presented, sheet.progress == 1 || sheet.drag != 0 else { return }
                        sheet.follow(v.translation.height)
                    }
                    .onEnded { v in
                        let projected = max(v.predictedEndTranslation.height, v.translation.height)
                        if projected > h * 0.3 {
                            sheet.close(animation: .smooth(duration: 0.35))
                        } else {
                            sheet.settle()
                        }
                    }
            )
            .offset(x: -insets.leading, y: -insets.top)
        }
        .onAppear { sheet.appeared() }
        .foregroundStyle(.white)
        .presentationBackground(.clear)
        // La hoja siempre es oscura: barra de estado y menús en blanco.
        .preferredColorScheme(.dark)
    }

    /// Anterior · reproducir/pausa · siguiente, centros separados 104 pt.
    private func transport(playing: Bool, width: CGFloat) -> some View {
        ZStack {
            Button { store.previous() } label: {
                Image(systemName: "backward.fill").font(.system(size: 30))
                    .frame(width: 80, height: 80).contentShape(Circle())
            }
            .offset(x: -M.skipOffset)
            .accessibilityLabel("Previous")
            Button { store.togglePlay() } label: {
                Image(systemName: playing ? "pause.fill" : "play.fill")
                    .font(.system(size: 42))
                    .contentTransition(.symbolEffect(.replace))
                    .frame(width: 80, height: 80).contentShape(Circle())
            }
            .accessibilityLabel(playing ? Text("Pause") : Text("Play"))
            .accessibilityIdentifier("playPause")
            Button { store.next() } label: {
                Image(systemName: "forward.fill").font(.system(size: 30))
                    .frame(width: 80, height: 80).contentShape(Circle())
            }
            .offset(x: M.skipOffset)
            .accessibilityLabel("Next")
        }
        .buttonStyle(.plain)
        .frame(width: width, height: 80)
    }

    /// Letra · equipo · cola, centros a 111 pt.
    private func bottomRow(width: CGFloat) -> some View {
        ZStack {
            SheetIcon("quote.bubble", label: "Lyrics") {}
                .offset(x: -M.bottomSpacing)
            SheetIcon("laptopcomputer", label: "Computer") {}
            SheetIcon("list.bullet", label: "Up Next") {}
                .offset(x: M.bottomSpacing)
        }
        .frame(width: width, height: 44)
    }

    /// Medidas (pt). Centros desde el borde inferior de la pantalla.
    enum M {
        static let edge: CGFloat = 33
        static let artSide: CGFloat = 24
        static let artGap: CGFloat = 44
        static let title: CGFloat = 355
        static let progress: CGFloat = 289
        static let timesBelow: CGFloat = 21
        static let controls: CGFloat = 199
        static let skipOffset: CGFloat = 104
        static let volume: CGFloat = 112
        static let bottom: CGFloat = 64
        static let bottomSpacing: CGFloat = 111
        /// Radio de las esquinas de la pantalla del iPhone (aprox.): la hoja las lleva al moverse.
        static let screenCorner: CGFloat = 50
    }
}

// MARK: - Vibrancia

extension View {
    /// Blanco con opacidad `alpha` sumado a lo que hay detrás (vibrancia de la hoja de iOS 26).
    func vibrant(_ alpha: Double) -> some View {
        foregroundStyle(.white.opacity(alpha)).blendMode(.plusLighter)
    }
}

private enum Vibrancy {
    static let title = 0.75
    static let artist = 0.58
    static let icon = 0.47
    static let fill = 0.32
    static let track = 0.18
    static let time = 0.275
}

// MARK: - Título

private struct TitleRow: View {
    let player: PlayerState?

    var body: some View {
        let track = player?.track
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 6) {
                    Text(track?.name ?? " ")
                        .font(.system(size: 20, weight: .semibold))
                        .lineLimit(1)
                        .vibrant(Vibrancy.title)
                    if track?.isExplicit == true {
                        // El cuadro suma +46 y la letra queda recortada (se ve el fondo), como iOS.
                        Image(systemName: "e.square.fill")
                            .font(.system(size: 13))
                            .vibrant(Vibrancy.track)
                            .accessibilityLabel("Explicit")
                    }
                }
                .frame(height: 24)
                Text(track?.artistName ?? " ")
                    .font(.system(size: 20))
                    .lineLimit(1)
                    .vibrant(Vibrancy.artist)
                    .frame(height: 24)
            }
            Spacer(minLength: 0)
            MoreMenu(player: player)
        }
    }
}

/// "…": favorita, biblioteca y aleatorio (Apple Music deja solo este botón junto al título).
private struct MoreMenu: View {
    @Environment(RemoteStore.self) private var store
    let player: PlayerState?

    var body: some View {
        let loved = player?.inFavorites?.boolValue == true
        Menu {
            Button(loved ? LocalizedStringKey("Remove from Favorites") : LocalizedStringKey("Favorite"), systemImage: loved ? "star.slash" : "star") {
                store.toggleLove()
            }
            if let inLibrary = player?.inLibrary?.boolValue {
                if inLibrary {
                    Button("Remove from Library", systemImage: "trash", role: .destructive) { store.toggleInLibrary() }
                } else {
                    Button("Add to Library", systemImage: "plus") { store.toggleInLibrary() }
                }
            }
            Button(player?.shuffle == true ? LocalizedStringKey("Turn Off Shuffle") : LocalizedStringKey("Turn On Shuffle"), systemImage: "shuffle") {
                store.toggleShuffle()
            }
        } label: {
            Image(systemName: "ellipsis")
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: 31, height: 31)
                .glassEffect(.clear.interactive(), in: .circle)
                .frame(width: 44, height: 44)
                .contentShape(Circle())
        }
        .disabled(player?.track == nil)
        .accessibilityLabel("More Options")
    }
}

// MARK: - Barras

/// Pista fina de iOS: sin pulgar, se ensancha mientras se arrastra. El relleno va encima de la pista
/// en la misma capa y la capa entera se suma al fondo (0,32 + 0,18 · 0,68 = 0,44).
private struct ThinTrack: View {
    let fraction: Double
    let active: Bool

    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .leading) {
                Capsule().fill(.white.opacity(Vibrancy.track))
                Rectangle().fill(.white.opacity(active ? 0.6 : Vibrancy.fill))
                    .frame(width: g.size.width * min(max(fraction, 0), 1))
            }
            .clipShape(Capsule())
            .compositingGroup()
            .blendMode(.plusLighter)
        }
        .frame(height: active ? 11 : 7)
    }
}

/// Barra de progreso de Apple Music con los tiempos 21 pt por debajo de su centro.
struct ScrubBar: View {
    let position: Double
    let duration: Double
    let onSeek: (Double) -> Void
    @State private var dragging: Double?

    var body: some View {
        let shown = dragging ?? position
        VStack(spacing: 0) {
            GeometryReader { g in
                ThinTrack(fraction: duration > 0 ? shown / duration : 0, active: dragging != nil)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { v in
                                dragging = duration * min(max(v.location.x / max(g.size.width, 1), 0), 1)
                            }
                            .onEnded { _ in
                                if let d = dragging { onSeek(d) }
                                dragging = nil
                            }
                    )
            }
            .frame(height: 44)
            // Centro de los tiempos 21 pt bajo el de la barra: 44 − 8 + 7 = 22 + 21.
            HStack {
                Text(formatTime(shown))
                Spacer()
                Text("\u{2212}" + formatTime(max(0, duration - shown)))
            }
            .font(.system(size: 11, weight: .medium).monospacedDigit())
            .vibrant(Vibrancy.time)
            .frame(height: 14)
            .padding(.top, -8)
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.8), value: dragging == nil)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Playback position")
        .accessibilityValue("\(formatTime(shown)) / \(formatTime(duration))")
    }
}

/// Volumen de Cider (no el del iPhone): altavoz pequeño, barra desde 24 pt del margen y altavoz
/// grande, como la fila de volumen de Apple Music.
private struct VolumeRow: View {
    let volume: Double
    let onChange: (Double) -> Void
    @State private var dragging: Double?

    var body: some View {
        HStack(spacing: 0) {
            Image(systemName: "speaker.fill")
                .font(.system(size: 13))
                .frame(width: 20)
                .offset(x: -3.7)
                .vibrant(Vibrancy.icon)
            GeometryReader { g in
                ThinTrack(fraction: dragging ?? volume, active: dragging != nil)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { v in
                                let value = min(max(v.location.x / max(g.size.width, 1), 0), 1)
                                dragging = value
                                onChange(value)
                            }
                            .onEnded { _ in dragging = nil }
                    )
            }
            .padding(.leading, 4)
            .padding(.trailing, 13)
            Image(systemName: "speaker.wave.3.fill")
                .font(.system(size: 13))
                .frame(width: 19)
                .vibrant(Vibrancy.icon)
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.8), value: dragging == nil)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Volume")
        .accessibilityValue("\(Int((volume * 100).rounded())) %")
    }
}

/// Icono de la fila de abajo (letra, equipo, cola).
private struct SheetIcon: View {
    let symbol: String
    let label: LocalizedStringKey
    let action: () -> Void

    init(_ symbol: String, label: LocalizedStringKey, action: @escaping () -> Void) {
        self.symbol = symbol
        self.label = label
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 20))
                .vibrant(Vibrancy.icon)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}
