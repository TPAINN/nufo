package com.nufo.app.scan

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Retail product barcodes only; QR codes etc. are ignored. */
val PRODUCT_BARCODES: BarcodeScannerOptions = BarcodeScannerOptions.Builder()
    .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
    .build()

/** [key] is the classifier's own dish name (English) when [label] is a translation of it. */
data class Guess(val label: String, val confidence: Float, val key: String? = null)

/**
 * How sure the dish guess is, calibrated on 62 real dish photos (android/scripts/score_food.py):
 * at 0.5 or more the first guess was right every time; under 0.15 it was usually wrong.
 */
object DishConfidence {
    const val CONFIDENT = 0.5f
    const val PLAUSIBLE = 0.15f
}

/** Greek names for ML Kit's English food labels, so chips and searches read naturally in Greek. */
private val GREEK_LABELS = mapOf(
    "Fruit" to "Φρούτα", "Vegetable" to "Λαχανικά", "Bread" to "Ψωμί", "Cake" to "Κέικ", "Pizza" to "Πίτσα",
    "Cookie" to "Μπισκότα", "Juice" to "Χυμός", "Cheese" to "Τυρί", "Pasta" to "Ζυμαρικά", "Egg" to "Αυγό",
    "Meat" to "Κρέας", "Soup" to "Σούπα", "Sushi" to "Σούσι", "Rice" to "Ρύζι", "Salad" to "Σαλάτα",
    "Sandwich" to "Σάντουιτς", "Hot dog" to "Χοτ ντογκ", "Hamburger" to "Χάμπουργκερ", "Dessert" to "Γλυκό",
    "Coffee" to "Καφές", "Tea" to "Τσάι", "Wine" to "Κρασί", "Beer" to "Μπύρα", "Drink" to "Ρόφημα",
    "Candy" to "Καραμέλες", "Chocolate" to "Σοκολάτα", "Ice cream" to "Παγωτό", "Pie" to "Πίτα", "Muffin" to "Μάφιν",
    "Doughnut" to "Ντόνατ", "Pancake" to "Τηγανίτα", "Waffle" to "Βάφλα", "Cereal" to "Δημητριακά", "Seafood" to "Θαλασσινά",
    "Fish" to "Ψάρι", "Chicken" to "Κοτόπουλο", "Noodle" to "Νουντλς", "Baked goods" to "Αρτοσκευάσματα", "Bun" to "Ψωμάκι",
    "Pastry" to "Γλύκισμα", "Cupcake" to "Κεκάκι", "Food" to "Φαγητό",
)

/** What on-device analysis found in a still photo. Nothing leaves the phone here. */
data class PhotoAnalysis(
    val barcode: String?,
    /** Specific dishes from the food classifier ("Moussaka"), most likely first. */
    val dishes: List<Guess>,
    /** Generic food categories from ML Kit ("Pasta"), used when no dish is recognised. */
    val labels: List<Guess>,
    /** Product-name-like lines read from the packaging, if any. */
    val textLines: List<String>,
)

// ML Kit's base labeler knows ~400 generic classes ("Handbag", "Toy"...). Only these can name a food;
// the generic "Food" label sorts last because it is never a useful search on its own.
private val FOOD_LABELS = setOf(
    "Fruit", "Vegetable", "Bread", "Cake", "Pizza", "Cookie", "Juice", "Cheese", "Pasta", "Egg", "Meat", "Soup",
    "Sushi", "Rice", "Salad", "Sandwich", "Hot dog", "Hamburger", "Dessert", "Coffee", "Tea", "Wine", "Beer",
    "Drink", "Candy", "Chocolate", "Ice cream", "Pie", "Muffin", "Doughnut", "Pancake", "Waffle", "Cereal",
    "Seafood", "Fish", "Chicken", "Noodle", "Baked goods", "Bun", "Pastry", "Cupcake", "Food",
)
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

object PhotoAnalyzer {
    suspend fun analyze(context: Context, bitmap: Bitmap, lang: String = "en"): PhotoAnalysis {
        val image = InputImage.fromBitmap(bitmap, 0)
        val dishes = runCatching { FoodClassifier.get(context).classify(bitmap) }.getOrDefault(emptyList())
            .map { it.copy(label = DishNames.display(it.label, lang), key = it.label) }
        val barcode = runCatching { BarcodeScanning.getClient(PRODUCT_BARCODES).process(image).await() }
            .getOrNull()?.firstNotNullOfOrNull { it.rawValue }
        val labels = runCatching {
            ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.55f).build()).process(image).await()
        }.getOrDefault(emptyList())
            .filter { it.text in FOOD_LABELS }
            .sortedBy { it.text == "Food" }
            .map { Guess(if (lang == "el") GREEK_LABELS[it.text] ?: it.text else it.text, it.confidence) }
            .take(6)
        val text = runCatching { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).await() }.getOrNull()
        val lines = text?.textBlocks.orEmpty()
            .sortedByDescending { b -> b.boundingBox?.height() ?: 0 } // biggest type first: usually the product name
            .flatMap { it.lines }
            .map { line -> line.text.replace(Regex("[^\\p{L} '&-]"), "").trim() } // OCR noise: digits, symbols
            .filter { it.length in 3..40 && it.count(Char::isLetter) >= 3 }
            .distinct()
            .take(3)
        return PhotoAnalysis(barcode, dishes, labels, lines)
    }
}