import Foundation

/// Which source a barcode lookup is asking right now, for the loading caption.
enum LookupStep { case openFoodFacts, usda, otherDatabases }

/// Longest a lookup may run before the user gets a saved copy or an error with Retry.
let giveUpSeconds = 15.0

enum Lookup {
    /// `cachedAt` is set when the network failed and the product came from the offline cache.
    case found(Product, cachedAt: Date? = nil)
    /// `identified` is what a secondary barcode database knows (name/photo, no nutrition).
    case notFound(Identified?)
    case failed(offline: Bool)
}

/// Thin HTTP layer over the free food APIs. Only barcodes and typed search terms are ever sent. Mirrors FoodApi.kt.
struct FoodAPI {
    static let userAgent = "Nufo/1.0 (contact@nufo.app)"
    /// Free key from https://fdc.nal.usda.gov/api-key-signup; DEMO_KEY works with low rate limits.
    static var usdaKey: String {
        let key = Bundle.main.object(forInfoDictionaryKey: "USDA_API_KEY") as? String ?? ""
        return key.isEmpty ? "DEMO_KEY" : key
    }

    let cache = ProductCache()

    private let session: URLSession = {
        let c = URLSessionConfiguration.default
        c.timeoutIntervalForRequest = 12
        c.timeoutIntervalForResource = giveUpSeconds
        c.httpAdditionalHeaders = ["User-Agent": FoodAPI.userAgent]
        return URLSession(configuration: c)
    }()

    private func get(_ url: URL) async throws -> JSON {
        let (data, res) = try await session.data(from: url)
        let code = (res as? HTTPURLResponse)?.statusCode ?? 0
        if code == 404 { return [:] }
        guard (200..<300).contains(code) else { throw URLError(.badServerResponse) }
        return (try JSONSerialization.jsonObject(with: data) as? JSON) ?? [:]
    }

    func offProduct(_ barcode: String, lang: String) async throws -> Product? {
        var c = URLComponents(string: "https://world.openfoodfacts.org/api/v2/product/\(barcode).json")!
        c.queryItems = [.init(name: "fields", value: OffParser.fields)]
        return OffParser.parseProductResponse(try await get(c.url!), barcode: barcode, lang: lang)
    }

    /// Tag id -> name in `lang` for one taxonomy. Best effort: failures give an empty map.
    func taxonomyNames(_ type: String, _ tags: [String], lang: String) async -> [String: String] {
        guard !tags.isEmpty else { return [:] }
        var c = URLComponents(string: "https://world.openfoodfacts.org/api/v2/taxonomy")!
        c.queryItems = [.init(name: "tagtype", value: type), .init(name: "tags", value: tags.joined(separator: ",")),
                        .init(name: "lc", value: lang), .init(name: "fields", value: "name")]
        guard let root = try? await get(c.url!) else { return [:] }
        return OffParser.parseTaxonomy(root, lang: lang)
    }

    /// Search-a-licious has no free-text OR, so each Greek variant (typed, unaccented, English) runs as its
    /// own query limited to products sold in Greece, alongside one worldwide query; Greek products come
    /// first and exact whole-word matches lead each group. User text is stripped of Lucene syntax
    /// (a stray ":" makes the server return 500). Zero hits: retry with fuzzy brand matching.
    func offSearch(_ query: String, filters: SearchFilters, lang: String) async throws -> [SearchHit] {
        let clean = query.replacingOccurrences(of: #"[+\-&|!(){}\[\]^"~*?:\\/]"#, with: " ", options: .regularExpression)
            .split(separator: " ").joined(separator: " ")
        guard !clean.isEmpty else { return [] }
        var greek = filters; greek.greekOnly = true
        let variants = GreekSearch.variants(clean)
        let results: [Result<[SearchHit], Error>] = await withTaskGroup(of: (Int, Result<[SearchHit], Error>).self) { group in
            for (i, v) in variants.enumerated() { group.addTask { (i, await catching { try await offQuery(v, greek, lang) }) } }
            if !filters.greekOnly { group.addTask { (variants.count, await catching { try await offQuery(clean, filters, lang) }) } }
            var out: [(Int, Result<[SearchHit], Error>)] = []
            for await r in group { out.append(r) }
            return out.sorted { $0.0 < $1.0 }.map(\.1)
        }
        if results.allSatisfy({ if case .failure = $0 { true } else { false } }), case .failure(let e) = results[0] { throw e }
        let wanted = variants.map { GreekSearch.normalize($0).split(separator: " ").map(String.init) }
        func exact(_ h: SearchHit) -> Bool {
            let words = Set(GreekSearch.normalize(h.name).components(separatedBy: CharacterSet.alphanumerics.union(["%"]).inverted))
            return wanted.contains { v in v.allSatisfy { words.contains($0) } }
        }
        var seen = Set<String>()
        let merged = results.flatMap { (try? $0.get()) ?? [] }
            .filter { seen.insert($0.barcode ?? $0.name).inserted }
            .enumerated()
            .sorted { a, b in
                // Greek first, then exact matches, then entries a shopper can trust at a glance (photo, calories, grade).
                let ka = (a.element.soldInGreece ? 1 : 0, exact(a.element) ? 1 : 0, a.element.richness)
                let kb = (b.element.soldInGreece ? 1 : 0, exact(b.element) ? 1 : 0, b.element.richness)
                return ka != kb ? ka > kb : a.offset < b.offset // stable within groups
            }
            .map(\.element)
        if !merged.isEmpty { return Array(merged.prefix(40)) }
        let words = GreekSearch.stripAccents(clean).split(separator: " ").filter { $0.count >= 3 }
        guard !words.isEmpty else { return [] }
        return try await offQuery("(" + words.map { "brands:\($0)~2" }.joined(separator: " OR ") + ")", filters, lang)
    }

    private func offQuery(_ q: String, _ filters: SearchFilters, _ lang: String) async throws -> [SearchHit] {
        var c = URLComponents(string: "https://search.openfoodfacts.org/search")!
        c.queryItems = [
            .init(name: "q", value: ([q] + filters.offQueryClauses).joined(separator: " ")),
            .init(name: "langs", value: "el,en"),
            .init(name: "page_size", value: "20"),
            .init(name: "fields", value: OffParser.searchFields),
        ]
        return OffParser.parseSearch(try await get(c.url!), lang: lang)
    }

    /// USDA is English-only: Greek queries go through the Greek food dictionary, or are skipped.
    func usdaSearch(_ query: String, pageSize: Int = 15) async throws -> [SearchHit] {
        let q: String
        if GreekSearch.hasGreek(query) { guard let e = GreekSearch.english(query) else { return [] }; q = e } else { q = query }
        var c = URLComponents(string: "https://api.nal.usda.gov/fdc/v1/foods/search")!
        c.queryItems = [.init(name: "api_key", value: Self.usdaKey), .init(name: "query", value: q), .init(name: "pageSize", value: String(pageSize))]
        return UsdaParser.parseSearch(try await get(c.url!))
    }

    /// Latest shopper-reported prices (Open Prices, by Open Food Facts); Greek stores first.
    func prices(_ barcode: String) async throws -> [PriceReport] {
        var c = URLComponents(string: "https://prices.openfoodfacts.org/api/v1/prices")!
        c.queryItems = [.init(name: "product_code", value: barcode), .init(name: "order_by", value: "-date"), .init(name: "size", value: "20")]
        return PricesParser.parse(try await get(c.url!))
    }

    /// Last resort for barcodes neither food database knows: UPCitemdb's free trial endpoint (no key,
    /// ~100 lookups/day per IP). Gives a name and photo only, so the user can search by name.
    func identify(_ barcode: String) async -> Identified? {
        guard (8...14).contains(barcode.count) else { return nil }
        var c = URLComponents(string: "https://api.upcitemdb.com/prod/trial/lookup")!
        c.queryItems = [.init(name: "upc", value: barcode)]
        return (try? await get(c.url!)).flatMap(UpcItemDbParser.parse)
    }

    /// Offline search: whole words of the query against saved and cached products.
    func searchSaved(_ query: String, extra: [Product]) -> [SearchHit] {
        let words = GreekSearch.normalize(query).split(separator: " ").map(String.init)
        guard !words.isEmpty else { return [] }
        var seen = Set<String>()
        return (extra + cache.all())
            .filter { p in
                let text = GreekSearch.normalize(p.name + " " + (p.brand ?? ""))
                return words.allSatisfy { text.contains($0) } && seen.insert(p.key).inserted
            }
            .map { p in
                SearchHit(name: p.name, brand: p.brand, imageUrl: p.imageUrl, nutriscoreGrade: p.nutriscoreGrade, novaGroup: p.novaGroup,
                          caloriesPer100g: p.nutritionPer100g.calories, source: p.source, barcode: p.barcode, product: p, soldInGreece: p.soldInGreece)
            }
    }

    /// Barcode pipeline: Open Food Facts (plus translated tag names) first, then USDA branded foods by UPC,
    /// then UPCitemdb for identity only. Without a connection, the offline cache answers.
    /// Gives up after `giveUpSeconds` (each request alone may take 12 s): past that a looping loader only
    /// frustrates, so the user gets the saved copy or an error with Retry instead.
    func lookupBarcode(_ barcode: String, lang: String, onStep: @escaping @Sendable (LookupStep) -> Void = { _ in }) async -> Lookup {
        if let result = await withDeadline(giveUpSeconds, { await fetch(barcode, lang: lang, onStep: onStep) }) { return result }
        if let e = cache.get(barcode) { return .found(e.product, cachedAt: e.fetchedAt) }
        return .failed(offline: false)
    }

    private func fetch(_ barcode: String, lang: String, onStep: @escaping @Sendable (LookupStep) -> Void) async -> Lookup {
        do {
            onStep(.openFoodFacts)
            if let base = try await offProduct(barcode, lang: lang) {
                async let a = taxonomyNames("allergens", base.allergenTags, lang: lang)
                async let l = taxonomyNames("labels", base.labelTags, lang: lang)
                async let c = taxonomyNames("categories", Array(base.categoryTags.suffix(4)), lang: lang)
                async let d = taxonomyNames("additives", base.additiveTags, lang: lang)
                var p = base
                for part in await [a, l, c, d] { p.tagNames.merge(part) { x, _ in x } }
                cache.put(p)
                return .found(p)
            }
            onStep(.usda)
            let trimmed = barcode.drop { $0 == "0" }
            if let p = try await usdaSearch(barcode, pageSize: 5).compactMap(\.product).first(where: { ($0.barcode ?? "").drop { $0 == "0" } == trimmed }) {
                cache.put(p)
                return .found(p)
            }
            onStep(.otherDatabases)
            return .notFound(await identify(barcode))
        } catch {
            if let e = cache.get(barcode) { return .found(e.product, cachedAt: e.fetchedAt) }
            return .failed(offline: isOffline(error))
        }
    }

    /// Both sources in parallel; one failing source never hides the other's results.
    func search(_ query: String, filters: SearchFilters, lang: String) async -> Result<[SearchHit], Error> {
        async let off = catching { try await offSearch(query, filters: filters, lang: lang) }
        async let usda = catching { () async throws -> [SearchHit] in
            if filters.needsOffTags { return [] }
            return try await usdaSearch(query)
        }
        let results = await [off, usda]
        let hits = results.flatMap { (try? $0.get()) ?? [] }.filter(filters.accepts)
        if hits.isEmpty, case .failure(let e) = results[0], case .failure = results[1] { return .failure(e) }
        return .success(hits)
    }
}

private func catching<T>(_ op: () async throws -> T) async -> Result<T, Error> {
    do { return .success(try await op()) } catch { return .failure(error) }
}
private extension SearchHit {
    var richness: Int { [imageUrl != nil, caloriesPer100g != nil, nutriscoreGrade != nil].filter { $0 }.count }
}

/// The result of `op`, or nil if it takes longer than `seconds` (the slow task is cancelled).
private func withDeadline<T>(_ seconds: Double, _ op: @escaping @Sendable () async -> T) async -> T? {
    await withTaskGroup(of: T?.self) { group in
        group.addTask { await op() }
        group.addTask { try? await Task.sleep(for: .seconds(seconds)); return nil }
        let first = await group.next() ?? nil
        group.cancelAll()
        return first
    }
}
