package com.vunbo.watchtogether.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.vunbo.watchtogether.core.config.UpdateConfig
import com.vunbo.watchtogether.core.network.OkHttpHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

object ApkDownloader {
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null), UpdateConfig.DOWNLOAD_DIR).apply {
            mkdirs()
        }
        val target = File(dir, "WatchTogether-${info.safeVersionName}.apk")
        val temp = File(dir, "${target.name}.download")
        if (temp.exists()) temp.delete()

        OkHttpHelper.getWithClient(info.downloadUrl, useJarClient = true).use { response ->
            if (!response.isSuccessful) {
                throw IOException("下载安装包失败：HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("安装包响应为空")
            val total = body.contentLength().takeIf { it > 0L } ?: info.apkSize
            body.byteStream().use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0L) {
                            onProgress((downloaded / total.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }

        val expectedSha256 = info.apkSha256?.trim().orEmpty()
        if (expectedSha256.isNotBlank()) {
            val actual = temp.sha256()
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                temp.delete()
                throw IOException("安装包校验失败，请重新下载")
            }
        }

        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        onProgress(1f)
        target
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun installApk(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(this).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte ->
            String.format(Locale.US, "%02x", byte)
        }
    }
}
