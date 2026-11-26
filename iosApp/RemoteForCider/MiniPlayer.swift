import SwiftUI

/// Miniplayer en el accesorio inferior del `TabView`: con la barra minimizada (`.inline`) solo
/// caben carátula, título y reproducir; desplegado lleva también el artista y "siguiente".
struct MiniPlayer: View {
    @Environment(RemoteStore.self) private var store
    @Environment(\.tabViewBottomAccessoryPlacement) private var placement
    var open: () -> Void

    var body: some View {
        let track = store.player?.track
        let playing = store.player?.isPlaying ?? false
        let enabled = track != nil && store.link == .online
        HStack(spacing: 10) {
            CoverImage(artwork: track?.artwork, pixels: 120, corner: 6)
                .frame(width: 30, height: 30)
            VStack(alignment: .leading, spacing: 0) {
                Text(track?.name ?? status)
                    .font(.subheadline.weight(.medium))
                    .lineLimit(1)
                if placement != .inline, let artist = track?.artistName, !artist.isEmpty {
                    Text(artist)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 0)
            Button { store.togglePlay() } label: {
                Image(systemName: playing ? "pause.fill" : "play.fill")
                    .font(.title3)
                    .contentTransition(.symbolEffect(.replace))
                    .frame(width: 34, height: 34)
            }
            if placement != .inline {
                Button { store.next() } label: {
                    Image(systemName: "forward.fill")
                        .font(.title3)
                        .frame(width: 34, height: 34)
                }
            }
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .padding(.leading, 10)
        .padding(.trailing, 6)
        .contentShape(Rectangle())
        .onTapGesture { if track != nil { open() } }
    }

    private var status: String {
        switch store.link {
        case .connecting: String(localized: "Connecting…")
        case .offline: String(localized: "Can't reach Cider")
        case .online: String(localized: "Not Playing")
        }
    }
}
