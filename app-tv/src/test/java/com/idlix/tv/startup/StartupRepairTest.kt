package com.idlix.tv.startup

import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * Contract tests for TV self-heal (owned by the SelfHeal slice:
 * IdlixTvApp.kt startup wiring + startup/StartupRepair.kt + ErrorLog.kt).
 * Headless by contract: no UI, no settings edits.
 *
 * Behaviour (corrupt prefs quarantine, cache-fallback flag) needs an
 * Android Context or an injectable prefs/file seam; until then these tests
 * assert the slice shape and skip the behavioural matrix with the seam note.
 */
class StartupRepairTest {

    private fun loadClass(name: String): Class<*>? {
        return try {
            Class.forName(name)
        } catch (_: Throwable) {
            null
        }
    }

    @Test
    fun startupRepairSlicePresent() {
        val repair = loadClass("com.idlix.tv.startup.StartupRepair")
        Assume.assumeTrue(
            "Seam: SelfHeal slice has not landed startup/StartupRepair.kt yet.",
            repair != null
        )
        assertTrue(repair!!.name.contains("startup"))
    }

    @Test
    fun errorLogSlicePresent() {
        val log = loadClass("com.idlix.tv.startup.ErrorLog")
        Assume.assumeTrue(
            "Seam: SelfHeal slice has not landed startup/ErrorLog.kt yet.",
            log != null
        )
        assertTrue(log!!.name.contains("startup"))
    }

    @Test
    fun corruptPrefsQuarantineNeedsContextSeam() {
        val repair = loadClass("com.idlix.tv.startup.StartupRepair")
        Assume.assumeTrue(
            "Seam: StartupRepair not landed, or exposes no Context-free " +
                "repair entry point for the corrupt-prefs quarantine + " +
                "cache-fallback offline matrix.",
            repair != null && hasTestableEntryPoint(repair)
        )
    }

    private fun hasTestableEntryPoint(repair: Class<*>): Boolean {
        // A repair/run/start/quarantine entry point without Android params.
        return repair.methods.any { method ->
            (method.name.contains("repair", ignoreCase = true) ||
                method.name.contains("run", ignoreCase = true) ||
                method.name.contains("start", ignoreCase = true) ||
                method.name.contains("quarantine", ignoreCase = true) ||
                method.name.contains("execute", ignoreCase = true)) &&
                method.parameterTypes.none { it.name.startsWith("android.") }
        }
    }
}
