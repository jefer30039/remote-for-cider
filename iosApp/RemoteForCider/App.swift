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

    var body: some View {
        if store.connection == nil {
            ConnectView()
        } else {
            MainTabs()
        }
    }
}
