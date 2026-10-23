package com.lostf1sh.pixelplayeross

import com.lostf1sh.pixelplayeross.presentation.navigation.Screen
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainNavItemsTest {

    @Test
    fun `navigation shows home search and library in order`() {
        assertEquals(
            listOf(Screen.Home, Screen.Search, Screen.Library),
            MainNavItems.map { it.screen },
        )
        assertEquals(
            listOf(R.string.tab_home, R.string.search, R.string.tab_library),
            MainNavItems.map { it.labelResId },
        )
    }

    @Test
    fun `every navigation label is translated in every locale`() {
        assertTranslatedEverywhere(MainNavItems.map { stringResourceName(it.labelResId) })
    }

    @Test
    fun `simplified chinese navigation labels are not left in english`() {
        val english = stringsIn("values")
        val chinese = stringsIn("values-zh-rCN")

        MainNavItems.forEach { item ->
            val name = stringResourceName(item.labelResId)
            val label = chinese[name]
            assertTrue(!label.isNullOrEmpty(), "values-zh-rCN has no text for $name")
            assertNotEquals(english[name], label, "$name is still English in values-zh-rCN")
            assertTrue(
                label!!.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN },
                "$name has no Chinese text in values-zh-rCN: $label",
            )
        }
    }

    @Test
    fun `strings shown around the navigation are translated in every locale`() {
        assertTranslatedEverywhere(
            listOf(
                R.string.setup_gate_preparing,
                R.string.home_your_mix_subtitle,
                R.string.home_your_mix_queue_name,
                R.string.song_info_label_provider,
                R.string.song_info_label_path,
                R.string.cd_playlist_artwork,
            ).map(::stringResourceName)
        )
    }

    @Test
    fun `queue names set from the library tab are translated in every locale`() {
        assertTranslatedEverywhere(
            listOf(
                R.string.tab_library,
                R.string.queue_name_liked_songs,
                R.string.queue_name_all_songs_shuffled,
                R.string.queue_name_liked_songs_shuffled,
                R.string.queue_name_selected_songs,
            ).map(::stringResourceName)
        )
    }

    private fun assertTranslatedEverywhere(names: List<String>) {
        val localeDirs = resDirectory().listFiles { file ->
            file.isDirectory && (file.name == "values" || LOCALE_DIR.matches(file.name))
        }.orEmpty()
        assertTrue(localeDirs.size > 1, "No locale folders found under ${resDirectory()}")

        localeDirs.forEach { dir ->
            val strings = stringsIn(dir.name)
            names.forEach { name ->
                assertTrue(!strings[name].isNullOrEmpty(), "${dir.name} has no text for $name")
            }
        }
    }

    private fun stringsIn(folder: String): Map<String, String> {
        val xml = File(resDirectory(), folder).listFiles { file -> file.extension == "xml" }.orEmpty()
            .joinToString("\n") { it.readText() }
        return STRING_ENTRY.findAll(xml).associate { match ->
            match.groupValues[1] to match.groupValues[2].trim()
        }
    }

    private fun stringResourceName(id: Int): String =
        R.string::class.java.fields.first { it.getInt(null) == id }.name

    private fun resDirectory(): File = sequenceOf(
        File("src/main/res"),
        File("app/src/main/res"),
    ).first { it.isDirectory }

    private companion object {
        // Translation folders such as values-ru or values-zh-rCN, not qualifiers like values-night.
        val LOCALE_DIR = Regex("""values-[a-z]{2,3}(-r[A-Z]{2})?""")
        val STRING_ENTRY = Regex("""<string name="([^"]+)"[^>]*>([^<]*)</string>""")
    }
}
