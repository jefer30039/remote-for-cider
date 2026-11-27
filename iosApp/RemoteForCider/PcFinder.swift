import Darwin
import Foundation

/// Busca el PC con Cider en la red del iPhone probando el puerto del puente en cada dirección de la
/// subred (como Android): así la app sigue funcionando si cambia la IP del PC. Wi-Fi (`en0`),
/// hotspot del iPhone (`bridge*`) y Ethernet o USB (`en*`); nunca datos móviles (`pdp_ip*`) ni VPN
/// (`utun*`, `ipsec*`). iOS pide el permiso de red local la primera vez.
enum PcFinder {
    private static let session: URLSession = {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 1.2
        config.timeoutIntervalForResource = 1.5
        config.waitsForConnectivity = false
        config.httpMaximumConnectionsPerHost = 1
        return URLSession(configuration: config)
    }()

    /// Con `token`, solo vale el Cider que lo acepte (el emparejado); sin token, cualquier Cider que
    /// responda que falta (para emparejar). La IP, o nil si no aparece en `budget` segundos.
    static func find(port: Int, token: String?, preferred: String? = nil, budget: Double = 8) async -> String? {
        let all = candidates(preferred: preferred)
        guard !all.isEmpty else { return nil }
        return await withTaskGroup(of: String?.self) { group in
            let deadline = Date().addingTimeInterval(budget)
            var next = 0
            func launch() {
                guard next < all.count else { return }
                let ip = all[next]
                next += 1
                group.addTask { await answers(ip, port: port, token: token) ? ip : nil }
            }
            for _ in 0..<min(48, all.count) { launch() }
            while let result = await group.next() {
                if let result {
                    group.cancelAll()
                    return result
                }
                if Date() > deadline { group.cancelAll(); return nil }
                launch()
            }
            return nil
        }
    }

    /// Si Cider responde en `ip` (con `token`, o pidiéndolo si no hay).
    static func answers(_ ip: String, port: Int, token: String?) async -> Bool {
        guard let url = URL(string: "http://\(ip):\(port)/api/v2/client/info") else { return false }
        var request = URLRequest(url: url)
        request.timeoutInterval = 1.2
        if let token { request.setValue(token, forHTTPHeaderField: "apptoken") }
        guard let (data, response) = try? await session.data(for: request),
              let status = (response as? HTTPURLResponse)?.statusCode else { return false }
        let body = String(decoding: data, as: UTF8.self)
        return token != nil ? status == 200 && body.contains("framework") : status == 403 && body.contains("UNAUTHORIZED_APP_TOKEN")
    }

    /// Direcciones de las redes locales, primero `preferred` y luego las más cercanas a la propia.
    /// Subredes mayores que /22 se limitan a la /24 propia para no tardar demasiado.
    static func candidates(preferred: String?) -> [String] {
        #if targetEnvironment(simulator)
        // En el simulador (CI) el Cider de demostración está en el propio Mac: no se recorre la red del runner.
        return Array(Set([preferred, "127.0.0.1"].compactMap { $0 }))
        #endif
        var out: [String] = preferred.map { [$0] } ?? []
        var seen = Set(out)
        for (own, prefix) in localSubnets() {
            let p = min(max(prefix, 22), 30)
            let mask: UInt32 = p == 0 ? 0 : ~UInt32(0) << (32 - p)
            let net = own & mask
            let size = UInt32(1) << (32 - p)
            let hosts = (1..<(size - 1)).map { net + $0 }.filter { $0 != own }
                .sorted { abs(Int64($0) - Int64(own)) < abs(Int64($1) - Int64(own)) }
            for h in hosts {
                let ip = "\(h >> 24 & 0xFF).\(h >> 16 & 0xFF).\(h >> 8 & 0xFF).\(h & 0xFF)"
                if seen.insert(ip).inserted { out.append(ip) }
            }
        }
        return out
    }

    /// (dirección propia, prefijo) de las interfaces IPv4 privadas que valen.
    private static func localSubnets() -> [(UInt32, Int)] {
        var list: [(UInt32, Int)] = []
        var head: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&head) == 0, let first = head else { return [] }
        defer { freeifaddrs(head) }
        var cursor: UnsafeMutablePointer<ifaddrs>? = first
        while let ifa = cursor {
            defer { cursor = ifa.pointee.ifa_next }
            let flags = Int32(ifa.pointee.ifa_flags)
            guard flags & IFF_UP != 0, flags & IFF_LOOPBACK == 0,
                  let addr = ifa.pointee.ifa_addr, addr.pointee.sa_family == UInt8(AF_INET),
                  let maskPtr = ifa.pointee.ifa_netmask else { continue }
            let name = String(cString: ifa.pointee.ifa_name)
            guard name.hasPrefix("en") || name.hasPrefix("bridge") else { continue }
            let ip = addr.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { UInt32(bigEndian: $0.pointee.sin_addr.s_addr) }
            let mask = maskPtr.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { UInt32(bigEndian: $0.pointee.sin_addr.s_addr) }
            guard isPrivate(ip) else { continue }
            list.append((ip, mask.nonzeroBitCount))
        }
        return list
    }

    private static func isPrivate(_ ip: UInt32) -> Bool {
        let a = ip >> 24, b = (ip >> 16) & 0xFF
        return a == 10 || (a == 172 && (16...31).contains(b)) || (a == 192 && b == 168)
    }
}
