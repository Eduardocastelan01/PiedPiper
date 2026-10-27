package com.lostf1sh.pixelplayeross.utils

import java.util.Locale

/**
 * Pied Piper -- Genre Normalizer
 *
 * Normalizes raw, messy genre tags from audio metadata into clean, canonical genres.
 *
 * Capabilities:
 * - Decodes ID3v1 numerical genre codes (e.g. "(17)", "17" -> "Rock")
 * - Cleans up messy punctuation and casing
 * - Maps common synonyms, slang and sub-variants to canonical genre names
 * - Formats acronyms properly (EDM, R&B, OST, K-Pop, J-Pop, etc.)
 */
object GenreNormalizer {

    /** Standard ID3v1 + Winamp extended genre code table (0..147) */
    private val ID3V1_GENRES = arrayOf(
        "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop",
        "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae",
        "Rock", "Techno", "Industrial", "Alternative", "Ska", "Death Metal", "Pranks",
        "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion",
        "Trance", "Classical", "Instrumental", "Acid", "House", "Game", "Sound Clip",
        "Gospel", "Noise", "AlternRock", "Bass", "Soul", "Punk", "Space", "Meditative",
        "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic", "Darkwave",
        "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock",
        "Comedy", "Cult", "Gangsta", "Top 40", "Christian Rap", "Pop/Funk", "Jungle",
        "Native American", "Cabaret", "New Wave", "Psychadelic", "Rave", "Showtunes",
        "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro",
        "Musical", "Rock & Roll", "Hard Rock", "Folk", "Folk-Rock", "National Folk",
        "Swing", "Fast Fusion", "Bebob", "Latin", "Revival", "Celtic", "Bluegrass",
        "Avantgarde", "Gothic Rock", "Progressive Rock", "Psychedelic Rock", "Symphonic Rock",
        "Slow Rock", "Big Band", "Chorus", "Easy Listening", "Acoustic", "Humour",
        "Speech", "Chanson", "Opera", "Chamber Music", "Sonata", "Symphony", "Booty Bass",
        "Primus", "Porn Groove", "Satire", "Slow Jam", "Club", "Tango", "Samba",
        "Folklore", "Ballad", "Power Ballad", "Rhythmic Soul", "Freestyle", "Duet",
        "Punk Rock", "Drum Solo", "Acapella", "Euro-House", "Dance Hall", "Goa",
        "Drum & Bass", "Club-House", "Hardcore", "Terror", "Indie", "BritPop",
        "Negerpunk", "Polsk Punk", "Beat", "Christian Gangsta Rap", "Heavy Metal",
        "Black Metal", "Crossover", "Contemporary Christian", "Christian Rock",
        "Merengue", "Salsa", "Thrash Metal", "Anime", "JPop", "Synthpop"
    )

    private val NUMERIC_GENRE_REGEX = Regex("""^\(?(\d{1,3})\)?$""")

    /** Canonical mapping from lower-case raw tokens to clean, human-friendly names */
    private val CANONICAL_MAP = mapOf(
        // Rock & Alt
        "rock" to "Rock",
        "classic rock" to "Classic Rock",
        "hard rock" to "Hard Rock",
        "alt rock" to "Alternative Rock",
        "alt. rock" to "Alternative Rock",
        "alternative" to "Alternative Rock",
        "alternative rock" to "Alternative Rock",
        "indie" to "Indie",
        "indie rock" to "Indie Rock",
        "indie pop" to "Indie Pop",
        "punk" to "Punk",
        "punk rock" to "Punk Rock",
        "pop punk" to "Pop Punk",
        "post-punk" to "Post-Punk",
        "post punk" to "Post-Punk",
        "grunge" to "Grunge",
        "progressive rock" to "Progressive Rock",
        "prog rock" to "Progressive Rock",
        "psychedelic rock" to "Psychedelic Rock",
        "psychedelic" to "Psychedelic",
        "metal" to "Metal",
        "heavy metal" to "Heavy Metal",
        "death metal" to "Death Metal",
        "black metal" to "Black Metal",
        "nu metal" to "Nu-Metal",
        "nu-metal" to "Nu-Metal",

        // Pop
        "pop" to "Pop",
        "dance pop" to "Dance-Pop",
        "dance-pop" to "Dance-Pop",
        "synthpop" to "Synth-Pop",
        "synth-pop" to "Synth-Pop",
        "electropop" to "Electro-Pop",
        "electro-pop" to "Electro-Pop",
        "k-pop" to "K-Pop",
        "kpop" to "K-Pop",
        "j-pop" to "J-Pop",
        "jpop" to "J-Pop",
        "c-pop" to "C-Pop",

        // Hip-Hop / Rap / Urban
        "hip hop" to "Hip-Hop",
        "hip-hop" to "Hip-Hop",
        "hiphop" to "Hip-Hop",
        "rap" to "Rap",
        "trap" to "Trap",
        "gangsta rap" to "Hip-Hop",
        "r&b" to "R&B",
        "r & b" to "R&B",
        "rnb" to "R&B",
        "contemporary r&b" to "R&B",
        "soul" to "Soul",
        "neo soul" to "Neo-Soul",
        "neo-soul" to "Neo-Soul",
        "funk" to "Funk",

        // Electronic / Dance
        "electronic" to "Electronic",
        "electronica" to "Electronic",
        "edm" to "EDM",
        "dance" to "Dance",
        "house" to "House",
        "deep house" to "Deep House",
        "tech house" to "Tech House",
        "techno" to "Techno",
        "trance" to "Trance",
        "drum and bass" to "Drum & Bass",
        "drum & bass" to "Drum & Bass",
        "dnb" to "Drum & Bass",
        "d&b" to "Drum & Bass",
        "dubstep" to "Dubstep",
        "ambient" to "Ambient",
        "chillout" to "Chillout",
        "chill" to "Chillout",
        "lo-fi" to "Lo-Fi",
        "lofi" to "Lo-Fi",
        "synthwave" to "Synthwave",
        "vaporwave" to "Vaporwave",
        "future bass" to "Future Bass",

        // Latin / Caribbean
        "latin" to "Latin",
        "latino" to "Latin",
        "reggaeton" to "Reggaeton",
        "reggae" to "Reggae",
        "salsa" to "Salsa",
        "bachata" to "Bachata",
        "cumbia" to "Cumbia",
        "merengue" to "Merengue",
        "latin pop" to "Latin Pop",
        "dancehall" to "Dancehall",

        // Classical / Acoustic / Jazz
        "classical" to "Classical",
        "baroque" to "Classical",
        "romantic" to "Classical",
        "orchestral" to "Classical",
        "soundtrack" to "Soundtrack",
        "ost" to "Soundtrack",
        "score" to "Soundtrack",
        "film score" to "Soundtrack",
        "video game" to "Game Soundtrack",
        "game" to "Game Soundtrack",
        "jazz" to "Jazz",
        "blues" to "Blues",
        "folk" to "Folk",
        "acoustic" to "Acoustic",
        "country" to "Country",
        "bluegrass" to "Bluegrass",
        "gospel" to "Gospel",
        "christian" to "Christian",
        "world" to "World",
        "flamenco" to "Flamenco",
        "bossa nova" to "Bossa Nova"
    )

    /**
     * Normalizes a single genre string.
     * Decodes numeric codes, maps known synonyms, and applies clean Title Case formatting.
     */
    fun normalize(rawGenre: String): String {
        val trimmed = rawGenre.trim()
        if (trimmed.isEmpty()) return ""

        // Check for numeric ID3v1 codes, e.g. "17" or "(17)"
        val numericMatch = NUMERIC_GENRE_REGEX.matchEntire(trimmed)
        if (numericMatch != null) {
            val code = numericMatch.groupValues[1].toIntOrNull()
            if (code != null && code in ID3V1_GENRES.indices) {
                return ID3V1_GENRES[code]
            }
        }

        // Canonical mapping lookup
        val lower = trimmed.lowercase(Locale.ROOT)
        CANONICAL_MAP[lower]?.let { return it }

        // Clean up common prefixes/suffixes
        var cleaned = trimmed
            .replace(Regex("""^\((.+)\)$"""), "$1") // Strip enclosing parens "(Rock)" -> "Rock"
            .trim()

        if (cleaned.isEmpty()) return ""

        // Title case formatting as fallback
        return toTitleCase(cleaned)
    }

    /**
     * Splits a raw genre tag by standard delimiters and returns normalized, deduplicated genres.
     */
    fun splitAndNormalize(raw: String?): List<String> {
        val splitGenres = GenreParser.split(raw)
        val seen = LinkedHashSet<String>()
        val result = mutableListOf<String>()

        for (item in splitGenres) {
            val normalized = normalize(item)
            if (normalized.isNotBlank() && seen.add(normalized.lowercase(Locale.ROOT))) {
                result.add(normalized)
            }
        }
        return result
    }

    private fun toTitleCase(input: String): String {
        val words = input.split(Regex("""\s+"""))
        return words.joinToString(" ") { word ->
            when (word.lowercase(Locale.ROOT)) {
                "and", "of", "the", "in", "on", "at", "to", "for" -> word.lowercase(Locale.ROOT)
                "ost" -> "OST"
                "edm" -> "EDM"
                "dnb" -> "D&B"
                "r&b" -> "R&B"
                "k-pop" -> "K-Pop"
                "j-pop" -> "J-Pop"
                else -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
        }.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }
}
