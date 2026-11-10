package app.vicsmusic.core.domain.repository

import app.vicsmusic.core.model.AppUpdateInfo
import java.io.File

interface AppUpdateRepository {
    suspend fun checkForUpdate(manifestUrl: String, currentVersionCode: Int): Result<AppUpdateInfo?>
    suspend fun downloadApk(updateInfo: AppUpdateInfo, onProgress: (Float) -> Unit): Result<File>
    fun launchInstaller(apkFile: File)
}
