package com.lostf1sh.pixelplayeross.presentation.components

import com.lostf1sh.pixelplayeross.data.model.Lyrics
import com.lostf1sh.pixelplayeross.data.model.SyncedLine
import com.lostf1sh.pixelplayeross.data.model.SyncedWord
import com.lostf1sh.pixelplayeross.utils.LyricsUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.text.Normalizer

class LyricsClipboardTextTest {

    @Test
    fun fullLyrics_syncedLyrics_copiesTextWithoutTimestamps() {
        val lyrics = LyricsUtils.parseLyrics("[00:01.00]Hello there\n[00:04.50]General Kenobi")

        assertEquals("Hello there\nGeneral Kenobi", LyricsClipboardText.fullLyrics(lyrics))
    }

    @Test
    fun fullLyrics_blankSyncedLines_becomeSingleStanzaBreaks() {
        val lyrics = LyricsUtils.parseLyrics(
            """
            [00:00.50]
            [00:01.00]First line of verse one
            [00:04.00]Second line of verse one
            [00:07.00]
            [00:08.00]
            [00:10.00]Chorus line one
            [00:13.00]Chorus line two
            [00:16.00]
            """.trimIndent()
        )

        assertEquals(
            "First line of verse one\nSecond line of verse one\n\nChorus line one\nChorus line two",
            LyricsClipboardText.fullLyrics(lyrics)
        )
    }

    @Test
    fun fullLyrics_syncedLine_leavesOutTranslationAndRomanization() {
        val lyrics = Lyrics(
            synced = listOf(
                SyncedLine(
                    time = 1_000,
                    line = "こんにちは",
                    translation = "Hello",
                    romanization = "konnichiwa"
                )
            ),
            plain = listOf("こんにちは\nkonnichiwa\nHello")
        )

        assertEquals("こんにちは", LyricsClipboardText.fullLyrics(lyrics))
    }

    @Test
    fun fullLyrics_sameTimestampTranslationLines_areNotCopied() {
        val lyrics = LyricsUtils.parseLyrics(
            "[00:01.00]Je t'aime\n[00:01.00]I love you\n[00:03.00]Moi non plus\n[00:03.00]Me neither"
        )

        assertEquals("Je t'aime\nMoi non plus", LyricsClipboardText.fullLyrics(lyrics))
    }

    @Test
    fun fullLyrics_enhancedLrc_dropsWordTagsVoiceTagsAndBackgroundVocals() {
        val lyrics = LyricsUtils.parseLyrics(
            """
            [ti:Bones]
            [00:02.00]v1: <00:02.00>There's <00:02.60>a <00:03.00>whis<00:03.40>per <00:03.90>in <00:04.20>my <00:04.60>bones<00:05.40>
            [bg: <00:05.40>(bones, <00:06.00>bones)<00:06.80>]
            [00:07.00]v2: <00:07.00>Kee<00:07.40>ping <00:07.90>me <00:08.30>rest<00:08.80>less<00:09.30>
            [00:29.00]<00:29.00>Oh, <00:29.50>oh, <00:30.00>woah<00:30.60>-oh<00:31.10>-oh<00:32.40>
            """.trimIndent()
        )

        assertEquals(
            "There's a whisper in my bones\nKeeping me restless\nOh, oh, woah-oh-oh",
            LyricsClipboardText.fullLyrics(lyrics)
        )
    }

    @Test
    fun syncedLine_wordTimedLine_matchesTheWordsTheScreenDraws() {
        // Kugou producers can omit spaces between timed Latin tokens. The parser keeps the raw
        // text in `line` but marks the word boundary, and the lyrics screen draws "Hello world".
        val lyrics = LyricsUtils.parseLyrics("[1000,2000]<0,500,0>Hello<500,500,0>world")
        val line = requireNotNull(lyrics.synced).single()

        assertEquals("Hello world", LyricsClipboardText.syncedLine(line))
    }

    @Test
    fun syncedLine_splitSyllables_joinWithoutSpaces() {
        val line = SyncedLine(
            time = 0,
            line = "v1: whisper",
            words = listOf(
                SyncedWord(time = 0, word = "v1:"),
                SyncedWord(time = 0, word = "whis"),
                SyncedWord(time = 400, word = "per", startsNewWord = false)
            )
        )

        assertEquals("whisper", LyricsClipboardText.syncedLine(line))
    }

    @Test
    fun syncedLine_mergedContinuationRows_keepsEveryRow() {
        val lyrics = LyricsUtils.parseLyrics("[00:01.00]First row\nSecond row\n[00:05.00]Third row")
        val synced = requireNotNull(lyrics.synced)

        assertEquals("First row\nSecond row", LyricsClipboardText.syncedLine(synced.first()))
        assertEquals("First row\nSecond row\nThird row", LyricsClipboardText.fullLyrics(lyrics))
    }

    @Test
    fun fullLyrics_prefersSyncedLyricsOverPlainOnes() {
        val lyrics = Lyrics(
            synced = listOf(SyncedLine(time = 0, line = "Synced text")),
            plain = listOf("Plain text")
        )

        assertEquals("Synced text", LyricsClipboardText.fullLyrics(lyrics))
    }

    @Test
    fun fullLyrics_plainLyrics_copiesFirstRowOfEachEntry() {
        val lyrics = Lyrics(plain = listOf("東京\ntoukyou\nTokyo", "Second line"))

        assertEquals("東京\nSecond line", LyricsClipboardText.fullLyrics(lyrics))
    }

    @Test
    fun fullLyrics_plainLyricsFromText_copiesEveryLineInOrder() {
        val lyrics = LyricsUtils.parseLyrics("Verse one\nStill verse one\nVerse two")

        assertEquals("Verse one\nStill verse one\nVerse two", LyricsClipboardText.fullLyrics(lyrics))
    }

    @Test
    fun fullLyrics_blankPlainEntries_becomeSingleStanzaBreaks() {
        val lyrics = Lyrics(plain = listOf("", "Verse one", "", "", "Verse two", ""))

        assertEquals("Verse one\n\nVerse two", LyricsClipboardText.fullLyrics(lyrics))
    }

    @Test
    fun plainLine_stripsStrayTimestampsVoiceTagsAndPadding() {
        assertEquals("Hello", LyricsClipboardText.plainLine("v1: Hello [00:12.00]  "))
        assertEquals("東京", LyricsClipboardText.plainLine("東京\ntoukyou"))
    }

    @Test
    fun fullLyrics_keepsDecomposedVietnameseMarks() {
        val decomposed = Normalizer.normalize("Người ơi người ở đừng về", Normalizer.Form.NFD)
        val lyrics = LyricsUtils.parseLyrics("[00:01.00]$decomposed")

        assertEquals(decomposed, LyricsClipboardText.fullLyrics(lyrics))
    }

    @Test
    fun fullLyrics_missingOrEmptyLyrics_returnsEmptyText() {
        assertEquals("", LyricsClipboardText.fullLyrics(null))
        assertEquals("", LyricsClipboardText.fullLyrics(Lyrics()))
        assertEquals("", LyricsClipboardText.fullLyrics(LyricsUtils.parseLyrics("")))
    }

    @Test
    fun hasCopyableText_isFalseWithoutAnyLyricText() {
        assertFalse(LyricsClipboardText.hasCopyableText(null))
        assertFalse(LyricsClipboardText.hasCopyableText(Lyrics(plain = emptyList(), synced = emptyList())))
        assertFalse(
            LyricsClipboardText.hasCopyableText(
                Lyrics(synced = listOf(SyncedLine(time = 0, line = ""), SyncedLine(time = 1_000, line = " ")))
            )
        )
    }

    @Test
    fun hasCopyableText_isTrueForPlainOrSyncedText() {
        assertTrue(LyricsClipboardText.hasCopyableText(Lyrics(plain = listOf("Only plain"))))
        assertTrue(LyricsClipboardText.hasCopyableText(LyricsUtils.parseLyrics("[00:01.00]Synced")))
    }
}
