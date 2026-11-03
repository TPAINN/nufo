package com.nufo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class MealDiaryTest {
    private val zone = ZoneId.of("Europe/Athens")
    private val today = LocalDate.of(2026, 11, 3) // a Tuesday

    private fun at(date: LocalDate, h: Int, m: Int = 0) = LocalDateTime.of(date, java.time.LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    private fun product(
        name: String, time: Long, kcalPer100: Double? = 200.0, grams: Double? = 300.0,
        isDish: Boolean = true, photo: String? = "file:///data/photos/$time.jpg", mealId: Long? = time, barcode: String? = null,
    ) = Product(
        barcode = barcode, name = name, brand = null, quantity = null, servingSize = null, servingGrams = grams, imageUrl = photo,
        ingredientsText = null, nutritionPer100g = Nutrition(calories = kcalPer100), nutritionPerServing = null,
        nutriscoreGrade = null, novaGroup = null, ecoscoreGrade = null, nufoScore = 0, source = MealParser.SOURCE,
        lastUpdated = time, isDish = isDish, mealId = mealId,
    )

    @Test fun `only photographed plates are meals`() {
        // Arrange
        val plate = product("Σαλάτα", at(today, 13))
        val oldPlate = product("Μουσακάς", at(today, 20), mealId = null) // saved before meal ids existed
        val scanned = product("Γιαούρτι", at(today, 9), isDish = false, photo = "https://images.openfoodfacts.org/x.jpg", mealId = null, barcode = "520")
        val dishWithoutPhoto = product("Φασολάδα", at(today, 14), photo = null, mealId = null)
        // Act + Assert
        assertTrue(plate.isMeal)
        assertTrue(oldPlate.isMeal)
        assertFalse(scanned.isMeal)
        assertFalse(dishWithoutPhoto.isMeal)
    }

    @Test fun `two plates with the same name are both kept`() {
        val monday = product("Χωριάτικη", at(today.minusDays(1), 14))
        val tuesday = product("Χωριάτικη", at(today, 14))
        assertNotEquals(monday.key, tuesday.key)
    }

    @Test fun `products saved before meal ids keep their old key`() {
        val old = product("Μουσακάς", 1000, mealId = null)
        assertEquals("${MealParser.SOURCE}:Μουσακάς", old.key)
    }

    @Test fun `a meal's calories are for the whole plate`() {
        assertEquals(600, product("Πιάτο", 1, kcalPer100 = 200.0, grams = 300.0).mealKcal)
        assertEquals(null, product("Πιάτο", 1, kcalPer100 = null).mealKcal)
    }

    @Test fun `meals are grouped by day, newest day and newest meal first`() {
        // Arrange
        val breakfast = product("Ομελέτα", at(today, 8, 40), kcalPer100 = 100.0, grams = 410.0)
        val lunch = product("Σολομός", at(today, 13, 20), kcalPer100 = 100.0, grams = 550.0)
        val yesterday = product("Σπαγγέτι", at(today.minusDays(1), 20, 45), kcalPer100 = 100.0, grams = 600.0)
        val scanned = product("Γιαούρτι", at(today, 10), isDish = false, photo = null, mealId = null, barcode = "520")
        // Act
        val days = mealDays(listOf(breakfast, scanned, yesterday, lunch), zone)
        // Assert
        assertEquals(listOf(today, today.minusDays(1)), days.map { it.date })
        assertEquals(listOf("Σολομός", "Ομελέτα"), days[0].meals.map { it.name })
        assertEquals(960, days[0].kcal)
        assertEquals(600, days[1].kcal)
    }

    @Test fun `a day after midnight belongs to the next day in the phone's time zone`() {
        val lateNight = product("Πίτσα", at(today.minusDays(1), 23, 50))
        val afterMidnight = product("Τοστ", at(today, 0, 10))
        assertEquals(listOf(today, today.minusDays(1)), mealDays(listOf(lateNight, afterMidnight), zone).map { it.date })
    }

    @Test fun `days are named the way people say them`() {
        assertEquals(DayName.Today, dayName(today, today))
        assertEquals(DayName.Yesterday, dayName(today.minusDays(1), today))
        assertEquals(DayName.Weekday, dayName(today.minusDays(6), today))
        assertEquals(DayName.Date, dayName(today.minusDays(7), today))
    }

    @Test fun `no meals gives no days`() {
        assertTrue(mealDays(emptyList(), zone).isEmpty())
    }
}
