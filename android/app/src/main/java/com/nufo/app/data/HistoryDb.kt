package com.nufo.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** The full product is stored as JSON so history renders offline exactly as it was scanned. */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val key: String,
    val json: String,
    val savedAt: Long,
)

/**
 * Every product fetched from the network, so a later scan still works offline. Separate from
 * history, which only holds what the user chose to keep.
 */
@Entity(tableName = "product_cache")
data class CachedProduct(
    @PrimaryKey val barcode: String,
    val json: String,
    val fetchedAt: Long,
)

@Dao
interface CacheDao {
    @Query("SELECT * FROM product_cache WHERE barcode = :barcode")
    suspend fun get(barcode: String): CachedProduct?

    @Query("SELECT * FROM product_cache ORDER BY fetchedAt DESC LIMIT 500")
    suspend fun recent(): List<CachedProduct>

    @Upsert
    suspend fun upsert(p: CachedProduct)

    @Query("DELETE FROM product_cache")
    suspend fun clear()
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY savedAt DESC")
    fun all(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE `key` = :key")
    suspend fun get(key: String): HistoryEntity?

    @Query("SELECT * FROM history")
    suspend fun snapshot(): List<HistoryEntity>

    @Upsert
    suspend fun upsert(entity: HistoryEntity)

    @Query("DELETE FROM history WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Database(entities = [HistoryEntity::class, CachedProduct::class], version = 2, exportSchema = false)
abstract class NufoDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao
    abstract fun cache(): CacheDao

    companion object {
        /** v2 adds the offline product cache; history is kept. */
        private val V1_TO_V2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `product_cache` (`barcode` TEXT NOT NULL, `json` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`barcode`))")
            }
        }

        fun create(context: Context) =
            Room.databaseBuilder(context, NufoDatabase::class.java, "nufo.db").addMigrations(V1_TO_V2).build()
    }
}