import Shared
import SwiftUI
import UIKit

/// Álbum, artista y enlace de cada canción: se consultan al tocar la acción y se recuerdan.
@MainActor
enum SongLinksCache {
    private static var cache: [String: SongLinks] = [:]

    static func get(_ api: CiderApi, _ id: String) async -> SongLinks? {
        if let hit = cache[id] { return hit }
        guard let links = try? await api.songLinks(id: id) else { return nil }
        cache[id] = links
        return links
    }
}

extension View {
    /// Menú de canción de Apple Music (mantener pulsado): reproducir a continuación o al final,
    /// añadir a una playlist, ir al álbum o al artista y compartir el enlace.
    func songMenu(type: String, id: String, name: String, enabled: Bool = true) -> some View {
        modifier(SongMenuModifier(type: type, id: id, name: name, enabled: enabled))
    }

    /// Deslizar a la derecha: a continuación (morado) o al final (naranja), como Apple Music.
    func queueSwipe(type: String, id: String, store: RemoteStore) -> some View {
        swipeActions(edge: .leading, allowsFullSwipe: true) {
            Button { store.playNext(type: type, id: id) } label: { Label("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") }
                .tint(.purple)
            Button { store.playLast(type: type, id: id) } label: { Label("Play Last", systemImage: "text.line.last.and.arrowtriangle.forward") }
                .tint(.orange)
        }
    }
}

private struct SongMenuModifier: ViewModifier {
    @Environment(RemoteStore.self) private var store
    @Environment(\.openRoute) private var openRoute
    let type: String
    let id: String
    let name: String
    let enabled: Bool
    @State private var addingToPlaylist = false

    func body(content: Content) -> some View {
        content
            .contextMenu {
                if enabled {
                    Section {
                        Button("Play", systemImage: "play") { store.play(type: type, id: id) }
                        Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { store.playNext(type: type, id: id) }
                        Button("Play Last", systemImage: "text.line.last.and.arrowtriangle.forward") { store.playLast(type: type, id: id) }
                    }
                    Section {
                        Button("Add to a Playlist…", systemImage: "text.badge.plus") { addingToPlaylist = true }
                    }
                    Section {
                        Button("Go to Album", systemImage: "square.stack") {
                            Task { if let r = await links()?.album?.route { openRoute(r) } }
                        }
                        Button("Go to Artist", systemImage: "music.microphone") {
                            Task { if let r = await links()?.artist?.route { openRoute(r) } }
                        }
                        Button("Share Song…", systemImage: "square.and.arrow.up") {
                            Task { if let url = await links()?.url.flatMap(URL.init(string:)) { share(url) } }
                        }
                    }
                }
            }
            .sheet(isPresented: $addingToPlaylist) {
                PlaylistPicker(items: [PlayRequest(type: type, id: id)], title: name)
            }
    }

    private func links() async -> SongLinks? {
        guard let api = store.api else { return nil }
        return await SongLinksCache.get(api, id)
    }
}

/// Hoja del sistema para compartir, sobre lo que esté delante.
@MainActor
func share(_ url: URL) {
    guard let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first,
          var top = scene.keyWindow?.rootViewController else { return }
    while let next = top.presentedViewController { top = next }
    let sheet = UIActivityViewController(activityItems: [url], applicationActivities: nil)
    sheet.popoverPresentationController?.sourceView = top.view
    top.present(sheet, animated: true)
}

/// "Añadir a una playlist": las playlists editables de la biblioteca y "Nueva playlist…".
/// Cider deja 10 escrituras por minuto.
struct PlaylistPicker: View {
    @Environment(RemoteStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let items: [PlayRequest]
    let title: String
    @State private var playlists: [LibraryPlaylist]?
    @State private var naming = false
    @State private var newName = ""
    @State private var message: String?

    var body: some View {
        NavigationStack {
            List {
                Button {
                    newName = ""
                    naming = true
                } label: {
                    Label("New Playlist…", systemImage: "plus")
                        .foregroundStyle(Color.accentRed)
                }
                if let playlists {
                    ForEach(playlists, id: \.id) { p in
                        Button { add(to: p.id) } label: {
                            HStack(spacing: 12) {
                                PlaylistArtwork(artwork: p.artwork)
                                    .frame(width: 48, height: 48)
                                Text(RenamedPlaylists.name(p.id) ?? p.name)
                                    .foregroundStyle(.primary)
                                    .lineLimit(1)
                            }
                        }
                    }
                } else {
                    LoadingView()
                }
            }
            .listStyle(.plain)
            .navigationTitle("Add to a Playlist")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            }
            .overlay(alignment: .bottom) {
                if let message {
                    Text(message)
                        .font(.footnote)
                        .padding(10)
                        .glassEffect(in: .capsule)
                        .padding(.bottom, 20)
                }
            }
        }
        .task { await load() }
        .alert("New Playlist", isPresented: $naming) {
            TextField("Playlist Name", text: $newName)
            Button("Cancel", role: .cancel) {}
            Button("Create") { create(newName) }
        }
    }

    private func load() async {
        guard let api = store.api else { return }
        var all: [LibraryPlaylist] = []
        var offset: Int32 = 0
        while let page = try? await api.playlists(offset: offset, limit: 100) {
            let got = page.items.compactMap { $0 as? LibraryPlaylist }
            all += got
            offset += Int32(got.count)
            if got.isEmpty || offset >= page.total { break }
        }
        playlists = all.filter { $0.canEdit }
    }

    private func add(to id: String) {
        guard let api = store.api else { return }
        Task {
            do {
                try await api.addToPlaylist(playlistId: id, items: items)
                dismiss()
            } catch {
                message = String(localized: "Couldn't add it: \(error.localizedDescription)")
            }
        }
    }

    private func create(_ name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard let api = store.api, !trimmed.isEmpty else { return }
        Task {
            do {
                if let id = try await api.createPlaylist(name: trimmed, tracks: []) {
                    try await api.addToPlaylist(playlistId: id, items: items)
                    dismiss()
                } else {
                    message = String(localized: "Couldn't create the playlist.")
                }
            } catch {
                message = String(localized: "Couldn't create the playlist.")
            }
        }
    }
}

/// Carátula de playlist; sin carátula, una nota sobre gris (Apple Music).
struct PlaylistArtwork: View {
    let artwork: Shared.Artwork?
    var corner: CGFloat = 5

    var body: some View {
        if artwork?.url != nil {
            CoverImage(artwork: artwork, pixels: 200, corner: corner)
        } else {
            RoundedRectangle(cornerRadius: corner, style: .continuous)
                .fill(Color(.secondarySystemFill))
                .overlay {
                    Image(systemName: "music.note.list")
                        .font(.title3)
                        .foregroundStyle(.secondary)
                }
        }
    }
}

/// Nombres nuevos de playlists renombradas desde la app: se enseñan al momento, sin esperar a Cider.
@MainActor
enum RenamedPlaylists {
    private static var names: [String: String] = [:]
    static func name(_ id: String) -> String? { names[id] }
    static func set(_ id: String, _ name: String) { names[id] = name }
}
