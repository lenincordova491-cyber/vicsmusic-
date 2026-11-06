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
     * Android VR client: its /player responses expose direct (non-ciphered)
     * adaptive audio URLs, so no signature handling is required.
     */
    val ANDROID_VR = InnerTubeClientProfile(
        clientName = "ANDROID_VR",
        clientVersion = "1.61.48",
        clientId = 28,
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 " +
            "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
        baseUrl = "https://www.youtube.com/youtubei/v1",
        deviceMake = "Oculus",
        deviceModel = "Quest 3",
        osName = "Android",
        osVersion = "12L",
        androidSdkVersion = 32
    )

    /** iOS client: fallback that also returns direct adaptive audio URLs. */
    val IOS = InnerTubeClientProfile(
        clientName = "IOS",
        clientVersion = "19.45.4",
        clientId = 5,
        userAgent = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
        baseUrl = "https://www.youtube.com/youtubei/v1",
        deviceMake = "Apple",
        deviceModel = "iPhone16,2",
        osName = "iPhone",
        osVersion = "18.1.0.22B83"
    )

    /** Ordered fallback chain used to resolve playable audio streams. */
    val STREAM_CLIENTS: List<InnerTubeClientProfile> = listOf(ANDROID_VR, IOS)

    /** `params` value of the YouTube Music search "Songs" filter chip. */
    const val SEARCH_FILTER_SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
}
