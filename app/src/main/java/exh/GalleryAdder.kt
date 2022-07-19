package exh

import android.content.Context
import androidx.core.net.toUri
import eu.kanade.domain.chapter.interactor.GetChapter
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.InsertManga
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.toMangaInfo
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.toSChapter
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.source.online.all.EHentai
import exh.log.xLogStack
import exh.source.getMainSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GalleryAdder(
    private val getManga: GetManga = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val insertManga: InsertManga = Injekt.get(),
    private val getChapter: GetChapter = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) {

    private val filters: Pair<Set<String>, Set<Long>> = Injekt.get<PreferencesHelper>().run {
        enabledLanguages().get() to disabledSources().get().map { it.toLong() }.toSet()
    }

    private val logger = xLogStack()

    fun pickSource(url: String): List<UrlImportableSource> {
        val uri = url.toUri()
        return sourceManager.getVisibleCatalogueSources()
            .mapNotNull { it.getMainSource<UrlImportableSource>() }
            .filter {
                it.lang in filters.first && it.id !in filters.second && try {
                    it.matchesUri(uri)
                } catch (e: Exception) {
                    false
                }
            }
    }

    suspend fun addGallery(
        context: Context,
        url: String,
        fav: Boolean = false,
        forceSource: UrlImportableSource? = null,
        throttleFunc: suspend () -> Unit = {},
    ): GalleryAddEvent {
        logger.d(context.getString(R.string.gallery_adder_importing_manga, url, fav.toString(), forceSource))
        try {
            val uri = url.toUri()

            // Find matching source
            val source = if (forceSource != null) {
                try {
                    if (forceSource.matchesUri(uri)) forceSource
                    else return GalleryAddEvent.Fail.UnknownSource(url, context)
                } catch (e: Exception) {
                    logger.e(context.getString(R.string.gallery_adder_source_uri_must_match), e)
                    return GalleryAddEvent.Fail.UnknownType(url, context)
                }
            } else {
                sourceManager.getVisibleCatalogueSources()
                    .mapNotNull { it.getMainSource<UrlImportableSource>() }
                    .find {
                        it.lang in filters.first && it.id !in filters.second && try {
                            it.matchesUri(uri)
                        } catch (e: Exception) {
                            false
                        }
                    } ?: return GalleryAddEvent.Fail.UnknownSource(url, context)
            }

            val realChapterUrl = try {
                source.mapUrlToChapterUrl(uri)
            } catch (e: Exception) {
                logger.e(context.getString(R.string.gallery_adder_uri_map_to_chapter_error), e)
                null
            }

            val cleanedChapterUrl = if (realChapterUrl != null) {
                try {
                    source.cleanChapterUrl(realChapterUrl)
                } catch (e: Exception) {
                    logger.e(context.getString(R.string.gallery_adder_uri_clean_error), e)
                    null
                }
            } else null

            val chapterMangaUrl = if (realChapterUrl != null) {
                source.mapChapterUrlToMangaUrl(realChapterUrl.toUri())
            } else null

            // Map URL to manga URL
            val realMangaUrl = try {
                chapterMangaUrl ?: source.mapUrlToMangaUrl(uri)
            } catch (e: Exception) {
                logger.e(context.getString(R.string.gallery_adder_uri_map_to_manga_error), e)
                null
            } ?: return GalleryAddEvent.Fail.UnknownType(url, context)

            // Clean URL
            val cleanedMangaUrl = try {
                source.cleanMangaUrl(realMangaUrl)
            } catch (e: Exception) {
                logger.e(context.getString(R.string.gallery_adder_uri_clean_error), e)
                null
            } ?: return GalleryAddEvent.Fail.UnknownType(url, context)

            // Use manga in DB if possible, otherwise, make a new manga
            var manga = getManga.await(cleanedMangaUrl, source.id)
                ?: run {
                    insertManga.await(
                        Manga.create().copy(
                            source = source.id,
                            url = cleanedMangaUrl,
                        ),
                    )
                    getManga.await(cleanedMangaUrl, source.id)!!
                }

            // Fetch and copy details
            val newManga = source.getMangaDetails(manga.toMangaInfo())
            updateManga.awaitUpdateFromSource(manga, newManga, false)
            manga = getManga.await(manga.id)!!

            if (fav) {
                updateManga.awaitUpdateFavorite(manga.id, true)
                manga = manga.copy(favorite = true)
            }

            // Fetch and copy chapters
            try {
                val chapterList = if (source is EHentai) {
                    source.getChapterList(manga.toMangaInfo(), throttleFunc)
                } else {
                    source.getChapterList(manga.toMangaInfo())
                }.map { it.toSChapter() }

                if (chapterList.isNotEmpty()) {
                    syncChaptersWithSource.await(chapterList, manga, source)
                }
            } catch (e: Exception) {
                logger.w(context.getString(R.string.gallery_adder_chapter_fetch_error, manga.title), e)
                return GalleryAddEvent.Fail.Error(url, context.getString(R.string.gallery_adder_chapter_fetch_error, url))
            }

            return if (cleanedChapterUrl != null) {
                val chapter = getChapter.await(cleanedChapterUrl, manga.id)
                if (chapter != null) {
                    GalleryAddEvent.Success(url, manga, context, chapter)
                } else {
                    GalleryAddEvent.Fail.Error(url, context.getString(R.string.gallery_adder_could_not_identify_chapter, url))
                }
            } else {
                GalleryAddEvent.Success(url, manga, context)
            }
        } catch (e: Exception) {
            logger.w(context.getString(R.string.gallery_adder_could_not_add_manga, url), e)

            if (e is EHentai.GalleryNotFoundException) {
                return GalleryAddEvent.Fail.NotFound(url, context)
            }

            return GalleryAddEvent.Fail.Error(
                url,
                ((e.message ?: "Unknown error!") + " (Gallery: $url)").trim(),
            )
        }
    }
}

sealed class GalleryAddEvent {
    abstract val logMessage: String
    abstract val galleryUrl: String
    open val galleryTitle: String? = null

    class Success(
        override val galleryUrl: String,
        val manga: Manga,
        val context: Context,
        val chapter: Chapter? = null,
    ) : GalleryAddEvent() {
        override val galleryTitle = manga.title
        override val logMessage = context.getString(R.string.batch_add_success_log_message, galleryTitle)
    }

    sealed class Fail : GalleryAddEvent() {
        class UnknownType(override val galleryUrl: String, val context: Context) : Fail() {
            override val logMessage = context.getString(R.string.batch_add_unknown_type_log_message, galleryUrl)
        }

        open class Error(
            override val galleryUrl: String,
            override val logMessage: String,
        ) : Fail()

        class NotFound(galleryUrl: String, context: Context) :
            Error(galleryUrl, context.getString(R.string.batch_add_not_exist_log_message, galleryUrl))

        class UnknownSource(override val galleryUrl: String, val context: Context) : Fail() {
            override val logMessage = context.getString(R.string.batch_add_unknown_source_log_message, galleryUrl)
        }
    }
}
