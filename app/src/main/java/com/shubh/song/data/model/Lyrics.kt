package com.shubh.song.data.model

import java.util.regex.Pattern

/**
 * A single lyric line, with the time (ms) it starts at when the lyric text
 * ships with LRC-style timestamps, otherwise [Long.MAX_VALUE].
 */
data class LyricsLine(
    val timeMs: Long,
    val text: String
)

/**
 * Parsed lyrics for a song. [synced] is true when the source text contains
 * timestamps, which lets the player highlight & auto-scroll lines in sync
 * with playback position.
 */
data class Lyrics(
    val lines: List<LyricsLine>,
    val synced: Boolean,
    val copyright: String? = null
) {

    /** Index of the line active at [positionMs], -1 when lyrics aren't synced. */
    fun lineIndexAt(positionMs: Long): Int {
        if (!synced || lines.isEmpty()) return -1
        var active = 0
        for ((index, line) in lines.withIndex()) {
            if (line.timeMs <= positionMs) active = index else break
        }
        return active
    }

    companion object {
        private val TIMESTAMP = Pattern.compile("^\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]")

        /**
         * Parses plain text or LRC ("[mm:ss.xx] text") lyrics into [Lyrics].
         * Returns null when the text is empty / unusable.
         */
        fun parse(raw: String?, copyright: String? = null): Lyrics? {
            val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val lines = mutableListOf<LyricsLine>()
            var timed = 0
            for (rawLine in text.split("\n")) {
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty()) continue
                val matcher = TIMESTAMP.matcher(trimmed)
                if (matcher.find()) {
                    val minutes = matcher.group(1)?.toIntOrNull() ?: 0
                    val seconds = matcher.group(2)?.toIntOrNull() ?: 0
                    val fraction = matcher.group(3)?.let { ms ->
                        (ms + "000").take(3).toIntOrNull() ?: 0
                    } ?: 0
                    val timeMs = minutes * 60_000L + seconds * 1_000L + fraction
                    val content = trimmed.substring(matcher.end()).trim()
                    if (content.isEmpty()) continue
                    timed++
                    lines.add(LyricsLine(timeMs, content))
                } else {
                    lines.add(LyricsLine(Long.MAX_VALUE, trimmed))
                }
            }
            if (lines.isEmpty()) return null
            val synced = timed > lines.size / 2
            return Lyrics(lines, synced, copyright)
        }
    }
}
