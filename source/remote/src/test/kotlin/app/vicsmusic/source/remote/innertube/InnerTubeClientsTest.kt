package app.vicsmusic.source.remote.innertube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerTubeClientsTest {

    @Test
    fun streamClientsContainsValidProfiles() {
        val clients = InnerTubeClients.STREAM_CLIENTS
        assertEquals(6, clients.size)
        assertEquals("ANDROID", clients[0].clientName)
        assertEquals("IOS", clients[1].clientName)
        assertEquals("WEB_REMIX", clients[2].clientName)
        assertEquals("ANDROID_VR", clients[3].clientName)
        assertEquals("TVHTML5", clients[4].clientName)
        assertEquals("VISIONOS", clients[5].clientName)
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
