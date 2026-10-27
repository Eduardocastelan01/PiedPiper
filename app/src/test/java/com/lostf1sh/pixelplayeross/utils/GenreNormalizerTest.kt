package com.lostf1sh.pixelplayeross.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreNormalizerTest {

    @Test
    fun `numeric ID3v1 codes are decoded to genre names`() {
        assertEquals("Rock", GenreNormalizer.normalize("17"))
        assertEquals("Rock", GenreNormalizer.normalize("(17)"))
        assertEquals("Pop", GenreNormalizer.normalize("13"))
        assertEquals("Hip-Hop", GenreNormalizer.normalize("7"))
        assertEquals("Classical", GenreNormalizer.normalize("32"))
    }

    @Test
    fun `common genre aliases are normalized to canonical names`() {
        assertEquals("Alternative Rock", GenreNormalizer.normalize("alt rock"))
        assertEquals("Alternative Rock", GenreNormalizer.normalize("alternative"))
        assertEquals("Alternative Rock", GenreNormalizer.normalize("alt. rock"))
        assertEquals("Hip-Hop", GenreNormalizer.normalize("hiphop"))
        assertEquals("Hip-Hop", GenreNormalizer.normalize("hip hop"))
        assertEquals("R&B", GenreNormalizer.normalize("rnb"))
        assertEquals("R&B", GenreNormalizer.normalize("r&b"))
        assertEquals("Electronic", GenreNormalizer.normalize("electronica"))
        assertEquals("Drum & Bass", GenreNormalizer.normalize("dnb"))
        assertEquals("Soundtrack", GenreNormalizer.normalize("ost"))
        assertEquals("Game Soundtrack", GenreNormalizer.normalize("video game"))
        assertEquals("Lo-Fi", GenreNormalizer.normalize("lofi"))
    }

    @Test
    fun `splitAndNormalize handles multi-value tags and deduplicates`() {
        val result = GenreNormalizer.splitAndNormalize("Rock; alt rock, 17 | Hip Hop")
        assertEquals(2, result.size)
        assertTrue(result.contains("Rock"))
        assertTrue(result.contains("Alternative Rock"))
    }

    @Test
    fun `proper title casing and acronym preservation`() {
        assertEquals("EDM", GenreNormalizer.normalize("edm"))
        assertEquals("K-Pop", GenreNormalizer.normalize("k-pop"))
        assertEquals("Post-Punk", GenreNormalizer.normalize("post-punk"))
    }
}
