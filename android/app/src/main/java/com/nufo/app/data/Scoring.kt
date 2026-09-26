package com.nufo.app.data

/** Why the Nufo Score moved. The UI turns each kind into a sentence in the app language. */
enum class Reason { NutriB, NutriC, NutriD, NutriE, Nova2, Nova3, Nova4, HighProtein, HighFiber, HighSugar, HighSalt, HighSatFat }

data class ScoreReason(val reason: Reason, val delta: Int)

data class NufoScoreResult(val score: Int, val reasons: List<ScoreReason>)

enum class InsightKind(val positive: Boolean) {
    HighProtein(true), GoodFiber(true), LowFiber(false), HighSugar(false), LowSugar(true),
    HighSodium(false), LowSodium(true), HighSatFat(false), UltraProcessed(false), Unprocessed(true),
}

/** One-word verdict for a Nufo Score band. */
enum class Verdict { Excellent, Good, Fair, Poor, VeryPoor;
    companion object {
        fun of(score: Int) = when {
            score >= 80 -> Excellent; score >= 60 -> Good; score >= 40 -> Fair; score >= 20 -> Poor; else -> VeryPoor
        }
    }
}

/** UK FSA front-of-pack traffic-light level for a nutrient per 100 g of food. */
enum class Level { Low, Medium, High }

/**
 * Transparent 0–100 score. Every adjustment is returned as a reason so the UI can show
 * exactly why a product scored what it did. Missing inputs contribute nothing — never guessed.
 */
object Scoring {
    fun nufoScore(n: Nutrition, nutriscore: String?, nova: Int?): NufoScoreResult {
        val reasons = buildList {
            when (nutriscore?.lowercase()) {
                "b" -> add(ScoreReason(Reason.NutriB, -5))
                "c" -> add(ScoreReason(Reason.NutriC, -15))
                "d" -> add(ScoreReason(Reason.NutriD, -30))
                "e" -> add(ScoreReason(Reason.NutriE, -45))
            }
            when (nova) {
                2 -> add(ScoreReason(Reason.Nova2, -5))
                3 -> add(ScoreReason(Reason.Nova3, -15))
                4 -> add(ScoreReason(Reason.Nova4, -30))
            }
            if ((n.protein ?: 0.0) >= 10) add(ScoreReason(Reason.HighProtein, +10))
            if ((n.fiber ?: 0.0) >= 5) add(ScoreReason(Reason.HighFiber, +10))
            if ((n.sugar ?: 0.0) > 10) add(ScoreReason(Reason.HighSugar, -15))
            if ((n.salt ?: 0.0) > 1) add(ScoreReason(Reason.HighSalt, -15))
            if ((n.saturatedFat ?: 0.0) > 5) add(ScoreReason(Reason.HighSatFat, -10))
        }
        return NufoScoreResult((100 + reasons.sumOf { it.delta }).coerceIn(0, 100), reasons)
    }

    /**
     * A score needs evidence: an official grade, or most of the key nutrients. Without either (a stub entry with a
     * name and little else) a high score would only mean "nothing was declared", so no score is shown at all.
     */
    fun canScore(p: Product): Boolean {
        val n = p.nutritionPer100g
        return p.nutriscoreGrade != null || p.novaGroup != null ||
            listOf(n.calories, n.fat, n.saturatedFat, n.sugar, n.salt ?: n.sodium, n.protein).count { it != null } >= 4
    }

    fun reasons(p: Product) = nufoScore(p.nutritionPer100g, p.nutriscoreGrade, p.novaGroup).reasons

    fun insights(p: Product): List<InsightKind> {
        val n = p.nutritionPer100g
        return buildList {
            n.protein?.let { if (it >= 10) add(InsightKind.HighProtein) }
            n.fiber?.let { if (it >= 5) add(InsightKind.GoodFiber) else if (it < 1.5) add(InsightKind.LowFiber) }
            n.sugar?.let { if (it > 10) add(InsightKind.HighSugar) else if (it <= 5) add(InsightKind.LowSugar) }
            n.salt?.let { if (it > 1) add(InsightKind.HighSodium) else if (it <= 0.3) add(InsightKind.LowSodium) }
            n.saturatedFat?.let { if (it > 5) add(InsightKind.HighSatFat) }
            if (p.novaGroup == 4) add(InsightKind.UltraProcessed)
            if (p.novaGroup == 1) add(InsightKind.Unprocessed)
        }
    }

    // FSA thresholds per 100 g of food (low ≤, high >). Drinks use half these values; Nufo can't
    // always tell drinks apart, so the food thresholds are shown and labelled as such.
    fun fatLevel(g: Double) = level(g, 3.0, 17.5)
    fun satFatLevel(g: Double) = level(g, 1.5, 5.0)
    fun sugarLevel(g: Double) = level(g, 5.0, 22.5)
    fun saltLevel(g: Double) = level(g, 0.3, 1.5)
    private fun level(v: Double, low: Double, high: Double) = when { v <= low -> Level.Low; v > high -> Level.High; else -> Level.Medium }
}

enum class DietNote { NotVegan, NotVegetarian, HighCarbsKeto, NotLowSodium }

/** Diet warning for this product, plus the per-100 g amount it refers to where relevant. */
fun dietNote(p: Product, diet: Diet): Pair<DietNote, Double?>? {
    val n = p.nutritionPer100g
    val animal = setOf("en:milk", "en:eggs", "en:fish", "en:crustaceans", "en:molluscs")
    val seafood = setOf("en:fish", "en:crustaceans", "en:molluscs")
    return when (diet) {
        Diet.Vegan -> if ("en:vegan" !in p.labelTags && p.allergenTags.any { it in animal }) DietNote.NotVegan to null else null
        Diet.Vegetarian -> if (p.allergenTags.any { it in seafood }) DietNote.NotVegetarian to null else null
        Diet.Keto -> n.carbs?.takeIf { it > 10 }?.let { DietNote.HighCarbsKeto to it }
        Diet.LowSodium -> n.salt?.takeIf { it > 0.3 }?.let { DietNote.NotLowSodium to it }
        Diet.None -> null
    }
}