package eu.kanade.tachiyomi.ui.reader.loader

import android.content.Context
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.util.system.logcat
import exh.debug.DebugFunctions.prefs
import exh.merged.sql.models.MergedMangaReference
import rx.Completable
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers

/**
 * Loader used to retrieve the [PageLoader] for a given chapter.
 */
class ChapterLoader(
    private val context: Context,
    private val downloadManager: DownloadManager,
    private val manga: Manga,
    private val source: Source,
    // SY -->
    private val sourceManager: SourceManager,
    private val mergedReferences: List<MergedMangaReference>,
    private val mergedManga: Map<Long, Manga>,
// SY <--
) {

    /**
     * Returns a completable that assigns the page loader and loads the its pages. It just
     * completes if the chapter is already loaded.
     */
    fun loadChapter(chapter: ReaderChapter): Completable {
        if (chapterIsReady(chapter)) {
            return Completable.complete()
        }

        return Observable.just(chapter)
            .doOnNext { chapter.state = ReaderChapter.State.Loading }
            .observeOn(Schedulers.io())
            .flatMap { readerChapter ->
                logcat { "Loading pages for ${chapter.chapter.name}" }

                val loader = getPageLoader(readerChapter)

                loader.getPages().take(1).doOnNext { pages ->
                    pages.forEach { it.chapter = chapter }
                }.map { pages -> loader to pages }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnError { chapter.state = ReaderChapter.State.Error(it) }
            .doOnNext { (loader, pages) ->
                if (pages.isEmpty()) {
                    throw Exception(context.getString(R.string.page_list_empty_error))
                }

                chapter.pageLoader = loader // Assign here to fix race with unref
                chapter.state = ReaderChapter.State.Loaded(pages)

                // If the chapter is partially read, set the starting page to the last the user read
                // otherwise use the requested page.
                if (!chapter.chapter.read /* --> EH */ || prefs
                    .preserveReadingPosition()
                    .get() // <-- EH
                ) {
                    chapter.requestedPage = chapter.chapter.last_page_read
                }
            }
            .toCompletable()
    }

    /**
     * Checks [chapter] to be loaded based on present pages and loader in addition to state.
     */
    private fun chapterIsReady(chapter: ReaderChapter): Boolean {
        return chapter.state is ReaderChapter.State.Loaded && chapter.pageLoader != null
    }

    /**
     * Returns the page loader to use for this [chapter].
     */
    private fun getPageLoader(chapter: ReaderChapter): PageLoader {
        val dbChapter = chapter.chapter
        val isDownloaded = downloadManager.isChapterDownloaded(dbChapter.name, dbChapter.scanlator, /* SY --> */ manga.ogTitle /* SY <-- */, manga.source, skipCache = true)
        return when {
            // SY -->
            source is MergedSource -> {
                val mangaReference = mergedReferences.firstOrNull { it.mangaId == chapter.chapter.manga_id } ?: error("Merge reference null")
                val source = sourceManager.get(mangaReference.mangaSourceId) ?: error("Source ${mangaReference.mangaSourceId} was null")
                val manga = mergedManga[chapter.chapter.manga_id] ?: error("Manga for merged chapter was null")
                val isMergedMangaDownloaded = downloadManager.isChapterDownloaded(chapter.chapter.name, chapter.chapter.scanlator, manga.ogTitle, manga.source, true)
                when {
                    isMergedMangaDownloaded -> DownloadPageLoader(chapter, manga, source, downloadManager)
                    source is HttpSource -> HttpPageLoader(chapter, source)
                    source is LocalSource -> source.getFormat(chapter.chapter).let { format ->
                        when (format) {
                            is LocalSource.Format.Directory -> DirectoryPageLoader(format.file)
                            is LocalSource.Format.Zip -> ZipPageLoader(format.file)
                            is LocalSource.Format.Rar -> RarPageLoader(format.file)
                            is LocalSource.Format.Epub -> EpubPageLoader(format.file)
                        }
                    }
                    else -> error(context.getString(R.string.loader_not_implemented_error))
                }
            }
            // SY <--
            isDownloaded -> DownloadPageLoader(chapter, manga, source, downloadManager)
            source is HttpSource -> HttpPageLoader(chapter, source)
            source is LocalSource -> source.getFormat(chapter.chapter).let { format ->
                when (format) {
                    is LocalSource.Format.Directory -> DirectoryPageLoader(format.file)
                    is LocalSource.Format.Zip -> ZipPageLoader(format.file)
                    is LocalSource.Format.Rar -> RarPageLoader(format.file)
                    is LocalSource.Format.Epub -> EpubPageLoader(format.file)
                }
            }
            source is SourceManager.StubSource -> throw source.getSourceNotInstalledException()
            else -> error(context.getString(R.string.loader_not_implemented_error))
        }
    }
}
