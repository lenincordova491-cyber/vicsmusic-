package app.vicsmusic.source.remote.innertube

import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class InnerTubeSession(
    val cookieHeader: String? = null,
    val visitorData: String? = null,
    val dataSyncId: String? = null
) {
    val isLoggedIn: Boolean
        get() = !cookieHeader.isNullOrBlank() && (
            cookieHeader.contains("SAPISID=") ||
                cookieHeader.contains("__Secure-3PAPISID=") ||
                cookieHeader.contains("__Secure-1PAPISID=") ||
                cookieHeader.contains("PAPISID=") ||
                cookieHeader.contains("__Secure-3PSID=") ||
                cookieHeader.contains("SID=") ||
                cookieHeader.contains("LOGIN_INFO=")
            )
}

@Singleton
class InnerTubeSessionManager @Inject constructor() {

    companion object {
        private const val YTM_ORIGIN = "https://music.youtube.com"
    }

    private val _session = MutableStateFlow(InnerTubeSession())
    val session: StateFlow<InnerTubeSession> = _session.asStateFlow()

    fun updateSession(cookieHeader: String?, visitorData: String?, dataSyncId: String? = null) {
        val cleanCookies = cookieHeader?.trim()?.takeIf { it.isNotEmpty() }
        val resolvedVisitor = visitorData?.trim()?.takeIf { it.isNotEmpty() }
            ?: cleanCookies?.let { extractCookie(it, "VISITOR_INFO1_LIVE") }
            ?: _session.value.visitorData
        _session.value = InnerTubeSession(
            cookieHeader = cleanCookies,
            visitorData = resolvedVisitor,
            dataSyncId = dataSyncId?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    fun updateVisitorData(visitorData: String) {
        if (visitorData.isNotBlank()) {
            _session.value = _session.value.copy(visitorData = visitorData)
        }
    }

    fun clearSession() {
        _session.value = InnerTubeSession(visitorData = _session.value.visitorData)
    }

    fun hasSession(): Boolean = _session.value.isLoggedIn

    /**
     * Calcula el header Authorization: SAPISIDHASH <timestamp>_<sha1> requerido por
     * los endpoints autenticados de YouTube Music (FEmusic_home, FEmusic_history, FEmusic_liked_videos).
     */
    fun buildAuthorizationHeader(origin: String = YTM_ORIGIN): String? {
        val cookies = _session.value.cookieHeader ?: return null
        val sapisid = extractCookie(cookies, "SAPISID")
            ?: extractCookie(cookies, "__Secure-3PAPISID")
            ?: extractCookie(cookies, "__Secure-1PAPISID")
            ?: extractCookie(cookies, "PAPISID")
            ?: return null

        val timestampSec = System.currentTimeMillis() / 1000L
        val payload = "$timestampSec $sapisid $origin"
        val sha1 = MessageDigest.getInstance("SHA-1")
            .digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        return "SAPISIDHASH ${timestampSec}_$sha1"
    }

    private fun extractCookie(cookieHeader: String, name: String): String? =
        cookieHeader.split(';')
            .asSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
}
