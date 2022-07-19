package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.lang.plusAssign
import eu.kanade.tachiyomi.util.system.logcat
import exh.source.isEhBasedSource
import exh.util.DataSaver
import exh.util.DataSaver.Companion.fetchImage
import logcat.LogPriority
import rx.Completable
import rx.Observable
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import rx.subjects.SerializedSubject
import rx.subscriptions.CompositeSubscription
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * Loader used to load chapters from an online source.
 */
class HttpPageLoader(
    private val chapter: ReaderChapter,
    private val source: HttpSource,
    private val chapterCache: ChapterCache = Injekt.get(),
    // SY -->
    private val preferences: PreferencesHelper = Injekt.get(),
// SY <--
) : PageLoader() {

    /**
     * A queue used to manage requests one by one while allowing priorities.
     */
    private val queue = PriorityBlockingQueue<PriorityPage>()

    /**
     * Current active subscriptions.
     */
    private val subscriptions = CompositeSubscription()

    private val preloadSize = /* SY --> */ preferences.preloadSize().get() // SY <--

    // SY -->
    private val dataSaver = DataSaver(source, preferences)
    // SY <--

    init {
        // EXH -->
        repeat(preferences.readerThreads().get()) {
            // EXH <--
            subscriptions += Observable.defer { Observable.just(queue.take().page) }
                .filter { it.status == Page.QUEUE }
                .concatMap { source.fetchImageFromCacheThenNet(it) }
                .repeat()
                .subscribeOn(Schedulers.io())
                .subscribe(
                    {
                    },
                    { error ->
                        if (error !is InterruptedException) {
                            logcat(LogPriority.ERROR, error)
                        }
                    },
                )
            // EXH -->
        }
        // EXH <--
    }

    /**
     * Recycles this loader and the active subscriptions and queue.
     */
    override fun recycle() {
        super.recycle()
        subscriptions.unsubscribe()
        queue.clear()

        // Cache current page list progress for online chapters to allow a faster reopen
        val pages = chapter.pages
        if (pages != null) {
            Completable
                .fromAction {
                    // Convert to pages without reader information
                    val pagesToSave = pages.map { Page(it.index, it.url, it.imageUrl) }
                    chapterCache.putPageListToCache(chapter.chapter.toDomainChapter()!!, pagesToSave)
                }
                .onErrorComplete()
                .subscribeOn(Schedulers.io())
                .subscribe()
        }
    }

    /**
     * Returns an observable with the page list for a chapter. It tries to return the page list from
     * the local cache, otherwise fallbacks to network.
     */
    override fun getPages(): Observable<List<ReaderPage>> {
        return Observable.fromCallable { chapterCache.getPageListFromCache(chapter.chapter.toDomainChapter()!!) }
            .onErrorResumeNext { source.fetchPageList(chapter.chapter) }
            .map { pages ->
                // SY -->
                val rp = pages.mapIndexed { index, page ->
                    // Don't trust sources and use our own indexing
                    ReaderPage(index, page.url, page.imageUrl)
                }
                if (preferences.aggressivePageLoading().get()) {
                    rp.forEach {
                        if (it.status == Page.QUEUE) {
                            queue.offer(PriorityPage(it, 0))
                        }
                    }
                }
                rp
                // SY <--
            }
    }

    /**
     * Returns an observable that loads a page through the queue and listens to its result to
     * emit new states. It handles re-enqueueing pages if they were evicted from the cache.
     */
    override fun getPage(page: ReaderPage): Observable<Int> {
        return Observable.defer {
            val imageUrl = page.imageUrl

            // Check if the image has been deleted
            if (page.status == Page.READY && imageUrl != null && !chapterCache.isImageInCache(imageUrl)) {
                page.status = Page.QUEUE
            }

            // Automatically retry failed pages when subscribed to this page
            if (page.status == Page.ERROR) {
                page.status = Page.QUEUE
            }

            val statusSubject = SerializedSubject(PublishSubject.create<Int>())
            page.setStatusSubject(statusSubject)

            val queuedPages = mutableListOf<PriorityPage>()
            if (page.status == Page.QUEUE) {
                queuedPages += PriorityPage(page, 1).also { queue.offer(it) }
            }
            queuedPages += preloadNextPages(page, preloadSize)

            statusSubject.startWith(page.status)
                .doOnUnsubscribe {
                    queuedPages.forEach {
                        if (it.page.status == Page.QUEUE) {
                            queue.remove(it)
                        }
                    }
                }
        }
            .subscribeOn(Schedulers.io())
            .unsubscribeOn(Schedulers.io())
    }

    /**
     * Preloads the given [amount] of pages after the [currentPage] with a lower priority.
     * @return a list of [PriorityPage] that were added to the [queue]
     */
    private fun preloadNextPages(currentPage: ReaderPage, amount: Int): List<PriorityPage> {
        val pageIndex = currentPage.index
        val pages = currentPage.chapter.pages ?: return emptyList()
        if (pageIndex == pages.lastIndex) return emptyList()

        return pages
            .subList(pageIndex + 1, min(pageIndex + 1 + amount, pages.size))
            .mapNotNull {
                if (it.status == Page.QUEUE) {
                    PriorityPage(it, 0).apply { queue.offer(this) }
                } else null
            }
    }

    /**
     * Retries a page. This method is only called from user interaction on the viewer.
     */
    override fun retryPage(page: ReaderPage) {
        if (page.status == Page.ERROR) {
            page.status = Page.QUEUE
        }
        // EXH -->
        // Grab a new image URL on EXH sources
        if (source.isEhBasedSource()) {
            page.imageUrl = null
        }

        if (preferences.readerInstantRetry().get()) // EXH <--
            {
                boostPage(page)
            } else {
            // EXH <--
            queue.offer(PriorityPage(page, 2))
        }
    }

    /**
     * Data class used to keep ordering of pages in order to maintain priority.
     */
    private class PriorityPage(
        val page: ReaderPage,
        val priority: Int,
    ) : Comparable<PriorityPage> {
        companion object {
            private val idGenerator = AtomicInteger()
        }

        private val identifier = idGenerator.incrementAndGet()

        override fun compareTo(other: PriorityPage): Int {
            val p = other.priority.compareTo(priority)
            return if (p != 0) p else identifier.compareTo(other.identifier)
        }
    }

    /**
     * Returns an observable of the page with the downloaded image.
     *
     * @param page the page whose source image has to be downloaded.
     */
    private fun HttpSource.fetchImageFromCacheThenNet(page: ReaderPage): Observable<ReaderPage> {
        return if (page.imageUrl.isNullOrEmpty()) {
            getImageUrl(page).flatMap { getCachedImage(it) }
        } else {
            getCachedImage(page)
        }
    }

    private fun HttpSource.getImageUrl(page: ReaderPage): Observable<ReaderPage> {
        page.status = Page.LOAD_PAGE
        return fetchImageUrl(page)
            .doOnError { page.status = Page.ERROR }
            .onErrorReturn { null }
            .doOnNext { page.imageUrl = it }
            .map { page }
    }

    /**
     * Returns an observable of the page that gets the image from the chapter or fallbacks to
     * network and copies it to the cache calling [cacheImage].
     *
     * @param page the page.
     */
    private fun HttpSource.getCachedImage(page: ReaderPage): Observable<ReaderPage> {
        val imageUrl = page.imageUrl ?: return Observable.just(page)

        return Observable.just(page)
            .flatMap {
                if (!chapterCache.isImageInCache(imageUrl)) {
                    cacheImage(page)
                } else {
                    Observable.just(page)
                }
            }
            .doOnNext {
                page.stream = { chapterCache.getImageFile(imageUrl).inputStream() }
                page.status = Page.READY
            }
            .doOnError { page.status = Page.ERROR }
            .onErrorReturn { page }
    }

    /**
     * Returns an observable of the page that downloads the image to [ChapterCache].
     *
     * @param page the page.
     */
    private fun HttpSource.cacheImage(page: ReaderPage): Observable<ReaderPage> {
        page.status = Page.DOWNLOAD_IMAGE
        return fetchImage(page, dataSaver)
            .doOnNext {
                chapterCache.putImageToCache(page.imageUrl!!, it)
            }
            .map { page }
    }

    // EXH -->
    fun boostPage(page: ReaderPage) {
        if (page.status == Page.QUEUE) {
            subscriptions += Observable.just(page)
                .concatMap { source.fetchImageFromCacheThenNet(it) }
                .subscribeOn(Schedulers.io())
                .subscribe(
                    {
                    },
                    { error ->
                        if (error !is InterruptedException) {
                            logcat(LogPriority.ERROR, error)
                        }
                    },
                )
        }
    }
    // EXH <--
}
