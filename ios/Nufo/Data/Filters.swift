import Foundation

/// EU's 14 regulated allergens as Open Food Facts tag ids; names come from `TagNames`.
let allergenTags = [
    "en:gluten", "en:milk", "en:eggs", "en:nuts", "en:peanuts", "en:soybeans", "en:fish", "en:crustaceans",
    "en:molluscs", "en:sesame-seeds", "en:celery", "en:mustard", "en:lupin", "en:sulphur-dioxide-and-sulphites",
]

/// Search categories as OFF tag ids with their localization keys.
let searchCategories: [(tag: String, key: String)] = [
    ("en:dairies", "Dairy"), ("en:cheeses", "Cheese"), ("en:yogurts", "Yogurt"), ("en:olive-oils", "Olive oil"),
    ("en:beverages", "Drinks"), ("en:snacks", "Snacks"), ("en:breakfasts", "Breakfast"),
    ("en:cereals-and-potatoes", "Cereals & potatoes"), ("en:fruits-and-vegetables-based-foods", "Fruit & veg"),
    ("en:meats-and-their-products", "Meat"), ("en:desserts", "Desserts"),
]

extension Product {
    /// Allergen tags from the user's alert list that this product declares.
    func matchingAllergens(_ alertTags: Set<String>) -> [String] { allergenTags.filter { alertTags.contains($0) } }
}

struct SearchFilters: Equatable {
    var category: String? = nil
    /// Only products sold in Greece (Open Food Facts countries_tags).
    var greekOnly = false
    var grades: Set<String> = []
    var nova: Set<Int> = []
    var vegetarian = false
    var vegan = false
    var excludeAllergens: Set<String> = []

    /// Tag filters only Open Food Facts can apply; USDA results are hidden while any is active.
    var needsOffTags: Bool { category != nil || greekOnly || vegetarian || vegan || !excludeAllergens.isEmpty }
    var isActive: Bool { needsOffTags || !grades.isEmpty || !nova.isEmpty }

    var offQueryClauses: [String] {
        var out: [String] = []
        if let c = category { out.append("categories_tags:\"\(c)\"") }
        if greekOnly { out.append("countries_tags:\"en:greece\"") }
        if vegan { out.append("labels_tags:\"en:vegan\"") } else if vegetarian { out.append("labels_tags:\"en:vegetarian\"") }
        for a in excludeAllergens.sorted() { out.append("-allergens_tags:\"\(a)\"") }
        return out
    }

    func accepts(_ h: SearchHit) -> Bool {
        (grades.isEmpty || grades.contains(h.nutriscoreGrade ?? "")) &&
            (nova.isEmpty || nova.contains(h.novaGroup ?? 0)) &&
            (!needsOffTags || h.source == OffParser.source)
    }
}