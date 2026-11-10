package app.vicsmusic.core.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyChartsClientTest {

    @Test
    fun defaultChartsContainPeruAndGlobal() {
        val charts = SpotifyChartsClient.DEFAULT_CHARTS
        val peru = charts.find { it.id == "top_50_peru" }
        val global = charts.find { it.id == "top_50_global" }
        val vivaLatino = charts.find { it.id == "viva_latino" }

        assertNotNull(peru)
        assertEquals("37i9dQZEVXbJfdy5b0KP7W", peru?.playlistId)
        assertEquals("Top 50 - Perú", peru?.defaultTitle)
        assertTrue(peru?.fallbackCoverUrl?.contains("region_pe_default.jpg") == true)

        assertNotNull(global)
        assertEquals("37i9dQZEVXbMDoHDwVN2tF", global?.playlistId)
        assertTrue(global?.fallbackCoverUrl?.contains("region_global_default.jpg") == true)

        assertNotNull(vivaLatino)
        assertEquals("37i9dQZF1DX10zKzsJ2jva", vivaLatino?.playlistId)
    }
}
