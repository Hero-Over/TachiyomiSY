package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import exh.md.utils.MdUtil
import tachiyomi.source.model.MangaInfo
import eu.kanade.domain.manga.model.Manga as DomainManga

interface Manga : SManga {

    var id: Long?

    var source: Long

    var favorite: Boolean

    // last time the chapter list changed in any way
    var last_update: Long

    var date_added: Long

    var viewer_flags: Int

    var chapter_flags: Int

    var cover_last_modified: Long

    var filtered_scanlators: String?

    fun sortDescending(): Boolean {
        return chapter_flags and DomainManga.CHAPTER_SORT_DIR_MASK.toInt() == DomainManga.CHAPTER_SORT_DESC.toInt()
    }

    fun getGenres(): List<String>? {
        if (genre.isNullOrBlank()) return null
        return genre?.split(", ")?.map { it.trim() }?.filterNot { it.isBlank() }?.distinct()
    }

    // SY -->
    fun getOriginalGenres(): List<String>? {
        return originalGenre?.split(", ")?.map { it.trim() }
    }
    // SY <--

    private fun setChapterFlags(flag: Int, mask: Int) {
        chapter_flags = chapter_flags and mask.inv() or (flag and mask)
    }

    private fun setViewerFlags(flag: Int, mask: Int) {
        viewer_flags = viewer_flags and mask.inv() or (flag and mask)
    }

    // Used to display the chapter's title one way or another
    var displayMode: Int
        get() = chapter_flags and DomainManga.CHAPTER_DISPLAY_MASK.toInt()
        set(mode) = setChapterFlags(mode, DomainManga.CHAPTER_DISPLAY_MASK.toInt())

    var readFilter: Int
        get() = chapter_flags and DomainManga.CHAPTER_UNREAD_MASK.toInt()
        set(filter) = setChapterFlags(filter, DomainManga.CHAPTER_UNREAD_MASK.toInt())

    var downloadedFilter: Int
        get() = chapter_flags and DomainManga.CHAPTER_DOWNLOADED_MASK.toInt()
        set(filter) = setChapterFlags(filter, DomainManga.CHAPTER_DOWNLOADED_MASK.toInt())

    var bookmarkedFilter: Int
        get() = chapter_flags and DomainManga.CHAPTER_BOOKMARKED_MASK.toInt()
        set(filter) = setChapterFlags(filter, DomainManga.CHAPTER_BOOKMARKED_MASK.toInt())

    var sorting: Int
        get() = chapter_flags and DomainManga.CHAPTER_SORTING_MASK.toInt()
        set(sort) = setChapterFlags(sort, DomainManga.CHAPTER_SORTING_MASK.toInt())

    var readingModeType: Int
        get() = viewer_flags and ReadingModeType.MASK
        set(readingMode) = setViewerFlags(readingMode, ReadingModeType.MASK)

    var orientationType: Int
        get() = viewer_flags and OrientationType.MASK
        set(rotationType) = setViewerFlags(rotationType, OrientationType.MASK)

    companion object {
        fun create(pathUrl: String, title: String, source: Long = 0): Manga = MangaImpl().apply {
            url = pathUrl
            this.title = title
            this.source = source
        }
    }
}

fun Manga.toMangaInfo(): MangaInfo {
    return MangaInfo(
        artist = this.artist ?: "",
        author = this.author ?: "",
        cover = this.thumbnail_url ?: "",
        description = this.description ?: "",
        genres = this.getGenres() ?: emptyList(),
        key = this.url,
        status = this.status,
        title = this.title,
    )
}

fun Manga.toDomainManga(): DomainManga? {
    val mangaId = id ?: return null
    return DomainManga(
        id = mangaId,
        source = source,
        favorite = favorite,
        lastUpdate = last_update,
        dateAdded = date_added,
        viewerFlags = viewer_flags.toLong(),
        chapterFlags = chapter_flags.toLong(),
        coverLastModified = cover_last_modified,
        url = url,
        // SY -->
        ogTitle = originalTitle,
        ogArtist = originalArtist,
        ogAuthor = originalAuthor,
        ogDescription = originalDescription,
        ogGenre = getOriginalGenres(),
        ogStatus = originalStatus.toLong(),
        // SY <--
        thumbnailUrl = thumbnail_url,
        initialized = initialized,
        // SY -->
        filteredScanlators = MdUtil.getScanlators(filtered_scanlators).toList(),
        // SY <--
    )
}
