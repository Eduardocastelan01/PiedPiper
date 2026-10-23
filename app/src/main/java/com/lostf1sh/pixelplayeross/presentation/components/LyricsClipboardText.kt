package com.lostf1sh.pixelplayeross.presentation.components

import com.lostf1sh.pixelplayeross.data.model.Lyrics
import com.lostf1sh.pixelplayeross.data.model.SyncedLine

/**
 * Text the lyrics screen copies to the clipboard: the original lyrics as they are shown, without
 * LRC timestamps, voice tags, romanization or translations.
 */
internal object LyricsClipboardText {

    /**
     * One synced line. Word-timed lines are rebuilt from their words, the way the lyrics screen
     * draws them; other lines keep merged continuation rows on lines of their own.
     */
    fun syncedLine(line: SyncedLine): String {
        val words = line.words?.let(::sanitizeSyncedWords).orEmpty()
        if (words.isNotEmpty()) {
            return clusterSyncedWords(words).joinToString(" ") { cluster ->
                cluster.words.joinToString("") { it.word }
            }
        }
        return line.line.lines()
            .map { sanitizeLyricLineText(it).trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    /** One plain lyrics entry. Rows after the first hold its romanization or translation. */
    fun plainLine(entry: String): String =
        sanitizeLyricLineText(entry.substringBefore('\n')).trim()

    /**
     * The whole song. Synced lyrics are preferred over plain ones so the result doesn't depend on
     * the active view. Their blank (instrumental) lines become one empty line between stanzas.
     */
    fun fullLyrics(lyrics: Lyrics?): String = buildString {
        var pendingStanzaBreak = false
        for (row in rows(lyrics)) {
            if (row.isEmpty()) {
                pendingStanzaBreak = isNotEmpty()
                continue
            }
            if (isNotEmpty()) append(if (pendingStanzaBreak) "\n\n" else "\n")
            append(row)
            pendingStanzaBreak = false
        }
    }

    fun hasCopyableText(lyrics: Lyrics?): Boolean = rows(lyrics).any { it.isNotEmpty() }

    private fun rows(lyrics: Lyrics?): Sequence<String> {
        val synced = lyrics?.synced
        return if (!synced.isNullOrEmpty()) {
            synced.asSequence().map(::syncedLine)
        } else {
            lyrics?.plain.orEmpty().asSequence().map(::plainLine)
        }
    }
}
