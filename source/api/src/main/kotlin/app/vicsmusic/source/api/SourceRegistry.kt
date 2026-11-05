package app.vicsmusic.source.api

import java.util.concurrent.ConcurrentHashMap

class SourceRegistry {
    private val sources = ConcurrentHashMap<String, MusicSource>()

    fun register(source: MusicSource) {
        sources[source.id] = source
    }

    fun unregister(sourceId: String) {
        sources.remove(sourceId)
    }

    fun getSource(id: String): MusicSource? {
        return sources[id]
    }

    fun getAllSources(): List<MusicSource> {
        return sources.values.toList()
    }
}
