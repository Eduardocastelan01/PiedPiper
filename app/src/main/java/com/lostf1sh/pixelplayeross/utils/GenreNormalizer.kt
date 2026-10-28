package com.lostf1sh.pixelplayeross.utils

import java.util.Locale

/**
 * Pied Piper -- Genre Normalizer & Macro-Category Aggregator
 *
 * Normalizes raw, messy genre tags into clean macro-categories (Pop, Rock, Metal,
 * Hip-Hop, Electronic, Latin, R&B & Soul, Classical, Soundtrack, Jazz & Blues, etc.)
 * so the user enjoys a curated, high-level music discovery experience rather than
 * dozens of fragmented subgenres.
 */
object GenreNormalizer {

    /** Umbrella categories */
    const val CATEGORY_POP = "Pop"
    const val CATEGORY_ROCK = "Rock"
    const val CATEGORY_METAL = "Metal"
    const val CATEGORY_HIP_HOP = "Hip-Hop"
    const val CATEGORY_ELECTRONIC = "Electronic"
    const val CATEGORY_LATIN = "Latin"
    const val CATEGORY_RNB_SOUL = "R&B & Soul"
    const val CATEGORY_CLASSICAL = "Classical"
    const val CATEGORY_SOUNDTRACK = "Soundtrack"
    const val CATEGORY_JAZZ_BLUES = "Jazz & Blues"
    const val CATEGORY_COUNTRY_FOLK = "Country & Folk"
    const val CATEGORY_REGGAE = "Reggae"

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
        "indie" to "Indie Rock",
        "indie rock" to "Indie Rock",
        "indie pop" to "Pop",
        "punk" to "Punk Rock",
        "punk rock" to "Punk Rock",
        "pop punk" to "Punk Rock",
        "post-punk" to "Post-Punk",
        "post punk" to "Post-Punk",
        "grunge" to "Grunge",
        "progressive rock" to "Progressive Rock",
        "prog rock" to "Progressive Rock",
        "psychedelic rock" to "Psychedelic Rock",
        "psychedelic" to "Psychedelic Rock",
        "metal" to "Metal",
        "heavy metal" to "Heavy Metal",
        "death metal" to "Death Metal",
        "black metal" to "Black Metal",
        "nu metal" to "Nu-Metal",
        "nu-metal" to "Nu-Metal",
        "metalcore" to "Metal",

        // Pop
        "pop" to "Pop",
        "dance pop" to "Pop",
        "dance-pop" to "Pop",
        "synthpop" to "Pop",
        "synth-pop" to "Pop",
        "electropop" to "Pop",
        "electro-pop" to "Pop",
        "k-pop" to "Pop",
        "kpop" to "Pop",
        "j-pop" to "Pop",
        "jpop" to "Pop",
        "c-pop" to "Pop",
        "teen pop" to "Pop",
        "dream pop" to "Pop",
        "hyperpop" to "Pop",
        "art pop" to "Pop",

        // Hip-Hop / Rap / Urban
        "hip hop" to "Hip-Hop",
        "hip-hop" to "Hip-Hop",
        "hiphop" to "Hip-Hop",
        "rap" to "Hip-Hop",
        "trap" to "Hip-Hop",
        "gangsta rap" to "Hip-Hop",
        "boom bap" to "Hip-Hop",
        "drill" to "Hip-Hop",
        "urban" to "Hip-Hop",

        // R&B / Soul
        "r&b" to "R&B & Soul",
        "r & b" to "R&B & Soul",
        "rnb" to "R&B & Soul",
        "contemporary r&b" to "R&B & Soul",
        "soul" to "R&B & Soul",
        "neo soul" to "R&B & Soul",
        "neo-soul" to "R&B & Soul",
        "funk" to "R&B & Soul",

        // Electronic / Dance
        "electronic" to "Electronic",
        "electronica" to "Electronic",
        "edm" to "Electronic",
        "dance" to "Electronic",
        "house" to "Electronic",
        "deep house" to "Electronic",
        "tech house" to "Electronic",
        "techno" to "Electronic",
        "trance" to "Electronic",
        "drum and bass" to "Electronic",
        "drum & bass" to "Electronic",
        "dnb" to "Electronic",
        "d&b" to "Electronic",
        "dubstep" to "Electronic",
        "ambient" to "Electronic",
        "chillout" to "Electronic",
        "chill" to "Electronic",
        "lo-fi" to "Electronic",
        "lofi" to "Electronic",
        "synthwave" to "Electronic",
        "vaporwave" to "Electronic",
        "future bass" to "Electronic",
        "eurodance" to "Electronic",
        "disco" to "Electronic",

        // Latin
        "latin" to "Latin",
        "latino" to "Latin",
        "reggaeton" to "Latin",
        "reggae ton" to "Latin",
        "salsa" to "Latin",
        "bachata" to "Latin",
        "cumbia" to "Latin",
        "merengue" to "Latin",
        "latin pop" to "Latin",
        "latin urban" to "Latin",
        "urbano latino" to "Latin",
        "banda" to "Latin",
        "corrido" to "Latin",
        "corridos tumbados" to "Latin",
        "mariachi" to "Latin",
        "ranchera" to "Latin",
        "flamenco" to "Latin",
        "bossa nova" to "Latin",

        // Classical
        "classical" to "Classical",
        "baroque" to "Classical",
        "romantic" to "Classical",
        "orchestral" to "Classical",
        "opera" to "Classical",
        "chamber music" to "Classical",
        "symphony" to "Classical",
        "sonata" to "Classical",
        "instrumental" to "Classical",
        "piano" to "Classical",

        // Soundtracks
        "soundtrack" to "Soundtrack",
        "ost" to "Soundtrack",
        "score" to "Soundtrack",
        "film score" to "Soundtrack",
        "movie score" to "Soundtrack",
        "video game" to "Soundtrack",
        "game" to "Soundtrack",
        "game soundtrack" to "Soundtrack",
        "anime" to "Soundtrack",
        "musical" to "Soundtrack",
        "theme" to "Soundtrack",

        // Jazz & Blues
        "jazz" to "Jazz & Blues",
        "blues" to "Jazz & Blues",
        "smooth jazz" to "Jazz & Blues",
        "bebop" to "Jazz & Blues",
        "swing" to "Jazz & Blues",
        "big band" to "Jazz & Blues",
        "fusion" to "Jazz & Blues",
        "acid jazz" to "Jazz & Blues",

        // Country & Folk
        "country" to "Country & Folk",
        "folk" to "Country & Folk",
        "bluegrass" to "Country & Folk",
        "acoustic" to "Country & Folk",
        "americana" to "Country & Folk",
        "singer-songwriter" to "Country & Folk",
        "celtic" to "Country & Folk",
        "world" to "Country & Folk",

        // Reggae
        "reggae" to "Reggae",
        "ska" to "Reggae",
        "dancehall" to "Reggae",
        "dub" to "Reggae"
    )

    /**
     * Maps known artists to their dominant macro category for untagged or unknown songs.
     */
    private val ARTIST_CATEGORY_MAP: Map<String, String> = mapOf(
        // Pop
        "taylor swift" to CATEGORY_POP, "michael jackson" to CATEGORY_POP, "madonna" to CATEGORY_POP,
        "ariana grande" to CATEGORY_POP, "dua lipa" to CATEGORY_POP, "billie eilish" to CATEGORY_POP,
        "justin bieber" to CATEGORY_POP, "katy perry" to CATEGORY_POP, "lady gaga" to CATEGORY_POP,
        "ed sheeran" to CATEGORY_POP, "bruno mars" to CATEGORY_POP, "adele" to CATEGORY_POP,
        "harry styles" to CATEGORY_POP, "rihanna" to CATEGORY_POP, "beyonce" to CATEGORY_POP,
        "britney spears" to CATEGORY_POP, "olivia rodrigo" to CATEGORY_POP, "the weeknd" to CATEGORY_POP,
        "selena gomez" to CATEGORY_POP, "shawn mendes" to CATEGORY_POP, "maroon 5" to CATEGORY_POP,
        "sia" to CATEGORY_POP, "charlie puth" to CATEGORY_POP, "camila cabello" to CATEGORY_POP,
        "bts" to CATEGORY_POP, "blackpink" to CATEGORY_POP, "twice" to CATEGORY_POP,

        // Rock
        "queen" to CATEGORY_ROCK, "the beatles" to CATEGORY_ROCK, "led zeppelin" to CATEGORY_ROCK,
        "pink floyd" to CATEGORY_ROCK, "the rolling stones" to CATEGORY_ROCK, "nirvana" to CATEGORY_ROCK,
        "ac/dc" to CATEGORY_ROCK, "guns n' roses" to CATEGORY_ROCK, "linkin park" to CATEGORY_ROCK,
        "foo fighters" to CATEGORY_ROCK, "green day" to CATEGORY_ROCK, "radiohead" to CATEGORY_ROCK,
        "u2" to CATEGORY_ROCK, "coldplay" to CATEGORY_ROCK, "arctic monkeys" to CATEGORY_ROCK,
        "the killers" to CATEGORY_ROCK, "red hot chili peppers" to CATEGORY_ROCK, "muse" to CATEGORY_ROCK,
        "oasis" to CATEGORY_ROCK, "blink-182" to CATEGORY_ROCK, "bon jovi" to CATEGORY_ROCK,
        "aerosmith" to CATEGORY_ROCK, "the police" to CATEGORY_ROCK, "gorillaz" to CATEGORY_ROCK,
        "twenty one pilots" to CATEGORY_ROCK, "imagine dragons" to CATEGORY_ROCK,

        // Metal
        "metallica" to CATEGORY_METAL, "iron maiden" to CATEGORY_METAL, "black sabbath" to CATEGORY_METAL,
        "judas priest" to CATEGORY_METAL, "megadeth" to CATEGORY_METAL, "slayer" to CATEGORY_METAL,
        "slipknot" to CATEGORY_METAL, "avenged sevenfold" to CATEGORY_METAL, "pantera" to CATEGORY_METAL,
        "system of a down" to CATEGORY_METAL, "rammstein" to CATEGORY_METAL, "korn" to CATEGORY_METAL,
        "disturbed" to CATEGORY_METAL, "ghost" to CATEGORY_METAL, "nightwish" to CATEGORY_METAL,

        // Hip-Hop
        "eminem" to CATEGORY_HIP_HOP, "drake" to CATEGORY_HIP_HOP, "kendrick lamar" to CATEGORY_HIP_HOP,
        "kanye west" to CATEGORY_HIP_HOP, "travis scott" to CATEGORY_HIP_HOP, "post malone" to CATEGORY_HIP_HOP,
        "2pac" to CATEGORY_HIP_HOP, "notorious b.i.g." to CATEGORY_HIP_HOP, "snoop dogg" to CATEGORY_HIP_HOP,
        "jay-z" to CATEGORY_HIP_HOP, "j. cole" to CATEGORY_HIP_HOP, "50 cent" to CATEGORY_HIP_HOP,
        "lil wayne" to CATEGORY_HIP_HOP, "future" to CATEGORY_HIP_HOP, "cardi b" to CATEGORY_HIP_HOP,
        "nicki minaj" to CATEGORY_HIP_HOP, "tyler, the creator" to CATEGORY_HIP_HOP, "juice wrld" to CATEGORY_HIP_HOP,
        "xxxtentacion" to CATEGORY_HIP_HOP, "mac miller" to CATEGORY_HIP_HOP,

        // Latin
        "bad bunny" to CATEGORY_LATIN, "j balvin" to CATEGORY_LATIN, "daddy yankee" to CATEGORY_LATIN,
        "rauw alejandro" to CATEGORY_LATIN, "maluma" to CATEGORY_LATIN, "karol g" to CATEGORY_LATIN,
        "ozuna" to CATEGORY_LATIN, "anuel aa" to CATEGORY_LATIN, "shakira" to CATEGORY_LATIN,
        "rosalia" to CATEGORY_LATIN, "peso pluma" to CATEGORY_LATIN, "feid" to CATEGORY_LATIN,
        "don omar" to CATEGORY_LATIN, "luis fonsi" to CATEGORY_LATIN, "enrique iglesias" to CATEGORY_LATIN,
        "romeo santos" to CATEGORY_LATIN, "marc anthony" to CATEGORY_LATIN, "natanael cano" to CATEGORY_LATIN,

        // Electronic
        "daft punk" to CATEGORY_ELECTRONIC, "avicii" to CATEGORY_ELECTRONIC, "calvin harris" to CATEGORY_ELECTRONIC,
        "david guetta" to CATEGORY_ELECTRONIC, "marshmello" to CATEGORY_ELECTRONIC, "skrillex" to CATEGORY_ELECTRONIC,
        "martin garrix" to CATEGORY_ELECTRONIC, "the chainsmokers" to CATEGORY_ELECTRONIC, "tiësto" to CATEGORY_ELECTRONIC,
        "deadmau5" to CATEGORY_ELECTRONIC, "swedish house mafia" to CATEGORY_ELECTRONIC, "kygo" to CATEGORY_ELECTRONIC,
        "alan walker" to CATEGORY_ELECTRONIC, "zedd" to CATEGORY_ELECTRONIC, "armin van buuren" to CATEGORY_ELECTRONIC,

        // Classical
        "mozart" to CATEGORY_CLASSICAL, "beethoven" to CATEGORY_CLASSICAL, "bach" to CATEGORY_CLASSICAL,
        "chopin" to CATEGORY_CLASSICAL, "vivaldi" to CATEGORY_CLASSICAL, "tchaikovsky" to CATEGORY_CLASSICAL,
        "brahms" to CATEGORY_CLASSICAL, "debussy" to CATEGORY_CLASSICAL, "handel" to CATEGORY_CLASSICAL,
        "rachmaninoff" to CATEGORY_CLASSICAL,

        // Soundtrack
        "hans zimmer" to CATEGORY_SOUNDTRACK, "john williams" to CATEGORY_SOUNDTRACK,
        "ennio morricone" to CATEGORY_SOUNDTRACK, "howard shore" to CATEGORY_SOUNDTRACK,
        "ludwig göransson" to CATEGORY_SOUNDTRACK, "alan silvestri" to CATEGORY_SOUNDTRACK,
        "joe hisaishi" to CATEGORY_SOUNDTRACK, "nobuo uematsu" to CATEGORY_SOUNDTRACK,
        "koji kondo" to CATEGORY_SOUNDTRACK, "ramin djawadi" to CATEGORY_SOUNDTRACK
    )

    /**
     * Normalizes a raw genre string into a major umbrella category.
     */
    fun toMacroCategory(rawGenre: String): String {
        val trimmed = rawGenre.trim()
        if (trimmed.isEmpty()) return ""

        // Check for numeric ID3v1 codes
        val numericMatch = NUMERIC_GENRE_REGEX.matchEntire(trimmed)
        if (numericMatch != null) {
            val code = numericMatch.groupValues[1].toIntOrNull()
            if (code != null && code in ID3V1_GENRES.indices) {
                return toMacroCategory(ID3V1_GENRES[code])
            }
        }

        val lower = trimmed.lowercase(Locale.ROOT)
            .replace(Regex("""^\((.+)\)$"""), "$1")
            .trim()

        CANONICAL_MAP[lower]?.let { canonical ->
            return when (canonical) {
                "Alternative Rock", "Classic Rock", "Hard Rock", "Indie Rock", "Punk Rock",
                "Post-Punk", "Grunge", "Progressive Rock", "Psychedelic Rock" -> CATEGORY_ROCK
                "Heavy Metal", "Death Metal", "Black Metal", "Nu-Metal" -> CATEGORY_METAL
                else -> canonical
            }
        }

        // Pattern-based heuristic fallbacks
        return when {
            lower.contains("pop") -> CATEGORY_POP
            lower.contains("metal") || lower.contains("deathcore") -> CATEGORY_METAL
            lower.contains("rock") || lower.contains("punk") || lower.contains("grunge") -> CATEGORY_ROCK
            lower.contains("rap") || lower.contains("hip-hop") || lower.contains("hiphop") || lower.contains("trap") -> CATEGORY_HIP_HOP
            lower.contains("electro") || lower.contains("house") || lower.contains("techno") ||
                lower.contains("trance") || lower.contains("dubstep") || lower.contains("edm") ||
                lower.contains("synth") || lower.contains("ambient") || lower.contains("lo-fi") -> CATEGORY_ELECTRONIC
            lower.contains("reggaeton") || lower.contains("salsa") || lower.contains("bachata") ||
                lower.contains("cumbia") || lower.contains("latin") || lower.contains("corrido") -> CATEGORY_LATIN
            lower.contains("r&b") || lower.contains("rnb") || lower.contains("soul") || lower.contains("funk") -> CATEGORY_RNB_SOUL
            lower.contains("classic") || lower.contains("orchestr") || lower.contains("symphon") -> CATEGORY_CLASSICAL
            lower.contains("soundtrack") || lower.contains("ost") || lower.contains("score") -> CATEGORY_SOUNDTRACK
            lower.contains("jazz") || lower.contains("blues") -> CATEGORY_JAZZ_BLUES
            lower.contains("country") || lower.contains("folk") || lower.contains("acoustic") -> CATEGORY_COUNTRY_FOLK
            lower.contains("reggae") || lower.contains("ska") || lower.contains("dancehall") -> CATEGORY_REGGAE
            else -> toTitleCase(trimmed)
        }
    }

    /**
     * Infers genre category based on artist or title when metadata is missing or unknown.
     */
    fun inferGenre(artist: String?, title: String? = null): String? {
        if (!artist.isNullOrBlank()) {
            val lowerArtist = artist.lowercase(Locale.ROOT).trim()
            ARTIST_CATEGORY_MAP[lowerArtist]?.let { return it }
            for ((key, cat) in ARTIST_CATEGORY_MAP) {
                if (lowerArtist.contains(key)) return cat
            }
        }
        if (!title.isNullOrBlank()) {
            val lowerTitle = title.lowercase(Locale.ROOT)
            when {
                lowerTitle.contains("ost") || lowerTitle.contains("soundtrack") || lowerTitle.contains("theme") -> return CATEGORY_SOUNDTRACK
                lowerTitle.contains("lo-fi") || lowerTitle.contains("lofi") || lowerTitle.contains("remix") -> return CATEGORY_ELECTRONIC
                lowerTitle.contains("symphony") || lowerTitle.contains("concerto") || lowerTitle.contains("sonata") -> return CATEGORY_CLASSICAL
            }
        }
        return null
    }

    /**
     * Splits a raw genre tag and maps each part to its umbrella macro category.
     * Deduplicates the categories and discards "unknown" placeholders.
     */
    fun splitAndNormalize(raw: String?, artist: String? = null, title: String? = null): List<String> {
        val splitGenres = GenreParser.split(raw)
        val seen = LinkedHashSet<String>()
        val result = mutableListOf<String>()

        for (item in splitGenres) {
            val category = toMacroCategory(item)
            val lowerCat = category.lowercase(Locale.ROOT)
            if (category.isNotBlank() &&
                lowerCat != "unknown" &&
                lowerCat != "<unknown>" &&
                seen.add(lowerCat)
            ) {
                result.add(category)
            }
        }

        // If untagged or unknown, attempt heuristic inference
        if (result.isEmpty()) {
            inferGenre(artist, title)?.let { inferred ->
                result.add(inferred)
            }
        }

        return result
    }

    /**
     * Checks if a song matches a given target macro category.
     */
    fun matchesCategory(rawGenre: String?, artist: String?, title: String?, targetCategory: String): Boolean {
        val target = targetCategory.trim().lowercase(Locale.ROOT)
        val categories = splitAndNormalize(rawGenre, artist, title)
        return categories.any { it.lowercase(Locale.ROOT) == target }
    }

    /** Legacy normalizer for backwards compatibility */
    fun normalize(rawGenre: String): String = toMacroCategory(rawGenre)

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
