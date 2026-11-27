import AVKit
import Shared
import SwiftUI

/// Inicio, Nuevo y Radio de Apple Music: estanterías con el título que manda Apple. Inicio es la
/// página personal (`listen-now`); Nuevo y Radio, las agrupaciones editoriales.
struct ShelvesPage: View {
    enum Source: Hashable { case home, new, radio }

    @Environment(RemoteStore.self) private var store
    let source: Source
    @State private var shelves: [HomeShelf]?
    @State private var failure: String?

    @MainActor private static var cache: [Source: [HomeShelf]] = [:]

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                if let shelves {
                    ForEach(Array(shelves.enumerated()), id: \.offset) { _, shelf in
                        ShelfView(shelf: shelf)
                    }
                } else if let failure {
                    EmptyStateView(title: "Couldn't load", detail: failure)
                } else {
                    LoadingView()
                }
            }
            .padding(.bottom, 24)
        }
        .navigationTitle(title)
        .toolbar { ToolbarItem(placement: .topBarTrailing) { ConnectionMenuButton() } }
        .task(id: store.connection) { await load() }
        .refreshable { await load() }
    }

    private var title: LocalizedStringKey {
        switch source {
        case .home: "Home"
        case .new: "New"
        case .radio: "Radio"
        }
    }

    private func load() async {
        if shelves == nil { shelves = Self.cache[source] }
        guard let api = store.api else { return }
        do {
            let fresh: [HomeShelf]
            switch source {
            case .home:
                let seconds = TimeZone.current.secondsFromGMT()
                let zone = String(format: "%@%02d:%02d", seconds >= 0 ? "+" : "-", abs(seconds) / 3600, abs(seconds) % 3600 / 60)
                fresh = try await api.home(timezone: zone)
            case .new: fresh = try await api.browse(page: .theNew)
            case .radio: fresh = try await api.browse(page: .radio)
            }
            shelves = fresh
            Self.cache[source] = fresh
            failure = nil
        } catch {
            if shelves == nil { failure = error.localizedDescription }
        }
    }
}

/// Una estantería: título (con "›" si lleva a algún sitio) y su fila horizontal.
struct ShelfView: View {
    @Environment(RemoteStore.self) private var store
    let shelf: HomeShelf
    var onTitle: (() -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let title = shelf.title, !title.isEmpty {
                Button { onTitle?() } label: {
                    HStack(spacing: 4) {
                        Text(title)
                            .font(.title2.bold())
                            .lineLimit(1)
                        if onTitle != nil {
                            Image(systemName: "chevron.right")
                                .font(.system(size: 17, weight: .bold))
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .buttonStyle(.plain)
                .disabled(onTitle == nil)
                .padding(.horizontal, 20)
            }
            row
        }
        .padding(.top, 22)
    }

    @ViewBuilder private var row: some View {
        switch shelf.kind {
        case .hero:
            carousel(width: 270, spacing: 12) { HeroCard(item: $0) }
        case .superHero, .feature:
            carousel(width: 340, spacing: 12) { FeatureCard(item: $0) }
        case .songs:
            SongColumns(items: shelf.items)
        case .videos:
            carousel(width: 260, spacing: 12) { item in
                // Cider no reproduce vídeos: se ven en el iPhone (vista previa de 30 s de los vídeos
                // musicales; los de "Más para ver", enteros), como en Android.
                Button {
                    if let url = item.videoUrl.flatMap(URL.init(string:)) { playVideo(url, title: item.name) }
                } label: { VideoTile(item: item) }
                .buttonStyle(.plain)
                .disabled(item.videoUrl == nil)
            }
        default:
            carousel(width: 166, spacing: 14) { item in
                CoverTile(
                    title: item.name, subtitle: item.subtitle, artwork: item.artwork, route: item.route,
                    explicit: item.explicit, circle: item.type.hasSuffix("artists"),
                    centered: item.type.hasSuffix("artists"),
                    action: item.route == nil ? { store.play(type: item.type, id: item.id) } : nil
                )
            }
        }
    }

    private func carousel<Cell: View>(width: CGFloat, spacing: CGFloat, @ViewBuilder cell: @escaping (HomeItem) -> Cell) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(alignment: .top, spacing: spacing) {
                ForEach(Array(shelf.items.enumerated()), id: \.offset) { _, item in
                    cell(item).frame(width: width)
                }
            }
            .scrollTargetLayout()
            .padding(.horizontal, 20)
        }
        .scrollTargetBehavior(.viewAligned)
    }
}

/// Tarjeta grande 3:4 de Inicio: la imagen (o su portada animada) llena la tarjeta; sin imagen
/// vertical, la carátula cuadrada con margen sobre el color de fondo. Título y subtítulo en blanco.
private struct HeroCard: View {
    @Environment(RemoteStore.self) private var store
    let item: HomeItem

    var body: some View {
        let bg = Color(hex: item.artwork?.bgColor) ?? Color(white: 0.2)
        let card = ZStack(alignment: .bottomLeading) {
            if let video = item.tallVideo, let url = URL(string: video) {
                LoopingVideo(url: url, preview: item.tallArtwork?.sized(w: 600, h: 800), fallback: item.artwork)
            } else if let tall = item.tallArtwork?.sized(w: 600, h: 800), let url = URL(string: tall) {
                AsyncImage(url: url) { phase in
                    if let image = phase.image { image.resizable().scaledToFill() } else { bg }
                }
            } else {
                bg.overlay(alignment: .top) {
                    CoverImage(artwork: item.artwork, pixels: 500, corner: 8)
                        .aspectRatio(1, contentMode: .fit)
                        .padding(22)
                        .shadow(color: .black.opacity(0.25), radius: 10, y: 6)
                }
            }
            LinearGradient(colors: [.clear, .black.opacity(0.55)], startPoint: .center, endPoint: .bottom)
            VStack(alignment: .leading, spacing: 2) {
                if let reason = item.reason {
                    Text(reason.uppercased())
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(.white.opacity(0.8))
                }
                HStack(spacing: 4) {
                    Text(item.name)
                        .font(.system(size: 17, weight: .semibold))
                        .lineLimit(2)
                    if item.explicit { ExplicitBadge(size: 11) }
                }
                if let subtitle = item.subtitle {
                    Text(subtitle)
                        .font(.system(size: 14))
                        .foregroundStyle(.white.opacity(0.8))
                        .lineLimit(2)
                }
            }
            .foregroundStyle(.white)
            .padding(14)
        }
        .aspectRatio(3 / 4, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .zoomSource(item.route)
        Group {
            if let route = item.route {
                NavigationLink(value: route) { card }.buttonStyle(.plain)
            } else {
                Button { store.play(type: item.type, id: item.id) } label: { card }.buttonStyle(.plain)
            }
        }
        .accessibilityIdentifier("heroCard")
    }
}

/// Tarjeta panorámica del carrusel de Nuevo y Radio, con la etiqueta encima ("NUEVO ÁLBUM").
private struct FeatureCard: View {
    @Environment(RemoteStore.self) private var store
    let item: HomeItem

    var body: some View {
        let content = VStack(alignment: .leading, spacing: 2) {
            if let reason = item.reason {
                Text(reason.uppercased())
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(.secondary)
            }
            Text(item.name)
                .font(.system(size: 20))
                .lineLimit(1)
            if let subtitle = item.subtitle {
                Text(subtitle)
                    .font(.system(size: 20))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Group {
                if let wide = item.wideArtwork?.cropped(w: 1040, h: 650) ?? item.artwork?.sized(px: 650), let url = URL(string: wide) {
                    AsyncImage(url: url) { phase in
                        if let image = phase.image { image.resizable().scaledToFill() } else { Color(hex: item.artwork?.bgColor) ?? Color(.secondarySystemFill) }
                    }
                } else {
                    Color(.secondarySystemFill)
                }
            }
            .aspectRatio(1.6, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            .zoomSource(item.route)
            .padding(.top, 6)
        }
        if let route = item.route {
            NavigationLink(value: route) { content }.buttonStyle(.plain)
        } else {
            Button { store.play(type: item.type, id: item.id) } label: { content }.buttonStyle(.plain)
        }
    }
}

/// Vídeo en 16:9 ("Videos musicales"): Cider no los reproduce, se enseñan sin acción.
/// Reproductor a pantalla completa del sistema (con imagen en imagen), sobre lo que esté delante.
@MainActor
func playVideo(_ url: URL, title: String) {
    guard let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first,
          var top = scene.keyWindow?.rootViewController else { return }
    while let next = top.presentedViewController { top = next }
    let item = AVPlayerItem(url: url)
    let meta = AVMutableMetadataItem()
    meta.identifier = .commonIdentifierTitle
    meta.value = title as NSString
    item.externalMetadata = [meta]
    let controller = AVPlayerViewController()
    controller.player = AVPlayer(playerItem: item)
    top.present(controller, animated: true) { controller.player?.play() }
}

private struct VideoTile: View {
    let item: HomeItem

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            CoverImage(artwork: item.artwork, pixels: 520, corner: 8)
                .aspectRatio(16 / 9, contentMode: .fit)
                .overlay(alignment: .bottomLeading) {
                    // De los vídeos musicales solo hay la vista previa de 30 s (el completo lleva DRM).
                    if item.type == "music-videos", item.videoUrl != nil {
                        Label("Preview", systemImage: "play.fill")
                            .font(.caption2.weight(.semibold))
                            .padding(.horizontal, 7)
                            .padding(.vertical, 4)
                            .background(.black.opacity(0.55), in: Capsule())
                            .foregroundStyle(.white)
                            .padding(8)
                    }
                }
            HStack(spacing: 4) {
                Text(item.name).font(.subheadline).lineLimit(1)
                if item.explicit { ExplicitBadge(size: 10) }
            }
            .padding(.top, 7)
            if let subtitle = item.subtitle {
                Text(subtitle).font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
            }
        }
    }
}

/// Canciones en columnas de cuatro filas que se desplazan de lado ("Canciones nuevas destacadas").
private struct SongColumns: View {
    @Environment(RemoteStore.self) private var store
    let items: [HomeItem]

    var body: some View {
        let columns = stride(from: 0, to: items.count, by: 4).map { Array(items[$0..<min($0 + 4, items.count)]) }
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(alignment: .top, spacing: 16) {
                ForEach(Array(columns.enumerated()), id: \.offset) { c, column in
                    VStack(spacing: 0) {
                        ForEach(Array(column.enumerated()), id: \.offset) { r, item in
                            Button {
                                let all = items.filter { $0.type.hasSuffix("songs") }.map { PlayRequest(type: $0.type, id: $0.id) }
                                let index = all.firstIndex { $0.id == item.id } ?? 0
                                store.play(all, from: index)
                            } label: {
                                SongRow(title: item.name, subtitle: item.subtitle, artwork: item.artwork, explicit: item.explicit)
                                    .frame(height: 60)
                                    .overlay(alignment: .bottom) {
                                        if r < column.count - 1 { Divider().padding(.leading, 60) }
                                    }
                            }
                            .buttonStyle(.plain)
                            .songMenu(type: item.type, id: item.id, name: item.name)
                        }
                    }
                    .frame(width: 320)
                    .id(c)
                }
            }
            .scrollTargetLayout()
            .padding(.horizontal, 20)
        }
        .scrollTargetBehavior(.viewAligned)
    }
}
