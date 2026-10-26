package com.idlix.tv.ui.screens.player

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test

/**
 * Token-preservation tests for [QueryPreservingDataSource]: the upstream
 * token query must survive on every segment request.
 *
 * DataSpec URIs are android.net.Uri, which is stubbed in plain JVM unit
 * tests — so these run only when Uri is functional and otherwise skip with
 * the seam note (missing dep: Robolectric, not in the version catalog).
 */
class QueryPreservingDataSourceTest {

    private class FakeDataSource : DataSource {
        var lastSpec: DataSpec? = null

        override fun addTransferListener(transferListener: TransferListener) = Unit

        override fun open(dataSpec: DataSpec): Long {
            lastSpec = dataSpec
            return 0L
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            androidx.media3.common.C.RESULT_END_OF_INPUT

        override fun getUri(): android.net.Uri? = lastSpec?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()

        override fun close() = Unit
    }

    @Before
    fun requireFunctionalUri() {
        val works = try {
            val uri = android.net.Uri.parse("https://cdn.example.com/v/seg.m3u8?t=abc")
            uri != null && uri.toString().contains("t=abc")
        } catch (_: Throwable) {
            false
        }
        Assume.assumeTrue(
            "Seam: android.net.Uri is stubbed; QueryPreservingDataSource " +
                "needs Robolectric (not in catalog) for DataSpec tests.",
            works
        )
    }

    private fun specOf(url: String): DataSpec =
        DataSpec(android.net.Uri.parse(url))

    @Test
    fun appendsTokenWhenNoQueryPresent() {
        val fake = FakeDataSource()
        val ds = QueryPreservingDataSource(fake, "t=abc")
        ds.open(specOf("https://cdn.example.com/v/seg0.m3u8"))

        val finalUrl = fake.lastSpec!!.uri.toString()
        assertEquals("https://cdn.example.com/v/seg0.m3u8?t=abc", finalUrl)
    }

    @Test
    fun joinsTokenWithAmpersandWhenQueryPresent() {
        val fake = FakeDataSource()
        val ds = QueryPreservingDataSource(fake, "t=abc")
        ds.open(specOf("https://cdn.example.com/v/seg0.m3u8?foo=1"))

        val finalUrl = fake.lastSpec!!.uri.toString()
        assertEquals("https://cdn.example.com/v/seg0.m3u8?foo=1&t=abc", finalUrl)
    }

    @Test
    fun leavesUriUntouchedWhenTokenAlreadyPresent() {
        val fake = FakeDataSource()
        val ds = QueryPreservingDataSource(fake, "t=abc")
        ds.open(specOf("https://cdn.example.com/v/seg0.m3u8?t=abc"))

        val finalUrl = fake.lastSpec!!.uri.toString()
        assertEquals("https://cdn.example.com/v/seg0.m3u8?t=abc", finalUrl)
        assertEquals(1, finalUrl.split("t=abc").size - 1)
    }

    @Test
    fun leavesUriUntouchedWhenTokenBlank() {
        val fake = FakeDataSource()
        val ds = QueryPreservingDataSource(fake, "")
        ds.open(specOf("https://cdn.example.com/v/seg0.m3u8"))

        assertEquals("https://cdn.example.com/v/seg0.m3u8", fake.lastSpec!!.uri.toString())
    }

    @Test
    fun factoryWrapsUpstreamDataSource() {
        val factory = QueryPreservingDataSourceFactory(
            DataSource.Factory { FakeDataSource() },
            "t=abc"
        )
        val ds = factory.createDataSource()
        assertTrue(ds is QueryPreservingDataSource)
        ds.open(specOf("https://cdn.example.com/v/seg0.m3u8"))
        val inner = ds as QueryPreservingDataSource
        assertTrue(inner.uri.toString().contains("t=abc"))
    }
}
