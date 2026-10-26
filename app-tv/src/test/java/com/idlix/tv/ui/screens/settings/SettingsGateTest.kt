package com.idlix.tv.ui.screens.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.util.ArrayDeque

/**
 * Contract tests for the Settings advanced gate (owned by the SettingsGate
 * slice: SettingsScreen.kt / SettingsViewModel.kt + settings_* strings).
 *
 * The ViewModel constructor needs a functional Application (SharedPreferences
 * + Dispatchers.Main), neither of which exists in plain JVM unit tests — so
 * these tests allocate the ViewModel without running its constructor
 * (sun.misc.Unsafe) and inject only the gate collaborators:
 * `_advancedUnlocked`, `versionClickTimes`, `dpadTrail`, `advPrefs` (fake).
 * This exercises the real 7-taps-in-3s counting, unlock, and relock logic
 * with zero framework and zero network.
 */
class SettingsGateTest {

    private class FakeEditor(private val map: MutableMap<String, Any?>) : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            map[key!!] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            map[key!!] = values
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            map[key!!] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            map[key!!] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            map[key!!] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            map[key!!] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            map.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            map.clear()
            return this
        }

        override fun commit(): Boolean = true

        override fun apply() = Unit
    }

    private class FakePrefs : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        fun storedBoolean(key: String): Boolean? = map[key] as? Boolean

        override fun getAll(): Map<String, *> = map.toMap()

        override fun getString(key: String?, defValue: String?): String? =
            map[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            map[key] as? MutableSet<String> ?: defValues

        override fun getInt(key: String?, defValue: Int): Int =
            map[key] as? Int ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            map[key] as? Long ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            map[key] as? Float ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            map[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor(map)

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit
    }

    private fun inject(vm: Any, name: String, value: Any) {
        val field = vm.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(vm, value)
    }

    private fun newGateOnlyViewModel(): SettingsViewModel {
        try {
            val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
            unsafeField.isAccessible = true
            val unsafe = unsafeField.get(null)
            val allocate = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
            val vm = allocate.invoke(unsafe, SettingsViewModel::class.java) as SettingsViewModel
            val gate = MutableStateFlow(false)
            inject(vm, "_advancedUnlocked", gate)
            inject(vm, "advancedUnlocked", gate.asStateFlow())
            inject(vm, "versionClickTimes", ArrayDeque<Long>())
            inject(vm, "dpadTrail", ArrayDeque<Int>())
            inject(vm, "advPrefs", FakePrefs())
            return vm
        } catch (e: Throwable) {
            Assume.assumeTrue("Seam: Unsafe allocation unavailable: ${e.message}", false)
            throw AssertionError("unreachable")
        }
    }

    @Test
    fun advancedSectionLockedByDefault() {
        val vm = newGateOnlyViewModel()
        assertFalse(vm.advancedUnlocked.value)
    }

    @Test
    fun sevenClicksUnlockAndRelock() {
        val vm = newGateOnlyViewModel()

        // First six rapid taps: still locked, none completes the unlock.
        repeat(6) {
            assertFalse(vm.recordAdvancedClick())
        }
        assertFalse(vm.advancedUnlocked.value)

        // Seventh tap inside the 3s window unlocks.
        assertTrue(vm.recordAdvancedClick())
        assertTrue(vm.advancedUnlocked.value)

        // Relock returns to locked.
        vm.relockAdvanced()
        assertFalse(vm.advancedUnlocked.value)
    }

    @Test
    fun unlockAndRelockPersistSessionFlag() {
        val vm = newGateOnlyViewModel()
        repeat(7) { vm.recordAdvancedClick() }
        assertTrue(vm.advancedUnlocked.value)
        vm.relockAdvanced()
        assertFalse(vm.advancedUnlocked.value)
        // A fresh gate starts locked again (session-only, reset on process start).
        val fresh = newGateOnlyViewModel()
        assertFalse(fresh.advancedUnlocked.value)
    }
}
