package app.vicsmusic.source.remote.declarative

import android.content.Context
import app.vicsmusic.core.model.ExtensionItem
import app.vicsmusic.source.api.SourceRegistry
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import java.io.InputStreamReader

class DeclarativeExtensionManager(
    private val context: Context,
    private val httpClient: HttpClient,
    private val sourceRegistry: SourceRegistry
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val loadedSources = mutableListOf<JsonMusicSource>()
    private val extensionItems = mutableListOf<ExtensionItem>()

    init {
        loadAllExtensions()
    }

    private fun loadAllExtensions() {
        runCatching {
            val assetManager = context.assets
            val indexStream = assetManager.open("extensions/index.json")
            val indexText = InputStreamReader(indexStream).use { it.readText() }
            val index = json.decodeFromString<ExtensionIndex>(indexText)

            for (entry in index.extensions) {
                runCatching {
                    val stream = assetManager.open("extensions/${entry.file}")
                    val text = InputStreamReader(stream).use { it.readText() }
                    val definition = json.decodeFromString<ExtensionDefinition>(text)

                    val source = JsonMusicSource(definition, httpClient)
                    loadedSources.add(source)
                    sourceRegistry.register(source)

                    val item = ExtensionItem(
                        id = definition.id,
                        name = definition.id,
                        displayName = definition.name,
                        version = definition.version,
                        description = definition.legal?.notice ?: "${definition.name} extension",
                        category = entry.types.firstOrNull() ?: "music",
                        tags = definition.capabilities
                    )
                    extensionItems.add(item)
                }
            }
        }
    }

    fun getAvailableExtensions(): List<ExtensionItem> = extensionItems.toList()
    fun getLoadedSources(): List<JsonMusicSource> = loadedSources.toList()
}
