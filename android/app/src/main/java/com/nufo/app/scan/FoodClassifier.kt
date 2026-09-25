package com.nufo.app.scan

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * On-device dish recognition with Google's AIY food classifier (MobileNet V1, 2,023 dishes including
 * moussaka, souvlaki, gyro, pastitsio, spanakopita; Apache 2.0), run with LiteRT. Bundled in the APK so it
 * works offline and without Google Play services. Input: 192 x 192 RGB uint8. Output: uint8 probabilities.
 */
class FoodClassifier private constructor(private val interpreter: Interpreter, private val labels: List<String>) {

    /**
     * The [top] most likely dishes, most likely first. Averages three views of the photo (centre square,
     * a tighter centre crop, and the whole frame), because a single crop misses plates shot off-centre
     * or at an angle. Background and internal ids ("/g/...") are never returned.
     */
    @Synchronized
    fun classify(bitmap: Bitmap, top: Int = 5): List<Guess> {
        val views = listOf(crop(bitmap, 1f), crop(bitmap, 0.7f), Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true))
        val sum = FloatArray(labels.size)
        for (view in views) run(view).forEachIndexed { i, p -> sum[i] += p }
        // Variants of one dish share its probability, so "Pizza" is not split across eight regional styles.
        val families = HashMap<String, Float>()
        sum.forEachIndexed { i, p ->
            val label = labels[i]
            if (i != 0 && !label.startsWith("/")) families.merge(FAMILY[label] ?: label, p / views.size, Float::plus)
        }
        return families.entries.sortedByDescending { it.value }.take(top).map { Guess(it.key, it.value) }
    }

    /** Probabilities for one 192 x 192 view. */
    private fun run(view: Bitmap): FloatArray {
        val input = ByteBuffer.allocateDirect(SIZE * SIZE * 3).order(ByteOrder.nativeOrder())
        val pixels = IntArray(SIZE * SIZE)
        view.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        for (p in pixels) {
            input.put((p shr 16 and 0xFF).toByte()); input.put((p shr 8 and 0xFF).toByte()); input.put((p and 0xFF).toByte())
        }
        input.rewind()
        val output = Array(1) { ByteArray(labels.size) }
        interpreter.run(input, output)
        return FloatArray(labels.size) { (output[0][it].toInt() and 0xFF) / 256f }
    }

    /** Centre square covering [fraction] of the shorter side, scaled to the model's input. */
    private fun crop(src: Bitmap, fraction: Float): Bitmap {
        val side = (minOf(src.width, src.height) * fraction).toInt()
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        return Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
    }

    companion object {
        private const val SIZE = 192

        /**
         * The model knows regional variants but not the everyday dish ("Neapolitan pizza", no "Pizza"), and
         * some dishes under a neighbour's name. These fold into the name people search for.
         */
        private val FAMILY = buildMap {
            listOf("Chicago-style pizza", "St. Louis-style pizza", "New York-style pizza", "New Haven-style pizza",
                "California-style pizza", "Detroit-style pizza", "Quad City-style pizza", "Neapolitan pizza").forEach { put(it, "Pizza") }
            listOf("Delmonico steak", "Swiss steak", "Steak Diane", "Steak de Burgo").forEach { put(it, "Steak") }
            listOf("Cheese fries", "Home fries", "Carne asada fries", "Potato wedges").forEach { put(it, "French fries") }
            listOf("Stuffed peppers", "Punjena paprika").forEach { put(it, "Stuffed peppers") }
            put("Sarma", "Dolma")
            put("Cacık", "Tzatziki")
        }

        @Volatile private var instance: FoodClassifier? = null

        fun get(context: Context): FoodClassifier = instance ?: synchronized(this) {
            instance ?: load(context.applicationContext).also { instance = it }
        }

        private fun load(context: Context): FoodClassifier {
            val model = context.assets.openFd("food/food_v1.tflite").use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use { it.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength) }
            }
            val labels = context.assets.open("food/labels_en.txt").bufferedReader().readLines()
            return FoodClassifier(Interpreter(model, Interpreter.Options().setNumThreads(2)), labels)
        }
    }
}