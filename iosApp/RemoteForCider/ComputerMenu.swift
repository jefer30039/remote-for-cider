import Shared
import SwiftUI

/// Ajustes de audio de Cider y graves de la bocina (servicio auxiliar del PC), leídos al abrir el
/// menú del equipo y cambiados al momento; si Cider falla, se vuelven a leer.
@MainActor
@Observable
final class AudioSettingsModel {
    private(set) var state: AudioState?
    private(set) var speaker: SpeakerMode?
    var seconds: Double = 5

    func load(_ api: CiderApi?) async {
        guard let api else { return }
        if let fresh = try? await api.audioState() {
            state = fresh
            seconds = Double(fresh.crossfadeSeconds)
        }
        speaker = try? await api.speakerMode()
    }

    func setCrossfade(_ on: Bool, _ api: CiderApi?) {
        run(api) { try await $0.setCrossfade(enabled: on) }
    }

    func commitSeconds(_ api: CiderApi?) {
        let s = Int32(seconds.rounded())
        run(api) { try await $0.setCrossfadeSeconds(seconds: s) }
    }

    func setAtmos(_ on: Bool, _ api: CiderApi?) {
        let mode = state?.atmosMode ?? "binaural"
        run(api) { try await $0.setAtmos(on: on, mode: mode) }
    }

    func setListeningMode(_ mode: String, _ api: CiderApi?) {
        run(api) { try await $0.setListeningMode(mode: mode) }
    }

    func setBass(_ on: Bool, _ api: CiderApi?) {
        guard let api else { return }
        Task {
            speaker = (try? await api.setSpeakerBass(on: on)) ?? speaker
        }
    }

    private func run(_ api: CiderApi?, _ block: @escaping (CiderApi) async throws -> Void) {
        guard let api else { return }
        Task {
            try? await block(api)
            await load(api)
        }
    }
}

/// Botón del equipo de la hoja (portátil): nombre del PC, crossfade, Dolby Atmos, modo de escucha y
/// "Graves para la bocina". Abre una hoja pequeña del sistema (los menús no admiten deslizadores).
struct ComputerButton: View {
    @Environment(RemoteStore.self) private var store
    @State private var open = false
    @State private var model = AudioSettingsModel()
    let label: AnyView

    var body: some View {
        Button { open = true } label: { label }
            .buttonStyle(.plain)
            .accessibilityLabel("Computer")
            .accessibilityIdentifier("computerButton")
            .sheet(isPresented: $open) {
                ComputerSheet(model: model)
                    .presentationDetents([.medium, .large])
                    .presentationBackground(.thinMaterial)
            }
    }
}

private struct ComputerSheet: View {
    @Environment(RemoteStore.self) private var store
    @Bindable var model: AudioSettingsModel

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Label {
                        VStack(alignment: .leading) {
                            Text("Cider")
                            if let c = store.connection {
                                Text(verbatim: c.host).font(.footnote).foregroundStyle(.secondary)
                            }
                        }
                    } icon: {
                        Image(systemName: "laptopcomputer")
                    }
                }
                if let s = model.state {
                    Section {
                        if let on = s.crossfade?.boolValue {
                            Toggle("Crossfade", isOn: Binding(get: { on }, set: { model.setCrossfade($0, store.api) }))
                            if on {
                                VStack(alignment: .leading) {
                                    Text("\(Int(model.seconds.rounded())) s").font(.footnote).foregroundStyle(.secondary)
                                    Slider(value: $model.seconds, in: 1...12, step: 1, onEditingChanged: { editing in
                                        if !editing { model.commitSeconds(store.api) }
                                    })
                                }
                            }
                        }
                        if let on = s.atmos?.boolValue {
                            Toggle("Dolby Atmos", isOn: Binding(get: { on }, set: { model.setAtmos($0, store.api) }))
                        }
                    } footer: {
                        if s.atmos != nil { Text("Cider applies Dolby Atmos from the next song.") }
                    }
                    if let mode = s.listeningMode {
                        Section("Listening Mode") {
                            Picker("Listening Mode", selection: Binding(get: { mode }, set: { model.setListeningMode($0, store.api) })) {
                                Text("Off").tag("off")
                                Text("Game").tag("game")
                                Text("Anti-fatigue").tag("antifatigue")
                            }
                            .pickerStyle(.segmented)
                        }
                    }
                } else {
                    LoadingView()
                }
                if let speaker = model.speaker, speaker.available {
                    Section {
                        Toggle("Bass for the Speaker", isOn: Binding(get: { speaker.bass }, set: { model.setBass($0, store.api) }))
                    } footer: {
                        Text("Switches EasyEffects on the computer between the Bocina and Normal presets.")
                    }
                }
            }
            .navigationTitle("Computer")
            .navigationBarTitleDisplayMode(.inline)
        }
        .task { await model.load(store.api) }
    }
}

// MARK: - Temporizador

/// Temporizador de apagado: pausa Cider a los N minutos o al acabar la canción (sondea cada 0,4 s y
/// pausa a menos de 0,9 s del final, como Android).
@MainActor
@Observable
final class SleepTimer {
    private(set) var endsAt: Date?
    private(set) var endOfTrack = false
    @ObservationIgnored private var task: Task<Void, Never>?

    var active: Bool { endsAt != nil || endOfTrack }

    func set(minutes: Int?, store: RemoteStore) {
        task?.cancel()
        endsAt = nil
        endOfTrack = false
        guard let minutes else { return }
        if minutes == 0 {
            endOfTrack = true
            task = Task { [weak self] in
                while !Task.isCancelled {
                    try? await Task.sleep(for: .milliseconds(400))
                    guard let p = store.player?.progress, p.durationSec > 0 else { continue }
                    if p.playing && p.durationSec - p.positionNow() < 0.9 {
                        store.remote?.pause()
                        self?.endOfTrack = false
                        return
                    }
                }
            }
        } else {
            let end = Date().addingTimeInterval(Double(minutes) * 60)
            endsAt = end
            task = Task { [weak self] in
                try? await Task.sleep(for: .seconds(Double(minutes) * 60))
                guard !Task.isCancelled else { return }
                store.remote?.pause()
                self?.endsAt = nil
            }
        }
    }
}
