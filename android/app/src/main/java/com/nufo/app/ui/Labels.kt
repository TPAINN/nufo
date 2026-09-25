package com.nufo.app.ui

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.nufo.app.R
import com.nufo.app.data.Diet
import com.nufo.app.data.DietNote
import com.nufo.app.data.InsightKind
import com.nufo.app.data.Level
import com.nufo.app.data.Product
import com.nufo.app.data.Reason
import com.nufo.app.data.Verdict
import com.nufo.app.ui.components.fmt
import java.util.Locale

/** "el" or "en": the language Nufo asks the food databases for. */
fun dataLang(locale: Locale = Locale.getDefault()) = if (locale.language == "el") "el" else "en"

/** Data language that follows the app's current configuration (updates when the per-app language changes). */
@Composable
@ReadOnlyComposable
fun currentDataLang(): String = dataLang(LocalConfiguration.current.locales[0])

@StringRes fun Reason.label() = when (this) {
    Reason.NutriB -> R.string.reason_nutri_b; Reason.NutriC -> R.string.reason_nutri_c
    Reason.NutriD -> R.string.reason_nutri_d; Reason.NutriE -> R.string.reason_nutri_e
    Reason.Nova2 -> R.string.reason_nova_2; Reason.Nova3 -> R.string.reason_nova_3; Reason.Nova4 -> R.string.reason_nova_4
    Reason.HighProtein -> R.string.reason_protein; Reason.HighFiber -> R.string.reason_fiber
    Reason.HighSugar -> R.string.reason_sugar; Reason.HighSalt -> R.string.reason_salt; Reason.HighSatFat -> R.string.reason_satfat
}

@StringRes fun InsightKind.label() = when (this) {
    InsightKind.HighProtein -> R.string.insight_high_protein; InsightKind.GoodFiber -> R.string.insight_good_fiber
    InsightKind.LowFiber -> R.string.insight_low_fiber; InsightKind.HighSugar -> R.string.insight_high_sugar
    InsightKind.LowSugar -> R.string.insight_low_sugar; InsightKind.HighSodium -> R.string.insight_high_sodium
    InsightKind.LowSodium -> R.string.insight_low_sodium; InsightKind.HighSatFat -> R.string.insight_high_satfat
    InsightKind.UltraProcessed -> R.string.insight_ultra; InsightKind.Unprocessed -> R.string.insight_unprocessed
}

@StringRes fun Verdict.label() = when (this) {
    Verdict.Excellent -> R.string.verdict_excellent; Verdict.Good -> R.string.verdict_good; Verdict.Fair -> R.string.verdict_fair
    Verdict.Poor -> R.string.verdict_poor; Verdict.VeryPoor -> R.string.verdict_very_poor
}

@StringRes fun Level.label() = when (this) { Level.Low -> R.string.level_low; Level.Medium -> R.string.level_medium; Level.High -> R.string.level_high }

@StringRes fun Diet.label() = when (this) {
    Diet.None -> R.string.diet_none; Diet.Vegetarian -> R.string.diet_vegetarian; Diet.Vegan -> R.string.diet_vegan
    Diet.Keto -> R.string.diet_keto; Diet.LowSodium -> R.string.diet_low_sodium_name
}

@StringRes fun novaLabel(group: Int) = when (group) { 1 -> R.string.nova_1; 2 -> R.string.nova_2; 3 -> R.string.nova_3; else -> R.string.nova_4 }

@StringRes fun categoryLabel(tag: String) = when (tag) {
    "en:dairies" -> R.string.cat_dairies; "en:cheeses" -> R.string.cat_cheeses; "en:yogurts" -> R.string.cat_yogurts
    "en:olive-oils" -> R.string.cat_olive_oils; "en:beverages" -> R.string.cat_beverages; "en:snacks" -> R.string.cat_snacks
    "en:breakfasts" -> R.string.cat_breakfasts; "en:cereals-and-potatoes" -> R.string.cat_cereals
    "en:fruits-and-vegetables-based-foods" -> R.string.cat_fruit_veg; "en:meats-and-their-products" -> R.string.cat_meat
    else -> R.string.cat_desserts
}

@Composable
fun dietNoteText(note: Pair<DietNote, Double?>): String = when (note.first) {
    DietNote.NotVegan -> stringResource(R.string.diet_not_vegan)
    DietNote.NotVegetarian -> stringResource(R.string.diet_not_vegetarian)
    DietNote.HighCarbsKeto -> stringResource(R.string.diet_note_keto, fmt(note.second ?: 0.0))
    DietNote.NotLowSodium -> stringResource(R.string.diet_note_low_sodium, fmt(note.second ?: 0.0))
}

@Composable
fun Product.displayName(): String = name.ifBlank { stringResource(R.string.unnamed_product) }

/** Per-app language (Android 13+). Older versions follow the phone's language. */
object AppLanguage {
    val supported get() = Build.VERSION.SDK_INT >= 33

    /** "el", "en", or null when following the phone. */
    fun get(context: android.content.Context): String? {
        if (!supported) return null
        val locales = context.getSystemService(android.app.LocaleManager::class.java).applicationLocales
        return if (locales.isEmpty) null else locales[0].language
    }

    /** Applies immediately: Android recreates the activity in the new language. */
    fun set(context: android.content.Context, tag: String?) {
        if (!supported) return
        context.getSystemService(android.app.LocaleManager::class.java).applicationLocales =
            if (tag == null) android.os.LocaleList.getEmptyLocaleList() else android.os.LocaleList.forLanguageTags(tag)
    }
}