package eu.kanade.data.manga

import eu.kanade.data.listOfStringsAndAdapter
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.data.database.models.LibraryManga

val mangaMapper: (Long, Long, String, String?, String?, String?, List<String>?, String, Long, String?, Boolean, Long?, Long?, Boolean, Long, Long, Long, Long, List<String>?) -> Manga =
    { id, source, url, artist, author, description, genre, title, status, thumbnailUrl, favorite, lastUpdate, _, initialized, viewer, chapterFlags, coverLastModified, dateAdded, filteredScanlators ->
        Manga(
            id = id,
            source = source,
            favorite = favorite,
            lastUpdate = lastUpdate ?: 0,
            dateAdded = dateAdded,
            viewerFlags = viewer,
            chapterFlags = chapterFlags,
            coverLastModified = coverLastModified,
            url = url,
            // SY -->
            ogTitle = title,
            ogArtist = artist,
            ogAuthor = author,
            ogDescription = description,
            ogGenre = genre,
            ogStatus = status,
            // SY <--
            thumbnailUrl = thumbnailUrl,
            initialized = initialized,
            // SY -->
            filteredScanlators = filteredScanlators,
            // SY <--
        )
    }

val mangaChapterMapper: (Long, Long, String, String?, String?, String?, List<String>?, String, Long, String?, Boolean, Long?, Long?, Boolean, Long, Long, Long, Long, List<String>?, Long, Long, String, String, String?, Boolean, Boolean, Long, Float, Long, Long, Long) -> Pair<Manga, Chapter> =
    { _id, source, url, artist, author, description, genre, title, status, thumbnailUrl, favorite, lastUpdate, next_update, initialized, viewerFlags, chapterFlags, coverLastModified, dateAdded, filteredScanlators, chapterId, mangaId, chapterUrl, name, scanlator, read, bookmark, lastPageRead, chapterNumber, sourceOrder, dateFetch, dateUpload ->
        Manga(
            id = _id,
            source = source,
            favorite = favorite,
            lastUpdate = lastUpdate ?: 0,
            dateAdded = dateAdded,
            viewerFlags = viewerFlags,
            chapterFlags = chapterFlags,
            coverLastModified = coverLastModified,
            url = url,
            // SY -->
            ogTitle = title,
            ogArtist = artist,
            ogAuthor = author,
            ogDescription = description,
            ogGenre = genre,
            ogStatus = status,
            // SY <--
            thumbnailUrl = thumbnailUrl,
            initialized = initialized,
            // SY -->
            filteredScanlators = filteredScanlators,
            // SY <--
        ) to Chapter(
            id = chapterId,
            mangaId = mangaId,
            read = read,
            bookmark = bookmark,
            lastPageRead = lastPageRead,
            dateFetch = dateFetch,
            sourceOrder = sourceOrder,
            url = chapterUrl,
            name = name,
            dateUpload = dateUpload,
            chapterNumber = chapterNumber,
            scanlator = scanlator,
        )
    }

val libraryManga: (Long, Long, String, String?, String?, String?, List<String>?, String, Long, String?, Boolean, Long?, Long?, Boolean, Long, Long, Long, Long, List<String>?, Long, Long, Long) -> LibraryManga =
    { _id, source, url, artist, author, description, genre, title, status, thumbnail_url, favorite, last_update, next_update, initialized, viewer, chapter_flags, cover_last_modified, date_added, filtered_scanlators, unread_count, read_count, category ->
        LibraryManga().apply {
            this.id = _id
            this.source = source
            this.url = url
            this.artist = artist
            this.author = author
            this.description = description
            this.genre = genre?.joinToString()
            this.title = title
            this.status = status.toInt()
            this.thumbnail_url = thumbnail_url
            this.favorite = favorite
            this.last_update = last_update ?: 0
            this.initialized = initialized
            this.viewer_flags = viewer.toInt()
            this.chapter_flags = chapter_flags.toInt()
            this.cover_last_modified = cover_last_modified
            this.date_added = date_added
            this.filtered_scanlators = filtered_scanlators?.let(listOfStringsAndAdapter::encode)
            this.unreadCount = unread_count.toInt()
            this.readCount = read_count.toInt()
            this.category = category.toInt()
        }
    }
