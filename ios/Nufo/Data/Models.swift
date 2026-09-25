import Foundation

struct Nutrition: Codable, Hashable {
    var calories: Double? = nil
    var protein: Double? = nil
    var carbs: Double? = nil
    var fat: Double? = nil
    var saturatedFat: Double? = nil
    var fiber: Double? = nil
    var sugar: Double? = nil
    /// Grams.
    var sodium: Double? = nil
    /// Grams.
    var salt: Double? = nil
    /// Keyed by Open Food Facts nutrient id, e.g. "vitamin-c".
    var vitamins: [String: Double] = [:]
    var minerals: [String: Double] = [:]

    var isEmpty: Bool { self == Nutrition() }

    func scaled(_ f: Double) -> Nutrition {
        Nutrition(
            calories: calories.map { $0 * f }, protein: protein.map { $0 * f }, carbs: carbs.map { $0 * f },
            fat: fat.map { $0 * f }, saturatedFat: saturatedFat.map { $0 * f }, fiber: fiber.map { $0 * f },
            sugar: sugar.map { $0 * f }, sodium: sodium.map { $0 * f }, salt: salt.map { $0 * f },
            vitamins: vitamins.mapValues { $0 * f }, minerals: minerals.mapValues { $0 * f }
        )
    }
}

struct Product: Codable, Hashable, Identifiable {
    var barcode: String?
    var name: String
    var brand: String?
    var quantity: String?
    var servingSize: String?
    /// Serving weight in grams when the source states it.
    var servingGrams: Double?
    var imageUrl: String?
    var ingredientsText: String?
    /// Raw Open Food Facts tag ids ("en:milk"); display names come from `tagName(_:lang:)`.
    var allergenTags: [String] = []
    var additiveTags: [String] = []
    var categoryTags: [String] = []
    var labelTags: [String] = []
    /// Tag id -> name in the app language, from the Open Food Facts taxonomy.
    var tagNames: [String: String] = [:]
    var nutritionPer100g: Nutrition
    var nutritionPerServing: Nutrition?
    var nutriscoreGrade: String?
    var novaGroup: Int?
    var ecoscoreGrade: String?
    var nufoScore: Int
    var source: String
    /// Milliseconds since epoch, 0 when unknown.
    var lastUpdated: Int64
    /// Open Food Facts' own 0...1 completeness estimate; nil for sources that don't publish one.
    var completeness: Double? = nil
    /// True when Open Food Facts lists Greece among the countries where it is sold.
    var soldInGreece = false

    /// Stable history key: barcode when known, otherwise source + name.
    var key: String { barcode ?? "\(source):\(name)" }
    var id: String { key }
}

/// Search row. OFF hits carry only a barcode (detail fetched on tap); USDA hits are complete.
struct SearchHit: Identifiable, Hashable {
    let id = UUID()
    var name: String
    var brand: String?
    var imageUrl: String?
    var nutriscoreGrade: String?
    var novaGroup: Int?
    var caloriesPer100g: Double?
    var source: String
    var barcode: String?
    var product: Product?
    var soldInGreece = false
}
/// Open Food Facts serves each selected photo at 100/200/400 px height and at full resolution
/// ("front_el.12.400.jpg" / "front_el.12.full.jpg"). Thumbnails use 400 px (sharp at 3x, shared with the
/// offline cache); the hero adds full resolution on top.
func offImage(_ url: String?, _ size: String) -> String? {
    guard let url else { return nil }
    return url.replacingOccurrences(of: #"\.(100|200|400)\.jpg$"#, with: ".\(size).jpg", options: .regularExpression)
}

/// One shopper-reported price from Open Prices.
struct PriceReport: Hashable {
    var price: Double
    var currency: String
    /// ISO date, "2026-09-13".
    var date: String
    var store: String?
    var city: String?
    var country: String?
    var inGreece: Bool { ["Greece", "Ελλάδα", "Ελλάς"].contains(country ?? "") }
}

/// What a secondary barcode database knows about a product Open Food Facts lacks: identity only, no nutrition.
struct Identified: Hashable {
    var name: String
    var brand: String?
    var imageUrl: String?
    var source: String
}
