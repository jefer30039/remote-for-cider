import Shared
import SwiftUI

/// Buscar de Apple Music: sin texto, las categorías en mosaicos de dos columnas (y las búsquedas
/// recientes al enfocar el campo); escribiendo, canciones, artistas, álbumes y playlists del
/// catálogo. Busca mientras se escribe, con una pausa para no saturar a Cider.
struct SearchView: View {
    @Environment(RemoteStore.self) private var store
    @Environment(\.openRoute) private var openRoute
    @State private var term = ""
    @State private var results: SearchResults?
    @State private var failure: String?
    @State private var categories: [SearchCategory] = []
    @State private var recents = RecentSearches.load()
    @State private var focused = false

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                if term.trimmingCharacters(in: .whitespaces).isEmpty {
                    if focused, !recents.isEmpty {
                        recentList
                    } else {
                        categoryGrid
                    }
                } else if let results {
                    resultList(results)
                } else if let failure {
                    EmptyStateView(title: "Couldn't search", detail: failure)
                } else {
                    LoadingView()
                }
            }
            .padding(.bottom, 24)
        }
        .scrollDismissesKeyboard(.immediately)
        .navigationTitle("Search")
        .searchable(text: $term, isPresented: $focused, prompt: "Artists, Songs, Lyrics, and More")
        .task(id: term) { await search() }
        .task(id: store.connection) {
            if categories.isEmpty, let api = store.api { categories = (try? await api.searchCategories()) ?? [] }
        }
        .toolbar { ToolbarItem(placement: .topBarTrailing) { ConnectionMenuButton() } }
    }

    private func search() async {
        let q = term.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { results = nil; return }
        if let link = AppleMusicLink(q) {
            await open(link)
            return
        }
        try? await Task.sleep(for: .milliseconds(350))
        guard !Task.isCancelled, let api = store.api else { return }
        do {
            results = try await api.search(term: q, limit: 8)
            failure = nil
        } catch {
            if !Task.isCancelled { failure = error.localizedDescription }
        }
    }

    /// Enlace pegado: la canción suena; álbum, playlist o artista se abren.
    private func open(_ link: AppleMusicLink) async {
        switch link {
        case .song(let id):
            store.play(type: "songs", id: id)
            term = ""
        case .artist(let id):
            term = ""
            openRoute(.artist(id: id, name: "", catalog: true))
        case .album(let id), .playlist(let id):
            let kind: String
            if case .album = link { kind = "albums" } else { kind = "playlists" }
            let item = try? await store.api?.catalogItem(type: kind, id: id)
            term = ""
            openRoute(.collection(CollectionRoute(kind: kind, id: id, title: item?.name ?? "",
                                                  subtitle: item?.attributes?.artistName ?? item?.attributes?.curatorName,
                                                  artwork: item?.artwork)))
        }
    }

    // MARK: Categorías

    private var categoryGrid: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !categories.isEmpty {
                Text("Browse Categories")
                    .font(.title2.bold())
                    .padding(.top, 8)
            }
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                ForEach(categories, id: \.id) { c in
                    NavigationLink(value: Route.category(id: c.id, name: c.name)) {
                        ZStack(alignment: .bottomLeading) {
                            Color(hex: c.bgColor) ?? Color(.secondarySystemFill)
                            if let image = c.imageUrl.flatMap(URL.init(string:)) {
                                AsyncImage(url: image) { phase in
                                    phase.image?.resizable().scaledToFill()
                                }
                            }
                            LinearGradient(colors: [.clear, .black.opacity(0.35)], startPoint: .center, endPoint: .bottom)
                            Text(c.name)
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundStyle(.white)
                                .lineLimit(2)
                                .padding(10)
                        }
                        .frame(height: 112)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                        .zoomSource(Route.category(id: c.id, name: c.name))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("categoryTile")
                }
            }
        }
        .padding(.horizontal, 20)
    }

    // MARK: Recientes

    private var recentList: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Recently Searched").font(.title3.bold())
                Spacer()
                Button("Clear") {
                    RecentSearches.clear()
                    recents = []
                }
                .foregroundStyle(Color.accentRed)
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
            .padding(.bottom, 4)
            ForEach(recents) { r in
                resultRow(id: r.id, type: r.type, name: r.name, subtitle: r.subtitle, artwork: Artwork(url: r.artworkUrl, width: nil, height: nil, bgColor: nil, textColor1: nil, textColor2: nil), explicit: false, remember: nil)
            }
        }
    }

    // MARK: Resultados

    @ViewBuilder
    private func resultList(_ r: SearchResults) -> some View {
        let songs = r.songs?.data ?? []
        let artists = r.artists?.data ?? []
        let albums = r.albums?.data ?? []
        let playlists = r.playlists?.data ?? []
        if songs.isEmpty && artists.isEmpty && albums.isEmpty && playlists.isEmpty {
            EmptyStateView(title: "No Results", detail: term)
        }
        // Apple Music pone primero los artistas que coinciden y luego el resto mezclado por tipo.
        section("Artists", artists)
        section("Songs", songs)
        section("Albums", albums)
        section("Playlists", playlists)
    }

    @ViewBuilder
    private func section(_ title: LocalizedStringKey, _ items: [AmItem]) -> some View {
        if !items.isEmpty {
            Text(title)
                .font(.title3.bold())
                .padding(.horizontal, 20)
                .padding(.top, 18)
                .padding(.bottom, 4)
            ForEach(items, id: \.id) { item in
                resultRow(id: item.id, type: item.type, name: item.name, subtitle: subtitle(item), artwork: item.artwork,
                          explicit: false, remember: item)
            }
        }
    }

    private func subtitle(_ item: AmItem) -> String? {
        let a = item.attributes
        switch item.type {
        case "songs": return [String(localized: "Song"), a?.artistName].compactMap { $0 }.joined(separator: " · ")
        case "albums": return [String(localized: "Album"), a?.artistName].compactMap { $0 }.joined(separator: " · ")
        case "playlists": return [String(localized: "Playlist"), a?.curatorName].compactMap { $0 }.joined(separator: " · ")
        case "artists": return String(localized: "Artist")
        default: return a?.artistName
        }
    }

    private func resultRow(id: String, type: String, name: String, subtitle: String?, artwork: Shared.Artwork?, explicit: Bool, remember: AmItem?) -> some View {
        let artist = type.hasSuffix("artists")
        let route: Route? = artist ? .artist(id: id, name: name, catalog: !type.hasPrefix("library"))
            : (type.hasSuffix("albums") || type.hasSuffix("playlists"))
            ? .collection(CollectionRoute(kind: type, id: id, title: name, subtitle: subtitle, artwork: artwork)) : nil
        return Button {
            if let remember { RecentSearches.add(remember, subtitle: subtitle); recents = RecentSearches.load() }
            focused = false
            if let route { openRoute(route) } else { store.play(type: type, id: id) }
        } label: {
            HStack(spacing: 12) {
                Group {
                    if artist && artwork?.url == nil { Monogram(name: name) }
                    else if artist { CoverImage(artwork: artwork, pixels: 150, corner: 0).clipShape(Circle()) }
                    else { CoverImage(artwork: artwork, pixels: 150, corner: 5) }
                }
                .frame(width: 52, height: 52)
                .zoomSource(artist ? nil : route)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 4) {
                        Text(name).lineLimit(1)
                        if explicit { ExplicitBadge(size: 10) }
                    }
                    if let subtitle { Text(subtitle).font(.subheadline).foregroundStyle(.secondary).lineLimit(1) }
                }
                Spacer(minLength: 0)
                if route != nil {
                    Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
                }
            }
            .frame(height: 64)
            .padding(.horizontal, 20)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("searchResult")
        .modifier(OptionalSongMenu(enabled: type.hasSuffix("songs"), type: type, id: id, name: name))
    }
}

private struct OptionalSongMenu: ViewModifier {
    let enabled: Bool
    let type: String
    let id: String
    let name: String

    func body(content: Content) -> some View {
        if enabled { content.songMenu(type: type, id: id, name: name) } else { content }
    }
}

/// Búsquedas recientes (las 12 últimas, en UserDefaults), como Apple Music.
struct RecentSearch: Codable, Identifiable, Equatable {
    let id: String
    let type: String
    let name: String
    let subtitle: String?
    let artworkUrl: String?
}

enum RecentSearches {
    private static let key = "busquedasRecientes"
    private static let limit = 12

    static func load() -> [RecentSearch] {
        guard let data = UserDefaults.standard.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([RecentSearch].self, from: data)) ?? []
    }

    static func add(_ item: AmItem, subtitle: String?) {
        let entry = RecentSearch(id: item.id, type: item.type, name: item.name, subtitle: subtitle, artworkUrl: item.artwork?.url)
        var list = load().filter { $0.id != entry.id }
        list.insert(entry, at: 0)
        save(Array(list.prefix(limit)))
    }

    static func clear() { save([]) }

    private static func save(_ list: [RecentSearch]) {
        UserDefaults.standard.set(try? JSONEncoder().encode(list), forKey: key)
    }
}

/// Categoría de Buscar (curador de Apple Music): sus playlists en cuadrícula.
struct CategoryView: View {
    @Environment(RemoteStore.self) private var store
    let id: String
    let name: String
    @State private var items: [AmItem]?
    @State private var failure: String?

    var body: some View {
        ScrollView {
            if let items {
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 16, alignment: .top), GridItem(.flexible(), spacing: 16, alignment: .top)], spacing: 18) {
                    ForEach(items, id: \.id) { item in
                        CoverTile(title: item.name, subtitle: item.attributes?.curatorName, artwork: item.artwork, route: item.route)
                    }
                }
                .padding(.horizontal, 20)
            } else if let failure {
                EmptyStateView(title: "Couldn't load", detail: failure)
            } else {
                LoadingView()
            }
        }
        .navigationTitle(name)
        .task(id: id) {
            guard let api = store.api else { return }
            do { items = try await api.curatorPlaylistsPage(id: id) } catch { failure = error.localizedDescription }
        }
    }
}

/// Enlace de Apple Music pegado en Buscar (`music.apple.com/…/album/nombre/ID?i=canción`,
/// `/playlist/…/pl.…`, `/artist/…/ID`, `/song/…/ID`): qué abrir o reproducir.
enum AppleMusicLink {
    case song(String)
    case album(String)
    case playlist(String)
    case artist(String)

    init?(_ text: String) {
        guard let url = URL(string: text.trimmingCharacters(in: .whitespacesAndNewlines)),
              let host = url.host, host.hasSuffix("music.apple.com") else { return nil }
        let parts = url.pathComponents.filter { $0 != "/" }
        let query = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems
        guard let id = parts.last else { return nil }
        if let song = query?.first(where: { $0.name == "i" })?.value { self = .song(song); return }
        if parts.contains("song") { self = .song(id) }
        else if parts.contains("album") { self = .album(id) }
        else if parts.contains("playlist") { self = .playlist(id) }
        else if parts.contains("artist") { self = .artist(id) }
        else { return nil }
    }
}
