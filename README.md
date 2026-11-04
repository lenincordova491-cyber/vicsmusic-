# Vicsmusic 🎵

Vicsmusic es un reproductor y cliente de streaming de música moderno, ligero y 100% libre de anuncios para Android.

## Características Principales
* **Cero Publicidad:** Experiencia pura, sin interrupciones, sin modelos de suscripción abusivos.
* **Arquitectura de Fuentes Modulares (Plugins):** Soporte desacoplado para biblioteca local (`MediaStore`) y streaming legal (`Jamendo API`), ampliable a cualquier fuente sin tocar la interfaz ni el reproductor.
* **Audio en Segundo Plano:** Basado en AndroidX Media3 (ExoPlayer), control en pantalla de bloqueo, auriculares y Android Auto.
* **Diseño Exquisito:** Jetpack Compose + Material 3, tema oscuro obsidian por defecto con soporte para Material You dinámico.

## Estructura de Módulos (Clean Architecture)
```
:app                      → Application, Activity única, navegación raíz, DI root
:core:common              → Utilidades, Result, dispatchers, extensiones
:core:model               → Modelos de dominio puros (sin Android)
:core:domain              → Casos de uso e interfaces de repositorio
:core:data                → Implementaciones de repositorios y mappers
:core:database            → Room (entidades, DAOs, migraciones)
:core:network             → Cliente HTTP Ktor y serialización
:core:datastore           → Preferencias de usuario
:core:player              → Servicio Media3, cola, sesión, ecualizador
:core:designsystem        → Tema, tipografía, componentes base reutilizables
:core:ui                  → Componentes compartidos
:source:api               → Interfaz MusicSource y contratos de plugins
:source:local             → LocalSource (MediaStore)
:source:remote            → RemoteSource (API configurable)
:feature:*                → Módulos de pantalla (home, search, library, player, etc.)
```

## Compilación e Instalación
Requisitos:
* JDK 17 (oficial de Android Studio / JetBrains Runtime)
* Android SDK (API 35)

```bash
./gradlew assembleDebug
```

## Licencia
Distribuido bajo la licencia [MIT](LICENSE).
