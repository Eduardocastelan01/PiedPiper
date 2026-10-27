package com.lostf1sh.pixelplayeross.data.gdrive

data class GoogleDriveFile(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val createdTime: Long = 0L,
    val modifiedTime: Long = 0L,
    val thumbnailLink: String? = null
) {
    /**
     * Attempts to parse artist and title from standard naming conventions:
     * "Artist - Title.ext", "Artist - Title", or falls back to clean filename.
     */
    val parsedArtistAndTitle: Pair<String, String>
        get() {
            val baseName = name.substringBeforeLast('.')
            if (baseName.contains(" - ")) {
                val parts = baseName.split(" - ", limit = 2)
                val artist = parts[0].trim()
                val title = parts[1].trim()
                if (artist.isNotBlank() && title.isNotBlank()) {
                    return Pair(artist, title)
                }
            }
            return Pair("Google Drive", baseName.ifBlank { name })
        }
}
