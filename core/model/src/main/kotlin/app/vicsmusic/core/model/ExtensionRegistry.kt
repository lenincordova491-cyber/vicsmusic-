package app.vicsmusic.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExtensionRegistry(
    val version: Int = 1,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val extensions: List<ExtensionItem> = emptyList()
)

@Serializable
data class ExtensionItem(
    val id: String,
    val name: String,
    @SerialName("display_name")
    val displayName: String,
    val version: String,
    val description: String = "",
    @SerialName("download_url")
    val downloadUrl: String? = null,
    @SerialName("icon_url")
    val iconUrl: String? = null,
    val sha256: String? = null,
    val category: String = "general",
    val tags: List<String> = emptyList(),
    val downloads: Long = 0L,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("min_app_version")
    val minAppVersion: String? = null
)
