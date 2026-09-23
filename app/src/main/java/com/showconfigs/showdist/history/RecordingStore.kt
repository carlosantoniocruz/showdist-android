package com.showconfigs.showdist.history

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

private const val TAG = "RecordingStore"
private const val FILE = "recordings.json"

/**
 * Saved recordings, newest first (SPEC.md §2.7), in `recordings.json` in the app's private
 * files. [recordings] is snapshot state, so the menu link, the Measure badge and History
 * update as soon as it changes. Changes happen on the main thread; the file is written on a
 * single background writer, atomically (a temp file renamed over the original), in order.
 */
object RecordingStore {
    val recordings: SnapshotStateList<Recording> = mutableStateListOf()

    private val io = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var file: File? = null
    private var loaded = false
    private var saveAfterLoad = false

    /** Loads the file once per process. Safe to call from every activity start. */
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, FILE)
        file = f
        scope.launch {
            val fromFile = withContext(io) { read(f) }
            // Anything saved before the load finished is newer, so it stays in front.
            recordings.addAll(fromFile)
            loaded = true
            if (saveAfterLoad) save()
        }
    }

    fun add(recording: Recording) {
        recordings.add(0, recording)
        save()
    }

    fun remove(ids: Set<String>) {
        recordings.removeAll { it.id in ids }
        save()
    }

    fun clear() {
        recordings.clear()
        save()
    }

    private fun save() {
        val f = file ?: return
        // Writing before the load finishes would drop the older recordings from the file.
        if (!loaded) {
            saveAfterLoad = true
            return
        }
        val json = JSONArray().apply {
            for (r in recordings) {
                put(
                    JSONObject()
                        .put("id", r.id)
                        .put("createdAt", r.createdAt)
                        .put("points", JSONArray().apply { r.points.forEach { p -> put(JSONArray(p.toList())) } }),
                )
            }
        }.toString()
        scope.launch(io) {
            runCatching {
                val tmp = File(f.parentFile, "$FILE.tmp")
                FileOutputStream(tmp).use { out ->
                    out.write(json.toByteArray())
                    out.fd.sync()
                }
                if (!tmp.renameTo(f)) error("rename failed")
            }.onFailure { Log.e(TAG, "Could not save recordings", it) }
        }
    }

    /** Reads the file; if it can't be parsed, moves it aside as `.corrupt` and starts empty. */
    private fun read(f: File): List<Recording> {
        if (!f.exists()) return emptyList()
        return try {
            val array = JSONArray(f.readText())
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                val pts = o.getJSONArray("points")
                Recording(
                    id = o.getString("id"),
                    createdAt = o.getString("createdAt"),
                    points = List(pts.length()) { j ->
                        val p = pts.getJSONArray(j)
                        doubleArrayOf(p.getDouble(0), p.getDouble(1), p.getDouble(2))
                    },
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "recordings.json is unreadable; starting empty", e)
            f.renameTo(File(f.parentFile, "$FILE.corrupt"))
            emptyList()
        }
    }
}
