import OSLog
import Shared
import SwiftUI

private let pageLog = Logger(subsystem: "io.github.jefer30039.remoteforcider", category: "paginas")

/// Canción de un álbum o playlist.
struct TrackItem: Identifiable, Hashable {
    let type: String
    let id: String
    let number: Int?
    /// Disco en álbumes de varios discos (catálogo: `discNumber`; biblioteca: cuando la numeración vuelve a empezar).
    var disc: Int = 1
    let title: String
    let artist: String?
    let durationMs: Int64?
    let artwork: Shared.Artwork?
    var explicit = false
    /// Falso en las del catálogo aún no publicadas (sin `playParams`): atenuadas y sin tocar.
    var available = true

    var request: PlayRequest { PlayRequest(type: type, id: id) }
}

/// Lo ya cargado de los álbumes y playlists abiertos hace poco: al volver, la página sale entera.
@MainActor
enum CollectionCache {
    static var tracks: [String: [TrackItem]] = [:]
    static var covers: [String: AnimatedCover] = [:]
    static var extras: [String: AlbumExtras] = [:]
}

/// Página de álbum o playlist de Apple Music: portada animada a todo el ancho (o carátula en
/// tarjeta), título, artista, datos, Reproducir/Aleatorio, canciones, pie y estanterías. El fondo es
/// el tono de la carátula; tocar una canción la reproduce y encola detrás las siguientes.
struct CollectionView: View {
    @Environment(RemoteStore.self) private var store
    @Environment(\.openRoute) private var openRoute
    @Environment(\.colorScheme) private var scheme
    let route: CollectionRoute

    @State private var tracks: [TrackItem]?
    @State private var failure: String?
    @State private var cover: AnimatedCover?
    @State private var extras: AlbumExtras?
    @State private var traits: [String] = []
    @State private var renaming = false
    @State private var newName = ""
    @State private var addingToPlaylist = false
    /// Enlace de Apple Music para Compartir; sin enlace (playlist propia sin publicar) no hay botón.
    @State private var shareUrl: URL?

    private var key: String { "\(route.kind)/\(route.id)" }

    var body: some View {
        let bg = PageColor.background(hex: cover?.bgColor ?? route.artwork?.bgColor, dark: scheme == .dark)
            ?? Color(white: scheme == .dark ? 0.17 : 0.55)
        let secondary = PageColor.readable(on: bg, alpha: 0.62)
        let tall = route.video ?? cover?.tall
        GeometryReader { geo in
            ScrollView {
                VStack(spacing: 0) {
                    header(width: geo.size.width + geo.safeAreaInsets.leading + geo.safeAreaInsets.trailing, top: geo.safeAreaInsets.top, tall: tall, bg: bg, secondary: secondary)
                    trackList(secondary: secondary)
                    footer(secondary: secondary)
                    if let shelves = extras?.shelves, !shelves.isEmpty {
                        VStack(alignment: .leading, spacing: 0) {
                            ForEach(Array(shelves.enumerated()), id: \.offset) { _, shelf in
                                ShelfView(shelf: shelf, onTitle: shelf.view == "more-by-artist" ? { toArtist() } : nil)
                            }
                        }
                        .padding(.top, 18)
                        .padding(.bottom, 30)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(PageColor.lowered(bg))
                    }
                }
            }
            .scrollEdgeEffectStyle(.soft, for: .top)
            .ignoresSafeArea(edges: tall == nil ? [] : .top)
        }
        .background(bg.ignoresSafeArea())
        .environment(\.colorScheme, .dark)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            // Compartir junto al "…": la barra los agrupa en una cápsula (iOS 27) y aparece al tener el enlace.
            if let shareUrl {
                ToolbarItem(placement: .topBarTrailing) {
                    ShareLink(item: shareUrl, subject: Text(route.title)) {
                        Label("Share", systemImage: "square.and.arrow.up")
                    }
                }
            }
            ToolbarItem(placement: .topBarTrailing) { moreMenu }
        }
        .task(id: key) { await load() }
        .task(id: key) {
            guard let api = store.api, shareUrl == nil else { return }
            let url = (try? await api.collectionUrl(kind: route.kind, id: route.id)).flatMap { $0.flatMap(URL.init(string:)) }
            if let url { withAnimation { shareUrl = url } }
        }
        .alert("Rename Playlist", isPresented: $renaming) {
            TextField("Playlist Name", text: $newName)
            Button("Cancel", role: .cancel) {}
            Button("Rename") { rename() }
        }
        .sheet(isPresented: $addingToPlaylist) {
            PlaylistPicker(items: (tracks ?? []).filter(\.available).map(\.request), title: route.title)
        }
    }

    // MARK: Cabecera

    @ViewBuilder
    private func header(width: CGFloat, top: CGFloat, tall: String?, bg: Color, secondary: Color) -> some View {
        VStack(spacing: 0) {
            if let tall, let url = URL(string: tall) {
                // Portada animada 3:4 a todo el ancho; el título se apoya en el degradado.
                ZStack {
                    LoopingVideo(url: url, preview: cover?.tallPreview?.sized(w: 1200, h: 1600), fallback: route.artwork)
                    fadeInto(bg)
                }
                .frame(width: width, height: width * 4 / 3)
                .clipped()
                .padding(.bottom, -72)
            } else {
                CoverImage(artwork: route.artwork, pixels: 800, corner: 10)
                    .aspectRatio(1, contentMode: .fit)
                    .frame(width: min(width * 0.66, 300))
                    .shadow(color: .black.opacity(0.3), radius: 18, y: 10)
                    .padding(.top, 12)
                    .padding(.bottom, 18)
            }

            Text(RenamedPlaylists.name(route.id) ?? route.title)
                .font(.system(size: 22, weight: .bold))
                .multilineTextAlignment(.center)
                .foregroundStyle(.white)
                .padding(.horizontal, 30)
            if let subtitle = route.subtitle, !subtitle.isEmpty {
                Button { toArtist() } label: {
                    Text(subtitle)
                        .font(.system(size: 20))
                        .foregroundStyle(.white.opacity(0.85))
                        .lineLimit(1)
                }
                .buttonStyle(.plain)
                .disabled(route.isPlaylist)
                .padding(.top, 2)
                .padding(.horizontal, 30)
            }
            let q = quality
            if metaLine != nil || q.atmos || q.lossless {
                HStack(spacing: 5) {
                    if let meta = metaLine { Text(meta) }
                    if q.atmos {
                        if metaLine != nil { Text(verbatim: "·") }
                        // Logotipo oficial (vector), con las mayúsculas algo mayores que las del texto.
                        Image("DolbyAtmos").resizable().scaledToFit().frame(height: 10)
                            .accessibilityLabel("Dolby Atmos")
                    }
                    if q.lossless {
                        if metaLine != nil || q.atmos { Text(verbatim: "·") }
                        Image("AppleLossless").resizable().scaledToFit().frame(height: 8)
                        Text(q.hiRes ? "Hi-Res Lossless" : "Lossless")
                    }
                }
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(secondary)
                .lineLimit(1)
                .padding(.horizontal, 20)
                .padding(.top, 6)
            }

            PlayShuffleButtons(play: { play(shuffle: false) }, shuffle: { play(shuffle: true) },
                               tint: .white, background: .white.opacity(0.14))
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 14)

            if let fullId = fullAlbumId {
                Button {
                    openRoute(.collection(CollectionRoute(kind: "albums", id: fullId, title: route.title, subtitle: route.subtitle, artwork: route.artwork)))
                } label: {
                    Label("Show Complete Album", systemImage: "square.stack")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .padding(.bottom, 10)
            }
        }
    }

    /// "Género • año" en álbumes; "Actualizada hace 2 d" en playlists. Los logos de Dolby Atmos y
    /// Lossless van aparte (`qualityLogos`), detrás, como en Apple Music.
    private var metaLine: String? {
        var parts: [String] = []
        if route.isPlaylist {
            if let iso = cover?.lastModified, let date = ISO8601DateFormatter().date(from: iso) {
                let ago = RelativeDateTimeFormatter()
                ago.unitsStyle = .short
                parts.append(String(localized: "Updated \(ago.localizedString(for: date, relativeTo: .now))"))
            }
        } else {
            if let genre = cover?.genre { parts.append(genre) }
            if let year = cover?.releaseYear { parts.append(year) }
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    /// Rasgos de audio del álbum o de alguna canción de la playlist: (Atmos, Lossless, Hi-Res).
    private var quality: (atmos: Bool, lossless: Bool, hiRes: Bool) {
        let all = (cover?.audioTraits ?? []) + traits
        return (all.contains("atmos"), all.contains("lossless") || all.contains("hi-res-lossless"), all.contains("hi-res-lossless"))
    }

    /// Álbum de la biblioteca con menos canciones que el del catálogo: "Ver álbum completo".
    private var fullAlbumId: String? {
        guard route.kind == "library-albums", let id = cover?.catalogId, let full = cover?.trackCount?.intValue,
              let have = tracks?.count, have < full else { return nil }
        return id
    }

    // MARK: Canciones

    @ViewBuilder
    private func trackList(secondary: Color) -> some View {
        if let tracks {
            LazyVStack(spacing: 0) {
                let discs = route.isPlaylist ? 1 : (tracks.map(\.disc).max() ?? 1)
                ForEach(Array(tracks.enumerated()), id: \.element.id) { i, t in
                    // Álbumes de varios discos: "Disco 1", "Disco 2"… encima de su primera canción.
                    if discs > 1, i == 0 || tracks[i - 1].disc != t.disc {
                        Text("Disc \(t.disc)")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 20)
                            .padding(.top, i == 0 ? 4 : 22)
                            .padding(.bottom, 6)
                            .accessibilityAddTraits(.isHeader)
                    }
                    Button { play(from: i) } label: {
                        if route.isPlaylist {
                            HStack(spacing: 12) {
                                CoverImage(artwork: t.artwork, pixels: 120, corner: 5)
                                    .frame(width: 44, height: 44)
                                VStack(alignment: .leading, spacing: 2) {
                                    HStack(spacing: 4) {
                                        Text(t.title).foregroundStyle(.white).lineLimit(1)
                                        if t.explicit { ExplicitBadge(size: 10) }
                                    }
                                    Text(t.artist ?? "").font(.subheadline).foregroundStyle(secondary).lineLimit(1)
                                }
                                Spacer(minLength: 0)
                                Color.clear.frame(width: 36)
                            }
                            .frame(height: 58)
                        } else {
                            HStack(spacing: 0) {
                                Text(t.number.map(String.init) ?? "")
                                    .font(.system(size: 16))
                                    .monospacedDigit()
                                    .foregroundStyle(secondary)
                                    .frame(width: 30, alignment: .leading)
                                Text(t.title).foregroundStyle(.white).lineLimit(1)
                                if t.explicit { ExplicitBadge(size: 10).padding(.leading, 4) }
                                Spacer(minLength: 8)
                                Color.clear.frame(width: 36)
                            }
                            .frame(height: 48)
                        }
                    }
                    .buttonStyle(.plain)
                    // Solo en el botón de la fila: puesto al final lo heredaba también el "…".
                    .accessibilityIdentifier("trackRow")
                    .disabled(!t.available)
                    .opacity(t.available ? 1 : 0.4)
                    // Fuera del botón de la fila: dentro de su etiqueta el menú no recibiría el toque.
                    .overlay(alignment: .trailing) {
                        if t.available { SongMoreButton(type: t.type, id: t.id, name: t.title, tint: secondary) }
                    }
                    .overlay(alignment: .bottom) {
                        Rectangle().fill(.white.opacity(0.12)).frame(height: 0.5)
                            .padding(.leading, route.isPlaylist ? 56 : 30)
                    }
                    .padding(.horizontal, 20)
                    .songMenu(type: t.type, id: t.id, name: t.title, enabled: t.available)
                }
            }
        } else if let failure {
            EmptyStateView(title: "Couldn't load", detail: failure)
        } else {
            LoadingView()
        }
    }

    @ViewBuilder
    private func footer(secondary: Color) -> some View {
        if let tracks, !tracks.isEmpty {
            let minutes = Int((Double(tracks.compactMap(\.durationMs).reduce(0, +)) / 60000).rounded())
            VStack(alignment: .leading, spacing: 4) {
                if let date = extras?.releaseDate.flatMap(Self.parseDate) {
                    Text(date.formatted(date: .long, time: .omitted))
                }
                Text("^[\(tracks.count) song](inflect: true), ^[\(minutes) minute](inflect: true)")
                if let copyright = extras?.copyright {
                    Text(copyright).padding(.top, 2)
                }
                if extras?.digitalMaster == true {
                    Label {
                        Text("Apple Digital Master")
                    } icon: {
                        Image("AppleDigitalMaster").resizable().scaledToFit().frame(width: 15, height: 14)
                    }
                        .padding(.top, 6)
                }
            }
            .font(.system(size: 15))
            .foregroundStyle(secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20)
            .padding(.top, 18)
            .padding(.bottom, 24)
        }
    }

    private static func parseDate(_ s: String) -> Date? {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "en_US_POSIX")
        return f.date(from: s)
    }

    // MARK: Menú

    private var moreMenu: some View {
        Menu {
            Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { store.playNext(type: route.kind, id: route.id) }
            Button("Play Last", systemImage: "text.line.last.and.arrowtriangle.forward") { store.playLast(type: route.kind, id: route.id) }
            Button("Add to a Playlist…", systemImage: "text.badge.plus") { addingToPlaylist = true }
                .disabled(tracks?.isEmpty ?? true)
            if route.kind == "library-playlists" {
                Button("Rename…", systemImage: "pencil") {
                    newName = RenamedPlaylists.name(route.id) ?? route.title
                    renaming = true
                }
            }
            if !route.isPlaylist, route.subtitle != nil {
                Button("Go to Artist", systemImage: "music.microphone") { toArtist() }
            }
        } label: {
            Image(systemName: "ellipsis")
        }
        .accessibilityIdentifier("collectionMenu")
    }

    // MARK: Acciones

    private func play(shuffle: Bool) {
        guard let api = store.api else { return }
        Task { try? await api.playCollection(type: route.kind, id: route.id, shuffle: KotlinBoolean(value: shuffle)) }
    }

    /// Cider no empieza una colección en la canción N: reproduce esa y encola detrás las siguientes.
    private func play(from index: Int) {
        guard let list = tracks, index < list.count, list[index].available else { return }
        if index == 0 {
            play(shuffle: false)
            return
        }
        let available = list.enumerated().filter { $0.element.available || $0.offset == index }
        let start = available.firstIndex { $0.offset == index } ?? 0
        store.play(available.map { $0.element.request }, from: start)
    }

    private func toArtist() {
        guard let api = store.api, !route.isPlaylist else { return }
        Task {
            if let artist = try? await api.albumArtist(kind: route.kind, id: route.id), let r = artist.route { openRoute(r) }
        }
    }

    private func rename() {
        let name = newName.trimmingCharacters(in: .whitespaces)
        guard let api = store.api, !name.isEmpty else { return }
        RenamedPlaylists.set(route.id, name)
        Task { try? await api.renamePlaylist(id: route.id, name: name) }
    }

    // MARK: Carga

    private func load() async {
        tracks = CollectionCache.tracks[key]
        cover = CollectionCache.covers[key]
        guard let api = store.api else { return }
        let kind = route.kind, id = route.id
        let coverTask = Task { try? await api.animatedCover(kind: kind, id: id) }
        let started = Date()
        pageLog.notice("\(route.kind, privacy: .public)/\(route.id, privacy: .public): pidiendo canciones")
        do {
            let fresh = try await Self.loadTracks(api, route)
            tracks = fresh
            CollectionCache.tracks[key] = fresh
            failure = nil
            pageLog.notice("\(route.id, privacy: .public): \(fresh.count) canciones en \(Date().timeIntervalSince(started)) s")
        } catch {
            pageLog.error("\(route.id, privacy: .public): \(String(describing: error), privacy: .public)")
            if tracks == nil { failure = error.localizedDescription }
        }
        if let fresh = await coverTask.value {
            cover = fresh
            CollectionCache.covers[key] = fresh
        }
        // Álbumes: pie y estanterías del álbum de catálogo. Playlists: rasgos de audio de sus canciones.
        if route.isPlaylist {
            if let catalog = route.kind == "playlists" ? route.id : cover?.catalogId {
                traits = (try? await api.playlistAudioTraits(catalogId: catalog)) ?? []
            }
        } else if let catalog = route.kind == "albums" ? route.id : cover?.catalogId {
            if let hit = CollectionCache.extras[catalog] { extras = hit }
            if let fresh = try? await api.albumExtras(catalogId: catalog) {
                extras = fresh
                CollectionCache.extras[catalog] = fresh
            }
        }
    }

    nonisolated private static func loadTracks(_ api: CiderApi, _ c: CollectionRoute) async throws -> [TrackItem] {
        if c.isLibrary {
            let album = c.kind == "library-albums"
            // Cider no dice cuáles no se pueden reproducir: se pregunta a Apple a la vez.
            let off = Task { (try? await api.libraryUnplayable(type: album ? "albums" : "playlists", id: c.id, max: 1000)) ?? [] }
            var songs: [LibrarySong] = []
            var offset: Int32 = 0
            while true {
                let page: Page<LibrarySong>
                if album {
                    page = try await api.albumSongs(id: c.id, offset: offset, limit: 100)
                } else {
                    page = try await api.playlistSongs(id: c.id, offset: offset, limit: 100)
                }
                let got = page.items.compactMap { $0 as? LibrarySong }
                songs += got
                offset += Int32(got.count)
                if got.isEmpty || offset >= page.total { break }
            }
            let unplayable = await off.value
            // Cider no da el disco de las canciones de la biblioteca: un disco nuevo empieza cuando la
            // numeración vuelve a bajar.
            var disc = 1
            var last = 0
            return songs.map { s in
                let n = s.trackNumber?.intValue
                if album, let n { if n < last { disc += 1 }; last = n }
                return TrackItem(type: "library-songs", id: s.id, number: album ? n : nil, disc: disc, title: s.name,
                                 artist: s.artistName, durationMs: s.durationMs, artwork: s.artwork, available: !unplayable.contains(s.id))
            }
        }
        let items = try await api.catalogTracks(type: c.kind, id: c.id)
        return items.enumerated().map { i, t in
            TrackItem(type: t.type, id: t.id, number: t.attributes?.trackNumber?.intValue ?? (i + 1),
                      disc: t.attributes?.discNumber?.intValue ?? 1, title: t.name, artist: t.attributes?.artistName,
                      durationMs: t.attributes?.durationInMillis?.int64Value, artwork: t.artwork,
                      available: t.attributes?.playParams != nil)
        }
    }
}

/// Vídeo en bucle sin sonido con su fotograma fijo de espera (o la carátula, si no hay fotograma).
struct LoopingVideo: View {
    let url: URL
    let preview: String?
    var fallback: Shared.Artwork? = nil
    @State private var video: CoverVideo?

    var body: some View {
        ZStack {
            if let preview, let previewURL = URL(string: preview) {
                AsyncImage(url: previewURL) { phase in
                    if let image = phase.image { image.resizable().scaledToFill() } else { CoverImage(artwork: fallback, pixels: 600, corner: 0) }
                }
            } else {
                CoverImage(artwork: fallback, pixels: 600, corner: 0)
            }
            if let video { VideoLayer(player: video.player) }
        }
        .clipped()
        .task(id: url) { video = CoverVideo(url: url) }
        .onDisappear { video?.player.pause() }
        .onAppear { video?.player.play() }
    }
}
