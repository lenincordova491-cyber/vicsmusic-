# Sistema de Extensiones Declarativas (.JSON) — Vicsmusic

Vicsmusic utiliza un sistema de extensiones declarativas en formato JSON (`vicsmusic-extension/1`), lo que permite integrar fuentes de música externas, radios, preescuchas y letras sincronizadas sin compilar APKs adicionales ni ejecutar código dinámico inseguro.

---

## 1. Estructura de Directorios

Las extensiones incluidas con la aplicación se encuentran en:
```
app/src/main/assets/extensions/
├── index.json               # Índice general de extensiones disponibles
├── jamendo.json             # Música libre de derechos (Creative Commons)
├── somafm.json              # Canales de radio online SomaFM
├── audius.json              # Plataforma descentralizada Audius
├── internet-archive.json    # Grabaciones de dominio público en Internet Archive
├── openverse-audio.json     # Catálogo abierto Openverse
├── radio-browser.json       # Miles de emisoras de radio comunitarias del mundo
├── itunes-preview.json      # Búsqueda y fragmentos de 30s de iTunes
├── deezer-preview.json      # Búsqueda y preescuchas de Deezer
├── ccmixter.json            # Remixes y samples de ccMixter
├── subsonic.json            # Servidores auto-alojados (Navidrome, Jellyfin, Subsonic)
├── lrclib.json              # Letras sincronizadas LRC
└── musicbrainz.json         # Metadatos abiertos de música
```

---

## 2. Esquema de Extensión (`vicsmusic-extension/1`)

Cada archivo `.json` define de forma declarativa cómo comunicarse con la API de la fuente:

```json
{
  "schema": "vicsmusic-extension/1",
  "official": true,
  "id": "mi-fuente",
  "name": "Mi Fuente Musical",
  "version": "1.0.0",
  "lang": ["multi"],
  "status": "activo",
  "legal": {
    "license": "Creative Commons / Dominio Público",
    "notice": "Aviso legal mostrado al usuario al activar la extensión.",
    "docs": "https://api.mifuente.org"
  },
  "auth": {
    "type": "none" // Opciones: "none", "apiKey", "bearer", "basic", "subsonic"
  },
  "http": {
    "baseUrl": "https://api.mifuente.org/v1",
    "userAgent": "Vicsmusic/{{appVersion}}",
    "timeoutMs": 10000,
    "maxBytes": 2097152
  },
  "capabilities": ["SEARCH", "STREAM", "BROWSE"],
  "paging": {
    "type": "offset", // o "page"
    "pageSize": 25
  },
  "endpoints": {
    "searchTracks": {
      "method": "GET",
      "path": "/tracks",
      "query": {
        "q": "{{query}}",
        "limit": "{{limit}}",
        "offset": "{{offset}}"
      },
      "response": {
        "itemsPath": "$.results",
        "map": {
          "id": "$.id",
          "title": "$.title",
          "artist": "$.artist_name",
          "album": "$.album_name",
          "durationMs": { "path": "$.duration_sec", "mul": 1000 },
          "artworkUrl": "$.cover_image",
          "streamUrl": "$.stream_audio"
        }
      }
    }
  }
}
```

---

## 3. Reglas de Seguridad Obligatorias

1. **Protocolo seguro HTTPS:** Solo se permiten URLs bajo protocolo `https://`.
2. **Sin ejecución de código arbitrario:** Toda la lógica es declarativa. No se permite ejecución de scripts JavaScript, eval, ni reflection.
3. **Aislamiento de la aplicación:** Las extensiones no tienen acceso al sistema de archivos del dispositivo, base de datos interna ni tokens privados de Android.
4. **Protección contra DoS:** Se aplican tiempos de espera estrictos (`timeoutMs`), límites de tamaño de descarga (`maxBytes`) y rate-limiting por host.
