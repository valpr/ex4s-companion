package com.valpr.bikecompanion.history

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Stages the newest BLE packet log under `cacheDir/packetlogs/` and fires an
 * `ACTION_SEND` share sheet so raw packet evidence can leave the phone
 * (messaging, email, Drive) for post-ride dropout analysis.
 */
object PacketLogShareHelper {
    const val STAGING_DIR = "packetlogs"
    fun shareLog(context: Context, file: File) {
        val dir = File(context.cacheDir, STAGING_DIR).apply { mkdirs() }
        val staged = File(dir, file.name)
        file.copyTo(staged, overwrite = true)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", staged)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share packet log (${file.name})")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
