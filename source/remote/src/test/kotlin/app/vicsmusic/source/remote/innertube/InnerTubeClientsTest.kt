package app.vicsmusic.source.remote.innertube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerTubeClientsTest {

    @Test
    fun streamClientsContainsFiveOrderedProfiles() {
        val clients = InnerTubeClients.STREAM_CLIENTS
        assertEquals(5, clients.size)
        assertEquals("ANDROID_VR", clients[0].clientName)
        assertEquals(28, clients[0].clientId)
        assertEquals("1.65.10", clients[0].clientVersion)

        assertEquals("IOS", clients[1].clientName)
        assertEquals(5, clients[1].clientId)
        assertEquals("21.26.4", clients[1].clientVersion)

        assertEquals("ANDROID", clients[2].clientName)
        assertEquals(3, clients[2].clientId)
        assertEquals("21.26.364", clients[2].clientVersion)

        assertEquals("TVHTML5", clients[3].clientName)
        assertEquals(7, clients[3].clientId)
        assertEquals("7.20260707.07.00", clients[3].clientVersion)

        assertEquals("VISIONOS", clients[4].clientName)
        assertEquals(101, clients[4].clientId)
        assertEquals("1.02", clients[4].clientVersion)
    }

    @Test
    fun allProfilesHaveValidUrlsAndUserAgents() {
        val allProfiles = listOf(
            InnerTubeClients.WEB_REMIX,
            InnerTubeClients.VISIONOS,
            InnerTubeClients.ANDROID_VR,
            InnerTubeClients.IOS,
            InnerTubeClients.ANDROID,
            InnerTubeClients.TVHTML5
        )

        for (profile in allProfiles) {
            assertTrue("Base URL must start with https://", profile.baseUrl.startsWith("https://"))
            assertFalse("User agent must not be blank", profile.userAgent.isBlank())
            assertFalse("Client name must not be blank", profile.clientName.isBlank())
            assertTrue("Client ID must be positive", profile.clientId > 0)
        }
    }
}
