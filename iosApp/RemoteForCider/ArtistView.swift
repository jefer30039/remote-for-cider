import Shared
import SwiftUI

/// Página de artista de Apple Music: cabecera con su vídeo (o la imagen editorial) fundida con el
/// fondo, nombre (o logo) y Reproducir; último lanzamiento, canciones top, estanterías y "Acerca de".
/// Los artistas de la biblioteca se buscan en el catálogo; sin él, se enseñan sus álbumes.
struct ArtistView: View {
    @Environment(RemoteStore.self) private var store
    let id: String
    let name: String
    let catalog: Bool

    @State private var page: ArtistPage?
    @State private var libraryAlbums: [LibraryAlbum]?
    @State private var failure: String?
    @State private var showAllTop = false
    @State private var showBio = false

    @MainActor private static var cache: [String: ArtistPage] = [:]

    var body: some View {
        Group {
            if let page {
                content(page)
            } else if let libraryAlbums {
                albumsOnly(libraryAlbums)
            } else if let failure {
                EmptyStateView(title: "Couldn't load", detail: failure)
            } else {
                LoadingView()
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .task(id: id) { await load() }
    }

    // MARK: Página del catálogo

    private func content(_ page: ArtistPage) -> some View {
        let bg = (page.videoSquare != nil ? Color(hex: page.videoSquarePreview?.bgColor) : nil)
            ?? PageColor.background(hex: page.heroTall?.bgColor ?? page.artwork?.bgColor, dark: true)
            ?? Color(white: 0.12)
        let secondary = PageColor.readable(on: bg, alpha: 0.62)
        return GeometryReader { geo in
            let w = geo.size.width + geo.safeAreaInsets.leading + geo.safeAreaInsets.trailing
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    header(page, width: w, bg: bg)

                    if let latest = page.latest {
                        NavigationLink(value: latest.route ?? .artist(id: page.id, name: page.name, catalog: true)) {
                            HStack(spacing: 14) {
                                CoverImage(artwork: latest.artwork, pixels: 300, corner: 6)
                                    .frame(width: 110, height: 110)
                                    .zoomSource(latest.route)
                                VStack(alignment: .leading, spacing: 3) {
                                    if let date = latest.releaseDate.flatMap(Self.longDate) {
                                        Text(date).font(.caption.weight(.semibold)).foregroundStyle(secondary)
                                    }
                                    Text(latest.name).font(.headline).lineLimit(2)
                                    if let n = latest.trackCount?.intValue { Text("^[\(n) song](inflect: true)").font(.subheadline).foregroundStyle(secondary) }
                                }
                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, 20)
                            .padding(.top, 18)
                        }
                        .buttonStyle(.plain)
                    }

                    if !page.topSongs.isEmpty {
                        Text(page.topSongsTitle ?? String(localized: "Top Songs"))
                            .font(.title2.bold())
                            .padding(.horizontal, 20)
                            .padding(.top, 26)
                            .padding(.bottom, 6)
                        let shown = showAllTop ? page.topSongs : Array(page.topSongs.prefix(5))
                        ForEach(Array(shown.enumerated()), id: \.offset) { i, song in
                            Button {
                                store.play(page.topSongs.map { PlayRequest(type: $0.type, id: $0.id) }, from: i)
                            } label: {
                                SongRow(title: song.name, subtitle: song.subtitle, artwork: song.artwork, explicit: song.explicit)
                                    .frame(height: 60)
                                    .padding(.horizontal, 20)
                            }
                            .buttonStyle(.plain)
                            .songMenu(type: song.type, id: song.id, name: song.name)
                        }
                        if page.topSongs.count > 5 {
                            Button(showAllTop ? LocalizedStringKey("Show Less") : LocalizedStringKey("See All")) { withAnimation { showAllTop.toggle() } }
                                .font(.subheadline.weight(.semibold))
                                .padding(.horizontal, 20)
                                .padding(.top, 6)
                        }
                    }

                    ForEach(Array(page.shelves.enumerated()), id: \.offset) { _, shelf in
                        ShelfView(shelf: shelf)
                    }

                    if page.bio != nil || page.origin != nil || page.bornOrFormed != nil {
                        about(page, secondary: secondary)
                    }
                }
                .padding(.bottom, 30)
                .foregroundStyle(.white)
            }
            .scrollEdgeEffectStyle(.soft, for: .top)
            .ignoresSafeArea(edges: .top)
        }
        .background(bg.ignoresSafeArea())
        .environment(\.colorScheme, .dark)
        .sheet(isPresented: $showBio) {
            NavigationStack {
                ScrollView {
                    Text(page.bio ?? "")
                        .padding(20)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .navigationTitle(page.name)
                .navigationBarTitleDisplayMode(.inline)
            }
            .presentationDetents([.medium, .large])
        }
    }

    @ViewBuilder
    private func header(_ page: ArtistPage, width: CGFloat, bg: Color) -> some View {
        ZStack(alignment: .bottomLeading) {
            Group {
                if let video = page.videoSquare, let url = URL(string: video) {
                    LoopingVideo(url: url, preview: page.videoSquarePreview?.sized(px: 1200), fallback: page.artwork)
                } else if let hero = page.heroTall?.cropped(w: 1200, h: 1296), let url = URL(string: hero) {
                    AsyncImage(url: url) { phase in
                        if let image = phase.image { image.resizable().scaledToFill() } else { bg }
                    }
                } else if page.artwork != nil {
                    CoverImage(artwork: page.artwork, pixels: 1200, corner: 0)
                } else {
                    bg
                }
            }
            .frame(width: width, height: width * 1.08)
            .clipped()
            fadeInto(bg, from: 0.5)
            HStack(alignment: .bottom) {
                if let logo = page.logo?.png(w: 600, h: 200), let url = URL(string: logo) {
                    AsyncImage(url: url) { phase in
                        if let image = phase.image { image.resizable().scaledToFit() } else { Text(page.name).font(.system(size: 34, weight: .bold)) }
                    }
                    .frame(maxWidth: width * 0.6, maxHeight: 70, alignment: .leading)
                } else {
                    Text(page.name)
                        .font(.system(size: 34, weight: .bold))
                        .lineLimit(2)
                        .minimumScaleFactor(0.6)
                }
                Spacer()
                Button { play(page) } label: {
                    Image(systemName: "play.fill")
                        .font(.system(size: 24))
                        .foregroundStyle(.black)
                        .frame(width: 56, height: 56)
                        .background(Circle().fill(.white))
                }
                .accessibilityLabel("Play")
                .accessibilityIdentifier("playArtist")
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 20)
            .padding(.bottom, 8)
        }
        .frame(width: width, height: width * 1.08)
    }

    private func about(_ page: ArtistPage, secondary: Color) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("About \(page.name)")
                .font(.title2.bold())
            if let bio = page.bio {
                Button { showBio = true } label: {
                    Text(bio)
                        .font(.callout)
                        .lineLimit(4)
                        .multilineTextAlignment(.leading)
                        .foregroundStyle(secondary)
                }
                .buttonStyle(.plain)
            }
            HStack(alignment: .top, spacing: 30) {
                if let origin = page.origin {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(page.bornOrFormed != nil ? LocalizedStringKey("Hometown") : LocalizedStringKey("Origin")).font(.caption.weight(.semibold))
                        Text(origin).font(.callout).foregroundStyle(secondary)
                    }
                }
                if let born = page.bornOrFormed {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Born or Formed").font(.caption.weight(.semibold))
                        Text(born).font(.callout).foregroundStyle(secondary)
                    }
                }
                if let genre = page.genres.first {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Genre").font(.caption.weight(.semibold))
                        Text(genre).font(.callout).foregroundStyle(secondary)
                    }
                }
            }
            .padding(.top, 4)
        }
        .padding(.horizontal, 20)
        .padding(.top, 30)
    }

    // MARK: Artista sin catálogo

    private func albumsOnly(_ albums: [LibraryAlbum]) -> some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 16, alignment: .top), GridItem(.flexible(), spacing: 16, alignment: .top)], spacing: 18) {
                ForEach(albums, id: \.id) { a in
                    CoverTile(title: a.name, subtitle: a.releaseDate.map { String($0.prefix(4)) }, artwork: a.artwork,
                              route: .collection(CollectionRoute(kind: "library-albums", id: a.id, title: a.name, subtitle: a.artistName, artwork: a.artwork)))
                }
            }
            .padding(.horizontal, 20)
        }
        .navigationTitle(name)
    }

    /// `2026-12-01` → "1 dic 2026" (como Apple Music sobre el último lanzamiento).
    private static func longDate(_ iso: String) -> String? {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        guard let d = f.date(from: iso) else { return nil }
        return d.formatted(date: .abbreviated, time: .omitted).uppercased()
    }

    // MARK: Carga y acciones

    private func play(_ page: ArtistPage) {
        let songs = page.topSongs.map { PlayRequest(type: $0.type, id: $0.id) }
        if !songs.isEmpty { store.play(songs, from: 0) }
    }

    private func load() async {
        if let hit = Self.cache[id] { page = hit }
        guard let api = store.api else { return }
        var catalogId: String? = catalog ? id : nil
        if !catalog {
            let info = try? await api.artistInfo(id: id, catalog: false)
            catalogId = info?.relationships?.catalog?.data.first?.id
        }
        if let catalogId {
            do {
                let fresh = try await api.artistPage(catalogId: catalogId)
                page = fresh
                Self.cache[id] = fresh
                return
            } catch {
                if page == nil && catalog { failure = error.localizedDescription }
            }
        }
        if page == nil && !catalog {
            do {
                libraryAlbums = try await api.artistAlbums(id: id, offset: 0, limit: 100).items.compactMap { $0 as? LibraryAlbum }
            } catch {
                failure = error.localizedDescription
            }
        }
    }
}
