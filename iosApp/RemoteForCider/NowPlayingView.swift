import Shared
import SwiftUI
import UIKit

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

    enum Mode { case artwork, lyrics, queue }
    /// `-demoLyrics YES` / `-demoQueue YES`: la hoja abre en la letra o la cola (capturas de CI).
    @State private var mode: Mode = UserDefaults.standard.bool(forKey: "demoLyrics") ? .lyrics
        : UserDefaults.standard.bool(forKey: "demoQueue") ? .queue : .artwork
    /// Con la letra y sonando, los controles se repliegan a los 4 s sin tocar nada (Apple Music).
    @State private var controlsHidden = false
    @State private var touches = 0
    /// "Dolby Atmos", "Lossless" o "Hi-Res Lossless" según lo que Cider dice que suena.
    @State private var quality: String?

    private static let modeChange = Animation.spring(response: 0.5, dampingFraction: 0.86)

    var body: some View {
        let player = store.player
        let track = player?.track
        let playing = player?.isPlaying ?? false
        let lyrics = mode == .lyrics
        // Letra y cola comparten la cabecera pequeña (carátula de 70 pt, título y "…").
        let compact = mode != .artwork
        // Portada animada, en cuanto está su fotograma fijo (antes, la tarjeta de siempre). Con la
        // letra no: el vídeo se funde y queda el fondo de colores con la carátula en la cabecera.
        let hero = compact ? nil : store.covers.current.flatMap { $0.image == nil ? nil : $0 }
        let hideControls = lyrics && controlsHidden
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
            let card = CGRect(x: (w - side) / 2, y: artBottom - side, width: side, height: side)
            // Cabecera de la letra (Android, medida en Apple Music): carátula de 70 pt a 28 pt del borde,
            // 30 pt bajo la barra de estado; la cabecera mide 120 pt.
            let header = CGRect(x: 28, y: insets.top + 30, width: 70, height: 70)
            let art = compact ? header : card
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

                if lyrics {
                    // La letra sube por debajo de la cabecera y se desvanece al llegar a ella; la parte de
                    // arriba de la línea activa queda 34 pt bajo la cabecera.
                    let bottom = hideControls ? h - 40 : h - M.progress - 34
                    LyricsPanel(player: player, anchorTop: insets.top + 154)
                        .frame(width: w, height: max(bottom, 1))
                        .mask {
                            LinearGradient(stops: [.init(color: .clear, location: (insets.top + 72) / max(bottom, 1)),
                                                   .init(color: .black, location: (insets.top + 134) / max(bottom, 1)),
                                                   .init(color: .black, location: 0.9),
                                                   .init(color: .clear, location: 1)],
                                           startPoint: .top, endPoint: .bottom)
                        }
                        .modifier(SheetFade(progress: sheet.progress, drag: sheet.drag, distance: distance))
                        .transition(.opacity)
                }

                if mode == .queue {
                    // La cola pasa por debajo de la cabecera y se desvanece al llegar a ella.
                    let bottom = h - M.progress - 34
                    QueuePanel(player: player, top: insets.top + 120)
                        .frame(width: w, height: max(bottom, 1))
                        .mask {
                            LinearGradient(stops: [.init(color: .clear, location: (insets.top + 96) / max(bottom, 1)),
                                                   .init(color: .black, location: (insets.top + 122) / max(bottom, 1)),
                                                   .init(color: .black, location: 0.94),
                                                   .init(color: .clear, location: 1)],
                                           startPoint: .top, endPoint: .bottom)
                        }
                        .modifier(SheetFade(progress: sheet.progress, drag: sheet.drag, distance: distance))
                        .transition(.opacity)
                }

                if compact {
                    // Título, artista y "…" junto a la carátula pequeña; la franja entera es de la
                    // cabecera (deslizar ahí cierra la hoja, no mueve la letra ni la cola).
                    ZStack(alignment: .topLeading) {
                        Color.clear.contentShape(Rectangle())
                            .frame(width: w, height: insets.top + 120)
                            .gesture(closeDrag(height: h))
                        HStack(spacing: 0) {
                            Color.clear
                                .frame(width: 70, height: 70)
                                .contentShape(Rectangle())
                                .onTapGesture { withAnimation(Self.modeChange) { mode = .artwork } }
                                .accessibilityLabel("Show Artwork")
                            VStack(alignment: .leading, spacing: 2) {
                                Text(track?.name ?? " ")
                                    .font(.system(size: 18, weight: .semibold))
                                    .lineLimit(1)
                                    .vibrant(Vibrancy.title)
                                Text(track?.artistName ?? " ")
                                    .font(.system(size: 17))
                                    .lineLimit(1)
                                    .vibrant(Vibrancy.artist)
                            }
                            .padding(.horizontal, 14)
                            Spacer(minLength: 0)
                            MoreMenu(player: player)
                        }
                        .padding(.leading, 28)
                        .padding(.trailing, 24 - 6.5)
                        .frame(width: w, height: 70)
                        .offset(y: insets.top + 30)
                    }
                    .modifier(SheetFade(progress: sheet.progress, drag: sheet.drag, distance: distance))
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
                        .opacity(compact ? 0 : 1)
                        .allowsHitTesting(!compact)

                    Group {

                    // La barra va centrada en los 44 pt de arriba y los tiempos debajo: la caja mide 50 pt
                    // y el centro de la barra queda 3 pt por encima del de la caja.
                    TimelineView(.periodic(from: .now, by: 0.25)) { _ in
                        ScrubBar(
                            position: player?.progress.positionNow() ?? 0,
                            duration: player?.progress.durationSec ?? 0,
                            badge: quality
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
                    .opacity(hideControls ? 0 : 1)
                    .offset(y: hideControls ? 30 : 0)
                    .allowsHitTesting(!hideControls)
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
                    progress: sheet.progress, scale: playing || compact ? 1 : 0.78, drag: sheet.drag,
                    from: sheet.miniArt, to: art, fromCorner: MiniPlayer.artCorner, toCorner: compact ? 6 : 10
                ))
                .animation(.spring(response: 0.5, dampingFraction: 0.72), value: playing)
                .allowsHitTesting(false)
                .transition(.opacity) }
            }
            .animation(.easeInOut(duration: 0.4), value: hero?.video)
            .animation(.easeInOut(duration: 0.35), value: hideControls)
            .frame(width: w, height: h)
            .contentShape(Rectangle())
            // Cualquier toque devuelve los controles (sin quitárselo a quien lo reciba).
            .simultaneousGesture(TapGesture().onEnded { touches += 1 })
            // Deslizar hacia abajo cierra. Con la cola, solo desde la cabecera (la lista se desplaza y
            // sus filas se arrastran): aquí el gesto se apaga y lo lleva la franja de la cabecera.
            .gesture(closeDrag(height: h), including: mode == .queue ? .subviews : .all)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .onAppear { sheet.appeared() }
        // Se vuelve a leer cada 5 s: también cambia al activar o quitar Atmos, no solo con la canción.
        .task(id: store.player?.track?.playParams?.id) {
            while !Task.isCancelled {
                if let api = store.api, let q = try? await api.audioQuality() { quality = q.badge }
                try? await Task.sleep(for: .seconds(5))
            }
        }
        .task(id: "\(mode)-\(store.player?.isPlaying == true)-\(touches)") {
            controlsHidden = false
            guard mode == .lyrics, store.player?.isPlaying == true else { return }
            try? await Task.sleep(for: .seconds(4))
            if !Task.isCancelled { controlsHidden = true }
        }
        // Con la letra a la vista la pantalla no se apaga.
        .onChange(of: mode) { _, m in UIApplication.shared.isIdleTimerDisabled = m == .lyrics }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
        .foregroundStyle(.white)
        .presentationBackground(.clear)
        // La hoja siempre es oscura: barra de estado y menús en blanco.
        .preferredColorScheme(.dark)
    }

    /// La hoja entera baja con el dedo y, al soltar, decide por la velocidad proyectada (como iOS) si
    /// se recoge en el miniplayer o vuelve arriba.
    private func closeDrag(height h: CGFloat) -> some Gesture {
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
            SheetIcon(mode == .lyrics ? "quote.bubble.fill" : "quote.bubble", label: "Lyrics", active: mode == .lyrics) {
                withAnimation(Self.modeChange) { mode = mode == .lyrics ? .artwork : .lyrics }
            }
            .offset(x: -M.bottomSpacing)
            .accessibilityIdentifier("lyricsButton")
            ComputerButton(label: AnyView(
                Image(systemName: "laptopcomputer")
                    .font(.system(size: 20))
                    .vibrant(Vibrancy.icon)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            ))
            SheetIcon("list.bullet", label: "Up Next", active: mode == .queue) {
                withAnimation(Self.modeChange) { mode = mode == .queue ? .artwork : .queue }
            }
            .overlay(alignment: .topTrailing) { queueBadge }
            .offset(x: M.bottomSpacing)
            .accessibilityIdentifier("queueButton")
        }
        .frame(width: width, height: 44)
    }

    /// Insignia de repetir/aleatorio sobre el icono de la cola (Apple Music).
    @ViewBuilder private var queueBadge: some View {
        let p = store.player
        let symbol: String? = p?.repeatMode == .one ? "repeat.1" : p.map { $0.repeatMode != Shared.RepeatMode.none } == true ? "repeat" : p?.shuffle == true ? "shuffle" : nil
        if let symbol {
            Image(systemName: symbol)
                .font(.system(size: 7, weight: .bold))
                .frame(width: 13, height: 13)
                .background(Circle().fill(.white.opacity(0.85)))
                .foregroundStyle(.black.opacity(0.7))
                .offset(x: -6, y: 8)
                .allowsHitTesting(false)
        }
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
    @Environment(RemoteStore.self) private var store
    @Environment(\.openRoute) private var openRoute
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
                        .onTapGesture { go(album: true) }
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
                    .onTapGesture { go(album: false) }
            }
            Spacer(minLength: 0)
            MoreMenu(player: player)
        }
    }

    /// Tocar el título abre el álbum y tocar el artista, su página (Apple Music).
    private func go(album: Bool) {
        guard let api = store.api, let id = player?.track?.playParams?.id else { return }
        Task {
            let links = await SongLinksCache.get(api, id)
            if let route = (album ? links?.album : links?.artist)?.route { openRoute(route) }
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
            Menu {
                Button("15 Minutes") { store.sleep.set(minutes: 15, store: store) }
                Button("30 Minutes") { store.sleep.set(minutes: 30, store: store) }
                Button("45 Minutes") { store.sleep.set(minutes: 45, store: store) }
                Button("1 Hour") { store.sleep.set(minutes: 60, store: store) }
                Button("When Current Song Ends") { store.sleep.set(minutes: 0, store: store) }
                if store.sleep.active {
                    Button("Turn Off Timer", role: .destructive) { store.sleep.set(minutes: nil, store: store) }
                }
            } label: {
                if let end = store.sleep.endsAt {
                    Label { Text("Sleep Timer · \(end, style: .timer)") } icon: { Image(systemName: "moon.zzz.fill") }
                } else if store.sleep.endOfTrack {
                    Label("Sleep Timer · End of Song", systemImage: "moon.zzz.fill")
                } else {
                    Label("Sleep Timer", systemImage: "moon.zzz")
                }
            }
            if let url = player?.track?.url.flatMap(URL.init(string:)) {
                ShareLink(item: url) { Label("Share Song…", systemImage: "square.and.arrow.up") }
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
    var badge: String? = nil
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
            .overlay { QualityBadge(badge: badge) }
            .frame(height: 14)
            .padding(.top, -8)
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.8), value: dragging == nil)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Playback position")
        .accessibilityValue("\(formatTime(shown)) / \(formatTime(duration))")
    }
}

/// Insignia de calidad entre los tiempos, como iOS 26: el logotipo de Dolby Atmos sin recuadro en el
/// color de los tiempos (87,3 pt de ancho); "Lossless" en un recuadro translúcido con la onda.
private struct QualityBadge: View {
    let badge: String?

    var body: some View {
        if let badge {
            if badge == "Dolby Atmos" {
                Image("DolbyAtmos").resizable().scaledToFit()
                    .frame(width: 87.3, height: 12.33)
                    .vibrant(Vibrancy.time)
                    .accessibilityLabel("Dolby Atmos")
            } else {
                HStack(spacing: 6.6) {
                    Image("AppleLossless").resizable().scaledToFit().frame(width: 15, height: 9)
                    Text(badge).font(.system(size: 11, weight: .medium))
                }
                .vibrant(Vibrancy.icon)
                .padding(.leading, 6.6)
                .padding(.trailing, 7.2)
                .frame(height: 21)
                .background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 7, style: .continuous))
            }
        }
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
    var active = false
    let action: () -> Void

    init(_ symbol: String, label: LocalizedStringKey, active: Bool = false, action: @escaping () -> Void) {
        self.symbol = symbol
        self.label = label
        self.active = active
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 20))
                .vibrant(active ? 0.9 : Vibrancy.icon)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}
