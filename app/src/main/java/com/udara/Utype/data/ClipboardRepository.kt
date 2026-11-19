package com.udara.Utype.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ClipboardItem(
    val id: Long,
    val text: String,
    val createdAt: Long,
    val isPinned: Boolean,
    val unpinnedAt: Long?,
) {
    /** The timestamp from which the expiry countdown starts for an unpinned item. */
    val effectiveTimestamp: Long
        get() = unpinnedAt ?: createdAt
}

/**
 * Manages encrypted local clipboard history with:
 * - Automatic deletion of unpinned items after [retentionMillis] (default 2 hours).
 * - Pinned items that never expire until unpinned.
 * - Fresh countdown starting from `unpinnedAt` when a pinned item is unpinned.
 * - Maximum 50 stored items (evicting oldest unpinned items first).
 */
class ClipboardRepository(
    private val dao: ClipboardDao,
    private val crypto: ClipboardCrypto,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        const val MAX_ITEMS = 50
        const val DEFAULT_RETENTION_MILLIS = 2L * 60L * 60L * 1000L // 2 hours
    }

    fun observeItems(): Flow<List<ClipboardItem>> = dao.observeAll().map { entities ->
        entities.mapNotNull { decryptEntity(it) }
    }

    suspend fun getItems(retentionMillis: Long = DEFAULT_RETENTION_MILLIS): List<ClipboardItem> {
        cleanupExpired(retentionMillis)
        return dao.getAll().mapNotNull { decryptEntity(it) }
    }

    suspend fun getPinnedItems(): List<ClipboardItem> =
        dao.getPinned().mapNotNull { decryptEntity(it) }

    /**
     * Saves a newly copied text item (encrypting with AES-256-GCM), runs expiry cleanup,
     * deduplicates identical items, and enforces the 50-item maximum (evicting oldest unpinned first).
     */
    suspend fun saveClip(
        text: String,
        retentionMillis: Long = DEFAULT_RETENTION_MILLIS,
        isPinned: Boolean = false,
    ): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val now = clock()
        cleanupExpired(retentionMillis)

        val hash = crypto.hash(text)
        val existing = dao.findByHash(hash)
        val id = if (existing != null) {
            // Refresh timestamp while keeping existing pin status
            val updated = existing.copy(
                createdAt = now,
                isPinned = existing.isPinned || isPinned,
                unpinnedAt = if (existing.isPinned || isPinned) null else existing.unpinnedAt,
            )
            dao.upsert(updated)
        } else {
            val payload = crypto.encrypt(text)
            dao.upsert(
                ClipboardItemEntity(
                    encryptedText = payload.ciphertext,
                    iv = payload.iv,
                    contentHash = hash,
                    createdAt = now,
                    isPinned = isPinned,
                    unpinnedAt = null,
                )
            )
        }

        enforceMaxLimit()
        return id
    }

    suspend fun pinItem(id: Long) {
        dao.pin(id)
    }

    /**
     * Unpins an item and starts a fresh expiry countdown from [clock] (`unpinnedAt = now`),
     * so an item pinned for hours/days gets a full fresh retention window after unpinning.
     */
    suspend fun unpinItem(id: Long) {
        dao.unpin(id, clock())
    }

    suspend fun togglePin(item: ClipboardItem) {
        if (item.isPinned) {
            unpinItem(item.id)
        } else {
            pinItem(item.id)
        }
    }

    suspend fun deleteItem(id: Long) {
        dao.deleteById(id)
    }

    /** Clears all unpinned items while preserving every pinned item. */
    suspend fun clearAllUnpinned() {
        dao.clearAllUnpinned()
    }

    /**
     * Deletes every UNPINNED item whose expiry time has passed based on saved timestamps.
     * Pinned items are never deleted.
     */
    suspend fun cleanupExpired(retentionMillis: Long = DEFAULT_RETENTION_MILLIS): Int {
        val cutoff = clock() - retentionMillis
        return dao.deleteExpiredUnpinned(cutoff)
    }

    private suspend fun enforceMaxLimit() {
        val count = dao.countAll()
        if (count > MAX_ITEMS) {
            val overflow = count - MAX_ITEMS
            val oldest = dao.oldestUnpinnedIds(overflow)
            if (oldest.isNotEmpty()) {
                dao.deleteByIds(oldest)
            }
        }
    }

    private fun decryptEntity(entity: ClipboardItemEntity): ClipboardItem? {
        return try {
            val plain = crypto.decrypt(entity.encryptedText, entity.iv)
            ClipboardItem(
                id = entity.id,
                text = plain,
                createdAt = entity.createdAt,
                isPinned = entity.isPinned,
                unpinnedAt = entity.unpinnedAt,
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Formats the remaining lifetime for an unpinned item (e.g., "1h 20m left", "45m left", "<1m left").
     */
    fun formatTimeLeft(
        item: ClipboardItem,
        retentionMillis: Long = DEFAULT_RETENTION_MILLIS,
        nowMillis: Long = clock(),
    ): String {
        if (item.isPinned) return "Pinned"
        val remainingMs = (item.effectiveTimestamp + retentionMillis) - nowMillis
        if (remainingMs <= 0L) return "Expired"
        val totalMinutes = remainingMs / 60_000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours > 0L && minutes > 0L -> "${hours}h ${minutes}m left"
            hours > 0L -> "${hours}h left"
            minutes > 0L -> "${minutes}m left"
            else -> "<1m left"
        }
    }
}
