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
        val installedCode = runCatching {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(packageInfo).toInt()
        }.getOrDefault(currentVersionCode)

        return appUpdateManager.checkForUpdate(manifestUrl, installedCode)
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
