import SwiftUI

@main
struct RemoteForCiderApp: App {
    @State private var store = RemoteStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(store)
        }
    }
}

struct RootView: View {
    @Environment(RemoteStore.self) private var store
    @Environment(\.scenePhase) private var phase

    var body: some View {
        Group {
            if UserDefaults.standard.bool(forKey: "glassLab") {
                // `-glassLab YES`: banco de animaciones del sistema para copiarlas en Android (CI).
                GlassLab()
            } else if store.connection == nil {
                ConnectView()
            } else {
                MainTabs()
            }
        }
        .onChange(of: phase) { old, new in
            if new == .active, old != .active { store.onForeground() }
        }
    }
}
