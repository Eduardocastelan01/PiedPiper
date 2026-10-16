package com.lostf1sh.pixelplayeross.data.preferences

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CollagePatternTest {

    @Test
    fun `every pattern round trips through its storage key`() {
        CollagePattern.entries.forEach { pattern ->
            assertEquals(pattern, CollagePattern.fromStorageKey(pattern.storageKey))
        }
    }

    @Test
    fun `storage keys are unique`() {
        val keys = CollagePattern.entries.map { it.storageKey }

        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `material shape patterns keep the storage keys shared with other editions`() {
        assertEquals(CollagePattern.SOFT_ORBIT, CollagePattern.fromStorageKey("soft_orbit"))
        assertEquals(CollagePattern.ARROW_FANFARE, CollagePattern.fromStorageKey("arrow_fanfare"))
        assertEquals(CollagePattern.SUNNY_BLOOM, CollagePattern.fromStorageKey("sunny_bloom"))
        assertEquals(CollagePattern.PIXEL_COOKIE, CollagePattern.fromStorageKey("pixel_cookie"))
        assertEquals(CollagePattern.HEART_GARDEN, CollagePattern.fromStorageKey("heart_garden"))
    }

    @Test
    fun `unknown or missing key falls back to cosmic swirl`() {
        assertEquals(CollagePattern.COSMIC_SWIRL, CollagePattern.fromStorageKey("not_a_pattern"))
        assertEquals(CollagePattern.COSMIC_SWIRL, CollagePattern.fromStorageKey(null))
        assertEquals(CollagePattern.COSMIC_SWIRL, CollagePattern.default)
    }
}
