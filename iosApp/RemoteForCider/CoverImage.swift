import Shared
import SwiftUI

/// Carátula de Apple Music: pide a mzstatic el tamaño justo y, mientras carga, enseña su color de fondo.
struct CoverImage: View {
    let artwork: Shared.Artwork?
    var pixels: Int32 = 600
    var corner: CGFloat = 8
    /// Sin carga: el color de fondo con una nota (falso: transparente, para poner una carátula pequeña debajo).
    var placeholder = true

    var body: some View {
        AsyncImage(url: artwork?.sized(px: pixels).flatMap { URL(string: $0) }) { phase in
            if let image = phase.image {
                image.resizable().scaledToFill()
            } else if !placeholder {
                Color.clear
            } else {
                Rectangle()
                    .fill(Color(hex: artwork?.bgColor) ?? Color(.secondarySystemFill))
                    .overlay {
                        Image(systemName: "music.note")
                            .font(.title)
                            .foregroundStyle(.tertiary)
                    }
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: corner, style: .continuous))
    }
}

extension Color {
    /// `"081418"` → color; los `bgColor` de Apple vienen sin `#`.
    init?(hex: String?) {
        guard let hex, let value = UInt32(hex.trimmingCharacters(in: CharacterSet(charactersIn: "#")), radix: 16) else {
            return nil
        }
        self.init(
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255
        )
    }
}

/// `125` → `"2:05"`.
func formatTime(_ seconds: Double) -> String {
    let s = max(0, Int(seconds.rounded(.down)))
    return String(format: "%d:%02d", s / 60, s % 60)
}
