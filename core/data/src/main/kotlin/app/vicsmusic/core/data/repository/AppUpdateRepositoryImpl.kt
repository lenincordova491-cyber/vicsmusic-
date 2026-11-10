package app.vicsmusic.core.data.repository

import android.content.Context
import app.vicsmusic.core.data.updater.AppUpdateManager
import app.vicsmusic.core.domain.repository.AppUpdateRepository
import app.vicsmusic.core.model.AppUpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepositoryImpl @Inject constructor(
    private val appUpdateManager: AppUpdateManager,
    @ApplicationContext private val context: Context
) : AppUpdateRepository {

    override suspend fun checkForUpdate(
        manifestUrl: String,
        currentVersionCode: Int
    ): Result<AppUpdateInfo?> {
        return appUpdateManager.checkForUpdate(manifestUrl, currentVersionCode)
    }

    override suspend fun downloadApk(
        updateInfo: AppUpdateInfo,
        onProgress: (Float) -> Unit
    ): Result<File> {
        return appUpdateManager.downloadApk(context, updateInfo, onProgress)
    }

    override fun launchInstaller(apkFile: File) {
        appUpdateManager.launchInstaller(context, apkFile)
    }
}
