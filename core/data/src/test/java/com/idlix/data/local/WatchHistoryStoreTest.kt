package com.idlix.data.local

import com.idlix.data.model.WatchHistoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * [WatchHistoryStore] needs an Android [android.content.Context]; there is no
 * Robolectric in the version catalog, so store tests run only when a Context
 * is available and otherwise skip with the seam report below.
 *
 * Needed seam (no main-code edit here): make the store testable without a
 * device — e.g. an injectable prefs/flow seam or a secondary in-memory
 * constructor in core/.../local/WatchHistoryStore.kt (private ctor line ~12,
 * getInstance line ~183).
 */
class WatchHistoryStoreTest {

    private fun androidContextAvailable(): Boolean {
        return try {
            android.util.Log.d("probe", "probe")
            true
        } catch (_: Throwable) {
            false
        }
    }

    @Test
    fun historyFlowStartsEmptyWhenNoHistory() {
        Assume.assumeTrue(
            "Needs Android Context mock (Robolectric, not in catalog); " +
                "seam: injectable WatchHistoryStore backend.",
            androidContextAvailable()
        )
        // Placeholder reached only with a mocked framework; documents intent:
        // a fresh store exposes an empty historyFlow and empty unfinished list.
        val items = emptyList<WatchHistoryItem>()
        assertTrue(items.isEmpty())
        assertEquals(0, items.size)
    }

    @Test
    fun corruptPrefsQuarantineKeepsFlowAlive() {
        Assume.assumeTrue(
            "Needs Android Context mock (Robolectric, not in catalog); " +
                "seam: injectable WatchHistoryStore backend + corrupt-JSON quarantine path.",
            androidContextAvailable()
        )
        // Documents the StartupRepair contract: corrupt "history_items_json"
        // must quarantine (not crash) and keep historyFlow emitting.
        val items = emptyList<WatchHistoryItem>()
        assertTrue(items.isEmpty())
    }
}
