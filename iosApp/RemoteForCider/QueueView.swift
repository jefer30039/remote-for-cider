import Shared
import SwiftUI

/// "A continuación" de la hoja: lo que sigue tras la canción actual, como la cola de Apple Music en
/// iOS 26. Arriba, aleatorio · repetir · ∞ (reproducción automática) · AutoMix en cápsulas; debajo,
/// las canciones: tocar salta a ella, mantener y arrastrar la cambia de sitio y deslizar a la
/// izquierda la quita. Se vuelve a leer con cada aviso de cola de Cider.
struct QueuePanel: View {
    @Environment(RemoteStore.self) private var store
    let player: PlayerState?
    /// La primera fila queda a esta distancia del borde de arriba de la vista (bajo la cabecera).
    let top: CGFloat

    @State private var model = QueueModel()

    var body: some View {
        List {
            Color.clear.frame(height: max(top - 12, 0))
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
                .moveDisabled(true)
                .deleteDisabled(true)

            modes
                .listRowInsets(EdgeInsets(top: 0, leading: 28, bottom: 14, trailing: 24))
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
                .moveDisabled(true)
                .deleteDisabled(true)

            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 1) {
                    Text("Playing Next")
                        .font(.system(size: 17, weight: .semibold))
                        .vibrant(0.75)
                    if let rows = model.rows, !rows.isEmpty {
                        Group {
                            if rows.count >= Int(QueueModel.window) {
                                Text("\(rows.count)+ songs")
                            } else {
                                Text("^[\(rows.count) song](inflect: true)")
                            }
                        }
                        .font(.system(size: 13))
                        .vibrant(0.47)
                    }
                }
                Spacer()
                if let rows = model.rows, !rows.isEmpty {
                    Button("Clear") { model.clear(store) }
                        .font(.system(size: 15, weight: .semibold))
                        .buttonStyle(.plain)
                        .vibrant(0.75)
                        .accessibilityIdentifier("queueClear")
                }
            }
            .listRowInsets(EdgeInsets(top: 0, leading: 28, bottom: 6, trailing: 28))
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
            .moveDisabled(true)
            .deleteDisabled(true)

            if let rows = model.rows {
                if rows.isEmpty {
                    Text("Nothing up next")
                        .font(.system(size: 15))
                        .vibrant(0.47)
                        .listRowInsets(EdgeInsets(top: 8, leading: 28, bottom: 8, trailing: 28))
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                        .moveDisabled(true)
                        .deleteDisabled(true)
                }
                ForEach(rows) { row in
                    QueueRowView(row: row)
                        .contentShape(Rectangle())
                        .onTapGesture { model.jump(to: row, store) }
                        .listRowInsets(EdgeInsets(top: 0, leading: 28, bottom: 0, trailing: 18))
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) { model.remove(row, store) } label: {
                                Label("Remove", systemImage: "trash")
                            }
                        }
                }
                .onMove { from, to in model.move(from: from, to: to, store) }
            } else {
                ProgressView()
                    .tint(.white.opacity(0.6))
                    .frame(maxWidth: .infinity)
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
            }

            Color.clear.frame(height: 24)
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
                .moveDisabled(true)
                .deleteDisabled(true)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .environment(\.defaultMinListRowHeight, 1)
        .accessibilityIdentifier("queueList")
        .task(id: store.connection) { await model.loadModes(store) }
        // Cada aviso de cola o de canción nueva (y al abrir) vuelve a leerla.
        .task(id: store.queueRevision) { await model.reload(store) }
    }

    /// Aleatorio · repetir · ∞ · AutoMix: cuatro cápsulas iguales a todo el ancho (iOS 26).
    private var modes: some View {
        HStack(spacing: 10) {
            ModeCapsule(symbol: "shuffle", label: "Shuffle", on: player?.shuffle == true) { store.toggleShuffle() }
                .accessibilityIdentifier("queueShuffle")
            ModeCapsule(
                symbol: player?.repeatMode == .one ? "repeat.1" : "repeat",
                label: "Repeat",
                on: player.map { $0.repeatMode != Shared.RepeatMode.none } ?? false
            ) { store.cycleRepeat() }
                .accessibilityIdentifier("queueRepeat")
            ModeCapsule(symbol: "infinity", label: "Autoplay", on: model.autoplay == true) { model.toggleAutoplay(store) }
                .disabled(model.autoplay == nil)
                .accessibilityIdentifier("queueAutoplay")
            ModeCapsule(symbol: "arrow.trianglehead.merge", label: "AutoMix", on: model.automix == true) { model.toggleAutomix(store) }
                .disabled(model.automix == nil)
                .accessibilityIdentifier("queueAutomix")
        }
        .frame(height: 34)
    }
}

/// Cápsula de un modo: apagada, blanco translúcido sumado al fondo; encendida, blanca con el icono
/// recortado (se ve el color de detrás), como en Apple Music.
private struct ModeCapsule: View {
    let symbol: String
    let label: LocalizedStringKey
    let on: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                Capsule().fill(.white.opacity(on ? 0.85 : 0.14))
                Image(systemName: symbol)
                    .font(.system(size: 15, weight: .semibold))
                    .blendMode(on ? .destinationOut : .normal)
                    .foregroundStyle(.white.opacity(on ? 1 : 0.8))
            }
            .compositingGroup()
            .blendMode(.plusLighter)
            .frame(maxWidth: .infinity)
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityAddTraits(on ? .isSelected : [])
        .animation(.easeOut(duration: 0.2), value: on)
    }
}

private struct QueueRowView: View {
    let row: QueueRow

    var body: some View {
        let t = row.track
        HStack(spacing: 12) {
            CoverImage(artwork: t?.artwork, pixels: 120, corner: 5)
                .frame(width: 48, height: 48)
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: 4) {
                    Text(t?.name ?? row.trackId)
                        .font(.system(size: 16))
                        .lineLimit(1)
                        .vibrant(0.85)
                    if t?.isExplicit == true {
                        Image(systemName: "e.square.fill")
                            .font(.system(size: 10))
                            .vibrant(0.4)
                    }
                }
                Text(t?.artistName ?? "")
                    .font(.system(size: 14))
                    .lineLimit(1)
                    .vibrant(0.47)
            }
            Spacer(minLength: 0)
            Image(systemName: "line.3.horizontal")
                .font(.system(size: 17))
                .vibrant(0.4)
                .frame(width: 30, height: 44)
                .accessibilityHidden(true)
        }
        .frame(height: 60)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("queueRow")
    }
}

struct QueueRow: Identifiable, Equatable {
    /// Índice en la cola de Cider (absoluto) e id: dos canciones iguales no se confunden.
    let index: Int
    let trackId: String
    let type: String
    let track: Track?

    var id: String { "\(index):\(trackId)" }
    static func == (a: QueueRow, b: QueueRow) -> Bool { a.index == b.index && a.trackId == b.trackId }
}

@MainActor
@Observable
final class QueueModel {
    /// Cuántas siguientes se muestran (como Android: cada elemento pesa ~7 KB).
    static let window: Int32 = 100

    private(set) var rows: [QueueRow]?
    private(set) var autoplay: Bool?
    private(set) var automix: Bool?
    /// Mientras se arrastra una fila o se espera la respuesta de un cambio, no se recarga encima.
    @ObservationIgnored private var busy = false
    @ObservationIgnored private var refill: Task<Void, Never>?

    func reload(_ store: RemoteStore) async {
        guard !busy, let api = store.api else { return }
        do {
            let start = try await api.queuePosition().position + 1
            let page = try await api.queue(offset: start, limit: Self.window).first
            let items = (page?.items ?? []).enumerated().map { i, item in
                QueueRow(index: Int(start) + i, trackId: item.track.id, type: item.track.type, track: item.track.attributes)
            }
            if !busy { rows = items }
        } catch {
            if rows == nil { rows = [] }
        }
    }

    func loadModes(_ store: RemoteStore) async {
        guard let api = store.api else { return }
        autoplay = (try? await api.autoplay())?.boolValue
        automix = (try? await api.automixEnabled())?.boolValue
    }

    func jump(to row: QueueRow, _ store: RemoteStore) {
        guard let api = store.api else { return }
        Task { try? await api.queueJump(index: Int32(row.index)) }
    }

    func remove(_ row: QueueRow, _ store: RemoteStore) {
        guard let api = store.api else { return }
        rows?.removeAll { $0 == row }
        busy = true
        Task {
            try? await api.queueRemove(index: Int32(row.index))
            busy = false
            await reload(store)
        }
    }

    /// `onMove` de SwiftUI: `to` es la posición en la lista de antes de mover.
    func move(from: IndexSet, to: Int, _ store: RemoteStore) {
        guard let api = store.api, var list = rows, let src = from.first, src < list.count else { return }
        let dst = to > src ? to - 1 : to
        guard dst != src else { return }
        let start = list.first?.index ?? 0
        list.move(fromOffsets: from, toOffset: to)
        // Los índices vuelven a ser consecutivos desde la primera (hasta recargar).
        rows = list.enumerated().map { i, r in QueueRow(index: start + i, trackId: r.trackId, type: r.type, track: r.track) }
        busy = true
        Task {
            try? await api.queueMove(from: Int32(start + src), to: Int32(start + dst))
            busy = false
            await reload(store)
        }
    }

    /// "Borrar": vacía lo que viene después. Con ∞ activado Cider rellena con sugerencias a los pocos
    /// segundos **sin mandar evento**: se vuelve a leer a 1,5, 4 y 8 s.
    func clear(_ store: RemoteStore) {
        guard let remote = store.remote else { return }
        rows = []
        busy = true
        refill?.cancel()
        refill = Task {
            try? await remote.clearUpNext()
            busy = false
            await reload(store)
            for wait in [1.5, 2.5, 4.0] {
                try? await Task.sleep(for: .seconds(wait))
                if Task.isCancelled { return }
                await reload(store)
            }
        }
    }

    func toggleAutoplay(_ store: RemoteStore) {
        guard let api = store.api, let on = autoplay else { return }
        autoplay = !on
        Task {
            do { try await api.setAutoplay(enabled: !on) } catch { await loadModes(store) }
        }
    }

    func toggleAutomix(_ store: RemoteStore) {
        guard let api = store.api, let on = automix else { return }
        automix = !on
        Task {
            do {
                try await api.setAutomix(enabled: !on)
            } catch {
                await loadModes(store)
            }
        }
    }
}
