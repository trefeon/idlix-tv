package com.idlix.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * Offline-only tests for [SubdlRepository] key handling.
 *
 * The live SubDL search path is only exercised when android.util.Log is
 * mockable; otherwise it is skipped (missing dep: Robolectric, not in the
 * version catalog).
 */
class SubdlRepositoryTest {

    private fun apiKeyField() =
        SubdlRepository::class.java.getDeclaredField("currentApiKey").apply {
            isAccessible = true
        }

    private fun resetApiKey() {
        apiKeyField().set(SubdlRepository, null)
    }

    private fun androidLogAvailable(): Boolean {
        return try {
            android.util.Log.d("probe", "probe")
            true
        } catch (_: Throwable) {
            false
        }
    }

    @After
    fun restoreSingleton() {
        resetApiKey()
    }

    @Test
    fun prefsFileIsSplitFromWatchHistory() {
        val prefsName = SubdlRepository::class.java
            .getDeclaredField("PREFS_NAME").apply { isAccessible = true }
            .get(null) as String
        assertEquals("subdl_config", prefsName)
        assertNotEquals("idlix_tv_watch_history", prefsName)
    }

    @Test
    fun baseUrlIsOfficialSubdlApi() {
        val baseUrl = SubdlRepository::class.java
            .getDeclaredField("BASE_URL").apply { isAccessible = true }
            .get(null) as String
        assertTrue(baseUrl.startsWith("https://api.subdl.com/"))
    }

    @Test
    fun apiKeyRoundtripIsTrimmed() {
        SubdlRepository.setApiKey("  key123  ")
        assertEquals("key123", SubdlRepository.getApiKey())
    }

    @Test
    fun blankApiKeyRoundtrip() {
        SubdlRepository.setApiKey("   ")
        assertEquals("", SubdlRepository.getApiKey())
    }

    @Test
    fun defaultApiKeyMatchesBuildConfig() {
        // Deterministic in any env: with no override the repo exposes the
        // build-provided key ("" when local.properties/CI provide none).
        resetApiKey()
        assertEquals(com.idlix.data.BuildConfig.SUBDL_API_KEY, SubdlRepository.getApiKey())
    }

    @Test
    fun blankKeySearchFailsClosedWithoutNetwork() {
        Assume.assumeTrue(
            "Needs android.util.Log mock (Robolectric, not in catalog).",
            androidLogAvailable()
        )
        SubdlRepository.setApiKey("")
        val result = runBlocking {
            SubdlRepository.searchAllSubtitles(title = "Fuori")
        }
        assertTrue(result.isEmpty())
    }
}
