package app.vicsmusic.core.data.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.vicsmusic.core.model.AppUpdateInfo
import app.vicsmusic.core.network.VicsHttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream



class AppUpdateManager(
    private val httpClient: HttpClient = VicsHttpClientFactory.create()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun checkForUpdate(
        manifestUrl: String,
        currentVersionCode: Int
    ): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val response: HttpResponse = httpClient.get(manifestUrl) {
                timeout {
                    requestTimeoutMillis = 6_000L
                    connectTimeoutMillis = 3_000L
                }
            }
            if (response.status == HttpStatusCode.OK) {
                val text = response.body<String>()
                val updateInfo = json.decodeFromString<AppUpdateInfo>(text)
                if (updateInfo.versionCode > currentVersionCode) {
                    updateInfo
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    suspend fun downloadApk(
        context: Context,
        updateInfo: AppUpdateInfo,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val response: HttpResponse = httpClient.get(updateInfo.downloadUrl) {
                timeout {
                    requestTimeoutMillis = 180_000L
                    connectTimeoutMillis = 10_000L
                }
            }
            if (response.status != HttpStatusCode.OK) {
                throw IllegalStateException("Servidor respondió con código HTTP ${response.status}")
            }

            val totalBytes = response.contentLength() ?: -1L
            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updateDir, "vicsmusic-v${updateInfo.versionName}.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val channel = response.bodyAsChannel()
            var downloadedBytes = 0L

            FileOutputStream(apkFile).use { output ->
                while (!channel.isClosedForRead) {
                    val packet = channel.readRemaining(8192L)
                    while (!packet.isEmpty) {
                        val bytes = packet.readBytes()
                        output.write(bytes)
                        downloadedBytes += bytes.size
                        if (totalBytes > 0) {
                            val progress = downloadedBytes.toFloat() / totalBytes.toFloat()
                            onProgress(progress.coerceIn(0f, 1f))
                        }
                    }
                }
            }
            onProgress(1f)
            apkFile
        }
    }

    fun launchInstaller(context: Context, apkFile: File) {
        val authority = "${context.packageName}.fileprovider"
        val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
