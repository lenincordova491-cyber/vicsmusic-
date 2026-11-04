# Cómo Agregar una Nueva Fuente de Música

1. Crea un nuevo módulo bajo `:source:tunombre` implementando `app.vicsmusic.source.api.MusicSource`.
2. Declara las capacidades de tu fuente en `capabilities: Set<SourceCapability>`.
3. Implementa los métodos requeridos: `search`, `getTrack`, `getStream`, `getAlbum`, `getArtist`, `browseHome`.
4. Registra tu fuente en el `SourceRegistry` inyectado mediante Hilt.
5. El reproductor y la interfaz consumirán automáticamente las pistas entregadas sin necesidad de ajustes adicionales.
