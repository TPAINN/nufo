package com.nufo.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * A plate the user photographed (their own photo, stored on the phone), as opposed to a product they scanned or
 * searched. Plates saved before meal ids existed are recognised by that photo.
 */
val Product.isMeal: Boolean get() = mealId != null || (isDish && imageUrl?.startsWith("file:") == true)

/** Calories of the whole plate as photographed, or null when the energy is unknown. */
val Product.mealKcal: Int? get() = nutritionPer100g.calories?.let { c -> servingGrams?.let { (c * it / 100).roundToInt() } }

data class MealDay(val date: LocalDate, val meals: List<Product>, val kcal: Int)

/** The user's plates grouped by the day they were eaten (phone's time zone), newest day and newest meal first. */
fun mealDays(history: List<Product>, zone: ZoneId = ZoneId.systemDefault()): List<MealDay> =
    history.filter { it.isMeal }
        .sortedByDescending { it.lastUpdated }
        .groupBy { Instant.ofEpochMilli(it.lastUpdated).atZone(zone).toLocalDate() }
        .map { (date, meals) -> MealDay(date, meals, meals.sumOf { it.mealKcal ?: 0 }) }

/** How a day is named: people say "yesterday" and "Sunday", and only reach for a date further back. */
enum class DayName { Today, Yesterday, Weekday, Date }

fun dayName(date: LocalDate, today: LocalDate): DayName = when (date) {
    today -> DayName.Today
    today.minusDays(1) -> DayName.Yesterday
    else -> if (date.isAfter(today.minusDays(7))) DayName.Weekday else DayName.Date
}
