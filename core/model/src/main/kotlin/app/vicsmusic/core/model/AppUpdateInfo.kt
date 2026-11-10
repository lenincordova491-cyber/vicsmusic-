package app.vicsmusic.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String,
    val minAppVersion: Int = 1,
    val forceUpdate: Boolean = false
)
