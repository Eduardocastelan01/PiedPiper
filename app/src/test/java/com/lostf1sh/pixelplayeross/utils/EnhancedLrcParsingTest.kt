package com.lostf1sh.pixelplayeross.utils

import com.lostf1sh.pixelplayeross.data.model.BackgroundVocals
import com.lostf1sh.pixelplayeross.data.model.SyncedLine
import com.lostf1sh.pixelplayeross.data.model.SyncedWord
import java.text.Normalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnhancedLrcParsingTest {

    private fun parseSynced(lrc: String): List<SyncedLine> =
        requireNotNull(LyricsUtils.parseLyrics(lrc).synced)

    @Test
    fun syllablesWithoutSpaceBetweenTags_formOneVisualWord() {
        // The line from issue #131: "whis" and "per" are one word because no space separates them.
        val line = parseSynced(
            "[00:45.00]<00:45.00>There's <00:45.40>a <00:45.73>whis<00:46.29>per <00:46.80>in"
        ).single()

        assertEquals("There's a whisper in", line.line)
        assertEquals(listOf("There's", "a", "whis", "per", "in"), line.words!!.map { it.word })
        assertEquals(listOf(true, true, true, false, true), line.words!!.map { it.startsNewWord })
    }

    @Test
    fun voicePrefixBeforeWordTags_setsVoiceAndIsStrippedFromText() {
        val line = parseSynced("[00:10.00]v1: <00:10.00>Hello <00:10.50>world").single()

        assertEquals(1, line.voice)
        assertEquals("Hello world", line.line)
        assertEquals(listOf("Hello", "world"), line.words!!.map { it.word })
        assertTrue(line.words!!.first().startsNewWord)
    }

    @Test
    fun voicePrefixAfterFirstWordTag_keepsTheTagTiming() {
        val line = parseSynced("[00:12.00]<00:12.00>v2:Hi <00:12.40>there").single()

        assertEquals(2, line.voice)
        assertEquals("Hi there", line.line)
        assertEquals(listOf(12_000, 12_400), line.words!!.map { it.time })
    }

    @Test
    fun voicePrefixOnPlainSyncedLine_isCaseInsensitive() {
        val line = parseSynced("[00:14.00]V2: Plain words").single()

        assertEquals(2, line.voice)
        assertEquals("Plain words", line.line)
        assertNull(line.words)
    }

    @Test
    fun linesWithoutPrefix_haveNoVoice() {
        assertNull(parseSynced("[00:14.00]Just a line").single().voice)
    }

    @Test
    fun differentVoicesAtSameTimestamp_areSeparateDuetLines() {
        val lines = parseSynced(
            """
            [00:18.00]v1:<00:18.00>Both <00:18.50>at <00:19.00>once
            [00:18.00]v2:<00:18.00>Both <00:18.50>at <00:19.00>once
            """.trimIndent()
        )

        assertEquals(listOf(1, 2), lines.map { it.voice })
        assertTrue(lines.all { it.translation == null })
    }

    @Test
    fun untaggedLineAtSameTimestamp_isStillTheTranslationOfAVoicedLine() {
        val line = parseSynced("[00:10.00]v1: Hello world\n[00:10.00]Hola mundo").single()

        assertEquals(1, line.voice)
        assertEquals("Hola mundo", line.translation)
    }

    @Test
    fun timedBackgroundLine_attachesToPreviousLineInsteadOfBeingDroppedAsMetadata() {
        val lines = parseSynced(
            """
            [ti:Bones]
            [00:02.00]<00:02.00>in <00:04.20>my <00:04.60>bones<00:05.40>
            [bg: <00:05.40>(whis<00:05.90>per) <00:06.30>bones<00:06.80>]
            [00:07.00]Next line
            """.trimIndent()
        )

        assertEquals(listOf("in my bones", "Next line"), lines.map { it.line })
        val background = requireNotNull(lines.first().background)
        assertEquals("(whisper) bones", background.text)
        assertEquals(listOf("(whis", "per)", "bones"), background.words!!.map { it.word })
        assertEquals(listOf(true, false, true), background.words!!.map { it.startsNewWord })
        assertEquals(6_800, background.words!!.last().endTime)
        assertNull(lines[1].background)
    }

    @Test
    fun backgroundLineAfterInstrumentalMarker_staysOnTheEmptyLine() {
        val lyrics = LyricsUtils.parseLyrics(
            """
            [00:58.00]Last words
            [01:00.00]
            [bg:<01:00.50>(ooh)<01:01.40>]
            [01:05.00]Next verse
            """.trimIndent()
        )

        val lines = requireNotNull(lyrics.synced)
        assertEquals(listOf("Last words", "", "Next verse"), lines.map { it.line })
        assertNull(lines[0].background)
        assertEquals("(ooh)", lines[1].background!!.text)
        assertEquals(61_400, lines[1].background!!.words!!.single().endTime)
        assertEquals("(ooh)", lyrics.plain!![1])
    }

    @Test
    fun plainBackgroundLine_attachesAsText() {
        val line = parseSynced("[00:02.00]Main line\n[bg: (ooh, ooh)]").single()

        assertEquals(BackgroundVocals(text = "(ooh, ooh)"), line.background)
    }

    @Test
    fun consecutiveBackgroundLines_areJoined() {
        val line = parseSynced(
            "[00:02.00]Main\n[bg:<00:03.00>first]\n[bg:<00:04.00>second]"
        ).single()

        val background = requireNotNull(line.background)
        assertEquals("first second", background.text)
        assertEquals(listOf(true, true), background.words!!.map { it.startsNewWord })
    }

    @Test
    fun timedBackgroundLineBeforeAnyLine_becomesItsOwnLine() {
        val line = parseSynced("[bg:<00:01.00>(oh <00:01.50>yeah)]").single()

        assertEquals(1_000, line.time)
        assertEquals("(oh yeah)", line.line)
        assertEquals(listOf("(oh", "yeah)"), line.words!!.map { it.word })
    }

    @Test
    fun trailingEmptyTag_setsWhenTheLastSyllableEnds() {
        val words = parseSynced("[00:02.00]<00:02.00>in <00:04.60>bones<00:05.40>").single().words!!

        assertEquals(listOf(null, 5_400), words.map { it.endTime })
    }

    @Test
    fun closingTagFollowedBySpace_endsSyllableAndKeepsTheWordBreak() {
        val words = parseSynced("[00:01.00]<00:01.00>word<00:01.40> <00:02.00>next").single().words!!

        assertEquals(listOf("word", "next"), words.map { it.word })
        assertEquals(listOf(true, true), words.map { it.startsNewWord })
        assertEquals(1_400, words.first().endTime)
    }

    @Test
    fun bareClosingTagAfterSpace_doesNotJoinTheNextWord() {
        val words = parseSynced("[00:01.00]<00:01.00>word <00:01.40><00:02.00>next").single().words!!

        assertEquals(listOf(true, true), words.map { it.startsNewWord })
    }

    @Test
    fun kugouDurations_setSyllableEndTimes() {
        val words = parseSynced("[1000,2000]<0,300,0>Hel<300,500,0>lo").single().words!!

        assertEquals(listOf(1_300, 1_800), words.map { it.endTime })
    }

    @Test
    fun decomposedVietnamese_isNormalizedToComposedForm() {
        val composed = "Người về em vẫn khóc thầm"
        val decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD)
        assertFalse(composed == decomposed)

        val line = parseSynced("[00:06.00]$decomposed").single()

        assertEquals(composed, line.line)
    }

    @Test
    fun decomposedVietnameseWordTags_keepToneMarksOnTheirLetters() {
        val decomposed = Normalizer.normalize(
            "[00:11.00]<00:11.00>Tiếng <00:11.60>hát <00:12.20>vang",
            Normalizer.Form.NFD
        )

        val words = parseSynced(decomposed).single().words!!

        assertEquals(listOf("Tiếng", "hát", "vang"), words.map { it.word })
    }

    @Test
    fun kugouTokenStartingWithToneMark_isFoldedIntoThePreviousSyllable() {
        // A producer that splits decomposed text by code unit can cut the tone mark of "ế" off its "ê".
        val words = parseSynced("[2000,1000]<0,500,0>Tiê<500,500,0>́ng").single().words!!

        assertEquals(listOf("Tiếng"), words.map { it.word })
        assertEquals(3_000, words.single().endTime)
    }

    @Test
    fun enhancedLrcRoundTrip_keepsVoicesBackgroundSyllablesTranslationsAndEndTimes() {
        val source = """
            [00:02.00]v1:<00:02.00>There's <00:02.60>a <00:03.00>whis<00:03.40>per<00:03.80>
            [bg:<00:04.00>(whis<00:04.40>per)<00:05.00>]
            [00:02.00]Hay un susurro
            [00:07.00]v2:<00:07.00>Kee<00:07.40>ping <00:07.90>me<00:08.50> <00:09.00>rest<00:09.40>less
            [00:11.00]v1: Plain left line
            [bg: (oh)]
        """.trimIndent()
        val parsed = parseSynced(source)

        val reparsed = parseSynced(LyricsUtils.toEnhancedLrc(parsed))

        assertEquals(parsed, reparsed)
        assertEquals("Hay un susurro", reparsed.first().translation)
        assertEquals(listOf(1, 2, 1), reparsed.map { it.voice })
    }

    @Test
    fun syncedToLrcString_keepsVoicePrefixesAndBackgroundTextWithoutWordTags() {
        val lrc = LyricsUtils.syncedToLrcString(
            listOf(
                SyncedLine(
                    time = 10_000,
                    line = "Hello world",
                    words = listOf(SyncedWord(10_000, "Hello"), SyncedWord(10_500, "world")),
                    voice = 2,
                    background = BackgroundVocals(
                        text = "(hello)",
                        words = listOf(SyncedWord(11_000, "(hello)"))
                    )
                )
            )
        )

        assertEquals("[00:10.00]v2:Hello world\n[bg:(hello)]", lrc)
    }

    @Test
    fun syncedToLrcString_writesAsciiDigitsWhateverTheDefaultLocale() {
        val previous = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))
        try {
            val lrc = LyricsUtils.syncedToLrcString(listOf(SyncedLine(time = 61_230, line = "Hi")))

            assertEquals("[01:01.23]Hi", lrc)
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}
