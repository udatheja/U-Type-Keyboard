package com.udara.Utype.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "clipboard_items")
data class ClipboardItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val encryptedText: String,
    val iv: String,
    val contentHash: String,
    val createdAt: Long,
    val isPinned: Boolean = false,
    val unpinnedAt: Long? = null,
)

@Entity(
    tableName = "learned_words",
    primaryKeys = ["word", "language"],
    indices = [Index(value = ["language", "frequency"])]
)
data class LearnedWordEntity(
    val word: String,
    val language: String, // "en" or "si"
    val frequency: Int = 1,
    val lastUsedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "text_shortcuts")
data class TextShortcutEntity(
    @PrimaryKey val shortCode: String,
    val expansion: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "recent_emojis")
data class RecentEmojiEntity(
    @PrimaryKey val emoji: String,
    val lastUsedAt: Long = System.currentTimeMillis(),
)

@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_items ORDER BY isPinned DESC, createdAt DESC")
    fun observeAll(): Flow<List<ClipboardItemEntity>>

    @Query("SELECT * FROM clipboard_items ORDER BY isPinned DESC, createdAt DESC")
    suspend fun getAll(): List<ClipboardItemEntity>

    @Query("SELECT * FROM clipboard_items WHERE isPinned = 1 ORDER BY createdAt DESC")
    suspend fun getPinned(): List<ClipboardItemEntity>

    @Query("SELECT * FROM clipboard_items WHERE contentHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): ClipboardItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ClipboardItemEntity): Long

    @Query("UPDATE clipboard_items SET isPinned = 1, unpinnedAt = NULL WHERE id = :id")
    suspend fun pin(id: Long)

    @Query("UPDATE clipboard_items SET isPinned = 0, unpinnedAt = :unpinnedAt WHERE id = :id")
    suspend fun unpin(id: Long, unpinnedAt: Long)

    @Query("DELETE FROM clipboard_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM clipboard_items WHERE isPinned = 0")
    suspend fun clearAllUnpinned()

    /**
     * Deletes every UNPINNED item whose effective start timestamp (unpinnedAt if unpinned,
     * otherwise createdAt) is older than or equal to [cutoffTimestamp].
     */
    @Query(
        "DELETE FROM clipboard_items WHERE isPinned = 0 AND COALESCE(unpinnedAt, createdAt) <= :cutoffTimestamp"
    )
    suspend fun deleteExpiredUnpinned(cutoffTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM clipboard_items")
    suspend fun countAll(): Int

    @Query(
        "SELECT id FROM clipboard_items WHERE isPinned = 0 ORDER BY COALESCE(unpinnedAt, createdAt) ASC LIMIT :limit"
    )
    suspend fun oldestUnpinnedIds(limit: Int): List<Long>

    @Query("DELETE FROM clipboard_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface LearnedWordDao {
    @Query("SELECT * FROM learned_words WHERE language = :language ORDER BY frequency DESC LIMIT 500")
    suspend fun getTopForLanguage(language: String): List<LearnedWordEntity>

    @Query("SELECT * FROM learned_words ORDER BY frequency DESC")
    suspend fun getAll(): List<LearnedWordEntity>

    @Query("SELECT COUNT(*) FROM learned_words")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM learned_words WHERE word = :word AND language = :language LIMIT 1")
    suspend fun find(word: String, language: String): LearnedWordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LearnedWordEntity)

    @Query("DELETE FROM learned_words")
    suspend fun clearAll()
}

@Dao
interface TextShortcutDao {
    @Query("SELECT * FROM text_shortcuts ORDER BY shortCode ASC")
    fun observeAll(): Flow<List<TextShortcutEntity>>

    @Query("SELECT * FROM text_shortcuts ORDER BY shortCode ASC")
    suspend fun getAll(): List<TextShortcutEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(shortcut: TextShortcutEntity)

    @Query("DELETE FROM text_shortcuts WHERE shortCode = :shortCode")
    suspend fun delete(shortCode: String)
}

@Dao
interface RecentEmojiDao {
    @Query("SELECT emoji FROM recent_emojis ORDER BY lastUsedAt DESC LIMIT 36")
    suspend fun getRecent(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RecentEmojiEntity)

    @Query("DELETE FROM recent_emojis")
    suspend fun clearAll()
}

@Database(
    entities = [
        ClipboardItemEntity::class,
        LearnedWordEntity::class,
        TextShortcutEntity::class,
        RecentEmojiEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class UTypeDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao
    abstract fun learnedWordDao(): LearnedWordDao
    abstract fun textShortcutDao(): TextShortcutDao
    abstract fun recentEmojiDao(): RecentEmojiDao

    companion object {
        @Volatile
        private var INSTANCE: UTypeDatabase? = null

        fun getInstance(context: Context): UTypeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UTypeDatabase::class.java,
                    "utype_offline.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
