package com.lostf1sh.pixelplayeross.data.preferences

enum class CollagePattern(
    val storageKey: String,
    val label: String
) {
    COSMIC_SWIRL("cosmic_swirl", "Cosmic Swirl"),
    HONEYCOMB_GROOVE("honeycomb_groove", "Honeycomb Groove"),
    VINYL_STACK("vinyl_stack", "Vinyl Stack"),
    PIXEL_MOSAIC("pixel_mosaic", "Pixel Mosaic"),
    STARDUST_SCATTER("stardust_scatter", "Stardust Scatter"),
    SOFT_ORBIT("soft_orbit", "Soft Orbit"),
    ARROW_FANFARE("arrow_fanfare", "Arrow Fanfare"),
    SUNNY_BLOOM("sunny_bloom", "Sunny Bloom"),
    PIXEL_COOKIE("pixel_cookie", "Pixel Cookie"),
    HEART_GARDEN("heart_garden", "Heart Garden");

    companion object {
        val default: CollagePattern = COSMIC_SWIRL

        fun fromStorageKey(value: String?): CollagePattern {
            return entries.firstOrNull { it.storageKey == value } ?: default
        }
    }
}
