package app.vicsmusic.core.data.metadata

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Inyector autónomo de etiquetas ID3v2.4 estándar para archivos MP3 sin dependencias externas.
 * Incorpora frames oficiales:
 * - TIT2: Título de la pista (UTF-8)
 * - TPE1: Artista principal (UTF-8)
 * - TALB: Nombre del álbum (UTF-8)
 * - APIC: Carátula frontal (Front Cover) embebida en alta resolución (JPEG/PNG)
 */
object Id3Tagger {

    fun tagMp3(
        file: File,
        title: String,
        artist: String,
        album: String?,
        artworkUrl: String?
    ) {
        if (!file.exists() || file.length() == 0L) return

        runCatching {
            val artworkBytes = artworkUrl?.let { fetchArtworkBytes(it) }
            val id3HeaderAndFrames = buildId3v24Tag(
                title = title,
                artist = artist,
                album = album ?: "Sencillo",
                artworkBytes = artworkBytes
            )

            // Detectar si el archivo ya contenía una cabecera ID3v2 previa para reemplazarla limpiamente
            val existingId3Size = detectExistingId3v2Size(file)

            val tempFile = File(file.parentFile, "${file.name}.tmp")
            FileOutputStream(tempFile).use { out ->
                out.write(id3HeaderAndFrames)
                FileInputStream(file).use { input ->
                    if (existingId3Size > 0) {
                        input.skip(existingId3Size.toLong())
                    }
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                }
            }

            if (tempFile.exists() && tempFile.length() > id3HeaderAndFrames.size) {
                if (file.delete()) {
                    tempFile.renameTo(file)
                } else {
                    tempFile.delete()
                }
            }
        }
    }

    private fun detectExistingId3v2Size(file: File): Int {
        if (file.length() < 10) return 0
        FileInputStream(file).use { input ->
            val header = ByteArray(10)
            if (input.read(header) != 10) return 0
            if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                val size = ((header[6].toInt() and 0x7F) shl 21) or
                    ((header[7].toInt() and 0x7F) shl 14) or
                    ((header[8].toInt() and 0x7F) shl 7) or
                    (header[9].toInt() and 0x7F)
                return size + 10
            }
        }
        return 0
    }

    private fun buildId3v24Tag(
        title: String,
        artist: String,
        album: String,
        artworkBytes: ByteArray?
    ): ByteArray {
        val framesOut = ByteArrayOutputStream()

        // TIT2 (Título)
        writeTextFrame(framesOut, "TIT2", title)
        // TPE1 (Artista)
        writeTextFrame(framesOut, "TPE1", artist)
        // TALB (Álbum)
        writeTextFrame(framesOut, "TALB", album)

        // APIC (Attached Picture)
        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            writePictureFrame(framesOut, artworkBytes)
        }

        val framesData = framesOut.toByteArray()
        val tagSize = framesData.size

        val id3Header = ByteArray(10)
        id3Header[0] = 'I'.code.toByte()
        id3Header[1] = 'D'.code.toByte()
        id3Header[2] = '3'.code.toByte()
        id3Header[3] = 0x04 // ID3v2.4
        id3Header[4] = 0x00
        id3Header[5] = 0x00 // Flags

        // Syncsafe integer para el tamaño total de los frames
        id3Header[6] = ((tagSize shr 21) and 0x7F).toByte()
        id3Header[7] = ((tagSize shr 14) and 0x7F).toByte()
        id3Header[8] = ((tagSize shr 7) and 0x7F).toByte()
        id3Header[9] = (tagSize and 0x7F).toByte()

        val fullOut = ByteArrayOutputStream()
        fullOut.write(id3Header)
        fullOut.write(framesData)
        return fullOut.toByteArray()
    }

    private fun writeTextFrame(out: ByteArrayOutputStream, frameId: String, text: String) {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val payloadSize = 1 + textBytes.size // 1 byte de encoding (0x03 UTF-8) + texto

        // Frame Header
        out.write(frameId.toByteArray(Charsets.US_ASCII))
        out.write(toSyncsafe(payloadSize))
        out.write(0x00) // Flag 1
        out.write(0x00) // Flag 2

        // Payload
        out.write(0x03) // UTF-8 encoding
        out.write(textBytes)
    }

    private fun writePictureFrame(out: ByteArrayOutputStream, imageBytes: ByteArray) {
        val isPng = imageBytes.size >= 8 &&
            imageBytes[0] == 0x89.toByte() &&
            imageBytes[1] == 0x50.toByte() &&
            imageBytes[2] == 0x4E.toByte()
        val mimeType = if (isPng) "image/png" else "image/jpeg"
        val mimeBytes = mimeType.toByteArray(Charsets.US_ASCII)

        // Encoding(1) + MIME(len + 1 null) + PictureType(1) + Description(1 null) + ImageBytes
        val payloadSize = 1 + (mimeBytes.size + 1) + 1 + 1 + imageBytes.size

        out.write("APIC".toByteArray(Charsets.US_ASCII))
        out.write(toSyncsafe(payloadSize))
        out.write(0x00)
        out.write(0x00)

        out.write(0x03) // UTF-8 encoding
        out.write(mimeBytes)
        out.write(0x00) // null-terminated MIME
        out.write(0x03) // Picture type: Cover (front)
        out.write(0x00) // null-terminated description (empty)
        out.write(imageBytes)
    }

    private fun toSyncsafe(value: Int): ByteArray {
        return byteArrayOf(
            ((value shr 21) and 0x7F).toByte(),
            ((value shr 14) and 0x7F).toByte(),
            ((value shr 7) and 0x7F).toByte(),
            (value and 0x7F).toByte()
        )
    }

    private fun fetchArtworkBytes(urlStr: String): ByteArray? {
        return runCatching {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { it.readBytes() }
            } else null
        }.getOrNull()
    }
}
