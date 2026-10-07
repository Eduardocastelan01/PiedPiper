package com.lostf1sh.pixelplayeross.utils

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MetadataTextNormalizationTest {

    @Test
    fun keepsCorrectFrenchAndPortugueseTitles() {
        // These used to be "repaired" into U+FFFD because they contain â / Ã.
        listOf(
            "Château de ma mère",
            "Ça va à la fête, où êtes-vous ?",
            "CORAÇÃO",
            "São Paulo",
            "Ãrvore",
            "Gonçalves — Canção",
            "Ðorđe Balašević",
        ).forEach { title ->
            assertThat(title.normalizeMetadataText()).isEqualTo(title)
            assertThat(title.normalizeMetadataText()).doesNotContain("�")
        }
    }

    @Test
    fun repairsUtf8TextThatWasDecodedAsWindows1252() {
        // "à" is C3 A0 in UTF-8; A0 decodes as a non-breaking space in Windows-1252.
        assertThat("CafÃ© Ã\u00A0 la franÃ§aise".normalizeMetadataText()).isEqualTo("Café à la française")
        assertThat("Donâ€™t Stop".normalizeMetadataText()).isEqualTo("Don’t Stop")
        assertThat("SÃ£o JoÃ£o".normalizeMetadataText()).isEqualTo("São João")
    }

    @Test
    fun leavesAsciiAndReplacementCharactersAlone() {
        assertThat("Plain Title".normalizeMetadataText()).isEqualTo("Plain Title")
        assertThat("Broken � tag".normalizeMetadataText()).isEqualTo("Broken � tag")
    }
}
