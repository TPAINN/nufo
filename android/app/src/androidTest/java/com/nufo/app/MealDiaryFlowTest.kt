package com.nufo.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nufo.app.data.MealParser
import com.nufo.app.data.Nutrition
import com.nufo.app.data.Product
import com.nufo.app.data.RecipePart
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * End-to-end over the real database and screens: a week of photographed plates appears under History > Meals, a
 * section per day, and a plate opens its own page. [seedSampleDiary] alone also prepares the emulator for recordings.
 */
@RunWith(AndroidJUnit4::class)
class MealDiaryFlowTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NufoApp

    private fun s(id: Int) = app.getString(id)
    private fun seen(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    /** name, photo asset, days ago, time, grams, kcal per 100 g, foods */
    private val week = listOf(
        Plate("Ομελέτα με λουκάνικο", "omelette", 0, 8, 40, 210, 195.0, "Ομελέτα", "Λουκάνικο ψητό"),
        Plate("Σολομός με λαχανικά", "salmon", 0, 13, 20, 400, 137.5, "Σολομός ψητός", "Κολοκυθάκια ψητά", "Πλιγούρι"),
        Plate("Βάφλα με φράουλες", "waffles", 1, 9, 10, 200, 187.5, "Βάφλα", "Φράουλες"),
        Plate("Σαλάτα του Καίσαρα", "caesar", 1, 14, 5, 175, 154.0, "Μαρούλι", "Κρουτόν", "Σως Caesar"),
        Plate("Σπαγγέτι μπολονέζ", "spaghetti", 1, 20, 45, 365, 164.0, "Σπαγγέτι", "Σάλτσα κιμά", "Τυρί τριμμένο"),
        Plate("Αυγά Benedict", "benedict", 2, 10, 30, 200, 257.5, "Αυγά ποσέ", "Σως ολλαντέζ", "Ψωμί μπριός"),
        Plate("Κλαμπ σάντουιτς με πατάτες", "club", 2, 15, 0, 380, 260.0, "Κλαμπ σάντουιτς", "Πατάτες τηγανητές"),
        Plate("Χούμους με πίτα", "hummus", 3, 13, 15, 190, 221.0, "Χούμους", "Πίτα"),
        Plate("Φαλάφελ με σαλάτα", "falafel", 3, 20, 30, 240, 227.0, "Φαλάφελ", "Σαλάτα", "Ταχίνι"),
        Plate("Καπρέζε", "caprese", 6, 13, 40, 230, 158.0, "Μοτσαρέλα", "Ντομάτα", "Ελαιόλαδο"),
    )

    private data class Plate(val name: String, val photo: String, val daysAgo: Long, val h: Int, val m: Int,
                             val grams: Int, val kcal100: Double, val foods: List<String>) {
        constructor(name: String, photo: String, daysAgo: Long, h: Int, m: Int, grams: Int, kcal100: Double, vararg foods: String) :
            this(name, photo, daysAgo, h, m, grams, kcal100, foods.toList())
    }

    @Test fun seedSampleDiary() = runBlocking {
        val photos = File(app.filesDir, "photos").apply { mkdirs() }
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        for (p in week) {
            val time = LocalDate.now().minusDays(p.daysAgo).atTime(LocalTime.of(p.h, p.m)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val file = File(photos, "$time.jpg")
            assets.open("diary/${p.photo}.jpg").use { input -> file.outputStream().use { input.copyTo(it) } }
            app.repository.save(Product(
                barcode = null, name = p.name, brand = s(R.string.meal_brand), quantity = null, servingSize = "${p.grams} g",
                servingGrams = p.grams.toDouble(), imageUrl = android.net.Uri.fromFile(file).toString(),
                ingredientsText = p.foods.joinToString(), nutritionPer100g = Nutrition(calories = p.kcal100, protein = p.kcal100 * 0.045, carbs = p.kcal100 * 0.1, fat = p.kcal100 * 0.04, saturatedFat = p.kcal100 * 0.012, sugar = p.kcal100 * 0.02, fiber = 2.0, salt = 0.5), nutritionPerServing = null,
                nutriscoreGrade = null, novaGroup = null, ecoscoreGrade = null, nufoScore = 0, source = MealParser.SOURCE,
                lastUpdated = time, isDish = true, mealId = time,
                recipe = p.foods.map { RecipePart(it, 100.0 / p.foods.size) },
            ))
        }
    }

    @Test fun meals_are_listed_by_day_and_open_their_page() {
        // Arrange
        seedSampleDiary()
        ActivityScenario.launch(MainActivity::class.java)
        rule.waitUntil(10_000) { seen(s(R.string.welcome_start)) || seen(s(R.string.home_scan)) }
        if (seen(s(R.string.welcome_start))) rule.onNodeWithText(s(R.string.welcome_start)).performClick()
        rule.waitUntil(10_000) { seen(s(R.string.nav_history)) }
        rule.onAllNodesWithText(s(R.string.nav_history)).onFirst().performClick()

        // Act
        rule.waitUntil(10_000) { seen(s(R.string.history_tab_meals)) }
        rule.onNodeWithText(s(R.string.history_tab_meals)).performClick()

        // Assert: today first, with its total (410 + 550 kcal), then yesterday
        rule.waitUntil(10_000) { seen(s(R.string.day_today)) }
        rule.onNodeWithText("960 kcal", substring = true).assertExists()
        rule.onNodeWithText("Σολομός με λαχανικά").assertExists()
        rule.onNodeWithText(s(R.string.day_yesterday)).assertExists()
        rule.onNodeWithText("Σολομός με λαχανικά").performClick()
        rule.waitUntil(10_000) { seen(s(R.string.nufo_score)) || seen("kcal") && !seen(s(R.string.history_tab_meals)) }
    }

    @Test fun undo_brings_a_swiped_meal_back_while_other_meals_stay() {
        // Arrange: a list with several meals, so the list itself stays on screen while one row goes.
        seedSampleDiary()
        ActivityScenario.launch(MainActivity::class.java)
        rule.waitUntil(10_000) { seen(s(R.string.welcome_start)) || seen(s(R.string.home_scan)) }
        if (seen(s(R.string.welcome_start))) rule.onNodeWithText(s(R.string.welcome_start)).performClick()
        rule.waitUntil(10_000) { seen(s(R.string.nav_history)) }
        rule.onAllNodesWithText(s(R.string.nav_history)).onFirst().performClick()
        rule.waitUntil(10_000) { seen(s(R.string.history_tab_meals)) }
        rule.onNodeWithText(s(R.string.history_tab_meals)).performClick()
        rule.waitUntil(10_000) { seen("Ομελέτα με λουκάνικο") }

        // Act: a real swipe (the swipe state is what used to come back "dismissed"), then Undo.
        rule.onNodeWithText("Ομελέτα με λουκάνικο").performTouchInput { swipeLeft() }
        rule.waitUntil(5_000) { seen(s(R.string.undo)) }
        rule.onNodeWithText(s(R.string.undo)).performClick()

        // Assert: it is back and stays back (it used to remove itself again a moment later).
        rule.waitUntil(5_000) { seen("Ομελέτα με λουκάνικο") }
        Thread.sleep(2_000)
        rule.waitForIdle()
        rule.onNodeWithText("Ομελέτα με λουκάνικο").assertExists()
        rule.onNodeWithText("960 kcal", substring = true).assertExists()
    }
}
