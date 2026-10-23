package com.lostf1sh.pixelplayeross.utils

import java.text.Normalizer
import java.util.Locale

/**
 * Splits and compares the free-text genre tags stored on songs.
 *
 * One genre value often carries several genres: "Alternative; Indie Rock" (Picard, MusicBee,
 * foobar2000), "Rock, Pop", "House|Electronic", Mp3tag's "Rock\Pop" or a NUL-separated ID3v2.4
 * list. Counting, browsing and display all go through [split] so each of those shows up as its
 * own genre instead of one combined tile. '/' and '&' are not separators on purpose: "Rap/Hip-Hop",
 * "R&B/Soul" and "Drum & Bass" are single genres in iTunes and Deezer tags.
 */
object GenreParser {

    /** Joins the values of a multi-value genre tag in the same form [split] reads back. */
    const val VALUE_SEPARATOR = "; "

    private val SEPARATOR_REGEX = Regex("""\s*[;,|\\\u0000]\s*""")
    private val WHITESPACE_REGEX = Regex("""\s+""")

    /**
     * Genres the Jellyfin and Navidrome sync store for songs the server sent without one. They stay
     * browsable as tiles, but name the source rather than the music, so [dominantGenre] skips them.
     */
    const val JELLYFIN_PLACEHOLDER = "Jellyfin"
    const val NAVIDROME_PLACEHOLDER = "Navidrome"

    /** Placeholders tag editors and MediaStore write for a missing genre. */
    private val UNKNOWN_VALUES = setOf("unknown", "<unknown>", "unknown genre")

    private val SOURCE_PLACEHOLDER_KEYS = setOf(key(JELLYFIN_PLACEHOLDER), key(NAVIDROME_PLACEHOLDER))

    /**
     * Every character that [split] drops when it stands alone: the separators, '&' and all
     * whitespace, including tabs and Unicode spaces. The Unknown query uses SQL TRIM with this
     * list to find values that may hold no genre, so a character missing here would hide those
     * songs from it. NUL is left out because SQLite reads a TRIM character list only up to its
     * first NUL.
     */
    val noGenreCharacters: String by lazy {
        buildString {
            append(";,|\\&")
            for (code in 1..Char.MAX_VALUE.code) {
                val char = code.toChar()
                if (char.isWhitespace()) append(char)
            }
        }
    }

    /**
     * The distinct genres in [raw], in tag order. Blank values and "unknown" placeholders are
     * dropped, so an empty result means the song has no genre.
     */
    fun split(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val seenKeys = HashSet<String>()
        return raw.split(SEPARATOR_REGEX).mapNotNull { token ->
            // Discogs-style "Folk, World, & Country" leaves a dangling "& Country".
            val name = token.trim().trimStart('&').trim().replace(WHITESPACE_REGEX, " ")
            when {
                name.isEmpty() -> null
                name.lowercase(Locale.ROOT) in UNKNOWN_VALUES -> null
                seenKeys.add(key(name)) -> name
                else -> null
            }
        }
    }

    /**
     * The stable identity of a genre name, also used as its [com.lostf1sh.pixelplayeross.data.model.Genre.id].
     * Case, spacing and Unicode composition differences collapse to the same key. Locale.ROOT keeps
     * "INDIE" from becoming a dotless "ındie" for Turkish users.
     */
    fun key(name: String): String =
        Normalizer.normalize(name.trim(), Normalizer.Form.NFC)
            .replace(WHITESPACE_REGEX, " ")
            .lowercase(Locale.ROOT)
            .replace(' ', '_')
            .replace('/', '_')

    /** Whether one of the genres in [raw] is [genre], compared by [key]. */
    fun matches(raw: String?, genre: String): Boolean {
        val target = key(genre)
        return split(raw).any { key(it) == target }
    }

    /**
     * Joins the separate values of a multi-value genre field, or null when none is set. A single
     * value is kept exactly as written so re-reading an untouched file does not rewrite it.
     */
    fun joinValues(values: Iterable<String?>): String? {
        val distinct = values
            .mapNotNull { value -> value?.trim()?.takeIf(String::isNotEmpty) }
            .distinctBy { it.lowercase(Locale.ROOT) }
        return distinct.joinToString(VALUE_SEPARATOR).ifEmpty { null }
    }

    /**
     * The genre most songs in [rawGenres] share, counting each genre of a multi-genre song, for
     * labels such as an album's "House · 2016". Ties go to the genre seen first; null when no
     * song has a genre other than a cloud source placeholder.
     */
    fun dominantGenre(rawGenres: Iterable<String?>): String? {
        val counts = LinkedHashMap<String, Int>()
        val names = HashMap<String, String>()
        rawGenres.forEach { raw ->
            split(raw).forEach { name ->
                val genreKey = key(name)
                if (genreKey in SOURCE_PLACEHOLDER_KEYS) return@forEach
                counts[genreKey] = (counts[genreKey] ?: 0) + 1
                names.putIfAbsent(genreKey, name)
            }
        }
        // maxByOrNull keeps the first of equal maxima, and counts iterates in first-seen order.
        return counts.maxByOrNull { it.value }?.key?.let(names::get)
    }

    /**
     * A SQL LIKE pattern that matches every stored value [matches] would accept for [genre], plus
     * some it would not; callers filter the rows with [matches] afterwards. Only ASCII letters,
     * digits and plain punctuation are kept: LIKE folds ASCII case only, and spacing, '/' and
     * non-ASCII letters can be spelled differently under the same [key], so those become '%'.
     * That also leaves no '%', '_' or '\' from the name to escape.
     */
    fun likePattern(genre: String): String = buildString {
        append('%')
        genre.forEach { char ->
            val keep = char.code < 128 && (char.isLetterOrDigit() || char in LIKE_SAFE_PUNCTUATION)
            if (keep) append(char) else if (last() != '%') append('%')
        }
        if (last() != '%') append('%')
    }

    private const val LIKE_SAFE_PUNCTUATION = "-'&.+!#()[]{}:\"*?@$^=~<>`"
}
