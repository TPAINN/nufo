import Foundation

typealias JSON = [String: Any]

private extension Dictionary where Key == String, Value == Any {
    func str(_ k: String) -> String? {
        guard let s = self[k] as? String else { return nil }
        let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
    func num(_ k: String) -> Double? {
        if let d = self[k] as? Double { return d }
        if let i = self[k] as? Int { return Double(i) }
        if let s = self[k] as? String { return Double(s) }
        return nil
    }
    func int(_ k: String) -> Int? { num(k).map { Int($0) } }
    func tags(_ k: String) -> [String] { (self[k] as? [String]) ?? [] }
}

/// "en:sesame-seeds" -> "Sesame seeds". Last resort when no translated name exists.
func prettyTag(_ tag: String) -> String {
    let raw = (tag.split(separator: ":", maxSplits: 1).last.map(String.init) ?? tag).replacingOccurrences(of: "-", with: " ")
    return raw.prefix(1).uppercased() + raw.dropFirst()
}

private func grade(_ v: String?) -> String? {
    guard let g = v?.lowercased(), g.count == 1, "abcde".contains(g) else { return nil }
    return g
}

/// OFF sometimes HTML-escapes names ("Φέτα &quot;Ελαφρύ&quot;").
private func unescape(_ s: String) -> String {
    s.replacingOccurrences(of: "&quot;", with: "\"").replacingOccurrences(of: "&amp;", with: "&")
        .replacingOccurrences(of: "&#39;", with: "'").replacingOccurrences(of: "&apos;", with: "'")
}

/// Drops the EU "℮" estimated-quantity mark, which OFF often stores as a trailing " e" ("400 g e").
func stripEstimatedMark(_ q: String) -> String {
    q.replacingOccurrences(of: #"\s*(℮|\be)\s*$"#, with: "", options: .regularExpression).trimmingCharacters(in: .whitespaces)
}

/// Energy in kcal, rejecting impossible values: nothing exceeds 900 kcal per 100 g (pure fat).
/// A kcal field above that is usually kJ typed into the wrong box, so the kJ field (/ 4.184) is used
/// instead, or nothing. `perGrams` is nil for per-serving values, which have no fixed ceiling.
func kcal(_ kcal: Double?, _ kj: Double?, perGrams: Double?) -> Double? {
    func ok(_ v: Double) -> Bool { v >= 0 && (perGrams == nil || v <= 9 * perGrams!) }
    if let k = kcal, ok(k) { return k }
    if let j = kj.map({ $0 / 4.184 }), ok(j) { return j }
    return nil
}

enum OffParser {
    static let source = "Open Food Facts"
    static let fields = "code,product_name,product_name_el,product_name_en,brands,quantity,serving_size,serving_quantity,nutriments,nutriscore_grade,nova_group,ecoscore_grade,ingredients_text,ingredients_text_el,ingredients_text_en,allergens_tags,additives_tags,image_url,categories_tags,labels_tags,countries_tags,completeness,last_modified_t"
    static let searchFields = "code,product_name,product_name_el,product_name_en,brands,image_url,nutriscore_grade,nova_group,nutriments,countries_tags"

    private static func nutrition(_ n: JSON, _ suffix: String) -> Nutrition? {
        func v(_ k: String) -> Double? { n.num("\(k)_\(suffix)") }
        var out = Nutrition(
            calories: kcal(v("energy-kcal"), v("energy"), perGrams: suffix == "100g" ? 100 : nil),
            protein: v("proteins"), carbs: v("carbohydrates"), fat: v("fat"), saturatedFat: v("saturated-fat"),
            fiber: v("fiber"), sugar: v("sugars"), sodium: v("sodium"), salt: v("salt")
        )
        for id in Micronutrients.vitaminIds { if let x = v(id) { out.vitamins[id] = x } }
        for id in Micronutrients.mineralIds { if let x = v(id) { out.minerals[id] = x } }
        return out.isEmpty ? nil : out
    }

    /// Text in the app language when OFF has it, else in the product's main language.
    private static func localized(_ p: JSON, _ field: String, _ lang: String) -> String? {
        (p.str("\(field)_\(lang)") ?? p.str(field) ?? p.str("\(field)_en") ?? p.str("\(field)_el")).map(unescape)
    }

    /// nil when OFF reports the product as missing (status != 1).
    static func parseProductResponse(_ root: JSON, barcode: String, lang: String = "en") -> Product? {
        guard root.int("status") == 1, let p = root["product"] as? JSON else { return nil }
        return parseProduct(p, barcode: barcode, lang: lang)
    }

    static func parseProduct(_ p: JSON, barcode: String?, lang: String = "en") -> Product {
        let nutriments = p["nutriments"] as? JSON ?? [:]
        let per100 = nutrition(nutriments, "100g") ?? Nutrition()
        let servingGrams = p.num("serving_quantity").flatMap { $0 > 0 ? $0 : nil }
        let perServing = nutrition(nutriments, "serving") ?? (per100.isEmpty ? nil : servingGrams.map { per100.scaled($0 / 100) })
        let nutri = grade(p.str("nutriscore_grade"))
        let nova = p.int("nova_group").flatMap { (1...4).contains($0) ? $0 : nil }
        return Product(
            barcode: barcode ?? p.str("code"),
            name: localized(p, "product_name", lang) ?? "",
            brand: p.str("brands")?.split(separator: ",").first.map { $0.trimmingCharacters(in: .whitespaces) },
            quantity: p.str("quantity").map(stripEstimatedMark).flatMap { $0.isEmpty ? nil : $0 }, servingSize: p.str("serving_size"), servingGrams: servingGrams,
            imageUrl: p.str("image_url"),
            ingredientsText: localized(p, "ingredients_text", lang)?.replacingOccurrences(of: "_", with: ""), // OFF wraps allergens in _underscores_
            allergenTags: p.tags("allergens_tags"), additiveTags: p.tags("additives_tags"),
            categoryTags: p.tags("categories_tags"), labelTags: p.tags("labels_tags"),
            nutritionPer100g: per100, nutritionPerServing: perServing,
            nutriscoreGrade: nutri, novaGroup: nova, ecoscoreGrade: grade(p.str("ecoscore_grade")),
            nufoScore: Scoring.nufoScore(per100, nutriscore: nutri, nova: nova).score,
            source: source, lastUpdated: Int64(p.num("last_modified_t") ?? 0) * 1000,
            completeness: p.num("completeness").map { min(1, max(0, $0)) },
            soldInGreece: p.tags("countries_tags").contains("en:greece")
        )
    }

    /// Legacy search.pl returns "products"; Search-a-licious returns "hits" with brands as an array.
    static func parseSearch(_ root: JSON, lang: String = "en") -> [SearchHit] {
        let items = (root["hits"] as? [JSON]) ?? (root["products"] as? [JSON]) ?? []
        return items.compactMap { p in
            guard let name = localized(p, "product_name", lang) else { return nil }
            let brand = (p["brands"] as? [String])?.first?.trimmingCharacters(in: .whitespaces)
                ?? p.str("brands")?.split(separator: ",").first.map { $0.trimmingCharacters(in: .whitespaces) }
            let n = p["nutriments"] as? JSON
            return SearchHit(
                name: name, brand: brand, imageUrl: p.str("image_url"),
                nutriscoreGrade: grade(p.str("nutriscore_grade")),
                novaGroup: p.int("nova_group").flatMap { (1...4).contains($0) ? $0 : nil },
                caloriesPer100g: n.flatMap { kcal($0.num("energy-kcal_100g"), $0.num("energy_100g"), perGrams: 100) },
                source: source, barcode: p.str("code"),
                soldInGreece: p.tags("countries_tags").contains("en:greece")
            )
        }
    }

    /// Taxonomy response `{"en:milk":{"name":{"el":"γάλα"}}}` -> tag id to translated name.
    static func parseTaxonomy(_ root: JSON, lang: String) -> [String: String] {
        var out: [String: String] = [:]
        for (tag, v) in root {
            if let name = ((v as? JSON)?["name"] as? JSON)?.str(lang) { out[tag] = name.prefix(1).uppercased() + name.dropFirst() }
        }
        return out
    }
}

enum UsdaParser {
    static let source = "USDA FoodData Central"
    private static let mg = 0.001, ug = 0.000001

    static func parseSearch(_ root: JSON) -> [SearchHit] {
        ((root["foods"] as? [JSON]) ?? []).compactMap { f in
            guard let p = parseFood(f) else { return nil }
            return SearchHit(name: p.name, brand: p.brand, imageUrl: nil, nutriscoreGrade: nil, novaGroup: nil,
                             caloriesPer100g: p.nutritionPer100g.calories, source: source, barcode: p.barcode, product: p)
        }
    }

    /// FDC search values are per 100 g, keyed by legacy nutrient number.
    static func parseFood(_ f: JSON) -> Product? {
        guard let name = f.str("description") else { return nil }
        var nutrients: [String: Double] = [:]
        for o in (f["foodNutrients"] as? [JSON]) ?? [] {
            if let n = o.str("nutrientNumber"), let v = o.num("value") { nutrients[n] = v }
        }
        func g(_ n: String, _ factor: Double = 1) -> Double? { nutrients[n].map { $0 * factor } }
        let sodium = g("307", mg)
        var per100 = Nutrition(
            calories: g("208") ?? g("957") ?? g("958"), protein: g("203"), carbs: g("205"), fat: g("204"),
            saturatedFat: g("606"), fiber: g("291"), sugar: g("269"), sodium: sodium,
            salt: sodium.map { $0 * 2.5 } // EU labelling convention: salt = sodium x 2.5
        )
        for (n, id, f) in [("320", "vitamin-a", ug), ("401", "vitamin-c", mg), ("328", "vitamin-d", ug),
                           ("323", "vitamin-e", mg), ("415", "vitamin-b6", mg), ("418", "vitamin-b12", ug)] {
            if let v = g(n, f) { per100.vitamins[id] = v }
        }
        for (n, id) in [("301", "calcium"), ("303", "iron"), ("304", "magnesium"), ("306", "potassium"), ("309", "zinc")] {
            if let v = g(n, mg) { per100.minerals[id] = v }
        }
        let unit = f.str("servingSizeUnit")?.lowercased()
        let servingGrams = (unit == "g" || unit == "grm") ? f.num("servingSize") : nil
        var updated: Int64 = 0
        if let d = f.str("publishedDate") ?? f.str("modifiedDate") {
            let fmt = DateFormatter(); fmt.dateFormat = "yyyy-MM-dd"; fmt.timeZone = TimeZone(identifier: "UTC")
            if let date = fmt.date(from: String(d.prefix(10))) { updated = Int64(date.timeIntervalSince1970 * 1000) }
        }
        return Product(
            barcode: f.str("gtinUpc"), name: name.lowercased().prefix(1).uppercased() + name.lowercased().dropFirst(),
            brand: f.str("brandOwner") ?? f.str("brandName"), quantity: nil,
            servingSize: servingGrams.map { "\(Int($0)) g" } ?? f.str("householdServingFullText"), servingGrams: servingGrams,
            imageUrl: nil, ingredientsText: f.str("ingredients"),
            categoryTags: [],
            nutritionPer100g: per100, nutritionPerServing: servingGrams.map { per100.scaled($0 / 100) },
            nutriscoreGrade: nil, novaGroup: nil, ecoscoreGrade: nil,
            nufoScore: Scoring.nufoScore(per100, nutriscore: nil, nova: nil).score,
            source: source, lastUpdated: updated
        )
    }
}
enum PricesParser {
    static let source = "Open Prices"

    /// Latest reports, Greek stores first; at most `limit`.
    static func parse(_ root: JSON, limit: Int = 3) -> [PriceReport] {
        let items = (root["items"] as? [JSON]) ?? []
        let reports: [PriceReport] = items.compactMap { o in
            guard let price = o.num("price"), price > 0, let currency = o.str("currency"), let date = o.str("date") else { return nil }
            let loc = o["location"] as? JSON
            return PriceReport(price: price, currency: currency, date: date,
                               store: loc?.str("osm_name"), city: loc?.str("osm_address_city"), country: loc?.str("osm_address_country"))
        }
        return Array(reports.enumerated().sorted { a, b in
            a.element.inGreece != b.element.inGreece ? a.element.inGreece : a.offset < b.offset
        }.map(\.element).prefix(limit))
    }
}

enum UpcItemDbParser {
    static let source = "UPCitemdb"

    static func parse(_ root: JSON) -> Identified? {
        guard let item = (root["items"] as? [JSON])?.first, let title = item.str("title") else { return nil }
        // Their image links point at third-party shops; only https ones are safe to load.
        return Identified(name: title, brand: item.str("brand"),
                          imageUrl: item.tags("images").first { $0.hasPrefix("https://") }, source: source)
    }
}
