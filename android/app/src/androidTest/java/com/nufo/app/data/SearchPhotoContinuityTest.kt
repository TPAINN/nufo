package com.nufo.app.data

import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration over live Open Food Facts + USDA (needs network): for 30 different searches, the photo a result row
 * shows must be the photo its product page shows. Same URL means the page draws the already-decoded thumbnail from
 * the first frame; a different URL (search index vs live API revision) or none made the photo load late or vanish.
 */
@RunWith(AndroidJUnit4::class)
class SearchPhotoContinuityTest {
    private lateinit var db: NufoDatabase
    private lateinit var repo: FoodRepository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, NufoDatabase::class.java).build()
        repo = FoodRepository(FoodApi(), db.history(), db.cache(), DishTable(ctx),
            FoodTable { ctx.assets.open("food/fndds.json").bufferedReader().use { it.readText() } })
    }

    @After fun tearDown() = db.close()

    private val queries = listOf(
        "fage", "δέλτα γάλα", "παπαδοπούλου", "ion σοκολάτα", "κρι κρι", "misko", "nutella", "coca cola", "μύθος μπύρα",
        "dark chocolate", "παυλίδης", "lacta", "μεβγάλ", "όλυμπος γιαούρτι", "φέτα", "ελαιόλαδο", "barilla", "kellogg's",
        "pringles", "lay's", "chipita", "7days", "αλλατίνη", "νουνού", "jumbo", "μπισκότα", "χυμός πορτοκάλι",
        "chiken", "kotopoulo", "giaourti", "tahini", "μέλι", "ζυμαρικά", "corn flakes", "γάλα εβαπορέ", "μπάρα δημητριακών",
        "πατατάκια", "σοκολάτα υγείας", "τυρί gouda", "παξιμάδια", "ρύζι", "σάλτσα ντομάτας",
    )

    @Test fun every_result_photo_reaches_its_product_page() = runBlocking {
        // Arrange
        val problems = mutableListOf<String>()
        var opened = 0
        for (q in queries) {
            if (opened == 30) break
            kotlinx.coroutines.delay(1_500) // a person's pace; Open Food Facts allows ~100 product reads a minute per IP
            val search = repo.search(q, SearchFilters(), "el")
            val hit = search.getOrNull()?.hits?.firstOrNull { it.imageUrl != null }
            if (hit == null) { Log.i("PhotoContinuity", "$q -> skipped: ${search.exceptionOrNull()?.message ?: "no result with a picture"}"); continue }
            // Act
            val lookup = repo.product(hit, "el")
            val product = (lookup as? Lookup.Found)?.product
            if (product == null) { Log.i("PhotoContinuity", "$q -> skipped: lookup $lookup"); continue }
            opened++
            val pagePhoto = product.photoUrl
            val verdict = when {
                pagePhoto == null -> "NO PHOTO on page"
                // The search index lacked a photo but the live product has one: the real photo wins, and the
                // generic picture the row showed stays underneath while it loads.
                hit.illustrative && product.imageUrl != null && product.illustrationUrl == hit.imageUrl -> "ok (own photo over illustration)"
                pagePhoto != hit.imageUrl -> "DIFFERENT photo (list ${hit.imageUrl} vs page $pagePhoto)"
                else -> "ok${if (product.imageUrl == null) " (illustrative)" else ""}"
            }
            Log.i("PhotoContinuity", "$q -> ${hit.name}: $verdict")
            if (!verdict.startsWith("ok")) problems += "$q: $verdict"
        }
        // Assert
        Log.i("PhotoContinuity", "opened $opened, problems ${problems.size}")
        assertTrue("opened only $opened products", opened == 30)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
