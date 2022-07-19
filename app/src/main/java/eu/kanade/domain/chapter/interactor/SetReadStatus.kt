package eu.kanade.domain.chapter.interactor

import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.chapter.model.ChapterUpdate
import eu.kanade.domain.chapter.repository.ChapterRepository
import eu.kanade.domain.download.interactor.DeleteDownload
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.repository.MangaRepository
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.util.system.logcat
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import logcat.LogPriority

class SetReadStatus(
    private val preferences: PreferencesHelper,
    private val deleteDownload: DeleteDownload,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val sourceManager: SourceManager,
) {

    private val mapper = { chapter: Chapter, read: Boolean ->
        ChapterUpdate(
            read = read,
            lastPageRead = if (!read) 0 else null,
            id = chapter.id,
        )
    }

    suspend fun await(read: Boolean, vararg values: Chapter): Result = withContext(NonCancellable) f@{
        val chapters = values.filterNot { it.read == read }

        if (chapters.isEmpty()) {
            return@f Result.NoChapters
        }

        val manga = chapters.fold(mutableSetOf<Manga>()) { acc, chapter ->
            if (acc.all { it.id != chapter.mangaId }) {
                acc += mangaRepository.getMangaById(chapter.mangaId)
            }
            acc
        }

        try {
            chapterRepository.updateAll(
                chapters.map { chapter ->
                    mapper(chapter, read)
                },
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@f Result.InternalError(e)
        }

        if (read && preferences.removeAfterMarkedAsRead()) {
            manga.forEach { manga ->
                deleteDownload.awaitAll(
                    manga = manga,
                    values = chapters
                        .filter { manga.id == it.mangaId }
                        .toTypedArray(),
                )
            }
        }

        Result.Success
    }

    suspend fun await(mangaId: Long, read: Boolean): Result = withContext(NonCancellable) f@{
        return@f await(
            read = read,
            values = chapterRepository
                .getChapterByMangaId(mangaId)
                .toTypedArray(),
        )
    }

    // SY -->
    private suspend fun awaitMerged(mangaId: Long, read: Boolean) = withContext(NonCancellable) f@{
        val mergedSource = sourceManager.get(MERGED_SOURCE_ID) as MergedSource
        return@f await(
            read = read,
            values = mergedSource
                .getChapters(mangaId, dedupe = false)
                .toTypedArray(),
        )
    }

    suspend fun await(manga: Manga, read: Boolean) = if (manga.source == MERGED_SOURCE_ID) {
        awaitMerged(manga.id, read)
    } else {
        await(manga.id, read)
    }
    // SY <--

    sealed class Result {
        object Success : Result()
        object NoChapters : Result()
        data class InternalError(val error: Throwable) : Result()
    }
}
