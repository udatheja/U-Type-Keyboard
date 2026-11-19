package com.udara.Utype.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests verifying all Clipboard Manager retention, pinning, unpinning, and clear rules:
 * 1. An unpinned item expires after 2 hours.
 * 2. A pinned item survives past 2 hours.
 * 3. An item unpinned after 5 hours is deleted 2 hours after unpinning, NOT instantly.
 * 4. "Clear all unpinned" keeps pinned items.
 * 5. Enforces max 50 items, removing oldest unpinned first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ClipboardExpiryTest {

    private lateinit var db: UTypeDatabase
    private var fakeNowMs: Long = 1_700_000_000_000L
    private lateinit var repo: ClipboardRepository

    private val oneHourMs = 60L * 60L * 1000L
    private val twoHoursMs = 2L * oneHourMs

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, UTypeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = ClipboardRepository(
            dao = db.clipboardDao(),
            crypto = PassThroughClipboardCrypto(),
            clock = { fakeNowMs },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun unpinnedItemExpiresAfterTwoHours() = runTest {
        repo.saveClip("temporary secret", retentionMillis = twoHoursMs)

        // Still present at 1h 59m
        fakeNowMs += (twoHoursMs - 60_000L)
        assertEquals(1, repo.getItems(twoHoursMs).size)

        // Expired at 2h 0m
        fakeNowMs += 60_000L
        assertEquals(0, repo.getItems(twoHoursMs).size)
    }

    @Test
    fun pinnedItemSurvivesPastTwoHours() = runTest {
        val id = repo.saveClip("keep forever", retentionMillis = twoHoursMs)!!
        repo.pinItem(id)

        // Advance 24 hours
        fakeNowMs += 24L * oneHourMs
        val items = repo.getItems(twoHoursMs)
        assertEquals(1, items.size)
        assertEquals("keep forever", items.first().text)
        assertTrue(items.first().isPinned)
    }

    @Test
    fun itemUnpinnedAfterFiveHoursGetsFreshTwoHoursBeforeDeletion() = runTest {
        val id = repo.saveClip("pinned then unpinned", retentionMillis = twoHoursMs)!!
        repo.pinItem(id)

        // Advance 5 hours while pinned -> still alive
        fakeNowMs += 5L * oneHourMs
        assertEquals(1, repo.getItems(twoHoursMs).size)

        // Unpin at T = 5h -> countdown restarts from T = 5h (unpinnedAt = 5h)
        repo.unpinItem(id)
        assertEquals("Should NOT be deleted instantly upon unpinning", 1, repo.getItems(twoHoursMs).size)

        // Advance 1h 59m after unpinning (T = 6h 59m) -> still alive
        fakeNowMs += (twoHoursMs - 60_000L)
        val remaining = repo.getItems(twoHoursMs)
        assertEquals(1, remaining.size)
        assertEquals("1m left", repo.formatTimeLeft(remaining.first(), twoHoursMs))

        // Advance 1 more minute (T = 7h 0m, i.e., 2h after unpinning) -> now deleted!
        fakeNowMs += 60_000L
        assertEquals(0, repo.getItems(twoHoursMs).size)
    }

    @Test
    fun clearAllUnpinnedKeepsPinnedItems() = runTest {
        val pinnedId = repo.saveClip("my address", retentionMillis = twoHoursMs)!!
        repo.pinItem(pinnedId)
        fakeNowMs += 1000L
        repo.saveClip("temp otp 1234", retentionMillis = twoHoursMs)
        fakeNowMs += 1000L
        repo.saveClip("temp link", retentionMillis = twoHoursMs)

        assertEquals(3, repo.getItems(twoHoursMs).size)

        repo.clearAllUnpinned()

        val afterClear = repo.getItems(twoHoursMs)
        assertEquals(1, afterClear.size)
        assertEquals("my address", afterClear.first().text)
        assertTrue(afterClear.first().isPinned)
    }

    @Test
    fun maxFiftyItemsEvictsOldestUnpinnedFirst() = runTest {
        val pinnedId = repo.saveClip("pinned #0", retentionMillis = twoHoursMs)!!
        repo.pinItem(pinnedId)

        for (i in 1..55) {
            fakeNowMs += 1000L
            repo.saveClip("clip #$i", retentionMillis = twoHoursMs)
        }

        val items = repo.getItems(twoHoursMs)
        assertEquals(50, items.size)
        assertTrue("Pinned item must never be evicted", items.any { it.text == "pinned #0" })
        assertTrue("Newest clip must be kept", items.any { it.text == "clip #55" })
        assertTrue("Oldest unpinned clip #1 should be evicted", items.none { it.text == "clip #1" })
    }

    @Test
    fun recopyingUnpinnedItemResetsFreshCountdown() = runTest {
        val id = repo.saveClip("re-copied text", retentionMillis = twoHoursMs)!!
        repo.pinItem(id)
        fakeNowMs += 3L * oneHourMs
        repo.unpinItem(id) // unpinnedAt = 3h

        // 1h 50m after unpinning (only 10m left), user copies the exact same text again
        fakeNowMs += (twoHoursMs - 10L * 60_000L)
        repo.saveClip("re-copied text", retentionMillis = twoHoursMs)

        // Advance 30 minutes (would have expired under old unpinnedAt, but MUST still survive!)
        fakeNowMs += 30L * 60_000L
        val items = repo.getItems(twoHoursMs)
        assertEquals("Re-copied unpinned clip must get a fresh 2h timer", 1, items.size)
        assertEquals("re-copied text", items.first().text)
    }
}
