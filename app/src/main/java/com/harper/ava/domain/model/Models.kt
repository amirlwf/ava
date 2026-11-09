package com.harper.ava.domain.model

/** A single audio track. The source of truth for identity is the MediaStore id. */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    val albumArtUri: String? = null,
    val dateAddedMs: Long = 0L,
    val trackNumber: Int? = null,
    val genre: String? = null,
    val year: Int? = null,
    /** MediaStore RELATIVE_PATH, e.g. "Music/Rock/" — the Folders tab groups by it. */
    val folderPath: String? = null,
    /** MediaStore DISPLAY_NAME, e.g. "song.mp3" — used for lyrics lookup and details. */
    val fileName: String? = null,
    val isFavorite: Boolean = false,
)

data class Playlist(
    val id: Long,
    val name: String,
    val createdAtMs: Long,
    val songCount: Int = 0,
    /** Cover art of the first song that has art (null when no song has art). */
    val coverArtUri: String? = null,
)
