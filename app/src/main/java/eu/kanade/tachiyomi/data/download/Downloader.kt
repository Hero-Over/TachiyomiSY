package eu.kanade.tachiyomi.data.download

import android.content.Context
import com.hippo.unifile.UniFile
import com.jakewharton.rxrelay.BehaviorRelay
import com.jakewharton.rxrelay.PublishRelay
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.download.model.DownloadQueue
import eu.kanade.tachiyomi.data.library.LibraryUpdateNotifier
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.fetchAllImageUrlsFromPageList
import eu.kanade.tachiyomi.util.lang.RetryWithDelay
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchNow
import eu.kanade.tachiyomi.util.lang.plusAssign
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.saveTo
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.logcat
import exh.util.DataSaver
import exh.util.DataSaver.Companion.fetchImage
import kotlinx.coroutines.async
import logcat.LogPriority
import okhttp3.Response
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subscriptions.CompositeSubscription
import uy.kohesive.injekt.injectLazy
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * This class is the one in charge of downloading chapters.
 *
 * Its [queue] contains the list of chapters to download. In order to download them, the downloader
 * subscriptions must be running and the list of chapters must be sent to them by [downloadsRelay].
 *
 * The queue manipulation must be done in one thread (currently the main thread) to avoid unexpected
 * behavior, but it's safe to read it from multiple threads.
 *
 * @param context the application context.
 * @param provider the downloads directory provider.
 * @param cache the downloads cache, used to add the downloads to the cache after their completion.
 * @param sourceManager the source manager.
 */
class Downloader(
    private val context: Context,
    private val provider: DownloadProvider,
    private val cache: DownloadCache,
    private val sourceManager: SourceManager,
) {

    private val chapterCache: ChapterCache by injectLazy()

    private val preferences: PreferencesHelper by injectLazy()

    /**
     * Store for persisting downloads across restarts.
     */
    private val store = DownloadStore(context, sourceManager)

    /**
     * Queue where active downloads are kept.
     */
    val queue = DownloadQueue(store)

    /**
     * Notifier for the downloader state and progress.
     */
    private val notifier by lazy { DownloadNotifier(context) }

    /**
     * Downloader subscriptions.
     */
    private val subscriptions = CompositeSubscription()

    /**
     * Relay to send a list of downloads to the downloader.
     */
    private val downloadsRelay = PublishRelay.create<List<Download>>()

    /**
     * Relay to subscribe to the downloader status.
     */
    val runningRelay: BehaviorRelay<Boolean> = BehaviorRelay.create(false)

    /**
     * Whether the downloader is running.
     */
    @Volatile
    var isRunning: Boolean = false
        private set

    init {
        launchNow {
            val chapters = async { store.restore() }
            queue.addAll(chapters.await())
        }
    }

    /**
     * Starts the downloader. It doesn't do anything if it's already running or there isn't anything
     * to download.
     *
     * @return true if the downloader is started, false otherwise.
     */
    fun start(): Boolean {
        if (isRunning || queue.isEmpty()) {
            return false
        }

        if (!subscriptions.hasSubscriptions()) {
            initializeSubscriptions()
        }

        val pending = queue.filter { it.status != Download.State.DOWNLOADED }
        pending.forEach { if (it.status != Download.State.QUEUE) it.status = Download.State.QUEUE }

        notifier.paused = false

        downloadsRelay.call(pending)
        return pending.isNotEmpty()
    }

    /**
     * Stops the downloader.
     */
    fun stop(reason: String? = null) {
        destroySubscriptions()
        queue
            .filter { it.status == Download.State.DOWNLOADING }
            .forEach { it.status = Download.State.ERROR }

        if (reason != null) {
            notifier.onWarning(reason)
            return
        }

        if (notifier.paused && !queue.isEmpty()) {
            notifier.onPaused()
        } else {
            notifier.onComplete()
        }

        notifier.paused = false
    }

    /**
     * Pauses the downloader
     */
    fun pause() {
        destroySubscriptions()
        queue
            .filter { it.status == Download.State.DOWNLOADING }
            .forEach { it.status = Download.State.QUEUE }
        notifier.paused = true
    }

    /**
     * Check if downloader is paused
     */
    fun isPaused() = !isRunning

    /**
     * Removes everything from the queue.
     *
     * @param isNotification value that determines if status is set (needed for view updates)
     */
    fun clearQueue(isNotification: Boolean = false) {
        destroySubscriptions()

        // Needed to update the chapter view
        if (isNotification) {
            queue
                .filter { it.status == Download.State.QUEUE }
                .forEach { it.status = Download.State.NOT_DOWNLOADED }
        }
        queue.clear()
        notifier.dismissProgress()
    }

    /**
     * Prepares the subscriptions to start downloading.
     */
    private fun initializeSubscriptions() {
        if (isRunning) return
        isRunning = true
        runningRelay.call(true)

        subscriptions.clear()

        subscriptions += downloadsRelay.concatMapIterable { it }
            // Concurrently download from 5 different sources
            .groupBy { it.source }
            .flatMap(
                { bySource ->
                    bySource.concatMap { download ->
                        downloadChapter(download).subscribeOn(Schedulers.io())
                    }
                },
                5,
            )
            .onBackpressureLatest()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    completeDownload(it)
                },
                { error ->
                    DownloadService.stop(context)
                    logcat(LogPriority.ERROR, error)
                    notifier.onError(error.message)
                },
            )
    }

    /**
     * Destroys the downloader subscriptions.
     */
    private fun destroySubscriptions() {
        if (!isRunning) return
        isRunning = false
        runningRelay.call(false)

        subscriptions.clear()
    }

    /**
     * Creates a download object for every chapter and adds them to the downloads queue.
     *
     * @param manga the manga of the chapters to download.
     * @param chapters the list of chapters to download.
     * @param autoStart whether to start the downloader after enqueing the chapters.
     */
    fun queueChapters(manga: Manga, chapters: List<Chapter>, autoStart: Boolean) = launchIO {
        val source = sourceManager.get(manga.source) as? HttpSource ?: return@launchIO
        val wasEmpty = queue.isEmpty()
        // Called in background thread, the operation can be slow with SAF.
        val chaptersWithoutDir = async {
            chapters
                // Filter out those already downloaded.
                .filter { provider.findChapterDir(it.name, it.scanlator, /* SY --> */ manga.ogTitle /* SY <-- */, source) == null }
                // Add chapters to queue from the start.
                .sortedByDescending { it.source_order }
        }

        // Runs in main thread (synchronization needed).
        val chaptersToQueue = chaptersWithoutDir.await()
            // Filter out those already enqueued.
            .filter { chapter -> queue.none { it.chapter.id == chapter.id } }
            // Create a download for each one.
            .map { Download(source, manga, it) }

        if (chaptersToQueue.isNotEmpty()) {
            queue.addAll(chaptersToQueue)

            if (isRunning) {
                // Send the list of downloads to the downloader.
                downloadsRelay.call(chaptersToQueue)
            }

            // Start downloader if needed
            if (autoStart && wasEmpty) {
                val queuedDownloads = queue.count { it.source !is UnmeteredSource }
                val maxDownloadsFromSource = queue
                    .groupBy { it.source }
                    .filterKeys { it !is UnmeteredSource }
                    .maxOfOrNull { it.value.size }
                    ?: 0
                if (
                    queuedDownloads > DOWNLOADS_QUEUED_WARNING_THRESHOLD ||
                    maxDownloadsFromSource > CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD
                ) {
                    withUIContext {
                        notifier.onWarning(
                            context.getString(R.string.download_queue_size_warning),
                            WARNING_NOTIF_TIMEOUT_MS,
                            NotificationHandler.openUrl(context, LibraryUpdateNotifier.HELP_WARNING_URL),
                        )
                    }
                }
                DownloadService.start(context)
            }
        }
    }

    /**
     * Returns the observable which downloads a chapter.
     *
     * @param download the chapter to be downloaded.
     */
    private fun downloadChapter(download: Download): Observable<Download> = Observable.defer {
        val mangaDir = provider.getMangaDir(/* SY --> */ download.manga.ogTitle /* SY <-- */, download.source)

        val availSpace = DiskUtil.getAvailableStorageSpace(mangaDir)
        if (availSpace != -1L && availSpace < MIN_DISK_SPACE) {
            download.status = Download.State.ERROR
            notifier.onError(context.getString(R.string.download_insufficient_space), download.chapter.name, download.manga.title)
            return@defer Observable.just(download)
        }

        val chapterDirname = provider.getChapterDirName(download.chapter.name, download.chapter.scanlator)
        val tmpDir = mangaDir.createDirectory(chapterDirname + TMP_DIR_SUFFIX)

        val pageListObservable = if (download.pages == null) {
            // Pull page list from network and add them to download object
            download.source.fetchPageList(download.chapter)
                .doOnNext { pages ->
                    if (pages.isEmpty()) {
                        throw Exception(context.getString(R.string.page_list_empty_error))
                    }
                    download.pages = pages
                }
        } else {
            // Or if the page list already exists, start from the file
            Observable.just(download.pages!!)
        }

        val dataSaver = if (preferences.dataSaverDownloader().get()) {
            DataSaver(download.source, preferences)
        } else DataSaver.NoOp

        pageListObservable
            .doOnNext { _ ->
                // Delete all temporary (unfinished) files
                tmpDir.listFiles()
                    ?.filter { it.name!!.endsWith(".tmp") }
                    ?.forEach { it.delete() }

                download.downloadedImages = 0
                download.status = Download.State.DOWNLOADING
            }
            // Get all the URLs to the source images, fetch pages if necessary
            .flatMap { download.source.fetchAllImageUrlsFromPageList(it) }
            // Start downloading images, consider we can have downloaded images already
            // Concurrently do 5 pages at a time
            .flatMap({ page -> getOrDownloadImage(page, download, tmpDir, dataSaver) }, 5)
            .onBackpressureLatest()
            // Do when page is downloaded.
            .doOnNext { notifier.onProgressChange(download) }
            .toList()
            .map { download }
            // Do after download completes
            .doOnNext { ensureSuccessfulDownload(download, mangaDir, tmpDir, chapterDirname) }
            // If the page list threw, it will resume here
            .onErrorReturn { error ->
                logcat(LogPriority.ERROR, error)
                download.status = Download.State.ERROR
                notifier.onError(error.message, download.chapter.name, download.manga.title)
                download
            }
    }

    /**
     * Returns the observable which gets the image from the filesystem if it exists or downloads it
     * otherwise.
     *
     * @param page the page to download.
     * @param download the download of the page.
     * @param tmpDir the temporary directory of the download.
     */
    private fun getOrDownloadImage(page: Page, download: Download, tmpDir: UniFile, dataSaver: DataSaver): Observable<Page> {
        // If the image URL is empty, do nothing
        if (page.imageUrl == null) {
            return Observable.just(page)
        }

        val filename = String.format("%03d", page.number)
        val tmpFile = tmpDir.findFile("$filename.tmp")

        // Delete temp file if it exists.
        tmpFile?.delete()

        // Try to find the image file.
        val imageFile = tmpDir.listFiles()!!.find { it.name!!.startsWith("$filename.") || it.name!!.contains("${filename}__001") }

        // If the image is already downloaded, do nothing. Otherwise download from network
        val pageObservable = when {
            imageFile != null -> Observable.just(imageFile)
            chapterCache.isImageInCache(page.imageUrl!!) -> copyImageFromCache(chapterCache.getImageFile(page.imageUrl!!), tmpDir, filename)
            else -> downloadImage(page, download.source, tmpDir, filename, dataSaver)
        }

        return pageObservable
            // When the page is ready, set page path, progress (just in case) and status
            .doOnNext { file ->
                val success = splitTallImageIfNeeded(page, tmpDir)
                if (success.not()) {
                    notifier.onError(context.getString(R.string.download_notifier_split_failed), download.chapter.name, download.manga.title)
                }
                page.uri = file.uri
                page.progress = 100
                download.downloadedImages++
                page.status = Page.READY
            }
            .map { page }
            // Mark this page as error and allow to download the remaining
            .onErrorReturn {
                page.progress = 0
                page.status = Page.ERROR
                notifier.onError(it.message, download.chapter.name, download.manga.title)
                page
            }
    }

    /**
     * Returns the observable which downloads the image from network.
     *
     * @param page the page to download.
     * @param source the source of the page.
     * @param tmpDir the temporary directory of the download.
     * @param filename the filename of the image.
     */
    private fun downloadImage(page: Page, source: HttpSource, tmpDir: UniFile, filename: String, dataSaver: DataSaver): Observable<UniFile> {
        page.status = Page.DOWNLOAD_IMAGE
        page.progress = 0
        return source.fetchImage(page, dataSaver)
            .map { response ->
                val file = tmpDir.createFile("$filename.tmp")
                try {
                    response.body!!.source().saveTo(file.openOutputStream())
                    val extension = getImageExtension(response, file)
                    file.renameTo("$filename.$extension")
                } catch (e: Exception) {
                    response.close()
                    file.delete()
                    throw e
                }
                file
            }
            // Retry 3 times, waiting 2, 4 and 8 seconds between attempts.
            .retryWhen(RetryWithDelay(3, { (2 shl it - 1) * 1000 }, Schedulers.trampoline()))
    }

    /**
     * Return the observable which copies the image from cache.
     *
     * @param cacheFile the file from cache.
     * @param tmpDir the temporary directory of the download.
     * @param filename the filename of the image.
     */
    private fun copyImageFromCache(cacheFile: File, tmpDir: UniFile, filename: String): Observable<UniFile> {
        return Observable.just(cacheFile).map {
            val tmpFile = tmpDir.createFile("$filename.tmp")
            cacheFile.inputStream().use { input ->
                tmpFile.openOutputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val extension = ImageUtil.findImageType(cacheFile.inputStream()) ?: return@map tmpFile
            tmpFile.renameTo("$filename.${extension.extension}")
            cacheFile.delete()
            tmpFile
        }
    }

    /**
     * Returns the extension of the downloaded image from the network response, or if it's null,
     * analyze the file. If everything fails, assume it's a jpg.
     *
     * @param response the network response of the image.
     * @param file the file where the image is already downloaded.
     */
    private fun getImageExtension(response: Response, file: UniFile): String {
        // Read content type if available.
        val mime = response.body?.contentType()?.let { ct -> "${ct.type}/${ct.subtype}" }
            // Else guess from the uri.
            ?: context.contentResolver.getType(file.uri)
            // Else read magic numbers.
            ?: ImageUtil.findImageType { file.openInputStream() }?.mime

        return ImageUtil.getExtensionFromMimeType(mime)
    }

    private fun splitTallImageIfNeeded(page: Page, tmpDir: UniFile): Boolean {
        if (!preferences.splitTallImages().get()) return true

        val filename = String.format("%03d", page.number)
        val imageFile = tmpDir.listFiles()?.find { it.name!!.startsWith(filename) }
            ?: throw Error(context.getString(R.string.download_notifier_split_page_not_found, page.number))
        val imageFilePath = imageFile.filePath
            ?: throw Error(context.getString(R.string.download_notifier_split_page_path_not_found, page.number))

        // check if the original page was previously splitted before then skip.
        if (imageFile.name!!.contains("__")) return true

        return try {
            ImageUtil.splitTallImage(imageFile, imageFilePath)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    /**
     * Checks if the download was successful.
     *
     * @param download the download to check.
     * @param mangaDir the manga directory of the download.
     * @param tmpDir the directory where the download is currently stored.
     * @param dirname the real (non temporary) directory name of the download.
     */
    private fun ensureSuccessfulDownload(
        download: Download,
        mangaDir: UniFile,
        tmpDir: UniFile,
        dirname: String,
    ) {
        // Ensure that the chapter folder has all the images.
        val downloadedImages = tmpDir.listFiles().orEmpty().filterNot { it.name!!.endsWith(".tmp") || (it.name!!.contains("__") && !it.name!!.contains("__001.jpg")) }

        download.status = if (downloadedImages.size == download.pages!!.size) {
            // Only rename the directory if it's downloaded.
            if (preferences.saveChaptersAsCBZ().get()) {
                archiveChapter(mangaDir, dirname, tmpDir)
            } else {
                tmpDir.renameTo(dirname)
            }
            cache.addChapter(dirname, mangaDir, download.manga)

            DiskUtil.createNoMediaFile(tmpDir, context)

            Download.State.DOWNLOADED
        } else {
            Download.State.ERROR
        }
    }

    /**
     * Archive the chapter pages as a CBZ.
     */
    private fun archiveChapter(
        mangaDir: UniFile,
        dirname: String,
        tmpDir: UniFile,
    ) {
        val zip = mangaDir.createFile("$dirname.cbz.tmp")
        ZipOutputStream(BufferedOutputStream(zip.openOutputStream())).use { zipOut ->
            zipOut.setMethod(ZipEntry.STORED)

            tmpDir.listFiles()?.forEach { img ->
                img.openInputStream().use { input ->
                    val data = input.readBytes()
                    val size = img.length()
                    val entry = ZipEntry(img.name).apply {
                        val crc = CRC32().apply {
                            update(data)
                        }
                        setCrc(crc.value)

                        compressedSize = size
                        setSize(size)
                    }
                    zipOut.putNextEntry(entry)
                    zipOut.write(data)
                }
            }
        }
        zip.renameTo("$dirname.cbz")
        tmpDir.delete()
    }

    /**
     * Completes a download. This method is called in the main thread.
     */
    private fun completeDownload(download: Download) {
        // Delete successful downloads from queue
        if (download.status == Download.State.DOWNLOADED) {
            // remove downloaded chapter from queue
            queue.remove(download)
        }
        if (areAllDownloadsFinished()) {
            DownloadService.stop(context)
        }
    }

    /**
     * Returns true if all the queued downloads are in DOWNLOADED or ERROR state.
     */
    private fun areAllDownloadsFinished(): Boolean {
        return queue.none { it.status.value <= Download.State.DOWNLOADING.value }
    }

    companion object {
        const val TMP_DIR_SUFFIX = "_tmp"
        const val WARNING_NOTIF_TIMEOUT_MS = 30_000L
        const val CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 15
        private const val DOWNLOADS_QUEUED_WARNING_THRESHOLD = 30
    }
}

// Arbitrary minimum required space to start a download: 200 MB
private const val MIN_DISK_SPACE = 200L * 1024 * 1024
