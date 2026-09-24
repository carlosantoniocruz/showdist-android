package com.showconfigs.showdist.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.showconfigs.showdist.measure.DistanceFormat
import com.showconfigs.showdist.measure.UnitSystem

/**
 * Share and Copy text for one or more recordings (SPEC.md §2.7). The text lives only in
 * memory for the action; nothing is written to storage. PDF is the only file export
 * (SPEC.md §2.12).
 */
object Export {
    /** Opens the share sheet with the text as `text/plain`. Returns a snackbar message on failure. */
    fun share(context: Context, recordings: List<Recording>, units: UnitSystem): String? = try {
        val subject = if (recordings.size == 1) {
            "Showdist: ${DistanceFormat.format(recordings[0].total, units)}"
        } else {
            "Showdist: ${recordings.size} measurements"
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, recordings.toShareText(units))
        }
        context.startActivity(Intent.createChooser(send, null))
        null
    } catch (e: Exception) {
        "Could not share: ${reason(e)}"
    }

    /** Puts the text on the clipboard. Returns the snackbar message either way. */
    fun copy(context: Context, recordings: List<Recording>, units: UnitSystem): String = try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Showdist", recordings.toShareText(units)))
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
