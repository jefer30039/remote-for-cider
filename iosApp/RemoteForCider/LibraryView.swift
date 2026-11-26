import Shared
import SwiftUI

/// Biblioteca (primera versión): cuadrícula de álbumes de la biblioteca de Cider.
struct LibraryView: View {
    @Environment(RemoteStore.self) private var store
    @State private var albums: [LibraryAlbum] = []
    @State private var failure: String?

    var body: some View {
        ScrollView {
            if let failure {
                Text(failure)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding()
            }
            LazyVGrid(
                columns: [GridItem(.flexible(), spacing: 16), GridItem(.flexible(), spacing: 16)],
                alignment: .leading,
                spacing: 20
            ) {
                ForEach(albums, id: \.id) { album in
                    VStack(alignment: .leading, spacing: 0) {
                        CoverImage(artwork: album.artwork, pixels: 400, corner: 8)
                            .aspectRatio(1, contentMode: .fit)
                        Text(album.name)
                            .font(.subheadline)
                            .lineLimit(1)
                            .padding(.top, 6)
                        Text(album.artistName)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }
            }
            .padding(.horizontal, 20)
        }
        .navigationTitle("Library")
        .task(id: store.connection) { await load() }
    }

    private func load() async {
        guard let api = store.api else { return }
        do {
            albums = try await api.albums(offset: 0, limit: 100).items
            failure = nil
        } catch {
            failure = error.localizedDescription
        }
    }
}
