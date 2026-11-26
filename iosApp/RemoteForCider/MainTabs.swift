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

    var body: some View {
        TabView(selection: $tab) {
            Tab("Home", systemImage: "house.fill", value: AppTab.home) {
                NavigationStack { PlaceholderPage(title: "Home") }
            }
            Tab("New", systemImage: "square.grid.2x2.fill", value: AppTab.new) {
                NavigationStack { PlaceholderPage(title: "New") }
            }
            Tab("Radio", systemImage: "dot.radiowaves.left.and.right", value: AppTab.radio) {
                NavigationStack { PlaceholderPage(title: "Radio") }
            }
            Tab("Library", systemImage: "square.stack.fill", value: AppTab.library) {
                NavigationStack { LibraryView() }
            }
            Tab(value: AppTab.search, role: .search) {
                NavigationStack { PlaceholderPage(title: "Search") }
            }
        }
        .tabBarMinimizeBehavior(.onScrollDown)
        .tabViewBottomAccessory {
            MiniPlayer { sheet.open() }
                .environment(sheet)
        }
        // Sin animación del sistema: la transición desde el miniplayer la hace la hoja (PlayerSheet).
        .fullScreenCover(isPresented: $sheet.presented) {
            NowPlayingView()
                .environment(sheet)
        }
        .environment(sheet)
    }
}

/// Pantallas aún sin hacer: con contenido desplazable para probar que la barra se minimiza.
struct PlaceholderPage: View {
    let title: LocalizedStringKey
    @Environment(RemoteStore.self) private var store

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                ForEach(0..<12) { _ in
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color(.secondarySystemFill))
                        .frame(height: 120)
                }
            }
            .padding(.horizontal, 20)
        }
        .navigationTitle(title)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    if let c = store.connection {
                        Text("\(c.host):\(String(c.port))")
                    }
                    Button("Forget this computer", systemImage: "xmark.circle", role: .destructive) {
                        store.forget()
                    }
                } label: {
                    Image(systemName: "ellipsis")
                }
            }
        }
    }
}
