package me.lgcode.ianua.service

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import me.lgcode.ianua.BuildConfig
import me.lgcode.ianua.R
import me.lgcode.ianua.rules.NodeSnapshot
import me.lgcode.ianua.rules.ScreenSnapshot
import java.io.File

/**
 * Debug builds only: captures the next gated-app screen as a rules/fixtures/android JSON file.
 * This is how fixtures are refreshed when YouTube changes its view ids.
 */
object DebugDump {
    private const val ARM_WINDOW_MS = 30_000L

    @Volatile
    private var armedUntil = 0L

    fun arm() {
        if (BuildConfig.DEBUG) armedUntil = SystemClock.elapsedRealtime() + ARM_WINDOW_MS
    }

    /** Called by the service for every evaluated screen; writes at most one dump per arming. */
    fun maybeDump(context: Context, packageName: String, root: NodeInfoScreenNode) {
        if (!BuildConfig.DEBUG || SystemClock.elapsedRealtime() > armedUntil) return
        armedUntil = 0
        val snapshot = ScreenSnapshot(packageName, NodeSnapshot.copyOf(root, { (it as NodeInfoScreenNode).info.className?.toString() }))
        val dir = File(context.getExternalFilesDir(null), "dumps").apply { mkdirs() }
        val file = File(dir, "${packageName.substringAfterLast('.')}_${System.currentTimeMillis()}.json")
        file.writeText(snapshot.toJson())
        Toast.makeText(context, context.getString(R.string.debug_dump_saved, file.name), Toast.LENGTH_LONG).show()
    }
}
