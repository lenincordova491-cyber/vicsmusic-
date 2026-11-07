package app.vicsmusic.source.remote.declarative

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ExtensionIndex(
    val schema: String = "vicsmusic-extension-index/1",
    val extensions: List<ExtensionIndexEntry> = emptyList()
)

@Serializable
data class ExtensionIndexEntry(
    val id: String,
    val name: String,
    val file: String,
    val status: String? = null,
    val types: List<String> = emptyList()
)

@Serializable
data class ExtensionLegal(
    val license: String? = null,
    val notice: String? = null,
    val docs: String? = null
)

@Serializable
data class ExtensionAuth(
    val type: String = "none",
    val userProvided: Boolean = false,
    @SerialName("in")
    val inLocation: String? = null,
    val name: String? = null,
    val label: String? = null
)

@Serializable
data class ExtensionHttp(
    val baseUrl: String,
    val userAgent: String? = null,
    val timeoutMs: Long = 10000L,
    val maxBytes: Long = 2097152L
)

@Serializable
data class ExtensionPaging(
    val type: String = "offset", // "offset" or "page"
    val pageSize: Int = 25
)

@Serializable
data class ExtensionDefinition(
    val schema: String = "vicsmusic-extension/1",
    val official: Boolean = true,
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val lang: List<String> = listOf("multi"),
    val status: String? = null,
    val legal: ExtensionLegal? = null,
    val auth: ExtensionAuth? = null,
    val http: ExtensionHttp,
    val capabilities: List<String> = emptyList(),
    val paging: ExtensionPaging? = null,
    val endpoints: Map<String, JsonElement> = emptyMap()
)
