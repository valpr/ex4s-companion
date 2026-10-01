package com.valpr.bikecompanion.history

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Stages a TCX document under `cacheDir/tcx/` and fires an `ACTION_SEND`
 * share sheet (Strava, Garmin Connect, Intervals.icu, TrainingPeaks).
 */
object TcxShareHelper {
    fun shareRide(context: Context, ride: CompletedRide) {
        stageAndShare(context, TcxExporter.fileNameFor(ride), TcxExporter.export(ride), ride.workoutName)
    }

    fun shareSummary(context: Context, summary: com.valpr.bikecompanion.workout.WorkoutSummary) {
        val fileName = TcxExporter.fileNameFor(
            CompletedRide.fromSummary(summary)
        )
        stageAndShare(context, fileName, TcxExporter.export(summary), summary.workoutName)
    }

    private fun stageAndShare(context: Context, fileName: String, tcx: String, workoutName: String) {
        val dir = File(context.cacheDir, "tcx").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(tcx, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.garmin.tcx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Export $workoutName (.tcx)")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
