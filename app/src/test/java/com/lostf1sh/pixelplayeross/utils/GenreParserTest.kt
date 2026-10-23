package com.lostf1sh.pixelplayeross.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class GenreParserTest {

    @Test
    fun `split separates semicolon, comma, pipe, backslash and NUL lists`() {
        assertEquals(listOf("Alternative", "Indie Rock"), GenreParser.split("Alternative; Indie Rock"))
        assertEquals(listOf("Rock", "Pop"), GenreParser.split("Rock,Pop"))
        assertEquals(listOf("House", "Electronic"), GenreParser.split("House | Electronic"))
        assertEquals(listOf("Rock", "Pop"), GenreParser.split("Rock\\Pop"))
        assertEquals(listOf("Rock", "Pop"), GenreParser.split("Rock\u0000Pop"))
    }

    @Test
    fun `split keeps slash and ampersand genres whole`() {
        assertEquals(listOf("Rap/Hip-Hop"), GenreParser.split("Rap/Hip-Hop"))
        assertEquals(listOf("R&B/Soul", "Drum & Bass"), GenreParser.split("R&B/Soul; Drum & Bass"))
    }

    @Test
    fun `split drops the dangling ampersand of a Discogs style list`() {
        assertEquals(listOf("Folk", "World", "Country"), GenreParser.split("Folk, World, & Country"))
    }

    @Test
    fun `split returns nothing for missing, blank and unknown genres`() {
        assertTrue(GenreParser.split(null).isEmpty())
        assertTrue(GenreParser.split("   ").isEmpty())
        assertTrue(GenreParser.split("<unknown>").isEmpty())
        assertTrue(GenreParser.split("Unknown").isEmpty())
        assertTrue(GenreParser.split(" ; , ").isEmpty())
        assertEquals(listOf("Rock"), GenreParser.split("Unknown; Rock"))
    }

    @Test
    fun `split removes case and spacing duplicates keeping the first spelling`() {
        assertEquals(listOf("Rock"), GenreParser.split("Rock; rock;ROCK"))
        assertEquals(listOf("Indie Rock"), GenreParser.split("Indie Rock, Indie   rock"))
    }

    @Test
    fun `key keeps the legacy genre ids`() {
        assertEquals("hip_hop", GenreParser.key("Hip Hop"))
        assertEquals("rap_hip-hop", GenreParser.key("Rap/Hip-Hop"))
        assertEquals("indie_rock", GenreParser.key("  Indie   Rock "))
    }

    @Test
    fun `key does not depend on the default locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("indie", GenreParser.key("INDIE"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `key treats composed and decomposed accents as the same genre`() {
        assertEquals(GenreParser.key("Électro"), GenreParser.key("Électro"))
    }

    @Test
    fun `matches finds a genre inside a multi-genre value only as a whole genre`() {
        assertTrue(GenreParser.matches("Alternative; Indie Rock", "alternative"))
        assertTrue(GenreParser.matches("Alternative; Indie Rock", "indie_rock"))
        assertFalse(GenreParser.matches("Alternative Rock", "Alternative"))
        assertFalse(GenreParser.matches(null, "Rock"))
    }

    @Test
    fun `joinValues keeps a single value as written and joins several`() {
        assertEquals("Rock, Pop", GenreParser.joinValues(listOf("Rock, Pop")))
        assertEquals("Electronic; House", GenreParser.joinValues(listOf(" Electronic ", "House", "house", "")))
        assertNull(GenreParser.joinValues(listOf(null, " ")))
        assertNull(GenreParser.joinValues(emptyList()))
    }

    @Test
    fun `dominantGenre counts every genre of every song`() {
        assertEquals("House", GenreParser.dominantGenre(listOf("House", "Deep House; House", null)))
        assertEquals("Indie Rock", GenreParser.dominantGenre(listOf("Alternative; Indie Rock", "Indie Rock")))
    }

    @Test
    fun `dominantGenre breaks ties by first appearance and ignores untagged songs`() {
        assertEquals("Jazz", GenreParser.dominantGenre(listOf("Jazz", "Soul")))
        assertNull(GenreParser.dominantGenre(listOf(null, "", "<unknown>")))
    }

    @Test
    fun `likePattern widens spacing, slashes and non ASCII letters to wildcards`() {
        assertEquals("%Indie%Rock%", GenreParser.likePattern("Indie Rock"))
        assertEquals("%Rap%Hip-Hop%", GenreParser.likePattern("Rap/Hip-Hop"))
        assertEquals("%lectro%", GenreParser.likePattern("Électro"))
        assertEquals("%", GenreParser.likePattern("流行"))
    }

    @Test
    fun `likePattern never carries LIKE wildcards or escapes from the name`() {
        assertEquals("%100%Pure%", GenreParser.likePattern("100% Pure"))
        assertEquals("%Lo%Fi%", GenreParser.likePattern("Lo_Fi"))
        assertEquals("%Rock%Pop%", GenreParser.likePattern("Rock\\Pop"))
    }
}
