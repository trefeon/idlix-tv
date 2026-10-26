package com.idlix.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline-only tests for [IdlixDomainResolver] pure URL policy.
 *
 * No network is performed: [IdlixDomainResolver.resolve] is never called.
 * Private [IdlixDomainResolver.normalizeAndValidate] is exercised via
 * reflection because it is the fail-closed gate (scheme + allowlist).
 */
class IdlixDomainResolverTest {

    private fun normalize(location: String): String? {
        val method = IdlixDomainResolver::class.java
            .getDeclaredMethod("normalizeAndValidate", String::class.java)
        method.isAccessible = true
        return method.invoke(IdlixDomainResolver, location) as String?
    }

    private fun readCached(): String? {
        val method = IdlixDomainResolver::class.java.getDeclaredMethod("readCached")
        method.isAccessible = true
        return method.invoke(IdlixDomainResolver) as String?
    }

    @Test
    fun canonicalUrlIsIdlixNetEntryPoint() {
        assertEquals("https://idlix.net/", IdlixDomainResolver.CANONICAL_URL)
    }

    @Test
    fun fallbackIsHttpsIdlixMirror() {
        val fallback = IdlixDomainResolver.FALLBACK_URL
        assertTrue(fallback.startsWith("https://"))
        assertTrue(fallback.endsWith("/"))
    }

    @Test
    fun fallbackPassesOwnAllowlist() {
        // Fail-closed fallback must itself be accepted by the gate.
        assertEquals(IdlixDomainResolver.FALLBACK_URL, normalize(IdlixDomainResolver.FALLBACK_URL))
    }

    @Test
    fun canonicalHostPassesAllowlist() {
        assertEquals("https://idlix.net/", normalize("https://idlix.net/"))
    }

    @Test
    fun liveMirrorVariantsAccepted() {
        assertEquals("https://z2.idlixku.com/", normalize("https://z2.idlixku.com/"))
        assertEquals("https://z9.idlixku.com/", normalize("https://z9.idlixku.com"))
        assertEquals("https://idlixku.com/", normalize("https://idlixku.com"))
        assertEquals("https://z2.idlixku.com/a/b/", normalize("https://z2.idlixku.com/a/b"))
    }

    @Test
    fun nonHttpsLocationRejected() {
        assertNull(normalize("http://z2.idlixku.com/"))
    }

    @Test
    fun openRedirectHostRejected() {
        assertNull(normalize("https://evil.example.com/"))
    }

    @Test
    fun broadSubstringAllowlistDocumentsHardeningSeam() {
        // Needed seam (no main-code edit here): the gate in
        // IdlixDomainResolver.kt allowlist (host.contains("idlix")) also
        // accepts lookalike hosts such as idlixku.evil.example.com. Current
        // behavior is asserted to lock it in; tightening to an exact /
        // registered-domain match is a recommended CoreResolver hardening.
        assertEquals("https://idlixku.evil.example.com/", normalize("https://idlixku.evil.example.com/"))
    }

    @Test
    fun legacyBackendHostsFailClosed() {
        // Deleted backend must never be reintroduced via a Location header.
        assertNull(normalize("https://streamapi.abdiworks.my.id/v1/movies"))
        assertNull(normalize("https://cdn.abdilah.id/assets/app.js"))
    }

    @Test
    fun blankAndGarbageLocationsRejected() {
        assertNull(normalize(""))
        assertNull(normalize("   "))
        assertNull(normalize("not a url"))
        assertNull(normalize("ftp://z2.idlixku.com/"))
    }

    @Test
    fun missingTrailingSlashIsNormalized() {
        assertEquals("https://z2.idlixku.com/", normalize("https://z2.idlixku.com"))
    }

    @Test
    fun hostCaseIsNormalized() {
        assertEquals("https://z2.idlixku.com/", normalize("HTTPS://Z2.IDLIXKU.COM/"))
    }

    @Test
    fun relativeLocationIsRejectedWithoutBaseResolve() {
        // Needed seam (no main-code edit here): 302 handlers in the wild may
        // emit a relative Location ("/abc"). Current normalizeAndValidate
        // returns null for it. To support it, resolve relative locations
        // against CANONICAL_URL in IdlixDomainResolver.kt normalizeAndValidate.
        assertNull(normalize("/z2-mirror-path"))
        assertNull(normalize("z2-mirror-path"))
    }

    @Test
    fun readCachedWithoutContextReturnsNull() {
        // No Context registered in unit tests: fail-closed, no crash.
        assertNull(readCached())
    }

    @Test
    fun defaultBaseUrlIsFallback() {
        assertEquals(IdlixDomainResolver.FALLBACK_URL, IdlixDomainResolver.currentBaseUrl())
    }

    @Test
    fun originHasNoTrailingSlash() {
        val origin = IdlixDomainResolver.origin()
        assertTrue(origin.startsWith("https://"))
        assertTrue(!origin.endsWith("/"))
    }

    @Test
    fun refererAlwaysEndsWithSlash() {
        val referer = IdlixDomainResolver.referer()
        assertTrue(referer.startsWith("https://"))
        assertTrue(referer.endsWith("/"))
    }

    @Test
    fun originPlusSlashEqualsReferer() {
        assertEquals(IdlixDomainResolver.origin() + "/", IdlixDomainResolver.referer())
    }
}
