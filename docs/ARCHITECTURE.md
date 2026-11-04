# Arquitectura de Vicsmusic

Vicsmusic sigue los principios de **Clean Architecture** y arquitectura modular por capas para Android:

## 1. Reglas de Dependencias
* Las capas exteriores dependen de las interiores: `UI -> Domain <- Data`.
* El módulo `:core:domain` es agnóstico del framework Android (puro Kotlin JVM).
* Los módulos de características (`feature:*`) dependen exclusivamente de `:core:domain`, `:core:designsystem` y `:core:ui`. Nunca se comunican entre sí directamente ni dependen de `:core:data`.

## 2. Flujo Unidireccional de Datos (UDF)
Cada pantalla sigue el patrón MVVM/MVI ligero:
* `UiState`: Representa el estado inmutable de la pantalla.
* `UiEvent`: Acciones del usuario enviadas al `ViewModel`.
* `UiEffect`: Efectos secundarios de disparo único (navegación, snackbars) emitidos por un `Channel`.

## 3. Desacoplamiento de Fuentes de Música (`:source:api`)
Toda fuente de datos musicales implementa la interfaz `MusicSource`:
```kotlin
interface MusicSource {
    val id: String
    val displayName: String
    val capabilities: Set<SourceCapability>
    ...
}
```
Esto permite intercambiar o agregar proveedores de contenido sin modificar una sola línea de código en la UI o en el motor de reproducción.
