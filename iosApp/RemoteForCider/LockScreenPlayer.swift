import AVFoundation
import MediaPlayer
import Shared
import UIKit

/// Reproductor del sistema (pantalla de bloqueo, Centro de Control, Dynamic Island).
///
/// iOS solo le da ese reproductor a la app que está sonando, así que mientras hay conexión con Cider
/// se reproduce **silencio en bucle** (categoría `.playback`, sin mezclar con otras apps). Eso además
/// mantiene viva la app en segundo plano y con ella el socket. Si otra app empieza a sonar en el
/// iPhone, se queda con el reproductor: es lo esperable. No va a la App Store (la revisión lo rechazaría).
@MainActor
final class LockScreenPlayer {
    struct Commands {
        var togglePlay: () -> Void
        var play: () -> Void
        var pause: () -> Void
        var next: () -> Void
        var previous: () -> Void
        var seek: (Double) -> Void
    }

    private let commands: Commands
    private var silence: AVAudioPlayer?
    private var active = false
    private var shownKey: String?
    private var shownPlaying: Bool?
    private var shownPosition: Double = 0
    private var shownAt = Date.distantPast
    private var artworkURL: String?
    private var artwork: MPMediaItemArtwork?
    private var observers: [NSObjectProtocol] = []

    init(commands: Commands) {
        self.commands = commands
        registerCommands()
        let center = NotificationCenter.default
        observers.append(center.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] note in
            let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt
            MainActor.assumeIsolated {
                // Al acabar la interrupción (llamada, Siri…) se vuelve a poner el silencio.
                if raw == AVAudioSession.InterruptionType.ended.rawValue, self?.active == true { self?.startSilence() }
            }
        })
        observers.append(center.addObserver(forName: AVAudioSession.mediaServicesWereResetNotification, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated {
                self?.silence = nil
                if self?.active == true { self?.startSilence() }
            }
        })
    }

    /// Conectado a Cider: suena el silencio aunque Cider esté en pausa (así no se pierde el reproductor).
    func setActive(_ value: Bool) {
        guard value != active else { return }
        active = value
        if value {
            startSilence()
        } else {
            silence?.stop()
            Self.audioQueue.async { try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation) }
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            MPNowPlayingInfoCenter.default().playbackState = .stopped
            shownKey = nil
            shownPlaying = nil
        }
    }

    /// Se llama con cada `PlayerState`; solo toca el centro de "Now Playing" si cambia algo visible
    /// (canción, pausa o un salto de posición), no con cada tic de un segundo.
    func update(_ state: PlayerState) {
        guard active, let track = state.track else {
            if active { MPNowPlayingInfoCenter.default().nowPlayingInfo = nil }
            shownKey = nil
            return
        }
        let key = track.playParams?.id ?? "\(track.name)|\(track.artistName)"
        let playing = state.isPlaying
        let position = state.progress.positionNow()
        let expected = shownPosition + (shownPlaying == true ? Date().timeIntervalSince(shownAt) : 0)
        guard key != shownKey || playing != shownPlaying || abs(position - expected) > 2 else { return }

        if key != shownKey { loadArtwork(track.artwork?.sized(px: 1000)) }
        shownKey = key
        shownPlaying = playing
        shownPosition = position
        shownAt = Date()

        var info: [String: Any] = [
            MPMediaItemPropertyTitle: track.name,
            MPMediaItemPropertyArtist: track.artistName,
            MPMediaItemPropertyAlbumTitle: track.albumName,
            MPMediaItemPropertyPlaybackDuration: state.progress.durationSec,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: position,
            MPNowPlayingInfoPropertyPlaybackRate: playing ? 1.0 : 0.0,
            MPNowPlayingInfoPropertyDefaultPlaybackRate: 1.0,
            MPNowPlayingInfoPropertyMediaType: MPNowPlayingInfoMediaType.audio.rawValue,
        ]
        if let artwork { info[MPMediaItemPropertyArtwork] = artwork }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        MPNowPlayingInfoCenter.default().playbackState = playing ? .playing : .paused
    }

    // MARK: - Silencio

    /// La sesión de audio se activa fuera del hilo principal: `setActive` es síncrono y puede tardar
    /// (iOS lo marca como "AVAudioSession Hang Risk" si se hace en el principal).
    private static let audioQueue = DispatchQueue(label: "silencio", qos: .userInitiated)

    private func startSilence() {
        Self.audioQueue.async { [weak self] in
            do {
                let session = AVAudioSession.sharedInstance()
                try session.setCategory(.playback, mode: .default, options: [])
                try session.setActive(true)
            } catch {
                print("LockScreenPlayer: no se pudo activar la sesión de audio: \(error)")
                return
            }
            DispatchQueue.main.async {
                MainActor.assumeIsolated {
                    guard let self, self.active else { return }
                    do {
                        if self.silence == nil {
                            self.silence = try AVAudioPlayer(data: Self.silentWav(seconds: 2))
                            self.silence?.numberOfLoops = -1
                        }
                        self.silence?.play()
                    } catch {
                        print("LockScreenPlayer: no se pudo poner el silencio: \(error)")
                    }
                }
            }
        }
    }

    /// WAV PCM de 16 bits, mono, 22 050 Hz, todo ceros.
    private static func silentWav(seconds: Double) -> Data {
        let rate: UInt32 = 22_050
        let samples = UInt32(Double(rate) * seconds)
        let dataSize = samples * 2
        var d = Data()
        func put32(_ v: UInt32) { withUnsafeBytes(of: v.littleEndian) { d.append(contentsOf: $0) } }
        func put16(_ v: UInt16) { withUnsafeBytes(of: v.littleEndian) { d.append(contentsOf: $0) } }
        d.append(contentsOf: Array("RIFF".utf8)); put32(36 + dataSize)
        d.append(contentsOf: Array("WAVE".utf8))
        d.append(contentsOf: Array("fmt ".utf8)); put32(16); put16(1); put16(1)
        put32(rate); put32(rate * 2); put16(2); put16(16)
        d.append(contentsOf: Array("data".utf8)); put32(dataSize)
        d.append(Data(count: Int(dataSize)))
        return d
    }

    // MARK: - Carátula

    private func loadArtwork(_ url: String?) {
        guard url != artworkURL else { return }
        artworkURL = url
        artwork = nil
        guard let url, let u = URL(string: url) else { return }
        Task { [weak self] in
            guard let (data, _) = try? await URLSession.shared.data(from: u), let image = UIImage(data: data) else { return }
            guard let self, self.artworkURL == url else { return }
            let art = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
            self.artwork = art
            var info = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
            info[MPMediaItemPropertyArtwork] = art
            MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        }
    }

    // MARK: - Mandos del sistema → Cider

    private func registerCommands() {
        let center = MPRemoteCommandCenter.shared()
        center.togglePlayPauseCommand.addTarget { [weak self] _ in self?.commands.togglePlay(); return .success }
        center.playCommand.addTarget { [weak self] _ in self?.commands.play(); return .success }
        center.pauseCommand.addTarget { [weak self] _ in self?.commands.pause(); return .success }
        center.nextTrackCommand.addTarget { [weak self] _ in self?.commands.next(); return .success }
        center.previousTrackCommand.addTarget { [weak self] _ in self?.commands.previous(); return .success }
        center.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            self?.commands.seek(event.positionTime)
            return .success
        }
    }
}
