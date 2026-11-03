package com.nufo.app.data

/** Why the Nufo Score moved. The UI turns each kind into a sentence in the app language. */
enum class Reason {
    NutriB, NutriC, NutriD, NutriE, Nova2, Nova3, Nova4,
    HighProtein, HighFiber, HighFat, HighSugar, HighSalt, HighSatFat, MediumSugar, MediumSalt, MediumSatFat,
}

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
 * Transparent 0–100 score (rules in docs/SCORING.md). Every adjustment is returned as a reason so the UI can show
 * exactly why a product scored what it did. Missing inputs contribute nothing — never guessed.
 *
 * With a Nutri-Score grade the grade is the base: it already weighs energy, sugar, saturated fat, salt, fibre,
 * protein and fruit/vegetables, so those nutrients are not counted a second time. Without a grade the nutrients
 * are judged on the same UK FSA table the traffic-light dots use, so the score and the dots never disagree.
 * NOVA processing adjusts both, never by more than one grade step.
 */
object Scoring {
    private val gradePenalty = mapOf("b" to -15, "c" to -35, "d" to -55, "e" to -75)
    private val novaPenalty = mapOf(2 to -5, 3 to -10, 4 to -15)

    /** The score for this product under the current rules, or null when it declares too little to score fairly. */
    fun score(p: Product): NufoScoreResult? = if (canScore(p)) nufoScore(p.nutritionPer100g, p.nutriscoreGrade, p.novaGroup, isDrink(p)) else null

    fun nufoScore(n: Nutrition, nutriscore: String?, nova: Int?, drink: Boolean = false): NufoScoreResult {
        val grade = nutriscore?.lowercase()?.takeIf { it in "abcde" && it.length == 1 }
        val reasons = buildList {
            if (grade != null) {
                gradePenalty[grade]?.let { add(ScoreReason(gradeReason(grade), it)) }
            } else {
                n.fat?.let { if (fatLevel(it, drink) == Level.High) add(ScoreReason(Reason.HighFat, -10)) }
                n.saturatedFat?.let { penalise(satFatLevel(it, drink), Reason.HighSatFat, Reason.MediumSatFat) }
                n.sugar?.let { penalise(sugarLevel(it, drink), Reason.HighSugar, Reason.MediumSugar) }
                (n.salt ?: n.sodium?.times(2.5))?.let { penalise(saltLevel(it, drink), Reason.HighSalt, Reason.MediumSalt) }
                if ((n.protein ?: 0.0) >= 10) add(ScoreReason(Reason.HighProtein, +5))
                // EU "high fibre" claim threshold.
                if ((n.fiber ?: 0.0) >= 6) add(ScoreReason(Reason.HighFiber, +5))
            }
            nova?.let { g -> novaPenalty[g]?.let { add(ScoreReason(novaReason(g), it)) } }
        }
        return NufoScoreResult((100 + reasons.sumOf { it.delta }).coerceIn(0, 100), reasons)
    }

    private fun MutableList<ScoreReason>.penalise(level: Level, high: Reason, medium: Reason) = when (level) {
        Level.High -> add(ScoreReason(high, -20))
        Level.Medium -> add(ScoreReason(medium, -5))
        Level.Low -> false
    }

    private fun gradeReason(g: String) = when (g) { "b" -> Reason.NutriB; "c" -> Reason.NutriC; "d" -> Reason.NutriD; else -> Reason.NutriE }
    private fun novaReason(n: Int) = when (n) { 2 -> Reason.Nova2; 3 -> Reason.Nova3; else -> Reason.Nova4 }

    /**
     * A score needs evidence: an official grade, or most of the key nutrients. Without either (a stub entry with a
     * name and little else) a high score would only mean "nothing was declared", so no score is shown at all.
     * A NOVA group alone says how processed a food is, not how nutritious, so it is not enough on its own.
     */
    fun canScore(p: Product): Boolean {
        val n = p.nutritionPer100g
        return p.nutriscoreGrade != null ||
            listOf(n.calories, n.fat, n.saturatedFat, n.sugar, n.salt ?: n.sodium, n.protein).count { it != null } >= 4
    }

    /** Drinks are judged per 100 ml on the FSA drink thresholds, which are half the food ones. */
    fun isDrink(p: Product) = p.categoryTags.any { it == "en:beverages" || it == "en:drinks" || it.endsWith("-beverages") }

    fun reasons(p: Product) = score(p)?.reasons.orEmpty()

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

    // FSA front-of-pack thresholds per 100 g of food (low ≤, high >). Drinks use half these values per 100 ml.
    fun fatLevel(g: Double, drink: Boolean = false) = level(g, 3.0, 17.5, drink)
    fun satFatLevel(g: Double, drink: Boolean = false) = level(g, 1.5, 5.0, drink)
    fun sugarLevel(g: Double, drink: Boolean = false) = level(g, 5.0, 22.5, drink)
    fun saltLevel(g: Double, drink: Boolean = false) = level(g, 0.3, 1.5, drink)
    private fun level(v: Double, low: Double, high: Double, drink: Boolean): Level {
        val f = if (drink) 0.5 else 1.0
        return when { v <= low * f -> Level.Low; v > high * f -> Level.High; else -> Level.Medium }
    }
}

enum class DietNote { NotVegan, NotVegetarian, HighCarbsKeto, NotLowSodium }

/** Diet warning for this product, plus the per-100 g amount it refers to where relevant. */
fun dietNote(p: Product, diet: Diet): Pair<DietNote, Double?>? {
    val n = p.nutritionPer100g
    val animal = setOf("en:milk", "en:eggs", "en:fish", "en:crustaceans", "en:molluscs")
    val seafood = setOf("en:fish", "en:crustaceans", "en:molluscs")
    // Meat is not an allergen, so its categories must be checked too ("meat-analogues" are plant-based).
    val meat = p.categoryTags.any { t ->
        !t.contains("analogue") && !t.contains("substitute") &&
            listOf("en:meat", "en:poultr", "en:ham", "en:sausage", "en:fishes", "en:seafood", "en:charcuterie").any { t.startsWith(it) }
    }
    return when (diet) {
        Diet.Vegan -> if ("en:vegan" !in p.labelTags && (meat || p.allergenTags.any { it in animal })) DietNote.NotVegan to null else null
        Diet.Vegetarian -> if ("en:vegetarian" !in p.labelTags && "en:vegan" !in p.labelTags && (meat || p.allergenTags.any { it in seafood })) DietNote.NotVegetarian to null else null
        Diet.Keto -> n.carbs?.takeIf { it > 10 }?.let { DietNote.HighCarbsKeto to it }
        Diet.LowSodium -> n.salt?.takeIf { it > 0.3 }?.let { DietNote.NotLowSodium to it }
        Diet.None -> null
    }
}