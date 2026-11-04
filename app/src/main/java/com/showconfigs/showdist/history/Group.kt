package com.showconfigs.showdist.history

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.showconfigs.showdist.measure.DistanceFormat
import com.showconfigs.showdist.measure.UnitSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

/**
 * One standalone line in a group (SPEC.md §2.13): its own two world points (metres, as
 * ARCore placed them), the calibration [scale] in force when it was measured, and an
 * optional short [label] such as "North wall". [tag] is set in §2.14.
 */
class GroupLine(
    val id: String,
    val a: DoubleArray,
    val b: DoubleArray,
    val scale: Double = 1.0,
    val label: String? = null,
    val tag: LineTag? = null,
) {
    val metres: Double = sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2])) * scale

    fun with(label: String? = this.label, tag: LineTag? = this.tag) = GroupLine(id, a, b, scale, label, tag)
}

/** What a group line measures, for area and volume (SPEC.md §2.14). */
enum class LineTag(val label: String) { Length("Length"), Width("Width"), Height("Height") }

/**
 * A group (SPEC.md §2.13): separate lines that belong to one thing, such as a room, a table
 * or a cabinet, with a [name]. [id] is the creation time in microseconds, like a Recording's,
 * so the two sort together in History.
 */
class MeasureGroup(
    val id: String,
    val name: String,
    val createdAt: String,
    val lines: List<GroupLine>,
    val unit: UnitSystem? = null,
) {
    /** "YYYY-MM-DD HH:MM". */
    val headingTime: String get() = createdAt.take(16).replace('T', ' ')

    /** "Mar 3, 2026", for the History row. */
    val shortDate: String get() = runCatching {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(createdAt.take(10))!!
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(d)
    }.getOrDefault(createdAt.take(10))

    fun copy(name: String = this.name, lines: List<GroupLine> = this.lines) = MeasureGroup(id, name, createdAt, lines, unit)

    /** "Line 3" when it has no label. */
    fun lineName(index: Int): String = lines[index].label ?: "Line ${index + 1}"

    companion object {
        fun create(name: String, lines: List<GroupLine>, unit: UnitSystem): MeasureGroup {
            val micros = newMicros()
            val base = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date(micros / 1000))
            return MeasureGroup(micros.toString(), name, base + (micros % 1000).toString().padStart(3, '0'), lines, unit)
        }

        fun newLineId(): String = newMicros().toString()

        private var lastMicros = 0L

        /** Microseconds since the epoch, strictly increasing within the process. */
        @Synchronized
        private fun newMicros(): Long {
            val m = maxOf(System.currentTimeMillis() * 1000 + (System.nanoTime() / 1000) % 1000, lastMicros + 1)
            lastMicros = m
            return m
        }
    }
}

/** Plain text for Copy and Share (SPEC.md §2.7, §2.13). */
fun MeasureGroup.toShareText(units: UnitSystem): String = buildString {
    append("$name · $headingTime\n")
    append("Group of ${lines.size} line${if (lines.size == 1) "" else "s"}\n")
    lines.forEachIndexed { i, l -> append("${i + 1}. ${lineName(i)}: ${DistanceFormat.format(l.metres, units)}\n") }
}.trimEnd()

private const val TAG = "GroupStore"
private const val FILE = "groups.json"

/**
 * Saved groups, newest first (SPEC.md §2.13), in `groups.json`, kept exactly like
 * [RecordingStore]: snapshot state, changes on the main thread, the file written atomically
 * on a single background writer.
 */
object GroupStore {
    val groups: SnapshotStateList<MeasureGroup> = mutableStateListOf()

    private val io = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var file: File? = null
    private var loaded = false
    private var saveAfterLoad = false

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, FILE)
        file = f
        scope.launch {
            val fromFile = withContext(io) { read(f) }
            groups.addAll(fromFile)
            loaded = true
            if (saveAfterLoad) save()
        }
    }

    fun byId(id: String?): MeasureGroup? = groups.firstOrNull { it.id == id }

    fun add(group: MeasureGroup) {
        groups.add(0, group)
        save()
    }

    /** Replaces the group with the same id (rename, edit lines, add lines). */
    fun update(group: MeasureGroup) {
        val i = groups.indexOfFirst { it.id == group.id }
        if (i < 0) return
        groups[i] = group
        save()
    }

    fun remove(ids: Set<String>) {
        if (groups.removeAll { it.id in ids }) save()
    }

    fun clear() {
        groups.clear()
        save()
    }

    private fun save() {
        val f = file ?: return
        if (!loaded) {
            saveAfterLoad = true
            return
        }
        val json = JSONArray().apply {
            for (g in groups) {
                put(
                    JSONObject()
                        .put("id", g.id)
                        .put("name", g.name)
                        .put("createdAt", g.createdAt)
                        .apply { g.unit?.let { put("unit", it.name) } }
                        .put(
                            "lines",
                            JSONArray().apply {
                                for (l in g.lines) {
                                    put(
                                        JSONObject()
                                            .put("id", l.id)
                                            .put("a", JSONArray(l.a.toList()))
                                            .put("b", JSONArray(l.b.toList()))
                                            .apply { if (l.scale != 1.0) put("scale", l.scale) }
                                            .apply { l.label?.let { put("label", it) } }
                                            .apply { l.tag?.let { put("tag", it.name) } },
                                    )
                                }
                            },
                        ),
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
            }.onFailure { Log.e(TAG, "Could not save groups", it) }
        }
    }

    private fun read(f: File): List<MeasureGroup> {
        if (!f.exists()) return emptyList()
        return try {
            val array = JSONArray(f.readText())
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                val ls = o.getJSONArray("lines")
                MeasureGroup(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    createdAt = o.getString("createdAt"),
                    unit = o.optString("unit").let { u -> UnitSystem.entries.firstOrNull { it.name == u } },
                    lines = List(ls.length()) { j ->
                        val l = ls.getJSONObject(j)
                        fun pt(key: String) = l.getJSONArray(key).let { doubleArrayOf(it.getDouble(0), it.getDouble(1), it.getDouble(2)) }
                        GroupLine(
                            id = l.getString("id"),
                            a = pt("a"),
                            b = pt("b"),
                            scale = l.optDouble("scale", 1.0),
                            label = if (l.has("label")) l.getString("label") else null,
                            tag = l.optString("tag").let { t -> LineTag.entries.firstOrNull { it.name == t } },
                        )
                    },
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "groups.json is unreadable; starting empty", e)
            f.renameTo(File(f.parentFile, "$FILE.corrupt"))
            emptyList()
        }
    }
}
