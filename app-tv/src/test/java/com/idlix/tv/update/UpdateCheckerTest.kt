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
 * (newer/equal/malformed/offline JSON -> Available/UpToDate/Failed) needs
 * an injectable release-fetch seam in UpdateChecker.kt so tests never hit
 * https://api.github.com/repos/trefeon/idlix-tv/releases/latest; no live
 * fetch is performed here.
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

    // Rate-limit quota saving: pure, network-free. No live GitHub fetch.

    @Test
    fun parseRateLimitResetHandlesHeaders() {
        assertEquals(1767225600L, UpdateChecker.parseRateLimitReset("1767225600"))
        assertEquals(1767225600L, UpdateChecker.parseRateLimitReset("  1767225600  "))
        assertEquals(0L, UpdateChecker.parseRateLimitReset(null))
        assertEquals(0L, UpdateChecker.parseRateLimitReset(""))
        assertEquals(0L, UpdateChecker.parseRateLimitReset("garbage"))
        assertEquals(0L, UpdateChecker.parseRateLimitReset("-5"))
        assertEquals(0L, UpdateChecker.parseRateLimitReset("0"))
    }

    @Test
    fun rateLimitWindowActiveOnlyWhenFuture() {
        assertTrue(UpdateChecker.isRateLimitWindowActive(1000L, 2000L))
        assertTrue(!UpdateChecker.isRateLimitWindowActive(2000L, 2000L))
        assertTrue(!UpdateChecker.isRateLimitWindowActive(3000L, 2000L))
        assertTrue(!UpdateChecker.isRateLimitWindowActive(1000L, 0L))
    }

    @Test
    fun rateLimitedBodyMatchesGitHubWording() {
        assertTrue(
            UpdateChecker.isRateLimitedBody(
                "{\"message\":\"API rate limit exceeded for 1.2.3.4.\"}"
            )
        )
        assertTrue(
            UpdateChecker.isRateLimitedBody(
                "{\"message\":\"You have exceeded a secondary rate limit.\"}"
            )
        )
        assertTrue(!UpdateChecker.isRateLimitedBody("{\"message\":\"Not Found\"}"))
        assertTrue(!UpdateChecker.isRateLimitedBody(""))
    }

    @Test
    fun retryTimeFormatsAsHourMinute() {
        val formatted = UpdateChecker.formatRetryTime(1767225600L)
        assertTrue("retry time must read HH:MM, got: $formatted", formatted.contains(":"))
    }

    @Test
    fun selectApkAssetPrefersApk() {
        val checksum = UpdateChecker.ReleaseAsset("https://x/app.zip", 10L)
        val apk = UpdateChecker.ReleaseAsset("https://x/app-release.apk", 20L)
        assertEquals(apk, UpdateChecker.selectApkAsset(listOf(checksum, apk)))
        assertEquals(checksum, UpdateChecker.selectApkAsset(listOf(checksum)))
        assertEquals(null, UpdateChecker.selectApkAsset(emptyList()))
    }

    @Test
    fun decideReleaseMatrix() {
        val apk = UpdateChecker.ReleaseAsset("https://x/app-release.apk", 42L)
        // Newer tag -> Available with trimmed version.
        val newer = UpdateChecker.decideRelease("v1.0.4", "notes", listOf(apk), "1.0.3", 3L)
        assertTrue(newer is UpdateChecker.ReleaseDecision.Available)
        assertEquals("1.0.4", (newer as UpdateChecker.ReleaseDecision.Available).version)
        assertEquals("https://x/app-release.apk", newer.assetUrl)
        // Equal tag -> UpToDate (the v1.0.2 loop regression).
        assertTrue(
            UpdateChecker.decideRelease("v1.0.3", "", listOf(apk), "1.0.3", 3L)
                is UpdateChecker.ReleaseDecision.UpToDate
        )
        // Newer tag but no asset -> NoAsset.
        assertTrue(
            UpdateChecker.decideRelease("v1.0.4", "", emptyList(), "1.0.3", 3L)
                is UpdateChecker.ReleaseDecision.NoAsset
        )
        // Blank tag -> BadData.
        assertTrue(
            UpdateChecker.decideRelease("v", "", listOf(apk), "1.0.3", 3L)
                is UpdateChecker.ReleaseDecision.BadData
        )
        // Non-semver tag falls back to trailing int.
        assertTrue(
            UpdateChecker.decideRelease("release-2", "", listOf(apk), "release-abc", 5L)
                is UpdateChecker.ReleaseDecision.UpToDate
        )
    }

    @Test
    fun decideCachedReleaseMatrix() {
        // Newer cached tag + asset -> Available (304 still offers download).
        val hit = UpdateChecker.decideCachedRelease(
            "v1.0.4", "notes", "https://x/app-release.apk", 42L, "1.0.3", 3L
        )
        assertTrue(hit is UpdateChecker.ReleaseDecision.Available)
        // Equal cached tag -> UpToDate.
        assertTrue(
            UpdateChecker.decideCachedRelease(
                "v1.0.3", "", "https://x/app-release.apk", 42L, "1.0.3", 3L
            ) is UpdateChecker.ReleaseDecision.UpToDate
        )
        // Newer tag but lost asset URL -> UpToDate (nothing to download).
        assertTrue(
            UpdateChecker.decideCachedRelease("v1.0.4", "", "", 0L, "1.0.3", 3L)
                is UpdateChecker.ReleaseDecision.UpToDate
        )
        // Empty cache -> UpToDate.
        assertTrue(
            UpdateChecker.decideCachedRelease("", "", "", 0L, "1.0.3", 3L)
                is UpdateChecker.ReleaseDecision.UpToDate
        )
    }
}
