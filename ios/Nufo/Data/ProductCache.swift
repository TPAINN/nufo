import Foundation

/// Every product looked up is kept on disk so it opens offline later (mirrors Android's product_cache table).
/// Application Support, not Caches: iOS may purge Caches exactly when the user is offline and needs it.
struct ProductCache {
    struct Entry: Codable { var product: Product; var fetchedAt: Date }

    private var dir: URL {
        let d = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("ProductCache", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }
    private func file(_ barcode: String) -> URL? {
        let digits = barcode.filter(\.isNumber)
        return digits.isEmpty ? nil : dir.appendingPathComponent(digits + ".json")
    }

    func get(_ barcode: String) -> Entry? {
        guard let f = file(barcode), let data = try? Data(contentsOf: f) else { return nil }
        return try? JSONDecoder().decode(Entry.self, from: data)
    }

    func put(_ p: Product) {
        guard let b = p.barcode, let f = file(b), let data = try? JSONEncoder().encode(Entry(product: p, fetchedAt: .now)) else { return }
        try? data.write(to: f, options: .atomic)
    }

    func all() -> [Product] {
        let files = (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? []
        return files.compactMap { (try? Data(contentsOf: $0)).flatMap { try? JSONDecoder().decode(Entry.self, from: $0).product } }
    }

    func clear() { try? FileManager.default.removeItem(at: dir) }
}

/// No connection (as opposed to a server fault): drives "you're offline" vs "the database is down" copy.
func isOffline(_ error: Error) -> Bool {
    guard let e = error as? URLError else { return false }
    return [.notConnectedToInternet, .networkConnectionLost, .timedOut, .cannotFindHost, .cannotConnectToHost,
            .dataNotAllowed, .internationalRoamingOff, .dnsLookupFailed].contains(e.code)
}
