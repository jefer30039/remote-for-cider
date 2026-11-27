import Shared
import SwiftUI
import UIKit

/// Colores de las páginas de álbum, playlist y artista (Apple Music): el fondo es el tono de la
/// carátula con brillo fijo (0,28 en oscuro, 0,70 en claro) y el texto va en blanco, con el
/// secundario tan translúcido como permita leerse (nunca gris oscuro).
enum PageColor {
    /// Fondo de página a partir de un color de Apple (`bgColor`, sin `#`).
    static func background(hex: String?, dark: Bool) -> Color? {
        guard let rgb = rgb(hex) else { return nil }
        var h: CGFloat = 0, s: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(red: rgb.r, green: rgb.g, blue: rgb.b, alpha: 1).getHue(&h, saturation: &s, brightness: &b, alpha: &a)
        if s > 0.2 { s = min(s * 1.2, 0.92) }
        return Color(hue: h, saturation: s, brightness: dark ? 0.28 : 0.70)
    }

    /// 10/255 más oscuro: fondo de las estanterías bajo las canciones del álbum.
    static func lowered(_ color: Color) -> Color {
        let ui = UIColor(color)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        ui.getRed(&r, green: &g, blue: &b, alpha: &a)
        let d: CGFloat = 10 / 255
        return Color(red: max(r - d, 0), green: max(g - d, 0), blue: max(b - d, 0))
    }

    /// Blanco con la opacidad justa para leerse sobre `bg`: la de `alpha` si basta y, si no, la
    /// primera que llegue a min(objetivo, 3, 82 % del contraste del blanco puro).
    static func readable(on bg: Color, alpha: Double, target: Double = 4.5) -> Color {
        let ui = UIColor(bg)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        ui.getRed(&r, green: &g, blue: &b, alpha: &a)
        let bgL = luminance(r, g, b)
        let goal = min(target, 3, contrast(1, bgL) * 0.82)
        func shown(_ k: Double) -> Double {
            luminance(r + (1 - r) * k, g + (1 - g) * k, b + (1 - b) * k)
        }
        if contrast(shown(alpha), bgL) >= goal { return .white.opacity(alpha) }
        for i in Int(alpha * 100)...100 {
            let k = Double(i) / 100
            if contrast(shown(k), bgL) >= goal { return .white.opacity(k) }
        }
        return .white
    }

    private static func rgb(_ hex: String?) -> (r: CGFloat, g: CGFloat, b: CGFloat)? {
        guard let hex, let v = UInt32(hex.trimmingCharacters(in: CharacterSet(charactersIn: "#")), radix: 16) else { return nil }
        return (CGFloat((v >> 16) & 0xFF) / 255, CGFloat((v >> 8) & 0xFF) / 255, CGFloat(v & 0xFF) / 255)
    }

    private static func luminance(_ r: CGFloat, _ g: CGFloat, _ b: CGFloat) -> Double {
        func lin(_ c: CGFloat) -> Double {
            let c = Double(c)
            return c <= 0.03928 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }

    private static func contrast(_ a: Double, _ b: Double) -> Double {
        (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }
}

/// Degradado de `from` (fracción del alto) al borde inferior hacia `color`, con curva suave: el
/// fundido de la portada al fondo de la página, largo y sin escalón.
func fadeInto(_ color: Color, from: Double = 0.45, steps: Int = 12) -> LinearGradient {
    let stops = (0...steps).map { i -> Gradient.Stop in
        let t = Double(i) / Double(steps)
        let eased = t * t * (3 - 2 * t)
        return .init(color: color.opacity(eased), location: from + (1 - from) * t)
    }
    return LinearGradient(stops: [.init(color: color.opacity(0), location: 0)] + stops, startPoint: .top, endPoint: .bottom)
}
