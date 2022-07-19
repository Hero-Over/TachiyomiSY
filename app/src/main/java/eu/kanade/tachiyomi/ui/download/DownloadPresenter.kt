package eu.kanade.tachiyomi.ui.download

import android.os.Bundle
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.download.model.DownloadQueue
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.system.logcat
import logcat.LogPriority
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import uy.kohesive.injekt.injectLazy

/**
 * Presenter of [DownloadController].
 */
class DownloadPresenter : BasePresenter<DownloadController>() {

    val downloadManager: DownloadManager by injectLazy()

    /**
     * Property to get the queue from the download manager.
     */
    val downloadQueue: DownloadQueue
        get() = downloadManager.queue

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        downloadQueue.getUpdatedObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .map { downloads ->
                downloads
                    .groupBy { it.source }
                    .map { entry ->
                        DownloadHeaderItem(entry.key.id, entry.key.name, entry.value.size).apply {
                            addSubItems(0, entry.value.map { DownloadItem(it, this) })
                        }
                    }
            }
            .subscribeLatestCache(DownloadController::onNextDownloads) { _, error ->
                logcat(LogPriority.ERROR, error)
            }
    }

    fun getDownloadStatusObservable(): Observable<Download> {
        return downloadQueue.getStatusObservable()
            .startWith(downloadQueue.getActiveDownloads())
    }

    fun getDownloadProgressObservable(): Observable<Download> {
        return downloadQueue.getProgressObservable()
            .onBackpressureBuffer()
    }

    /**
     * Pauses the download queue.
     */
    fun pauseDownloads() {
        downloadManager.pauseDownloads()
    }

    /**
     * Clears the download queue.
     */
    fun clearQueue() {
        downloadManager.clearQueue()
    }

    fun reorder(downloads: List<Download>) {
        downloadManager.reorderQueue(downloads)
    }

    fun cancelDownload(download: Download) {
        downloadManager.deletePendingDownload(download)
    }

    fun cancelDownloads(downloads: List<Download>) {
        downloadManager.deletePendingDownloads(*downloads.toTypedArray())
    }
}
