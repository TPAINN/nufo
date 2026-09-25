package com.nufo.app

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nufo.app.scan.PhotoAnalyzer
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Food photo recognition benchmark over real dish photos (androidTest/assets/food, Wikimedia Commons).
 * Logs one line per photo; scripts/score_food.py scores them against the dish in each file name.
 * Run: ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nufo.app.FoodRecognitionBenchmark
 */
@RunWith(AndroidJUnit4::class)
class FoodRecognitionBenchmark {
    @Test
    fun recognizeDishPhotos(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val files = assets.list("food").orEmpty().filter { it.endsWith(".jpg") }.sorted()
        for (file in files) {
            val bitmap = assets.open("food/$file").use(BitmapFactory::decodeStream)
            val started = System.nanoTime()
            val a = PhotoAnalyzer.analyze(instrumentation.targetContext, bitmap, "en")
            val ms = (System.nanoTime() - started) / 1_000_000
            fun List<com.nufo.app.scan.Guess>.log() = joinToString(";") { "${it.label}:${"%.2f".format(it.confidence)}" }
            Log.i(TAG, "$file|$ms|${a.dishes.log()}|${a.labels.log()}|${a.textLines.joinToString(";")}")
        }
        Log.i(TAG, "DONE ${files.size}")
    }

    private companion object { const val TAG = "NUFO_BENCH" }
}