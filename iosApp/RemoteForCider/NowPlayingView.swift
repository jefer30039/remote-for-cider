import Shared
import SwiftUI

/// Hoja de reproducción (primera versión): carátula, título, progreso, controles y volumen sobre la
/// carátula desenfocada. El vuelo desde el miniplayer, la portada animada y la letra vienen después.
struct NowPlayingView: View {
    @Environment(RemoteStore.self) private var store
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let player = store.player
        let track = player?.track
        let playing = player?.isPlaying ?? false
        GeometryReader { geo in
            let side = geo.size.width - 2 * Layout.margin
            VStack(spacing: 0) {
                Capsule()
                    .fill(.white.opacity(0.35))
                    .frame(width: 36, height: 5)
                    .padding(.top, 6)
                Spacer(minLength: 12)
                CoverImage(artwork: track?.artwork, pixels: 1200, corner: 12)
                    .frame(width: side, height: side)
                    .shadow(color: .black.opacity(0.35), radius: 24, y: 12)
                    // Como iOS: la carátula se encoge al pausar.
                    .scaleEffect(playing ? 1 : 0.78)
                    .animation(.spring(response: 0.45, dampingFraction: 0.72), value: playing)
                Spacer(minLength: 24)
                VStack(spacing: 0) {
                    titleRow(track)
                    TimelineView(.periodic(from: .now, by: 0.25)) { _ in
                        ScrubBar(
                            position: player?.progress.positionNow() ?? 0,
                            duration: player?.progress.durationSec ?? 0
                        ) { store.seek(to: $0) }
                    }
                    .padding(.top, 18)
                    controls(playing: playing)
                        .padding(.top, 26)
                    VolumeBar(volume: player?.volume ?? 0) { store.setVolume($0) }
                        .padding(.top, 30)
                    bottomRow
                        .padding(.top, 26)
                }
                .padding(.horizontal, Layout.margin)
                .padding(.bottom, 12)
            }
            .frame(maxWidth: .infinity)
        }
        .foregroundStyle(.white)
        .background { Backdrop(artwork: track?.artwork) }
        .gesture(
            DragGesture(minimumDistance: 20).onEnded { value in
                if value.translation.height > 120 { dismiss() }
            }
        )
    }

    private func titleRow(_ track: Track?) -> some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(track?.name ?? "")
                        .font(.title3.weight(.semibold))
                        .lineLimit(1)
                    if track?.isExplicit == true {
                        Image(systemName: "e.square.fill")
                            .font(.footnote)
                            .foregroundStyle(.white.opacity(0.6))
                    }
                }
                Text(track?.artistName ?? "")
                    .font(.title3)
                    .foregroundStyle(.white.opacity(0.6))
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
            Button {} label: {
                Image(systemName: "ellipsis")
                    .font(.body.weight(.semibold))
                    .frame(width: 31, height: 31)
                    .background(.white.opacity(0.15), in: Circle())
            }
        }
    }

    private func controls(playing: Bool) -> some View {
        HStack {
            Spacer()
            Button { store.previous() } label: {
                Image(systemName: "backward.fill").font(.system(size: 34))
            }
            Spacer()
            Button { store.togglePlay() } label: {
                Image(systemName: playing ? "pause.fill" : "play.fill")
                    .font(.system(size: 44))
                    .contentTransition(.symbolEffect(.replace))
                    .frame(width: 60, height: 60)
            }
            .accessibilityIdentifier("playPause")
            Spacer()
            Button { store.next() } label: {
                Image(systemName: "forward.fill").font(.system(size: 34))
            }
            Spacer()
        }
        .buttonStyle(.plain)
    }

    private var bottomRow: some View {
        HStack {
            Image(systemName: "quote.bubble")
            Spacer()
            Image(systemName: "airplay.audio")
            Spacer()
            Image(systemName: "list.bullet")
        }
        .font(.title3)
        .foregroundStyle(.white.opacity(0.6))
        .padding(.horizontal, 30)
    }

    enum Layout {
        static let margin: CGFloat = 32
    }
}

/// Fondo provisional: la carátula muy desenfocada y oscurecida (el campo de color de Apple Music va después).
private struct Backdrop: View {
    let artwork: Shared.Artwork?

    var body: some View {
        ZStack {
            Color(hex: artwork?.bgColor) ?? .black
            CoverImage(artwork: artwork, pixels: 120, corner: 0)
                .scaleEffect(1.6)
                .blur(radius: 70)
                .opacity(0.9)
            Color.black.opacity(0.28)
        }
        .ignoresSafeArea()
    }
}

/// Barra de progreso de Apple Music: fina, se ensancha al arrastrar; tiempos debajo.
struct ScrubBar: View {
    let position: Double
    let duration: Double
    let onSeek: (Double) -> Void
    @State private var dragging: Double?

    var body: some View {
        let shown = dragging ?? position
        VStack(spacing: 6) {
            GeometryReader { g in
                let fraction = duration > 0 ? min(max(shown / duration, 0), 1) : 0
                ZStack(alignment: .leading) {
                    Capsule().fill(.white.opacity(0.2))
                    Capsule().fill(.white.opacity(dragging == nil ? 0.6 : 0.9))
                        .frame(width: g.size.width * fraction)
                }
                .frame(height: dragging == nil ? 7 : 12)
                .frame(maxHeight: .infinity)
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
            .frame(height: 20)
            HStack {
                Text(formatTime(shown))
                Spacer()
                Text("-" + formatTime(max(0, duration - shown)))
            }
            .font(.caption.weight(.medium).monospacedDigit())
            .foregroundStyle(.white.opacity(0.45))
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.8), value: dragging == nil)
    }
}

/// Volumen de Cider (no el del teléfono).
struct VolumeBar: View {
    let volume: Double
    let onChange: (Double) -> Void
    @State private var dragging: Double?

    var body: some View {
        let shown = dragging ?? volume
        HStack(spacing: 14) {
            Image(systemName: "speaker.fill").font(.footnote)
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Capsule().fill(.white.opacity(0.2))
                    Capsule().fill(.white.opacity(dragging == nil ? 0.6 : 0.9))
                        .frame(width: g.size.width * min(max(shown, 0), 1))
                }
                .frame(height: dragging == nil ? 7 : 12)
                .frame(maxHeight: .infinity)
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
            .frame(height: 20)
            Image(systemName: "speaker.wave.3.fill").font(.footnote)
        }
        .foregroundStyle(.white.opacity(0.45))
        .animation(.spring(response: 0.3, dampingFraction: 0.8), value: dragging == nil)
    }
}
