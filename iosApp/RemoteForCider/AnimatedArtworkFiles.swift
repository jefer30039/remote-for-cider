import AVFoundation
import CryptoKit
import Foundation
import UIKit

/// Archivos locales de las portadas animadas para la pantalla de bloqueo. iOS solo acepta un vídeo
/// **local** (ni HLS ni URLs remotas) y lo pide cuando lo va a enseñar; Apple aconseja no bajarlo antes.
///
/// Las portadas de Apple son HLS: la lista maestra enumera variantes y la lista de cada una apunta a un
/// único MP4 fragmentado (`#EXT-X-MAP` + trozos por rango de bytes del mismo archivo), así que basta con
/// bajar ese archivo entero (~5 MB a 664 × 886). Si una variante viniera en archivos sueltos, se
/// concatenan cabecera y trozos (un MP4 fragmentado sigue siendo válido así). Se guardan en Caches.
enum AnimatedArtworkFiles {
    /// Ancho máximo de la variante elegida: la pantalla de bloqueo la amplía, pero más ancho son
    /// muchos más megas por canción.
    private static let maxWidth = 900
    private static let keep = 12

    private static var folder: URL {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("portadas-animadas", isDirectory: true)
    }

    private static func name(_ url: URL) -> String {
        SHA256.hash(data: Data(url.absoluteString.utf8)).prefix(12).map { String(format: "%02x", $0) }.joined()
    }

    /// El MP4 local de una portada animada (HLS de Apple), bajándolo si hace falta. `nil` si falla.
    static func localFile(hls: URL) async -> URL? {
        let target = folder.appendingPathComponent(name(hls) + ".mp4")
        if FileManager.default.fileExists(atPath: target.path) {
            try? FileManager.default.setAttributes([.modificationDate: Date()], ofItemAtPath: target.path)
            return target
        }
        do {
            guard let media = try await pickVariant(hls) else { return nil }
            let parts = try await files(of: media)
            guard !parts.isEmpty else { return nil }
            var data = Data()
            for part in parts {
                let (chunk, _) = try await URLSession.shared.data(from: part)
                data.append(chunk)
            }
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            let temp = folder.appendingPathComponent(UUID().uuidString + ".part")
            try data.write(to: temp)
            _ = try? FileManager.default.replaceItemAt(target, withItemAt: temp)
            if !FileManager.default.fileExists(atPath: target.path) { try FileManager.default.moveItem(at: temp, to: target) }
            prune()
            lockLog.notice("portada animada bajada: \(data.count, privacy: .public) bytes")
            await describe(target)
            return target
        } catch {
            lockLog.error("portada animada: no se pudo bajar: \(error.localizedDescription, privacy: .public)")
            return nil
        }
    }

    /// Anota si AVFoundation puede reproducir el archivo, su duración y su tamaño (para comprobar en
    /// CI que el MP4 fragmentado de Apple vale como archivo local).
    private static func describe(_ file: URL) async {
        let asset = AVURLAsset(url: file)
        do {
            let (playable, duration) = try await asset.load(.isPlayable, .duration)
            let track = try await asset.loadTracks(withMediaType: .video).first
            let size = try await track?.load(.naturalSize) ?? .zero
            lockLog.notice("portada animada local: reproducible \(playable, privacy: .public), \(duration.seconds, privacy: .public) s, \(Int(size.width), privacy: .public)×\(Int(size.height), privacy: .public)")
        } catch {
            lockLog.error("portada animada local: AVFoundation no la lee: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// Imagen remota (fotograma fijo del vídeo cuadrado).
    static func image(_ url: URL) async -> UIImage? {
        guard let (data, _) = try? await URLSession.shared.data(from: url) else { return nil }
        return UIImage(data: data)
    }

    /// La lista de medios de la variante H.264 más ancha que no pase de `maxWidth` (o la más
    /// estrecha si todas pasan). H.264 antes que HEVC: lo reproduce cualquier iPhone sin pensarlo.
    private static func pickVariant(_ master: URL) async throws -> URL? {
        let text = try await fetchText(master)
        let lines = text.components(separatedBy: .newlines)
        var variants: [(width: Int, avc: Bool, url: URL)] = []
        for (i, line) in lines.enumerated() where line.hasPrefix("#EXT-X-STREAM-INF:") {
            guard i + 1 < lines.count, let url = URL(string: lines[i + 1].trimmingCharacters(in: .whitespaces), relativeTo: master) else { continue }
            let width = line.range(of: #"RESOLUTION=(\d+)x"#, options: .regularExpression)
                .flatMap { Int(line[$0].dropFirst("RESOLUTION=".count).dropLast()) } ?? 0
            variants.append((width, line.contains("avc1"), url.absoluteURL))
        }
        let pool = variants.contains { $0.avc } ? variants.filter { $0.avc } : variants
        let fitting = pool.filter { $0.width <= maxWidth }
        return (fitting.max { $0.width < $1.width } ?? pool.min { $0.width < $1.width })?.url
    }

    /// Los archivos que componen una variante, en orden y sin repetir: la cabecera (`#EXT-X-MAP`) y los trozos.
    private static func files(of media: URL) async throws -> [URL] {
        let text = try await fetchText(media)
        var out: [URL] = []
        for raw in text.components(separatedBy: .newlines) {
            let line = raw.trimmingCharacters(in: .whitespaces)
            var uri: String?
            if line.hasPrefix("#EXT-X-MAP:"), let r = line.range(of: #"URI="[^"]+""#, options: .regularExpression) {
                uri = String(line[r].dropFirst(5).dropLast())
            } else if !line.isEmpty, !line.hasPrefix("#") {
                uri = line
            }
            if let uri, let url = URL(string: uri, relativeTo: media)?.absoluteURL, !out.contains(url) {
                out.append(url)
            }
        }
        return out
    }

    private static func fetchText(_ url: URL) async throws -> String {
        let (data, _) = try await URLSession.shared.data(from: url)
        return String(decoding: data, as: UTF8.self)
    }

    /// Deja solo las `keep` más recientes.
    private static func prune() {
        let fm = FileManager.default
        guard let items = try? fm.contentsOfDirectory(at: folder, includingPropertiesForKeys: [.contentModificationDateKey]) else { return }
        let dated = items.filter { $0.pathExtension == "mp4" }.map { url in
            (url, (try? url.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast)
        }
        for (url, _) in dated.sorted(by: { $0.1 > $1.1 }).dropFirst(keep) { try? fm.removeItem(at: url) }
    }
}
