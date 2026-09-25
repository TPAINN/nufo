import Foundation
import SwiftData
import SwiftUI

/// History row. The full product is stored as JSON so history renders offline exactly as scanned.
@Model
final class HistoryItem {
    @Attribute(.unique) var key: String
    var json: Data
    var savedAt: Date

    init(product: Product) {
        key = product.key
        json = (try? JSONEncoder().encode(product)) ?? Data()
        savedAt = .now
    }

    var product: Product? { try? JSONDecoder().decode(Product.self, from: json) }
}

enum Units: String, CaseIterable { case metric = "Metric", imperial = "Imperial" }
enum ThemeMode: String, CaseIterable {
    case system = "System", light = "Light", dark = "Dark"
    var scheme: ColorScheme? { self == .light ? .light : self == .dark ? .dark : nil }
}
enum Diet: String, CaseIterable { case none = "None", vegetarian = "Vegetarian", vegan = "Vegan", keto = "Keto", lowSodium = "Low-sodium" }

/// Preferences live in UserDefaults via @AppStorage; allergen tags are stored comma-separated.
enum Prefs {
    static let onboarded = "onboarded", units = "units", theme = "theme", diet = "diet", allergens = "allergens"
    static func tags(_ raw: String) -> Set<String> { Set(raw.split(separator: ",").map(String.init)) }
}

extension ModelContext {
    func saveProduct(_ p: Product) {
        let key = p.key
        try? delete(model: HistoryItem.self, where: #Predicate { $0.key == key })
        insert(HistoryItem(product: p))
        try? save()
    }
    func removeProduct(_ p: Product) {
        let key = p.key
        try? delete(model: HistoryItem.self, where: #Predicate { $0.key == key })
        try? save()
    }
}