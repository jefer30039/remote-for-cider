import SwiftUI

enum AppTab: String {
    case home, new, radio, library, search
}

/// Pestañas de Apple Music en iOS 26: la barra se minimiza al desplazar hacia abajo y el
/// miniplayer va encima de ella (o dentro, minimizada) como accesorio.
struct MainTabs: View {
    @Environment(RemoteStore.self) private var store
    // `-demoTab library` / `-demoOpenPlayer YES`: para las capturas del simulador en CI.
    @State private var tab = AppTab(rawValue: UserDefaults.standard.string(forKey: "demoTab") ?? "") ?? .home
    @State private var sheet = PlayerSheet(open: UserDefaults.standard.bool(forKey: "demoOpenPlayer"))

    @State private var paths: [AppTab: [Route]] = [:]

    var body: some View {
        TabView(selection: $tab) {
            Tab("Home", systemImage: "house.fill", value: AppTab.home) {
                TabStack(path: path(.home)) { ShelvesPage(source: .home) }
            }
            Tab("New", systemImage: "square.grid.2x2.fill", value: AppTab.new) {
                TabStack(path: path(.new)) { ShelvesPage(source: .new) }
            }
            Tab("Radio", systemImage: "dot.radiowaves.left.and.right", value: AppTab.radio) {
                TabStack(path: path(.radio)) { ShelvesPage(source: .radio) }
            }
            Tab("Library", systemImage: "square.stack.fill", value: AppTab.library) {
                TabStack(path: path(.library)) { LibraryView() }
            }
            Tab(value: AppTab.search, role: .search) {
                TabStack(path: path(.search)) { SearchView() }
            }
        }
        .tint(.accentRed)
        .tabBarMinimizeBehavior(.onScrollDown)
        .tabViewBottomAccessory {
            MiniPlayer { sheet.open() }
                .environment(sheet)
        }
        // Sin animación del sistema: la transición desde el miniplayer la hace la hoja (PlayerSheet).
        .fullScreenCover(isPresented: $sheet.presented) {
            NowPlayingView()
                .environment(sheet)
                // Desde la hoja (título → álbum, artista → su página): se cierra y se abre en la pestaña actual.
                .environment(\.openRoute, { route in
                    sheet.close(animation: .smooth(duration: 0.35))
                    paths[tab, default: []].append(route)
                })
        }
        .environment(sheet)
    }

    private func path(_ tab: AppTab) -> Binding<[Route]> {
        Binding(get: { paths[tab] ?? [] }, set: { paths[tab] = $0 })
    }
}

