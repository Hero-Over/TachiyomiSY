package eu.kanade.tachiyomi.data.download

import android.content.Context
import com.hippo.unifile.UniFile
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.download.model.DownloadQueue
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.logcat
import exh.log.xLogE
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

/**
 * This class is used to manage chapter downloads in the application. It must be instantiated once
 * and retrieved through dependency injection. You can use this class to queue new chapters or query
 * downloaded chapters.
 *
 * @param context the application context.
 */
class DownloadManager(
    private val context: Context,
    private val getCategories: GetCategories = Injekt.get(),
) {

    private val sourceManager: SourceManager by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()

    /**
     * Downloads provider, used to retrieve the folders where the chapters are or should be stored.
     */
    val provider = DownloadProvider(context)

    /**
     * Cache of downloaded chapters.
     */
    private val cache = DownloadCache(context, provider, sourceManager)

    /**
     * Downloader whose only task is to download chapters.
     */
    private val downloader = Downloader(context, provider, cache, sourceManager)

    /**
     * Queue to delay the deletion of a list of chapters until triggered.
     */
    private val pendingDeleter = DownloadPendingDeleter(context)

    /**
     * Downloads queue, where the pending chapters are stored.
     */
    val queue: DownloadQueue
        get() = downloader.queue

    /**
     * Subject for subscribing to downloader status.
     */
    val runningRelay: BehaviorRelay<Boolean>
        get() = downloader.runningRelay

    /**
     * Tells the downloader to begin downloads.
     *
     * @return true if it's started, false otherwise (empty queue).
     */
    fun startDownloads(): Boolean {
        return downloader.start()
    }

    /**
     * Tells the downloader to stop downloads.
     *
     * @param reason an optional reason for being stopped, used to notify the user.
     */
    fun stopDownloads(reason: String? = null) {
        downloader.stop(reason)
    }

    /**
     * Tells the downloader to pause downloads.
     */
    fun pauseDownloads() {
        downloader.pause()
    }

    /**
     * Empties the download queue.
     *
     * @param isNotification value that determines if status is set (needed for view updates)
     */
    fun clearQueue(isNotification: Boolean = false) {
        downloader.clearQueue(isNotification)
    }

    fun startDownloadNow(chapterId: Long?) {
        if (chapterId == null) return
        val download = downloader.queue.find { it.chapter.id == chapterId }
        // If not in queue try to start a new download
        val toAdd = download ?: runBlocking { Download.fromChapterId(chapterId) } ?: return
        val queue = downloader.queue.toMutableList()
        download?.let { queue.remove(it) }
        queue.add(0, toAdd)
        reorderQueue(queue)
        if (isPaused()) {
            if (DownloadService.isRunning(context)) {
                downloader.start()
            } else {
                DownloadService.start(context)
            }
        }
    }

    fun isPaused() = downloader.isPaused()

    /**
     * Reorders the download queue.
     *
     * @param downloads value to set the download queue to
     */
    fun reorderQueue(downloads: List<Download>) {
        val wasRunning = downloader.isRunning

        if (downloads.isEmpty()) {
            DownloadService.stop(context)
            downloader.queue.clear()
            return
        }

        downloader.pause()
        downloader.queue.clear()
        downloader.queue.addAll(downloads)

        if (wasRunning) {
            downloader.start()
        }
    }

    /**
     * Tells the downloader to enqueue the given list of chapters.
     *
     * @param manga the manga of the chapters.
     * @param chapters the list of chapters to enqueue.
     * @param autoStart whether to start the downloader after enqueing the chapters.
     */
    fun downloadChapters(manga: Manga, chapters: List<Chapter>, autoStart: Boolean = true) {
        downloader.queueChapters(manga, chapters, autoStart)
    }

    /**
     * Builds the page list of a downloaded chapter.
     *
     * @param source the source of the chapter.
     * @param manga the manga of the chapter.
     * @param chapter the downloaded chapter.
     * @return an observable containing the list of pages from the chapter.
     */
    fun buildPageList(source: Source, manga: Manga, chapter: Chapter): Observable<List<Page>> {
        return buildPageList(provider.findChapterDir(chapter.name, chapter.scanlator, /* SY --> */ manga.ogTitle /* SY <-- */, source))
    }

    /**
     * Builds the page list of a downloaded chapter.
     *
     * @param chapterDir the file where the chapter is downloaded.
     * @return an observable containing the list of pages from the chapter.
     */
    private fun buildPageList(chapterDir: UniFile?): Observable<List<Page>> {
        return Observable.fromCallable {
            val files = chapterDir?.listFiles().orEmpty()
                .filter { "image" in it.type.orEmpty() }

            if (files.isEmpty()) {
                throw Exception(context.getString(R.string.page_list_empty_error))
            }

            files.sortedBy { it.name }
                .mapIndexed { i, file ->
                    Page(i, uri = file.uri).apply { status = Page.READY }
                }
        }
    }

    /**
     * Returns true if the chapter is downloaded.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     * @param mangaTitle the title of the manga to query.
     * @param sourceId the id of the source of the chapter.
     * @param skipCache whether to skip the directory cache and check in the filesystem.
     */
    fun isChapterDownloaded(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        sourceId: Long,
        skipCache: Boolean = false,
    ): Boolean {
        return cache.isChapterDownloaded(chapterName, chapterScanlator, mangaTitle, sourceId, skipCache)
    }

    /**
     * Returns the download from queue if the chapter is queued for download
     * else it will return null which means that the chapter is not queued for download
     *
     * @param chapter the chapter to check.
     */
    fun getChapterDownloadOrNull(chapter: Chapter): Download? {
        return downloader.queue
            .firstOrNull { it.chapter.id == chapter.id && it.chapter.manga_id == chapter.manga_id }
    }

    /**
     * Returns the amount of downloaded chapters for a manga.
     *
     * @param manga the manga to check.
     */
    fun getDownloadCount(manga: Manga): Int {
        return cache.getDownloadCount(manga)
    }

    /**
     * Calls delete chapter, which deletes a temp download.
     *
     * @param download the download to cancel.
     */
    fun deletePendingDownload(download: Download) {
        deleteChapters(listOf(download.chapter), download.manga, download.source, true)
    }

    fun deletePendingDownloads(vararg downloads: Download) {
        val downloadsByManga = downloads.groupBy { it.manga.id }
        downloadsByManga.map { entry ->
            val manga = entry.value.first().manga
            val source = entry.value.first().source
            deleteChapters(entry.value.map { it.chapter }, manga, source, true)
        }
    }

    /**
     * Deletes the directories of a list of downloaded chapters.
     *
     * @param chapters the list of chapters to delete.
     * @param manga the manga of the chapters.
     * @param source the source of the chapters.
     * @param isCancelling true if it's simply cancelling a download
     */
    fun deleteChapters(chapters: List<Chapter>, manga: Manga, source: Source, isCancelling: Boolean = false): List<Chapter> {
        val filteredChapters = if (isCancelling) {
            chapters
        } else {
            getChaptersToDelete(chapters, manga)
        }

        launchIO {
            removeFromDownloadQueue(filteredChapters)

            val chapterDirs = provider.findChapterDirs(filteredChapters, manga, source)
            chapterDirs.forEach { it.delete() }
            cache.removeChapters(filteredChapters, manga)
            if (cache.getDownloadCount(manga) == 0) { // Delete manga directory if empty
                chapterDirs.firstOrNull()?.parentFile?.delete()
            }
        }
        return filteredChapters
    }

    private fun removeFromDownloadQueue(chapters: List<Chapter>) {
        val wasRunning = downloader.isRunning
        if (wasRunning) {
            downloader.pause()
        }

        downloader.queue.remove(chapters)

        if (wasRunning) {
            if (downloader.queue.isEmpty()) {
                DownloadService.stop(context)
                downloader.stop()
            } else if (downloader.queue.isNotEmpty()) {
                downloader.start()
            }
        }
    }

    // SY -->
    /**
     * return the list of all manga folders
     */
    fun getMangaFolders(source: Source): List<UniFile> {
        return provider.findSourceDir(source)?.listFiles()?.toList().orEmpty()
    }

    /**
     * Deletes the directories of chapters that were read or have no match
     *
     * @param allChapters the list of chapters to delete.
     * @param manga the manga of the chapters.
     * @param source the source of the chapters.
     */
    fun cleanupChapters(allChapters: List<Chapter>, manga: Manga, source: Source, removeRead: Boolean, removeNonFavorite: Boolean): Int {
        var cleaned = 0

        if (removeNonFavorite && !manga.favorite) {
            val mangaFolder = provider.getMangaDir(/* SY --> */ manga.ogTitle /* SY <-- */, source)
            cleaned += 1 + mangaFolder.listFiles().orEmpty().size
            mangaFolder.delete()
            cache.removeManga(manga)
            return cleaned
        }

        val filesWithNoChapter = provider.findUnmatchedChapterDirs(allChapters, manga, source)
        cleaned += filesWithNoChapter.size
        cache.removeFolders(filesWithNoChapter.mapNotNull { it.name }, manga)
        filesWithNoChapter.forEach { it.delete() }

        if (removeRead) {
            val readChapters = allChapters.filter { it.read }
            val readChapterDirs = provider.findChapterDirs(readChapters, manga, source)
            readChapterDirs.forEach { it.delete() }
            cleaned += readChapterDirs.size
            cache.removeChapters(readChapters, manga)
        }

        if (cache.getDownloadCount(manga) == 0) {
            val mangaFolder = provider.getMangaDir(/* SY --> */ manga.ogTitle /* SY <-- */, source)
            if (!mangaFolder.listFiles().isNullOrEmpty()) {
                mangaFolder.delete()
                cache.removeManga(manga)
            } else {
                xLogE("Cache and download folder doesn't match for " + /* SY --> */ manga.ogTitle /* SY <-- */)
            }
        }
        return cleaned
    }
    // SY <--

    /**
     * Deletes the directory of a downloaded manga.
     *
     * @param manga the manga to delete.
     * @param source the source of the manga.
     */
    fun deleteManga(manga: Manga, source: Source) {
        launchIO {
            downloader.queue.remove(manga)
            provider.findMangaDir(/* SY --> */ manga.ogTitle /* SY <-- */, source)?.delete()
            cache.removeManga(manga)
        }
    }

    /**
     * Adds a list of chapters to be deleted later.
     *
     * @param chapters the list of chapters to delete.
     * @param manga the manga of the chapters.
     */
    fun enqueueDeleteChapters(chapters: List<Chapter>, manga: Manga) {
        pendingDeleter.addChapters(getChaptersToDelete(chapters, manga), manga)
    }

    /**
     * Triggers the execution of the deletion of pending chapters.
     */
    fun deletePendingChapters() {
        val pendingChapters = pendingDeleter.getPendingChapters()
        for ((manga, chapters) in pendingChapters) {
            val source = sourceManager.get(manga.source) ?: continue
            deleteChapters(chapters, manga, source)
        }
    }

    /**
     * Renames an already downloaded chapter
     *
     * @param source the source of the manga.
     * @param manga the manga of the chapter.
     * @param oldChapter the existing chapter with the old name.
     * @param newChapter the target chapter with the new name.
     */
    fun renameChapter(source: Source, manga: Manga, oldChapter: Chapter, newChapter: Chapter) {
        val oldNames = provider.getValidChapterDirNames(oldChapter.name, oldChapter.scanlator)
        val mangaDir = provider.getMangaDir(/* SY --> */ manga.ogTitle /* SY <-- */, source)

        // Assume there's only 1 version of the chapter name formats present
        val oldDownload = oldNames.asSequence()
            .mapNotNull { mangaDir.findFile(it) }
            .firstOrNull() ?: return

        var newName = provider.getChapterDirName(newChapter.name, newChapter.scanlator)
        if (oldDownload.isFile && oldDownload.name?.endsWith(".cbz") == true) {
            newName += ".cbz"
        }

        if (oldDownload.renameTo(newName)) {
            cache.removeChapter(oldChapter, manga)
            cache.addChapter(newName, mangaDir, manga)
        } else {
            logcat(LogPriority.ERROR) { "Could not rename downloaded chapter: ${oldNames.joinToString()}." }
        }
    }

    private fun getChaptersToDelete(chapters: List<Chapter>, manga: Manga): List<Chapter> {
        // Retrieve the categories that are set to exclude from being deleted on read
        val categoriesToExclude = preferences.removeExcludeCategories().get().map(String::toInt)

        val categoriesForManga = runBlocking { getCategories.await(manga.id!!) }
            .map { it.id }
            .takeUnless { it.isEmpty() }
            ?: listOf(0)

        return if (categoriesForManga.intersect(categoriesToExclude).isNotEmpty()) {
            chapters.filterNot { it.read }
        } else if (!preferences.removeBookmarkedChapters()) {
            chapters.filterNot { it.bookmark }
        } else {
            chapters
        }
    }

    fun renameMangaDir(oldTitle: String, newTitle: String, source: Long) {
        val sourceDir = provider.findSourceDir(sourceManager.getOrStub(source)) ?: return
        val mangaDir = sourceDir.findFile(DiskUtil.buildValidFilename(oldTitle), true) ?: return
        mangaDir.renameTo(DiskUtil.buildValidFilename(newTitle))
    }
}
