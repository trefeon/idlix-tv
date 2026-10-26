package com.idlix.data.net

import com.idlix.data.model.MovieItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline-only tests for the shared [CoreHttp] stack: JSON policy and
 * OkHttp client wiring. No request is ever executed.
 */
class CoreHttpTest {

    @Test
    fun jsonIgnoresUnknownKeys() {
        val parsed = CoreHttp.json.decodeFromString<MovieItem>(
            """{"id":"1","title":"Fuori","unknownField":123,"nested":{"a":1}}"""
        )
        assertEquals("Fuori", parsed.title)
    }

    @Test
    fun jsonConfigurationIsLenientAndCoercing() {
        val config = CoreHttp.json.configuration
        assertTrue(config.ignoreUnknownKeys)
        assertTrue(config.isLenient)
        assertTrue(config.coerceInputValues)
    }

    @Test
    fun jsonCoercesNullStringToDefault() {
        val parsed = CoreHttp.json.decodeFromString<MovieItem>(
            """{"id":"1","title":null}"""
        )
        assertEquals("", parsed.title)
    }

    @Test
    fun jsonDecodesUnquotedLenientInput() {
        val parsed = CoreHttp.json.decodeFromString<MovieItem>(
            """{id:"1",title:"Fuori"}"""
        )
        assertEquals("Fuori", parsed.title)
    }

    @Test
    fun okHttpClientIsSingleton() {
        assertSame(CoreHttp.okHttpClient, CoreHttp.okHttpClient)
    }

    @Test
    fun noRedirectClientIsSingleton() {
        assertSame(CoreHttp.noRedirectClient, CoreHttp.noRedirectClient)
    }

    @Test
    fun okHttpClientTimeoutsMatchPolicy() {
        val client = CoreHttp.okHttpClient
        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(45_000, client.readTimeoutMillis)
        assertEquals(30_000, client.writeTimeoutMillis)
    }

    @Test
    fun noRedirectClientNeverFollowsRedirects() {
        val client = CoreHttp.noRedirectClient
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    @Test
    fun headerInterceptorIsInstalled() {
        assertTrue(CoreHttp.okHttpClient.interceptors.isNotEmpty())
        assertNotNull(CoreHttp.noRedirectClient)
    }
}
