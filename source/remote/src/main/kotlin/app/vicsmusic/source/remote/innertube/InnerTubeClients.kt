package app.vicsmusic.source.remote.innertube

/**
 * Identity an InnerTube request presents. Each profile maps to an official
 * YouTube client; values are public and rotate over time, so they are kept in
 * one place for easy maintenance.
 */
data class InnerTubeClientProfile(
    val clientName: String,
    val clientVersion: String,
    val clientId: Int,
    val userAgent: String,
    val baseUrl: String,
    val origin: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val osName: String? = null,
    val osVersion: String? = null,
    val androidSdkVersion: Int? = null
)

object InnerTubeClients {

    // Verification timestamp for current client versions and headers: 2026-11-05
    // TODO: Periodically verify versions against latest yt-dlp upstream table

    /** YouTube Music web client: richest catalog metadata for search/browse. */
    val WEB_REMIX = InnerTubeClientProfile(
        clientName = "WEB_REMIX",
        clientVersion = "1.20241127.01.00",
        clientId = 67,
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        baseUrl = "https://music.youtube.com/youtubei/v1",
        origin = "https://music.youtube.com"
    )

    /**
     * Android Testsuite client: top priority for direct non-ciphered, non-restricted audio streams.
     * Verified with live 206 stream response: 2026-11-06
     */
    val ANDROID_TESTSUITE = InnerTubeClientProfile(
        clientName = "ANDROID_TESTSUITE",
        clientVersion = "1.9",
        clientId = 82,
        userAgent = "Google/1.0",
        baseUrl = "https://www.youtube.com/youtubei/v1",
        deviceMake = "Google",
        deviceModel = "Pixel 7",
        osName = "Android",
        osVersion = "14",
        androidSdkVersion = 34
    )

    /**
     * VisionOS client: top priority for direct adaptive streams without SABR/cipher.
     * Verified with X-Goog-Visitor-Id: 2026-11-06
     */
    val VISIONOS = InnerTubeClientProfile(
        clientName = "VISIONOS",
        clientVersion = "1.02",
        clientId = 101,
        userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
        baseUrl = "https://www.youtube.com/youtubei/v1",
        origin = "https://www.youtube.com",
        deviceMake = "Apple",
        deviceModel = "RealityDevice17,1",
        osName = "visionOS",
        osVersion = "26.5.23O471"
    )

    /**
     * Android VR client: exposes direct non-ciphered adaptive audio URLs.
     * Verified: 2026-11-05
     */
    val ANDROID_VR = InnerTubeClientProfile(
        clientName = "ANDROID_VR",
        clientVersion = "1.65.10",
        clientId = 28,
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 " +
            "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
        baseUrl = "https://www.youtube.com/youtubei/v1",
        deviceMake = "Oculus",
        deviceModel = "Quest 3",
        osName = "Android",
        osVersion = "12L",
        androidSdkVersion = 32
    )

    /**
     * iOS client: fallback returning direct adaptive audio URLs.
     * Verified: 2026-11-05
     */
    val IOS = InnerTubeClientProfile(
        clientName = "IOS",
        clientVersion = "21.26.4",
        clientId = 5,
        userAgent = "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
        baseUrl = "https://www.youtube.com/youtubei/v1",
        deviceMake = "Apple",
        deviceModel = "iPhone16,2",
        osName = "iPhone",
        osVersion = "18.1.0"
    )

    /**
     * Android client: Primary verified client for direct adaptive audio streams (itag 140/251)
     * without signatureCipher or bot-login prompts.
     * Verified HTTP 206 Partial Content: 2026-11-06
     */
    val ANDROID = InnerTubeClientProfile(
        clientName = "ANDROID",
        clientVersion = "20.10.38",
        clientId = 3,
        userAgent = "com.google.android.youtube/20.10.38 (Linux; U; Android 13; en_US)",
        baseUrl = "https://www.youtube.com/youtubei/v1",
        deviceMake = "Google",
        deviceModel = "Pixel 7",
        osName = "Android",
        osVersion = "13",
        androidSdkVersion = 33
    )

    /**
     * TVHTML5 client: smart TV web runtime fallback.
     * Verified: 2026-11-05
     */
    val TVHTML5 = InnerTubeClientProfile(
        clientName = "TVHTML5",
        clientVersion = "7.20260707.07.00",
        clientId = 7,
        userAgent = "Mozilla/5.0 (ChromiumStylePlatform; Linux x86_64; smart-tv) AppleWebKit/537.36 (KHTML, like Gecko) Cobalt/24.lts.4-gold Safari/537.36",
        baseUrl = "https://www.youtube.com/youtubei/v1",
        origin = "https://www.youtube.com"
    )

    /**
     * Ordered fallback chain used to resolve playable audio streams.
     * Priority order: ANDROID (20.10.38) -> IOS -> WEB_REMIX -> ANDROID_VR -> TVHTML5 -> VISIONOS
     */
    val STREAM_CLIENTS: List<InnerTubeClientProfile> = listOf(
        ANDROID,
        IOS,
        WEB_REMIX,
        ANDROID_VR,
        TVHTML5,
        VISIONOS
    )

    /** `params` value of the YouTube Music search "Songs" filter chip. */
    const val SEARCH_FILTER_SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
}
