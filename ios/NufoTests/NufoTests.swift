import XCTest
@testable import Nufo

/// Same cases and the same real API fixtures as the Android unit tests, so both apps score identically.
final class ScoringTests: XCTestCase {
    func testPerfectDataIsCappedAt100() {
        XCTAssertEqual(Scoring.nufoScore(Nutrition(protein: 20, fiber: 8), nutriscore: "a", nova: 1).score, 100)
    }
    func testWorstCaseFloorsAtZero() {
        XCTAssertEqual(Scoring.nufoScore(Nutrition(saturatedFat: 10, sugar: 50, salt: 3), nutriscore: "e", nova: 4).score, 0)
    }
    func testEachRuleAppliesItsDelta() {
        XCTAssertEqual(Scoring.nufoScore(Nutrition(), nutriscore: "b", nova: nil).score, 95)
        XCTAssertEqual(Scoring.nufoScore(Nutrition(), nutriscore: "d", nova: nil).score, 70)
        XCTAssertEqual(Scoring.nufoScore(Nutrition(), nutriscore: nil, nova: 3).score, 85)
        XCTAssertEqual(Scoring.nufoScore(Nutrition(sugar: 10.1), nutriscore: nil, nova: nil).score, 85)
        XCTAssertEqual(Scoring.nufoScore(Nutrition(saturatedFat: 5.1), nutriscore: nil, nova: nil).score, 90)
    }
    func testThresholdsAreExclusive() {
        XCTAssertEqual(Scoring.nufoScore(Nutrition(saturatedFat: 5, sugar: 10, salt: 1), nutriscore: nil, nova: nil).score, 100)
    }
    func testMissingValuesNeverChangeScore() {
        let r = Scoring.nufoScore(Nutrition(), nutriscore: nil, nova: nil)
        XCTAssertEqual(r.score, 100); XCTAssertTrue(r.reasons.isEmpty)
    }
}

final class ParserTests: XCTestCase {
    private func fixture(_ name: String) throws -> JSON {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: name, withExtension: "json"))
        return try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? JSON)
    }

    func testParsesRealOpenFoodFactsProduct() throws {
        let p = try XCTUnwrap(OffParser.parseProductResponse(try fixture("off_nutella"), barcode: "3017620422003"))
        XCTAssertEqual(p.name, "Nutella")
        XCTAssertEqual(p.nutriscoreGrade, "e")
        XCTAssertEqual(p.novaGroup, 4)
        XCTAssertNil(p.ecoscoreGrade, "'unknown' eco-score must not be shown as a grade")
        XCTAssertEqual(p.nutritionPer100g.calories ?? 0, 539, accuracy: 0.5)
        XCTAssertEqual(p.allergenTags, ["en:milk", "en:nuts", "en:soybeans"])
        XCTAssertEqual(p.tagName("en:milk", lang: "el"), "Γάλα")
    }
    func testMissingProductReturnsNil() {
        XCTAssertNil(OffParser.parseProductResponse(["status": 0], barcode: "000"))
    }
    func testAbsentNutrientsStayNil() {
        let p = OffParser.parseProductResponse(["status": 1, "product": ["product_name": "Water", "nutriments": ["energy-kcal_100g": 0]]], barcode: "1")!
        XCTAssertEqual(p.nutritionPer100g.calories, 0)
        XCTAssertNil(p.nutritionPer100g.protein)
        XCTAssertNil(p.nutritionPerServing)
    }
    func testParsesSearchALiciousHits() throws {
        let hits = OffParser.parseSearch(try fixture("off_search_hits"))
        XCTAssertEqual(hits.count, 3)
        XCTAssertEqual(hits[0].brand, "Stonyfield Organic")
        XCTAssertEqual(hits[0].barcode, "0052159703356")
    }
    func testParsesRealUsdaSearch() throws {
        let n = try XCTUnwrap(UsdaParser.parseSearch(try fixture("usda_banana")).first?.product?.nutritionPer100g)
        XCTAssertEqual(n.calories ?? 0, 346, accuracy: 0.1)
        XCTAssertEqual(n.sodium ?? 0, 0.003, accuracy: 1e-6)
    }
    func testFiltersBecomeLuceneClauses() {
        let f = SearchFilters(category: "en:dairies", vegan: true, excludeAllergens: ["en:milk"])
        XCTAssertEqual(f.offQueryClauses, ["categories_tags:\"en:dairies\"", "labels_tags:\"en:vegan\"", "-allergens_tags:\"en:milk\""])
    }
}

final class GreekSearchTests: XCTestCase {
    func testNormalizeRemovesAccentsAndFinalSigma() {
        XCTAssertEqual(GreekSearch.normalize("Γιαούρτι"), "γιαουρτι")
        XCTAssertEqual(GreekSearch.normalize("φακές"), "φακεσ")
    }
    func testTranslatesKnownWords() {
        XCTAssertEqual(GreekSearch.english("γιαουρτι"), "yogurt")
        XCTAssertEqual(GreekSearch.english("Στραγγιστό ΓΙΑΟΎΡΤΙ"), "strained yogurt")
        XCTAssertEqual(GreekSearch.english("φέτα 2%"), "feta 2%")
        XCTAssertNil(GreekSearch.english("ΔΕΛΤΑ γάλα"))
    }
    func testVariants() {
        XCTAssertEqual(GreekSearch.variants("γιαούρτι"), ["γιαούρτι", "γιαουρτι", "yogurt"])
        XCTAssertEqual(GreekSearch.variants("feta"), ["feta"])
    }
    func testRejectsImpossibleEnergy() {
        XCTAssertEqual(kcal(1291, 1100, perGrams: 100)!, 1100 / 4.184, accuracy: 0.01)
        XCTAssertNil(kcal(1291, 5400, perGrams: 100))
    }
    func testFsaLevels() {
        XCTAssertEqual(Scoring.sugarLevel(5), .low)
        XCTAssertEqual(Scoring.saltLevel(1.6), .high)
    }
}
final class ExtraSourcesTests: XCTestCase {
    func testEstimatedMarkIsStripped() {
        XCTAssertEqual(stripEstimatedMark("400 g e"), "400 g")
        XCTAssertEqual(stripEstimatedMark("1 l ℮"), "1 l")
        XCTAssertEqual(stripEstimatedMark("Large"), "Large")
    }
    func testOffImageSizes() {
        let u = "https://images.openfoodfacts.org/images/products/520/103/750/8506/front_el.12.400.jpg"
        XCTAssertEqual(offImage(u, "full"), "https://images.openfoodfacts.org/images/products/520/103/750/8506/front_el.12.full.jpg")
        XCTAssertEqual(offImage("https://example.com/a.png", "400"), "https://example.com/a.png")
        XCTAssertNil(offImage(nil, "400"))
    }
    func testPricesPutGreekStoresFirstAndDropZero() {
        let root: JSON = ["items": [
            ["price": 4.68, "currency": "EUR", "date": "2026-09-13", "location": ["osm_name": "Carrefour", "osm_address_country": "France"]],
            ["price": 0, "currency": "EUR", "date": "2026-09-12"],
            ["price": 3.99, "currency": "EUR", "date": "2026-09-01", "location": ["osm_name": "ΑΒ", "osm_address_country": "Ελλάδα"]],
        ]]
        let r = PricesParser.parse(root)
        XCTAssertEqual(r.map(\.price), [3.99, 4.68])
        XCTAssertTrue(r[0].inGreece)
    }
    func testUpcItemDbKeepsOnlyHttpsImages() {
        let root: JSON = ["items": [["title": "Coca-Cola 12 oz", "brand": "Coca-Cola", "images": ["http://x.com/a.jpg", "https://y.com/b.jpg"]]]]
        XCTAssertEqual(UpcItemDbParser.parse(root)?.imageUrl, "https://y.com/b.jpg")
        XCTAssertNil(UpcItemDbParser.parse(["items": []]))
    }
}
