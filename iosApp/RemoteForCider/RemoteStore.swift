import Foundation
import Observation
import Shared

/// Conexión guardada con un Cider (o el puente, puerto 10768).
struct Connection: Equatable {
    var host: String
    var port: Int
    var token: String

    static let defaultPort = 10768

    /// Desde UserDefaults; los argumentos de lanzamiento (`-ciderHost 127.0.0.1 -ciderToken demo`)
    /// entran en el mismo dominio, así el simulador de CI arranca ya conectado al Cider de demostración.
    static func saved() -> Connection? {
        let d = UserDefaults.standard
        guard let host = d.string(forKey: "ciderHost"), !host.isEmpty,
              let token = d.string(forKey: "ciderToken"), !token.isEmpty else { return nil }
        let port = d.integer(forKey: "ciderPort")
        return Connection(host: host, port: port == 0 ? defaultPort : port, token: token)
    }

    func save() {
        let d = UserDefaults.standard
        d.set(host, forKey: "ciderHost")
        d.set(port, forKey: "ciderPort")
        d.set(token, forKey: "ciderToken")
    }

    static func forget() {
        let d = UserDefaults.standard
        for key in ["ciderHost", "ciderPort", "ciderToken"] { d.removeObject(forKey: key) }
    }
}

/// Estado de la app para SwiftUI: envuelve el `CiderRemote` de shared y publica su `PlayerState`.
@MainActor
@Observable
final class RemoteStore {
    enum LinkStatus: Equatable {
        case connecting
        case online
        case offline(String?)
    }

    private(set) var connection: Connection?
    private(set) var player: PlayerState?
    private(set) var link: LinkStatus = .connecting
    /// Tras perder la conexión el silencio sigue un rato (si no, en segundo plano iOS suspendería la
    /// app y no volvería a conectar); si Cider se cerró de verdad, se corta y no gasta batería.
    @ObservationIgnored private var lockScreenGrace: Task<Void, Never>?
    private static let lockScreenGraceSeconds: UInt64 = 60

    @ObservationIgnored private var session: CiderSession?
    @ObservationIgnored private var observer: Task<Void, Never>?
    @ObservationIgnored private lazy var lockScreen = LockScreenPlayer(commands: .init(
        togglePlay: { [weak self] in self?.togglePlay() },
        play: { [weak self] in if self?.player?.isPlaying == false { self?.togglePlay() } },
        pause: { [weak self] in self?.remote?.pause() },
        next: { [weak self] in self?.next() },
        previous: { [weak self] in self?.previous() },
        seek: { [weak self] in self?.seek(to: $0) }
    ))

    var api: CiderApi? { session?.api }
    var remote: CiderRemote? { session?.remote }

    init() {
        if let saved = Connection.saved() { connect(saved) }
    }

    func connect(_ connection: Connection) {
        disconnect()
        self.connection = connection
        let session = CiderSession(host: connection.host, port: Int32(connection.port), token: connection.token)
        self.session = session
        session.start()
        observer = Task { [weak self] in
            for await state in session.remote.state {
                self?.apply(state)
            }
        }
    }

    func disconnect() {
        observer?.cancel()
        observer = nil
        session?.close()
        session = nil
        player = nil
        link = .connecting
        lockScreenGrace?.cancel()
        lockScreenGrace = nil
        lockScreen.setActive(false)
    }

    func forget() {
        disconnect()
        connection = nil
        Connection.forget()
    }

    private func apply(_ state: PlayerState) {
        player = state
        switch onEnum(of: state.link) {
        case .connecting: link = .connecting
        case .online: link = .online
        case .offline(let offline): link = .offline(offline.reason)
        }
        // Silencio y reproductor del sistema mientras hay conexión (también con Cider en pausa).
        if link == .online {
            lockScreenGrace?.cancel()
            lockScreenGrace = nil
            lockScreen.setActive(true)
        } else if lockScreenGrace == nil {
            lockScreenGrace = Task { [weak self] in
                try? await Task.sleep(nanoseconds: Self.lockScreenGraceSeconds * 1_000_000_000)
                guard !Task.isCancelled, let self else { return }
                self.lockScreenGrace = nil
                if self.link != .online { self.lockScreen.setActive(false) }
            }
        }
        lockScreen.update(state)
    }

    // MARK: - Comandos (optimistas en shared; el socket corrige)

    func togglePlay() { remote?.togglePlay() }
    func next() { remote?.next() }
    func previous() { remote?.previous() }
    func seek(to seconds: Double) { remote?.seek(seconds: seconds) }
    func setVolume(_ volume: Double) { remote?.setVolume(volume: volume) }
    func toggleShuffle() { remote?.toggleShuffle() }
    func cycleRepeat() { remote?.cycleRepeat() }
}
