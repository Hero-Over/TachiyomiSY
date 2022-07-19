package eu.kanade.domain.manga.model

import eu.kanade.data.listOfStringsAdapter
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.widget.ExtendedNavigationView
import tachiyomi.source.model.MangaInfo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.Serializable
import eu.kanade.tachiyomi.data.database.models.Manga as DbManga

data class Manga(
    val id: Long,
    val source: Long,
    val favorite: Boolean,
    val lastUpdate: Long,
    val dateAdded: Long,
    val viewerFlags: Long,
    val chapterFlags: Long,
    val coverLastModified: Long,
    val url: String,
    // SY -->
    val ogTitle: String,
    val ogArtist: String?,
    val ogAuthor: String?,
    val ogDescription: String?,
    val ogGenre: List<String>?,
    val ogStatus: Long,
    // SY <--
    val thumbnailUrl: String?,
    val initialized: Boolean,
    // SY -->
    val filteredScanlators: List<String>?,
    // SY <--
) : Serializable {

    // SY -->
    private val customMangaInfo = if (favorite) {
        customMangaManager.getManga(this)
    } else null

    val title: String
        get() = customMangaInfo?.title ?: ogTitle

    val author: String?
        get() = customMangaInfo?.author ?: ogAuthor

    val artist: String?
        get() = customMangaInfo?.artist ?: ogArtist

    val description: String?
        get() = customMangaInfo?.description ?: ogDescription

    val genre: List<String>?
        get() = customMangaInfo?.genre ?: ogGenre

    val status: Long
        get() = customMangaInfo?.statusLong ?: ogStatus
    // SY <--

    val sorting: Long
        get() = chapterFlags and CHAPTER_SORTING_MASK

    val displayMode: Long
        get() = chapterFlags and CHAPTER_DISPLAY_MASK

    val unreadFilterRaw: Long
        get() = chapterFlags and CHAPTER_UNREAD_MASK

    val downloadedFilterRaw: Long
        get() = chapterFlags and CHAPTER_DOWNLOADED_MASK

    val bookmarkedFilterRaw: Long
        get() = chapterFlags and CHAPTER_BOOKMARKED_MASK

    val unreadFilter: TriStateFilter
        get() = when (unreadFilterRaw) {
            CHAPTER_SHOW_UNREAD -> TriStateFilter.ENABLED_IS
            CHAPTER_SHOW_READ -> TriStateFilter.ENABLED_NOT
            else -> TriStateFilter.DISABLED
        }

    val downloadedFilter: TriStateFilter
        get() {
            if (forceDownloaded()) return TriStateFilter.ENABLED_IS
            return when (downloadedFilterRaw) {
                CHAPTER_SHOW_DOWNLOADED -> TriStateFilter.ENABLED_IS
                CHAPTER_SHOW_NOT_DOWNLOADED -> TriStateFilter.ENABLED_NOT
                else -> TriStateFilter.DISABLED
            }
        }

    val bookmarkedFilter: TriStateFilter
        get() = when (bookmarkedFilterRaw) {
            CHAPTER_SHOW_BOOKMARKED -> TriStateFilter.ENABLED_IS
            CHAPTER_SHOW_NOT_BOOKMARKED -> TriStateFilter.ENABLED_NOT
            else -> TriStateFilter.DISABLED
        }

    fun chaptersFiltered(): Boolean {
        return unreadFilter != TriStateFilter.DISABLED ||
            downloadedFilter != TriStateFilter.DISABLED ||
            bookmarkedFilter != TriStateFilter.DISABLED
    }

    fun forceDownloaded(): Boolean {
        return favorite && Injekt.get<PreferencesHelper>().downloadedOnly().get()
    }

    fun sortDescending(): Boolean {
        return chapterFlags and CHAPTER_SORT_DIR_MASK == CHAPTER_SORT_DESC
    }

    fun toSManga(): SManga = SManga.create().also {
        it.url = url
        it.title = title
        it.artist = artist
        it.author = author
        it.description = description
        it.genre = genre.orEmpty().joinToString()
        it.status = status.toInt()
        it.thumbnail_url = thumbnailUrl
        it.initialized = initialized
    }

    companion object {
        // Generic filter that does not filter anything
        const val SHOW_ALL = 0x00000000L

        const val CHAPTER_SORT_DESC = 0x00000000L
        const val CHAPTER_SORT_ASC = 0x00000001L
        const val CHAPTER_SORT_DIR_MASK = 0x00000001L

        const val CHAPTER_SHOW_UNREAD = 0x00000002L
        const val CHAPTER_SHOW_READ = 0x00000004L
        const val CHAPTER_UNREAD_MASK = 0x00000006L

        const val CHAPTER_SHOW_DOWNLOADED = 0x00000008L
        const val CHAPTER_SHOW_NOT_DOWNLOADED = 0x00000010L
        const val CHAPTER_DOWNLOADED_MASK = 0x00000018L

        const val CHAPTER_SHOW_BOOKMARKED = 0x00000020L
        const val CHAPTER_SHOW_NOT_BOOKMARKED = 0x00000040L
        const val CHAPTER_BOOKMARKED_MASK = 0x00000060L

        const val CHAPTER_SORTING_SOURCE = 0x00000000L
        const val CHAPTER_SORTING_NUMBER = 0x00000100L
        const val CHAPTER_SORTING_UPLOAD_DATE = 0x00000200L
        const val CHAPTER_SORTING_MASK = 0x00000300L

        const val CHAPTER_DISPLAY_NAME = 0x00000000L
        const val CHAPTER_DISPLAY_NUMBER = 0x00100000L
        const val CHAPTER_DISPLAY_MASK = 0x00100000L

        fun create() = Manga(
            id = -1L,
            url = "",
            // Sy -->
            ogTitle = "",
            // SY <--
            source = -1L,
            favorite = false,
            lastUpdate = -1L,
            dateAdded = -1L,
            viewerFlags = -1L,
            chapterFlags = -1L,
            coverLastModified = -1L,
            // SY -->
            ogArtist = null,
            ogAuthor = null,
            ogDescription = null,
            ogGenre = null,
            ogStatus = 0L,
            // SY <--
            thumbnailUrl = null,
            initialized = false,
            // SY -->
            filteredScanlators = null,
            // SY <--
        )

        // SY -->
        private val customMangaManager: CustomMangaManager by injectLazy()
        // SY <--
    }
}

enum class TriStateFilter {
    DISABLED, // Disable filter
    ENABLED_IS, // Enabled with "is" filter
    ENABLED_NOT, // Enabled with "not" filter
}

fun TriStateFilter.toTriStateGroupState(): ExtendedNavigationView.Item.TriStateGroup.State {
    return when (this) {
        TriStateFilter.DISABLED -> ExtendedNavigationView.Item.TriStateGroup.State.IGNORE
        TriStateFilter.ENABLED_IS -> ExtendedNavigationView.Item.TriStateGroup.State.INCLUDE
        TriStateFilter.ENABLED_NOT -> ExtendedNavigationView.Item.TriStateGroup.State.EXCLUDE
    }
}

// TODO: Remove when all deps are migrated
fun Manga.toDbManga(): DbManga = MangaImpl().also {
    it.id = id
    it.source = source
    it.favorite = favorite
    it.last_update = lastUpdate
    it.date_added = dateAdded
    it.viewer_flags = viewerFlags.toInt()
    it.chapter_flags = chapterFlags.toInt()
    it.cover_last_modified = coverLastModified
    it.url = url
    // SY -->
    it.title = ogTitle
    it.artist = ogArtist
    it.author = ogAuthor
    it.description = ogDescription
    it.genre = ogGenre?.let(listOfStringsAdapter::encode)
    it.status = ogStatus.toInt()
    // SY <--
    it.thumbnail_url = thumbnailUrl
    it.initialized = initialized
}

fun Manga.toMangaInfo(): MangaInfo = MangaInfo(
    // SY -->
    artist = ogArtist ?: "",
    author = ogAuthor ?: "",
    cover = thumbnailUrl ?: "",
    description = ogDescription ?: "",
    genres = ogGenre ?: emptyList(),
    key = url,
    status = ogStatus.toInt(),
    title = ogTitle,
    // SY <--
)

fun Manga.toMangaUpdate(): MangaUpdate {
    return MangaUpdate(
        id = id,
        source = source,
        favorite = favorite,
        lastUpdate = lastUpdate,
        dateAdded = dateAdded,
        viewerFlags = viewerFlags,
        chapterFlags = chapterFlags,
        coverLastModified = coverLastModified,
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = genre,
        status = status,
        thumbnailUrl = thumbnailUrl,
        initialized = initialized,
    )
}

fun Manga.isLocal(): Boolean = source == LocalSource.ID

fun Manga.hasCustomCover(coverCache: CoverCache = Injekt.get()): Boolean {
    return coverCache.getCustomCoverFile(id).exists()
}
