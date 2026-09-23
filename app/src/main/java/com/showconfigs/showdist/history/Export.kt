package com.showconfigs.showdist.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.showconfigs.showdist.measure.DistanceFormat
import com.showconfigs.showdist.measure.UnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Share .txt and Copy text for one or more recordings (SPEC.md §2.7). */
object Export {
    /** Writes the text to `<cache>/exports/` and opens the share sheet. Returns a snackbar message on failure. */
    suspend fun share(context: Context, recordings: List<Recording>, units: UnitSystem): String? = try {
        val single = recordings.size == 1
        val text = if (single) recordings[0].toText(units) else recordings.toBulkText(units)
        val name = if (single) {
            "showdist-${recordings[0].fileTime}.txt"
        } else {
            "showdist-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}-${recordings.size}.txt"
        }
        val file = withContext(Dispatchers.IO) {
            // Old exports are removed each time.
            val dir = File(context.cacheDir, "exports").apply { deleteRecursively(); mkdirs() }
            File(dir, name).apply { writeText(text) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (single) {
                putExtra(Intent.EXTRA_SUBJECT, "Showdist: ${DistanceFormat.format(recordings[0].total, units)}")
                putExtra(Intent.EXTRA_TEXT, text)
            } else {
                putExtra(Intent.EXTRA_SUBJECT, "Showdist: ${recordings.size} measurements")
            }
            clipData = ClipData.newRawUri(name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null))
        null
    } catch (e: Exception) {
        "Could not share: ${reason(e)}"
    }

    /** Puts the text on the clipboard. Returns the snackbar message either way. */
    fun copy(context: Context, recordings: List<Recording>, units: UnitSystem): String = try {
        val text = if (recordings.size == 1) recordings[0].toText(units) else recordings.toBulkText(units)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Showdist", text))
        "Copied to clipboard"
    } catch (e: Exception) {
        "Could not copy: ${reason(e)}"
    }

    /** First line of the error, cut to 117 characters plus "..." if longer than 120. */
    private fun reason(e: Exception): String {
        val line = (e.message ?: e.javaClass.simpleName).lineSequence().first()
        return if (line.length > 120) line.take(117) + "..." else line
    }
}
