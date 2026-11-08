# Big Music 🎧 (Vicsmusic Engine)

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-M3-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/AndroidX-Media3%20ExoPlayer-FF6F00?logo=googleplay&logoColor=white)](https://developer.android.com/media/media3)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**Big Music** es un cliente de streaming y reproductor de música de código abierto para Android, diseñado con una experiencia visual obsidian idéntica a Spotify, sin anuncios, con sonido de alta definición y conexión directa con el catálogo global de YouTube Music.

---

## 🌟 Características Principales

* 🚫 **100% Libre de Publicidad:** Reproducción limpia y continua sin interrupciones ni suscripciones de pago.
* 🎵 **Streaming Global con YouTube Music:** Acceso ilimitado a millones de canciones oficiales, acústicos, directos, covers y cumbia regional mediante la API de Innertube.
* 🏆 **Rankings y Listas de Éxitos Reales:** Carruseles dinámicos con el *Top 50 - Perú*, *Top 50 - Global*, *Virales* y *Viva Latino* con vista detallada de 50 temas clasificados por puesto.
* 🎤 **Perfiles de Artistas Interactivos:** Carrusel *"Tus artistas favoritos"* (Canserbero, Ke Personajes, Agua Marina, Grupo 5, Coldplay, Bad Bunny, etc.) con biografías, botón Seguir y canciones más reproducidas.
* 🔍 **Búsqueda Avanzada e Historial Persistente:** Filtrado rápido por canciones, artistas y playlists con guardado automático en base de datos local Room y borrado individual.
* 🎛️ **Audio en Segundo Plano Profesional:** Motor basado en AndroidX Media3 (ExoPlayer), compatible con auriculares Bluetooth, pantalla de bloqueo y notificaciones multimedia interactivas.
* ⚡ **Arquitectura Reactiva y Rendimiento:** Carga asíncrona de portadas con Coil 3, monogramas elegantes de respaldo sin espacios en blanco y Pull-to-Refresh.
* 🔄 **Actualizaciones OTA Remotas:** Soporte nativo para comprobación de nuevas versiones mediante configuración en `update.json`.

---

## 🏗️ Arquitectura Modular (Clean Architecture)

El proyecto sigue los principios de arquitectura limpia de Google y buenas prácticas de modularización en Android:

```
bigmusic/
├── app/                  # Application, Single Activity, NavHost y Dependency Injection root
├── core/
│   ├── model/            # Modelos de dominio puros e inmutables (Track, Artist, Playlist)
│   ├── domain/           # Casos de uso (SearchTracks, GetPlayHistory, MusicRepository)
│   ├── data/             # Implementaciones de repositorios, sincronización y mediadores
│   ├── database/         # Persistencia local Room (DAOs de historial, descargas y pistas)
│   ├── datastore/        # Preferencias de usuario y configuración
│   ├── network/          # Clientes HTTP Ktor y llamadas seguras
│   ├── player/           # MediaSessionService, gestión de cola, reproducción en background
│   ├── designsystem/     # Paleta de colores Obsidian, tipografías y tema Material 3
│   └── ui/               # Componentes UI reutilizables (ArtworkImage, TrackRow, etc.)
├── source/
│   ├── api/              # Interfaces y contratos de fuentes de música
│   ├── local/            # Lector de almacenamiento interno MediaStore
│   └── remote/           # Integración con YouTube Music Innertube y extensiones
├── feature/
│   ├── home/             # Pantalla de inicio, carruseles de artistas, charts y detalle
│   ├── search/           # Búsqueda instantánea, catálogo y gestión de historial
│   ├── library/          # Listas guardadas, canciones favoritas y descargas
│   ├── player/           # Reproductor a pantalla completa, cola y lyrics sincronizadas
│   └── onboarding/       # Inicialización de preferencias
└── update.json           # Manifiesto de actualizaciones remotas (OTA)
```

---

## 🛠️ Guía de Instalación y Compilación

### Requisitos Previos
1. **JDK 17:** [OpenJDK 17](https://adoptium.net) o JetBrains Runtime incluido en Android Studio.
2. **Android SDK:** Nivel de API 35 (mínimo soportado: Android 8.0, API 26).
3. **Android Studio:** Ladybug / Koala o superior (opcional si se usa línea de comandos).

### Compilar desde Terminal

```bash
# Clonar el repositorio
git clone https://github.com/TU_USUARIO/BigMusic.git
cd BigMusic

# Generar el instalador APK en modo Debug
./gradlew assembleDebug
```

El archivo ejecutable generado se encontrará en:
`app/build/outputs/apk/debug/app-debug.apk`

### Instalar directamente en un teléfono conectado (vía ADB):
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🚀 Cómo Subir este Proyecto a tu Cuenta de GitHub

Sigue estos 3 sencillos pasos desde la terminal de tu computadora:

1. **Crea un nuevo repositorio en GitHub:**
   * Entra a [github.com/new](https://github.com/new)
   * Nómbralo: `BigMusic` (o el nombre que prefieras).
   * Deja la opción **Public** o **Private** seleccionada y **no** marques "Initialize with README" (ya lo tenemos creado aquí).

2. **Vincula tu repositorio local con tu cuenta:**
   ```bash
   cd C:\Proyectos\vicsmusic
   git remote add origin https://github.com/TU_USUARIO_GITHUB/BigMusic.git
   ```

3. **Sube todo el código:**
   ```bash
   git branch -M master
   git push -u origin master
   ```

*(Si utilizas autenticación por SSH, cambia la URL a `git@github.com:TU_USUARIO_GITHUB/BigMusic.git`)*

---

## 🔄 Sistema de Actualizaciones OTA (`update.json`)

Para lanzar una actualización sin obligar a los usuarios a pasar por tiendas cerradas, edita el archivo `update.json` en la rama principal o en tu servidor:

```json
{
  "app_name": "Big Music",
  "latest_version": "1.1.0",
  "version_code": 2,
  "download_url": "https://github.com/TU_USUARIO/BigMusic/releases/latest/download/app-debug.apk",
  "release_notes": {
    "es": [
      "✨ Vista detallada en rankings y listas de éxitos.",
      "🎤 Perfiles interactivos para artistas favoritos."
    ]
  },
  "mandatory": false
}
```

---

## 📄 Licencia

Distribuido bajo la licencia [MIT](LICENSE). Libre para uso personal, modificación y distribución sin restricciones.
