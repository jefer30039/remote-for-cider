import Shared
import SwiftUI

/// Páginas de la biblioteca de Cider, pedidas de 50 en 50 al acercarse al final.
@MainActor
@Observable
final class Pager<Item> {
    private(set) var items: [Item] = []
    private(set) var total: Int?
    private(set) var failure: String?
    private(set) var loading = false
    @ObservationIgnored private let fetch: (CiderApi, Int32) async throws -> (items: [Item], total: Int)
    @ObservationIgnored private let key: (Item) -> String

    init(key: @escaping (Item) -> String, fetch: @escaping (CiderApi, Int32) async throws -> (items: [Item], total: Int)) {
        self.key = key
        self.fetch = fetch
    }

    var done: Bool { total.map { items.count >= $0 } ?? false }

    func loadMore(_ api: CiderApi?) async {
        guard let api, !loading, !done else { return }
        loading = true
        defer { loading = false }
        do {
            let page = try await fetch(api, Int32(items.count))
            let known = Set(items.map(key))
            items += page.items.filter { !known.contains(key($0)) }
            total = page.items.isEmpty ? items.count : page.total
            failure = nil
        } catch {
            failure = error.localizedDescription
            if total == nil { total = items.count }
        }
    }

    func reset() {
        items = []
        total = nil
        failure = nil
    }

    /// Pedir más si `index` está cerca del final.
    func near(_ index: Int, _ api: CiderApi?) {
        if index >= items.count - 12 { Task { await loadMore(api) } }
    }
}

private let pageSize: Int32 = 50

// MARK: - Playlists

struct PlaylistsList: View {
    @Environment(RemoteStore.self) private var store
    @Environment(\.openRoute) private var openRoute
    @State private var pager = Pager<LibraryPlaylist>(key: { $0.id }) { api, offset in
        let page = try await api.playlists(offset: offset, limit: pageSize)
        return (page.items.compactMap { $0 as? LibraryPlaylist }, Int(page.total))
    }
    @State private var naming = false
    @State private var newName = ""

    var body: some View {
        List {
            Button {
                newName = ""
                naming = true
            } label: {
                HStack(spacing: 14) {
                    RoundedRectangle(cornerRadius: 5, style: .continuous)
                        .fill(Color(.secondarySystemFill))
                        .frame(width: 56, height: 56)
                        .overlay { Image(systemName: "plus").font(.title2).foregroundStyle(Color.accentRed) }
                    Text("New Playlist…")
                        .foregroundStyle(Color.accentRed)
                }
            }
            .listRowSeparator(.hidden, edges: .top)
            ForEach(Array(pager.items.enumerated()), id: \.element.id) { i, p in
                let route = CollectionRoute(kind: "library-playlists", id: p.id, title: RenamedPlaylists.name(p.id) ?? p.name, subtitle: p.description_, artwork: p.artwork)
                NavigationLink(value: Route.collection(route)) {
                    HStack(spacing: 14) {
                        PlaylistArtwork(artwork: p.artwork)
                            .frame(width: 56, height: 56)
                            .zoomSource(.collection(route))
                        Text(route.title)
                            .lineLimit(1)
                    }
                }
                .queueSwipe(type: "library-playlists", id: p.id, store: store)
                .accessibilityIdentifier("playlistRow")
                .onAppear { pager.near(i, store.api) }
            }
            footer(pager, empty: "Your playlists will appear here.")
        }
        .listStyle(.plain)
        .navigationTitle("Playlists")
        .task(id: store.connection) { if pager.items.isEmpty { await pager.loadMore(store.api) } }
        .refreshable { pager.reset(); await pager.loadMore(store.api) }
        .alert("New Playlist", isPresented: $naming) {
            TextField("Playlist Name", text: $newName)
            Button("Cancel", role: .cancel) {}
            Button("Create") { create() }
        }
    }

    private func create() {
        let name = newName.trimmingCharacters(in: .whitespaces)
        guard let api = store.api, !name.isEmpty else { return }
        Task {
            guard let id = try? await api.createPlaylist(name: name, tracks: []) else { return }
            pager.reset()
            await pager.loadMore(store.api)
            openRoute(.collection(CollectionRoute(kind: "library-playlists", id: id, title: name, subtitle: nil, artwork: nil)))
        }
    }
}

// MARK: - Artistas

struct ArtistsList: View {
    @Environment(RemoteStore.self) private var store
    /// Artistas desde Apple Music para tener foto; si la página viene llena puede haber más.
    @State private var pager = Pager<AmItem>(key: { $0.id }) { api, offset in
        let list = try await api.libraryArtistsWithArt(offset: offset, limit: 100)
        return (list, list.count < 100 ? Int(offset) + list.count : Int(offset) + list.count + 1)
    }

    var body: some View {
        List {
            ForEach(Array(pager.items.enumerated()), id: \.element.id) { i, a in
                NavigationLink(value: Route.artist(id: a.id, name: a.name, catalog: false)) {
                    HStack(spacing: 14) {
                        Group {
                            if a.artwork == nil { Monogram(name: a.name) } else { CoverImage(artwork: a.artwork, pixels: 150, corner: 0).clipShape(Circle()) }
                        }
                        .frame(width: 48, height: 48)
                        Text(a.name).lineLimit(1)
                    }
                }
                .accessibilityIdentifier("artistRow")
                .onAppear { pager.near(i, store.api) }
            }
            footer(pager, empty: "Artists in your library will appear here.")
        }
        .listStyle(.plain)
        .navigationTitle("Artists")
        .task(id: store.connection) { if pager.items.isEmpty { await pager.loadMore(store.api) } }
        .refreshable { pager.reset(); await pager.loadMore(store.api) }
    }
}

// MARK: - Álbumes

struct AlbumsGrid: View {
    @Environment(RemoteStore.self) private var store
    @State private var pager = Pager<LibraryAlbum>(key: { $0.id }) { api, offset in
        let page = try await api.albums(offset: offset, limit: pageSize)
        return (page.items.compactMap { $0 as? LibraryAlbum }, Int(page.total))
    }

    var body: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 16, alignment: .top), GridItem(.flexible(), spacing: 16, alignment: .top)], spacing: 18) {
                ForEach(Array(pager.items.enumerated()), id: \.element.id) { i, a in
                    CoverTile(
                        title: a.name, subtitle: a.artistName, artwork: a.artwork,
                        route: .collection(CollectionRoute(kind: "library-albums", id: a.id, title: a.name, subtitle: a.artistName, artwork: a.artwork))
                    )
                    .onAppear { pager.near(i, store.api) }
                }
            }
            .padding(.horizontal, 20)
            if pager.loading { LoadingView() }
            if pager.items.isEmpty, pager.total == 0 {
                EmptyStateView(title: "Albums in your library will appear here.", detail: pager.failure)
            }
        }
        .navigationTitle("Albums")
        .task(id: store.connection) { if pager.items.isEmpty { await pager.loadMore(store.api) } }
        .refreshable { pager.reset(); await pager.loadMore(store.api) }
    }
}

// MARK: - Canciones

struct SongsList: View {
    @Environment(RemoteStore.self) private var store
    @State private var pager = Pager<LibrarySong>(key: { $0.id }) { api, offset in
        let page = try await api.songs(offset: offset, limit: pageSize)
        return (page.items.compactMap { $0 as? LibrarySong }, Int(page.total))
    }

    var body: some View {
        List {
            Section {
                PlayShuffleButtons(
                    play: { play(from: 0) },
                    shuffle: {
                        store.setShuffle(true)
                        play(from: Int.random(in: 0..<max(pager.items.count, 1)))
                    }
                )
                .listRowSeparator(.hidden)
                .listRowInsets(EdgeInsets(top: 4, leading: 20, bottom: 12, trailing: 20))
            }
            ForEach(Array(pager.items.enumerated()), id: \.element.id) { i, s in
                Button { play(from: i) } label: {
                    SongRow(title: s.name, subtitle: s.artistName, artwork: s.artwork)
                }
                .buttonStyle(.plain)
                .songMenu(type: "library-songs", id: s.id, name: s.name)
                .queueSwipe(type: "library-songs", id: s.id, store: store)
                .accessibilityIdentifier("songRow")
                .onAppear { pager.near(i, store.api) }
            }
            footer(pager, empty: "Songs in your library will appear here.")
        }
        .listStyle(.plain)
        .navigationTitle("Songs")
        .task(id: store.connection) { if pager.items.isEmpty { await pager.loadMore(store.api) } }
        .refreshable { pager.reset(); await pager.loadMore(store.api) }
    }

    private func play(from index: Int) {
        let items = pager.items.map { PlayRequest(type: "library-songs", id: $0.id) }
        guard index < items.count else { return }
        store.play(items, from: index)
    }
}

/// Fila de canción con carátula pequeña (playlists, canciones, resultados).
struct SongRow: View {
    let title: String
    let subtitle: String?
    let artwork: Shared.Artwork?
    var explicit = false
    var available = true

    var body: some View {
        HStack(spacing: 12) {
            CoverImage(artwork: artwork, pixels: 120, corner: 5)
                .frame(width: 48, height: 48)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Text(title).lineLimit(1)
                    if explicit { ExplicitBadge(size: 10) }
                }
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 0)
        }
        .opacity(available ? 1 : 0.4)
        .contentShape(Rectangle())
    }
}

/// Reproducir · Aleatorio en dos cápsulas iguales (Apple Music).
struct PlayShuffleButtons: View {
    let play: () -> Void
    let shuffle: () -> Void
    var tint: Color = .accentRed
    var background: Color = Color(.secondarySystemFill)

    var body: some View {
        HStack(spacing: 14) {
            Button(action: play) {
                Label("Play", systemImage: "play.fill")
                    .frame(maxWidth: .infinity, minHeight: 48)
            }
            .accessibilityIdentifier("playCollection")
            Button(action: shuffle) {
                Label("Shuffle", systemImage: "shuffle")
                    .frame(maxWidth: .infinity, minHeight: 48)
            }
            .accessibilityIdentifier("shuffleCollection")
        }
        .font(.system(size: 17, weight: .semibold))
        .foregroundStyle(tint)
        .buttonStyle(CapsuleFill(background: background))
    }
}

private struct CapsuleFill: ButtonStyle {
    let background: Color

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(background, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .opacity(configuration.isPressed ? 0.6 : 1)
            .contentShape(Rectangle())
    }
}

/// Final de una lista paginada: cargando, vacía o con error.
@ViewBuilder
private func footer<T>(_ pager: Pager<T>, empty: LocalizedStringKey) -> some View {
    if pager.loading {
        LoadingView().listRowSeparator(.hidden)
    } else if pager.items.isEmpty, let failure = pager.failure {
        EmptyStateView(title: "Couldn't load", detail: failure).listRowSeparator(.hidden)
    } else if pager.items.isEmpty, pager.total == 0 {
        EmptyStateView(title: empty).listRowSeparator(.hidden)
    }
}
