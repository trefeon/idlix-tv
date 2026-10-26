package com.idlix.tv.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * Contract tests for the in-app update engine (owned by the UpdateEngine
 * slice: UpdateState.kt, UpdateChecker.kt, UpdateDownloader.kt,
 * UpdateInstaller.kt, UpdateCheckWorker.kt, UpdateSettingsRow.kt).
 *
 * Shape assertions run offline via reflection. The behavioural matrix
 * (newer/equal/malformed/offline redirect -> Available/UpToDate/Failed) runs
 * offline against UpdateChecker.kt's pure helpers; no live GitHub fetch is
 * performed here.
 */
class UpdateCheckerTest {

    private fun updateStateClass(): Class<*>? {
        return try {
            Class.forName("com.idlix.tv.update.UpdateState")
        } catch (_: Throwable) {
            null
        }
    }

    private fun variant(name: String): Class<*>? {
        return try {
            Class.forName("com.idlix.tv.update.UpdateState\$$name")
        } catch (_: Throwable) {
            null
        }
    }

    @Test
    fun updateStateSealedHierarchyShape() {
        val base = updateStateClass()
        Assume.assumeTrue(
            "Seam: UpdateEngine slice has not landed UpdateState yet.",
            base != null
        )
        for (name in listOf("Idle", "Checking", "UpToDate", "Available", "Downloading", "Downloaded", "Failed")) {
            assertTrue("UpdateState must declare $name.", variant(name) != null)
        }
        assertHasArity("UpToDate", 1)
        assertHasArity("Available", 4)
        assertHasArity("Downloading", 1)
        assertHasArity("Downloaded", 1)
        assertHasArity("Failed", 1)
    }

    private fun assertHasArity(variant: String, arity: Int) {
        // Any declared constructor (incl. synthetic default-value ctors).
        val arities = variant(variant)!!.declaredConstructors.map { it.parameterCount }
        assertTrue("UpdateState.$variant must declare a $arity-arg constructor.", arity in arities)
    }
    @Test
    fun updateCheckerExposesSuspendCheck() {
        val checker = try {
            Class.forName("com.idlix.tv.update.UpdateChecker")
        } catch (_: Throwable) {
            null
        }
        Assume.assumeTrue(
            "Seam: UpdateEngine slice has not landed UpdateChecker yet.",
            checker != null
        )
        val method = checker!!.methods.firstOrNull { it.name == "checkForUpdate" }
        Assume.assumeTrue(
            "Seam: UpdateChecker must expose checkForUpdate(context, currentVersionCode: Long).",
            method != null
        )
        assertTrue(method!!.parameterTypes.any { it == Long::class.javaPrimitiveType })
    }

    @Test
    fun versionCompareMatrixNeedsFakeFetcherSeam() {
        val checker = try {
            Class.forName("com.idlix.tv.update.UpdateChecker")
        } catch (_: Throwable) {
            null
        }
        Assume.assumeTrue(
            "Seam: UpdateChecker not landed, or has no injectable release-JSON " +
                "fetcher for the offline matrix (newer->Available, equal->UpToDate, " +
                "malformed/offline->Failed). No live GitHub fetch in unit tests.",
            checker != null && hasInjectableFetcher(checker)
        )
    }

    private fun hasInjectableFetcher(checker: Class<*>): Boolean {
        return checker.declaredConstructors.any { ctor ->
            ctor.parameterTypes.any { it.name.contains("Function") || it.name.contains("Fetcher") }
        } || checker.methods.any { it.name.contains("parse") }
    }

    // Regression: "update offered while on latest" (v1.0.2 loop). The old
    // trailing-int-vs-versionCode scheme broke whenever tags outran the
    // installed versionCode; semver-vs-versionName decides now.
    @Test
    fun parseSemverHandlesTags() {
        assertEquals(listOf(1, 0, 3), UpdateChecker.parseSemver("v1.0.3"))
        assertEquals(listOf(1, 0, 3), UpdateChecker.parseSemver("1.0.3"))
        assertEquals(listOf(2, 0, 0), UpdateChecker.parseSemver("v2"))
        assertEquals(listOf(1, 0, 0), UpdateChecker.parseSemver("1.0"))
        assertEquals(listOf(1, 0, 3), UpdateChecker.parseSemver("1.0.3-beta"))
        assertEquals(null, UpdateChecker.parseSemver("release-abc"))
        assertEquals(null, UpdateChecker.parseSemver("  "))
        assertEquals(null, UpdateChecker.parseSemver(""))
    }

    @Test
    fun compareSemverOrdersReleases() {
        assertTrue(UpdateChecker.compareSemver(listOf(1, 0, 3), listOf(1, 0, 3)) == 0)
        assertTrue(UpdateChecker.compareSemver(listOf(1, 0, 3), listOf(1, 0, 2)) > 0)
        assertTrue(UpdateChecker.compareSemver(listOf(1, 0, 0), listOf(1, 0, 2)) < 0)
        assertTrue(UpdateChecker.compareSemver(listOf(2, 0, 0), listOf(1, 9, 9)) > 0)
        assertTrue(UpdateChecker.compareSemver(listOf(1, 0), listOf(1, 0, 1)) < 0)
    }

    @Test
    fun installedLatestIsUpToDate() {
        // The exact reported loop: installed 1.0.3 against tag v1.0.3.
        val tag = UpdateChecker.parseSemver("v1.0.3")!!
        val app = UpdateChecker.parseSemver("1.0.3")!!
        assertTrue(UpdateChecker.compareSemver(app, tag) >= 0)
    }

    // No-API redirect flow: pure, network-free. No live GitHub fetch.

    @Test
    fun isRedirectAcceptsRedirectFamily() {
        assertTrue(UpdateChecker.isRedirect(301))
        assertTrue(UpdateChecker.isRedirect(302))
        assertTrue(UpdateChecker.isRedirect(303))
        assertTrue(UpdateChecker.isRedirect(307))
        assertTrue(UpdateChecker.isRedirect(308))
        assertTrue(!UpdateChecker.isRedirect(200))
        assertTrue(!UpdateChecker.isRedirect(404))
        assertTrue(!UpdateChecker.isRedirect(500))
    }

    @Test
    fun parseTagFromLocationHandlesForms() {
        assertEquals(
            "v1.0.5",
            UpdateChecker.parseTagFromLocation("/trefeon/idlix-tv/releases/tag/v1.0.5")
        )
        assertEquals(
            "v1.0.5",
            UpdateChecker.parseTagFromLocation("https://github.com/trefeon/idlix-tv/releases/tag/v1.0.5")
        )
        assertEquals(
            "v1.0.5",
            UpdateChecker.parseTagFromLocation("https://github.com/trefeon/idlix-tv/releases/tag/v1.0.5/")
        )
        assertEquals(
            "v1.0.5",
            UpdateChecker.parseTagFromLocation("/trefeon/idlix-tv/releases/tag/v1.0.5?foo=bar")
        )
        assertEquals(null, UpdateChecker.parseTagFromLocation(null))
        assertEquals(null, UpdateChecker.parseTagFromLocation(""))
        assertEquals(null, UpdateChecker.parseTagFromLocation("   "))
        // Unexpected redirect targets (login wall, tag list) carry no tag.
        assertEquals(null, UpdateChecker.parseTagFromLocation("https://github.com/login"))
        assertEquals(null, UpdateChecker.parseTagFromLocation("/trefeon/idlix-tv/releases"))
        assertEquals(null, UpdateChecker.parseTagFromLocation("/trefeon/idlix-tv/releases/tag/"))
    }

    @Test
    fun assetUrlForTagIsDeterministic() {
        assertEquals(
            "https://github.com/trefeon/idlix-tv/releases/download/v1.0.5/idlix-tv-v1.0.5.apk",
            UpdateChecker.assetUrlForTag("v1.0.5")
        )
    }

    @Test
    fun parseContentLengthHandlesHeaders() {
        assertEquals(42L, UpdateChecker.parseContentLength("42"))
        assertEquals(42L, UpdateChecker.parseContentLength("  42  "))
        assertEquals(0L, UpdateChecker.parseContentLength(null))
        assertEquals(0L, UpdateChecker.parseContentLength(""))
        assertEquals(0L, UpdateChecker.parseContentLength("garbage"))
        assertEquals(0L, UpdateChecker.parseContentLength("0"))
        assertEquals(0L, UpdateChecker.parseContentLength("-5"))
    }

    @Test
    fun resolveAssetSizeMatrix() {
        // HEAD served: size from Content-Length, 0 when absent.
        assertEquals(42L, UpdateChecker.resolveAssetSize(200, "42", null, null))
        assertEquals(0L, UpdateChecker.resolveAssetSize(200, null, null, null))
        assertEquals(0L, UpdateChecker.resolveAssetSize(200, "garbage", null, null))
        // HEAD blocked (405/403) -> range GET decides.
        assertEquals(99L, UpdateChecker.resolveAssetSize(405, null, 206, "99"))
        assertEquals(7L, UpdateChecker.resolveAssetSize(403, null, 200, "7"))
        assertEquals(0L, UpdateChecker.resolveAssetSize(405, null, 206, null))
        // Both fail -> APK missing.
        assertEquals(null, UpdateChecker.resolveAssetSize(404, null, 404, null))
        assertEquals(null, UpdateChecker.resolveAssetSize(500, null, null, null))
    }

    @Test
    fun decideReleaseMatrix() {
        val url = UpdateChecker.assetUrlForTag("v1.0.4")
        // Newer tag -> Available with trimmed version.
        val newer = UpdateChecker.decideRelease("v1.0.4", "1.0.3", 3L, url, 42L)
        assertTrue(newer is UpdateChecker.ReleaseDecision.Available)
        newer as UpdateChecker.ReleaseDecision.Available
        assertEquals("1.0.4", newer.version)
        assertEquals(url, newer.assetUrl)
        assertEquals(42L, newer.sizeBytes)
        // Unknown size (0) is still offered.
        val unknownSize = UpdateChecker.decideRelease("v1.0.4", "1.0.3", 3L, url, 0L)
        assertTrue(unknownSize is UpdateChecker.ReleaseDecision.Available)
        assertEquals(0L, (unknownSize as UpdateChecker.ReleaseDecision.Available).sizeBytes)
        // Equal tag -> UpToDate (the v1.0.2 loop regression).
        assertTrue(
            UpdateChecker.decideRelease(
                "v1.0.3", "1.0.3", 3L, UpdateChecker.assetUrlForTag("v1.0.3"), 1L
            ) is UpdateChecker.ReleaseDecision.UpToDate
        )
        // Older tag -> UpToDate.
        assertTrue(
            UpdateChecker.decideRelease(
                "v1.0.2", "1.0.3", 3L, UpdateChecker.assetUrlForTag("v1.0.2"), 1L
            ) is UpdateChecker.ReleaseDecision.UpToDate
        )
        // Newer tag but blank asset URL -> NoAsset.
        assertTrue(
            UpdateChecker.decideRelease("v1.0.4", "1.0.3", 3L, "", 0L)
                is UpdateChecker.ReleaseDecision.NoAsset
        )
        // Blank tag -> BadData.
        assertTrue(
            UpdateChecker.decideRelease("v", "1.0.3", 3L, url, 1L)
                is UpdateChecker.ReleaseDecision.BadData
        )
        // Non-semver tag falls back to trailing int.
        assertTrue(
            UpdateChecker.decideRelease("release-2", "release-abc", 5L, url, 1L)
                is UpdateChecker.ReleaseDecision.UpToDate
        )
        // Non-semver tag with no trailing int falls back to versionName equality.
        assertTrue(
            UpdateChecker.decideRelease("release-abc", "release-abc", 0L, url, 1L)
                is UpdateChecker.ReleaseDecision.UpToDate
        )
        assertTrue(
            UpdateChecker.decideRelease("release-xyz", "release-abc", 0L, url, 1L)
                is UpdateChecker.ReleaseDecision.Available
        )
    }
}
