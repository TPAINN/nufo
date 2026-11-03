package com.nufo.app.bench

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nufo.app.NufoApp
import com.nufo.app.R
import com.nufo.app.data.MealParser
import com.nufo.app.data.Nutrition
import com.nufo.app.data.Product
import com.nufo.app.data.RecipePart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Seeds the same sample week as MealDiaryFlowTest into the release-like build, for recordings. Not in release. */
class SeedDiaryReceiver : BroadcastReceiver() {
    private class Plate(val name: String, val photo: String, val daysAgo: Long, val h: Int, val m: Int, val grams: Int, val kcal100: Double, vararg val foods: String)

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

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as NufoApp
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
            val photos = File(app.filesDir, "photos").apply { mkdirs() }
            for (p in week) {
                val time = LocalDate.now().minusDays(p.daysAgo).atTime(LocalTime.of(p.h, p.m)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val file = File(photos, "$time.jpg")
                app.assets.open("diary/${p.photo}.jpg").use { input -> file.outputStream().use { input.copyTo(it) } }
                app.repository.save(Product(
                    barcode = null, name = p.name, brand = app.getString(R.string.meal_brand), quantity = null, servingSize = "${p.grams} g",
                    servingGrams = p.grams.toDouble(), imageUrl = android.net.Uri.fromFile(file).toString(),
                    ingredientsText = p.foods.joinToString(), nutritionPer100g = Nutrition(calories = p.kcal100, protein = p.kcal100 * 0.045, carbs = p.kcal100 * 0.1, fat = p.kcal100 * 0.04, saturatedFat = p.kcal100 * 0.012, sugar = p.kcal100 * 0.02, fiber = 2.0, salt = 0.5), nutritionPerServing = null,
                    nutriscoreGrade = null, novaGroup = null, ecoscoreGrade = null, nufoScore = 0, source = MealParser.SOURCE,
                    lastUpdated = time, isDish = true, mealId = time, recipe = p.foods.map { RecipePart(it, 100.0 / p.foods.size) },
                ))
            }
            } finally { done.finish() }
        }
    }
}
