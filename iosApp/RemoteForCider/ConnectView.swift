import Shared
import SwiftUI

/// Conexión con el PC (primera versión): dirección y emparejamiento con el diálogo de Cider.
/// La búsqueda automática en la red y el diseño definitivo vienen después.
struct ConnectView: View {
    @Environment(RemoteStore.self) private var store
    @State private var host = ""
    @State private var port = String(Connection.defaultPort)
    @State private var token = ""
    @State private var pairing = false
    @State private var message: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Computer address (e.g. 192.168.0.110)", text: $host)
                        .keyboardType(.numbersAndPunctuation)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField("Port", text: $port)
                        .keyboardType(.numberPad)
                } footer: {
                    Text("Install the bridge on your computer first: Cider only listens on the computer itself. The bridge uses port 10768.")
                }
                Section {
                    Button {
                        Task { await pair() }
                    } label: {
                        HStack {
                            Text("Pair with Cider")
                            if pairing {
                                Spacer()
                                ProgressView()
                            }
                        }
                    }
                    .disabled(host.isEmpty || pairing)
                } footer: {
                    Text(message ?? String(localized: "Cider will ask you on the computer to allow Remote for Cider."))
                }
                Section("Already have a token?") {
                    SecureField("Token", text: $token)
                    Button("Connect") { finish(token: token) }
                        .disabled(host.isEmpty || token.isEmpty)
                }
            }
            .navigationTitle("Remote for Cider")
        }
    }

    private func pair() async {
        pairing = true
        message = String(localized: "Waiting for you to allow it in Cider…")
        defer { pairing = false }
        let client = CiderPairingClient()
        defer { client.close() }
        do {
            let result = try await client.request(host: host, port: Int32(Int(port) ?? Connection.defaultPort), appName: "Remote for Cider (iOS)")
            switch onEnum(of: result) {
            case .approved(let approved):
                finish(token: approved.token)
            case .denied:
                message = String(localized: "Cider denied the request.")
            case .timedOut:
                message = String(localized: "Nobody answered in Cider within 2 minutes.")
            case .tryLater:
                message = String(localized: "Cider is busy. Try again in a few seconds.")
            case .failed(let failed):
                message = String(localized: "Couldn't reach Cider: \(failed.error.message ?? "")")
            }
        } catch {
            message = error.localizedDescription
        }
    }

    private func finish(token: String) {
        let connection = Connection(host: host, port: Int(port) ?? Connection.defaultPort, token: token)
        connection.save()
        store.connect(connection)
    }
}
