package com.nufo.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Integration: the real Room schema and SQL, in memory. */
@RunWith(AndroidJUnit4::class)
class HistoryDaoTest {
    private lateinit var db: NufoDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NufoDatabase::class.java).build()
    }

    @After fun close() = db.close()

    @Test fun history_lists_newest_first() = runBlocking {
        // Arrange
        db.history().upsert(HistoryEntity("a", "{}", savedAt = 1))
        db.history().upsert(HistoryEntity("b", "{}", savedAt = 2))
        // Act
        val keys = db.history().all().first().map { it.key }
        // Assert
        assertEquals(listOf("b", "a"), keys)
    }

    @Test fun saving_again_replaces_instead_of_duplicating() = runBlocking {
        db.history().upsert(HistoryEntity("a", "{\"v\":1}", savedAt = 1))
        db.history().upsert(HistoryEntity("a", "{\"v\":2}", savedAt = 3))
        val all = db.history().snapshot()
        assertEquals(1, all.size)
        assertEquals("{\"v\":2}", all.single().json)
    }

    @Test fun delete_and_clear_remove_rows() = runBlocking {
        db.history().upsert(HistoryEntity("a", "{}", savedAt = 1))
        db.history().upsert(HistoryEntity("b", "{}", savedAt = 2))
        db.history().delete("a")
        assertNull(db.history().get("a"))
        db.history().clear()
        assertEquals(0, db.history().snapshot().size)
    }

    @Test fun offline_cache_returns_the_latest_copy() = runBlocking {
        db.cache().upsert(CachedProduct("123", "{\"old\":true}", fetchedAt = 1))
        db.cache().upsert(CachedProduct("123", "{\"old\":false}", fetchedAt = 2))
        assertEquals("{\"old\":false}", db.cache().get("123")?.json)
        assertEquals(1, db.cache().recent().size)
    }
}
