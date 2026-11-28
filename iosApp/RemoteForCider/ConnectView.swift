import Shared
import SwiftUI

/// Emparejamiento con el PC: al abrir busca solo el Cider de la red (el puente responde que falta el
/// token) y rellena la dirección; "Emparejar" pide permiso a Cider, que muestra un diálogo en el PC.
/// Si no aparece, sugiere instalar el puente (con enlace al README). También se puede escribir la
/// dirección o pegar un token a mano.
struct ConnectView: View {
    @Environment(RemoteStore.self) private var store
    @State private var host = ""
    /// En el simulador, el puerto del Cider de demostración (sin puente).
    #if targetEnvironment(simulator)
    @State private var port = "10767"
    #else
    @State private var port = String(Connection.defaultPort)
    #endif
    @State private var token = ""
    @State private var pairing = false
    @State private var searching = false
    @State private var notFound = false
    @State private var message: String?
    @State private var manual = false

    private static let bridgeHelp = URL(string: "https://github.com/jefer30039/remote-for-cider#1-install-the-bridge-on-the-computer")!
    private static let bridgeHelpEs = URL(string: "https://github.com/jefer30039/remote-for-cider/blob/main/README.es.md#1-instalar-el-puente-en-la-computadora")!

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 22) {
                    Image("AboutIcon")
                        .resizable()
                        .frame(width: 88, height: 88)
                        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                        .padding(.top, 40)
                    VStack(spacing: 8) {
                        Text("Remote for Cider")
                            .font(.largeTitle.bold())
                        Text("Control Cider on your computer from your iPhone.")
                            .font(.body)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                    }

                    statusCard

                    Button {
                        Task { await pair() }
                    } label: {
                        HStack(spacing: 8) {
                            if pairing { ProgressView().tint(.white) }
                            Text(pairing ? LocalizedStringKey("Waiting for Cider…") : LocalizedStringKey("Pair with Cider"))
                        }
                        .font(.headline)
                        .frame(maxWidth: .infinity, minHeight: 50)
                    }
                    .buttonStyle(.glassProminent)
                    .tint(.accentRed)
                    .disabled(host.isEmpty || pairing)
                    .accessibilityIdentifier("pairButton")

                    if let message {
                        Text(message)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                    }

                    if notFound { bridgeCard }

                    DisclosureGroup("Enter the Address Manually", isExpanded: $manual) {
                        VStack(spacing: 12) {
                            field("Computer Address", text: $host, keyboard: .numbersAndPunctuation)
                            field("Port", text: $port, keyboard: .numberPad)
                            SecureField("Token (optional)", text: $token)
                                .textFieldStyle(.roundedBorder)
                            Button("Connect with Token") { finish(token: token) }
                                .disabled(host.isEmpty || token.isEmpty)
                        }
                        .padding(.top, 12)
                    }
                    .tint(.primary)
                    .padding(.top, 8)
                }
                .padding(.horizontal, 28)
                .padding(.bottom, 40)
            }
        }
        .task { await search() }
    }

    @ViewBuilder private var statusCard: some View {
        HStack(spacing: 12) {
            if searching {
                ProgressView()
                Text("Looking for your computer…")
            } else if !host.isEmpty {
                Image(systemName: "desktopcomputer").foregroundStyle(Color.accentRed)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Cider found").font(.headline)
                    Text(verbatim: "\(host):\(port)").font(.subheadline).foregroundStyle(.secondary)
                }
            } else {
                Image(systemName: "wifi.exclamationmark").foregroundStyle(.secondary)
                Text("Couldn't find Cider on this network.")
            }
            Spacer(minLength: 0)
            if !searching {
                Button { Task { await search() } } label: { Image(systemName: "arrow.clockwise") }
                    .accessibilityLabel("Search Again")
            }
        }
        .padding(16)
        .glassEffect(in: .rect(cornerRadius: 18))
    }

    private var bridgeCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Did you install the bridge?").font(.headline)
            Text("Cider only listens on the computer itself. The bridge lets your iPhone reach it on port 10768.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Link("How to install it", destination: Locale.current.language.languageCode?.identifier == "es" ? Self.bridgeHelpEs : Self.bridgeHelp)
                .font(.subheadline.weight(.semibold))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private func field(_ title: LocalizedStringKey, text: Binding<String>, keyboard: UIKeyboardType) -> some View {
        TextField(title, text: text)
            .keyboardType(keyboard)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .textFieldStyle(.roundedBorder)
    }

    private func search() async {
        searching = true
        defer { searching = false }
        let p = Int(port) ?? Connection.defaultPort
        if let ip = await PcFinder.find(port: p, token: nil, preferred: host.isEmpty ? nil : host) {
            host = ip
            notFound = false
        } else {
            notFound = host.isEmpty
        }
    }

    private func pair() async {
        pairing = true
        message = String(localized: "Allow Remote for Cider in the dialog on your computer.")
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
                notFound = true
            }
        } catch {
            message = error.localizedDescription
            notFound = true
        }
    }

    private func finish(token: String) {
        let connection = Connection(host: host, port: Int(port) ?? Connection.defaultPort, token: token)
        connection.save()
        store.connect(connection)
    }
}
