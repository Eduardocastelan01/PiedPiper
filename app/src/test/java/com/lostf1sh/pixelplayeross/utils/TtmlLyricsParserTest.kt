package com.lostf1sh.pixelplayeross.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TtmlLyricsParserTest {

    private fun ttml(head: String = "", body: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
          <head><metadata>$head</metadata></head>
          <body><div>$body</div></body>
        </tt>
    """.trimIndent()

    @Test
    fun numberedAgents_becomeVoicePrefixes() {
        val lrc = TtmlLyricsParser.parseToEnhancedLrc(
            ttml(
                body = """
                    <p begin="1.000" ttm:agent="v1">First singer</p>
                    <p begin="3.000" ttm:agent="v2">Second singer</p>
                """.trimIndent()
            )
        )

        assertEquals("[00:01.00]v1:First singer\n[00:03.00]v2:Second singer", lrc)
    }

    @Test
    fun namedAgents_areNumberedByFirstAppearanceAndGroupsSingInTheMiddle() {
        val synced = requireNotNull(
            LyricsUtils.parseLyrics(
                ttml(
                    head = """
                        <ttm:agent type="person" xml:id="lead"/>
                        <ttm:agent type="person" xml:id="guest"/>
                        <ttm:agent type="group" xml:id="everyone"/>
                    """.trimIndent(),
                    body = """
                        <p begin="1.000" ttm:agent="guest">Guest</p>
                        <p begin="2.000" ttm:agent="lead">Lead</p>
                        <p begin="3.000" ttm:agent="guest">Guest again</p>
                        <p begin="4.000" ttm:agent="everyone">All</p>
                    """.trimIndent()
                )
            ).synced
        )

        assertEquals(listOf(1, 2, 1, 1_000), synced.map { it.voice })
    }

    @Test
    fun backgroundSpans_becomeBackgroundVocalsInsteadOfJoiningTheLine() {
        val synced = requireNotNull(
            LyricsUtils.parseLyrics(
                ttml(
                    body = """
                        <p begin="2.000" end="6.000" ttm:agent="v2"><span begin="2.000" end="2.400">Whis</span><span begin="2.400" end="3.000">per</span> <span begin="3.000" end="3.500">now</span><span ttm:role="x-bg"><span begin="4.000" end="4.500">(whis</span><span begin="4.500" end="5.000">per)</span></span></p>
                    """.trimIndent()
                )
            ).synced
        )

        val line = synced.single()
        assertEquals(2, line.voice)
        assertEquals("Whisper now", line.line)
        assertEquals(listOf(true, false, true), line.words!!.map { it.startsNewWord })
        val background = requireNotNull(line.background)
        assertEquals("(whisper)", background.text)
        assertEquals(listOf("(whis", "per)"), background.words!!.map { it.word })
        assertEquals(listOf(true, false), background.words!!.map { it.startsNewWord })
    }

    @Test
    fun spanEndTimes_closeEachSyllable() {
        val words = requireNotNull(
            LyricsUtils.parseLyrics(
                ttml(
                    body = """<p begin="1.000"><span begin="1.000" end="1.400">Hold</span> <span begin="2.000" end="3.500">on</span></p>"""
                )
            ).synced
        ).single().words!!

        assertEquals(listOf(1_400, 3_500), words.map { it.endTime })
        assertEquals(listOf(true, true), words.map { it.startsNewWord })
    }

    @Test
    fun inlineTranslationAndRomanizationSpans_doNotJoinTheSungLine() {
        val line = requireNotNull(
            LyricsUtils.parseLyrics(
                ttml(
                    body = """
                        <p begin="1.000" ttm:agent="v1"><span begin="1.000">Hello</span><span ttm:role="x-translation" xml:lang="es">Hola</span><span ttm:role="x-roman">heh-loh</span></p>
                    """.trimIndent()
                )
            ).synced
        ).single()

        assertEquals("Hello", line.line)
        assertEquals("Hola", line.translation)
        assertEquals(1, line.voice)
    }

    @Test
    fun paragraphsWithoutAgents_haveNoVoicePrefix() {
        val lrc = requireNotNull(TtmlLyricsParser.parseToEnhancedLrc(ttml(body = """<p begin="1.000">Solo</p>""")))

        assertFalse(lrc.contains("v1:"))
        assertNull(LyricsUtils.parseLyrics(lrc).synced!!.single().voice)
        assertTrue(lrc.startsWith("[00:01.00]Solo"))
    }
}
