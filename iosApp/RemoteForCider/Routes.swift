import Shared
import SwiftUI

/// Rojo de Apple Music en iOS.
extension Color {
    static let accentRed = Color(red: 250 / 255, green: 45 / 255, blue: 72 / 255)
}

/// Álbum o playlist que abrir. `kind` como en las rutas de Cider: `albums`, `playlists`,
/// `library-albums`, `library-playlists`.
struct CollectionRoute: Hashable {
    let kind: String
    let id: String
    let title: String
    let subtitle: String?
    let artwork: Shared.Artwork?
    /// Portada animada vertical si quien abre ya la conoce (estanterías de Inicio).
    var video: String? = nil

    var isPlaylist: Bool { kind.hasSuffix("playlists") }
    var isLibrary: Bool { kind.hasPrefix("library-") }
}

/// Pantallas a las que se navega dentro de una pestaña.
enum Route: Hashable {
    case playlists, artists, albums, songs
    case collection(CollectionRoute)
    /// Artista del catálogo (`catalog`) o de la biblioteca (`r.…`).
    case artist(id: String, name: String, catalog: Bool)
    /// Categoría de Buscar (curador de Apple Music): sus playlists.
    case category(id: String, name: String)
}

extension AmItem {
    /// Lo que abre al tocarlo; `nil` si se reproduce directamente (canciones, emisoras).
    var route: Route? {
        if type == "artists" { return .artist(id: id, name: name, catalog: true) }
        if type == "library-artists" { return .artist(id: id, name: name, catalog: false) }
        if type.hasSuffix("albums") || type.hasSuffix("playlists") {
            return .collection(CollectionRoute(
                kind: type, id: id, title: name,
                subtitle: attributes?.artistName ?? attributes?.curatorName, artwork: artwork
            ))
        }
        return nil
    }
}

extension HomeItem {
    var route: Route? {
        if type == "artists" { return .artist(id: id, name: name, catalog: true) }
        if type == "library-artists" { return .artist(id: id, name: name, catalog: false) }
        if type.hasSuffix("albums") || type.hasSuffix("playlists") {
            return .collection(CollectionRoute(kind: type, id: id, title: name, subtitle: subtitle, artwork: artwork, video: tallVideo))
        }
        return nil
    }
}

/// Espacio de nombres del zoom de la pestaña: la carátula tocada es el origen de la transición.
private struct ZoomNamespaceKey: EnvironmentKey {
    static let defaultValue: Namespace.ID? = nil
}

extension EnvironmentValues {
    var zoomNamespace: Namespace.ID? {
        get { self[ZoomNamespaceKey.self] }
        set { self[ZoomNamespaceKey.self] = newValue }
    }
}

extension View {
    /// Origen del zoom de iOS 18+ (como Apple Music al abrir un álbum).
    @ViewBuilder func zoomSource(_ route: Route?) -> some View {
        modifier(ZoomSourceModifier(route: route))
    }
}

private struct ZoomSourceModifier: ViewModifier {
    @Environment(\.zoomNamespace) private var ns
    let route: Route?

    func body(content: Content) -> some View {
        if let ns, let route {
            content.matchedTransitionSource(id: route, in: ns)
        } else {
            content
        }
    }
}

/// Abre una ruta en la pila de la pestaña actual (menús contextuales, hoja de reproducción…).
private struct OpenRouteKey: EnvironmentKey {
    static let defaultValue: (Route) -> Void = { _ in }
}

extension EnvironmentValues {
    var openRoute: (Route) -> Void {
        get { self[OpenRouteKey.self] }
        set { self[OpenRouteKey.self] = newValue }
    }
}

/// Pila de navegación de una pestaña: rutas comunes y zoom desde la carátula.
struct TabStack<Root: View>: View {
    @Namespace private var zoom
    @Binding var path: [Route]
    @ViewBuilder var root: () -> Root

    var body: some View {
        NavigationStack(path: $path) {
            root()
                .navigationDestination(for: Route.self) { route in
                    destination(route)
                        .navigationTransition(.zoom(sourceID: route, in: zoom))
                }
        }
        .environment(\.zoomNamespace, zoom)
        .environment(\.openRoute, { route in path.append(route) })
    }

    @ViewBuilder private func destination(_ route: Route) -> some View {
        switch route {
        case .playlists: PlaylistsList()
        case .artists: ArtistsList()
        case .albums: AlbumsGrid()
        case .songs: SongsList()
        case .collection(let c): CollectionView(route: c)
        case .artist(let id, let name, let catalog): ArtistView(id: id, name: name, catalog: catalog)
        case .category(let id, let name): CategoryView(id: id, name: name)
        }
    }
}

/// Indicador de carga que solo aparece si tarda (en cargas rápidas solo parpadearía).
struct LoadingView: View {
    @State private var show = false

    var body: some View {
        ZStack {
            if show { ProgressView() }
        }
        .frame(maxWidth: .infinity, minHeight: 80)
        .task {
            try? await Task.sleep(for: .milliseconds(500))
            show = true
        }
    }
}

/// Mensaje centrado cuando no hay nada o falla la carga.
struct EmptyStateView: View {
    let title: LocalizedStringKey
    var detail: String? = nil

    var body: some View {
        VStack(spacing: 6) {
            Text(title).font(.headline)
            if let detail { Text(detail).font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center) }
        }
        .frame(maxWidth: .infinity)
        .padding(32)
    }
}

/// Iniciales sobre gris (artistas sin foto).
struct Monogram: View {
    let name: String

    var body: some View {
        let letters = name.split(separator: " ").prefix(2).compactMap { $0.first }.map(String.init).joined()
        Circle()
            .fill(LinearGradient(colors: [Color(white: 0.62), Color(white: 0.5)], startPoint: .top, endPoint: .bottom))
            .overlay {
                Text(letters.uppercased())
                    .font(.system(size: 200, weight: .semibold, design: .rounded))
                    .minimumScaleFactor(0.01)
                    .padding(18)
                    .foregroundStyle(.white)
            }
    }
}
