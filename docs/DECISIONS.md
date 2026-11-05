# Registro de Decisiones de Arquitectura (ADR)

* **ADR-0001**: Uso de Hilt como contenedor de inyección de dependencias para integración nativa con Jetpack Compose y AndroidX Media3.
* **ADR-0002**: Ubicación del proyecto establecida permanentemente en `C:\Proyectos\vicsmusic` con repositorio Git inicializado desde la Fase 0.
* **ADR-0003**: Configuración de `gradle/libs.versions.toml` como única fuente de verdad para dependencias y plugins.
* **ADR-0004**: Entorno de compilación configurado con Microsoft OpenJDK 17 y Android SDK oficial en `C:\Users\Admin\AppData\Local\Android\Sdk`, desacoplado completamente de Unity.
* **ADR-0005**: Base de datos local implementada con Room (`VicsmusicDatabase`, v1, schema exportado) con 10 entidades (`tracks`, `albums`, `artists`, `playlists`, `playlist_tracks`, `play_history`, `search_history`, `downloads`, `playback_queue`, `lyrics_cache`), consultas reactivas con `Flow` e índices en campos clave de búsqueda y orden.
* **ADR-0006**: Persistencia de configuración de usuario mediante AndroidX Preferences DataStore (`UserPreferencesDataStore`), gestionando tema (oscuro por defecto), calidad de audio diferenciada (Wi-Fi vs Celular), crossfade y última pista reproducida.
* **ADR-0007**: Arquitectura de fuentes de música desacoplada con `:source:api` (`MusicSource`, `SourceRegistry`) y primera implementación concreta en `:source:local` (`LocalMusicSource`) consultando `MediaStore.Audio.Media` con filtrado de duración (>=5s) y soporte Android 14 Scoped Storage.
