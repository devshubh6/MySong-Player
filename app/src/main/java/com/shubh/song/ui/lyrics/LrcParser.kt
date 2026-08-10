package com.shubh.song.ui.lyrics

import com.shubh.song.data.model.LyricsLine
import java.util.regex.Pattern

/**
 * Production-grade LRC (LyRiCs) parser for karaoke rendering.
 *
 * Handles the full subset of the LRC format used by the LRC Lib corpus:
 *  - multiple time tags per line ("[00:12.00][00:15.00]line") — expanded
 *    into one [LyricsLine] per tag so every occurrence lights up in sync;
 *  - timestamps in "mm:ss", "mm:ss.x", "mm:ss.xx" and "mm:ss.xxx" forms
 *    (centiseconds and milliseconds both supported, minutes may exceed 59);
 *  - metadata tags "[ti:…]", "[ar:…]", "[al:…]", "[by:…]", "[re:…]",
 *    "[ve:…]", "[length:…]" — skipped from rendering;
 *  - the global "[offset:±ms]" tag, applied to every line uniformly
 *    (positive = shift timestamps forward);
 *  - "hh:mm:ss.xx" hour-prefixed times;
 *  - non-timestamped trailing lines (instrumental filler, credits) kept at
 *    the end with [LyricsLine.timeMs] = [Long.MAX_VALUE] so they render but
 *    are never highlighted.
 *
 * Output is sorted ascending; the caller renders it with the karaoke
 * [androidx.recyclerview.widget.RecyclerView] adapter.
 */
object LrcParser {

    private val TIME_TAG = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,2}))?]")
    private val META_TAG = Pattern.compile("^\\[([a-zA-Z]+):([^\\]]*)]")
    private val HOUR_TAG = Pattern.compile("^\\[(\\d{1,3}):(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,2}))?]")

    /**
     * Parses raw LRC text into a sorted, offset-adjusted line list.
     * Returns an empty list when there is nothing renderable.
     */
    fun parse(raw: String?): List<LyricsLine> {
        if (raw.isNullOrBlank()) return emptyList()

        var offsetMs = 0L
        val timed = mutableListOf<LyricsLine>()
        val untimed = mutableListOf<LyricsLine>()

        for (rawLine in raw.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            // metadata tags — swallowed and applied once
            val meta = META_TAG.matcher(trimmed).takeIf { it.find() }
            if (meta != null) {
                val key = meta.group(1)?.lowercase() ?: ""
                val value = meta.group(2)?.removeSuffix("]")?.trim() ?: ""
                if (key == "offset") {
                    offsetMs = value.toLongOrNull() ?: 0L
                }
                continue
            }

            // hh:mm:ss(.xx) footer timestamps
            val hour = HOUR_TAG.matcher(trimmed).takeIf { it.find() }
            if (hour != null) {
                val h = hour.group(1)?.toLongOrNull() ?: 0
                val m = hour.group(2)?.toLongOrNull() ?: 0
                val s = hour.group(3)?.toLongOrNull() ?: 0
                val frac = fractionMs(hour.group(4))
                val text = trimmed.substring(hour.group().length).trim()
                if (text.isNotEmpty()) {
                    timed.add(LyricsLine(h * 3_600_000L + m * 60_000L + s * 1_000L + frac, text))
                }
                continue
            }

            // collect every leading [mm:ss(.xx)] tag of the line
            val tags = mutableListOf<Long>()
            var cursor = 0
            while (cursor < trimmed.length && trimmed[cursor] == '[') {
                val matcher = TIME_TAG.matcher(trimmed)
                matcher.region(cursor, trimmed.length)
                if (!matcher.find() || matcher.start() != cursor) break
                val minutes = matcher.group(1)?.toLongOrNull() ?: 0
                val seconds = matcher.group(2)?.toLongOrNull() ?: 0
                val frac = fractionMs(matcher.group(3))
                tags.add(minutes * 60_000L + seconds * 1_000L + frac)
                cursor = matcher.end()
            }

            val text = trimmed.substring(cursor).trim()
            if (text.isEmpty()) continue

            if (tags.isEmpty()) {
                untimed.add(LyricsLine(Long.MAX_VALUE, text))
            } else {
                for (timeMs in tags) timed.add(LyricsLine(timeMs, text))
            }
        }

        timed.sortBy { it.timeMs }

        // apply the [offset] tag so highlights stay locked to the audio
        val effective = if (offsetMs != 0L) {
            timed.map { LyricsLine((it.timeMs + offsetMs).coerceAtLeast(0L), it.text) }
        } else {
            timed
        }
        return effective + untimed
    }

    private fun fractionMs(fraction: String?): Long {
        if (fraction.isNullOrEmpty()) return 0L
        if (fraction.length == 1) return fraction[0].digitToIntOrNull()?.toLong()?.times(100) ?: 0L
        if (fraction.length == 2) {
            return (fraction.toIntOrNull() ?: 0) * 10L
        }
        return (((fraction + "00").take(3)).toIntOrNull() ?: 0).toLong()
    }
}