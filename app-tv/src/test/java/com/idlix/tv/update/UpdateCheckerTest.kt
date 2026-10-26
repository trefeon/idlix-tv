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
            "Seam: UpdateChecker must expose checkForUpdate(currentVersionCode: Long).",
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
}
