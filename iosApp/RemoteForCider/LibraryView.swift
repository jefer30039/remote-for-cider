import Shared
import SwiftUI

/// Biblioteca de Apple Music en iOS 26: fijados arriba (de 3 en 3), las categorías (Playlists,
/// Artistas, Álbumes, Canciones) y "Agregado recientemente" en dos columnas. Apple da hasta 150
/// agregados, de 10 en 10: las páginas siguientes se piden al acercarse al final.
struct LibraryView: View {
    @Environment(RemoteStore.self) private var store
    @State private var pins: [AmItem] = []
    @State private var recent: [AmItem]?
    @State private var recentEnd = false
    @State private var loadingMore = false
    @State private var failure: String?

    private static let page: Int32 = 10
    private let columns = [GridItem(.flexible(), spacing: 16, alignment: .top), GridItem(.flexible(), spacing: 16, alignment: .top)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if !pins.isEmpty {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 14, alignment: .top), count: 3), spacing: 16) {
                        ForEach(pins, id: \.id) { pin in
                            PinTile(item: pin)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 4)
                    .padding(.bottom, 12)
                }

                VStack(spacing: 0) {
                    CategoryRow(title: "Playlists", symbol: "music.note.list", route: .playlists, id: "playlists")
                    CategoryRow(title: "Artists", symbol: "music.microphone", route: .artists, id: "artists")
                    CategoryRow(title: "Albums", symbol: "square.stack", route: .albums, id: "albums")
                    CategoryRow(title: "Songs", symbol: "music.note", route: .songs, id: "songs", divider: false)
                }

                Text("Recently Added")
                    .font(.title2.bold())
                    .padding(.horizontal, 20)
                    .padding(.top, 30)
                    .padding(.bottom, 12)

                if let failure, recent == nil {
                    EmptyStateView(title: "Couldn't load", detail: failure)
                } else if let recent {
                    LazyVGrid(columns: columns, spacing: 18) {
                        ForEach(Array(recent.enumerated()), id: \.element.id) { i, item in
                            CoverTile(title: item.name, subtitle: item.attributes?.artistName ?? item.attributes?.curatorName,
                                      artwork: item.artwork, route: item.route)
                                .onAppear { if i >= recent.count - 4 { Task { await loadMore() } } }
                        }
                    }
                    .padding(.horizontal, 20)
                    if loadingMore { LoadingView() }
                } else {
                    LoadingView()
                }
            }
            .padding(.bottom, 24)
        }
        .navigationTitle("Library")
        .toolbar { ToolbarItem(placement: .topBarTrailing) { ConnectionMenuButton() } }
        .task(id: store.connection) { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        guard let api = store.api else { return }
        do {
            let first = try await api.recentlyAdded(limit: Self.page, offset: 0)
            recent = first
            recentEnd = first.count < Self.page
            failure = nil
        } catch {
            failure = error.localizedDescription
        }
        if let fresh = try? await api.libraryPins() { pins = fresh }
    }

    private func loadMore() async {
        guard let api = store.api, let current = recent, !recentEnd, !loadingMore else { return }
        loadingMore = true
        defer { loadingMore = false }
        do {
            let next = try await api.recentlyAdded(limit: Self.page, offset: Int32(current.count))
            let known = Set(current.map { $0.type + $0.id })
            recent = current + next.filter { !known.contains($0.type + $0.id) }
            if next.count < Self.page { recentEnd = true }
        } catch {
            recentEnd = true
        }
    }
}

/// Fila de categoría: icono rojo a la izquierda, flecha a la derecha y separador desde el texto.
private struct CategoryRow: View {
    let title: LocalizedStringKey
    let symbol: String
    let route: Route
    let id: String
    var divider = true

    var body: some View {
        NavigationLink(value: route) {
            HStack(spacing: 0) {
                Image(systemName: symbol)
                    .font(.system(size: 21))
                    .foregroundStyle(Color.accentRed)
                    .frame(width: 30)
                    .padding(.trailing, 14)
                Text(title)
                    .font(.system(size: 20))
                    .foregroundStyle(.primary)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.tertiary)
            }
            .frame(height: 50)
            .overlay(alignment: .bottom) {
                if divider { Divider().padding(.leading, 44) }
            }
            .padding(.horizontal, 20)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("library-\(id)")
    }
}

/// Fijado: carátula (círculo si es artista) con el nombre centrado debajo.
private struct PinTile: View {
    @Environment(RemoteStore.self) private var store
    let item: AmItem

    var body: some View {
        if let route = item.route {
            NavigationLink(value: route) { content }.buttonStyle(.plain)
        } else {
            Button { store.play(type: item.type, id: item.id) } label: { content }.buttonStyle(.plain)
        }
    }

    private var content: some View {
        let artist = item.type.hasSuffix("artists")
        return VStack(spacing: 6) {
            Group {
                if artist && item.artwork == nil {
                    Monogram(name: item.name)
                } else if artist {
                    CoverImage(artwork: item.artwork, pixels: 300, corner: 0).clipShape(Circle())
                } else {
                    CoverImage(artwork: item.artwork, pixels: 300, corner: 6)
                }
            }
            .aspectRatio(1, contentMode: .fit)
            .zoomSource(artist ? nil : item.route)
            Text(item.name)
                .font(.footnote)
                .lineLimit(1)
                .multilineTextAlignment(.center)
        }
    }
}

/// Celda de cuadrícula: carátula y textos debajo, esquinas de 6 pt (Apple Music).
struct CoverTile: View {
    let title: String
    let subtitle: String?
    let artwork: Shared.Artwork?
    let route: Route?
    var explicit = false
    var circle = false
    var centered = false
    /// Sin ruta (canciones, emisoras): tocar hace esto.
    var action: (() -> Void)? = nil

    var body: some View {
        Group {
            if let route {
                NavigationLink(value: route) { content }.buttonStyle(.plain)
            } else if let action {
                Button(action: action) { content }.buttonStyle(.plain)
            } else {
                content
            }
        }
        .accessibilityIdentifier("coverTile")
    }

    private var content: some View {
        VStack(alignment: centered ? .center : .leading, spacing: 0) {
            Group {
                if circle && artwork == nil {
                    Monogram(name: title)
                } else if circle {
                    CoverImage(artwork: artwork, pixels: 400, corner: 0).clipShape(Circle())
                } else {
                    CoverImage(artwork: artwork, pixels: 400, corner: 6)
                }
            }
            .aspectRatio(1, contentMode: .fit)
            .zoomSource(circle ? nil : route)
            HStack(spacing: 4) {
                Text(title)
                    .font(.subheadline)
                    .lineLimit(1)
                if explicit { ExplicitBadge() }
            }
            .padding(.top, 7)
            if let subtitle, !subtitle.isEmpty {
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, alignment: centered ? .center : .leading)
        .contentShape(Rectangle())
    }
}

/// La "E" de explícito de Apple Music.
struct ExplicitBadge: View {
    var size: CGFloat = 11

    var body: some View {
        Image(systemName: "e.square.fill")
            .font(.system(size: size))
            .foregroundStyle(.secondary)
            .accessibilityLabel("Explicit")
    }
}

/// "…" de las raíces: el equipo conectado, buscarlo en la red, cambiar la dirección y olvidarlo.
struct ConnectionMenuButton: View {
    @Environment(RemoteStore.self) private var store
    @State private var editing = false
    @State private var address = ""
    @State private var about = false

    var body: some View {
        Menu {
            if let c = store.connection {
                Section("Connected to") {
                    Text(verbatim: "\(c.host):\(c.port)")
                }
            }
            Button("Find the Computer on the Network", systemImage: "wifi") { store.searchNow() }
            Button("Change Address…", systemImage: "pencil") {
                address = store.connection?.host ?? ""
                editing = true
            }
            Button("Forget This Computer", systemImage: "xmark.circle", role: .destructive) {
                store.forget()
            }
            Section {
                Button("About Remote for Cider", systemImage: "info.circle") { about = true }
            }
        } label: {
            Image(systemName: "ellipsis")
        }
        .accessibilityIdentifier("connectionMenu")
        .alert("Computer Address", isPresented: $editing) {
            TextField("192.168.0.110", text: $address)
                .keyboardType(.numbersAndPunctuation)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            Button("Cancel", role: .cancel) {}
            Button("Save") { store.changeHost(address.trimmingCharacters(in: .whitespaces)) }
        }
        .sheet(isPresented: $about) { AboutView() }
    }
}

/// "Acerca de": versión, repositorio y avisos de marcas (como en Android).
struct AboutView: View {
    @Environment(\.dismiss) private var dismiss
    private static let repo = URL(string: "https://github.com/jefer30039/remote-for-cider")!

    var body: some View {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        NavigationStack {
            List {
                Section {
                    VStack(spacing: 6) {
                        Image("AboutIcon")
                            .resizable()
                            .frame(width: 80, height: 80)
                            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                            .padding(.bottom, 6)
                        Text(verbatim: "Remote for Cider").font(.title2.bold())
                        Text("Version \(version) (\(build))").foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                    .listRowBackground(Color.clear)
                }
                Section {
                    SwiftUI.Link(destination: Self.repo) {
                        Label("Source Code on GitHub", systemImage: "chevron.left.forwardslash.chevron.right")
                    }
                    SwiftUI.Link(destination: Self.repo.appending(path: "issues")) {
                        Label("Report a Problem", systemImage: "exclamationmark.bubble")
                    }
                } footer: {
                    Text("Unofficial remote for Cider. Not affiliated with Cider Collective or Apple. Apple Music, Apple Digital Master and Apple Lossless are trademarks of Apple Inc.; Dolby Atmos is a trademark of Dolby Laboratories. MIT License © Jeferson Marín.")
                }
            }
            .navigationTitle("About")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
        }
    }
}
