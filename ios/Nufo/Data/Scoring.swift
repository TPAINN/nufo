import Foundation

/// Why the Nufo Score moved; the UI turns each into a localized sentence. Mirrors Scoring.kt.
enum Reason: Hashable {
    case nutriB, nutriC, nutriD, nutriE, nova2, nova3, nova4, highProtein, highFiber, highSugar, highSalt, highSatFat
}
struct ScoreReason: Hashable { let reason: Reason; let delta: Int }

enum InsightKind: Hashable {
    case highProtein, goodFiber, lowFiber, highSugar, lowSugar, highSodium, lowSodium, highSatFat, ultraProcessed, unprocessed
    var positive: Bool { [.highProtein, .goodFiber, .lowSugar, .lowSodium, .unprocessed].contains(self) }
}

enum Verdict {
    case excellent, good, fair, poor, veryPoor
    static func of(_ s: Int) -> Verdict { s >= 80 ? .excellent : s >= 60 ? .good : s >= 40 ? .fair : s >= 20 ? .poor : .veryPoor }
}

/// UK FSA front-of-pack traffic-light level per 100 g of food.
enum Level: CaseIterable { case low, medium, high }

enum Scoring {
    static func nufoScore(_ n: Nutrition, nutriscore: String?, nova: Int?) -> (score: Int, reasons: [ScoreReason]) {
        var r: [ScoreReason] = []
        switch nutriscore?.lowercased() {
        case "b": r.append(.init(reason: .nutriB, delta: -5))
        case "c": r.append(.init(reason: .nutriC, delta: -15))
        case "d": r.append(.init(reason: .nutriD, delta: -30))
        case "e": r.append(.init(reason: .nutriE, delta: -45))
        default: break
        }
        switch nova {
        case 2: r.append(.init(reason: .nova2, delta: -5))
        case 3: r.append(.init(reason: .nova3, delta: -15))
        case 4: r.append(.init(reason: .nova4, delta: -30))
        default: break
        }
        if (n.protein ?? 0) >= 10 { r.append(.init(reason: .highProtein, delta: 10)) }
        if (n.fiber ?? 0) >= 5 { r.append(.init(reason: .highFiber, delta: 10)) }
        if (n.sugar ?? 0) > 10 { r.append(.init(reason: .highSugar, delta: -15)) }
        if (n.salt ?? 0) > 1 { r.append(.init(reason: .highSalt, delta: -15)) }
        if (n.saturatedFat ?? 0) > 5 { r.append(.init(reason: .highSatFat, delta: -10)) }
        return (min(100, max(0, 100 + r.reduce(0) { $0 + $1.delta })), r)
    }

    static func reasons(_ p: Product) -> [ScoreReason] {
        nufoScore(p.nutritionPer100g, nutriscore: p.nutriscoreGrade, nova: p.novaGroup).reasons
    }

    static func insights(_ p: Product) -> [InsightKind] {
        let n = p.nutritionPer100g
        var out: [InsightKind] = []
        if let v = n.protein, v >= 10 { out.append(.highProtein) }
        if let v = n.fiber { if v >= 5 { out.append(.goodFiber) } else if v < 1.5 { out.append(.lowFiber) } }
        if let v = n.sugar { if v > 10 { out.append(.highSugar) } else if v <= 5 { out.append(.lowSugar) } }
        if let v = n.salt { if v > 1 { out.append(.highSodium) } else if v <= 0.3 { out.append(.lowSodium) } }
        if let v = n.saturatedFat, v > 5 { out.append(.highSatFat) }
        if p.novaGroup == 4 { out.append(.ultraProcessed) }
        if p.novaGroup == 1 { out.append(.unprocessed) }
        return out
    }

    // FSA thresholds per 100 g of food (low <=, high >).
    static func fatLevel(_ g: Double) -> Level { level(g, 3, 17.5) }
    static func satFatLevel(_ g: Double) -> Level { level(g, 1.5, 5) }
    static func sugarLevel(_ g: Double) -> Level { level(g, 5, 22.5) }
    static func saltLevel(_ g: Double) -> Level { level(g, 0.3, 1.5) }
    private static func level(_ v: Double, _ low: Double, _ high: Double) -> Level { v <= low ? .low : v > high ? .high : .medium }
}

enum DietNote { case notVegan, notVegetarian, highCarbsKeto(Double), notLowSodium(Double) }

func dietNote(_ p: Product, _ diet: Diet) -> DietNote? {
    let n = p.nutritionPer100g
    let animal: Set = ["en:milk", "en:eggs", "en:fish", "en:crustaceans", "en:molluscs"]
    let seafood: Set = ["en:fish", "en:crustaceans", "en:molluscs"]
    switch diet {
    case .vegan: return !p.labelTags.contains("en:vegan") && p.allergenTags.contains { animal.contains($0) } ? .notVegan : nil
    case .vegetarian: return p.allergenTags.contains { seafood.contains($0) } ? .notVegetarian : nil
    case .keto: return n.carbs.flatMap { $0 > 10 ? .highCarbsKeto($0) : nil }
    case .lowSodium: return n.salt.flatMap { $0 > 0.3 ? .notLowSodium($0) : nil }
    case .none: return nil
    }
}